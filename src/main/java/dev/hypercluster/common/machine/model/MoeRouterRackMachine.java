package dev.hypercluster.common.machine.model;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
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
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.datacenter.AirCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.IvInferenceHatchMachine;
import dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.OpticalFabricSwitchMachine;
import dev.hypercluster.common.machine.datacenter.VramRating;
import dev.hypercluster.common.machine.datacenter.VramStorageRackMachine;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;
import dev.hypercluster.common.model.ModelRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * 0.6.1 · MoE routing control cabinet ({@code hypercluster:moe_router_rack}).
 *
 * <p>
 * The open-camp deployment hub: a 5×5×5 stepped cluster that hosts open-weight models inside the
 * cluster's VRAM pool. One deployment slot takes an {@code open_weight_module}; the cabinet then
 * asks the pool for that model's VRAM (plus any LoRA cards and DeepThink reserve) and either:
 *
 * <ul>
 * <li><b>loads it</b> — {@link State#ONLINE}, resident in memory, consuming CWU/t every tick;</li>
 * <li><b>refuses with OOM</b> — not enough TB online, the load is rejected and nothing is
 * consumed.</li>
 * </ul>
 *
 * <p>
 * <b>Pulling the card or losing the memory cabinet drops the model immediately</b> (下线): the
 * tick loop re-checks the pool every tick, so a pulled module or a dark memory rack takes the model
 * offline on the very next tick.
 *
 * <p>
 * <b>Qwen LoRA extension.</b> Models carrying {@link AIModelDefinition.Trait#LORA_SLOTS} expose
 * {@value #LORA_SLOTS} LoRA slots; the 0.2.1 mining/chemical LoRA cards ride there, each
 * costing extra VRAM and CWU/t and stacking its own by-product specialisation.
 *
 * <p>
 * <b>DeepThink.</b> Models carrying {@link AIModelDefinition.Trait#DEEP_THINK} get a charge
 * switch; while charged they reserve extra VRAM and force probability by-products to 100%.
 */
public class MoeRouterRackMachine extends WorkableElectricMultiblockMachine implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MoeRouterRackMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** LoRA extension slots the router exposes for LoRA-capable models. */
    public static final int LORA_SLOTS = 4;

    /** Chebyshev reach (blocks) for the memory pool and compute providers. */
    public static final int POOL_RANGE = 16;

    /** Ticks an agent-parked machine room stays down before probing its rails again. */
    public static final int AGENT_PARK_TICKS = 40;

    /** Ticks the rails get to prove they can work after a probe resumes them. */
    public static final int AGENT_PROBE_TICKS = 20;

    /** Deployment state of the router. */
    public enum State {
        /** No weights in the deployment slot. */
        EMPTY,
        /** Weights present but the model is not resident yet. */
        LOADING,
        /** Model resident in cluster memory. */
        ONLINE,
        /** Not enough cluster VRAM to load the model. */
        OOM,
        /** The memory pool or compute feed went away while loaded. */
        EVICTED,
        /** Paused with a soft mallet. */
        PAUSED,
        /** Maintenance problems unfixed. */
        MAINTENANCE,
        /**
         * 0.6.2 · GLM-2333 Flash's room-inspection protective sleep: the room browned out below
         * {@value dev.hypercluster.common.model.ModelRuntime#BROWNOUT_FRACTION} of its stored
         * energy, so the router parks the model and draws nothing until power returns.
         */
        SLEEPING,
        /**
         * 0.6.2 · GLM-2333 Max's agent tool calling parked every associated multiblock on a jam or
         * a starvation, so the room idles on standby instead of burning EU.
         */
        AGENT_IDLE
    }

    @Persisted
    protected final CustomItemStackHandler modelSlot;
    @Persisted
    protected final CustomItemStackHandler loraSlot;

    @javax.annotation.Nullable
    protected IMaintenanceMachine maintenance;
    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    /** Loaded model identity, persisted so a reload re-validates against the pool. */
    @Persisted
    @DescSynced
    protected String loadedModelId = "";
    @Persisted
    @DescSynced
    protected State state = State.EMPTY;
    @Persisted
    @DescSynced
    protected boolean deepThink = false;
    @Persisted
    @DescSynced
    protected int lastVramTb = 0;
    @Persisted
    @DescSynced
    protected int lastCwu = 0;
    /**
     * 0.6.2 · CWU/t the router really pulled from the room this tick. Zero unless the model is
     * ONLINE: a sleeping, agent-idling or OOM pool bills nothing.
     */
    @Persisted
    @DescSynced
    protected int consumedCwu = 0;
    /** True while GLM-2333 Max's agent has parked the associated multiblocks (jam/starvation). */
    @Persisted
    @DescSynced
    protected boolean agentParked = false;
    /** True while a DeepSeek-Pro DeepThink round was settled with by-products forced to 100%. */
    @Persisted
    @DescSynced
    protected boolean byproductsForced = false;
    /** Countdown until an agent-parked room probes its rails again. */
    @Persisted
    @DescSynced
    protected int agentParkCountdown = 0;
    /** Ticks remaining in the current probe window (do not re-park while it runs). */
    @Persisted
    @DescSynced
    protected int agentProbeHoldoff = 0;
    /** How many associated inference hatches the LoRA projection reached on the last tick. */
    @DescSynced
    protected int projectedLoras = 0;
    /** How many associated inference hatches the DeepThink projection reached on the last tick. */
    @DescSynced
    protected int projectedForce = 0;

    public MoeRouterRackMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.modelSlot = new CustomItemStackHandler(1);
        this.modelSlot.setFilter(stack -> stack.is(HyperClusterItems.OPEN_WEIGHT_MODULE.get()));
        this.loraSlot = new CustomItemStackHandler(LORA_SLOTS);
        this.loraSlot.setFilter(stack -> stack.is(HyperClusterItems.MINING_LORA_CARD.get()) ||
                stack.is(HyperClusterItems.CHEMICAL_LORA_CARD.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accessors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public State getState() {
        return state;
    }

    @Nullable
    public AIModelDefinition loadedModel() {
        if (loadedModelId.isEmpty()) {
            return null;
        }
        // loadedModelId is the FULL id string (hypercluster:x), so the namespaced lookup is right
        // here — but a bare path from an older save must still resolve.
        AIModelDefinition byFullId = AIModels.catalog().get(loadedModelId);
        if (byFullId != null) {
            return byFullId;
        }
        for (AIModelDefinition definition : AIModels.catalog().all()) {
            if (definition.id().getPath().equals(loadedModelId)) {
                return definition;
            }
        }
        return null;
    }

    public String getLoadedModelId() {
        return loadedModelId;
    }

    public boolean isDeepThink() {
        return deepThink;
    }

    public void setDeepThink(boolean value) {
        this.deepThink = value;
    }

    public int getLastVramTb() {
        return lastVramTb;
    }

    public int getLastCwu() {
        return lastCwu;
    }

    public int getConsumedCwu() {
        return consumedCwu;
    }

    public boolean isAgentParked() {
        return agentParked;
    }

    public int getAgentParkCountdown() {
        return agentParkCountdown;
    }

    public int getAgentProbeHoldoff() {
        return agentProbeHoldoff;
    }

    public boolean isByproductsForced() {
        return byproductsForced;
    }

    public int getProjectedLoras() {
        return projectedLoras;
    }

    public int getProjectedForce() {
        return projectedForce;
    }

    /** True while a room-inspection model has parked the rack in protective sleep. */
    public boolean isSleeping() {
        return state == State.SLEEPING;
    }

    /** The blackout fraction of this rack's own energy buffer (0..1; 0 when it has no buffer). */
    public double brownoutFraction() {
        var energy = getEnergyContainer();
        if (energy == null || energy.getEnergyCapacity() <= 0) {
            return 0;
        }
        return (double) energy.getEnergyStored() / (double) energy.getEnergyCapacity();
    }

    /** True when the rack is below the protective-sleep threshold right now. */
    public boolean isBrownedOut() {
        var energy = getEnergyContainer();
        if (energy == null) {
            return false;
        }
        return ModelRuntime.isBrownout(energy.getEnergyStored(), energy.getEnergyCapacity());
    }

    public CustomItemStackHandler getModelSlot() {
        return modelSlot;
    }

    public CustomItemStackHandler getLoraSlot() {
        return loraSlot;
    }

    /** LoRA cards currently inserted. */
    public int getLoraCount() {
        int count = 0;
        for (int i = 0; i < loraSlot.getSlots(); i++) {
            if (!loraSlot.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Cluster pool //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Cluster VRAM pool (TB). An optical switch in range publishes the whole room's memory;
     * without a switch the router sums the memory cabinets directly.
     */
    public int poolTerabytes() {
        OpticalFabricSwitchMachine fabric = nearbySwitch();
        if (fabric != null) {
            return fabric.aggregateMemoryTb();
        }
        int total = 0;
        for (VramStorageRackMachine rack : nearbyMemoryRacks()) {
            total += rack.getTerabytesOnline();
        }
        return total;
    }

    /** Aggregate CWU/t the compute racks in range can offer. */
    public int poolCwu() {
        OpticalFabricSwitchMachine fabric = nearbySwitch();
        if (fabric != null) {
            return Math.min(VramRating.SWITCH_BANDWIDTH_LIMIT, fabric.aggregateAvailableCwu());
        }
        int total = 0;
        for (AirCooledComputeRackMachine rack : nearbyAirRacks()) {
            total += rack.availableCWUt();
        }
        for (LiquidCooledComputeRackMachine rack : nearbyLiquidRacks()) {
            total += rack.availableCWUt();
        }
        return total;
    }

    @Nullable
    private OpticalFabricSwitchMachine nearbySwitch() {
        if (getLevel() == null) {
            return null;
        }
        int r = POOL_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof OpticalFabricSwitchMachine fabric && fabric.isFormed()) {
                return fabric;
            }
        }
        return null;
    }

    public List<VramStorageRackMachine> nearbyMemoryRacks() {
        List<VramStorageRackMachine> found = new ArrayList<>();
        if (getLevel() == null) {
            return found;
        }
        int r = POOL_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof VramStorageRackMachine rack && rack.isFormed()) {
                found.add(rack);
            }
        }
        return found;
    }

    private List<AirCooledComputeRackMachine> nearbyAirRacks() {
        List<AirCooledComputeRackMachine> found = new ArrayList<>();
        if (getLevel() == null) {
            return found;
        }
        int r = POOL_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof AirCooledComputeRackMachine rack && rack.isFormed()) {
                found.add(rack);
            }
        }
        return found;
    }

    private List<LiquidCooledComputeRackMachine> nearbyLiquidRacks() {
        List<LiquidCooledComputeRackMachine> found = new ArrayList<>();
        if (getLevel() == null) {
            return found;
        }
        int r = POOL_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof LiquidCooledComputeRackMachine rack && rack.isFormed()) {
                found.add(rack);
            }
        }
        return found;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Deployment //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The model definition named by the weights in the deployment slot, or null.
     *
     * <p>
     * 0.6.2 resolves the <b>exact</b> {@code hc_model_id} the module carries, so all five
     * open-weight families deploy independently. A module minted before 0.6.2 has no such key and
     * reads back as {@code qwen_9187_pro_max_ultra} (the only identity 0.6.1 could produce), which
     * keeps old weights working. A module naming an id that is not a deployable open model is
     * rejected outright rather than being silently coerced into another family.
     */
    @Nullable
    public AIModelDefinition modelInSlot() {
        ItemStack stack = modelSlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        AIModelDefinition definition = dev.hypercluster.common.item.OpenWeightModuleItem.definition(stack);
        if (definition == null || !definition.isLocal()) {
            return null;
        }
        return definition;
    }

    /** VRAM the current deployment would need, including LoRAs and DeepThink. */
    public int requiredTerabytes() {
        AIModelDefinition model = modelInSlot();
        if (model == null) {
            return 0;
        }
        return model.vramFor(getLoraCount(), deepThink && model.has(AIModelDefinition.Trait.DEEP_THINK));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Tick //
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
        tick();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.maintenance = null;
        unload("unformed");
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
        if (!isFormed() || getLevel() == null || getLevel().isClientSide()) {
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
        AIModelDefinition model = modelInSlot();
        if (model == null) {
            unload("empty");
            return;
        }
        // Pulled card, pulled module or a dark memory cabinet evicts the model immediately.
        int required = model.vramFor(getLoraCount(), deepThink && model.has(AIModelDefinition.Trait.DEEP_THINK));
        int pool = poolTerabytes();
        if (!VramRating.fits(pool, required)) {
            if (!loadedModelId.isEmpty()) {
                unload("oom");
            } else {
                setState(State.OOM);
            }
            this.lastVramTb = required;
            return;
        }
        // VRAM is decided before compute: a job that does not fit can never load, so OOM is the
        // final answer rather than a transient LOADING state.
        int cwu = model.billedCwuPerTick();
        if (poolCwu() < cwu) {
            if (!loadedModelId.isEmpty()) {
                unload("no_compute");
            } else {
                setState(State.LOADING);
            }
            return;
        }
        // 0.6.2 · GLM-2333 Flash room inspection: a room below 20% stored energy parks the model
        // in protective sleep. Nothing is billed while asleep and no CWU/t is pulled, which is
        // exactly the "省待机电" promise; the rack wakes itself the tick the grid recovers.
        if (ModelRuntime.sleepsOnBrownout(model) && isBrownedOut()) {
            this.loadedModelId = model.id().toString();
            this.lastVramTb = required;
            this.lastCwu = 0;
            this.consumedCwu = 0;
            this.projectedLoras = 0;
            this.projectedForce = 0;
            setState(State.SLEEPING);
            return;
        }

        // 0.6.2 · GLM-2333 Max agent tool calling: stop the associated multiblocks on a jam or a
        // starvation (省待机电) and start them again once the room can work. This is a real
        // structural pause on the host controllers (setWorkingEnabled), not a label on this rack.
        //
        // A parked room cannot report a reason — its logic is suspended — so the agent runs a
        // duty cycle: park, then periodically probe by resuming the rails for
        // {@value #AGENT_PROBE_TICKS} ticks and watching whether they really pick up work. That
        // keeps a jammed room from flapping one tick at a time and lets it recover by itself.
        if (ModelRuntime.drivesAgentToolCalls(model)) {
            if (this.agentParked) {
                if (--this.agentParkCountdown > 0) {
                    this.loadedModelId = model.id().toString();
                    this.lastVramTb = required;
                    this.lastCwu = 0;
                    this.consumedCwu = 0;
                    setState(State.AGENT_IDLE);
                    return;
                }
                applyAgentToolCalls(true);      // probe window: let the rails try again
                this.agentParked = false;
                this.agentProbeHoldoff = AGENT_PROBE_TICKS;
            } else if (this.agentProbeHoldoff > 0) {
                this.agentProbeHoldoff--;
            } else if (associatedMultiblocksStalled()) {
                applyAgentToolCalls(false);     // jam or starvation: park the room
                this.agentParked = true;
                this.agentParkCountdown = AGENT_PARK_TICKS;
                this.loadedModelId = model.id().toString();
                this.lastVramTb = required;
                this.lastCwu = 0;
                this.consumedCwu = 0;
                setState(State.AGENT_IDLE);
                return;
            }
        } else if (this.agentParked) {
            // The operator swapped in a non-agent family: hand the room back to a running state.
            applyAgentToolCalls(true);
            this.agentParked = false;
            this.agentParkCountdown = 0;
            this.agentProbeHoldoff = 0;
        }

        // Everything checks out: the model is resident and really pulls its CWU/t from the room.
        this.loadedModelId = model.id().toString();
        this.lastVramTb = required;
        this.lastCwu = cwu;
        this.consumedCwu = drawComputation(cwu);
        this.byproductsForced = ModelRuntime.forcesByproducts(model, deepThink);
        projectOntoAssociatedHatches(model);
        setState(State.ONLINE);
    }

    /**
     * Really pull the model's billed CWU/t out of the cluster this tick, so the demand is booked
     * against the same-tick budget of the racks and the optical switch. Returns what was actually
     * drawn: a model whose feed vanished reports 0 and the next tick evicts it.
     */
    protected int drawComputation(int demand) {
        if (demand <= 0) {
            return 0;
        }
        OpticalFabricSwitchMachine fabric = nearbySwitch();
        if (fabric != null) {
            return fabric.requestCWUt(demand, false, new ArrayList<>());
        }
        int remaining = demand;
        for (LiquidCooledComputeRackMachine rack : nearbyLiquidRacks()) {
            if (remaining <= 0) {
                break;
            }
            remaining -= rack.requestCWUt(remaining, false, new ArrayList<>());
        }
        for (AirCooledComputeRackMachine rack : nearbyAirRacks()) {
            if (remaining <= 0) {
                break;
            }
            remaining -= rack.requestCWUt(remaining, false, new ArrayList<>());
        }
        return demand - remaining;
    }

    /**
     * 0.6.2 · the projections a loaded model casts onto the inference hatches it is linked to.
     *
     * <ul>
     * <li><b>Qwen LoRA specialisation</b> — a LoRA-capable model with mining/chemical cards in its
     * extension slots boosts the matching domain on every associated hatch.</li>
     * <li><b>DeepSeek-Pro DeepThink</b> — while charged, by-products are forced to 100% on every
     * associated hatch, so the tower's chanced golden samples stop being a gamble.</li>
     * </ul>
     */
    protected void projectOntoAssociatedHatches(AIModelDefinition model) {
        this.projectedLoras = 0;
        this.projectedForce = 0;
        boolean force = ModelRuntime.forcesByproducts(model, deepThink);
        int loras = Math.min(getLoraCount(), model.loraSlots());
        for (IvInferenceHatchMachine hatch : linkedHatches()) {
            if (!hatch.isFormed()) {
                continue;
            }
            if (loras > 0 && hatch.acceptLoraProjection(model, loras)) {
                this.projectedLoras++;
            }
            if (force && hatch.acceptByproductForce(true)) {
                this.projectedForce++;
            }
        }
    }

    /**
     * Drops every projection the model cast onto its linked hatches. Called the moment the router
     * stops being ONLINE, so a model that went OOM, was evicted or was pulled can never keep
     * forcing by-products on a tower it no longer serves.
     */
    public void clearProjections() {
        for (IvInferenceHatchMachine hatch : linkedHatches()) {
            hatch.acceptByproductForce(false);
            hatch.acceptLoraProjection(null, 0);
        }
        this.projectedLoras = 0;
        this.projectedForce = 0;
        this.byproductsForced = false;
    }

    /**
     * 0.6.2 · the agent's hands: suspend / resume the recipe logic of every linked host.
     * {@code run = false} parks the rails (nothing is crafted, standby draw only), {@code true}
     * pulls them back up so the room resumes by itself once materials or output space return.
     */
    protected void applyAgentToolCalls(boolean run) {
        for (IvInferenceHatchMachine hatch : linkedHatches()) {
            var logic = hatch.hostLogic();
            if (logic != null && logic.isWorkingEnabled() != run) {
                logic.setWorkingEnabled(run);
            }
        }
    }

    /**
     * 0.6.2 · GLM-2333 Max's agent reads the associated multiblocks' work state. A rail that is
     * jammed (its output bus cannot take the products) or starved (no input at all) counts as
     * stalled, and the agent parks the room instead of idling at full standby draw.
     */
    protected boolean associatedMultiblocksStalled() {
        for (IvInferenceHatchMachine hatch : linkedHatches()) {
            var logic = hatch.hostLogic();
            if (logic == null) {
                continue;
            }
            if (logic.isWaiting()) {
                return true;
            }
            // A host that is neither working nor suspended has nothing to do: for the agent that
            // is starvation, and parking the room is exactly what it promises.
            if (!logic.isWorking() && !logic.isSuspend()) {
                return true;
            }
        }
        // No linked rail at all: nothing to park, the room stays online.
        return false;
    }

    /**
     * The inference hatches this router serves. The hatches are parts of a <b>different</b>
     * multiblock (the data scrubbing tower), so the association is real proximity inside the same
     * machine room — the same {@value #POOL_RANGE}-block reach the memory and compute pools use.
     */
    public List<IvInferenceHatchMachine> linkedHatches() {
        List<IvInferenceHatchMachine> found = new ArrayList<>();
        if (getLevel() == null) {
            return found;
        }
        int r = POOL_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof IvInferenceHatchMachine hatch) {
                found.add(hatch);
            }
        }
        return found;
    }

    /** Takes the model out of memory (kept on disk: the weights item stays in its slot). */
    public void unload(String reason) {
        this.loadedModelId = "";
        this.lastCwu = 0;
        this.consumedCwu = 0;
        this.byproductsForced = false;
        this.projectedLoras = 0;
        this.projectedForce = 0;
        setState(switch (reason) {
            case "oom" -> State.OOM;
            case "no_compute" -> State.EVICTED;
            case "unformed", "empty" -> State.EMPTY;
            default -> State.EVICTED;
        });
    }

    protected void setState(State next) {
        if (this.state != next) {
            this.state = next;
            if (next != State.ONLINE) {
                // 0.6.2 · a model that is no longer resident casts no projections.
                clearProjections();
            }
            updateRenderState();
        }
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(modelSlot);
        clearInventory(loraSlot);
    }

    protected void updateRenderState() {
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = state == State.ONLINE ? RecipeLogic.Status.WORKING :
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
        String uiKey = "hypercluster.machine.moe_router_rack.";
        WidgetGroup controls = MachineUi.multiblockControls(53);
        controls.addWidgets(
                MachineUi.label(8, 0, 78, uiKey + "label.model"),
                MachineUi.label(90, 0, 90, uiKey + "label.lora"));
        SlotWidget model = new SlotWidget(modelSlot, 0, 14, 13, true, false);
        model.setBackground(GuiTextures.SLOT);
        model.setHoverTooltips(Component.translatable(uiKey + "model_slot"));
        controls.addWidget(model);
        for (int i = 0; i < LORA_SLOTS; i++) {
            SlotWidget lora = new SlotWidget(loraSlot, i, 92 + i * 22, 13, true, false);
            lora.setBackground(GuiTextures.SLOT);
            lora.setHoverTooltips(Component.translatable(uiKey + "lora_slot"));
            controls.addWidget(lora);
        }
        ToggleButtonWidget deepThinkToggle = new ToggleButtonWidget(14, 35, 18, 18,
                GuiTextures.BUTTON_BATCH, this::isDeepThink, this::setDeepThink);
        deepThinkToggle.setHoverTooltips(Component.translatable(uiKey + "deepthink.tooltip"));
        controls.addWidget(deepThinkToggle);
        return MachineUi.multiblockPage(this, controls);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        String uiKey = "hypercluster.machine.moe_router_rack.";
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), state == State.ONLINE)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        uiKey + "online")
                .addCustom(lines -> {
                    if (!isFormed()) {
                        return;
                    }
                    AIModelDefinition model = loadedModel();
                    if (model != null) {
                        lines.add(Component.translatable(uiKey + "model_line",
                                Component.translatable(model.displayKey()).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    lines.add(Component.translatable(uiKey + "vram",
                            Component.literal(String.valueOf(requiredTerabytes())).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(poolTerabytes())).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "compute",
                            Component.literal(String.valueOf(lastCwu)).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(poolCwu())).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    if (deepThink) {
                        lines.add(Component.translatable(uiKey + "deepthink.on")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    // 0.6.2 · the real runtime readings: what the room drew, and what the model's
                    // projections reached on the linked inference hatches.
                    if (consumedCwu > 0) {
                        lines.add(Component.translatable(uiKey + "consumed",
                                Component.literal(String.valueOf(consumedCwu)).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    if (projectedLoras > 0) {
                        lines.add(Component.translatable(uiKey + "projection.lora",
                                Component.literal(String.valueOf(projectedLoras))
                                        .withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));
                    }
                    if (projectedForce > 0) {
                        lines.add(Component.translatable(uiKey + "projection.force",
                                Component.literal(String.valueOf(projectedForce))
                                        .withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GOLD));
                    }
                    if (agentParked) {
                        lines.add(Component.translatable(uiKey + "agent.on").withStyle(ChatFormatting.RED));
                    }
                    if (state != State.ONLINE) {
                        lines.add(Component
                                .translatable(uiKey + "state." + state.name().toLowerCase(Locale.ROOT))
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
        dev.hypercluster.common.machine.datacenter.VentParticles.emit(getLevel(), random, getPos(),
                getFrontFacing(), 1.0, 2);
    }
}
