package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
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
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fluids.FluidStack;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 0.6 · Tier III cold-plate liquid-cooled compute rack
 * ({@code hypercluster:liquid_cooled_compute_rack}).
 *
 * <p>
 * The LuV step above the 0.3 air-cooled rack: a 5×5×3 double-row liquid cabinet built from
 * {@code hypercluster:liquid_rack_casing}, GTCEu reinforced glass, a cable-tray frame on top and
 * the standard energy / fluid / maintenance / native computation hatches. It accepts four
 * {@code hypercluster:luv_liquid_blade} items, each worth {@value LiquidDatacenterRating#CWU_PER_BLADE}
 * CWU/t, so a fully loaded rack offers <b>{@value LiquidDatacenterRating#SWITCH_BANDWIDTH_LIMIT}
 * CWU/t</b> — enough for one rack to drive the vanilla {@code gtceu:research_station} loop that
 * needs {@value LiquidDatacenterRating#RESEARCH_STATION_CWUT} CWU/t on its own.
 *
 * <p>
 * <b>Cooling is a real input.</b> While providing computation the rack drinks
 * {@value LiquidDatacenterRating#COOLANT_PER_TICK} mB/t of cold-plate coolant (distilled water, or
 * PCB coolant for a 15% cooler room). A rack that cannot drink derates to 0 CWU/t after
 * {@value LiquidDatacenterRating#COOLANT_STARVE_TICKS} ticks — cooling loss is a compute loss, not
 * a cosmetic warning.
 *
 * <p>
 * <b>PUE.</b> The room settles at {@code 1 + 0.20 = 1.20} in a neutral environment
 * ({@link LiquidDatacenterRating#PUE_TIER_III}); the native-cold-source and hostile-environment
 * coefficients of {@link DatacenterRating} still apply to the cooling term.
 *
 * <p>
 * <b>Two hard budgets</b> are exposed for the training cluster: the switch bandwidth ceiling
 * ({@value LiquidDatacenterRating#SWITCH_BANDWIDTH_LIMIT} CWU/t per tick) and the VRAM budget
 * ({@value LiquidDatacenterRating#VRAM_PER_BLADE} TB per blade — 0 since 0.6.1; cluster memory comes from the VRAM
 * storage rack).
 *
 * <p>
 * <b>Strict same-tick CWU/t budget.</b> Identical to the 0.3 rack: {@code simulate} only queries,
 * real allocations share one per-tick window, so two consumers in the same tick can never
 * double-dip.
 */
public class LiquidCooledComputeRackMachine extends WorkableElectricMultiblockMachine
                                            implements IOpticalComputationProvider, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LiquidCooledComputeRackMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Chebyshev reach (blocks) for "a running precision CRAC is nearby". */
    public static final int CRAC_RANGE = 8;

    /** Bounded re-validation cadence for the room rating (not a per-tick scan). */
    public static final int ROOM_RECHECK_INTERVAL = 40;

    /** Why the rack currently offers zero CWU/t. NONE = providing. */
    public enum DerateReason {
        NONE,
        PAUSED,
        MAINTENANCE,
        NO_BLADES,
        LOW_POWER,
        NO_COOLANT
    }

    @Persisted
    protected final CustomItemStackHandler bladeInventory;
    @Persisted
    protected final CustomItemStackHandler snackSlot;

    @javax.annotation.Nullable
    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    // Same-tick CWU/t budget.
    private long budgetStamp = Long.MIN_VALUE;
    private int allocatedCWUt = 0;

    // Live settlement state.
    @Persisted
    @DescSynced
    protected int roomTier = 3;
    protected double environmentMultiplier = 1.0;
    @DescSynced
    protected DerateReason derateReason = DerateReason.NO_BLADES;
    protected boolean providing = false;
    @DescSynced
    protected long lastTotalEUt = LiquidDatacenterRating.STANDBY_EU_T;
    @DescSynced
    protected double coolantEfficiency = 1.0;
    @DescSynced
    protected String coolantName = "";
    /** Consecutive ticks the rack wanted coolant and found none. */
    protected int coolantStarveTicks = 0;

    private int recheckCountdown = 1;

    public LiquidCooledComputeRackMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.bladeInventory = new CustomItemStackHandler(LiquidDatacenterRating.BLADE_SLOTS);
        this.bladeInventory.setFilter(stack -> stack.is(HyperClusterItems.LUV_LIQUID_BLADE.get()));
        this.snackSlot = new CustomItemStackHandler(1);
        this.snackSlot.setFilter(stack -> stack.is(HyperClusterItems.GREEN_SNACK_PACK.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structure lifecycle //
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
        this.environmentMultiplier = DatacenterRating.environmentMultiplier(getLevel(), getPos());
        this.recheckCountdown = 1;
        this.coolantStarveTicks = 0;
        this.evaluateRoomTier();
        this.settleTick();
        this.updateTickSubscription();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.maintenance = null;
        this.providing = false;
        this.derateReason = DerateReason.NO_BLADES;
        this.roomTier = 3;
        updateRenderState();
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
            this.tickSubs = subscribeServerTick(this.tickSubs, this::tick);
        } else if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Per-tick settlement //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void tick() {
        if (!isFormed()) {
            return;
        }
        if (--this.recheckCountdown <= 0) {
            this.evaluateRoomTier();
            this.recheckCountdown = ROOM_RECHECK_INTERVAL;
        }
        this.settleTick();
    }

    /**
     * The PUE settlement: draw {@code IT + cooling × env × airflow × coolant} when providing,
     * standby when idle, nothing when derated. Records the derate reason, the coolant in use and
     * the offered CWU/t.
     */
    protected void settleTick() {
        int blades = getBladeCount();
        boolean snack = isSnackPresent();
        DerateReason reason;
        long draw;
        boolean nowProviding = false;

        if (!recipeLogic.isWorkingEnabled()) {
            reason = DerateReason.PAUSED;
            draw = 0;
            this.coolantStarveTicks = 0;
        } else if (maintenance != null && maintenance.getNumMaintenanceProblems() > 0) {
            reason = DerateReason.MAINTENANCE;
            draw = 0;
            this.coolantStarveTicks = 0;
        } else if (blades == 0) {
            reason = DerateReason.NO_BLADES;
            draw = LiquidDatacenterRating.totalEUt(0, environmentMultiplier,
                    LiquidDatacenterRating.AIRFLOW_TIER_III, coolantEfficiency, snack);
            this.coolantStarveTicks = 0;
        } else {
            long total = LiquidDatacenterRating.totalEUt(blades, environmentMultiplier,
                    LiquidDatacenterRating.AIRFLOW_TIER_III, coolantEfficiency, snack);
            IEnergyContainer energy = getEnergyContainer();
            boolean powered = energy != null && energy.getEnergyStored() >= total;
            // Coolant next: a dry cold plate cannot hold a load no matter how much EU it has.
            boolean cooled = drainCoolant();
            if (!powered) {
                reason = DerateReason.LOW_POWER;
                draw = 0;
            } else if (!cooled) {
                // Grace window: a one-tick fluid gap must not flap the whole room to zero.
                this.coolantStarveTicks++;
                if (this.coolantStarveTicks >= LiquidDatacenterRating.COOLANT_STARVE_TICKS) {
                    reason = DerateReason.NO_COOLANT;
                    draw = 0;
                } else {
                    reason = DerateReason.NONE;
                    draw = total;
                    nowProviding = true;
                }
            } else {
                this.coolantStarveTicks = 0;
                reason = DerateReason.NONE;
                draw = total;
                nowProviding = true;
            }
        }

        if (draw > 0) {
            IEnergyContainer energy = getEnergyContainer();
            if (energy != null && energy.getEnergyStored() >= draw) {
                energy.removeEnergy(draw);
            }
        }

        if (this.derateReason != reason || this.providing != nowProviding) {
            this.derateReason = reason;
            this.providing = nowProviding;
            updateRenderState();
        }
        this.lastTotalEUt = draw > 0 ? draw : 0;
    }

    /**
     * Drinks one tick of cold-plate coolant out of the structure's fluid input hatches. The
     * coolant is a real energy-like input: a dry cold plate cannot hold a load.
     */
    protected boolean drainCoolant() {
        for (var part : getParts()) {
            if (!(part instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine hatch)) {
                continue;
            }
            var tank = hatch.tank;
            for (int slot = 0; slot < tank.getTanks(); slot++) {
                FluidStack stack = tank.getFluidInTank(slot);
                if (stack.isEmpty() || stack.getAmount() < LiquidDatacenterRating.COOLANT_PER_TICK) {
                    continue;
                }
                String id = stack.getFluid().builtInRegistryHolder().key().location().toString();
                double efficiency = LiquidDatacenterRating.coolantEfficiency(id);
                if (efficiency <= 0) {
                    continue;
                }
                // Write the shrunken stack back through the tank's own storage. Neither drain()
                // (capability-IO gated: an INPUT hatch is not a fluid sink) nor the fluid-id
                // based drainInternal() reliably reports a removal here, so the machine drinks
                // by setting the exact remainder on the storage it owns.
                FluidStack remain = stack.copy();
                remain.setAmount(stack.getAmount() - LiquidDatacenterRating.COOLANT_PER_TICK);
                if (remain.getAmount() <= 0) {
                    tank.getStorages()[slot].setFluid(FluidStack.EMPTY);
                } else {
                    tank.getStorages()[slot].setFluid(remain);
                }
                tank.onContentsChanged();
                this.coolantEfficiency = efficiency;
                this.coolantName = id;
                return true;
            }
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // IOpticalComputationProvider: strict same-tick budget //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        int available = availableCWUt();
        if (cwut <= 0 || available <= 0) {
            return 0;
        }
        long now = getOffsetTimer();
        if (this.budgetStamp < now) {
            this.budgetStamp = now;
            this.allocatedCWUt = 0;
        }
        int room = Math.max(0, available - this.allocatedCWUt);
        int granted = Math.min(cwut, room);
        if (!simulate && granted > 0) {
            this.allocatedCWUt += granted;
        }
        return granted;
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return availableCWUt();
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return true;
    }

    /** CWU/t the rack can offer right now (0 while derated; 0..64 otherwise). */
    public int availableCWUt() {
        return isFormed() && this.providing && this.derateReason == DerateReason.NONE ?
                LiquidDatacenterRating.maxCWUt(getBladeCount()) : 0;
    }

    public int getAllocatedCWUt() {
        return this.allocatedCWUt;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Machine room rating //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNeighborChanged(net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        if (isFormed() && !getLevel().isClientSide()) {
            evaluateRoomTier();
        }
    }

    protected BlockPos rel(int right, int up, int back) {
        Direction front = getFrontFacing();
        return getPos().relative(front.getClockWise(), right)
                .relative(Direction.UP, up)
                .relative(front.getOpposite(), back);
    }

    /**
     * A Tier III room is the rack's own cold-plate loop: it does not need a raised floor, open
     * air channels or a CRAC. The tier label reports III while the cold plates are fed and drops
     * to II while the coolant is dry (the room is only as good as its cooling).
     */
    protected void evaluateRoomTier() {
        int tier = this.coolantEfficiency > 0 ? 3 : 2;
        if (tier != this.roomTier) {
            this.roomTier = tier;
            updateRenderState();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accessors (UI + GameTests) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public int getBladeCount() {
        int blades = 0;
        for (int i = 0; i < bladeInventory.getSlots(); i++) {
            if (!bladeInventory.getStackInSlot(i).isEmpty()) {
                blades++;
            }
        }
        return blades;
    }

    public boolean isSnackPresent() {
        return !snackSlot.getStackInSlot(0).isEmpty();
    }

    public CustomItemStackHandler getBladeInventory() {
        return bladeInventory;
    }

    public CustomItemStackHandler getSnackSlot() {
        return snackSlot;
    }

    public DerateReason getDerateReason() {
        return derateReason;
    }

    public int getRoomTier() {
        return roomTier;
    }

    public double getEnvironmentMultiplier() {
        return environmentMultiplier;
    }

    public double getCoolantEfficiency() {
        return coolantEfficiency;
    }

    public String getCoolantName() {
        return coolantName;
    }

    /** Live PUE; -1 while idle. */
    public double getPUE() {
        return LiquidDatacenterRating.pue(getBladeCount(), environmentMultiplier,
                LiquidDatacenterRating.AIRFLOW_TIER_III, coolantEfficiency);
    }

    public long getTotalEUt() {
        return lastTotalEUt;
    }

    /** Switch bandwidth this rack may hand out per tick. */
    public int getSwitchBandwidthLimit() {
        return LiquidDatacenterRating.SWITCH_BANDWIDTH_LIMIT;
    }

    /**
     * VRAM carried by the blades — always 0 since 0.6.1. Kept as an explicit accessor so callers
     * (and tests) read "blades carry no memory" instead of guessing.
     */
    public int getVramCapacity() {
        return LiquidDatacenterRating.vramCapacity(getBladeCount());
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(bladeInventory);
        clearInventory(snackSlot);
    }

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = this.providing ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
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
        String uiKey = "hypercluster.machine.liquid_cooled_compute_rack.";
        int slotY = 13;
        WidgetGroup controls = MachineUi.multiblockControls(31);
        controls.addWidgets(
                MachineUi.label(11, 0, 96, uiKey + "label.blades"),
                MachineUi.label(123, 0, 58, uiKey + "label.snack"));
        for (int i = 0; i < LiquidDatacenterRating.BLADE_SLOTS; i++) {
            SlotWidget blade = new SlotWidget(bladeInventory, i, 17 + i * 22, slotY, true, true);
            blade.setBackground(GuiTextures.SLOT);
            blade.setHoverTooltips(Component.translatable(uiKey + "blade_slot"));
            controls.addWidget(blade);
        }
        SlotWidget snack = new SlotWidget(snackSlot, 0, 143, slotY, true, true);
        snack.setBackground(GuiTextures.SLOT);
        snack.setHoverTooltips(Component.translatable(uiKey + "snack_slot"));
        controls.addWidget(snack);
        return MachineUi.multiblockPage(this, controls);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), this.providing)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        "hypercluster.machine.liquid_cooled_compute_rack.providing")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    int blades = getBladeCount();
                    long it = LiquidDatacenterRating.itEUt(blades);
                    long cooling = LiquidDatacenterRating.coolingEUt(it, environmentMultiplier,
                            LiquidDatacenterRating.AIRFLOW_TIER_III, coolantEfficiency);
                    lines.add(Component.translatable(uiKey("computation"),
                            Component.literal(String.valueOf(availableCWUt())).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(LiquidDatacenterRating.maxCWUt(blades)))
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey("bandwidth"),
                            Component.literal(String.valueOf(getSwitchBandwidthLimit()))
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    // No VRAM line: blades carry 0 TB since 0.6.1. Cluster memory is a separate
                    // hardware budget reported by the VRAM storage rack and the MoE router.
                    lines.add(Component.translatable(uiKey("room"),
                            Component.literal(String.valueOf(roomTier)).withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.valueOf(getEnvironmentMultiplier() * 100) + "%")
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GRAY));
                    if (blades == 0) {
                        lines.add(Component.translatable(uiKey("standby"),
                                Component.literal(FormattingUtil.formatNumbers(lastTotalEUt) + " EU/t")
                                        .withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY));
                    } else {
                        lines.add(Component.translatable(uiKey("pue"),
                                Component.literal(String.format("%.2f", getPUE())).withStyle(ChatFormatting.AQUA),
                                Component.literal(FormattingUtil.formatNumbers(it)).withStyle(ChatFormatting.GRAY),
                                Component.literal(FormattingUtil.formatNumbers(cooling))
                                        .withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    lines.add(Component.translatable(uiKey("coolant"),
                            coolantName.isEmpty() ?
                                    Component.translatable(uiKey("coolant.none")).withStyle(ChatFormatting.RED) :
                                    Component.literal(coolantName).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    if (derateReason != DerateReason.NONE) {
                        lines.add(Component
                                .translatable(uiKey("derate." + derateReason.name().toLowerCase(java.util.Locale.ROOT)))
                                .withStyle(ChatFormatting.RED));
                    }
                    if (isSnackPresent() && DatacenterConfigs.snackPackBonus) {
                        lines.add(Component.translatable(uiKey("snack_bonus")).withStyle(ChatFormatting.GREEN));
                    }
                })
                .addWorkingStatusLine();
    }

    private static String uiKey(String suffix) {
        return "hypercluster.machine.liquid_cooled_compute_rack." + suffix;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Client-side WORKING ambience //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        Direction exhaust = getFrontFacing().getOpposite();
        VentParticles.emit(getLevel(), random, getPos().relative(exhaust), exhaust, 2.0, 3);
    }
}
