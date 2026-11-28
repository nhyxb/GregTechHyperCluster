package dev.hypercluster.common.machine.training;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.common.sound.MachineAlerts;

import java.util.List;

/**
 * 0.6 · UPS power distribution array ({@code hypercluster:ups_power_array}).
 *
 * <p>
 * A 5×4×3 industrial battery bank built from {@code hypercluster:ups_battery_casing} on an
 * exposed steel-frame skeleton, with an energy hatch and a maintenance hatch.
 *
 * <p>
 * <b>Float charge.</b> While the grid feeds it, the array tops itself up at
 * {@value TrainingRating#UPS_CHARGE_EU_T} EU/t into a {@value TrainingRating#UPS_CAPACITY_EU} EU
 * buffer. That buffer buys {@value TrainingRating#UPS_BUFFER_TICKS} ticks (60 s) of cover for
 * every {@link TensorPretrainingClusterMachine} within
 * {@value TrainingRating#UPS_RANGE} blocks.
 *
 * <p>
 * <b>Cover, not free power.</b> When a protected cluster loses grid power the array discharges
 * {@value TrainingRating#UPS_DISCHARGE_EU_T} EU/t into it so the run keeps stepping and its
 * checkpoints keep landing. Within the 60 s window a restored grid resumes the run from the most
 * recent checkpoint with no lost progress; once the buffer is dry the cluster loses its unsaved
 * progress (清零未保存进度). The array never generates EU — it only returns what it stored.
 */
public class UpsPowerArrayMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            UpsPowerArrayMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Stored EU in the battery bank (persisted: a reload must not empty the batteries). */
    @Persisted
    @DescSynced
    protected long storedEu = 0;

    /** True while the array is actually carrying a cluster through a brownout. */
    @Persisted
    @DescSynced
    protected boolean discharging = false;

    /** 0.6.3: stamp of the last terminal_alert cue, throttled by {@link MachineAlerts}. */
    protected long lastAlertTick = MachineAlerts.NEVER;

    /** Ticks the array has been discharging in the current brownout. */
    @Persisted
    @DescSynced
    protected int dischargeTicks = 0;

    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    public UpsPowerArrayMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accessors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public long getStoredEu() {
        return storedEu;
    }

    public boolean isDischarging() {
        return discharging;
    }

    public int getDischargeTicks() {
        return dischargeTicks;
    }

    /** Share of the buffer still available (0.0 – 1.0). */
    public double getChargeFraction() {
        return TrainingRating.UPS_CAPACITY_EU <= 0 ? 0.0 :
                Math.min(1.0, storedEu / (double) TrainingRating.UPS_CAPACITY_EU);
    }

    /** True while this array still has buffer left to carry a cluster. */
    public boolean canProtect() {
        return isFormed() && storedEu > 0;
    }

    /** Ticks of cover left at the discharge rate (bounded by the 60 s contract). */
    public int remainingCoverTicks() {
        if (storedEu <= 0) {
            return 0;
        }
        long ticks = storedEu / Math.max(1, TrainingRating.UPS_DISCHARGE_EU_T);
        return (int) Math.min(TrainingRating.UPS_BUFFER_TICKS, ticks);
    }

    /** Tops the buffer up (grid float charge, or a GameTest priming the bank). */
    public long charge(long eu) {
        if (eu <= 0) {
            return 0;
        }
        long room = TrainingRating.UPS_CAPACITY_EU - storedEu;
        long accepted = Math.min(room, eu);
        if (accepted > 0) {
            storedEu += accepted;
        }
        return accepted;
    }

    /**
     * Draws backup power for a protected cluster. Returns the EU actually delivered (0 when the
     * bank is dry, in which case the cluster must fall back to its checkpoint).
     */
    public long discharge(long requested) {
        if (requested <= 0 || storedEu <= 0) {
            return 0;
        }
        long delivered = Math.min(Math.min(requested, TrainingRating.UPS_DISCHARGE_EU_T), storedEu);
        storedEu -= delivered;
        if (delivered > 0) {
            // 0.6.3: the first delivered joule of a brownout is the switch to battery cover.
            if (!discharging) {
                this.lastAlertTick = MachineAlerts.play(getLevel(), getPos(), this.lastAlertTick,
                        getOffsetTimer());
            }
            discharging = true;
        }
        return delivered;
    }

    /** Test hook: empties the bank atomically (the gametest needs a deterministic drained state). */
    public void dishargeAllForTest() {
        this.storedEu = 0;
        this.discharging = false;
        this.dischargeTicks = 0;
    }

    /** Called when the protected grid comes back: the array stops carrying the load. */
    public void notifyGridRestored() {
        if (discharging) {
            discharging = false;
        }
        dischargeTicks = 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structure lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        updateTickSubscription();
        notifyNearbyClusters(true);
        updateRenderState();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        discharging = false;
        dischargeTicks = 0;
        notifyNearbyClusters(false);
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
    // Per-tick float charge //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void tick() {
        if (!isFormed()) {
            return;
        }
        if (recipeLogic.isWorkingEnabled()) {
            IEnergyContainer energy = getEnergyContainer();
            if (energy != null && storedEu < TrainingRating.UPS_CAPACITY_EU) {
                long headroom = TrainingRating.UPS_CAPACITY_EU - storedEu;
                long accepted = charge(Math.min(TrainingRating.UPS_CHARGE_EU_T, headroom));
                if (accepted > 0) {
                    energy.removeEnergy(accepted);
                }
            }
        }
        if (discharging) {
            dischargeTicks++;
        }
        updateRenderState();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Protected-cluster notification (event-driven, never a per-tick scan) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Tells every cluster in range whether this array can cover it. */
    protected void notifyNearbyClusters(boolean available) {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        for (TensorPretrainingClusterMachine cluster : nearbyClusters()) {
            cluster.rescanUpsCover();
        }
    }

    /** Bounded scan for protected clusters (structure-change triggered). */
    public java.util.List<TensorPretrainingClusterMachine> nearbyClusters() {
        java.util.List<TensorPretrainingClusterMachine> found = new java.util.ArrayList<>();
        if (getLevel() == null || getLevel().isClientSide()) {
            return found;
        }
        int r = TrainingRating.UPS_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof TensorPretrainingClusterMachine cluster) {
                found.add(cluster);
            }
        }
        return found;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Render state, UI, ambience //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = discharging ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        String uiKey = "hypercluster.machine.ups_power_array.";
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), discharging)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        uiKey + "discharging")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    lines.add(Component.translatable(uiKey + "charge",
                            Component.literal(String.format("%.1f%%", getChargeFraction() * 100))
                                    .withStyle(ChatFormatting.GREEN),
                            Component.literal(FormattingUtil.formatNumbers(storedEu))
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "cover",
                            Component.literal((remainingCoverTicks() / 20) + " s")
                                    .withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.valueOf(TrainingRating.UPS_BUFFER_TICKS / 20) + " s")
                                    .withStyle(ChatFormatting.GRAY))
                            .withStyle(ChatFormatting.GRAY));
                })
                .addWorkingStatusLine();
    }

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        dev.hypercluster.common.machine.datacenter.VentParticles.emit(getLevel(), random, getPos(),
                getFrontFacing(), 1.0, 2);
    }
}
