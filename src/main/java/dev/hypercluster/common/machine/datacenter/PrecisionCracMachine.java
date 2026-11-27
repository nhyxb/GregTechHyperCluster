package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 0.3 · Tier II precision CRAC ({@code hypercluster:precision_crac}).
 *
 * <p>
 * A compact IV multiblock blower unit. While formed and powered it draws {@value #CRAC_EU_T} EU/t
 * and counts as a "running precision CRAC" for every {@link AirCooledComputeRackMachine} within
 * {@link AirCooledComputeRackMachine#CRAC_RANGE} blocks — the third condition (besides the raised
 * floor plenum and open air channels) that upgrades a Tier I machine room to Tier II
 * (PUE 1.80 → 1.45).
 *
 * <p>
 * Running-state flips and structure changes notify nearby racks to rescan their bounded CRAC
 * cache — the room rating stays event-driven, with no per-tick world scans anywhere.
 */
public class PrecisionCracMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            PrecisionCracMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Blower power draw while running (EU/t). */
    public static final long CRAC_EU_T = 128;

    @Persisted
    @DescSynced
    protected boolean running = false;

    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    public PrecisionCracMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public boolean isRunning() {
        return isFormed() && running;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structure lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        updateTickSubscription();
        settleTick();
        notifyNearbyRacks();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (running) {
            running = false;
        }
        updateRenderState();
        notifyNearbyRacks();
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

    protected void tick() {
        if (!isFormed()) {
            return;
        }
        settleTick();
    }

    protected void settleTick() {
        boolean nowRunning = false;
        if (recipeLogic.isWorkingEnabled()) {
            IEnergyContainer energy = getEnergyContainer();
            if (energy != null && energy.getEnergyStored() >= CRAC_EU_T) {
                nowRunning = true;
            }
        }
        if (nowRunning) {
            IEnergyContainer energy = getEnergyContainer();
            if (energy != null) {
                energy.removeEnergy(CRAC_EU_T);
            }
        }
        if (nowRunning != running) {
            running = nowRunning;
            updateRenderState();
            notifyNearbyRacks();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Rack notification (event-driven room re-rating) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Asks every compute rack whose bounded scan box can see this CRAC to rescan. Called on
     * structure changes and running-state flips only — never per tick.
     */
    protected void notifyNearbyRacks() {
        notifyRacks();
    }

    private void notifyRacks() {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        Direction front = getFrontFacing();
        BlockPos min = getPos().relative(front.getClockWise(), -2).relative(Direction.UP, -2)
                .relative(front.getOpposite(), -2);
        BlockPos max = getPos().relative(front.getClockWise(), 2).relative(Direction.UP, 2)
                .relative(front.getOpposite(), 2);
        min = min.offset(-AirCooledComputeRackMachine.CRAC_RANGE - 4,
                -AirCooledComputeRackMachine.CRAC_RANGE - 4, -AirCooledComputeRackMachine.CRAC_RANGE - 4);
        max = max.offset(AirCooledComputeRackMachine.CRAC_RANGE + 4,
                AirCooledComputeRackMachine.CRAC_RANGE + 4, AirCooledComputeRackMachine.CRAC_RANGE + 4);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof AirCooledComputeRackMachine rack) {
                rack.rescanNearbyCracs();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Render state & UI //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = running ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), running)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        "hypercluster.machine.precision_crac.running")
                .addCustom(lines -> {
                    if (isFormed()) {
                        lines.add(Component.translatable("hypercluster.machine.precision_crac.power",
                                Component.literal(CRAC_EU_T + " EU/t").withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.GRAY));
                    }
                })
                .addWorkingStatusLine();
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
        VentParticles.emit(getLevel(), random, getPos(), getFrontFacing(), 1.15, 2);
    }
}
