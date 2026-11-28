package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;

import java.util.List;

/**
 * 0.6.1 · TB-scale cluster memory cabinet ({@code hypercluster:vram_storage_rack}).
 *
 * <p>
 * A 5×5×3 cabinet of {@code hypercluster:vram_rack_casing} behind reinforced glass, with an
 * energy hatch and a maintenance hatch. It holds {@value VramRating#HBM_SLOTS} HBM modules and
 * exposes their combined capacity as the cluster memory pool:
 *
 * <ul>
 * <li>{@code hypercluster:iv_hbm_module} — {@value VramRating#IV_HBM_TB} TB each, so a full IV
 * rack is {@value VramRating#IV_RACK_TB} TB;</li>
 * <li>{@code hypercluster:luv_hbm_module} — {@value VramRating#LUV_HBM_TB} TB each, so a full
 * LuV rack is {@value VramRating#LUV_RACK_TB} TB.</li>
 * </ul>
 *
 * <p>
 * Two full LuV racks reach the {@value VramRating#TRAINING_JOB_TB} TB pretraining gate — the
 * 0.6.1 replacement for the old on-blade memory rule. Compute blades no longer carry any
 * memory at all: this rack is the only place cluster VRAM exists, so pulling a module (or losing
 * power here) takes the memory offline immediately and every dependent model unloads.
 */
public class VramStorageRackMachine extends WorkableElectricMultiblockMachine
                                    implements IMachineLife, DatacenterHardwareHatchMachine.HardwareHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            VramStorageRackMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Why the rack is not contributing memory right now. */
    public enum State {
        /** Memory is online and counted in the pool. */
        ONLINE,
        /** No HBM modules inserted. */
        EMPTY,
        /** Maintenance problems unfixed. */
        MAINTENANCE,
        /** Paused with a soft mallet. */
        PAUSED,
        /** Not enough energy to keep the modules refreshed. */
        LOW_POWER
    }

    /** EU/t the rack draws while refreshing its modules. */
    public static final long REFRESH_EU_T = 512L;

    @Persisted
    protected final CustomItemStackHandler moduleSlot;

    @javax.annotation.Nullable
    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    @Persisted
    @DescSynced
    protected State state = State.EMPTY;

    public VramStorageRackMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.moduleSlot = DatacenterHardwareHatchMachine.componentSlots(VramRating.HBM_SLOTS);
        this.moduleSlot.setFilter(stack -> stack.is(HyperClusterItems.IV_HBM_MODULE.get()) ||
                stack.is(HyperClusterItems.LUV_HBM_MODULE.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Memory pool //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Total TB this rack provides with its current modules (regardless of power state). */
    public int getTerabytesInstalled() {
        int total = 0;
        for (int i = 0; i < moduleSlot.getSlots(); i++) {
            ItemStack stack = moduleSlot.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            total += stack.is(HyperClusterItems.LUV_HBM_MODULE.get()) ?
                    VramRating.LUV_HBM_TB : VramRating.IV_HBM_TB;
        }
        return total;
    }

    /** TB this rack actually contributes to the cluster pool right now (0 unless ONLINE). */
    public int getTerabytesOnline() {
        return isFormed() && state == State.ONLINE ? getTerabytesInstalled() : 0;
    }

    public int getSlotCount() {
        return VramRating.HBM_SLOTS;
    }

    public CustomItemStackHandler getModuleSlot() {
        return moduleSlot;
    }

    public State getState() {
        return state;
    }

    public void setState(State next) {
        if (this.state != next) {
            this.state = next;
            updateRenderState();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        DatacenterFloorNetwork.formed(this);
        this.maintenance = null;
        for (var part : getParts()) {
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
        }
        updateTickSubscription();
        settle();
    }

    @Override
    public void onStructureInvalid() {
        DatacenterFloorNetwork.detached(this);
        super.onStructureInvalid();
        this.maintenance = null;
        setState(State.EMPTY);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getServer().execute(this::updateTickSubscription);
        }
    }

    @Override
    public void onUnload() {
        DatacenterFloorNetwork.detached(this);
        super.onUnload();
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed()) {
            this.tickSubs = subscribeServerTick(this.tickSubs, this::settle);
        } else if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    /** Decides the pool state: modules present, maintenance fixed, powered and not paused. */
    protected void settle() {
        if (!isFormed()) {
            return;
        }
        if (!recipeLogic.isWorkingEnabled()) {
            setState(State.PAUSED);
            return;
        }
        if (maintenance != null && maintenance.getNumMaintenanceProblems() > 0) {
            setState(State.MAINTENANCE);
            return;
        }
        if (getTerabytesInstalled() <= 0) {
            setState(State.EMPTY);
            return;
        }
        var energy = getEnergyContainer();
        if (energy == null || energy.getEnergyStored() < REFRESH_EU_T) {
            setState(State.LOW_POWER);
            return;
        }
        energy.removeEnergy(REFRESH_EU_T);
        setState(State.ONLINE);
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(moduleSlot);
    }

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = state == State.ONLINE ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // UI //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public IItemHandlerModifiable hardwareInventory() {
        return moduleSlot;
    }

    @Override
    public String hardwareSlotKey(int slot) {
        return "hypercluster.machine.vram_storage_rack.module_slot";
    }

    @Override
    public Widget createUIWidget() {
        return MachineUi.multiblockPage(this, MachineUi.multiblockControls(1));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        String uiKey = "hypercluster.machine.vram_storage_rack.";
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), state == State.ONLINE)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        uiKey + "online")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    lines.add(Component.translatable(uiKey + "pool",
                            Component.literal(String.valueOf(getTerabytesOnline())).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(VramRating.LUV_RACK_TB)).withStyle(ChatFormatting.GRAY))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "draw",
                            Component.literal(REFRESH_EU_T + " EU/t").withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GRAY));
                    if (state != State.ONLINE) {
                        lines.add(Component
                                .translatable(uiKey + "state." + state.name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(ChatFormatting.RED));
                    }
                })
                .addWorkingStatusLine();
    }

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        VentParticles.emit(getLevel(), random, getPos(), getFrontFacing(), 1.0, 2);
    }
}
