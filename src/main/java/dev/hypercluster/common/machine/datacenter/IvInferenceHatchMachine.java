package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.ModelRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3 · IV inference coprocessor hatch ({@code hypercluster:iv_inference_hatch}).
 *
 * <p>
 * A GT-style multiblock part (attachable to the data scrubbing tower's structure) that acts as an
 * optical <b>computation receiver</b>: like GTCEu's own computation reception hatch it pulls real
 * {@code CWU/t} through an adjacent optical pipe from an {@link AirCooledComputeRackMachine}. It
 * adds one inference chip slot (ordinary or aligned domain edge chips) and a per-recipe contract:
 *
 * <ul>
 * <li>Host recipes whitelisted with the {@code hc_inference_boost} tag (the two base data
 * scrubbing recipes) match under the original rules (2048 EU/t). When this hatch holds a chip and
 * the optical link can deliver, {@link #modifyRecipe} rewrites the recipe into its boosted form:
 * the chip's CWU/t demand (2 for an ordinary domain edge chip, 8 for an aligned one) is added as
 * a tick input and the EU/t drops by the chip's discount (25% / 50%). The demand is then billed
 * every tick through this hatch's native computation container — the discount exists exactly
 * while CWU/t is really consumed. Never a free discount.</li>
 * <li>If the chip cannot serve the demand, no chip is inserted or the optical link cannot
 * deliver, the recipe passes through unchanged — the tower runs under the original rules.</li>
 * <li>If the CWU/t feed is cut mid-run, a small watchdog marks the host's recipe dirty so the
 * next search falls back to the original rules instead of stalling.</li>
 * </ul>
 */
public class IvInferenceHatchMachine extends OpticalComputationHatchMachine implements IMachineLife {

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

    /**
     * 0.6.2 · the domain a router's LoRA extension currently boosts on this hatch ({@code ""} for
     * none) and how many LoRA cards stand behind it.
     */
    @com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced
    protected String loraDomain = "";
    @com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced
    protected int loraCards = 0;
    /**
     * 0.6.2 · true while a DeepSeek-Pro router with DeepThink charged forces this hatch's
     * probability by-products to 100%.
     */
    @com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced
    protected boolean byproductsForced = false;

    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    public IvInferenceHatchMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, false);
        this.chipSlot = new CustomItemStackHandler(1);
        this.chipSlot.setFilter(stack -> isInferenceChip(stack));
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
        WidgetGroup group = MachineUi.panel(164, 78);
        group.addWidget(MachineUi.label(16, 21, 132, "hypercluster.machine.iv_inference_hatch.label.chip"));
        SlotWidget chip = new SlotWidget(chipSlot, 0, (group.getSize().width - 18) / 2, 35, true, true);
        chip.setBackground(GuiTextures.SLOT);
        chip.setHoverTooltips(Component.translatable("hypercluster.machine.iv_inference_hatch.chip_slot"));
        group.addWidget(chip);
        return group;
    }

    public CustomItemStackHandler getChipSlot() {
        return chipSlot;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.6.2 · projections cast by a linked MoE router //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Accepts a LoRA specialisation projection. Returns true when this hatch really takes it: an
     * inference chip must be present, because a LoRA without a base model to specialise is not a
     * boost. The domain is derived from which domains the router's model serves.
     */
    public boolean acceptLoraProjection(AIModelDefinition model, int cards) {
        if (cards <= 0 || model == null || !ModelRuntime.loraBoostsDomain(model, domainOfChip())) {
            this.loraDomain = "";
            this.loraCards = 0;
            return false;
        }
        this.loraDomain = domainOfChip();
        this.loraCards = cards;
        return true;
    }

    /** Accepts a by-product force projection (DeepSeek-Pro DeepThink). Always taken live. */
    public boolean acceptByproductForce(boolean force) {
        this.byproductsForced = force;
        return force;
    }

    /** The domain the inserted inference chip serves ({@code ""} when there is no chip). */
    public String domainOfChip() {
        ItemStack chip = chipSlot.getStackInSlot(0);
        if (chip.is(HyperClusterItems.MINING_EDGE_CHIP.get()) ||
                chip.is(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get())) {
            return "mining";
        }
        if (chip.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()) ||
                chip.is(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get())) {
            return "chemical";
        }
        return "";
    }

    /** The domain a router's LoRA extension is boosting here, or {@code ""}. */
    public String getLoraDomain() {
        return loraDomain;
    }

    public int getLoraCards() {
        return loraCards;
    }

    public boolean isByproductsForced() {
        return byproductsForced;
    }

    /**
     * 0.6.2 · the LoRA specialisation bonus this hatch's host gets on its by-product chances. A
     * boosted domain turns the ordinary 1/3 cobalt chance into 2/3 (the 0.2.1 LoRA contract),
     * applied through {@link #modifyRecipe} as a real recipe rewrite.
     */
    public boolean loraBoostsRecipeDomain(java.util.function.Predicate<String> servesDomain) {
        return !loraDomain.isEmpty() && servesDomain.test(loraDomain);
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(chipSlot);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addedToController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.addedToController(controller);
        this.tickSubs = subscribeServerTick(this.tickSubs, this::watchdogTick);
    }

    @Override
    public void removedFromController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.removedFromController(controller);
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
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

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Inference contract //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public GTRecipe modifyRecipe(GTRecipe recipe) {
        recipe = super.modifyRecipe(recipe);
        if (recipe != null) {
            // 0.6.2 · a linked router's projections are applied to the recipe the host really runs,
            // for EVERY recipe — not only the whitelisted boost set. This is where DeepSeek-Pro's
            // DeepThink really forces probability by-products to 100%.
            recipe = applyByproductForce(recipe);
            recipe = applyLoraSpecialisation(recipe);
        }
        if (recipe == null || !isFormed() || !recipe.data.contains(INFERENCE_BOOST_TAG)) {
            return recipe;
        }
        ItemStack chip = chipSlot.getStackInSlot(0);
        int demand = chipCapacity(chip);
        if (demand <= 0) {
            return recipe; // no chip: original rules.
        }
        // Simulate only: the real per-tick billing runs through this hatch's computation
        // container while the recipe works (never a free discount).
        int granted = computationContainer.requestCWUt(demand, true);
        if (granted < demand) {
            return recipe; // no CWU/t right now: original rules.
        }
        long boostedEUt = Math.max(1,
                Math.round(recipeEUt(recipe) * (1.0 - chipDiscount(chip))));
        return boostRecipe(recipe, demand, boostedEUt);
    }

    /**
     * 0.6.2 · force every probability output of {@code recipe} to certainty while a linked
     * DeepSeek-Pro router has DeepThink charged. A by-product forced this way keeps its share of
     * the total: the chance is raised to the max, which is exactly "概率副产拉满 100%".
     */
    public GTRecipe applyByproductForce(GTRecipe recipe) {
        if (!byproductsForced || recipe == null) {
            return recipe;
        }
        return forceByproducts(recipe);
    }

    /**
     * 0.6.2 · the Qwen LoRA projection: while a linked router holds LoRA cards for the domain this
     * hatch's chip serves, the host's by-product chances get the 0.2.1 LoRA treatment — a
     * {@code 1/3} rare by-product becomes {@code 2/3}. The boost is capped at certainty and never
     * touches an output that is already guaranteed.
     */
    public GTRecipe applyLoraSpecialisation(GTRecipe recipe) {
        if (loraCards <= 0 || loraDomain.isEmpty() || recipe == null) {
            return recipe;
        }
        return boostByproducts(recipe, loraCards);
    }

    /**
     * Pure rewrite: raise every chanced regular output's probability by {@code 1/3} per LoRA card,
     * clamped at certainty. Four cards take the classic {@code 1/3} cobalt by-product to 100%.
     */
    public static GTRecipe boostByproducts(GTRecipe recipe, int loraCards) {
        if (recipe == null || loraCards <= 0) {
            return recipe;
        }
        int boost = Math.max(1, loraCards);
        boolean any = false;
        for (List<Content> contents : recipe.outputs.values()) {
            for (Content content : contents) {
                if (content.chance < content.maxChance) {
                    any = true;
                    break;
                }
            }
            if (any) {
                break;
            }
        }
        if (!any) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        copy.outputs.clear();
        for (var entry : recipe.outputs.entrySet()) {
            List<Content> boosted = new ArrayList<>(entry.getValue().size());
            for (Content content : entry.getValue()) {
                if (content.chance >= content.maxChance) {
                    boosted.add(content);
                    continue;
                }
                // Each card adds an equal share of the total chance space.
                int step = content.maxChance / 3;
                int raised = Math.min(content.maxChance, content.chance + step * boost);
                boosted.add(new Content(content.content,
                        Math.max(content.chance, raised), content.maxChance, content.tierChanceBoost));
            }
            copy.outputs.put(entry.getKey(), boosted);
        }
        return copy;
    }

    /**
     * Pure rewrite: every chanced regular output becomes a guaranteed one. Content is immutable,
     * so the outputs map is rebuilt rather than mutated in place; the recipe copy keeps the
     * original registry entry untouched.
     */
    public static GTRecipe forceByproducts(GTRecipe recipe) {
        boolean any = false;
        for (List<Content> contents : recipe.outputs.values()) {
            for (Content content : contents) {
                if (content.chance < content.maxChance) {
                    any = true;
                    break;
                }
            }
            if (any) {
                break;
            }
        }
        if (!any) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        copy.outputs.clear();
        for (var entry : recipe.outputs.entrySet()) {
            List<Content> forced = new ArrayList<>(entry.getValue().size());
            for (Content content : entry.getValue()) {
                forced.add(content.chance < content.maxChance ?
                        new Content(content.content, content.maxChance, content.maxChance,
                                content.tierChanceBoost) :
                        content);
            }
            copy.outputs.put(entry.getKey(), forced);
        }
        return copy;
    }

    /** True when this hatch's host recipe has at least one chanced output worth forcing. */
    public static boolean hasChancedOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        for (List<Content> contents : recipe.outputs.values()) {
            for (Content content : contents) {
                if (content.chance < content.maxChance) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * Watchdog: while a host works an inference recipe whose CWU/t feed died mid-run, mark the
     * recipe dirty so the next search falls back to the original rules instead of stalling.
     */
    protected void watchdogTick() {
        if (!isFormed() || getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        var logic = hostLogic();
        if (logic == null) {
            return;
        }
        GTRecipe last = logic.getLastRecipe();
        int demand = last == null ? 0 : inferenceDemand(last);
        if (demand <= 0 || !logic.isWaiting()) {
            return; // nothing boosted is running, or nothing is stalled.
        }
        int granted = computationContainer.requestCWUt(demand, true);
        if (granted < demand) {
            // The CWU/t feed died mid-run: fall back to the original rules instead of stalling
            // forever. The interrupt sends the logic back to the search, where modifyRecipe
            // passes the recipe through un-boosted (the original 2048 EU/t, no CWU demand).
            logic.interruptRecipe();
        }
    }

    @javax.annotation.Nullable
    public com.gregtechceu.gtceu.api.machine.trait.RecipeLogic hostLogic() {
        for (var controller : getControllers()) {
            if (controller instanceof IWorkableMultiController workable && workable.getRecipeLogic() != null) {
                return workable.getRecipeLogic();
            }
        }
        return null;
    }
}
