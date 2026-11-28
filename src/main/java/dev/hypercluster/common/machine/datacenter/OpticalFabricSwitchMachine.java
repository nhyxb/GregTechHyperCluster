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
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.sound.MachineAlerts;

import java.util.Collection;
import java.util.List;

/**
 * 0.6.1 · optical fabric switch ({@code hypercluster:optical_fabric_switch}).
 *
 * <p>
 * A 5×4×3 backplane cabinet that turns a room full of separate racks into <b>one cluster pool</b>.
 * It aggregates racks on the same compliant raised floor and switched native optical inputs
 * into a single {@link IOpticalComputationProvider} with a
 * measurable hard ceiling:
 *
 * <ul>
 * <li><b>Bandwidth ceiling</b> {@value VramRating#SWITCH_BANDWIDTH_LIMIT} CWU/t. Demand inside the
 * ceiling is served from the members' own same-tick budgets, so no CWU/t is ever invented.</li>
 * <li><b>Over the ceiling → 503.</b> The switch refuses the whole tick, records
 * {@code 503 Server Busy} and stays busy for {@value VramRating#BUSY_COOLDOWN_TICKS} ticks
 * (15 s). Nothing is billed and no member budget is consumed during the cooldown.</li>
 * <li>Memory aggregation rides along: the connected rooms' {@link VramStorageRackMachine} cabinets
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
        /** No connected computation providers. */
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

    /** 0.6.3: stamp of the last terminal_alert cue, throttled by {@link MachineAlerts}. */
    protected long lastAlertTick = MachineAlerts.NEVER;

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

    public List<DatacenterOpticalNetwork.Link> memberProviders() {
        return DatacenterOpticalNetwork.providers(this, false);
    }

    public int aggregateAvailableCwu() {
        return getMaxCWUt();
    }

    public int aggregateMemoryTb() {
        return DatacenterOpticalNetwork.memoryRacks(this).stream()
                .mapToInt(VramStorageRackMachine::getTerabytesOnline).sum();
    }

    public boolean canForward() {
        return isFormed() && !isBusy() && (status == Status.SERVING || status == Status.NO_MEMBERS);
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (cwut <= 0 || !canForward()) {
            return 0;
        }
        long now = getOffsetTimer();
        if (this.budgetStamp < now) {
            this.budgetStamp = now;
            this.allocatedCWUt = 0;
            this.windowDemand = 0;
        }
        // Native GT recipe hatches use MAX_VALUE in simulation to query availability.
        boolean availabilityProbe = simulate && cwut == Integer.MAX_VALUE;
        if (!availabilityProbe && cwut > getBandwidthLimit() - this.windowDemand) {
            tripBusy();
            return 0;
        }
        int room = getBandwidthLimit() - this.allocatedCWUt;
        int granted = DatacenterOpticalNetwork.request(memberProviders(), Math.min(cwut, room), simulate, seen);
        if (!simulate && granted > 0) {
            this.allocatedCWUt += granted;
            this.windowDemand += cwut;
            this.lastServedCwu = granted;
        }
        return granted;
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (!canForward()) {
            return 0;
        }
        return Math.min(getBandwidthLimit(), DatacenterOpticalNetwork.capacity(memberProviders(), seen));
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
        if (this.cooldownTicks > 0 && --this.cooldownTicks > 0) {
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
            // 0.6.3: entering the 503 busy cooldown raises the terminal alert.
            if (next == Status.BUSY_503) {
                this.lastAlertTick = MachineAlerts.play(getLevel(), getPos(), this.lastAlertTick,
                        getOffsetTimer());
            }
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
