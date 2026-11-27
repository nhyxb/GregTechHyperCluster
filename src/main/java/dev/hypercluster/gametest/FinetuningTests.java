package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.TelemetryChemicalReactorMachine;

import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.EV;

/**
 * 0.2.1 acceptance gametests: the fine-tuning loop.
 *
 * <ul>
 * <li>the 0.2 fixes: clean-dataset carriers yield 4 (covered by SurvivalCraftingTests) and the
 * chip recipe conserves aluminium (covered by DataWorkshopTests.chipChemicalRecipeSavesAluminium,
 * now asserting 2 sulfite out for 2 dust in);</li>
 * <li>golden labelled samples: 10% of both scrub recipes, asserted from the recipe contents;</li>
 * <li>the LoRA engraving chamber (7×5×5 gantry cleanroom multiblock) engraves both domain
 * LoRA cards;</li>
 * <li>the red-team sandbox (7×6×7 octagonal blast dome multiblock) upgrades both edge chips to
 * their aligned forms by burning corrupted noise;</li>
 * <li>circuit #3 LoRA recipes: the chemical reactor doubles the small indium yield; the washer
 * raises the cobalt by-product chance from 1/3 to 2/3;</li>
 * <li>circuit #4 aligned-chip recipes run at 75% of the base energy.</li>
 * </ul>
 */
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
@net.minecraftforge.gametest.GameTestHolder(HyperCluster.MOD_ID)
public class FinetuningTests {

