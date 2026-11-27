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
import dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.OpticalFabricSwitchMachine;
import dev.hypercluster.common.machine.datacenter.VramRating;
import dev.hypercluster.common.machine.datacenter.VramStorageRackMachine;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;

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
        MAINTENANCE
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
        return loadedModelId.isEmpty() ? null : AIModels.catalog().get(loadedModelId);
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

    /** The model definition named by the weights in the deployment slot, or null. */
    @Nullable
    public AIModelDefinition modelInSlot() {
        ItemStack stack = modelSlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        String domain = dev.hypercluster.common.item.OpenWeightModuleItem.domain(stack);
        // The module's domain decides which open-camp identity it carries.
        for (AIModelDefinition definition : AIModels.localModels()) {
            if (definition.servesDomain(domain)) {
                return definition;
            }
        }
        return null;
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
        // Everything checks out: the model is resident.
        this.loadedModelId = model.id().toString();
        this.lastVramTb = required;
        this.lastCwu = cwu;
        setState(State.ONLINE);
    }

    /** Takes the model out of memory (kept on disk: the weights item stays in its slot). */
    public void unload(String reason) {
        this.loadedModelId = "";
        this.lastCwu = 0;
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
