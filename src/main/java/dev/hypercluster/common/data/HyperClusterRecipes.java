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

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;

/**
 * GregTech HyperCluster recipes, injected through {@code IGTAddon#addRecipes}. All recipe ids live
 * in the {@code hypercluster} namespace and are part of the telemetry whitelist.
 */
public final class HyperClusterRecipes {

    private HyperClusterRecipes() {}

    public static void init(java.util.function.Consumer<FinishedRecipe> provider) {
        registerMicroBurnerRecipes(provider);
        registerTelemetryOreWashingRecipes(provider);
        registerTelemetryChemicalRecipes(provider);
        registerDataScrubbingRecipes(provider);
        registerFineTuningRecipes(provider);
        registerSurvivalRecipes(provider);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.1 chain — micro burner + telemetry ore washing //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static void registerMicroBurnerRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
        // P03 — burn a telemetry log and a blank carrier into a mining edge chip.
        HyperClusterRecipeTypes.MICRO_BURNER_RECIPES
                .recipeBuilder(HyperCluster.id("burn_mining_edge_chip"))
                .inputItems(new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .outputItems(new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()))
                .save(provider);

        // 0.2 — burn a high-purity mining dataset + carrier into a mining chip: the purified
        // dataset is a verified sample, so burning it costs 1/4 the energy and runs 2x faster
        // than the raw-log recipe (128 EU/t × 64t vs 512 × 128).
        HyperClusterRecipeTypes.MICRO_BURNER_RECIPES
                .recipeBuilder(HyperCluster.id("burn_mining_edge_chip_pure"))
                .inputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .outputItems(new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()))
                .EUt(128)
                .duration(64)
                .save(provider);

