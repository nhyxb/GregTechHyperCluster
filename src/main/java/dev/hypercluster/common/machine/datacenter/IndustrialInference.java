package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.world.item.ItemStack;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.TelemetrySettlement;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import dev.hypercluster.common.model.AIModels;

import java.util.List;

/** Keeps the vanilla modifier's overclocks and parallels, then applies the hatch's inference plan. */
public final class IndustrialInference {

    private static final String PLAN_KEY = "hypercluster_industrial_inference";

    private IndustrialInference() {}

    public record Plan(int demand, int discount, int chanceMode) {

        int encoded() {
            return demand << 8 | discount << 2 | chanceMode;
        }
    }

    public static void installRecipeModifiers() {
        for (var entry : GTRegistries.MACHINES.entries()) {
            if (!entry.getKey().getNamespace().equals("gtceu") ||
                    !(entry.getValue() instanceof MultiblockMachineDefinition definition)) {
                continue;
            }
            var base = definition.getRecipeModifier();
            definition.setRecipeModifier((machine, recipe) -> {
                var original = base.getModifier(machine, recipe);
                int plan = recipe.data.getInt(PLAN_KEY);
                return plan == 0 ? original : original.andThen(resolved -> applyPlan(resolved, plan));
            });
        }
    }

    static boolean hasPlan(GTRecipe recipe) {
        return recipe.data.contains(PLAN_KEY);
    }

    static GTRecipe mark(GTRecipe recipe, Plan plan) {
        GTRecipe tagged = recipe.copy();
        // GTRecipe.copy shares the data tag unless it is explicitly copied.
        tagged.data = recipe.data.copy();
        tagged.data.putInt(PLAN_KEY, plan.encoded());
        return tagged;
    }

    static boolean matchesPlan(GTRecipe recipe, Plan current) {
        return current != null && recipe.data.getInt(PLAN_KEY) == current.encoded();
    }

    static Plan plan(WorkableElectricMultiblockMachine host, GTRecipe recipe, ItemStack card,
                     List<MoeRouterRackMachine> routers) {
        String domain = recipe.getType() == GTRecipeTypes.BLAST_RECIPES ? "mining" :
                TelemetrySettlement.domainOf(recipe.getType());
        boolean mining = "mining".equals(domain);
        boolean chemical = "chemical".equals(domain);
        boolean lora = mining && card.is(HyperClusterItems.MINING_LORA_CARD.get()) ||
                chemical && card.is(HyperClusterItems.CHEMICAL_LORA_CARD.get());
        boolean aligned = mining && card.is(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get()) ||
                chemical && card.is(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get());
        boolean ordinary = mining && card.is(HyperClusterItems.MINING_EDGE_CHIP.get()) ||
                chemical && card.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get());
        if (!ordinary && !aligned && !lora && routers.isEmpty()) {
            return null;
        }
        boolean deepThink = routers.stream().anyMatch(router -> router.isDeepThink() &&
                router.loadedModel() == AIModels.DEEPSEEK_V6767_PRO);
        int scale = 1 << Math.max(0, host.getTier() - GTValues.IV);
        return new Plan((aligned ? 8 : 2) * scale, aligned ? 50 : 25, deepThink ? 2 : lora ? 1 : 0);
    }

    private static GTRecipe applyPlan(GTRecipe recipe, int plan) {
        if (recipe.getTickInputContents(EURecipeCapability.CAP).isEmpty() ||
                !recipe.getTickInputContents(CWURecipeCapability.CAP).isEmpty()) {
            return recipe;
        }
        int discount = (plan & 255) >> 2;
        int chanceMode = plan & 3;
        GTRecipe upgraded = recipe.copy();
        int max = ChanceLogic.getMaxChancedValue();
        upgraded.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(plan >> 8, max, max, 0)));
        upgraded.tickInputs.computeIfPresent(EURecipeCapability.CAP,
                (capability, contents) -> contents.stream().map(content -> {
                    EnergyStack energy = EURecipeCapability.CAP.of(content.content);
                    long voltage = Math.max(1, Math.round(energy.voltage() * (100 - discount) / 100.0));
                    return new Content(new EnergyStack(voltage, energy.amperage()), content.chance, content.maxChance,
                            content.tierChanceBoost);
                }).toList());
        if (chanceMode != 0) {
            // Preserve quantities and GT's chance logic. AND/XOR groups must still roll as groups.
            upgraded.outputs.replaceAll((capability, contents) -> contents.stream()
                    .map(content -> raiseChance(content, chanceMode)).toList());
            upgraded.tickOutputs.replaceAll((capability, contents) -> contents.stream()
                    .map(content -> raiseChance(content, chanceMode)).toList());
        }
        return upgraded;
    }

    private static Content raiseChance(Content content, int mode) {
        if (content.chance <= 0 || content.chance >= content.maxChance) {
            return content;
        }
        // Keep boosted entries in the chanced group even at the top of the probability range.
        int chance = (int) Math.min(content.maxChance - 1L, mode == 2 ? content.maxChance : content.chance * 2L);
        return new Content(content.content, chance, content.maxChance, content.tierChanceBoost);
    }
}