    public static final String FINETUNING_BATCH = "hypercluster_02_1_finetuning";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. the LoRA chamber forms and engraves both domain cards //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 1600)
    public static void loraChamberEngravesBothCards(GameTestHelper helper) {
        ChamberParts parts = placeLoraChamber(helper);
        primeChamberEnergy(parts, helper, 40, 20, 1400);

        helper.runAtTickTime(60, () -> {
            DataWorkshopTests.ensureTowerFormed(parts.controller());
            helper.assertTrue(parts.controller().isFormed(), "LoRA chamber must form before engraving");
        });

        // batch 1: mining LoRA card (2048 EU/t x 300t, no OC headroom -> exactly 300 ticks)
        parts.importBus().getInventory().setStackInSlot(0,
                new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()));
        parts.importBus().getInventory().setStackInSlot(1, new ItemStack(HyperClusterItems.BLANK_CARRIER.get()));
        parts.importBus().getInventory().setStackInSlot(2, new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()));
        parts.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(1000));

        // batch 2: chemical LoRA card
        parts.importBus().getInventory().setStackInSlot(4,
                new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()));
        parts.importBus().getInventory().setStackInSlot(5, new ItemStack(HyperClusterItems.BLANK_CARRIER.get()));
        parts.importBus().getInventory().setStackInSlot(6, new ItemStack(HyperClusterItems.GOLDEN_DATASET.get()));

        helper.runAtTickTime(1500, () -> {
            int miningCards = countBus(parts.exportBus(), HyperClusterItems.MINING_LORA_CARD.get());
            int chemicalCards = countBus(parts.exportBus(), HyperClusterItems.CHEMICAL_LORA_CARD.get());
            helper.assertTrue(miningCards == 1,
                    "the mining batch must engrave exactly one mining LoRA card, got " + miningCards);
            helper.assertTrue(chemicalCards == 1,
                    "the chemical batch must engrave exactly one chemical LoRA card, got " + chemicalCards);
            System.out.printf("[HC-021] lora engraving: mining=%d chemical=%d status=%s%n", miningCards,
                    chemicalCards, parts.controller().recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. the red-team sandbox aligns both edge chips //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 1200)
    public static void redTeamSandboxAlignsBothChips(GameTestHelper helper) {
        ChamberParts parts = placeSandbox(helper);
        primeChamberEnergy(parts, helper, 40, 20, 1000);

        helper.runAtTickTime(60, () -> {
            DataWorkshopTests.ensureTowerFormed(parts.controller());
            if (!parts.controller().isFormed()) {
                var state = parts.controller().getMultiblockState();
                System.out.printf("[HC-021-DIAG] sandbox not formed: origin=%s failPos=%s failRel=%s found=%s err=%s%n",
                        helper.absolutePos(net.minecraft.core.BlockPos.ZERO), state.getPos(),
                        state.getPos() == null ? null :
                                state.getPos().subtract(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)),
                        state.getPos() == null ? null : helper.getLevel().getBlockState(state.getPos()),
                        state.error == null ? "none" : state.error.getErrorInfo().getString());
            }
            helper.assertTrue(parts.controller().isFormed(), "red-team sandbox must form before validating");
        });

        parts.importBus().getInventory().setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()));
        parts.importBus().getInventory().setStackInSlot(1, new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()));
        parts.importBus().getInventory().setStackInSlot(2, new ItemStack(HyperClusterItems.CORRUPTED_NOISE.get(), 4));

        helper.runAtTickTime(1100, () -> {
            int alignedMining = countBus(parts.exportBus(), HyperClusterItems.ALIGNED_MINING_EDGE_CHIP.get());
            int alignedChemical = countBus(parts.exportBus(), HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get());
            helper.assertTrue(alignedMining == 1,
                    "the sandbox must align the mining edge chip, got " + alignedMining);
            helper.assertTrue(alignedChemical == 1,
                    "the sandbox must align the chemical edge chip, got " + alignedChemical);
            helper.assertTrue(parts.importBus().getInventory().getStackInSlot(2).isEmpty(),
                    "all corrupted noise must be consumed as adversarial samples");
            System.out.printf("[HC-021] red team: alignedMining=%d alignedChemical=%d noiseLeft=%d%n", alignedMining,
                    alignedChemical, parts.importBus().getInventory().getStackInSlot(2).getCount());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. circuit #3 — the chemical LoRA recipe doubles the indium yield //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 600)
    public static void loraChemicalRecipeDoublesIndium(GameTestHelper helper) {
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        reactor.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(3));
        reactor.importItems.setStackInSlot(0, dust(GTMaterials.Aluminium, 2));
        reactor.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.CHEMICAL_LORA_CARD.get()));
        reactor.importFluids.setFluidInTank(0, GTMaterials.IndiumConcentrate.getFluid(1000));
        primeReactorEnergy(reactor, helper, 1, 20, 500);

        helper.runAtTickTime(550, () -> {
            int indium = countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium).getItem());
            int sulfite = countItem(reactor, ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite)
                    .getItem());
            helper.assertTrue(indium == 2 && sulfite == 2,
                    "LoRA chemical batch must double indium (2) and conserve aluminium (2 sulfite), got indium=" +
                            indium + " sulfite=" + sulfite);
            helper.assertTrue(reactor.getTelemetryBufferCount() == 1,
                    "LoRA batch is whitelisted and must settle 1 log, got " + reactor.getTelemetryBufferCount());
            ItemStack cardAfter = reactor.importItems.getStackInSlot(1);
            helper.assertTrue(cardAfter.is(HyperClusterItems.CHEMICAL_LORA_CARD.get()),
                    "the chemical LoRA card must stay retrievable, got " + cardAfter);
            System.out.printf("[HC-021] chemical lora: indium=%d sulfite=%d card=%s%n", indium, sulfite, cardAfter);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. circuit #3 — the washer LoRA recipe raises the cobalt chance to 2/3 //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 700)
    public static void loraWashRecipeRaisesCobaltChance(GameTestHelper helper) {
        // recipe-level assertion: the LoRA wash declares cobalt at 2/3 (base and chip run 1/3).
        var lora = recipe(helper, "telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore_lora");
        var base = recipe(helper, "telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore");
        helper.assertTrue(chancedRatio(lora, cobalt()) > 0.66 && chancedRatio(lora, cobalt()) < 0.67,
                "LoRA wash cobalt chance must stay 2/3, ratio=" + chancedRatio(lora, cobalt()));
        helper.assertTrue(chancedRatio(base, cobalt()) > 0.33 && chancedRatio(base, cobalt()) < 0.34,
                "base wash cobalt chance must stay 1/3, ratio=" + chancedRatio(base, cobalt()));

        // runtime side: the #3 recipe settles a whitelisted batch and regular products.
        var washer = placeWasher(helper, new BlockPos(2, 1, 2));
        washer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(3));
        washer.importItems.setStackInSlot(0, ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper));
        washer.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.MINING_LORA_CARD.get()));
        washer.importFluids.setFluidInTank(0, GTMaterials.Water.getFluid(100));
        washer.energyContainer.addEnergy(washer.energyContainer.getEnergyCapacity());
        for (long t = 1; t <= 600; t += 20) {
            helper.runAtTickTime(t, () -> washer.energyContainer
                    .addEnergy(washer.energyContainer.getEnergyCapacity()));
        }

        helper.runAtTickTime(650, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 1,
                    "LoRA wash batch must settle 1 log, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(countItem(washer, ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper)
                    .getItem()) == 1, "LoRA wash must yield the purified ore");
            helper.assertTrue(countItem(washer, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Stone)
                    .getItem()) == 1, "LoRA wash must yield stone dust");
            helper.assertTrue(washer.importItems.getStackInSlot(1).is(HyperClusterItems.MINING_LORA_CARD.get()),
                    "the mining LoRA card must stay retrievable");
            System.out.printf("[HC-021] wash lora: log=%d purified=1 loraCardKept%n",
                    washer.getTelemetryBufferCount());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 5. circuit #4 — aligned chips run at 75% of the base energy //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 900)
    public static void alignedChipRecipesRunCheaper(GameTestHelper helper) {
        // recipe-level: aligned chemical recipe EUt 450 (base 600); aligned wash EUt 12 (base 16).
        var alignedChemical = recipe(helper, "telemetry_chemical_processing/indium_concentrate_separation_aligned");
        var alignedWash = recipe(helper, "telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore_aligned");
        helper.assertTrue(com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRealEUt(alignedChemical)
                .getTotalEU() == 450, "aligned chemical recipe must run at 450 EU/t (75% of 600), got " +
                        com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRealEUt(alignedChemical).getTotalEU());
        helper.assertTrue(
                com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRealEUt(alignedWash).getTotalEU() == 12,
                "aligned wash recipe must run at 12 EU/t (75% of 16), got " +
                        com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRealEUt(alignedWash).getTotalEU());

        // runtime side: the aligned chemical batch settles regular products and keeps the chip.
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        reactor.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(4));
        reactor.importItems.setStackInSlot(0, dust(GTMaterials.Aluminium, 4));
        reactor.importItems.setStackInSlot(1,
                new ItemStack(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get()));
        reactor.importFluids.setFluidInTank(0, GTMaterials.IndiumConcentrate.getFluid(1000));
        primeReactorEnergy(reactor, helper, 1, 20, 800);

        helper.runAtTickTime(850, () -> {
            int indium = countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium).getItem());
            int sulfite = countItem(reactor, ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite)
                    .getItem());
            helper.assertTrue(indium == 1 && sulfite == 4,
                    "aligned chemical batch keeps base products (1 indium, 4 sulfite), got " + indium + "/" +
                            sulfite);
            helper.assertTrue(reactor.getTelemetryBufferCount() == 1,
                    "aligned batch must settle 1 log, got " + reactor.getTelemetryBufferCount());
            helper.assertTrue(
                    reactor.importItems.getStackInSlot(1).is(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get()),
                    "the aligned chip must stay retrievable");
            System.out.printf("[HC-021] aligned chemical: indium=%d sulfite=%d log=%d chipKept%n", indium, sulfite,
                    reactor.getTelemetryBufferCount());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 6. golden samples: 10% of both scrub recipes + full 0.2.1 recipe registration //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = FINETUNING_BATCH, timeoutTicks = 100)
    public static void goldenSamplesAndRecipeRegistration(GameTestHelper helper) {
        String[] expected = { "hypercluster:data_scrubbing/scrub_mining_telemetry_log",
                "hypercluster:data_scrubbing/scrub_chemical_telemetry_log",
                "hypercluster:lora_engraving/engrave_mining_lora_card",
                "hypercluster:lora_engraving/engrave_chemical_lora_card",
                "hypercluster:red_team_testing/align_mining_edge_chip",
                "hypercluster:red_team_testing/align_chemical_edge_chip",
                "hypercluster:telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore_lora",
                "hypercluster:telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore_aligned",
                "hypercluster:telemetry_chemical_processing/indium_concentrate_separation_lora",
                "hypercluster:telemetry_chemical_processing/indium_concentrate_separation_aligned" };
        for (String id : expected) {
            var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.tryParse(id));
            helper.assertTrue(recipe.isPresent(), "missing registered recipe: " + id);
        }
        // both scrub recipes declare the golden sample at exactly 1/10.
        for (String id : new String[] { "hypercluster:data_scrubbing/scrub_mining_telemetry_log",
                "hypercluster:data_scrubbing/scrub_chemical_telemetry_log" }) {
            var scrub = recipe(helper, id.replace("hypercluster:", ""));
            double ratio = chancedRatio(scrub, HyperClusterItems.GOLDEN_DATASET.get());
            helper.assertTrue(Math.abs(ratio - 0.1) < 1e-6,
                    "scrub recipe must drop golden samples at exactly 1/10, ratio=" + ratio);
            double noiseRatio = chancedRatio(scrub, HyperClusterItems.CORRUPTED_NOISE.get());
            helper.assertTrue(Math.abs(noiseRatio - 0.9) < 1e-6,
                    "scrub recipe must drop corrupted noise at exactly 9/10, ratio=" + noiseRatio);
        }
        System.out.printf("[HC-021] registration: %d recipes verified; golden=10%% noise=90%%%n",
                expected.length);
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private record ChamberParts(WorkableElectricMultiblockMachine controller, ItemBusPartMachine importBus,
                                ItemBusPartMachine exportBus, FluidHatchPartMachine fluidHatch,
                                EnergyHatchPartMachine energyHatch) {}

    /**
     * Places the 7×5×5 gantry cleanroom of the LoRA engraving chamber. Bottom corner at
     * (4, 1, 4): y=0 the plascrete platform with the filter-casing stage at local (3,2);
     * y=1..3 cleanroom-glass front/back windows between plascrete posts, the plascrete side
     * walls hosting the hatches, and the controller as the suspended engraving column at local
     * (3,3,2); y=4 the exposed titanium gantry ring with its central crossbeam.
     */
    private static ChamberParts placeLoraChamber(GameTestHelper helper) {
        int bx = 4, by = 1, bz = 4;
        // y=0: the 7×1×5 white plascrete platform; the filter-casing engraving stage embeds in
        // its centre.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(bx + x, by, bz + z),
                        x == 3 && z == 2 ? GTBlocks.FILTER_CASING.get().defaultBlockState() :
                                GTBlocks.PLASTCRETE.get().defaultBlockState());
            }
        }
        // y=1..3: the optical cleanroom — glass front/back walls between plascrete posts, the
        // plascrete side walls carrying the hatches, open air core.
        for (int dy = 1; dy <= 3; dy++) {
            int y = by + dy;
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean frontBack = z == 0 || z == 4;
                    boolean post = x == 0 || x == 6;
                    if (frontBack && !post) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                GTBlocks.CLEANROOM_GLASS.get().defaultBlockState());
                    } else if (post) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                GTBlocks.PLASTCRETE.get().defaultBlockState());
                    }
                    // interior (and the controller slot at (3,3,2)) stays air here
                }
            }
        }
        // The hatches ride the left/right plascrete walls of the first room layer.
        helper.setBlock(new BlockPos(bx, by + 1, bz + 1),
                GTMachines.ITEM_IMPORT_BUS[EV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 6, by + 1, bz + 1),
                GTMachines.ITEM_EXPORT_BUS[EV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by + 1, bz + 2), GTMachines.FLUID_IMPORT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 6, by + 1, bz + 2), GTMachines.ENERGY_INPUT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx, by + 1, bz + 3), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        // GTCEu convention: a fresh maintenance hatch starts with ALL problems; fix like a player.
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by + 1, bz + 3)))
                .fixAllMaintenanceProblems();
        // y=4: the exposed titanium gantry — perimeter ring plus the central crossbeam row.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 5; z++) {
                boolean perimeter = x == 0 || x == 6 || z == 0 || z == 4;
                boolean beam = z == 2;
                if (perimeter || beam) {
                    helper.setBlock(new BlockPos(bx + x, by + 4, bz + z),
                            ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Titanium).defaultBlockState());
                }
            }
        }
        // The controller: the suspended engraving column hanging from the beam centre.
        helper.setBlock(new BlockPos(bx + 3, by + 3, bz + 2),
                HyperClusterMachines.LORA_ENGRAVING_CHAMBER.getBlock().defaultBlockState());
        WorkableElectricMultiblockMachine controller = (WorkableElectricMultiblockMachine) metaMachine(helper,
                new BlockPos(bx + 3, by + 3, bz + 2));
        ItemBusPartMachine importBus = (ItemBusPartMachine) metaMachine(helper,
                new BlockPos(bx, by + 1, bz + 1));
        ItemBusPartMachine exportBus = (ItemBusPartMachine) metaMachine(helper,
                new BlockPos(bx + 6, by + 1, bz + 1));
        FluidHatchPartMachine fluidHatch = (FluidHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by + 1, bz + 2));
        EnergyHatchPartMachine energyHatch = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 6, by + 1, bz + 2));
        return new ChamberParts(controller, importBus, exportBus, fluidHatch, energyHatch);
    }

    /**
     * Places the 7×6×7 octagonal blast dome of the red-team sandbox. Bottom corner at
     * (4, 2, 4): y=0 the octagonal solid-steel floor with the controller console at local
     * (3,0,0) and the hatches beside it; y=1 the wall base ring around the isolated sturdy
     * pedestal at local (3,1..2,3); y=2..3 the 3×2 tempered-glass windows with steel-frame ribs;
     * y=4 the sturdy upper belt; y=5 the stepped-in roof plate.
     */
    private static ChamberParts placeSandbox(GameTestHelper helper) {
        // by=2: the cut-corner air cells of the octagon floor must hover one above the bedrock.
        int bx = 4, by = 2, bz = 4;
        boolean[][] octagon = new boolean[7][7];
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean cornerCut = (x == 0 || x == 6) && (z == 0 || z == 6);
                octagon[x][z] = !cornerCut;
            }
        }
        // y=0: the octagonal floor; the controller console and its hatch row sit on the front.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                if (octagon[x][z] && !(x == 3 && z == 0) && !(x == 1 && z == 0) && !(x == 2 && z == 0) &&
                        !(x == 4 && z == 0) && !(x == 5 && z == 0) && !(x == 0 && z == 2)) {
                    helper.setBlock(new BlockPos(bx + x, by, bz + z),
                            GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(new BlockPos(bx + 3, by, bz),
                HyperClusterMachines.RED_TEAM_SANDBOX.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 1, by, bz), GTMachines.ITEM_IMPORT_BUS[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 5, by, bz), GTMachines.ITEM_EXPORT_BUS[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.ENERGY_INPUT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 2), GTMachines.FLUID_IMPORT_HATCH[EV].getBlock()
                .defaultBlockState());
        // GTCEu convention: a fresh maintenance hatch starts with ALL problems; fix like a player.
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 2, by, bz)))
                .fixAllMaintenanceProblems();
        // y=1..4: the wall ring — solid steel base, the window band with steel-frame ribs, the
        // sturdy upper belt — around the isolated sturdy pedestal column.
        for (int dy = 1; dy <= 4; dy++) {
            int y = by + dy;
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean ring = octagon[x][z] && (x == 0 || x == 6 || z == 0 || z == 6);
                    boolean pedestal = x == 3 && z == 3 && dy <= 2;
                    boolean windowBand = dy == 2 || dy == 3;
                    boolean window = windowBand &&
                            ((z == 0 || z == 6) ? (x >= 2 && x <= 4) : (x == 0 || x == 6) && (z >= 2 && z <= 4));
                    boolean rib = windowBand && ring && !window;
                    if (pedestal) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                GTBlocks.CASING_HSSE_STURDY.get().defaultBlockState());
                    } else if (window) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                GTBlocks.CASING_TEMPERED_GLASS.get().defaultBlockState());
                    } else if (rib) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel).defaultBlockState());
                    } else if (ring) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z), dy == 4 ?
                                GTBlocks.CASING_HSSE_STURDY.get().defaultBlockState() :
                                GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState());
                    }
                }
            }
        }
        // y=5: the stepped-in roof plate (5×5 inset by one from the ring).
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(bx + x, by + 5, bz + z),
                        GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState());
            }
        }
        WorkableElectricMultiblockMachine controller = (WorkableElectricMultiblockMachine) metaMachine(helper,
                new BlockPos(bx + 3, by, bz));
        ItemBusPartMachine importBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx + 1, by, bz));
        ItemBusPartMachine exportBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx + 5, by, bz));
        FluidHatchPartMachine fluidHatch = (FluidHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by, bz + 2));
        EnergyHatchPartMachine energyHatch = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 4, by, bz));
        return new ChamberParts(controller, importBus, exportBus, fluidHatch, energyHatch);
    }

    private static void primeChamberEnergy(ChamberParts parts, GameTestHelper helper, long firstRefillTick,
                                           long refillEvery, long lastRefillTick) {
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> parts.energyHatch().energyContainer
                    .addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity()));
        }
        parts.energyHatch().energyContainer.addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity());
    }

    private static Object metaMachine(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof IMachineBlockEntity, "expected a machine block entity at " + pos);
        return ((IMachineBlockEntity) be).getMetaMachine();
    }

    private static TelemetryChemicalReactorMachine placeChemicalReactor(GameTestHelper helper,
                                                                        BlockPos relativePos) {
        helper.setBlock(relativePos,
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR.getBlock().defaultBlockState());
        return (TelemetryChemicalReactorMachine) ((IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static dev.hypercluster.common.machine.TelemetryOreWasherMachine placeWasher(GameTestHelper helper,
                                                                                         BlockPos relativePos) {
        helper.setBlock(relativePos, HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.getBlock().defaultBlockState());
        return (dev.hypercluster.common.machine.TelemetryOreWasherMachine) ((IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static void primeReactorEnergy(TelemetryChemicalReactorMachine reactor, GameTestHelper helper,
                                           long firstRefillTick, long refillEvery, long lastRefillTick) {
        reactor.energyContainer.addEnergy(reactor.energyContainer.getEnergyCapacity());
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> reactor.energyContainer
                    .addEnergy(reactor.energyContainer.getEnergyCapacity()));
        }
    }

    private static ItemStack dust(com.gregtechceu.gtceu.api.data.chemical.material.Material material, int count) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.dust, material);
        stack.setCount(count);
        return stack;
    }

    private static int countItem(SimpleTieredMachine machine, net.minecraft.world.item.Item item) {
        int found = 0;
        for (int i = 0; i < machine.exportItems.getSlots(); i++) {
            ItemStack stack = machine.exportItems.getStackInSlot(i);
            if (stack.getItem() == item) found += stack.getCount();
        }
        return found;
    }

    private static int countBus(ItemBusPartMachine bus, net.minecraft.world.item.Item item) {
        int found = 0;
        for (int i = 0; i < bus.getInventory().getSlots(); i++) {
            ItemStack stack = bus.getInventory().getStackInSlot(i);
            if (stack.getItem() == item) found += stack.getCount();
        }
        return found;
    }

    private static GTRecipe recipe(GameTestHelper helper, String path) {
        var recipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(HyperCluster.MOD_ID, path)).orElse(null);
        helper.assertTrue(recipe instanceof GTRecipe, "missing hypercluster:" + path + " GTRecipe");
        return (GTRecipe) recipe;
    }

    private static net.minecraft.world.item.Item cobalt() {
        return ChemicalHelper.get(TagPrefix.dust, GTMaterials.Cobalt).getItem();
    }

    /** The chance/maxChance ratio of the chanced content of {@code item} in {@code recipe}. */
    private static double chancedRatio(GTRecipe recipe, net.minecraft.world.item.Item item) {
        for (var content : recipe.getOutputContents(
                com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)) {
            if (content.content instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.is(item)) {
                        return content.chance / (double) content.maxChance;
                    }
                }
            }
        }
        throw new IllegalStateException("no chanced content for " + item);
    }
}