        // 0.2 — burn a high-purity chemical dataset + carrier into a chemical edge chip.
        HyperClusterRecipeTypes.MICRO_BURNER_RECIPES
                .recipeBuilder(HyperCluster.id("burn_chemical_edge_chip"))
                .inputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .outputItems(new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()))
                .save(provider);
    }

    private static void registerTelemetryOreWashingRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
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

        // 0.2.1 — LoRA-specialised wash (circuit #3 + a non-consumed mining LoRA card): the rare
        // cobalt by-product chance rises from 1/3 to 2/3. Strict isolation by circuit number.
        HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES
                .recipeBuilder(HyperCluster.id("wash_copper_crushed_ore_to_purified_ore_lora"))
                .inputItems(ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper))
                .notConsumable(new ItemStack(HyperClusterItems.MINING_LORA_CARD.get()))
                .circuitMeta(3)
                .inputFluids(GTMaterials.Water.getFluid(100))
                .outputItems(ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper))
                .chancedOutput(TagPrefix.dust, GTMaterials.Cobalt, "2/3", 0)
                .outputItems(TagPrefix.dust, GTMaterials.Stone)
                .save(provider);

        // 0.2.1 — aligned-chip wash (circuit #4 + a non-consumed aligned mining edge chip):
        // the red-team validated chip runs the same wash at 25% less energy (16 -> 12 EU/t),
        // no adversarial noise interference.
        HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES
                .recipeBuilder(HyperCluster.id("wash_copper_crushed_ore_to_purified_ore_aligned"))
                .inputItems(ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper))
                .notConsumable(new ItemStack(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get()))
                .circuitMeta(4)
                .inputFluids(GTMaterials.Water.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper))
                .chancedOutput(TagPrefix.dust, GTMaterials.Cobalt, "1/3", 0)
                .outputItems(TagPrefix.dust, GTMaterials.Stone)
                .EUt(12)
                .duration(400)
                .save(provider);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.2 chain — EV telemetry chemical processing //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static void registerTelemetryChemicalRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
        // Mirrored base EV chemical recipe: identical inputs/outputs to GTCEu 7.4.0's own
        // chemical_reactor/indium_concentrate_separation (600 EU/t × 50t, circuit #1):
        // 4 aluminium dust + 1000mB indium concentrate -> 1 small indium dust
        // + 4 aluminium sulfite dust + 1000mB lead zinc solution.
        HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES
                .recipeBuilder(HyperCluster.id("indium_concentrate_separation"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Aluminium, 4))
                .circuitMeta(1)
                .inputFluids(GTMaterials.IndiumConcentrate.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium))
                .outputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite, 4))
                .outputFluids(GTMaterials.LeadZincSolution.getFluid(1000))
                .EUt(600)
                .duration(50)
                .save(provider);

        // 0.2 — chip-enhanced separation: HALF the auxiliary aluminium dust (4 -> 2) with the
        // aluminium sulfite output reduced to 2 as well (aluminium conservation 2 -> 2), identical
        // indium/solution yields, chemical edge chip stays retrievable. Strict isolation by circuit #2.
        HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES
                .recipeBuilder(HyperCluster.id("indium_concentrate_separation_chip"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Aluminium, 2))
                .notConsumable(new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()))
                .circuitMeta(2)
                .inputFluids(GTMaterials.IndiumConcentrate.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium))
                .outputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite, 2))
                .outputFluids(GTMaterials.LeadZincSolution.getFluid(1000))
                .EUt(600)
                .duration(50)
                .save(provider);

        // 0.2.1 — LoRA-specialised separation (circuit #3 + a non-consumed chemical LoRA card):
        // DOUBLE the small indium yield from the same 2 aluminium dust (better reagent economy AND
        // yield), aluminium conservation kept at 2 -> 2 sulfite. Strict isolation by circuit #3.
        HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES
                .recipeBuilder(HyperCluster.id("indium_concentrate_separation_lora"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Aluminium, 2))
                .notConsumable(new ItemStack(HyperClusterItems.CHEMICAL_LORA_CARD.get()))
                .circuitMeta(3)
                .inputFluids(GTMaterials.IndiumConcentrate.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium, 2))
                .outputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite, 2))
                .outputFluids(GTMaterials.LeadZincSolution.getFluid(1000))
                .EUt(600)
                .duration(50)
                .save(provider);

        // 0.2.1 — aligned-chip separation (circuit #4 + a non-consumed aligned chemical edge chip):
        // the red-team validated chip runs the base recipe at 25% less energy (600 -> 450 EU/t),
        // no adversarial noise interference.
        HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES
                .recipeBuilder(HyperCluster.id("indium_concentrate_separation_aligned"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Aluminium, 4))
                .notConsumable(new ItemStack(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get()))
                .circuitMeta(4)
                .inputFluids(GTMaterials.IndiumConcentrate.getFluid(1000))
                .outputItems(ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium))
                .outputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite, 4))
                .outputFluids(GTMaterials.LeadZincSolution.getFluid(1000))
                .EUt(450)
                .duration(50)
                .save(provider);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.2 chain — EV data scrubbing tower //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static void registerDataScrubbingRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
        // Scrub raw mining telemetry: log + distilled water -> high-purity mining dataset
        // + clean dataset + 90% corrupted noise + 10% golden labelled samples (0.2.1).
        HyperClusterRecipeTypes.DATA_SCRUBBING_RECIPES
                .recipeBuilder(HyperCluster.id("scrub_mining_telemetry_log"))
                .inputItems(new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()))
                .inputFluids(GTMaterials.DistilledWater.getFluid(1000))
                .outputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()))
                .outputItems(new ItemStack(HyperClusterItems.CLEAN_DATASET.get()))
                .chancedOutput(new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get()), "9/10", 0)
                .chancedOutput(new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()), "1/10", 0)
                .save(provider);

        // Scrub raw chemical telemetry: same contract in the chemical domain.
        HyperClusterRecipeTypes.DATA_SCRUBBING_RECIPES
                .recipeBuilder(HyperCluster.id("scrub_chemical_telemetry_log"))
                .inputItems(new ItemStack(HyperClusterItems.CHEMICAL_TELEMETRY_LOG.get()))
                .inputFluids(GTMaterials.DistilledWater.getFluid(1000))
                .outputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()))
                .outputItems(new ItemStack(HyperClusterItems.CLEAN_DATASET.get()))
                .chancedOutput(new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get()), "9/10", 0)
                .chancedOutput(new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()), "1/10", 0)
                .save(provider);

        // Every output is processable: formatted corrupted noise recovers raw silicon powder.
        HyperClusterRecipeTypes.DATA_SCRUBBING_RECIPES
                .recipeBuilder(HyperCluster.id("recycle_corrupted_noise"))
                .inputItems(new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get(), 4))
                .inputFluids(GTMaterials.DistilledWater.getFluid(250))
                .outputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Silicon))
                .EUt(1024)
                .duration(100)
                .save(provider);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.2.1 — EV LoRA engraving chamber + red-team sandbox //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static void registerFineTuningRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
        // Engrave the mining LoRA card: high-purity mining dataset + blank carrier + golden
        // labelled sample + distilled water. EV power from the multiblock's energy hatch.
        HyperClusterRecipeTypes.LORA_ENGRAVING_RECIPES
                .recipeBuilder(HyperCluster.id("engrave_mining_lora_card"))
                .inputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .inputItems(new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()))
                .inputFluids(GTMaterials.DistilledWater.getFluid(500))
                .outputItems(new ItemStack(HyperClusterItems.MINING_LORA_CARD.get()))
                .save(provider);

        // Engrave the chemical LoRA card: same contract in the chemical domain.
        HyperClusterRecipeTypes.LORA_ENGRAVING_RECIPES
                .recipeBuilder(HyperCluster.id("engrave_chemical_lora_card"))
                .inputItems(new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()))
                .inputItems(new ItemStack(HyperClusterItems.BLANK_CARRIER.get()))
                .inputItems(new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()))
                .inputFluids(GTMaterials.DistilledWater.getFluid(500))
                .outputItems(new ItemStack(HyperClusterItems.CHEMICAL_LORA_CARD.get()))
                .save(provider);

        // Red-team the mining edge chip: corrupted noise serves as adversarial samples; a chip
        // that survives comes out aligned (circuit #4 recipes at 25% less energy).
        HyperClusterRecipeTypes.RED_TEAM_TESTING_RECIPES
                .recipeBuilder(HyperCluster.id("align_mining_edge_chip"))
                .inputItems(new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get(), 2))
                .inputItems(new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()))
                .outputItems(new ItemStack(HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get()))
                .save(provider);

        // Red-team the chemical edge chip: same contract in the chemical domain.
        HyperClusterRecipeTypes.RED_TEAM_TESTING_RECIPES
                .recipeBuilder(HyperCluster.id("align_chemical_edge_chip"))
                .inputItems(new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get(), 2))
                .inputItems(new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()))
                .outputItems(new ItemStack(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get()))
                .save(provider);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.2 — survival (workbench) recipes: the whole chain stays craftable without /give //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static void registerSurvivalRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
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

        // 0.2 — clean datasets format blank carriers: the scrubbing residue has a purpose.
        // 0.2.1 fix: yields 4 per craft so the clean-dataset route clearly beats the base recipe.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("blank_carrier_from_clean_dataset"),
                new ItemStack(HyperClusterItems.BLANK_CARRIER.get(), 4),
                "PDP",
                "WCW",
                'D', new ItemStack(HyperClusterItems.CLEAN_DATASET.get()),
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

        // 0.2 — EV telemetry chemical reactor: the plain GT EV chemical reactor upgraded in place
        // with an EV sensor, a second EV circuit and EV cabling.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("ev_telemetry_chemical_reactor"),
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR.asStack(1),
                " S ",
                "CMC",
                "GWG",
                'S', GTCraftingComponents.SENSOR.get(EV),
                'C', GTCraftingComponents.CIRCUIT.get(EV),
                'M', GTMachines.CHEMICAL_REACTOR[EV].asStack(1),
                'G', Items.GLASS,
                'W', GTCraftingComponents.CABLE.get(EV));

        // 0.2 — data scrubbing tower controller: an EV machine hull with a glass observation
        // stack, two EV circuits and titanium plating.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("data_scrubbing_tower"),
                HyperClusterMachines.DATA_SCRUBBING_TOWER.asStack(1),
                "GCG",
                "CMC",
                "GPG",
                'G', Items.GLASS,
                'C', GTCraftingComponents.CIRCUIT.get(EV),
                'M', GTCraftingComponents.HULL.get(EV),
                'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Titanium));

        // 0.2.1 — LoRA engraving chamber: an EV hull wired behind glass with an EV sensor for
        // precise fine-tune optics and titanium plating.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("lora_engraving_chamber"),
                HyperClusterMachines.LORA_ENGRAVING_CHAMBER.asStack(1),
                "GSG",
                "CMC",
                "GPG",
                'G', Items.GLASS,
                'S', GTCraftingComponents.SENSOR.get(EV),
                'C', GTCraftingComponents.CIRCUIT.get(EV),
                'M', GTCraftingComponents.HULL.get(EV),
                'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Titanium));

        // 0.2.1 — red-team sandbox: an EV hull behind glass with an EV emitter for adversarial
        // interference beams and titanium plating.
        VanillaRecipeHelper.addShapedRecipe(provider, true,
                HyperCluster.id("red_team_sandbox"),
                HyperClusterMachines.RED_TEAM_SANDBOX.asStack(1),
                "GEG",
                "CMC",
                "GPG",
                'G', Items.GLASS,
                'E', GTCraftingComponents.EMITTER.get(EV),
                'C', GTCraftingComponents.CIRCUIT.get(EV),
                'M', GTCraftingComponents.HULL.get(EV),
                'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Titanium));
    }

    /** Simple cross-mod item lookup for recipe ingredients (existence is asserted in GameTests). */
    private static ItemStack gtItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu", path)));
    }
}
