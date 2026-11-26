package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
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
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.common.item.HyperClusterItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 0.3 · Tier I fixed-structure air-cooled compute rack ({@code hypercluster:air_cooled_compute_rack}).
 *
 * <p>
 * A GT-style multiblock built from the intake/exhaust 42U casings, a standard energy hatch, a
 * maintenance hatch and GTCEu's native computation transmission hatch. No PCB coolant required.
 * Up to {@value DatacenterRating#BLADE_SLOTS} {@code hypercluster:iv_compute_blade} items live in
 * the GUI slots; each contributes {@value DatacenterRating#CWU_PER_BLADE} CWU/t (empty slots
 * contribute 0), so a full rack offers 24 CWU/t through {@link IOpticalComputationProvider} and
 * GT's optical pipes.
 *
 * <p>
 * <b>Strict same-tick CWU/t budget.</b> Real allocations are bounded by the remaining per-tick
 * budget shared by every consumer of the same tick (research stations, inference hatch, ...).
 * {@code requestCWUt(cwut, simulate=true)} only queries and never deducts. The budget window is
 * the machine's offset timer, so consumers ticking in any order share one coherent window and a
 * consumer can never double-dip into the previous tick's budget.
 *
 * <p>
 * <b>Derating.</b> Low power, unfixed maintenance problems, no blades or a paused rack zero the
 * offered computation and record the reason ({@link DerateReason}); a cut optical cable zeroes the
 * delivered CWU/t at the consumer side (nothing to allocate against).
 *
 * <p>
 * <b>Room rating / PUE.</b> The controller settles {@code 总耗电 = IT 耗电 + 基础制冷耗电 × 环境系数 ×
 * 气流系数} and displays {@code PUE = 总耗电 / IT 耗电} (standby draw when idle). Tier I defaults to
 * PUE 1.80; a raised-floor plenum under the whole footprint + open front/back air channels + a
 * running {@link PrecisionCracMachine} within {@value #CRAC_RANGE} blocks upgrades the room to
 * Tier II (airflow 0.5625, PUE 1.45). Snowy/frozen/deep-ocean biomes and Y &lt; 20 cut cooling power
 * by 40%; the Nether and hot deserts double it. Room checks are event-driven (structure changes,
 * CRAC state flips) plus a cheap bounded re-validation every {@value #ROOM_RECHECK_INTERVAL} ticks —
 * never a per-tick world scan.
 */
public class AirCooledComputeRackMachine extends WorkableElectricMultiblockMachine
                                         implements IOpticalComputationProvider,
                                         com.gregtechceu.gtceu.api.machine.feature.IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AirCooledComputeRackMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

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
        LOW_POWER
    }

    @Persisted
    protected final CustomItemStackHandler bladeInventory;
    @Persisted
    protected final CustomItemStackHandler snackSlot;

    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    // Same-tick CWU/t budget.
    private long budgetStamp = Long.MIN_VALUE;
    private int allocatedCWUt = 0;

    // Live settlement state.
    @Persisted
    protected int roomTier = 1;
    protected double environmentMultiplier = 1.0;
    protected DerateReason derateReason = DerateReason.NO_BLADES;
    protected boolean providing = false;
    protected long lastTotalEUt = DatacenterRating.STANDBY_EU_T;

    /** Cached nearby CRAC controllers (validated on re-check, rescanned on structure events). */
    protected final List<PrecisionCracMachine> nearbyCracs = new ArrayList<>();

    private int recheckCountdown = 1;

    public AirCooledComputeRackMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.bladeInventory = new CustomItemStackHandler(DatacenterRating.BLADE_SLOTS);
        this.bladeInventory.setFilter(
                stack -> stack.is(HyperClusterItems.IV_COMPUTE_BLADE.get()));
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
        this.rescanNearbyCracs();
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
        this.nearbyCracs.clear();
        this.roomTier = 1;
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
     * The PUE settlement: draw {@code IT + cooling × env × airflow} when providing, standby when
     * idle, nothing when derated. Records the derate reason and the offered CWU/t.
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
        } else if (maintenance != null && maintenance.getNumMaintenanceProblems() > 0) {
            reason = DerateReason.MAINTENANCE;
            draw = 0;
        } else if (blades == 0) {
            reason = DerateReason.NO_BLADES;
            draw = DatacenterRating.totalEUt(0, environmentMultiplier,
                    DatacenterRating.airflowMultiplier(roomTier), snack);
        } else {
            long total = DatacenterRating.totalEUt(blades, environmentMultiplier,
                    DatacenterRating.airflowMultiplier(roomTier), snack);
            IEnergyContainer energy = getEnergyContainer();
            if (energy != null && energy.getEnergyStored() >= total) {
                reason = DerateReason.NONE;
                draw = total;
                nowProviding = true;
            } else {
                reason = DerateReason.LOW_POWER;
                draw = 0;
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
            // First request of this tick: a fresh budget window for every consumer of the tick.
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

    /** CWU/t the rack can offer right now (0 while derated; 0..24 otherwise). */
    public int availableCWUt() {
        return isFormed() && this.providing && this.derateReason == DerateReason.NONE ?
                DatacenterRating.maxCWUt(getBladeCount()) : 0;
    }

    /** CWU/t already allocated in the current tick window (multi-consumer budget accounting). */
    public int getAllocatedCWUt() {
        return this.allocatedCWUt;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Machine room rating: Tier I -> Tier II //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNeighborChanged(net.minecraft.world.level.block.Block block, BlockPos fromPos,
                                  boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        // The raised-floor plenum, the front intake and the back exhaust channels are all
        // adjacent to the structure: a neighbor change is the cheap, event-driven trigger to
        // re-rate the room (no per-tick scans).
        if (isFormed() && !getLevel().isClientSide()) {
            evaluateRoomTier();
        }
    }

    /**
     * Position of a structure-relative cell: {@code right} along the horizontal clockwise axis of
     * the front facing, {@code up} along world up, {@code back} along the back of the rack.
     */
    protected BlockPos rel(int right, int up, int back) {
        Direction front = getFrontFacing();
        return getPos().relative(front.getClockWise(), right)
                .relative(Direction.UP, up)
                .relative(front.getOpposite(), back);
    }

    /** The 3×2 footprint of the rack one layer below its bottom row. */
    protected boolean isRaisedFloorLaid() {
        for (int right = -1; right <= 1; right++) {
            for (int back = 0; back <= 1; back++) {
                if (!getLevel().getBlockState(rel(right, -3, back))
                        .is(dev.hypercluster.common.data.HyperClusterBlocks.RAISED_FLOOR.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Front (intake) and back (exhaust) air channels must not be plugged solid. */
    protected boolean areChannelsOpen() {
        return !getLevel().getBlockState(rel(0, 0, -1)).isSolidRender(getLevel(), rel(0, 0, -1)) &&
                !getLevel().getBlockState(rel(0, 0, 2)).isSolidRender(getLevel(), rel(0, 0, 2));
    }

    /** True if at least one cached nearby CRAC is still formed and running. */
    protected boolean hasRunningCrac() {
        for (PrecisionCracMachine crac : nearbyCracs) {
            if (crac != null && crac.isFormed() && crac.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-evaluates the room tier from the current world state: raised floor + open channels + a
     * running nearby CRAC upgrades to Tier II (airflow 0.5625). Cheap: cached CRACs only.
     */
    protected void evaluateRoomTier() {
        this.nearbyCracs.removeIf(crac -> crac == null || crac.isInValid() || !crac.isFormed());
        int tier = isRaisedFloorLaid() && areChannelsOpen() && hasRunningCrac() ? 2 : 1;
        if (tier != this.roomTier) {
            this.roomTier = tier;
        }
    }

    /**
     * Full bounded rescan for CRAC controllers around the rack (structure-change triggered).
     * Called when the rack forms and when a nearby CRAC forms, invalidates or flips its running
     * state — never per tick.
     */
    public void rescanNearbyCracs() {
        nearbyCracs.clear();
        BlockPos min = rel(-1 - CRAC_RANGE, -2 - CRAC_RANGE, -CRAC_RANGE);
        BlockPos max = rel(1 + CRAC_RANGE, 2 + CRAC_RANGE, 1 + CRAC_RANGE);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof PrecisionCracMachine crac) {
                nearbyCracs.add(crac);
            }
        }
        evaluateRoomTier();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Rack inventory & state accessors (UI + GameTests) //
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

    /** Live PUE; -1 while idle (the UI shows the standby draw instead). */
    public double getPUE() {
        return DatacenterRating.pue(getBladeCount(), environmentMultiplier,
                DatacenterRating.airflowMultiplier(roomTier));
    }

    /** Current settled total power draw (EU/t); the standby line while idle. */
    public long getTotalEUt() {
        return lastTotalEUt;
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
        WidgetGroup group = (WidgetGroup) super.createUIWidget();
        int slotY = 20;
        for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
            SlotWidget blade = new SlotWidget(bladeInventory, i, 53 + i * 18, slotY, true, true);
            blade.setBackground(GuiTextures.SLOT);
            if (i == 0) {
                blade.setHoverTooltips(
                        Component.translatable("hypercluster.machine.air_cooled_compute_rack.blade_slot"));
            }
            group.addWidget(blade);
        }
        SlotWidget snack = new SlotWidget(snackSlot, 0, 53 + 2 * 18, slotY + 18, true, true);
        snack.setBackground(GuiTextures.SLOT);
        snack.setHoverTooltips(Component.translatable("hypercluster.machine.air_cooled_compute_rack.snack_slot"));
        group.addWidget(snack);
        return group;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), this.providing)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        "hypercluster.machine.air_cooled_compute_rack.providing")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    int blades = getBladeCount();
                    long it = DatacenterRating.itEUt(blades);
                    long cooling = DatacenterRating.coolingEUt(it, environmentMultiplier,
                            DatacenterRating.airflowMultiplier(roomTier));
                    lines.add(Component.translatable("hypercluster.machine.air_cooled_compute_rack.computation",
                            Component.literal(String.valueOf(availableCWUt())).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(DatacenterRating.maxCWUt(blades)))
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("hypercluster.machine.air_cooled_compute_rack.room",
                            Component.literal(String.valueOf(roomTier)).withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.valueOf(getEnvironmentMultiplier() * 100) + "%")
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GRAY));
                    if (blades == 0) {
                        lines.add(Component.translatable("hypercluster.machine.air_cooled_compute_rack.standby",
                                Component.literal(FormattingUtil.formatNumbers(lastTotalEUt) + " EU/t")
                                        .withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY));
                    } else {
                        double pue = getPUE();
                        lines.add(Component.translatable("hypercluster.machine.air_cooled_compute_rack.pue",
                                Component.literal(String.format("%.2f", pue)).withStyle(ChatFormatting.AQUA),
                                Component.literal(FormattingUtil.formatNumbers(it)).withStyle(ChatFormatting.GRAY),
                                Component.literal(FormattingUtil.formatNumbers(cooling))
                                        .withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    if (derateReason != DerateReason.NONE) {
                        lines.add(Component
                                .translatable("hypercluster.machine.air_cooled_compute_rack.derate." +
                                        derateReason.name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(ChatFormatting.RED));
                    }
                    if (isSnackPresent() && DatacenterConfigs.snackPackBonus) {
                        lines.add(Component.translatable("hypercluster.machine.air_cooled_compute_rack.snack_bonus")
                                .withStyle(ChatFormatting.GREEN));
                    }
                })
                .addWorkingStatusLine();
    }
}
