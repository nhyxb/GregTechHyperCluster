package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterFluids;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.OpenWeightModuleItem;
import dev.hypercluster.common.machine.TelemetryHatchMachine;
import dev.hypercluster.common.machine.datacenter.DatacenterRating;
import dev.hypercluster.common.machine.datacenter.IvInferenceHatchMachine;
import dev.hypercluster.common.machine.datacenter.LiquidDatacenterRating;
import dev.hypercluster.common.machine.datacenter.RackHeatRecovery;
import dev.hypercluster.common.machine.datacenter.ShutdownDiagnosis;
import dev.hypercluster.common.model.AIModels;

import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@GameTestHolder(HyperCluster.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndustrialIntegrationTests {

    private static final String BATCH = "hypercluster_064_industrial";

    @GameTest(template = "hc_network_field", batch = BATCH)
    public static void telemetryBooksOneLogOnNativeLcr(GameTestHelper helper) {
        var lcr = placeLcr(helper, 3, 1, 3);
        helper.runAtTickTime(10, () -> {
            form(helper, lcr.host());
            power(lcr.energy());
            lcr.input().getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            var logic = lcr.host().getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(sampleRecipe()), "native LCR should start the batch");
            logic.setProgress(logic.getDuration());
            logic.onRecipeFinish();
            var first = lcr.firstBus();
            var second = lcr.secondBus();
            helper.assertTrue(first.getTelemetryBufferCount() + second.getTelemetryBufferCount() == 1,
                    "a native LCR with two telemetry buses must earn exactly one log");
            var log = first.getTelemetryBufferCount() > 0 ? first : second;
            helper.assertTrue(
                    log.getTelemetryBuffer().getStackInSlot(0).is(HyperClusterItems.CHEMICAL_TELEMETRY_LOG.get()),
                    "LCR must earn a chemical log");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = BATCH)
    public static void jammedAndCancelledNativeBatchesDoNotBook(GameTestHelper helper) {
        var lcr = placeLcr(helper, 3, 1, 3);
        helper.runAtTickTime(10, () -> {
            form(helper, lcr.host());
            power(lcr.energy());
            lcr.input().getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var logic = lcr.host().getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(sampleRecipe()), "unblocked batch should start");
            logic.interruptRecipe();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(sampleRecipe()), "second batch should start");
            fillBus(lcr.firstBus());
            fillBus(lcr.secondBus());
            logic.setProgress(logic.getDuration());
            logic.onRecipeFinish();
            helper.assertTrue(lcr.firstBus().getTelemetryBufferCount() + lcr.secondBus().getTelemetryBufferCount() == 0,
                    "cancellation and a jam at real completion must both earn zero logs");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = BATCH, timeoutTicks = 150)
    public static void nativeInferenceChargesCwuAndFallsBack(GameTestHelper helper) {
        var lcr = placeLcr(helper, 3, 1, 7);
        var rack = DatacenterTests.placeRack(helper, 3, 1, 1);
        var receiver = new BlockPos(5, 2, 7);
        // Air rack transmitter at (5,2,2); receiver at the LCR's front.
        var pipeEnd = receiver.north();
        var pipeStart = new BlockPos(5, 2, 3);
        DatacenterTests.placeOpticalPipeRun(helper, pipeStart, pipeEnd);
        DatacenterTests.connectPipeToBlock(helper, pipeStart, Direction.NORTH);
        DatacenterTests.connectPipeToBlock(helper, pipeEnd, Direction.SOUTH);
        var transmitter = ((IMachineBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 2))).getMetaMachine();
        transmitter.setFrontFacing(Direction.SOUTH);

        helper.runAtTickTime(10, () -> {
            power(lcr.energy());
            power(rack.energyHatch());
            rack.controller().getBladeInventory().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            form(helper, rack.controller());
            form(helper, lcr.host());
        });
        helper.runAtTickTime(25, () -> {
            var host = lcr.host();
            var raw = sampleRecipe();
            var base = host.fullModifyRecipe(raw);
            helper.assertTrue(base != null, "native baseline recipe must be valid");
            lcr.inference().getChipSlot().setStackInSlot(0, new ItemStack(HyperClusterItems.CHEMICAL_LORA_CARD.get()));
            var boosted = host.fullModifyRecipe(raw);
            helper.assertTrue(boosted != null && IvInferenceHatchMachine.inferenceDemand(boosted) == 2,
                    "connected LoRA must add 2 CWU/t");
            helper.assertTrue(boosted.duration == base.duration &&
                    RecipeHelper.getRealEUt(boosted).getTotalEU() * 4 ==
                            RecipeHelper.getRealEUt(base).getTotalEU() * 3,
                    "LoRA discounts EU/t after native overclocking with the same duration");
            var outputs = boosted.getOutputContents(ItemRecipeCapability.CAP);
            var original = base.getOutputContents(ItemRecipeCapability.CAP);
            helper.assertTrue(outputs.size() == 2 &&
                    outputs.get(0).chance == original.get(0).chance &&
                    ItemRecipeCapability.CAP.of(outputs.get(0).content).getItems()[0].getCount() ==
                            ItemRecipeCapability.CAP.of(original.get(0).content).getItems()[0].getCount() &&
                    outputs.get(1).chance == original.get(1).chance * 2,
                    "LoRA doubles the chanced roll and preserves the guaranteed stack");
            lcr.input().getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            helper.assertTrue(host.getRecipeLogic().checkMatchedRecipeAvailable(raw), "boosted recipe must start");
            int available = rack.controller().requestCWUt(100, true);
            host.getRecipeLogic().handleRecipeWorking();
            helper.assertTrue(rack.controller().requestCWUt(100, true) == available - 2,
                    "recipe must really debit CWU");
            ((OpticalPipeBlockEntity) helper.getBlockEntity(pipeEnd)).setConnection(Direction.SOUTH, false, false);
            var fallback = host.fullModifyRecipe(raw);
            helper.assertTrue(fallback != null && IvInferenceHatchMachine.inferenceDemand(fallback) == 0 &&
                    RecipeHelper.getRealEUt(fallback).equals(RecipeHelper.getRealEUt(base)),
                    "disconnecting the cable must restore the native recipe");
            lcr.inference().getChipSlot().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.ALIGNED_CHEMICAL_EDGE_CHIP.get()));
            lcr.inference().setMaintenanceProblems((byte) 0);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(lcr.inference().getNumMaintenanceProblems() == 0,
                    "an aligned chip must repair all maintenance faults");
            helper.assertTrue(lcr.host().getRecipeLogic().getLastRecipe() == null ||
                    IvInferenceHatchMachine.inferenceDemand(lcr.host().getRecipeLogic().getLastRecipe()) == 0,
                    "the running boost must not survive the disconnection");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = BATCH, timeoutTicks = 120)
    public static void floorInferencePaysSharedRackAndFallsBack(GameTestHelper helper) {
        TopologyTests.fillFloor(helper, 1, 0, 1, 10, 12);
        var lcr = placeLcr(helper, 3, 1, 7);
        var rack = DatacenterTests.placeRack(helper, 3, 1, 1);
        var floorTile = new BlockPos(1, 0, 1);
        var raw = sampleRecipe();
        helper.runAtTickTime(10, () -> {
            power(lcr.energy());
            power(rack.energyHatch());
            rack.controller().getBladeInventory().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            form(helper, rack.controller());
            form(helper, lcr.host());
        });
        helper.runAtTickTime(25, () -> {
            var host = lcr.host();
            var base = host.fullModifyRecipe(raw);
            helper.assertTrue(base != null, "native baseline recipe must be valid");
            lcr.inference().getChipSlot().setStackInSlot(0, new ItemStack(HyperClusterItems.CHEMICAL_EDGE_CHIP.get()));
            var boosted = host.fullModifyRecipe(raw);
            helper.assertTrue(boosted != null && IvInferenceHatchMachine.inferenceDemand(boosted) == 2 &&
                    RecipeHelper.getRealEUt(boosted).getTotalEU() * 4 ==
                            RecipeHelper.getRealEUt(base).getTotalEU() * 3,
                    "shared raised floor alone should unlock the 2 CWU/t discount");

            lcr.input().getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var logic = host.getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(raw), "floor-boosted recipe should start");
            int available = rack.controller().requestCWUt(100, true);
            logic.handleRecipeWorking();
            helper.assertTrue(available >= 2 && rack.controller().requestCWUt(100, true) == available - 2,
                    "the floor must debit the same rack budget that an optical client uses");
            rack.controller().requestCWUt(100, false);
            var exhausted = host.fullModifyRecipe(raw);
            helper.assertTrue(exhausted != null && IvInferenceHatchMachine.inferenceDemand(exhausted) == 0 &&
                    RecipeHelper.getRealEUt(exhausted).equals(RecipeHelper.getRealEUt(base)),
                    "an exhausted shared rack must not grant a discount");
            helper.setBlock(floorTile, Blocks.AIR);
        });
        helper.runAtTickTime(30, () -> {
            var host = lcr.host();
            var fallback = host.fullModifyRecipe(raw);
            helper.assertTrue(fallback != null && IvInferenceHatchMachine.inferenceDemand(fallback) == 0,
                    "breaking the shared floor must remove the discount");
            helper.assertTrue(host.getRecipeLogic().checkMatchedRecipeAvailable(raw), "base recipe should restart");
            helper.setBlock(floorTile, HyperClusterBlocks.RAISED_FLOOR.get());
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(lcr.host().getRecipeLogic().isRecipeDirty(),
                    "restoring floor compute should refresh a cached undiscounted recipe at its next batch");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = BATCH, timeoutTicks = 180)
    public static void poweredGlmDiagnosesNativeLcr(GameTestHelper helper) {
        TopologyTests.fillFloor(helper, 1, 0, 1, 15, 13);
        var router = TopologyTests.placeMoeRouter(helper, 1, 1, 1);
        var memory = TopologyTests.placeVramRack(helper, 10, 1, 1);
        var compute = TopologyTests.placeLiquidRack(helper, 10, 1, 7);
        var lcr = placeLcr(helper, 6, 1, 6);
        helper.runAtTickTime(10, () -> {
            power(router.energyHatch());
            power(memory.energyHatch());
            power(compute.energyHatch());
            power(lcr.energy());
            form(helper, memory.controller());
            form(helper, compute.controller());
            form(helper, router.controller());
            form(helper, lcr.host());
            for (int i = 0; i < 4; i++) {
                memory.controller().getModuleSlot().setStackInSlot(i,
                        new ItemStack(HyperClusterItems.LUV_HBM_MODULE.get()));
            }
            compute.controller().getBladeInventory().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(1000));
            var module = OpenWeightModuleItem.create(new ItemStack(HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                    "chemical", "fp16", 1, 1);
            router.controller().getModelSlot().setStackInSlot(0, module);
            for (int i = 0; i < 3; i++) {
                router.controller().cycleModel();
            }
            lcr.inference().setMaintenanceProblems((byte) 0);
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(router.controller().isInferenceOnline() &&
                    router.controller().loadedModel() == AIModels.GLM_2333_FLASH, "powered GLM Flash should deploy");
            helper.assertTrue(lcr.inference().getNumMaintenanceProblems() == 0, "GLM must repair the host");
            helper.assertTrue(lcr.inference().diagnosis() == ShutdownDiagnosis.MISSING_INPUT_OR_NO_RECIPE,
                    "powered empty LCR should report missing inputs, got " + lcr.inference().diagnosis());
            var boosted = lcr.host().fullModifyRecipe(sampleRecipe());
            helper.assertTrue(boosted != null && IvInferenceHatchMachine.inferenceDemand(boosted) == 2,
                    "GLM with a shared compute room must unlock the discount without a chip or optical cable");
            lcr.energy().energyContainer.removeEnergy(lcr.energy().energyContainer.getEnergyStored());
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(lcr.inference().diagnosis() == ShutdownDiagnosis.INSUFFICIENT_ENERGY,
                    "unpowered LCR should report insufficient energy");
            power(lcr.energy());
            lcr.input().getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(2));
            lcr.input().getInventory().setStackInSlot(0, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Sulfur));
            lcr.fluidIn().tank.setFluidInTank(0, GTMaterials.Oxygen.getFluid(2000));
            lcr.fluidOut().tank.setFluidInTank(0, GTMaterials.Water.getFluid(lcr.fluidOut().tank.getTankCapacity(0)));
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(lcr.inference().diagnosis() == ShutdownDiagnosis.OUTPUT_FULL,
                    "powered LCR with present inputs and saturated outputs should report output full, got " +
                            lcr.inference().diagnosis());
            lcr.host().getRecipeLogic().setWorkingEnabled(false);
        });
        helper.runAtTickTime(55, () -> {
            helper.assertTrue(lcr.inference().diagnosis() == ShutdownDiagnosis.SUSPENDED,
                    "manually paused LCR should report suspension before the saturated output");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = BATCH, timeoutTicks = 150)
    public static void rackHeatRecoveryAndFreezerLoop(GameTestHelper helper) {
        var air = DatacenterTests.placeRack(helper, 1, 1, 1);
        var liquid = TopologyTests.placeLiquidRack(helper, 7, 1, 1);
        var legacy = TopologyTests.placeLiquidRack(helper, 7, 1, 7);
        var water = place(helper, new BlockPos(1, 1, 2), GTMachines.FLUID_IMPORT_HATCH[IV],
                FluidHatchPartMachine.class);
        var airOut = place(helper, new BlockPos(2, 1, 2), GTMachines.FLUID_EXPORT_HATCH[IV],
                FluidHatchPartMachine.class);
        var liquidOut = place(helper, new BlockPos(8, 3, 3), GTMachines.FLUID_EXPORT_HATCH[LuV],
                FluidHatchPartMachine.class);

        helper.runAtTickTime(10, () -> {
            water.tank.setFluidInTank(0, GTMaterials.Water.getFluid(1000));
            air.controller().getBladeInventory().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            power(air.energyHatch());
            form(helper, air.controller());
            for (var rack : java.util.List.of(liquid, legacy)) {
                rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(1000));
                rack.controller().getBladeInventory().setStackInSlot(0,
                        new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
                power(rack.energyHatch());
                form(helper, rack.controller());
            }
        });
        helper.runAtTickTime(30, () -> {
            var steam = airOut.tank.getFluidInTank(0);
            var coldPlateSteam = liquidOut.tank.getFluidInTank(0);
            helper.assertTrue(steam.getFluid() == GTMaterials.Steam.getFluid() && steam.getAmount() > 0 &&
                    water.tank.getFluidInTank(0).getAmount() < 1000, "air rack must turn water into steam");
            helper.assertTrue(
                    coldPlateSteam.getFluid() == GTMaterials.Steam.getFluid() && coldPlateSteam.getAmount() > 0,
                    "liquid rack must recover steam from distilled water");
            long airDraw = air.energyHatch().energyContainer.getEnergyCapacity() -
                    air.energyHatch().energyContainer.getEnergyStored();
            helper.assertTrue(4L * RackHeatRecovery.MAX_STEAM_EU_PER_MB * steam.getAmount() < airDraw,
                    "the total steam actually recovered must return below a quarter of the total rack draw");
            for (long it : new long[] { DatacenterRating.itEUt(1), LiquidDatacenterRating.itEUt(1) }) {
                helper.assertTrue(4L * RackHeatRecovery.MAX_STEAM_EU_PER_MB * RackHeatRecovery.steamLimit(it) < it,
                        "maximum turbine return must stay strictly below 25% of IT draw");
            }
            helper.assertTrue(legacy.controller().getMaxCWUt() > 0,
                    "liquid rack without an export hatch must still work");
            liquidOut.tank.setFluidInTank(0, net.minecraftforge.fluids.FluidStack.EMPTY);
            liquid.fluidHatch().tank.setFluidInTank(0, GTMaterials.PCBCoolant.getFluid(1000));
        });
        helper.runAtTickTime(40, () -> {
            var heated = liquidOut.tank.getFluidInTank(0);
            helper.assertTrue(heated.getFluid() == HyperClusterFluids.HOT_PCB_COOLANT.getFluid() &&
                    heated.getAmount() == 1000 - liquid.fluidHatch().tank.getFluidInTank(0).getAmount(),
                    "cold plates must return exactly one hot mB per PCB coolant mB consumed");
            helper.assertTrue(
                    BuiltInRegistries.FLUID.getKey(heated.getFluid()).equals(HyperCluster.id("hot_pcb_coolant")) &&
                            heated.getFluid().getBucket() != Items.AIR,
                    "hot coolant must have the addon fluid ID and a bucket");
            var raw = helper.getLevel().getRecipeManager()
                    .byKey(HyperCluster.id("vacuum_freezer/recycle_hot_pcb_coolant")).orElse(null);
            helper.assertTrue(raw instanceof GTRecipe recipe && recipe.getType() == GTRecipeTypes.VACUUM_RECIPES &&
                    RecipeHelper.getInputFluids(recipe).get(0).isFluidEqual(heated) &&
                    RecipeHelper.getInputFluids(recipe).get(0).getAmount() == 1000 &&
                    RecipeHelper.getOutputFluids(recipe).get(0)
                            .isFluidStackIdentical(GTMaterials.PCBCoolant.getFluid(1000)),
                    "native Vacuum Freezer must have the 1:1 coolant return recipe");
            helper.succeed();
        });
    }

    private static GTRecipe sampleRecipe() {
        return GTRecipeTypes.LARGE_CHEMICAL_RECIPES.recipeBuilder(HyperCluster.id("test_industrial_batch"))
                .inputItems(new ItemStack(Items.IRON_INGOT))
                .outputItems(new ItemStack(Items.GOLD_INGOT, 2))
                .chancedOutput(new ItemStack(Items.COPPER_INGOT), "1/4", 0)
                .EUt(120).duration(128).buildRawRecipe();
    }

    private static void power(EnergyHatchPartMachine hatch) {
        hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity());
    }

    private static void fillBus(ItemBusPartMachine bus) {
        for (int i = 0; i < bus.getInventory().getSlots(); i++) {
            bus.getInventory().setStackInSlot(i, new ItemStack(Items.OBSIDIAN, 64));
        }
    }

    private static void form(GameTestHelper helper, WorkableElectricMultiblockMachine machine) {
        TopologyTests.ensureFormed(machine);
        helper.assertTrue(machine.isFormed(), "expected " + machine.getDefinition().getName() + " to form: " +
                TopologyTests.multiblockError(machine));
    }

    private record Lcr(WorkableElectricMultiblockMachine host, ItemBusPartMachine input, TelemetryHatchMachine firstBus,
                       TelemetryHatchMachine secondBus, EnergyHatchPartMachine energy,
                       IvInferenceHatchMachine inference,
                       FluidHatchPartMachine fluidIn, FluidHatchPartMachine fluidOut) {}

    private static Lcr placeLcr(GameTestHelper helper, int x, int y, int z) {
        var origin = new BlockPos(x, y, z);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    helper.setBlock(origin.offset(dx, dy, dz), GTBlocks.CASING_PTFE_INERT.getDefaultState());
                }
            }
        }
        helper.setBlock(origin.offset(1, 1, 1), GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.getDefaultState());
        helper.setBlock(origin.offset(1, 1, 2), GTBlocks.COIL_CUPRONICKEL.getDefaultState());
        var host = place(helper, origin.offset(1, 1, 0), GTMultiMachines.LARGE_CHEMICAL_REACTOR,
                WorkableElectricMultiblockMachine.class);
        var energy = place(helper, origin.offset(1, 2, 2), GTMachines.ENERGY_INPUT_HATCH[HV],
                EnergyHatchPartMachine.class);
        var input = place(helper, origin, GTMachines.ITEM_IMPORT_BUS[HV], ItemBusPartMachine.class);
        var first = place(helper, origin.offset(2, 0, 0), HyperClusterMachines.HV_TELEMETRY_HATCH,
                TelemetryHatchMachine.class);
        var second = place(helper, origin.offset(2, 2, 0), HyperClusterMachines.HV_TELEMETRY_HATCH,
                TelemetryHatchMachine.class);
        var inference = place(helper, origin.offset(2, 1, 0), HyperClusterMachines.IV_INFERENCE_HATCH,
                IvInferenceHatchMachine.class);
        var fluidIn = place(helper, origin.offset(0, 1, 0), GTMachines.FLUID_IMPORT_HATCH[HV],
                FluidHatchPartMachine.class);
        var fluidOut = place(helper, origin.offset(0, 2, 0), GTMachines.FLUID_EXPORT_HATCH[HV],
                FluidHatchPartMachine.class);
        return new Lcr(host, input, first, second, energy, inference, fluidIn, fluidOut);
    }

    private static <T> T place(GameTestHelper helper, BlockPos pos, MachineDefinition definition, Class<T> type) {
        helper.setBlock(pos, definition.getBlock().defaultBlockState());
        return type.cast(((IMachineBlockEntity) helper.getBlockEntity(pos)).getMetaMachine());
    }
}
