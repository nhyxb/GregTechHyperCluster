package dev.hypercluster.common.machine.training;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.OpenWeightModuleItem;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.MultiblockItemBuses;
import dev.hypercluster.common.machine.datacenter.DatacenterFloorNetwork;
import dev.hypercluster.common.machine.datacenter.DatacenterOpticalNetwork;
import dev.hypercluster.common.machine.datacenter.LiquidDatacenterRating;
import dev.hypercluster.common.machine.datacenter.VramStorageRackMachine;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * 0.6 · tensor pretraining cluster ({@code hypercluster:tensor_pretraining_cluster}).
 *
 * <p>
 * A 7×6×7 stepped computing cluster: a heavy casing base, a waisted mid-section with large
 * see-through glass core windows around a visible compute core column, and a heat-dissipation
 * ring beam on top; the controller sits on the <b>second layer front</b> for comfortable
 * eye-height access.
 *
 * <p>
 * <b>The run contract</b> (see {@link TrainingRating}):
 *
 * <ul>
 * <li><b>Inputs.</b> LuV power ({@value TrainingRating#TRAINING_EU_T} EU/t), a live
 * {@value TrainingRating#TRAINING_CWUT} CWU/t through the room floor or an optical pipe, distilled water coolant
 * ({@value TrainingRating#COOLANT_PER_TICK} mB/t) and one high-purity domain dataset in the
 * item import bus. A run takes {@value TrainingRating#BASE_TRAINING_TICKS} ticks.</li>
 * <li><b>VRAM gate (OOM).</b> The job declares a VRAM requirement; a cluster whose host racks
 * bring less than that online <b>refuses to start</b> with an explicit OOM reason instead of
 * degrading.</li>
 * <li><b>Checkpoints.</b> Every {@value TrainingRating#CHECKPOINT_EVERY_PERCENT}% of progress a
 * checkpoint lands in NBT, so a reload resumes from it.</li>
 * <li><b>UPS cover.</b> A running {@link UpsPowerArrayMachine} in range lends
 * {@value TrainingRating#UPS_DISCHARGE_EU_T} EU/t when the grid browns out. Inside the 60 s
 * window the run survives and resumes from its last checkpoint; when no UPS covers the loss —
 * or the buffer runs dry — the unsaved progress is cleared.</li>
 * <li><b>Export.</b> A completed run mints one {@code hypercluster:open_weight_module} carrying
 * the domain, precision and training version in the item export bus.</li>
 * </ul>
 *
 * <p>
 * <b>No double-dip.</b> The CWU/t a run pulls is a real allocation against the host rack's
 * same-tick budget, so an offload node renting the same rack in the same tick cannot rent the
 * same computation twice.
 */
public class TensorPretrainingClusterMachine extends WorkableElectricMultiblockMachine
                                             implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TensorPretrainingClusterMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Why a run cannot start (or why the active run stalled). */
    public enum RunState {
        IDLE,
        NO_DATASET,
        NO_COMPUTATION,
        OOM,
        NO_COOLANT,
        LOW_POWER,
        NO_POWER_CHECKPOINT_LOST,
        TRAINING,
        OUTPUT_BLOCKED,
        PAUSED
    }

    /**
     * VRAM a training job needs (TB) since 0.6.1: the cluster memory pool must hold 96 TB, i.e.
     * two full LuV memory racks (4 × 16 TB each).
     */
    public static final int JOB_VRAM = dev.hypercluster.common.machine.datacenter.VramRating.TRAINING_JOB_TB;

    // Old controller input is drained first when upgrading existing worlds.
    @Persisted
    protected final CustomItemStackHandler datasetSlot;

    // Persisted run state (the checkpoint contract lives here).
    @Persisted
    @DescSynced
    protected int progressTicks = 0;
    @Persisted
    @DescSynced
    protected int totalTicks = TrainingRating.BASE_TRAINING_TICKS;
    @Persisted
    @DescSynced
    protected int checkpointPercent = 0;
    @Persisted
    @DescSynced
    protected boolean running = false;
    @Persisted
    protected boolean outputPending;
    @Persisted
    @DescSynced
    protected String domain = "";
    @Persisted
    @DescSynced
    protected int completedRuns = 0;
    /** True while the run is being carried by UPS cover instead of the grid. */
    @Persisted
    @DescSynced
    protected boolean onBackupPower = false;

    @DescSynced
    protected RunState state = RunState.IDLE;
    @DescSynced
    protected double progressPercent = 0;

    protected int jobVram = JOB_VRAM;
    @javax.annotation.Nullable
    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    private int recheckCountdown = 1;

    public TensorPretrainingClusterMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.datasetSlot = new CustomItemStackHandler(1);
        this.datasetSlot.setFilter(stack -> stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ||
                stack.is(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accessors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public RunState getState() {
        return state;
    }

    public boolean isRunning() {
        return running;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getCheckpointPercent() {
        return checkpointPercent;
    }

    public double getProgressPercent() {
        return running && totalTicks > 0 ? Math.min(100.0, progressTicks * 100.0 / totalTicks) : progressPercent;
    }

    public String getDomain() {
        return domain;
    }

    public int getCompletedRuns() {
        return completedRuns;
    }

    public boolean isOnBackupPower() {
        return onBackupPower;
    }

    public int getJobVram() {
        return jobVram;
    }

    public void setJobVram(int vram) {
        this.jobVram = Math.max(0, vram);
    }

    public boolean hasUpsCover() {
        return findCoveringUps() != null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structure lifecycle //
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
        this.recheckCountdown = 1;
        updateTickSubscription();
        updateRenderState();
    }

    @Override
    public void onStructureInvalid() {
        DatacenterFloorNetwork.detached(this);
        super.onStructureInvalid();
        this.maintenance = null;
        if (running && !outputPending) {
            failRun(false); // a broken structure loses the unsaved progress, checkpoints survive
        }
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
        DatacenterFloorNetwork.detached(this);
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
    // Every-tick contract //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void tick() {
        if (!isFormed() || getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        if (--this.recheckCountdown <= 0) {
            rescanUpsCover();
            this.recheckCountdown = 40;
        }
        if (outputPending) {
            finishRun();
        } else if (running) {
            stepRun();
        } else {
            tryStartRun();
        }
        updateRenderState();
    }

    /**
     * The start gate, in order: pause, dataset, maintenance, VRAM (OOM), coolant, CWU/t, power.
     * A gate that fails records why and starts nothing — never a degraded silent run.
     */
    protected void tryStartRun() {
        if (!recipeLogic.isWorkingEnabled()) {
            state = RunState.PAUSED;
            return;
        }
        ItemStack dataset = dataset(true);
        if (dataset.isEmpty()) {
            state = RunState.NO_DATASET;
            return;
        }
        if (maintenance != null && maintenance.getNumMaintenanceProblems() > 0) {
            state = RunState.LOW_POWER;
            return;
        }
        // VRAM gate: an oversized job refuses to start (OOM) instead of degrading silently.
        if (!LiquidDatacenterRating.fitsVram(hostVramCapacity(), jobVram)) {
            state = RunState.OOM;
            return;
        }
        if (!hasCoolant()) {
            state = RunState.NO_COOLANT;
            return;
        }
        if (!hasComputation()) {
            state = RunState.NO_COMPUTATION;
            return;
        }
        if (!hasPower(TrainingRating.TRAINING_EU_T)) {
            state = RunState.LOW_POWER;
            return;
        }
        this.domain = dataset.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ?
                TrainingRating.DOMAIN_MINING : TrainingRating.DOMAIN_CHEMICAL;
        // Resume from the persisted checkpoint instead of restarting a half-done run.
        this.progressTicks = savedProgressTicks();
        this.running = true;
        this.onBackupPower = false;
        this.state = RunState.TRAINING;
        dataset(false);
        updateRenderState();
    }

    /** Ticks already covered by the persisted checkpoint (0 for a fresh run). */
    protected int savedProgressTicks() {
        return totalTicks - TrainingRating.remainingTicks(totalTicks, checkpointPercent);
    }

    /** One training tick: pay, verify the feed, step, checkpoint on the milestone, maybe finish. */
    protected void stepRun() {
        if (!recipeLogic.isWorkingEnabled()) {
            state = RunState.PAUSED;
            return;
        }
        if (!payPower()) {
            failRun(true);
            return;
        }
        if (!hasComputation() || !hasCoolant() ||
                DatacenterOpticalNetwork.request(this, TrainingRating.TRAINING_CWUT, false) <
                        TrainingRating.TRAINING_CWUT ||
                !drainCoolant()) {
            failRun(true);
            return;
        }
        int before = progressTicks;
        progressTicks++;
        this.progressPercent = Math.min(100.0, progressTicks * 100.0 / totalTicks);
        if (TrainingRating.crossesCheckpoint(before, progressTicks, totalTicks)) {
            this.checkpointPercent = TrainingRating.checkpointPercent(progressTicks, totalTicks);
        }
        if (progressTicks >= totalTicks) {
            this.outputPending = true;
            finishRun();
            return;
        }
        this.state = RunState.TRAINING;
    }

    /** Grid power first, UPS cover second; sets {@link #onBackupPower}. */
    protected boolean payPower() {
        IEnergyContainer energy = getEnergyContainer();
        if (energy != null && energy.getEnergyStored() >= TrainingRating.TRAINING_EU_T) {
            energy.removeEnergy(TrainingRating.TRAINING_EU_T);
            if (onBackupPower) {
                onBackupPower = false;
                UpsPowerArrayMachine ups = findCoveringUps();
                if (ups != null) {
                    ups.notifyGridRestored();
                }
            }
            return true;
        }
        UpsPowerArrayMachine ups = findCoveringUps();
        if (ups != null && ups.discharge(TrainingRating.TRAINING_EU_T) >= TrainingRating.TRAINING_EU_T) {
            onBackupPower = true;
            return true;
        }
        return false;
    }

    /**
     * A fault ends the running tick sequence. The run always rolls back to its last checkpoint
     * (that is the whole point of the checkpoint contract); what differs is whether the run keeps
     * going. With a UPS still covering the cluster the run survives on battery; without cover it
     * stops and must be restarted, which resumes from the same checkpoint.
     */
    protected void failRun(boolean allowRescue) {
        this.progressTicks = savedProgressTicks();
        this.progressPercent = Math.min(100.0, progressTicks * 100.0 / totalTicks);
        if (allowRescue && findCoveringUps() != null) {
            this.onBackupPower = true;
            this.state = RunState.TRAINING; // still stepping, carried by the battery bank
            return;
        }
        this.onBackupPower = false;
        this.running = false;
        this.state = RunState.NO_POWER_CHECKPOINT_LOST;
        updateRenderState();
    }

    /** Export the completed checkpoint exactly once. A full bus leaves the checkpoint intact. */
    protected void finishRun() {
        ItemStack module = OpenWeightModuleItem.create(
                new ItemStack(HyperClusterItems.OPEN_WEIGHT_MODULE.get()), domain,
                TrainingRating.PRECISION, TrainingRating.moduleVersion(100), completedRuns + 1);
        if (!MultiblockItemBuses.export(this, module, false)) {
            this.state = RunState.OUTPUT_BLOCKED;
            return;
        }
        this.completedRuns++;
        this.outputPending = false;
        this.running = false;
        this.progressTicks = 0;
        this.checkpointPercent = 0;
        this.progressPercent = 0;
        this.onBackupPower = false;
        this.state = RunState.IDLE;
        updateRenderState();
    }

    private ItemStack dataset(boolean simulate) {
        if (!datasetSlot.getStackInSlot(0).isEmpty()) {
            return datasetSlot.extractItem(0, 1, simulate);
        }
        return MultiblockItemBuses.take(this,
                stack -> stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ||
                        stack.is(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()),
                1, simulate);
    }

    public int hostVramCapacity() {
        return DatacenterOpticalNetwork.memoryRacks(this).stream()
                .mapToInt(VramStorageRackMachine::getTerabytesOnline).sum();
    }

    protected boolean hasComputation() {
        return DatacenterOpticalNetwork.request(this, TrainingRating.TRAINING_CWUT, true) >=
                TrainingRating.TRAINING_CWUT;
    }

    protected boolean hasCoolant() {
        for (var part : getParts()) {
            if (part instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine hatch) {
                for (int slot = 0; slot < hatch.tank.getTanks(); slot++) {
                    var fluid = hatch.tank.getFluidInTank(slot);
                    if (!fluid.isEmpty() &&
                            fluid.getAmount() >= TrainingRating.COOLANT_PER_TICK &&
                            LiquidDatacenterRating.coolantEfficiency(
                                    fluid.getFluid().builtInRegistryHolder().key().location().toString()) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean drainCoolant() {
        for (var part : getParts()) {
            if (part instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine hatch) {
                for (int slot = 0; slot < hatch.tank.getTanks(); slot++) {
                    var fluid = hatch.tank.getFluidInTank(slot);
                    if (fluid.isEmpty() || fluid.getAmount() < TrainingRating.COOLANT_PER_TICK) {
                        continue;
                    }
                    if (LiquidDatacenterRating.coolantEfficiency(
                            fluid.getFluid().builtInRegistryHolder().key().location().toString()) <= 0) {
                        continue;
                    }
                    // drainInternal(stack, ...): the machine drinks from its own INPUT hatch, which
                    // is not a capability-level fluid sink (see LiquidCooledComputeRackMachine).
                    var drained = hatch.tank.drainInternal(
                            new net.minecraftforge.fluids.FluidStack(fluid.getFluid(),
                                    TrainingRating.COOLANT_PER_TICK),
                            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean hasPower(long eut) {
        IEnergyContainer energy = getEnergyContainer();
        return energy != null && energy.getEnergyStored() >= eut;
    }

    /** The nearest UPS array that can still cover a brownout, or null. */
    @Nullable
    public UpsPowerArrayMachine findCoveringUps() {
        if (getLevel() == null) {
            return null;
        }
        int r = TrainingRating.UPS_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof UpsPowerArrayMachine ups && ups.canProtect()) {
                return ups;
            }
        }
        return null;
    }

    /** Re-evaluates UPS cover (called by a UPS array on structure/state flips). */
    public void rescanUpsCover() {
        if (!running || outputPending) {
            return;
        }
        if (onBackupPower && findCoveringUps() == null) {
            failRun(false); // the batteries ran dry mid-brownout
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Render state, UI, ambience //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = outputPending ? RecipeLogic.Status.WAITING :
                running ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    @Override
    public Widget createUIWidget() {
        return MachineUi.multiblockPage(this, MachineUi.multiblockControls(1));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        String uiKey = "hypercluster.machine.tensor_pretraining_cluster.";
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), running && !outputPending)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        uiKey + "training")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    lines.add(Component.translatable(uiKey + "progress",
                            Component.literal(String.format(Locale.ROOT, "%.1f%%", getProgressPercent()))
                                    .withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(getCheckpointPercent()))
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "vram",
                            Component.literal(String.valueOf(hostVramCapacity())).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(jobVram)).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "ups",
                            Component.translatable(uiKey + (hasUpsCover() ? "ups.covered" : "ups.bare"))
                                    .withStyle(hasUpsCover() ? ChatFormatting.GREEN : ChatFormatting.RED))
                            .withStyle(ChatFormatting.GRAY));
                    if (state != RunState.TRAINING && state != RunState.IDLE) {
                        lines.add(Component.translatable(uiKey + "state." + state.name().toLowerCase(Locale.ROOT))
                                .withStyle(ChatFormatting.RED));
                    }
                    if (onBackupPower) {
                        lines.add(Component.translatable(uiKey + "backup").withStyle(ChatFormatting.GOLD));
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
        Direction back = getFrontFacing().getOpposite();
        for (int i = 0; i < 3; i++) {
            BlockPos cell = getPos().above(2 + random.nextInt(3)).relative(back, 3);
            double x = cell.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = cell.getY() + random.nextDouble();
            double z = cell.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            getLevel().addParticle(random.nextBoolean() ? net.minecraft.core.particles.ParticleTypes.END_ROD :
                    net.minecraft.core.particles.ParticleTypes.SCRAPE,
                    x, y, z, 0.0, 0.08 + random.nextDouble() * 0.05, 0.0);
        }
    }
}
