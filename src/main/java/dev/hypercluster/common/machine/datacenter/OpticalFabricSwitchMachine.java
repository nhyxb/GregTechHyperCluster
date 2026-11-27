package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.common.machine.MachineUi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 0.6.1 · optical fabric switch ({@code hypercluster:optical_fabric_switch}).
 *
 * <p>
 * A 5×4×3 backplane cabinet that turns a room full of separate racks into <b>one cluster pool</b>.
 * Every tick it aggregates the offer of every compute rack within
 * {@value VramRating#SWITCH_RANGE} blocks — IV air-cooled racks (24 CWU/t each) and LuV
 * liquid-cooled racks (64 CWU/t each) — into a single {@link IOpticalComputationProvider} with a
 * measurable hard ceiling:
 *
 * <ul>
 * <li><b>Bandwidth ceiling</b> {@value VramRating#SWITCH_BANDWIDTH_LIMIT} CWU/t. Demand inside the
 * ceiling is served from the members' own same-tick budgets, so no CWU/t is ever invented.</li>
 * <li><b>Over the ceiling → 503.</b> The switch refuses the whole tick, records
 * {@code 503 Server Busy} and stays busy for {@value VramRating#BUSY_COOLDOWN_TICKS} ticks
 * (15 s). Nothing is billed and no member budget is consumed during the cooldown.</li>
 * <li>Memory aggregation rides along: the same range's {@link VramStorageRackMachine} cabinets
 * sum into the cluster VRAM pool.</li>
 * </ul>
 */
public class OpticalFabricSwitchMachine extends WorkableElectricMultiblockMachine
                                        implements IOpticalComputationProvider {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            OpticalFabricSwitchMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** EU/t the switch draws while aggregating. */
    public static final long SWITCH_EU_T = 1024L;

    /** What the switch is doing this tick. */
    public enum Status {
        /** Aggregating and serving within bandwidth. */
        SERVING,
        /** No member racks in range. */
        NO_MEMBERS,
        /** Aggregate demand exceeded the ceiling: 503, cooling down. */
        BUSY_503,
        /** Not enough energy. */
        LOW_POWER,
        /** Paused with a soft mallet. */
        PAUSED,
        /** Maintenance problems unfixed. */
        MAINTENANCE
    }

    @javax.annotation.Nullable
    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    /** Same-tick aggregated budget window. */
    private long budgetStamp = Long.MIN_VALUE;
    private int allocatedCWUt = 0;
    /** Demand seen during the current window; used for the 503 decision. */
    private int windowDemand = 0;

    @Persisted
    @DescSynced
    protected Status status = Status.NO_MEMBERS;
    @Persisted
    @DescSynced
    protected int cooldownTicks = 0;
    @Persisted
    @DescSynced
    protected int lastServedCwu = 0;
    @Persisted
    @DescSynced
    protected int lastMemoryTb = 0;

    public OpticalFabricSwitchMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accessors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public Status getStatus() {
        return status;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getLastServedCwu() {
        return lastServedCwu;
    }

    public int getLastMemoryTb() {
        return lastMemoryTb;
    }

    public int getBandwidthLimit() {
        return VramRating.SWITCH_BANDWIDTH_LIMIT;
    }

    /** True while a 503 cooldown is running: the whole switch refuses work. */
    public boolean isBusy() {
        return cooldownTicks > 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Aggregation //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Compute racks the switch currently aggregates (both IV air-cooled and LuV liquid). */
    public List<IOpticalComputationProvider> memberProviders() {
        List<IOpticalComputationProvider> members = new ArrayList<>();
        if (getLevel() == null || getLevel().isClientSide()) {
            return members;
        }
        int r = VramRating.SWITCH_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (!(be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE)) {
                continue;
            }
            var machine = machineBE.getMetaMachine();
            if (machine instanceof AirCooledComputeRackMachine rack && rack.isFormed()) {
                members.add(rack);
            } else if (machine instanceof LiquidCooledComputeRackMachine rack && rack.isFormed()) {
                members.add(rack);
            }
        }
        return members;
    }

    /** Aggregate CWU/t the member racks offer right now. */
    public int aggregateAvailableCwu() {
        int total = 0;
        for (IOpticalComputationProvider member : memberProviders()) {
            total += member.getMaxCWUt(new ArrayList<>());
        }
        return total;
    }

    /** Aggregate cluster memory (TB) of the memory racks in range. */
    public int aggregateMemoryTb() {
        int total = 0;
        if (getLevel() == null || getLevel().isClientSide()) {
            return 0;
        }
        int r = VramRating.SWITCH_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof VramStorageRackMachine vram && vram.isFormed()) {
                total += vram.getTerabytesOnline();
            }
        }
        return total;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // IOpticalComputationProvider: aggregate + bandwidth ceiling //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (cwut <= 0 || !isFormed() || status == Status.BUSY_503 || isBusy()) {
            return 0;
        }
        long now = getOffsetTimer();
        if (this.budgetStamp < now) {
            this.budgetStamp = now;
            this.allocatedCWUt = 0;
            this.windowDemand = 0;
        }
        // Bandwidth ceiling first: demand above it trips the 503 and serves nobody this tick.
        if (!VramRating.withinSwitchBandwidth(this.windowDemand + cwut)) {
            tripBusy();
            return 0;
        }
        int memberBudget = 0;
        for (IOpticalComputationProvider member : memberProviders()) {
            memberBudget += member.getMaxCWUt(new ArrayList<>());
        }
        int room = Math.max(0, Math.min(VramRating.SWITCH_BANDWIDTH_LIMIT, memberBudget) - this.allocatedCWUt);
        int granted = Math.min(cwut, room);
        if (!simulate && granted > 0) {
            // Really pull from the members so their same-tick budgets are actually consumed.
            int remaining = granted;
            for (IOpticalComputationProvider member : memberProviders()) {
                if (remaining <= 0) {
                    break;
                }
                int took = member.requestCWUt(remaining, false, new ArrayList<>());
                remaining -= took;
            }
            int actuallyGranted = granted - Math.max(0, remaining);
            this.allocatedCWUt += actuallyGranted;
            this.windowDemand += cwut;
            this.lastServedCwu = actuallyGranted;
            return actuallyGranted;
        }
        if (simulate) {
            return granted;
        }
        return 0;
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (!isFormed() || isBusy()) {
            return 0;
        }
        return Math.min(VramRating.SWITCH_BANDWIDTH_LIMIT, aggregateAvailableCwu());
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return true;
    }

    /** Trips the 503 busy cooldown: nobody is served until it expires. */
    public void tripBusy() {
        this.cooldownTicks = VramRating.BUSY_COOLDOWN_TICKS;
        this.status = Status.BUSY_503;
        updateRenderState();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
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
        super.onStructureInvalid();
        this.maintenance = null;
        this.cooldownTicks = 0;
        setStatus(Status.NO_MEMBERS);
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

    /** Per-tick housekeeping: pay the draw, run down the 503 cooldown, publish the pool. */
    protected void settle() {
        if (!isFormed()) {
            return;
        }
        if (!recipeLogic.isWorkingEnabled()) {
            setStatus(Status.PAUSED);
            return;
        }
        if (maintenance != null && maintenance.getNumMaintenanceProblems() > 0) {
            setStatus(Status.MAINTENANCE);
            return;
        }
        var energy = getEnergyContainer();
        if (energy == null || energy.getEnergyStored() < SWITCH_EU_T) {
            setStatus(Status.LOW_POWER);
            return;
        }
        energy.removeEnergy(SWITCH_EU_T);
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            setStatus(Status.BUSY_503);
            return;
        }
        this.lastMemoryTb = aggregateMemoryTb();
        setStatus(memberProviders().isEmpty() ? Status.NO_MEMBERS : Status.SERVING);
    }

    protected void setStatus(Status next) {
        if (this.status != next) {
            this.status = next;
            updateRenderState();
        }
    }

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = status == Status.SERVING ? RecipeLogic.Status.WORKING :
                RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // UI //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        // The switch has no slots or buttons: its whole state is the status display above. It
        // still gets a single-pixel-tall controls strip so the page geometry stays uniform with
        // every other multiblock (a zero-size group would fail the UI bounds check).
        return MachineUi.multiblockPage(this, MachineUi.multiblockControls(1));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        String uiKey = "hypercluster.machine.optical_fabric_switch.";
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), status == Status.SERVING)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        uiKey + "serving")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    lines.add(Component.translatable(uiKey + "members",
                            Component.literal(String.valueOf(memberProviders().size()))
                                    .withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(aggregateAvailableCwu()))
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "bandwidth",
                            Component.literal(String.valueOf(VramRating.SWITCH_BANDWIDTH_LIMIT))
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "memory",
                            Component.literal(String.valueOf(lastMemoryTb)).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    if (status == Status.BUSY_503) {
                        lines.add(Component.translatable(uiKey + "busy",
                                Component.literal(String.format("%.1f", cooldownTicks / 20.0))
                                        .withStyle(ChatFormatting.RED))
                                .withStyle(ChatFormatting.RED));
                    } else if (status != Status.SERVING) {
                        lines.add(Component
                                .translatable(uiKey + "status." + status.name().toLowerCase(java.util.Locale.ROOT))
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
