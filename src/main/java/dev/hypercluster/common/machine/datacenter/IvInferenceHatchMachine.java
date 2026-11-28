package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import dev.hypercluster.common.model.AIModels;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 0.3 · IV inference coprocessor hatch ({@code hypercluster:iv_inference_hatch}).
 *
 * <p>
 * A GT-style multiblock part (attachable to the data scrubbing tower's structure) that acts as an
 * optical <b>computation receiver</b>: it draws real {@code CWU/t} through an adjacent optical
 * pipe or the raised-floor room's compute providers. It
 * adds one inference chip slot (ordinary or aligned domain edge chips) and a per-recipe contract:
 *
 * <ul>
 * <li>Host recipes whitelisted with the {@code hc_inference_boost} tag (the two base data
 * scrubbing recipes) match under the original rules (2048 EU/t). When this hatch holds a chip and
 * the linked compute providers can deliver, {@link #modifyRecipe} rewrites the recipe into its boosted form:
 * the chip's CWU/t demand (2 for an ordinary domain edge chip, 8 for an aligned one) is added as
 * a tick input and the EU/t drops by the chip's discount (25% / 50%). The demand is then billed
 * every tick through this hatch's native computation container — the discount exists exactly
 * while CWU/t is really consumed. Never a free discount.</li>
 * <li>The tower keeps the original recipe if a chip or enough computation is unavailable.</li>
 * <li>If the CWU/t feed is cut mid-run, a small watchdog marks the host's recipe dirty so the
 * next search falls back to the original rules instead of stalling.</li>
 * </ul>
 */
public class IvInferenceHatchMachine extends OpticalComputationHatchMachine
                                     implements IMachineLife, IMaintenanceMachine, IInteractedMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            IvInferenceHatchMachine.class, OpticalComputationHatchMachine.MANAGED_FIELD_HOLDER);

    /** Recipe data tag: whitelists a recipe for the inference assist (1 = boostable). */
    public static final String INFERENCE_BOOST_TAG = "hc_inference_boost";

    /** Ordinary domain edge chips serve inference demands up to 2 CWU/t. */
    public static final int EDGE_CHIP_MAX_CWU = 2;

    /** Aligned domain edge chips serve inference demands up to 8 CWU/t. */
    public static final int ALIGNED_CHIP_MAX_CWU = 8;

    @Persisted
    protected final CustomItemStackHandler chipSlot;

    @Persisted
    @DescSynced
    private byte maintenanceProblems = NO_PROBLEMS;
    @Persisted
    private int timeActive;
    @Persisted
    private boolean taped;

    @Nullable
    protected TickableSubscription tickSubs;

    private List<MoeRouterRackMachine> attachedRouters = List.of();
    private ShutdownDiagnosis diagnosis = ShutdownDiagnosis.NONE;
    private boolean glmOnline;
    private ItemStack lastCard = ItemStack.EMPTY;

    public IvInferenceHatchMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, false);
        this.chipSlot = new CustomItemStackHandler(1);
        this.chipSlot.setFilter(stack -> isInferenceChip(stack) || stack.is(HyperClusterItems.MINING_LORA_CARD.get()) ||
                stack.is(HyperClusterItems.CHEMICAL_LORA_CARD.get()));
    }

    @Override
    protected NotifiableComputationContainer createComputationContainer(Object... args) {
        return new NotifiableComputationContainer(this, IO.IN, false) {

            @Override
            public List<Integer> handleRecipeInner(IO io, GTRecipe recipe, List<Integer> left, boolean simulate) {
                if (io != IO.IN || !isInferenceRecipe(recipe)) {
                    return super.handleRecipeInner(io, recipe, left, simulate);
                }
                var host = host();
                if (host == null) {
                    return left;
                }
                int demand = left.stream().mapToInt(Integer::intValue).sum();
                int outstanding = demand - requestInferenceCWUt(host, demand, simulate);
                return outstanding > 0 ? List.of(outstanding) : null;
            }
        };
    }

    private static boolean isInferenceRecipe(GTRecipe recipe) {
        return IndustrialInference.hasPlan(recipe) || recipe.data.contains(INFERENCE_BOOST_TAG);
    }

    private int requestInferenceCWUt(IWorkableMultiController host, int demand, boolean simulate) {
        if (demand <= 0) {
            return 0;
        }
        // Choose one path for the whole charge. A wired endpoint and the floor may share a rack.
        if (computationContainer.requestCWUt(demand, true) >= demand) {
            return simulate ? demand : computationContainer.requestCWUt(demand, false);
        }
        var room = DatacenterOpticalNetwork.floorProviders(host.self());
        if (DatacenterOpticalNetwork.request(room, demand, true, new ArrayList<>()) < demand) {
            return 0;
        }
        return simulate ? demand : DatacenterOpticalNetwork.request(room, demand, false, new ArrayList<>());
    }

    private boolean hasInferenceCapacity(IWorkableMultiController host, int demand) {
        return computationContainer.getMaxCWUt() >= demand ||
                DatacenterOpticalNetwork.capacity(DatacenterOpticalNetwork.floorProviders(host.self()),
                        new ArrayList<>()) >= demand;
    }

    /** The existing domain edge chips (ordinary and red-team aligned) drive the hatch. */
    public static boolean isInferenceChip(ItemStack stack) {
        return stack.is(HyperClusterItems.MINING_EDGE_CHIP.get()) ||
                stack.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()) ||
                stack.is(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get()) ||
                stack.is(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get());
    }

    /** CWU/t demand an inserted chip serves: 0 (none), 2 (ordinary) or 8 (aligned). */
    public static int chipCapacity(ItemStack chip) {
        if (chip.is(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get()) ||
                chip.is(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get())) {
            return ALIGNED_CHIP_MAX_CWU;
        }
        if (chip.is(HyperClusterItems.MINING_EDGE_CHIP.get()) || chip.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get())) {
            return EDGE_CHIP_MAX_CWU;
        }
        return 0;
    }

    /** EU/t discount the inserted chip earns: 25% (ordinary) or 50% (aligned). */
    public static double chipDiscount(ItemStack chip) {
        return chipCapacity(chip) >= ALIGNED_CHIP_MAX_CWU ? 0.5 : 0.25;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand,
                                net.minecraft.world.phys.BlockHitResult hit) {
        return true;
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = MachineUi.panel(164, 121);
        group.addWidget(MachineUi.label(16, 21, 132, "hypercluster.machine.iv_inference_hatch.label.chip"));
        SlotWidget chip = new SlotWidget(chipSlot, 0, (group.getSize().width - 18) / 2, 35, true, true);
        chip.setBackground(GuiTextures.SLOT);
        chip.setHoverTooltips(Component.translatable("hypercluster.machine.iv_inference_hatch.chip_slot"));
        group.addWidget(chip);
        group.addWidget(MachineUi.display(8, 62, 148, 51, GuiTextures.DISPLAY,
                MachineUi.statusText(isRemote(), lines -> lines.add(diagnosticText()))));
        return group;
    }

    public ShutdownDiagnosis diagnosis() {
        return diagnosis;
    }

    public boolean hasGlmDiagnosis() {
        return glmOnline;
    }

    private Component diagnosticText() {
        return glmOnline ? diagnosis.message() : Component.translatable("hypercluster.diagnosis.offline");
    }

    @Override
    public void addMultiText(List<Component> textList) {
        if (glmOnline) {
            textList.add(diagnosticText());
        }
    }

    @Override
    public boolean isFullAuto() {
        return automaticMaintenance();
    }

    @Override
    public byte startProblems() {
        return NO_PROBLEMS;
    }

    @Override
    public byte getMaintenanceProblems() {
        return maintenanceProblems;
    }

    @Override
    public void setMaintenanceProblems(byte problems) {
        maintenanceProblems = problems;
    }

    @Override
    public int getTimeActive() {
        return timeActive;
    }

    @Override
    public void setTimeActive(int time) {
        timeActive = time;
    }

    @Override
    public boolean isTaped() {
        return taped;
    }

    @Override
    public void setTaped(boolean taped) {
        this.taped = taped;
    }

    // GT's normal tape repair, without taking the one hardware slot away from the player.
    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        var held = player.getItemInHand(hand);
        if (hasMaintenanceProblems() && held.is(GTItems.DUCT_TAPE.get())) {
            if (!isRemote()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                setMaintenanceProblems(NO_PROBLEMS);
                setTaped(true);
                var host = host();
                if (host != null) {
                    requestRecipeSearch(host.getRecipeLogic());
                }
            }
            return InteractionResult.sidedSuccess(isRemote());
        }
        return IInteractedMachine.super.onUse(state, level, pos, player, hand, hit);
    }

    public CustomItemStackHandler getChipSlot() {
        return chipSlot;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(chipSlot);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        this.tickSubs = subscribeServerTick(this.tickSubs, this::watchdogTick);
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        clearDiagnostics();
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    @Override
    public void onUnload() {
        clearDiagnostics();
        super.onUnload();
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Inference contract //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public GTRecipe modifyRecipe(GTRecipe recipe) {
        if (recipe == null || !isFormed()) {
            return recipe;
        }
        var host = host();
        repairIfAutomatic(host);
        if (!(host != null && host.self() instanceof dev.hypercluster.common.machine.DataScrubbingTowerMachine)) {
            recipe = IMaintenanceMachine.super.modifyRecipe(recipe);
        }
        if (recipe == null) {
            return null;
        }
        if (!recipe.data.contains(INFERENCE_BOOST_TAG)) {
            if (host == null || !eligibleForIndustrialInference(host, recipe)) {
                return recipe;
            }
            var plan = IndustrialInference.plan((WorkableElectricMultiblockMachine) host.self(), recipe,
                    chipSlot.getStackInSlot(0), onlineRouters(host));
            return plan != null && requestInferenceCWUt(host, plan.demand(), true) >= plan.demand() ?
                    IndustrialInference.mark(recipe, plan) : recipe;
        }
        ItemStack chip = chipSlot.getStackInSlot(0);
        int demand = chipCapacity(chip);
        if (host == null || demand <= 0) {
            return recipe; // no chip: original rules.
        }
        // Simulate only: the real per-tick billing runs through this hatch's computation
        // container while the recipe works (never a free discount).
        int granted = requestInferenceCWUt(host, demand, true);
        if (granted < demand) {
            return recipe; // no CWU/t right now: original rules.
        }
        long boostedEUt = Math.max(1,
                Math.round(recipeEUt(recipe) * (1.0 - chipDiscount(chip))));
        return boostRecipe(recipe, demand, boostedEUt);
    }

    private static boolean eligibleForIndustrialInference(IWorkableMultiController host, GTRecipe recipe) {
        return host.self() instanceof WorkableElectricMultiblockMachine electric &&
                electric.getDefinition().getId().getNamespace().equals("gtceu") &&
                !recipe.getTickInputContents(EURecipeCapability.CAP).isEmpty() && inferenceDemand(recipe) == 0;
    }

    /** The recipe's EU/t from its EU tick input (0 if it has none). */
    public static long recipeEUt(GTRecipe recipe) {
        List<Content> eu = recipe.tickInputs.get(EURecipeCapability.CAP);
        if (eu == null || eu.isEmpty()) {
            return 0;
        }
        return EURecipeCapability.CAP.of(eu.get(0).content).voltage();
    }

    /**
     * The boosted rewrite: add the CWU/t demand as a tick input (billed every tick through this
     * hatch's computation container) and run the recipe at the discounted EU/t.
     */
    public static GTRecipe boostRecipe(GTRecipe recipe, int demand, long boostedEUt) {
        GTRecipe copy = recipe.copy();
        List<Content> cwu = new ArrayList<>();
        cwu.add(new Content(demand, com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic
                .getMaxChancedValue(),
                com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic.getMaxChancedValue(), 0));
        copy.tickInputs.put(CWURecipeCapability.CAP, cwu);
        List<Content> eu = copy.tickInputs.get(EURecipeCapability.CAP);
        if (eu != null && !eu.isEmpty()) {
            Content content = eu.get(0);
            EnergyStack stack = EURecipeCapability.CAP.of(content.content);
            List<Content> replacement = new ArrayList<>();
            replacement.add(new Content(new EnergyStack(boostedEUt, stack.amperage()),
                    content.chance, content.maxChance, content.tierChanceBoost));
            copy.tickInputs.put(EURecipeCapability.CAP, replacement);
        }
        return copy;
    }

    /** CWU/t demanded by the currently running (boosted) recipe, 0 for ordinary recipes. */
    public static int inferenceDemand(GTRecipe recipe) {
        List<Content> cwu = recipe.tickInputs.get(CWURecipeCapability.CAP);
        if (cwu == null || cwu.isEmpty()) {
            return 0;
        }
        int demand = 0;
        for (Content content : cwu) {
            demand += CWURecipeCapability.CAP.of(content.content);
        }
        return demand;
    }

    @Override
    public boolean onWorking(IWorkableMultiController host) {
        repairIfAutomatic(host);
        if (host.self() instanceof dev.hypercluster.common.machine.DataScrubbingTowerMachine) {
            return true;
        }
        return IMaintenanceMachine.super.onWorking(host);
    }

    protected void watchdogTick() {
        var host = host();
        if (host == null || getLevel() == null || isRemote()) {
            return;
        }
        var logic = host.getRecipeLogic();
        var routers = onlineRouters(host);
        var card = chipSlot.getStackInSlot(0);
        boolean glm = routers.stream().anyMatch(IvInferenceHatchMachine::isGlm);
        if (!ItemStack.isSameItemSameTags(card, lastCard) || !routers.equals(attachedRouters) || glm != glmOnline) {
            requestRecipeSearch(logic);
            lastCard = card.copy();
        }
        repairIfAutomatic(host, glm);

        updateRunningInference(host, card, routers);

        glmOnline = glm;
        diagnosis = glm ? ShutdownDiagnosis.inspect(host) : ShutdownDiagnosis.NONE;
        for (var router : attachedRouters) {
            if (!routers.contains(router)) {
                router.updateDiagnostic(this, ShutdownDiagnosis.NONE);
            }
        }
        for (var router : routers) {
            router.updateDiagnostic(this, diagnosis);
        }
        attachedRouters = routers;
    }

    private void updateRunningInference(IWorkableMultiController host, ItemStack card,
                                        List<MoeRouterRackMachine> routers) {
        var logic = host.getRecipeLogic();
        GTRecipe last = logic.getLastRecipe();
        if (last == null || logic.isSuspend() || logic.getDuration() <= 0) {
            return;
        }
        boolean tower = last.data.contains(INFERENCE_BOOST_TAG);
        boolean industrial = IndustrialInference.hasPlan(last);
        if (!tower && !industrial && !eligibleForIndustrialInference(host, last)) {
            return;
        }

        var plan = !tower && host.self() instanceof WorkableElectricMultiblockMachine electric ?
                IndustrialInference.plan(electric, last, card, routers) : null;
        int demand = inferenceDemand(last);
        if (demand == 0) {
            int nextDemand = tower ? chipCapacity(card) : plan == null ? 0 : plan.demand();
            if (nextDemand > 0 && requestInferenceCWUt(host, nextDemand, true) >= nextDemand) {
                // Some GT hosts reuse the last recipe after completion. Re-evaluate at the batch boundary.
                logic.markLastRecipeDirty();
            }
        } else if (industrial && !IndustrialInference.matchesPlan(last, plan) ||
                !hasInferenceCapacity(host, demand) ||
                logic.isWaiting() && requestInferenceCWUt(host, demand, true) < demand) {
                    // The inputs were already consumed. Interrupt and search again using the available paths.
                    logic.markLastRecipeDirty();
                    logic.interruptRecipe();
                }
    }

    private void clearDiagnostics() {
        for (var router : attachedRouters) {
            router.updateDiagnostic(this, ShutdownDiagnosis.NONE);
        }
        attachedRouters = List.of();
        glmOnline = false;
        diagnosis = ShutdownDiagnosis.NONE;
    }

    private boolean automaticMaintenance() {
        var host = host();
        return chipCapacity(chipSlot.getStackInSlot(0)) == ALIGNED_CHIP_MAX_CWU ||
                host != null && onlineRouters(host).stream().anyMatch(IvInferenceHatchMachine::isGlm);
    }

    private void repairIfAutomatic(@Nullable IWorkableMultiController host) {
        repairIfAutomatic(host, host != null && onlineRouters(host).stream().anyMatch(IvInferenceHatchMachine::isGlm));
    }

    private void repairIfAutomatic(@Nullable IWorkableMultiController host, boolean glm) {
        if (host == null || !glm && chipCapacity(chipSlot.getStackInSlot(0)) != ALIGNED_CHIP_MAX_CWU) {
            return;
        }
        for (var part : host.getParts()) {
            if (part instanceof IMaintenanceMachine maintenance && maintenance.hasMaintenanceProblems()) {
                maintenance.setMaintenanceProblems(NO_PROBLEMS);
                requestRecipeSearch(host.getRecipeLogic());
            }
        }
    }

    private static void requestRecipeSearch(RecipeLogic logic) {
        logic.markLastRecipeDirty();
        logic.updateTickSubscription();
    }

    private static boolean isGlm(MoeRouterRackMachine router) {
        var model = router.loadedModel();
        return model == AIModels.GLM_2333_FLASH || model == AIModels.GLM_2333_MAX;
    }

    private List<MoeRouterRackMachine> onlineRouters(IWorkableMultiController host) {
        return DatacenterOpticalNetwork.modelRouters(host.self()).stream()
                .filter(MoeRouterRackMachine::isInferenceOnline).toList();
    }

    @Nullable
    private IWorkableMultiController host() {
        for (var controller : getControllers()) {
            if (controller instanceof IWorkableMultiController workable) {
                return workable;
            }
        }
        return null;
    }
}
