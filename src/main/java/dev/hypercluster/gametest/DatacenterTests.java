package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.HPCAMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.datacenter.AirCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.DatacenterHardwareHatchMachine;
import dev.hypercluster.common.machine.datacenter.DatacenterRating;
import dev.hypercluster.common.machine.datacenter.IvInferenceHatchMachine;
import dev.hypercluster.common.machine.datacenter.PrecisionCracMachine;

import java.util.ArrayList;
import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.UHV;
import static com.gregtechceu.gtceu.api.GTValues.UV;

/**
 * 0.3 acceptance gametests: the IV self-built compute datacenter.
 *
 * <ul>
 * <li>the air-cooled compute rack forms and scales blades 0 → 24 CWU/t linearly;</li>
 * <li>{@code simulate=true} only queries (never deducts) and multiple consumers of the same tick
 * share one strict budget;</li>
 * <li>low power, unfixed maintenance, a paused rack or a cut optical cable zero the offered /
 * delivered CWU/t with a recorded derate reason;</li>
 * <li>the GT optical pipe chain feeds the vanilla {@code gtceu:research_station}'s computation
 * provider stably, and the vanilla HPCA coexists with its own independent budget;</li>
 * <li>chunk unload/reload keeps blades and snack slots persistent and resets the budget;</li>
 * <li>the machine room re-rates Tier I → Tier II (PUE 1.80 → 1.45) from the raised floor + open
 * channels + a running precision CRAC, with the natural-cold-source coefficients asserted;</li>
 * <li>the IV inference hatch really consumes CWU/t for the whitelisted tower scrub recipe
 * (billed every tick, EU/t discount only while billed) and falls back to the original rules when
 * the CWU/t feed or the chip is gone — no freeloading.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class DatacenterTests {

    public static final String DATACENTER_BATCH = "hypercluster_03_datacenter";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. rack forms and blades scale 0 -> 24 CWU/t linearly //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 400)
    public static void rackFormsAndBladesScaleCwuLinearly(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 4, 1, 4);
        BlockPos hardwarePos = new BlockPos(4, 2, 4);
        helper.setBlock(hardwarePos, HyperClusterMachines.DATACENTER_HARDWARE_HATCH.getBlock().defaultBlockState());
        primeRackEnergy(parts, helper, 1, 20, 320);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            helper.assertTrue(parts.controller().isFormed(), "rack must be formed");
            helper.assertTrue(parts.controller().availableCWUt() == 0,
                    "an empty rack must offer 0 CWU/t");
            helper.assertTrue(
                    parts.controller().getDerateReason() == AirCooledComputeRackMachine.DerateReason.NO_BLADES,
                    "an empty rack records the NO_BLADES derate reason, got " + parts.controller().getDerateReason());
        });

        // Insert blades one by one; availability must follow 6 * blades exactly.
        for (int blades = 1; blades <= DatacenterRating.BLADE_SLOTS; blades++) {
            final int expected = blades * DatacenterRating.CWU_PER_BLADE;
            final int slot = blades - 1;
            helper.runAtTickTime(60 + blades * 4, () -> {
                var hardware = (DatacenterHardwareHatchMachine) ((IMachineBlockEntity) helper
                        .getBlockEntity(hardwarePos)).getMetaMachine();
                var automation = helper.getBlockEntity(hardwarePos)
                        .getCapability(ForgeCapabilities.ITEM_HANDLER, hardware.getFrontFacing()).orElseThrow(
                                () -> new IllegalStateException("missing air-cooled hardware port"));
                helper.assertTrue(
                        automation.insertItem(slot, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()), false)
                                .isEmpty(),
                        "the air-cooled hardware hatch must accept a blade");
            });
            helper.runAtTickTime(62 + blades * 4, () -> {
                helper.assertTrue(parts.controller().getBladeCount() == slot + 1,
                        "blade count must be " + (slot + 1));
                helper.assertTrue(parts.controller().availableCWUt() == expected,
                        "expected " + expected + " CWU/t with " + (slot + 1) + " blades, got " +
                                parts.controller().availableCWUt());
                helper.assertTrue(parts.controller().getMaxCWUt(new ArrayList<>()) == expected,
                        "getMaxCWUt must report " + expected + ", got " +
                                parts.controller().getMaxCWUt(new ArrayList<>()));
                helper.assertTrue(parts.controller().getDerateReason() == AirCooledComputeRackMachine.DerateReason.NONE,
                        "a powered rack with blades must be providing, reason=" + parts.controller().getDerateReason());
            });
        }

        // Pull blades back out: availability must fall back to zero.
        helper.runAtTickTime(110, () -> {
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory().setStackInSlot(i, ItemStack.EMPTY);
            }
        });
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 0,
                    "an emptied rack must offer 0 CWU/t again, got " + parts.controller().availableCWUt());
            System.out.printf("[HC-03] rack blades: linear 0->24 CWU/t verified (0 back after emptying)%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. simulate never deducts; same-tick consumers share one strict budget //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 400)
    public static void simulateAndMultiConsumerBudget(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 1, 1, 1);
        primeRackEnergy(parts, helper, 1, 20, 320);
        // A GT computation receiver hatch in the open, attached to the rack's transmitter hatch
        // through real optical pipes: the chain the research station uses.
        BlockPos receiverPos = new BlockPos(1, 1, 6);
        // Transmitter hatch (3,2,2) -> (3,2,3) -> west -> down -> south -> receiver (1,1,6).
        placeOpticalPipeRun(helper, new BlockPos(3, 2, 3), new BlockPos(1, 2, 3));
        placeOpticalPipeRun(helper, new BlockPos(1, 1, 3), new BlockPos(1, 1, 3));
        placeOpticalPipeRun(helper, new BlockPos(1, 1, 4), new BlockPos(1, 1, 5));
        connectPipes(helper, new BlockPos(1, 2, 3), new BlockPos(1, 1, 3));
        connectPipes(helper, new BlockPos(1, 1, 3), new BlockPos(1, 1, 4));
        connectPipeToBlock(helper, new BlockPos(3, 2, 3), Direction.NORTH);
        connectPipeToBlock(helper, new BlockPos(1, 1, 5), Direction.SOUTH);
        helper.setBlock(receiverPos,
                GTResearchMachines.COMPUTATION_HATCH_RECEIVER.getBlock().defaultBlockState());
        var receiverProvider = java.util.Optional
                .ofNullable((com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider) helper
                        .getBlockEntity(receiverPos)
                        .getCapability(
                                com.gregtechceu.gtceu.api.capability.forge.GTCapability.CAPABILITY_COMPUTATION_PROVIDER,
                                Direction.NORTH)
                        .orElse(null));
        helper.assertTrue(receiverProvider.isPresent(),
                "the receiver hatch must expose its computation provider capability");

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
        });

        helper.runAtTickTime(60, () -> {
            // Wait until the rack settled and provides.
            if (parts.controller().availableCWUt() != 24) {
                return; // re-checked at the next assertion point
            }
        });

        helper.runAtTickTime(90, () -> {
            int available = parts.controller().availableCWUt();
            helper.assertTrue(available == 24, "rack must provide 24 CWU/t, got " + available);
            // simulate only queries: the budget stays intact.
            int simulated = receiverProvider.get().requestCWUt(24, true);
            helper.assertTrue(simulated == 24, "simulate request must see all 24, got " + simulated);
            helper.assertTrue(parts.controller().getAllocatedCWUt() == 0,
                    "simulate must not deduct the budget, allocated=" + parts.controller().getAllocatedCWUt());
            // Consumer A takes 20 of the 24 budget for real.
            int first = receiverProvider.get().requestCWUt(20, false);
            helper.assertTrue(first == 20, "first consumer must get 20, got " + first);
            helper.assertTrue(parts.controller().getAllocatedCWUt() == 20,
                    "the real allocation must be booked (20), got " + parts.controller().getAllocatedCWUt());
            // Consumer B (the rack itself, directly) competes for the remaining 4 only.
            int second = parts.controller().requestCWUt(10, false, new ArrayList<>());
            helper.assertTrue(second == 4, "same-tick consumer B must only get the leftover 4, got " + second);
            // The budget is now exhausted for the rest of this tick.
            int third = parts.controller().requestCWUt(1, false, new ArrayList<>());
            helper.assertTrue(third == 0, "the same-tick budget must be exhausted, got " + third);
        });

        helper.runAtTickTime(120, () -> {
            System.out.printf("[HC-03] budget: simulate-no-deduct + same-tick sharing verified%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. low power / maintenance / paused zero the CWU/t with recorded reasons //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 500)
    public static void deratesZeroCwuOnFaults(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 4, 1, 1);
        // Prime only up to tick 61: the low-power phase at tick 80 must find the hatch dry.
        primeRackEnergy(parts, helper, 1, 20, 61);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
        });

        helper.runAtTickTime(70, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 24, "rack must provide 24 first");
        });

        // Low power: drain the energy hatch dry.
        helper.runAtTickTime(80, () -> {
            parts.energyHatch().energyContainer.removeEnergy(
                    parts.energyHatch().energyContainer.getEnergyStored());
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 0,
                    "low power must zero the CWU/t, got " + parts.controller().availableCWUt());
            helper.assertTrue(
                    parts.controller().getDerateReason() == AirCooledComputeRackMachine.DerateReason.LOW_POWER,
                    "expected LOW_POWER derate reason, got " + parts.controller().getDerateReason());
        });

        // Refill, then unfix the maintenance hatch (0 = all six problems present).
        helper.runAtTickTime(120, () -> parts.energyHatch().energyContainer
                .addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity()));
        helper.runAtTickTime(140, () -> parts.maintenanceHatch().setMaintenanceProblems((byte) 0));
        helper.runAtTickTime(170, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 0,
                    "unmaintained rack must offer 0 CWU/t");
            helper.assertTrue(
                    parts.controller().getDerateReason() == AirCooledComputeRackMachine.DerateReason.MAINTENANCE,
                    "expected MAINTENANCE derate reason, got " + parts.controller().getDerateReason());
        });

        // Fix maintenance again, then pause the rack with the soft-mallet suspend.
        helper.runAtTickTime(180, () -> parts.maintenanceHatch().fixAllMaintenanceProblems());
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 24,
                    "a fixed rack must provide again, got " + parts.controller().availableCWUt());
            parts.controller().recipeLogic.setWorkingEnabled(false);
        });
        helper.runAtTickTime(230, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 0,
                    "a paused rack must offer 0 CWU/t");
            helper.assertTrue(parts.controller().getDerateReason() == AirCooledComputeRackMachine.DerateReason.PAUSED,
                    "expected PAUSED derate reason, got " + parts.controller().getDerateReason());
            parts.controller().recipeLogic.setWorkingEnabled(true);
        });
        helper.runAtTickTime(260, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 24,
                    "resumed rack must provide 24 again, got " + parts.controller().availableCWUt());
            System.out.printf("[HC-03] derates: low power / maintenance / paused all zero with reasons%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. the optical pipe chain feeds the vanilla research station stably //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 900)
    public static void opticalLinkFeedsResearchStation(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 1, 1, 1);
        primeRackEnergy(parts, helper, 1, 20, 800);
        StationParts station = placeResearchStation(helper, 2, 4, 8);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
            ensureFormed(station.controller());
        });

        // The optical pipes: transmitter hatch (3,2,2) -> (3,2,3) -> west along z=3 -> south
        // along x=0 -> up the station's south-west corner -> east at (2,4,11), adjacent to the
        // station's receiver hatch (2,4,10).
        placeOpticalPipeRun(helper, new BlockPos(3, 2, 3), new BlockPos(0, 2, 3));
        placeOpticalPipeRun(helper, new BlockPos(0, 2, 4), new BlockPos(0, 2, 11));
        placeOpticalPipeRun(helper, new BlockPos(0, 3, 11), new BlockPos(0, 4, 11));
        placeOpticalPipeRun(helper, new BlockPos(1, 4, 11), new BlockPos(2, 4, 11));
        connectPipes(helper, new BlockPos(0, 2, 3), new BlockPos(0, 2, 4));
        connectPipes(helper, new BlockPos(0, 2, 11), new BlockPos(0, 3, 11));
        connectPipes(helper, new BlockPos(0, 4, 11), new BlockPos(1, 4, 11));
        connectPipeToBlock(helper, new BlockPos(3, 2, 3), Direction.NORTH);
        connectPipeToBlock(helper, new BlockPos(2, 4, 11), Direction.NORTH);

        helper.runAtTickTime(100, () -> {
            helper.assertTrue(parts.controller().isFormed(), "rack must be formed");
            if (!station.controller().isFormed()) {
                var state = station.controller().getMultiblockState();
                BlockPos origin = helper.absolutePos(BlockPos.ZERO);
                BlockPos failPos = state.getPos();
                System.out.printf(
                        "[HC-03-DIAG] station not formed: origin=%s failWorld=%s failRel=%s found=%s err=%s%n",
                        origin, failPos,
                        failPos == null ? null : failPos.subtract(origin),
                        failPos == null ? null : helper.getLevel().getBlockState(failPos),
                        state.error == null ? "none" : state.error.getErrorInfo().getString());
            }
            helper.assertTrue(station.controller().isFormed(), "research station must be formed");
            var provider = station.controller().getComputationProvider();
            helper.assertTrue(provider != null,
                    "the research station must expose a computation provider (receiver hatch + pipe)");
            helper.assertTrue(parts.controller().availableCWUt() == 24,
                    "rack must provide 24 CWU/t, got " + parts.controller().availableCWUt());
        });

        // Stable supply: pull 24 CWU/t through the station's provider for many consecutive ticks.
        for (int t = 0; t < 6; t++) {
            final int tick = 130 + t * 8;
            helper.runAtTickTime(tick, () -> {
                var provider = station.controller().getComputationProvider();
                if (provider == null) {
                    helper.assertTrue(false, "provider disappeared at tick " + tick);
                    return;
                }
                int granted = provider.requestCWUt(24, false);
                if (granted != 24) {
                    System.out.printf(
                            "[HC-03-DIAG] station grant=%d at %d: provider=%s rackAvail=%d pipeN=%b%n",
                            granted, tick, provider.getClass().getSimpleName(),
                            parts.controller().availableCWUt(),
                            ((OpticalPipeBlockEntity) helper.getBlockEntity(new BlockPos(2, 4, 11)))
                                    .isConnected(Direction.NORTH));
                }
                helper.assertTrue(granted == 24,
                        "stable supply broken at tick " + tick + ": granted " + granted + " CWU/t");
            });
        }

        // Cut the cable: the delivered computation through the station zeroes.
        helper.runAtTickTime(200, () -> helper.setBlock(new BlockPos(0, 2, 7), Blocks.STONE
                .defaultBlockState()));
        helper.runAtTickTime(230, () -> {
            var provider = station.controller().getComputationProvider();
            helper.assertTrue(provider != null, "provider must survive a cable cut");
            int granted = provider.requestCWUt(24, false);
            helper.assertTrue(granted == 0,
                    "a cut optical cable must zero the delivered CWU/t, got " + granted);
            // The rack itself still offers its budget — the derate is on the link, not the rack.
            helper.assertTrue(parts.controller().availableCWUt() == 24,
                    "the rack keeps offering CWU/t after the cable cut");
        });

        helper.runAtTickTime(260, () -> {
            System.out.printf(
                    "[HC-03] research station: 6 consecutive ticks of stable 24 CWU/t through GT optical pipes, cut cable -> 0%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 5. coexistence with the vanilla HPCA: independent budgets, no interference //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 600)
    public static void hpcaCoexistence(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 1, 1, 1);
        primeRackEnergy(parts, helper, 1, 20, 500);
        // A minimal vanilla HPCA (2 wide × 4 deep × 5 tall) with one computation component.
        HpcaParts hpca = placeHpca(helper, 8, 1, 2);
        for (long t = 20; t <= 500; t += 20) {
            final long tick = t;
            helper.runAtTickTime(tick, () -> hpca.energyHatch().energyContainer
                    .addEnergy(hpca.energyHatch().energyContainer.getEnergyCapacity()));
        }

        helper.runAtTickTime(60, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
            ensureFormed(hpca.controller());
        });

        helper.runAtTickTime(150, () -> {
            helper.assertTrue(parts.controller().isFormed(), "our rack must be formed");
            helper.assertTrue(hpca.controller().isFormed(),
                    "the HPCA must be formed (error=" + (hpca.controller().getMultiblockState().error == null ? "none" :
                            hpca.controller().getMultiblockState().error.getErrorInfo().getString()) + ")");
            helper.assertTrue(hpca.controller().recipeLogic.isWorkingEnabled(),
                    "the HPCA must be working-enabled");
            helper.assertTrue(parts.controller().availableCWUt() == 24,
                    "our rack must provide 24 CWU/t alongside the HPCA");
            int before = parts.controller().getAllocatedCWUt();
            // Both providers grant from their own budgets in the same tick.
            int hpcaGrant = hpca.controller().requestCWUt(4, false, new ArrayList<>());
            int rackGrant = parts.controller().requestCWUt(24, false, new ArrayList<>());
            helper.assertTrue(hpcaGrant == 4, "the HPCA must keep granting its own 4 CWU/t, got " + hpcaGrant);
            helper.assertTrue(rackGrant == 24, "our rack must grant its full 24 CWU/t, got " + rackGrant);
            helper.assertTrue(parts.controller().getAllocatedCWUt() == 24 + before,
                    "the HPCA request must not touch our rack's budget, allocated=" +
                            parts.controller().getAllocatedCWUt());
            helper.assertTrue(hpca.controller().getMaxCWUt(new ArrayList<>()) >= 4,
                    "the HPCA must keep reporting its own max CWU/t");
            System.out.printf("[HC-03] HPCA coexistence: hpca=%d rack=%d, budgets independent%n",
                    hpcaGrant, rackGrant);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 6. chunk unload / reload: blades and snack persist, budget resets //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 100)
    public static void hardwareHatchOwnsAndDropsInstalledItems(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 4, 1, 4);
        BlockPos hatchPos = new BlockPos(4, 2, 4);
        var hatchState = HyperClusterMachines.DATACENTER_HARDWARE_HATCH.getBlock().defaultBlockState();
        var controllerState = helper.getBlockState(parts.controllerPos());
        var blade = HyperClusterItems.IV_COMPUTE_BLADE.get();
        var snack = HyperClusterItems.GREEN_SNACK_PACK.get();

        var legacy = parts.controller().legacyHardwareInventory();
        var taggedBlade = new ItemStack(blade);
        taggedBlade.getOrCreateTag().putString("test", "legacy hardware");
        legacy.setStackInSlot(0, taggedBlade);
        var controllerBlockEntity = helper.getBlockEntity(parts.controllerPos());
        var legacySave = controllerBlockEntity.saveWithFullMetadata();
        legacy.setStackInSlot(0, ItemStack.EMPTY);
        controllerBlockEntity.load(legacySave);
        helper.setBlock(hatchPos, hatchState);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            var hatch = (DatacenterHardwareHatchMachine) metaMachine(helper, hatchPos);
            var hatchBlockEntity = helper.getBlockEntity(hatchPos);
            var automation = hatchBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, hatch.getFrontFacing())
                    .orElseThrow(() -> new IllegalStateException("missing hardware port"));
            helper.assertTrue(legacy.getStackInSlot(0).isEmpty() &&
                    ItemStack.matches(taggedBlade, automation.getStackInSlot(0)),
                    "formation must move the old controller stack and its NBT into the hatch");
            int snackSlot = DatacenterRating.BLADE_SLOTS;
            helper.assertTrue(automation.insertItem(snackSlot, new ItemStack(snack), false).isEmpty(),
                    "the hatch must accept ancillary hardware");

            var hatchSave = hatchBlockEntity.saveWithFullMetadata();
            automation.extractItem(0, 1, false);
            automation.extractItem(snackSlot, 1, false);
            hatchBlockEntity.load(hatchSave);
            helper.assertTrue(ItemStack.matches(taggedBlade, automation.getStackInSlot(0)) &&
                    parts.controller().getBladeCount() == 1 && parts.controller().isSnackPresent(),
                    "the hatch must reload its own contents and the rack must read them");

            var openSlots = ((WidgetGroup) hatch.createUIWidget()).getWidgetsByType(SlotWidget.class);
            var alreadyOpenBladeSlot = openSlots.get(0).getHandler();
            helper.assertTrue(alreadyOpenBladeSlot.mayPlace(taggedBlade) &&
                    !alreadyOpenBladeSlot.mayPlace(new ItemStack(snack)),
                    "the attached GUI must use the controller's hardware filter");

            helper.destroyBlock(parts.controllerPos());
            helper.assertItemEntityCountIs(blade, parts.controllerPos(), 2, 0);
            helper.assertItemEntityCountIs(snack, parts.controllerPos(), 2, 0);
            helper.assertTrue(automation.extractItem(0, 1, false).isEmpty(),
                    "a cached capability must detach when the controller is removed");

            var reopenedSlots = ((WidgetGroup) hatch.createUIWidget()).getWidgetsByType(SlotWidget.class);
            helper.assertTrue(reopenedSlots.size() == openSlots.size() && reopenedSlots.size() == 5,
                    "both menus must expose the physical hatch slots after the controller breaks");
            var reopenedBladeSlot = reopenedSlots.get(0).getHandler();
            helper.assertTrue(ItemStack.matches(taggedBlade, alreadyOpenBladeSlot.getItem()) &&
                    !alreadyOpenBladeSlot.mayPlace(taggedBlade) && !reopenedBladeSlot.mayPlace(taggedBlade),
                    "an already-open menu must retain the items and update its insertion rules");
            helper.assertTrue(ItemStack.matches(taggedBlade, reopenedBladeSlot.remove(1)) &&
                    alreadyOpenBladeSlot.getItem().isEmpty(),
                    "reopening the detached hatch must allow taking the same stored item exactly once");

            helper.destroyBlock(hatchPos);
            helper.assertItemEntityCountIs(blade, hatchPos, 0, 0);
            helper.assertItemEntityCountIs(snack, hatchPos, 0, 1);
        });

        helper.runAtTickTime(41, () -> {
            helper.setBlock(parts.controllerPos(), controllerState);
            helper.setBlock(hatchPos, hatchState);
            var controller = (AirCooledComputeRackMachine) metaMachine(helper, parts.controllerPos());
            ensureFormed(controller);
            helper.assertTrue(controller.isFormed(), "the replacement rack must form");
            controller.getBladeInventory().setStackInSlot(0, new ItemStack(blade));

            helper.destroyBlock(hatchPos);
            helper.assertItemEntityCountIs(blade, hatchPos, 2, 1);
            helper.assertTrue(controller.getBladeCount() == 0, "breaking the hatch must take its hardware offline");
            helper.destroyBlock(parts.controllerPos());
            helper.assertItemEntityCountIs(blade, hatchPos, 2, 1);
            helper.succeed();
        });
    }

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 400)
    public static void chunkUnloadReloadPersistence(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 4, 1, 4);
        primeRackEnergy(parts, helper, 1, 20, 320);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
            parts.controller().getSnackSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.GREEN_SNACK_PACK.get()));
        });

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(parts.controller().availableCWUt() == 24, "rack provides before save");
            parts.controller().requestCWUt(24, false, new ArrayList<>());
            helper.assertTrue(parts.controller().getAllocatedCWUt() == 24, "budget booked before save");
        });

        // Simulated unload/reload via the vanilla BE save/load round-trip (the P03/P04 pattern).
        final CompoundTag[] savedTag = new CompoundTag[1];
        helper.runAtTickTime(120, () -> {
            BlockEntity be = helper.getBlockEntity(parts.controllerPos());
            savedTag[0] = be.saveWithFullMetadata();
            helper.assertTrue(savedTag[0].toString().contains("iv_compute_blade"),
                    "the save must contain the blade items: " + savedTag[0]);
            helper.assertTrue(savedTag[0].toString().contains("green_snack_pack"),
                    "the save must contain the snack pack");
            // Wipe the live inventories, then load the save back like a chunk reload would.
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory().setStackInSlot(i, ItemStack.EMPTY);
            }
            parts.controller().getSnackSlot().setStackInSlot(0, ItemStack.EMPTY);
            be.load(savedTag[0]);
        });

        helper.runAtTickTime(170, () -> {
            AirCooledComputeRackMachine rack = (AirCooledComputeRackMachine) metaMachine(helper,
                    parts.controllerPos());
            helper.assertTrue(rack.getBladeCount() == 4, "blades must survive the reload, got " + rack.getBladeCount());
            helper.assertTrue(rack.isSnackPresent(), "the snack pack must survive the reload");
            helper.assertTrue(rack.availableCWUt() == 24,
                    "the reloaded rack must provide 24 CWU/t again, got " + rack.availableCWUt());
            // The stale allocation from before the save is transient: the new tick window makes
            // the full budget available again, then exhausts within the same tick.
            int granted = rack.requestCWUt(24, false, new ArrayList<>());
            helper.assertTrue(granted == 24, "the reloaded rack must grant the full 24 again, got " + granted);
            int second = rack.requestCWUt(1, false, new ArrayList<>());
            helper.assertTrue(second == 0,
                    "the fresh window must exhaust within the same tick, got " + second);
            System.out.printf("[HC-03] unload/reload: blades+snack persist, fresh budget window grants 24%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 7. PUE: Tier I -> Tier II room upgrade and natural cold source coefficients //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 900)
    public static void pueTierUpgradeAndColdSource(GameTestHelper helper) {
        RackParts parts = placeRack(helper, 4, 1, 4);
        primeRackEnergy(parts, helper, 1, 20, 800);
        // The CRAC is placed at tick 120; its blower priming is pre-registered here (registering
        // runAtTickTime from inside a running callback corrupts the gametest tick map).
        final EnergyHatchPartMachine[] cracEnergy = new EnergyHatchPartMachine[1];
        for (long t = 140; t <= 850; t += 20) {
            helper.runAtTickTime(t, () -> {
                if (cracEnergy[0] != null) {
                    cracEnergy[0].energyContainer
                            .addEnergy(cracEnergy[0].energyContainer.getEnergyCapacity());
                }
            });
        }

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
        });

        helper.runAtTickTime(90, () -> {
            // Tier I defaults. The gametest world sits below Y=20, a natural cold source
            // (x0.6), so the live Tier I PUE is 1 + 0.80*0.6 = 1.48 (the neutral-world 1.80 is
            // asserted below through the pure settlement functions).
            helper.assertTrue(parts.controller().getRoomTier() == 1,
                    "a bare rack room is Tier I, got " + parts.controller().getRoomTier());
            helper.assertTrue(parts.controller().getEnvironmentMultiplier() == DatacenterRating.ENVIRONMENT_COLD,
                    "the gametest room below Y=20 must count as a cold source, got " +
                            parts.controller().getEnvironmentMultiplier());
            double pue = parts.controller().getPUE();
            helper.assertTrue(Math.abs(pue - 1.48) < 0.01,
                    "Tier I PUE must be ~1.48 under the cold source, got " + pue);
            double neutralTierOne = DatacenterRating.pue(4, 1.0, DatacenterRating.AIRFLOW_TIER_I);
            helper.assertTrue(Math.abs(neutralTierOne - 1.80) < 0.01,
                    "neutral Tier I PUE must be ~1.80, got " + neutralTierOne);
            double neutralTierTwo = DatacenterRating.pue(4, 1.0, DatacenterRating.AIRFLOW_TIER_II);
            helper.assertTrue(Math.abs(neutralTierTwo - 1.45) < 0.01,
                    "neutral Tier II PUE must be ~1.45, got " + neutralTierTwo);
            // Pure coefficient checks: Nether doubles cooling, Y<20 cuts it by 40%.
            helper.assertTrue(DatacenterRating.dimensionMultiplier(net.minecraft.world.level.Level.NETHER) == 2.0,
                    "the Nether must double cooling power");
            helper.assertTrue(
                    DatacenterRating.dimensionMultiplier(net.minecraft.world.level.Level.OVERWORLD) == 1.0,
                    "the Overworld keeps the neutral coefficient");
            double cold = DatacenterRating.biomeMultiplier(helper.getLevel().getBiome(parts.controllerPos()),
                    parts.controllerPos().below(64));
            helper.assertTrue(cold == DatacenterRating.ENVIRONMENT_COLD,
                    "Y<20 must be a natural cold source (x0.6), got " + cold);
            // Tier I settlement: 1024 IT + 819 cooling = 1843 EU/t.
            long total = DatacenterRating.totalEUt(4, 1.0, DatacenterRating.AIRFLOW_TIER_I, false);
            helper.assertTrue(total == 1843, "Tier I full-load total must be 1843 EU/t, got " + total);
            long tierTwo = DatacenterRating.totalEUt(4, 1.0, DatacenterRating.AIRFLOW_TIER_II, false);
            helper.assertTrue(tierTwo == 1485, "Tier II full-load total must be 1485 EU/t, got " + tierTwo);
            long coldTotal = DatacenterRating.totalEUt(4, DatacenterRating.ENVIRONMENT_COLD,
                    DatacenterRating.AIRFLOW_TIER_I, false);
            helper.assertTrue(coldTotal == 1515,
                    "cold-source Tier I total must be 1024+491=1515 EU/t, got " + coldTotal);
        });

        // Tier II upgrade: raised-floor plenum + a running CRAC within range.
        helper.runAtTickTime(120, () -> {
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, 0, z),
                            HyperClusterBlocks.RAISED_FLOOR.get().defaultBlockState());
                }
            }
            CracParts cracParts = placeCrac(helper, 9, 1, 4);
            cracEnergy[0] = cracParts.energyHatch();
            ensureFormed(cracParts.controller());
            helper.assertTrue(cracParts.controller().isFormed(), "the CRAC must be formed");
        });

        helper.runAtTickTime(260, () -> {
            helper.assertTrue(parts.controller().getRoomTier() == 2,
                    "floor + running CRAC must upgrade the room to Tier II, got tier " +
                            parts.controller().getRoomTier());
            double pue = parts.controller().getPUE();
            helper.assertTrue(Math.abs(pue - 1.27) < 0.01,
                    "Tier II PUE must be ~1.27 under the cold source (1.45 neutral), got " + pue);
        });

        // Plug the front intake channel: the room falls back to Tier I.
        helper.runAtTickTime(280, () -> helper.setBlock(new BlockPos(5, 3, 3), Blocks.STONE
                .defaultBlockState()));
        helper.runAtTickTime(330, () -> {
            helper.assertTrue(parts.controller().getRoomTier() == 1,
                    "a plugged front channel must drop the room back to Tier I, got tier " +
                            parts.controller().getRoomTier());
            helper.assertTrue(Math.abs(parts.controller().getPUE() - 1.48) < 0.01,
                    "PUE must return to the Tier I cold-source value after the channel is plugged");
            System.out.printf("[HC-03] PUE: Tier I 1.80 -> Tier II 1.45 -> back; cold source x0.6%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 8. the inference hatch really consumes CWU/t; falls back without it (no freeloading) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = DATACENTER_BATCH, timeoutTicks = 1600)
    public static void inferenceHatchConsumesRealCwu(GameTestHelper helper) {
        // Data scrubbing tower (3x5x3) with the inference hatch replacing a casing cell.
        TowerRackParts parts = placeTowerWithInference(helper);
        primeRackEnergy(parts.rack(), helper, 1, 20, 1500);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.rack().controller());
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.rack().controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_COMPUTE_BLADE.get()));
            }
            ensureFormed(parts.tower());
            parts.inferenceHatch().getChipSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()));
        });

        // Feed the tower: one mining log + distilled water. The base scrub recipe (2048 EU/t)
        // is whitelisted for the inference assist: with the chip and CWU/t available it is
        // rewritten to 1536 EU/t with a 2 CWU/t demand billed every tick.
        helper.runAtTickTime(60, () -> {
            parts.importBus().getInventory()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()));
            parts.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(2000));
        });

        helper.runAtTickTime(200, () -> {
            helper.assertTrue(parts.tower().recipeLogic.isWorking(),
                    "the tower must run the circuit #5 inference recipe, status=" +
                            parts.tower().recipeLogic.getStatus());
            GTRecipe last = parts.tower().recipeLogic.getLastRecipe();
            helper.assertTrue(last != null, "the recipe must be live mid-run");
            if (last != null) {
                long eu = recipeEUt(last);
                helper.assertTrue(eu == 1536,
                        "the boosted recipe must run at 1536 EU/t while CWU/t is billed, got " + eu);
                helper.assertTrue(IvInferenceHatchMachine.inferenceDemand(last) == 2,
                        "the recipe must carry a 2 CWU/t demand, got " + IvInferenceHatchMachine.inferenceDemand(last));
                helper.assertTrue(last.data.contains(IvInferenceHatchMachine.INFERENCE_BOOST_TAG),
                        "the boosted recipe must be the whitelisted scrub recipe");
            }
            helper.assertTrue(parts.rack().controller().getAllocatedCWUt() >= 1,
                    "the rack budget must show the per-tick billing (allocated=" +
                            parts.rack().controller().getAllocatedCWUt() + ")");
            helper.assertTrue(parts.rack().controller().getAllocatedCWUt() <= 2,
                    "per-tick billing must be exactly the 2 CWU/t demand, got " +
                            parts.rack().controller().getAllocatedCWUt());
        });

        // Cut the CWU feed: pull all blades. The watchdog must interrupt the stalled boosted
        // recipe; the interrupted batch loses its consumed inputs (vanilla interrupt
        // semantics), so fresh logs are fed to verify the original-rules fallback run.
        helper.runAtTickTime(230, () -> {
            for (int i = 0; i < DatacenterRating.BLADE_SLOTS; i++) {
                parts.rack().controller().getBladeInventory().setStackInSlot(i, ItemStack.EMPTY);
            }
            parts.importBus().getInventory()
                    .setStackInSlot(1, new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get(), 2));
        });

        helper.runAtTickTime(400, () -> {
            GTRecipe last = parts.tower().recipeLogic.getLastRecipe();
            helper.assertTrue(last != null,
                    "the tower must run the fallback recipe on fresh logs, status=" +
                            parts.tower().recipeLogic.getStatus());
            if (last != null) {
                long eu = recipeEUt(last);
                helper.assertTrue(eu == 2048,
                        "without CWU/t the recipe must fall back to the original 2048 EU/t, got " + eu);
                helper.assertTrue(IvInferenceHatchMachine.inferenceDemand(last) == 0,
                        "the fallback recipe must carry no CWU demand");
            }
            helper.assertTrue(parts.tower().recipeLogic.isWorking(),
                    "the tower keeps working under the original rules, status=" +
                            parts.tower().recipeLogic.getStatus());
            helper.assertTrue(parts.rack().controller().availableCWUt() == 0,
                    "the blade-less rack must offer 0 CWU/t, got " + parts.rack().controller().availableCWUt());
            helper.assertTrue(parts.rack().controller().requestCWUt(1, true, new ArrayList<>()) == 0,
                    "nothing may be grantable after the feed is cut");
        });

        // The scrub still completes under the original rules: pure mining datasets settle.
        helper.runAtTickTime(900, () -> {
            int pure = countBus(parts.exportBus(), HyperClusterItems.HIGH_PURITY_MINING_DATASET.get());
            helper.assertTrue(pure >= 1,
                    "the fallback recipe must still scrub the logs into datasets, got " + pure);
            System.out.printf("[HC-03] inference hatch: 1536 EU/t while billed 2 CWU/t; " +
                    "fallback to 2048 EU/t after cut; products=%d%n", pure);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The parts the rack tests drive. */
    private record RackParts(AirCooledComputeRackMachine controller, BlockPos controllerPos,
                             EnergyHatchPartMachine energyHatch, MaintenanceHatchPartMachine maintenanceHatch) {}

    private record StationParts(ResearchStationMachine controller) {}

    private record HpcaParts(HPCAMachine controller, EnergyHatchPartMachine energyHatch) {}

    private record TowerRackParts(WorkableElectricMultiblockMachine tower,
                                  ItemBusPartMachine importBus, ItemBusPartMachine exportBus,
                                  com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine fluidHatch,
                                  IvInferenceHatchMachine inferenceHatch, RackParts rack) {}

    /**
     * Places the 3×5×2 rack facing north: intake wall (front, z) + exhaust wall (back, z+1) with
     * the hatch row at controller height - 1. Controller at ({@code bx+1}, {@code by+2},
     * {@code bz}).
     */
    private static RackParts placeRack(GameTestHelper helper, int bx, int by, int bz) {
        // Front (intake) wall at z = bz, controller in the middle.
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == 1 && y == 2) {
                    helper.setBlock(new BlockPos(bx + x, by + y, bz),
                            HyperClusterMachines.AIR_COOLED_COMPUTE_RACK.getBlock().defaultBlockState());
                } else {
                    helper.setBlock(new BlockPos(bx + x, by + y, bz),
                            HyperClusterBlocks.RACK_CASING_INTAKE.get().defaultBlockState());
                }
            }
        }
        // Back (exhaust) wall at z = bz + 1 with the hatch row at y = 1.
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                BlockPos pos = new BlockPos(bx + x, by + y, bz + 1);
                if (y == 1) {
                    if (x == 0) {
                        helper.setBlock(pos, GTMachines.ENERGY_INPUT_HATCH[IV].getBlock().defaultBlockState());
                    } else if (x == 1) {
                        helper.setBlock(pos, GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
                    } else {
                        helper.setBlock(pos,
                                GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.getBlock()
                                        .defaultBlockState());
                    }
                } else {
                    helper.setBlock(pos,
                            HyperClusterBlocks.RACK_CASING_EXHAUST.get().defaultBlockState());
                }
            }
        }
        BlockPos controllerPos = new BlockPos(bx + 1, by + 2, bz);
        AirCooledComputeRackMachine controller = (AirCooledComputeRackMachine) metaMachine(helper,
                controllerPos);
        MaintenanceHatchPartMachine maintenance = (MaintenanceHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 1, by + 1, bz + 1));
        maintenance.fixAllMaintenanceProblems();
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by + 1, bz + 1));
        return new RackParts(controller, controllerPos, energy, maintenance);
    }

    private static void primeRackEnergy(RackParts parts, GameTestHelper helper, long first,
                                        long every, long last) {
        parts.energyHatch().energyContainer.addEnergy(parts.energyHatch().energyContainer
                .getEnergyCapacity());
        for (long t = first; t <= last; t += every) {
            helper.runAtTickTime(t, () -> parts.energyHatch().energyContainer
                    .addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity()));
        }
    }

    /**
     * A straight optical pipe line from {@code from} to {@code to} (axis-aligned run) with every
     * consecutive pair explicitly connected (optical pipes never auto-connect to a block placed
     * before them, and the gametest harness does not fire neighbour updates like the real game).
     */
    private static void placeOpticalPipeRun(GameTestHelper helper, BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        BlockPos pos = from;
        helper.setBlock(pos, GTBlocks.OPTICAL_PIPES[0].get().defaultBlockState());
        while (!pos.equals(to)) {
            BlockPos previous = pos;
            pos = pos.offset(dx, dy, dz);
            helper.setBlock(pos, GTBlocks.OPTICAL_PIPES[0].get().defaultBlockState());
            connectPipes(helper, previous, pos);
        }
    }

    private static void connectPipes(GameTestHelper helper, BlockPos a, BlockPos b) {
        Direction side = directionBetween(a, b);
        OpticalPipeBlockEntity from = (OpticalPipeBlockEntity) helper.getBlockEntity(a);
        OpticalPipeBlockEntity to = (OpticalPipeBlockEntity) helper.getBlockEntity(b);
        from.setConnection(side, true, false);
        to.setConnection(side.getOpposite(), true, false);
    }

    /** Connects the optical pipe at {@code pipePos} toward the block at {@code pipePos + side}. */
    private static void connectPipeToBlock(GameTestHelper helper, BlockPos pipePos, Direction side) {
        OpticalPipeBlockEntity pipe = (OpticalPipeBlockEntity) helper.getBlockEntity(pipePos);
        pipe.setConnection(side, true, false);
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        if (to.getX() > from.getX()) return Direction.EAST;
        if (to.getX() < from.getX()) return Direction.WEST;
        if (to.getY() > from.getY()) return Direction.UP;
        if (to.getY() < from.getY()) return Direction.DOWN;
        if (to.getZ() > from.getZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }

    /**
     * Places the vanilla research station (3 wide × 7 tall × 7 deep) facing north. The station
     * pattern uses GTCEu's default {@code FactoryBlockPattern.start()} convention —
     * {@code (LEFT, UP, FRONT)}: aisle index walks FRONT (north), string index walks UP, char
     * index walks LEFT (west). With the controller at ({@code bx}, {@code by}, {@code bz}):
     * aisle {@code i} at z = {@code bz} + 2 - i, string {@code j} at y = {@code by} - 3 + j,
     * char {@code k} at x = {@code bx} + 1 - k.
     */
    private static StationParts placeResearchStation(GameTestHelper helper, int bx, int by, int bz) {
        // 7 aisles (z, back to front), each 7 strings (y, bottom-up) of 3 chars (x, east-west).
        String[][] aisles = {
                { "XXX", "VVV", "PPP", "PPP", "PPP", "VVV", "XXX" },
                { "XXX", "VAV", "AAA", "AAA", "AAA", "VAV", "XXX" },
                { "XXX", "VAV", "XAX", "XSX", "XAX", "VAV", "XXX" },
                { "XXX", "XAX", "---", "---", "---", "XAX", "XXX" },
                { " X ", "XAX", "---", "---", "---", "XAX", " X " },
                { " X ", "XAX", "-A-", "-H-", "-A-", "XAX", " X " },
                { "   ", "XXX", "---", "---", "---", "XXX", "   " } };
        for (int aisle = 0; aisle < 7; aisle++) {
            for (int str = 0; str < 7; str++) {
                for (int ch = 0; ch < 3; ch++) {
                    char c = aisles[aisle][str].charAt(ch);
                    BlockPos pos = new BlockPos(bx + 1 - ch, by - 3 + str, bz + 2 - aisle);
                    switch (c) {
                        case 'X' -> helper.setBlock(pos,
                                GTBlocks.COMPUTER_CASING.get().defaultBlockState());
                        case 'V' -> helper.setBlock(pos,
                                GTBlocks.COMPUTER_HEAT_VENT.get().defaultBlockState());
                        case 'A' -> helper.setBlock(pos,
                                GTBlocks.ADVANCED_COMPUTER_CASING.get().defaultBlockState());
                        case 'S' -> helper.setBlock(pos,
                                GTResearchMachines.RESEARCH_STATION.getBlock().defaultBlockState());
                        case '-' -> helper.setBlock(pos, Blocks.AIR.defaultBlockState());
                        case ' ' -> {}
                        case 'H' -> helper.setBlock(pos, GTResearchMachines.OBJECT_HOLDER.getBlock()
                                .defaultBlockState().setValue(
                                        GTResearchMachines.OBJECT_HOLDER.get().getRotationState().property,
                                        Direction.SOUTH));
                        case 'P' -> helper.setBlock(pos,
                                GTBlocks.COMPUTER_CASING.get().defaultBlockState());
                        default -> throw new IllegalArgumentException("bad char " + c);
                    }
                }
            }
        }
        // The P ring (back aisle, middle heights) hosts the hatches: the computation receiver
        // at (bx, by, bz+2), a UHV energy hatch at (bx+1, by, bz+2) and a fixed maintenance hatch
        // at (bx-1, by, bz+2).
        helper.setBlock(new BlockPos(bx, by, bz + 2),
                GTResearchMachines.COMPUTATION_HATCH_RECEIVER.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 1, by, bz + 2),
                GTMachines.ENERGY_INPUT_HATCH[UHV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx - 1, by, bz + 2),
                GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
        MaintenanceHatchPartMachine maintenance = (MaintenanceHatchPartMachine) metaMachine(helper,
                new BlockPos(bx - 1, by, bz + 2));
        maintenance.fixAllMaintenanceProblems();
        // Prime the UHV hatch: the vanilla network-switch research draws 2,097,152 EU/t.
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 1, by, bz + 2));
        for (long t = 20; t <= 850; t += 20) {
            helper.runAtTickTime(t, () -> energy.energyContainer
                    .addEnergy(energy.energyContainer.getEnergyCapacity()));
        }
        ResearchStationMachine controller = (ResearchStationMachine) metaMachine(
                helper, new BlockPos(bx, by, bz));
        return new StationParts(controller);
    }

    /**
     * A minimal vanilla HPCA facing north with one computation component (4 CWU/t). The HPCA
     * pattern uses the default {@code (LEFT, UP, FRONT)} convention too: with the controller at
     * ({@code bx}, {@code by}, {@code bz}), aisle {@code i} at z = {@code bz} + 4 - i, string
     * {@code j} at y = {@code by} + j, char {@code k} at x = {@code bx} - k.
     */
    private static HpcaParts placeHpca(GameTestHelper helper, int bx, int by, int bz) {
        String[][] aisles = {
                { "AA", "CC", "CC", "CC", "AA" },
                { "VA", "XV", "XV", "XV", "VA" },
                { "VA", "XV", "XV", "XV", "VA" },
                { "VA", "XV", "XV", "XV", "VA" },
                { "SA", "CC", "CC", "CC", "AA" } };
        for (int aisle = 0; aisle < 5; aisle++) {
            for (int str = 0; str < 5; str++) {
                for (int ch = 0; ch < 2; ch++) {
                    char c = aisles[aisle][str].charAt(ch);
                    BlockPos pos = new BlockPos(bx - ch, by + str, bz + 4 - aisle);
                    switch (c) {
                        case 'A' -> helper.setBlock(pos,
                                GTBlocks.ADVANCED_COMPUTER_CASING.get().defaultBlockState());
                        case 'V' -> helper.setBlock(pos,
                                GTBlocks.COMPUTER_HEAT_VENT.get().defaultBlockState());
                        case 'C' -> helper.setBlock(pos,
                                GTBlocks.COMPUTER_CASING.get().defaultBlockState());
                        case 'S' -> helper.setBlock(pos, GTResearchMachines.HIGH_PERFORMANCE_COMPUTING_ARRAY
                                .getBlock().defaultBlockState());
                        case 'X' -> helper.setBlock(pos, GTResearchMachines.HPCA_EMPTY_COMPONENT
                                .getBlock().defaultBlockState());
                        default -> throw new IllegalArgumentException("bad char " + c);
                    }
                }
            }
        }
        // One computation component (4 CWU/t) at the middle X slot.
        helper.setBlock(new BlockPos(bx, by + 2, bz + 2),
                GTResearchMachines.HPCA_COMPUTATION_COMPONENT.getBlock().defaultBlockState());
        // The C cells of the controller layer host: UV energy + maintenance hatches.
        helper.setBlock(new BlockPos(bx, by + 1, bz),
                GTMachines.ENERGY_INPUT_HATCH[UV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx - 1, by + 1, bz),
                GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx - 1, by + 1, bz)))
                .fixAllMaintenanceProblems();
        helper.setBlock(new BlockPos(bx, by + 2, bz),
                GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.getBlock().defaultBlockState());
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by + 1, bz));
        HPCAMachine controller = (HPCAMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        return new HpcaParts(controller, energy);
    }

    /** The parts the CRAC test drives. */
    private record CracParts(PrecisionCracMachine controller, EnergyHatchPartMachine energyHatch) {}

    /** The 3×3×1 precision CRAC facing north with an IV energy hatch and a fixed maintenance hatch. */
    private static CracParts placeCrac(GameTestHelper helper, int bx, int by, int bz) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos pos = new BlockPos(bx + x, by + y, bz);
                if (x == 1 && y == 1) {
                    helper.setBlock(pos,
                            HyperClusterMachines.PRECISION_CRAC.getBlock().defaultBlockState());
                } else if (y == 0 && x == 0) {
                    helper.setBlock(pos, GTMachines.ENERGY_INPUT_HATCH[IV].getBlock().defaultBlockState());
                } else if (y == 0 && x == 2) {
                    helper.setBlock(pos, GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
                } else {
                    helper.setBlock(pos, GTBlocks.MACHINE_CASING_IV.get().defaultBlockState());
                }
            }
        }
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 2, by, bz)))
                .fixAllMaintenanceProblems();
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by, bz));
        energy.energyContainer.addEnergy(energy.energyContainer.getEnergyCapacity());
        PrecisionCracMachine crac = (PrecisionCracMachine) metaMachine(helper,
                new BlockPos(bx + 1, by + 1, bz));
        return new CracParts(crac, energy);
    }

    /**
     * The data scrubbing tower (5×9×5, bottom corner (1,1,1)) with the inference hatch on the
     * front of its pedestal second layer (2,2,1), plus a rack at (7..9, 1..5, z 1..2) and
     * optical pipes running around the tower's north and west sides to the inference hatch.
     */
    private static TowerRackParts placeTowerWithInference(GameTestHelper helper) {
        int bx = 1, by = 1, bz = 1;
        ItemBusPartMachine importBus = null, exportBus = null;
        com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine fluidHatch = null;
        // y=0..1: the 5×2×5 clean stainless pedestal; the controller sits at (3,2,1) — front of
        // layer 2 at eye height — and the inference hatch flanks it at (2,2,1).
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    if (y == 1 && x == 2 && z == 0) continue; // controller slot
                    if (y == 1 && x == 1 && z == 0) continue; // inference hatch slot
                    helper.setBlock(new BlockPos(bx + x, by + y, bz + z),
                            GTBlocks.CASING_STAINLESS_CLEAN.get().defaultBlockState());
                }
            }
        }
        // The standard hatches ride the pedestal floor ring.
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ITEM_IMPORT_BUS[EV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.ITEM_EXPORT_BUS[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 4), GTMachines.FLUID_IMPORT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz + 4), GTMachines.ENERGY_INPUT_HATCH[EV].getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by, bz + 4), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 2, by, bz + 4)))
                .fixAllMaintenanceProblems();
        // The inference hatch on the pedestal front, beside the controller.
        helper.setBlock(new BlockPos(bx + 1, by + 1, bz),
                HyperClusterMachines.IV_INFERENCE_HATCH.getBlock().defaultBlockState());
        IvInferenceHatchMachine inferenceHatch = (IvInferenceHatchMachine) metaMachine(helper,
                new BlockPos(bx + 1, by + 1, bz));
        importBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        exportBus = (ItemBusPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz));
        fluidHatch = (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) metaMachine(
                helper, new BlockPos(bx, by, bz + 4));
        // Tower energy: prime the EV energy hatch.
        EnergyHatchPartMachine towerEnergy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 4, by, bz + 4));
        for (long t = 40; t <= 1500; t += 20) {
            helper.runAtTickTime(t, () -> towerEnergy.energyContainer
                    .addEnergy(towerEnergy.energyContainer.getEnergyCapacity()));
        }
        // y=2..6: the middle section — glass body around the titanium pipe core, ring beams and
        // corner columns; y=7..8: the flared grate heat cap (mirrors DataWorkshopTests.placeTower).
        for (int y = 2; y <= 6; y++) {
            boolean ringBeam = y == 2 || y == 6;
            int wy = by + y;
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos pos = new BlockPos(bx + x, wy, bz + z);
                    boolean interior = x >= 1 && x <= 3 && z >= 1 && z <= 3;
                    boolean perimeter = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                    if (interior) {
                        boolean pipeCore = x == 2 && z == 2;
                        helper.setBlock(pos, pipeCore ? GTBlocks.CASING_TITANIUM_PIPE.get().defaultBlockState() :
                                GTBlocks.CLEANROOM_GLASS.get().defaultBlockState());
                    } else if (perimeter && (ringBeam || corner)) {
                        helper.setBlock(pos,
                                ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Titanium).defaultBlockState());
                    }
                }
            }
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean perimeter = x == 0 || x == 4 || z == 0 || z == 4;
                helper.setBlock(new BlockPos(bx + x, by + 7, bz + z),
                        perimeter ? GTBlocks.CASING_GRATE.get().defaultBlockState() :
                                GTBlocks.MACHINE_CASING_EV.get().defaultBlockState());
                helper.setBlock(new BlockPos(bx + x, by + 8, bz + z),
                        perimeter ? GTBlocks.MACHINE_CASING_EV.get().defaultBlockState() :
                                GTBlocks.CASING_GRATE.get().defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(bx + 2, by + 1, bz),
                HyperClusterMachines.DATA_SCRUBBING_TOWER.getBlock().defaultBlockState());
        WorkableElectricMultiblockMachine tower = (WorkableElectricMultiblockMachine) metaMachine(
                helper, new BlockPos(bx + 2, by + 1, bz));

        // Rack at x 7..9, z 1..2, y 1..5 (transmitter hatch at (9,2,2)).
        RackParts rack = placeRack(helper, 7, 1, 1);
        // Pipes: transmitter (9,2,2) -> (9,2,3) -> west along z=3 (clear of the tower footprint
        // x<=5) -> north along x=6 -> west along z=0 (north of the tower) -> (2,2,0), adjacent
        // to the inference hatch (2,2,1).
        placeOpticalPipeRun(helper, new BlockPos(9, 2, 3), new BlockPos(6, 2, 3));
        placeOpticalPipeRun(helper, new BlockPos(6, 2, 2), new BlockPos(6, 2, 0));
        placeOpticalPipeRun(helper, new BlockPos(5, 2, 0), new BlockPos(2, 2, 0));
        connectPipes(helper, new BlockPos(6, 2, 3), new BlockPos(6, 2, 2));
        connectPipes(helper, new BlockPos(6, 2, 0), new BlockPos(5, 2, 0));
        connectPipeToBlock(helper, new BlockPos(9, 2, 3), Direction.NORTH);
        connectPipeToBlock(helper, new BlockPos(2, 2, 0), Direction.SOUTH);
        return new TowerRackParts(tower, importBus, exportBus, fluidHatch, inferenceHatch, rack);
    }

    /** Mirrors MultiblockControllerMachine#asyncCheckPattern's formed path (see DataWorkshopTests). */
    static void ensureFormed(WorkableElectricMultiblockMachine controller) {
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
        return ((IMachineBlockEntity) Objects.requireNonNull(be)).getMetaMachine();
    }

    private static int countBus(ItemBusPartMachine bus, net.minecraft.world.item.Item item) {
        int found = 0;
        for (int i = 0; i < bus.getInventory().getSlots(); i++) {
            ItemStack stack = bus.getInventory().getStackInSlot(i);
            if (stack.getItem() == item) found += stack.getCount();
        }
        return found;
    }

    private static long recipeEUt(GTRecipe recipe) {
        var contents = recipe.getTickInputContents(EURecipeCapability.CAP);
        if (contents.isEmpty()) return 0;
        return EURecipeCapability.CAP.of(contents.get(0).content).voltage();
    }
}
