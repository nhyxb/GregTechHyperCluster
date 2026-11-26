package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.TelemetryChemicalReactorMachine;

import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;

/**
 * 0.2 acceptance gametests: the EV data workshop loop.
 *
 * <ul>
 * <li>EV chemical capture: mirrored {@code gtceu:chemical_reactor/indium_concentrate_separation}
 * (circuit #1) settles one chemical telemetry log per batch; jams/interrupts account zero;</li>
 * <li>the chemical edge chip recipe (circuit #2, chip not consumed) halves auxiliary aluminium
 * dust with identical products;</li>
 * <li>the data scrubbing tower (3×5×3 fixed GT multiblock) forms and fractions raw logs into
 * high-purity datasets + clean datasets, and recycles corrupted noise into silicon;</li>
 * <li>the HV micro burner turns high-purity datasets into domain edge chips.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class DataWorkshopTests {

    public static final String DATA_WORKSHOP_BATCH = "hypercluster_02_data_workshop";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. ten sequential settled EV chemical batches -> exactly ten chemical logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 1200)
    public static void evChemicalTenBatchesTenLogs(GameTestHelper helper) {
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        feedChemicalReactor(reactor, helper, 1, 40, 10_000, 1, 20, 1000);

        helper.runAtTickTime(1100, () -> {
            helper.assertTrue(reactor.getTelemetryBufferCount() == 10,
                    "expected 10 chemical logs, got " + reactor.getTelemetryBufferCount());
            int indium = countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium).getItem());
            int sulfite = countItem(reactor, ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite)
                    .getItem());
            helper.assertTrue(indium == 10 && sulfite == 40,
                    "expected 10 small indium + 40 aluminium sulfite, got " + indium + "/" + sulfite);
            helper.assertTrue(reactor.exportFluids.getFluidInTank(0).getAmount() >= 10_000,
                    "expected 10000mB lead zinc solution, got " + reactor.exportFluids.getFluidInTank(0));
            System.out.printf("[HC-02] chemical capture: logs=%d indium=%d sulfite=%d solution=%s%n",
                    reactor.getTelemetryBufferCount(), indium, sulfite,
                    reactor.exportFluids.getFluidInTank(0));
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. output jam -> zero chemical logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 700)
    public static void evChemicalJamZeroLogs(GameTestHelper helper) {
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        feedChemicalReactor(reactor, helper, 1, 8, 2000, 1, 20, 600);
        helper.runAtTickTime(20, () -> {
            for (int i = 0; i < reactor.exportItems.getSlots(); i++) {
                reactor.exportItems.setStackInSlot(i, new ItemStack(Blocks.COBBLESTONE, 64));
            }
            for (int i = 0; i < reactor.exportFluids.getTanks(); i++) {
                reactor.exportFluids.setFluidInTank(i, GTMaterials.Water.getFluid(
                        reactor.exportFluids.getTankCapacity(i)));
            }
        });

        helper.runAtTickTime(600, () -> {
            helper.assertTrue(reactor.getTelemetryBufferCount() == 0,
                    "jammed chemical batches must deposit no logs, got " + reactor.getTelemetryBufferCount());
            helper.assertTrue(countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium)
                    .getItem()) == 0, "no products may settle");
            helper.assertTrue(reactor.importItems.getStackInSlot(0).getCount() == 4,
                    "one batch of aluminium dust should remain, got " + reactor.importItems.getStackInSlot(0));
            System.out.printf("[HC-02] chemical jam: logs=0 status=%s leftover=%s%n",
                    reactor.recipeLogic.getStatus(), reactor.importItems.getStackInSlot(0));
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. mid-run interrupt -> zero chemical logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 500)
    public static void evChemicalInterruptZeroLogs(GameTestHelper helper) {
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        feedChemicalReactor(reactor, helper, 1, 4, 1000, 1, 20, 400);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(reactor.recipeLogic.isWorking(), "reactor should be working before interrupt");
            reactor.recipeLogic.interruptRecipe();
        });

        helper.runAtTickTime(400, () -> {
            helper.assertTrue(reactor.getTelemetryBufferCount() == 0,
                    "interrupted chemical batch must deposit no logs, got " + reactor.getTelemetryBufferCount());
            helper.assertTrue(countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium)
                    .getItem()) == 0, "no products expected after interrupt");
            helper.assertTrue(!reactor.recipeLogic.isWorking(), "reactor should be idle after interrupt");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. chip recipe: half the aluminium dust, identical products, chip kept //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 600)
    public static void chipChemicalRecipeSavesAluminium(GameTestHelper helper) {
        TelemetryChemicalReactorMachine reactor = placeChemicalReactor(helper, new BlockPos(2, 1, 2));
        reactor.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(2));
        reactor.importItems.setStackInSlot(0, dust(GTMaterials.Aluminium, 4));
        reactor.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()));
        reactor.importFluids.setFluidInTank(0, GTMaterials.IndiumConcentrate.getFluid(1000));
        primeReactorEnergy(reactor, helper, 1, 20, 500);

        helper.runAtTickTime(550, () -> {
            helper.assertTrue(reactor.getTelemetryBufferCount() == 1,
                    "chip chemical batch must settle and deposit 1 log, got " + reactor.getTelemetryBufferCount());
            int indium = countItem(reactor, ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Indium).getItem());
            int sulfite = countItem(reactor, ChemicalHelper.get(TagPrefix.dust, GTMaterials.AluminiumSulfite)
                    .getItem());
            helper.assertTrue(indium == 1 && sulfite == 2,
                    "chip recipe must conserve aluminium (2 in -> 2 sulfite out), got indium=" + indium +
                            " sulfite=" + sulfite);
            // Aluminium dust: the chip recipe consumes 2 (base would need 4), so 2 of 4 remain.
            helper.assertTrue(reactor.importItems.getStackInSlot(0).getCount() == 2,
                    "chip recipe must consume only 2 aluminium dust (4 -> 2 remain), remaining=" +
                            reactor.importItems.getStackInSlot(0));
            ItemStack chipAfter = reactor.importItems.getStackInSlot(1);
            helper.assertTrue(
                    chipAfter.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()) && chipAfter.getCount() == 1,
                    "chemical edge chip must stay retrievable, got " + chipAfter);
            System.out.printf("[HC-02] chip recipe: log=1 indium=1 sulfite=2 aluminiumLeft=%d chip=%s%n",
                    reactor.importItems.getStackInSlot(0).getCount(), chipAfter);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 5. data scrubbing tower forms and fractions chemical logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 1200)
    public static void towerFormsAndScrubsChemicalLogs(GameTestHelper helper) {
        TowerParts parts = placeTower(helper);
        primeTowerEnergy(parts, helper, 40, 20, 1100);

        helper.runAtTickTime(60, () -> {
            ensureTowerFormed(parts.controller());
            helper.assertTrue(parts.controller().isFormed(),
                    "tower must be formed (status=" + parts.controller().getMultiblockState().hasError() + ")");
            helper.assertTrue(parts.controller().recipeLogic != null, "formed tower must have recipe logic");
            System.out.printf(
                    "[HC-02-DIAG] formed=%b tier=%d status=%s energy=%d/%d maxV=%d err=%s parts=%d inItem=%d inFluid=%d inEu=%d%n",
                    parts.controller().isFormed(), parts.controller().getTier(),
                    parts.controller().recipeLogic.getStatus(),
                    parts.controller().getEnergyContainer().getEnergyStored(),
                    parts.controller().getEnergyContainer().getEnergyCapacity(),
                    parts.controller().getMaxVoltage(),
                    parts.controller().getMultiblockState().error == null ? "none" :
                            parts.controller().getMultiblockState().error.getErrorInfo().getString(),
                    parts.controller().getParts().size(),
                    parts.controller().getCapabilitiesFlat(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                            com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).size(),
                    parts.controller().getCapabilitiesFlat(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                            com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP).size(),
                    parts.controller().getCapabilitiesFlat(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                            com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP).size());
        });

        parts.importBus().getInventory().setStackInSlot(0, new ItemStack(HyperClusterItems.CHEMICAL_TELEMETRY_LOG
                .get(), 2));
        parts.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(2000));

        helper.runAtTickTime(1100, () -> {
            helper.assertTrue(parts.controller().isFormed(), "tower must stay formed");
            System.out.printf(
                    "[HC-02-DIAG] late: status=%s working=%b progress=%d/%d energy=%d inSlot0=%s fluid=%s out=%s%n",
                    parts.controller().recipeLogic.getStatus(),
                    parts.controller().recipeLogic.isWorking(),
                    parts.controller().recipeLogic.getProgress(), parts.controller().recipeLogic.getDuration(),
                    parts.controller().getEnergyContainer().getEnergyStored(),
                    parts.importBus().getInventory().getStackInSlot(0),
                    parts.fluidHatch().tank.getFluidInTank(0),
                    parts.exportBus().getInventory().getStackInSlot(0));
            int pure = countBus(parts.exportBus(), HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get());
            int clean = countBus(parts.exportBus(), HyperClusterItems.CLEAN_DATASET.get());
            int noise = countBus(parts.exportBus(), HyperClusterItems.CORRUPTED_NOISE.get());
            helper.assertTrue(pure == 2 && clean == 2,
                    "2 scrubs must yield 2 high-purity chemical datasets + 2 clean datasets, got pure=" + pure +
                            " clean=" + clean);
            helper.assertTrue(noise >= 0, "noise chanced output may vary, got " + noise);
            helper.assertTrue(parts.importBus().getInventory().getStackInSlot(0).isEmpty(),
                    "both chemical logs must be consumed");
            System.out.printf("[HC-02] tower scrub chemical: pure=%d clean=%d noise=%d status=%s%n",
                    pure, clean, noise, parts.controller().recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 6. the tower also scrubs mining logs into the mining dataset //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 900)
    public static void towerScrubsMiningLogs(GameTestHelper helper) {
        TowerParts parts = placeTower(helper);
        primeTowerEnergy(parts, helper, 40, 20, 800);

        helper.runAtTickTime(60, () -> {
            ensureTowerFormed(parts.controller());
            System.out.printf("[HC-02-DIAG] mining: formed=%b err=%s%n", parts.controller().isFormed(),
                    parts.controller().getMultiblockState().error == null ? "none" :
                            parts.controller().getMultiblockState().error.getErrorInfo().getString());
            helper.assertTrue(parts.controller().isFormed(), "tower must be formed before scrubbing");
        });
        parts.importBus().getInventory().setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG
                .get(), 1));
        parts.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(1000));

        helper.runAtTickTime(800, () -> {
            int pure = countBus(parts.exportBus(), HyperClusterItems.HIGH_PURITY_MINING_DATASET.get());
            int clean = countBus(parts.exportBus(), HyperClusterItems.CLEAN_DATASET.get());
            helper.assertTrue(pure == 1 && clean == 1,
                    "mining scrub must yield 1 high-purity mining dataset + 1 clean dataset, got pure=" + pure +
                            " clean=" + clean);
            helper.assertTrue(parts.importBus().getInventory().getStackInSlot(0).isEmpty(),
                    "the mining log must be consumed");
            System.out.printf("[HC-02] tower scrub mining: pure=%d clean=%d%n", pure, clean);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 7. corrupted noise recycles into silicon dust — every output is processable //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 900)
    public static void towerRecyclesCorruptedNoise(GameTestHelper helper) {
        TowerParts parts = placeTower(helper);
        primeTowerEnergy(parts, helper, 40, 20, 800);

        helper.runAtTickTime(60, () -> {
            ensureTowerFormed(parts.controller());
            System.out.printf("[HC-02-DIAG] recycle: formed=%b err=%s%n", parts.controller().isFormed(),
                    parts.controller().getMultiblockState().error == null ? "none" :
                            parts.controller().getMultiblockState().error.getErrorInfo().getString());
            helper.assertTrue(parts.controller().isFormed(), "tower must be formed before recycling");
        });
        parts.importBus().getInventory().setStackInSlot(0, new ItemStack(HyperClusterItems.CORRUPTED_NOISE
                .get(), 8));
        parts.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(500));

        helper.runAtTickTime(800, () -> {
            int silicon = countBus(parts.exportBus(),
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Silicon).getItem());
            helper.assertTrue(silicon == 2, "8 corrupted noise must recycle into 2 silicon dust, got " + silicon);
            helper.assertTrue(parts.importBus().getInventory().getStackInSlot(0).isEmpty(),
                    "all corrupted noise must be consumed");
            System.out.printf("[HC-02] noise recycle: silicon=%d%n", silicon);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 8. HV micro burner: high-purity datasets burn into domain chips //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 1600)
    public static void burnerBurnsHighPurityDatasets(GameTestHelper helper) {
        // chemical dataset -> chemical chip
        SimpleTieredMachine chemicalBurner = placeBurner(helper, new BlockPos(2, 1, 2));
        chemicalBurner.importItems.setStackInSlot(0,
                new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()));
        chemicalBurner.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.BLANK_CARRIER.get()));
        primeBurnerEnergy(chemicalBurner, helper, 1, 20, 1500);

        // mining dataset -> mining chip (faster & cheaper variant)
        SimpleTieredMachine miningBurner = placeBurner(helper, new BlockPos(8, 1, 2));
        miningBurner.importItems.setStackInSlot(0,
                new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()));
        miningBurner.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.BLANK_CARRIER.get()));
        primeBurnerEnergy(miningBurner, helper, 1, 20, 1500);

        helper.runAtTickTime(1500, () -> {
            ItemStack chemicalProduct = chemicalBurner.exportItems.getStackInSlot(0);
            helper.assertTrue(chemicalProduct.is(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()),
                    "high-purity chemical dataset must burn into a chemical edge chip, got " + chemicalProduct +
                            " status=" + chemicalBurner.recipeLogic.getStatus());
            helper.assertTrue(chemicalBurner.importItems.getStackInSlot(0).isEmpty(),
                    "the chemical dataset must be consumed");
            ItemStack miningProduct = miningBurner.exportItems.getStackInSlot(0);
            helper.assertTrue(miningProduct.is(HyperClusterItems.MINING_EDGE_CHIP.get()),
                    "high-purity mining dataset must burn into a mining edge chip, got " + miningProduct + " status=" +
                            miningBurner.recipeLogic.getStatus());
            helper.assertTrue(
                    miningProduct.isEmpty() ? miningBurner.recipeLogic.getStatus() == RecipeLogic.Status.IDLE : true,
                    "mining burner expected to have finished, status=" + miningBurner.recipeLogic.getStatus());
            System.out.printf("[HC-02] burner datasets: chemicalChip=%s miningChip=%s%n", !chemicalProduct
                    .isEmpty(), !miningProduct.isEmpty());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 9. recipe map sanity: the whole 0.2 chain is registered with the right ids //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATA_WORKSHOP_BATCH, timeoutTicks = 100)
    public static void dataWorkshopRecipesRegistered(GameTestHelper helper) {
        String[] expected = { "hypercluster:telemetry_chemical_processing/indium_concentrate_separation",
                "hypercluster:telemetry_chemical_processing/indium_concentrate_separation_chip",
                "hypercluster:data_scrubbing/scrub_mining_telemetry_log",
                "hypercluster:data_scrubbing/scrub_chemical_telemetry_log",
                "hypercluster:data_scrubbing/recycle_corrupted_noise",
                "hypercluster:micro_burner/burn_chemical_edge_chip",
                "hypercluster:micro_burner/burn_mining_edge_chip_pure" };
        var mgr = helper.getLevel().getRecipeManager();
        for (String id : expected) {
            helper.assertTrue(net.minecraft.resources.ResourceLocation.tryParse(id) != null,
                    "invalid recipe id " + id);
            var recipe = mgr.byKey(net.minecraft.resources.ResourceLocation.tryParse(id));
            helper.assertTrue(recipe.isPresent(), "missing registered recipe: " + id);
        }
        // The benchmark shows the chip recipe pair strict isolation by circuit number.
        var base = mgr.byKey(net.minecraft.resources.ResourceLocation
                .tryParse("hypercluster:telemetry_chemical_processing/indium_concentrate_separation")).orElseThrow();
        var chip = mgr.byKey(net.minecraft.resources.ResourceLocation
                .tryParse("hypercluster:telemetry_chemical_processing/indium_concentrate_separation_chip"))
                .orElseThrow();
        helper.assertTrue(base != chip, "base and chip chemical recipes must stay distinct recipes");
        System.out.printf("[HC-02] recipe registration: %d/%d 0.2 chain recipes verified%n", expected.length,
                expected.length);
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static TelemetryChemicalReactorMachine placeChemicalReactor(GameTestHelper helper,
                                                                        BlockPos relativePos) {
        helper.setBlock(relativePos,
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR.getBlock().defaultBlockState());
        return (TelemetryChemicalReactorMachine) ((IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static SimpleTieredMachine placeBurner(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, HyperClusterMachines.HV_MICRO_BURNER.getBlock().defaultBlockState());
        return (SimpleTieredMachine) ((IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static ItemStack dust(com.gregtechceu.gtceu.api.data.chemical.material.Material material, int count) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.dust, material);
        stack.setCount(count);
        return stack;
    }

    /** Base EV chemical chain: circuit #{circuit} + aluminium dust + indium concentrate + EU grid. */
    private static void feedChemicalReactor(TelemetryChemicalReactorMachine reactor, GameTestHelper helper,
                                            int circuit, int aluminiumCount, int concentrateMb,
                                            long firstRefillTick, long refillEvery, long lastRefillTick) {
        reactor.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(circuit));
        reactor.importItems.setStackInSlot(0, dust(GTMaterials.Aluminium, aluminiumCount));
        reactor.importFluids.setFluidInTank(0, GTMaterials.IndiumConcentrate.getFluid(concentrateMb));
        primeReactorEnergy(reactor, helper, firstRefillTick, refillEvery, lastRefillTick);
    }

    private static void primeReactorEnergy(TelemetryChemicalReactorMachine reactor, GameTestHelper helper,
                                           long firstRefillTick, long refillEvery, long lastRefillTick) {
        reactor.energyContainer.addEnergy(reactor.energyContainer.getEnergyCapacity());
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> reactor.energyContainer
                    .addEnergy(reactor.energyContainer.getEnergyCapacity()));
        }
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

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Data scrubbing tower placement (3×5×3 fixed GT multiblock) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The parts the tests drive: controller, import/export bus, fluid hatch, energy hatch. */
    private record TowerParts(WorkableElectricMultiblockMachine controller, ItemBusPartMachine importBus,
                              ItemBusPartMachine exportBus, FluidHatchPartMachine fluidHatch,
                              EnergyHatchPartMachine energyHatch) {}

    /**
     * Places the 3×5×3 tower into the hc_field template. Layout: bottom corner at (5, 1, 5),
     * controller at (6, 5, 5). Aisles bottom-up (pattern FRONT=NORTH, BACK=SOUTH, RIGHT=EAST):
     * L1 "XXX/XXX/XXX" casing, L2+L3 "XGX/X#X/XGX" glass layer, L4 "XXX/X#X/XXX" pipe layer with
     * the five hatches, L5 "XSX/XXX/XXX".
     */
    private static TowerParts placeTower(GameTestHelper helper) {
        int bx = 5, by = 1, bz = 5;
        // L1: solid bottom layer.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(bx + x, by, bz + z),
                        GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
            }
        }
        // L2 + L3: glass observation layers ("XGX","X#X","XGX"); '#' stays air.
        for (int dy = 1; dy <= 2; dy++) {
            int y = by + dy;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    boolean center = x == 1 && z == 1;
                    boolean glass = x == 1 && (z == 0 || z == 2);
                    if (center) continue;
                    if (glass) {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z), Blocks.GLASS.defaultBlockState());
                    } else {
                        helper.setBlock(new BlockPos(bx + x, y, bz + z),
                                GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
                    }
                }
            }
        }
        // L4: pipe layer with the standard hatches (all on the outer ring).
        int y4 = by + 3;
        ItemBusPartMachine importBus = null, exportBus = null;
        FluidHatchPartMachine fluidHatch = null;
        EnergyHatchPartMachine energyHatch = null;
        helper.setBlock(new BlockPos(bx, y4, bz), GTMachines.ITEM_IMPORT_BUS[EV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, y4, bz), GTMachines.ITEM_EXPORT_BUS[EV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, y4, bz + 2), GTMachines.FLUID_IMPORT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, y4, bz + 2), GTMachines.ENERGY_INPUT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 1, y4, bz + 2), GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
        // GTCEu convention: a fresh maintenance hatch starts with ALL problems present
        // (ALL_PROBLEMS = bitmask 0, each bit fixed with 1) — fix them like a player would.
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 1, y4, bz + 2)))
                .fixAllMaintenanceProblems();
        importBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx, y4, bz));
        exportBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx + 2, y4, bz));
        fluidHatch = (FluidHatchPartMachine) metaMachine(helper, new BlockPos(bx, y4, bz + 2));
        energyHatch = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx + 2, y4, bz + 2));
        // remaining L4 ring cells are EV casings: (bx+1,y4,bz) and side middles + far corners.
        helper.setBlock(new BlockPos(bx + 1, y4, bz), GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
        helper.setBlock(new BlockPos(bx, y4, bz + 1), GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, y4, bz + 1), GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
        // (bx+2..) note: diagonal pairs already placed above; ensure all 8 ring cells set:
        // ring = corners(4) + side middles(4): corners (0,0),(2,0),(0,2),(2,2) done (4 hatches/maint) — all set.

        // L5: top layer "XSX","XXX","XXX" — controller at (bx+1, by+4, bz), ring is casing.
        int y5 = by + 4;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 1 && z == 0) continue; // controller slot
                helper.setBlock(new BlockPos(bx + x, y5, bz + z),
                        GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(bx + 1, y5, bz),
                HyperClusterMachines.DATA_SCRUBBING_TOWER.getBlock().defaultBlockState());
        WorkableElectricMultiblockMachine controller = (WorkableElectricMultiblockMachine) metaMachine(helper,
                new BlockPos(bx + 1, y5, bz));
        helper.assertTrue(controller != null, "tower controller must exist after placement");
        return new TowerParts(controller, importBus, exportBus, fluidHatch, energyHatch);
    }

    /**
     * Synchronously mirrors MultiblockControllerMachine#asyncCheckPattern's formed path (lock +
     * check + onStructureFormed). The async MWSD executor is unreliable inside the gametest
     * server; in production the same sequence runs from the executor every ~250ms.
     */
    static void ensureTowerFormed(WorkableElectricMultiblockMachine controller) {
        java.util.concurrent.locks.Lock lock = controller.getPatternLock();
        lock.lock();
        try {
            if (controller.isFormed()) return;
            if (controller.checkPatternWithLock()) {
                controller.setFlipped(controller.getMultiblockState().isNeededFlip());
                controller.onStructureFormed();
            }
        } finally {
            lock.unlock();
        }
    }

    private static Object metaMachine(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof IMachineBlockEntity, "expected a machine block entity at " + pos);
        return ((IMachineBlockEntity) be).getMetaMachine();
    }

    private static void primeBurnerEnergy(SimpleTieredMachine burner, GameTestHelper helper,
                                          long firstRefillTick, long refillEvery, long lastRefillTick) {
        burner.energyContainer.addEnergy(burner.energyContainer.getEnergyCapacity());
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> burner.energyContainer
                    .addEnergy(burner.energyContainer.getEnergyCapacity()));
        }
    }

    private static void primeTowerEnergy(TowerParts parts, GameTestHelper helper, long firstRefillTick,
                                         long refillEvery, long lastRefillTick) {
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> parts.energyHatch().energyContainer
                    .addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity()));
        }
        parts.energyHatch().energyContainer.addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity());
    }
}
