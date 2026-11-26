package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.GTCraftingComponents;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.item.HyperClusterItems;

import static com.gregtechceu.gtceu.api.GTValues.HV;

/**
 * GregTech HyperCluster recipes, injected through {@code IGTAddon#addRecipes}. All recipe ids live
 * in the {@code hypercluster} namespace and are part of the telemetry whitelist.
 */
public final class HyperClusterRecipes {

    private HyperClusterRecipes() {}

    public static void init(java.util.function.Consumer<FinishedRecipe> provider) {
        // P03 — burn a telemetry log and a blank carrier into a mining edge chip.
        HyperClusterRecipeTypes.MICRO_BURNER_RECIPES
                .recipeBuilder(HyperCluster.id("burn_mining_edge_chip"))
                .inputItems(new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .outputItems(new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()))
                .save(provider);

        // Mirrored base wash recipe: identical inputs/outputs to GTCEu's own
        // wash_copper_crushed_ore_to_purified_ore (circuit #1, 1000 mB water).
        HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES
                .recipeBuilder(HyperCluster.id("wash_copper_crushed_ore_to_purified_ore"))
                .inputItems(ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper))
                .circuitMeta(1)
                .inputFluids(GTMaterials.Water.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper))
                .chancedOutput(TagPrefix.dust, GTMaterials.Cobalt, "1/3", 0)
                .outputItems(TagPrefix.dust, GTMaterials.Stone)
                .save(provider);

        // P03 — chip-enhanced wash: 90% less water, same products, chip stays retrievable.
        // Strict isolation from the base recipe by programmed circuit #2.
        HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES
                .recipeBuilder(HyperCluster.id("wash_copper_crushed_ore_to_purified_ore_chip"))
                .inputItems(ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper))
                .notConsumable(new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()))
                .circuitMeta(2)
                .inputFluids(GTMaterials.Water.getFluid(100))
                .outputItems(ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper))
                .chancedOutput(TagPrefix.dust, GTMaterials.Cobalt, "1/3", 0)
                .outputItems(TagPrefix.dust, GTMaterials.Stone)
                .save(provider);

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // P05 — survival (workbench) recipes: the whole 0.1 chain is craftable without /give. //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // Blank carrier: a silicon wafer in a polyethylene shell, formatted by one HV circuit.
        // Yields 2 carriers per craft.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("blank_carrier"),
                new ItemStack(HyperClusterItems.BLANK_CARRIER.get(), 2),
                "PWP",
                "PCP",
                'W', gtItem("silicon_wafer"),
                'C', GTCraftingComponents.CIRCUIT.get(HV),
                'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Polyethylene));

        // HV micro burner: the GT laser-engraver pattern at HV (emitter + circuit + cables around
        // an HV machine hull, glass for the burn chamber).
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("hv_micro_burner"),
                HyperClusterMachines.HV_MICRO_BURNER.asStack(1),
                "ECE",
                "GMG",
                "WCW",
                'E', GTCraftingComponents.EMITTER.get(HV),
                'C', GTCraftingComponents.CIRCUIT.get(HV),
                'M', GTCraftingComponents.HULL.get(HV),
                'G', Items.GLASS,
                'W', GTCraftingComponents.CABLE.get(HV));

        // HV telemetry ore washer: an ordinary HV ore washer upgraded in place with an HV sensor,
        // a second HV circuit and HV cabling.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("hv_telemetry_ore_washer"),
                HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.asStack(1),
                " S ",
                "CMC",
                "GWG",
                'S', GTCraftingComponents.SENSOR.get(HV),
                'C', GTCraftingComponents.CIRCUIT.get(HV),
                'M', GTMachines.ORE_WASHER[HV].asStack(1),
                'G', Items.GLASS,
                'W', GTCraftingComponents.CABLE.get(HV));
    }

    /** Simple cross-mod item lookup for recipe ingredients (existence is asserted in GameTests). */
    private static ItemStack gtItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu", path)));
    }
}
