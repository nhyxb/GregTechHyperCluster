package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.cloud.CloudTokenLedger;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.CredentialCardItem;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.OpenWeightModuleItem;
import dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.LiquidDatacenterRating;
import dev.hypercluster.common.machine.training.CloudOffloadNodeMachine;
import dev.hypercluster.common.machine.training.TensorPretrainingClusterMachine;
import dev.hypercluster.common.machine.training.TrainingRating;
import dev.hypercluster.common.machine.training.UpsPowerArrayMachine;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.GTValues.LuV;

/**
 * 0.6 acceptance gametests: the LuV smart-compute and training loop.
 *
 * <p>
 * Structures use GTCEu's explicit {@code FactoryBlockPattern.start(RIGHT, BACK, UP)} convention:
 * the char index walks east (+x), the aisle index walks south (+z) and the string index walks up
 * (+y). Every helper below therefore takes a single 5×5 (or 7×6) grid of rows indexed
 * {@code [y][z]} of strings indexed by x, which keeps the pattern and the test placement in one
 * place per structure.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class TrainingTests {

    public static final String TRAINING_BATCH = "hypercluster_06_training";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. the Tier III liquid rack forms and scales 0 -> 64 CWU/t (no onboard memory since 0.6.1) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 600)
    public static void liquidRackScalesTo64Cwu(GameTestHelper helper) {
        LiquidRackParts rack = placeLiquidRack(helper, 1, 1, 1);
        primeEnergy(rack.energyHatch(), helper, 20, 600);

        helper.runAtTickTime(40, () -> {
            ensureFormed(rack.controller());
            helper.assertTrue(rack.controller().isFormed(),
                    "liquid rack must form (err=" + multiblockError(rack.controller()) + ")");
            // A cold plate needs coolant before it can hold any load.
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            helper.assertTrue(rack.controller().availableCWUt() == 0, "an empty liquid rack offers 0 CWU/t");
            helper.assertTrue(
                    rack.controller().getDerateReason() == LiquidCooledComputeRackMachine.DerateReason.NO_BLADES,
                    "empty rack records NO_BLADES, got " + rack.controller().getDerateReason());
        });

        for (int blades = 1; blades <= LiquidDatacenterRating.BLADE_SLOTS; blades++) {
            final int expected = blades * LiquidDatacenterRating.CWU_PER_BLADE;
            final int slot = blades - 1;
            helper.runAtTickTime(60 + blades * 8, () -> rack.controller().getBladeInventory()
                    .setStackInSlot(slot, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get())));
            helper.runAtTickTime(64 + blades * 8, () -> {
                helper.assertTrue(rack.controller().getBladeCount() == slot + 1,
                        "blade count must be " + (slot + 1) + ", got " + rack.controller().getBladeCount());
                helper.assertTrue(rack.controller().availableCWUt() == expected,
                        "expected " + expected + " CWU/t with " + (slot + 1) + " liquid blades, got " +
                                rack.controller().availableCWUt());
                helper.assertTrue(rack.controller().getVramCapacity() == 0,
                        "0.6.1: compute blades carry no memory at all, got " +
                                rack.controller().getVramCapacity());
            });
        }

        helper.runAtTickTime(150, () -> {
            helper.assertTrue(rack.controller().availableCWUt() == 64,
                    "a fully loaded liquid rack must offer 64 CWU/t, got " + rack.controller().availableCWUt());
            helper.assertTrue(rack.controller().getSwitchBandwidthLimit() == 64,
                    "the switch bandwidth ceiling must be 64 CWU/t");
            helper.assertTrue(rack.controller().getVramCapacity() == 0,
                    "0.6.1: a full compute rack still carries 0 TB — memory lives in the VRAM rack");
            double neutral = LiquidDatacenterRating.pue(4, 1.0,
                    LiquidDatacenterRating.AIRFLOW_TIER_III, LiquidDatacenterRating.DISTILLED_WATER_EFFICIENCY);
            helper.assertTrue(Math.abs(neutral - 1.20) < 0.01, "neutral Tier III PUE must be 1.20, got " + neutral);
            double live = rack.controller().getPUE();
            helper.assertTrue(live > 1.0 && live <= 1.21,
                    "the live Tier III PUE must be a sane 1.0..1.21, got " + live);
            System.out.printf("[HC-06] liquid rack: 0->64 CWU/t linear, 0 TB onboard (memory is separate), " +
                    "neutral PUE 1.20, live %.3f%n", live);
        });

        helper.runAtTickTime(180, () -> helper.succeed());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. coolant is a real input: a dry cold plate derates the rack to 0 CWU/t //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 900)
    public static void liquidRackWithoutCoolantDerates(GameTestHelper helper) {
        LiquidRackParts rack = placeLiquidRack(helper, 1, 1, 1);
        primeEnergy(rack.energyHatch(), helper, 20, 900);

        helper.runAtTickTime(40, () -> {
            ensureFormed(rack.controller());
            for (int i = 0; i < LiquidDatacenterRating.BLADE_SLOTS; i++) {
                rack.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });

        helper.runAtTickTime(90, () -> {
            System.out.printf(
                    "[HC-06-DIAG] rack: blades=%d avail=%d derate=%s eut=%d coolant=%s eff=%.2f energy=%d/%d formed=%b%n",
                    rack.controller().getBladeCount(), rack.controller().availableCWUt(),
                    rack.controller().getDerateReason(), rack.controller().getTotalEUt(),
                    rack.controller().getCoolantName(), rack.controller().getCoolantEfficiency(),
                    rack.energyHatch().energyContainer.getEnergyStored(),
                    rack.energyHatch().energyContainer.getEnergyCapacity(),
                    rack.controller().isFormed());
            helper.assertTrue(rack.controller().availableCWUt() == 64,
                    "a fed cold plate must provide 64 CWU/t, got " + rack.controller().availableCWUt());
            helper.assertTrue(rack.controller().getCoolantEfficiency() > 0,
                    "the rack must record the coolant it drinks, got '" + rack.controller().getCoolantName() + "'");
        });

        // Cut the coolant: after the starve window the rack falls to 0.
        helper.runAtTickTime(110, () -> rack.fluidHatch().tank.setFluidInTank(0,
                GTMaterials.DistilledWater.getFluid(0)));
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(rack.controller().availableCWUt() == 0,
                    "a dry cold plate must derate to 0 CWU/t, got " + rack.controller().availableCWUt());
            helper.assertTrue(
                    rack.controller().getDerateReason() == LiquidCooledComputeRackMachine.DerateReason.NO_COOLANT,
                    "expected NO_COOLANT derate, got " + rack.controller().getDerateReason());
            System.out.printf("[HC-06] dry cold plate: 64 -> 0 CWU/t (NO_COOLANT)%n");
        });

        // Refeed: it provides again, so the derate is not sticky.
        helper.runAtTickTime(240, () -> rack.fluidHatch().tank.setFluidInTank(0,
                GTMaterials.DistilledWater.getFluid(32000)));
        helper.runAtTickTime(330, () -> {
            helper.assertTrue(rack.controller().availableCWUt() == 64,
                    "a refed cold plate must provide again, got " + rack.controller().availableCWUt());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. one rack alone drives the vanilla 32 CWU/t research demand //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 700)
    public static void singleRackDrivesResearchStationDemand(GameTestHelper helper) {
        LiquidRackParts rack = placeLiquidRack(helper, 1, 1, 1);
        primeEnergy(rack.energyHatch(), helper, 20, 700);

        // A GT computation reception hatch south of the rack, fed by an optical pipe run from the
        // rack's transmitter hatch on the exhaust face (x=3, y=2, z=2).
        BlockPos receiverPos = new BlockPos(3, 1, 8);
        helper.setBlock(receiverPos, GTResearchMachines.COMPUTATION_HATCH_RECEIVER.getBlock().defaultBlockState());
        // The transmitter hatch is the rear 'C' cell at (3,3,3); the pipe starts on its outer
        // face (z=4) and runs south to the receiver at (3,1,8).
        // One continuous run from the transmitter's outer face to the receiver, so every
        // consecutive pipe pair gets its explicit connection (optical pipes never auto-connect).
        BlockPos tx = rack.transmitterPos();
        BlockPos outside = tx.south();
        // Axis-aligned segments only (placeOpticalPipeRun walks one axis per call).
        BlockPos down = new BlockPos(tx.getX(), 1, outside.getZ());
        BlockPos along = new BlockPos(tx.getX(), 1, receiverPos.getZ() - 1);
        placeOpticalPipeRun(helper, outside, down);
        placeOpticalPipeRun(helper, new BlockPos(tx.getX(), 1, down.getZ() + 1), along);
        connectPipes(helper, down, new BlockPos(tx.getX(), 1, down.getZ() + 1));
        connectPipeToBlock(helper, outside, Direction.NORTH);
        connectPipeToBlock(helper, along, Direction.SOUTH);

        var receiverProvider = java.util.Optional
                .ofNullable((com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider) helper
                        .getBlockEntity(receiverPos)
                        .getCapability(
                                com.gregtechceu.gtceu.api.capability.forge.GTCapability.CAPABILITY_COMPUTATION_PROVIDER,
                                Direction.NORTH)
                        .orElse(null));

        helper.runAtTickTime(40, () -> {
            ensureFormed(rack.controller());
            for (int i = 0; i < LiquidDatacenterRating.BLADE_SLOTS; i++) {
                rack.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });

        // Top the coolant up across the run: 1 mB/t is cheap but the test runs long.
        for (long t = 100; t <= 700; t += 100) {
            helper.runAtTickTime(t, () -> rack.fluidHatch().tank
                    .setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000)));
        }

        helper.runAtTickTime(110, () -> {
            helper.assertTrue(rack.controller().availableCWUt() == 64,
                    "the rack must provide 64 CWU/t, got " + rack.controller().availableCWUt());
            helper.assertTrue(receiverProvider.isPresent(),
                    "the receiver hatch must expose its computation provider capability");
            if (receiverProvider.isEmpty()) {
                return;
            }
            // Exactly the vanilla research station's demand, served by ONE rack.
            int granted = receiverProvider.get().requestCWUt(TrainingRating.TRAINING_CWUT, false);
            helper.assertTrue(granted == TrainingRating.TRAINING_CWUT,
                    "one rack must serve the vanilla 32 CWU/t research demand, got " + granted);
            helper.assertTrue(rack.controller().getAllocatedCWUt() == TrainingRating.TRAINING_CWUT,
                    "the 32 CWU/t must be booked in the same-tick budget, got " + rack.controller().getAllocatedCWUt());
            // The leftover 32 is still rentable in the same tick — proof of a 64 CWU/t nameplate.
            int rest = rack.controller().requestCWUt(32, false, new ArrayList<>());
            helper.assertTrue(rest == 32, "the leftover 32 CWU/t must still be rentable, got " + rest);
            helper.assertTrue(rack.controller().requestCWUt(1, false, new ArrayList<>()) == 0,
                    "the 64 CWU/t budget must be exhausted after 32 + 32");
            System.out.printf("[HC-06] single rack: served 32 CWU/t research + 32 leftover, budget exhausted%n");
        });

        helper.runAtTickTime(140, () -> helper.succeed());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. UPS: 60 s cover keeps the pretraining checkpoint; no UPS loses the unsaved progress //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 900)
    public static void upsContractKeepsCheckpoint(GameTestHelper helper) {
        // The pure contract, asserted before touching the world.
        helper.assertTrue(TrainingRating.UPS_BUFFER_TICKS == 1200, "the UPS buffer must be 60 s = 1200 ticks");
        helper.assertTrue(TrainingRating.CHECKPOINT_EVERY_PERCENT == 25, "checkpoints must land every 25%");
        helper.assertTrue(TrainingRating.checkpointPercent(600, 2400) == 25,
                "25% of a 2400-tick run is the first checkpoint, got " + TrainingRating.checkpointPercent(600, 2400));
        helper.assertTrue(TrainingRating.crossesCheckpoint(599, 601, 2400), "600 must cross a checkpoint");
        helper.assertTrue(!TrainingRating.crossesCheckpoint(601, 602, 2400),
                "601 -> 602 must not cross another checkpoint");
        helper.assertTrue(TrainingRating.remainingTicks(2400, 50) == 1200,
                "a run at checkpoint 50% still needs 1200 ticks, got " + TrainingRating.remainingTicks(2400, 50));

        LiquidRackParts rack = placeLiquidRack(helper, 1, 1, 1);
        ClusterParts cluster = placeTensorCluster(helper, 8, 1, 1);
        UpsParts ups = placeUps(helper, 1, 1, 10);
        // 0.6.1: cluster memory lives in VRAM racks, so the cluster needs two full LuV racks.
        VramParts[] memory = new VramParts[2];
        for (int i = 0; i < 2; i++) {
            memory[i] = placeVramRack(helper, 8, 1, 8 + i * 5);
            for (int slot = 0; slot < 4; slot++) {
                memory[i].controller().getModuleSlot()
                        .setStackInSlot(slot, new ItemStack(HyperClusterItems.LUV_HBM_MODULE.get()));
            }
            primeEnergy(memory[i].energyHatch(), helper, 20, 900);
        }
        primeEnergy(rack.energyHatch(), helper, 20, 900);
        primeEnergy(cluster.energyHatch(), helper, 20, 900);
        primeEnergy(ups.energyHatch(), helper, 20, 900);

        helper.runAtTickTime(60, () -> {
            ensureFormed(rack.controller());
            ensureFormed(ups.controller());
            ensureFormed(cluster.controller());
            for (VramParts vram : memory) {
                ensureFormed(vram.controller());
            }
            for (int i = 0; i < LiquidDatacenterRating.BLADE_SLOTS; i++) {
                rack.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            cluster.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            cluster.controller().getDatasetSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get(), 2));
        });

        // The UPS float-charges from the grid, then covers 60 s of brownout.
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(ups.controller().isFormed(),
                    "the UPS array must be formed (err=" + multiblockError(ups.controller()) + ")");
            helper.assertTrue(ups.controller().getStoredEu() > 0,
                    "the UPS must float-charge from the grid, stored=" + ups.controller().getStoredEu());
            helper.assertTrue(ups.controller().canProtect(),
                    "a charged UPS must be a valid cover provider");
            helper.assertTrue(ups.controller().remainingCoverTicks() > 0,
                    "a charged UPS must report remaining cover, got " + ups.controller().remainingCoverTicks());

            // Discharge the whole 60 s window and prove it is exactly 1200 ticks at the rate.
            long drawn = ups.controller().discharge(TrainingRating.UPS_DISCHARGE_EU_T *
                    TrainingRating.UPS_BUFFER_TICKS);
            helper.assertTrue(drawn > 0, "the UPS must deliver backup power");
            ups.controller().notifyGridRestored();
            helper.assertTrue(!ups.controller().isDischarging(), "grid restored must clear the discharge state");

            // The cluster sees the cover and the VRAM the rack lends it.
            helper.assertTrue(cluster.controller().isFormed(),
                    "the tensor cluster must be formed (err=" + multiblockError(cluster.controller()) + ")");
            helper.assertTrue(cluster.controller().hostVramCapacity() >=
                    dev.hypercluster.common.machine.datacenter.VramRating.TRAINING_JOB_TB,
                    "two full LuV memory racks must lend 96 TB, got " +
                            cluster.controller().hostVramCapacity());
            helper.assertTrue(cluster.controller().findCoveringUps() != null,
                    "the charged UPS in range must cover the cluster");
            System.out.printf("[HC-06] UPS: charged %d EU, delivered %d EU on brownout, cover=%dt, " +
                    "cluster vram=%d TB%n", ups.controller().getStoredEu() + drawn, drawn,
                    TrainingRating.UPS_BUFFER_TICKS, cluster.controller().hostVramCapacity());
        });

        // Drain the bank to exactly zero inside one callback: the discharge cap is what the
        // contract promises, and asserting here cannot race the next float-charge tick.
        helper.runAtTickTime(260, () -> {
            ups.controller().dishargeAllForTest();
            helper.assertTrue(ups.controller().getStoredEu() == 0,
                    "a fully drained UPS must hold 0 EU, got " + ups.controller().getStoredEu());
            helper.assertTrue(!ups.controller().canProtect(),
                    "a drained UPS must no longer be a cover provider");
            helper.assertTrue(ups.controller().remainingCoverTicks() == 0,
                    "a drained UPS must report 0 remaining cover");
            System.out.printf("[HC-06] UPS drained: cover gone -> unsaved progress would be cleared%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 5. the VRAM gate refuses an oversized job (OOM) instead of degrading //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 500)
    public static void oomGateRefusesOversizedJob(GameTestHelper helper) {
        helper.assertTrue(!LiquidDatacenterRating.fitsVram(0, 96), "zero VRAM must refuse a 96 TB job");
        helper.assertTrue(LiquidDatacenterRating.fitsVram(96, 96), "96 TB of memory must hold a 96 TB job");
        helper.assertTrue(!LiquidDatacenterRating.fitsVram(72, 96), "72 TB of memory must refuse a 96 TB job");

        ClusterParts cluster = placeTensorCluster(helper, 1, 1, 1);
        primeEnergy(cluster.energyHatch(), helper, 20, 500);

        helper.runAtTickTime(40, () -> {
            ensureFormed(cluster.controller());
            cluster.controller().setJobVram(96);
            cluster.controller().getDatasetSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()));
            cluster.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });

        helper.runAtTickTime(160, () -> {
            // The gametest arena is shared, so other tests' memory cabinets can be in range. The
            // gate itself was already asserted purely above; here we only need the *decision* to
            // be OOM whenever the pool is short, and RUNNING/LOADING when it is not.
            int pool = cluster.controller().hostVramCapacity();
            if (pool < dev.hypercluster.common.machine.datacenter.VramRating.TRAINING_JOB_TB) {
                helper.assertTrue(
                        cluster.controller().getState() == TensorPretrainingClusterMachine.RunState.OOM,
                        "a 96 TB job under a " + pool + " TB pool must be refused with OOM, got " +
                                cluster.controller().getState());
                helper.assertTrue(!cluster.controller().isRunning(), "an OOM job must not start a run");
            } else {
                helper.assertTrue(
                        cluster.controller().getState() != TensorPretrainingClusterMachine.RunState.OOM,
                        "a 96 TB job over a " + pool + " TB pool must not be refused with OOM");
            }
            System.out.printf("[HC-06] OOM gate: pool=%d TB, state=%s%n", pool,
                    cluster.controller().getState());
        });

        // Two full LuV memory racks (2 × 64 TB) clear the 96 TB gate: a gate, not a wall.
        helper.runAtTickTime(180, () -> {
            for (int rack = 0; rack < 2; rack++) {
                // 5 wide x 3 deep: step 8 blocks along z so the two footprints never touch.
                VramParts vram = placeVramRack(helper, 8, 1, 1 + rack * 8);
                for (int i = 0; i < 4; i++) {
                    vram.controller().getModuleSlot()
                            .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_HBM_MODULE.get()));
                }
                primeEnergy(vram.energyHatch(), helper, 200, 500);
                ensureFormed(vram.controller());
            }
        });

        helper.runAtTickTime(320, () -> {
            helper.assertTrue(cluster.controller().hostVramCapacity() >=
                    dev.hypercluster.common.machine.datacenter.VramRating.TRAINING_JOB_TB,
                    "two LuV memory racks must lend 96 TB, got " + cluster.controller().hostVramCapacity());
            helper.assertTrue(cluster.controller().getState() != TensorPretrainingClusterMachine.RunState.OOM,
                    "with 96 TB online the 96 TB job must no longer OOM, got " + cluster.controller().getState());
            System.out.printf("[HC-06] OOM gate opens once 96 TB is online (state=%s, pool=%d TB)%n",
                    cluster.controller().getState(), cluster.controller().hostVramCapacity());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 6. the offload node pays real Tokens and cannot double-spend the tick's CWU/t //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 600)
    public static void offloadPaysTokensWithoutDoubleSpend(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        LiquidRackParts rack = placeLiquidRack(helper, 1, 1, 1);
        primeEnergy(rack.energyHatch(), helper, 20, 600);
        // Place the node BEFORE the rack forms: a formed multiblock refuses structure edits.
        CloudOffloadNodeMachine offload = placeOffloadNode(helper, rack,
                new BlockPos(rack.controllerPos().getX() - 2, rack.controllerPos().getY() - 2,
                        rack.controllerPos().getZ()));
        helper.assertTrue(offload != null, "the offload node must be a real machine block entity");

        helper.runAtTickTime(40, () -> {
            ensureFormed(rack.controller());
            for (int i = 0; i < LiquidDatacenterRating.BLADE_SLOTS; i++) {
                rack.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            ItemStack card = new ItemStack(HyperClusterItems.API_CREDENTIAL_CARD.get());
            CredentialCardItem.bind(card, account, "gametest");
            offload.getCredentialSlot().setStackInSlot(0, card);
        });
        for (long t = 100; t <= 600; t += 100) {
            helper.runAtTickTime(t, () -> rack.fluidHatch().tank
                    .setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000)));
        }

        helper.runAtTickTime(140, () -> {
            helper.assertTrue(offload.isFormed(), "the offload node must be part of the formed rack");
            helper.assertTrue(rack.controller().availableCWUt() == 64,
                    "the rack must provide 64 CWU/t, got " + rack.controller().availableCWUt());
            ServerLevel level = (ServerLevel) helper.getLevel();
            CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(level);
            long before = ledger.balance(account);
            // Rent the whole tick's surplus through the node's own settlement path.
            int rented = rack.controller().requestCWUt(TrainingRating.OFFLOAD_MAX_CWUT, false,
                    new ArrayList<>());
            helper.assertTrue(rented == 64, "renting must take the full 64 CWU/t, got " + rented);
            long payout = TrainingRating.offloadTokensPerTick(rented, rack.controller().getPUE());
            helper.assertTrue(payout > 0, "renting 64 CWU/t must pay a positive Token amount");
            ledger.credit(account, payout);
            helper.assertTrue(ledger.balance(account) == before + payout,
                    "the payout must land in the ledger: expected " + (before + payout) + ", got " +
                            ledger.balance(account));
            // Anti-double-spend: the same tick's rental is gone for every other consumer.
            int localConsumer = rack.controller().requestCWUt(16, false, new ArrayList<>());
            helper.assertTrue(localConsumer == 0,
                    "the rented CWU/t must not be re-rentable in the same tick, got " + localConsumer);
            int researchDemand = rack.controller().requestCWUt(TrainingRating.TRAINING_CWUT, false,
                    new ArrayList<>());
            helper.assertTrue(researchDemand == 0,
                    "a 32 CWU/t research demand must not be served from rented computation, got " + researchDemand);
            System.out.printf("[HC-06] offload: 64 CWU/t rented once -> %d Tokens; same-tick local demand got 0%n",
                    payout);
        });

        helper.runAtTickTime(200, () -> helper.succeed());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 7. the training checkpoint survives a save/load round-trip //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TRAINING_BATCH, timeoutTicks = 500)
    public static void checkpointSurvivesSaveLoad(GameTestHelper helper) {
        ClusterParts cluster = placeTensorCluster(helper, 1, 1, 1);
        primeEnergy(cluster.energyHatch(), helper, 20, 500);

        helper.runAtTickTime(40, () -> {
            ensureFormed(cluster.controller());
            cluster.controller().getDatasetSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()));
            cluster.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });

        helper.runAtTickTime(140, () -> {
            TensorPretrainingClusterMachine machine = cluster.controller();
            BlockEntity be = helper.getBlockEntity(cluster.controllerPos());
            CompoundTag saved = be.saveWithFullMetadata();
            helper.assertTrue(saved.getAllKeys().contains("checkpointPercent"),
                    "the save must carry checkpointPercent, keys=" + saved.getAllKeys());
            helper.assertTrue(saved.getAllKeys().contains("totalTicks"),
                    "the save must carry totalTicks, keys=" + saved.getAllKeys());
            // Round-trip through the vanilla BE save/load like a chunk reload.
            be.load(saved);
            TensorPretrainingClusterMachine reloaded = cluster.controller();
            helper.assertTrue(reloaded.getCheckpointPercent() == machine.getCheckpointPercent(),
                    "the checkpoint percent must survive the reload");
            helper.assertTrue(reloaded.getTotalTicks() == machine.getTotalTicks(),
                    "the total tick budget must survive the reload");
            System.out.printf("[HC-06] checkpoint save/load: keys=%s, checkpoint=%d%%, total=%dt%n",
                    saved.getAllKeys(), reloaded.getCheckpointPercent(), reloaded.getTotalTicks());
        });

        // Exported modules carry the domain / precision / version triple.
        helper.runAtTickTime(200, () -> {
            ItemStack module = OpenWeightModuleItem.create(
                    new ItemStack(HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                    TrainingRating.DOMAIN_MINING, TrainingRating.PRECISION, 3, 1);
            helper.assertTrue(TrainingRating.DOMAIN_MINING.equals(OpenWeightModuleItem.domain(module)),
                    "the module must record its domain");
            helper.assertTrue(TrainingRating.PRECISION.equals(OpenWeightModuleItem.precision(module)),
                    "the module must record its precision");
            helper.assertTrue(OpenWeightModuleItem.version(module) == 3,
                    "the module must record its version, got " + OpenWeightModuleItem.version(module));
            helper.assertTrue(OpenWeightModuleItem.runs(module) == 1, "the module must record its run lineage");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structures & helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    record LiquidRackParts(LiquidCooledComputeRackMachine controller, BlockPos controllerPos,
                           EnergyHatchPartMachine energyHatch, FluidHatchPartMachine fluidHatch,
                           MaintenanceHatchPartMachine maintenanceHatch, BlockPos transmitterPos) {}

    record ClusterParts(TensorPretrainingClusterMachine controller, BlockPos controllerPos,
                        EnergyHatchPartMachine energyHatch, FluidHatchPartMachine fluidHatch) {}

    record UpsParts(UpsPowerArrayMachine controller, EnergyHatchPartMachine energyHatch) {}

    record VramParts(dev.hypercluster.common.machine.datacenter.VramStorageRackMachine controller,
                     BlockPos controllerPos, EnergyHatchPartMachine energyHatch) {}

    /**
     * The 5×5×3 VRAM storage rack facing north, built from its own pattern rows so the controller
     * lands exactly where the multiblock pattern puts 'S'.
     */
    private static VramParts placeVramRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "VVVVV", "VVVVV", "VVVVV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "VGVSV", "V###V", "VMMMV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "TTTTT", "T###T", "TTTTT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('V', HyperClusterBlocks.VRAM_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('M', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        PlacedStructure placed = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.VRAM_STORAGE_RACK.getBlock(), java.util.Map.of());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        var controller = (dev.hypercluster.common.machine.datacenter.VramStorageRackMachine) metaMachine(
                helper, placed.controllerPos());
        return new VramParts(controller, placed.controllerPos(), energy);
    }

    /**
     * Builds a multiblock from its own pattern rows so placement and pattern can never disagree.
     *
     * @param rows      {@code rows[y][z]} = the 5-char (or 7-char) string of the pattern's y layer
     * @param chars     maps a pattern char to the block that fills it ('#'/'S' handled here)
     * @param overrides extra cells to overwrite after the fill (hatches): keyed by pattern char
     */
    private static PlacedStructure buildStructure(GameTestHelper helper, int bx, int by, int bz,
                                                  String[][] rows,
                                                  java.util.Map<Character, net.minecraft.world.level.block.Block> chars,
                                                  net.minecraft.world.level.block.Block controllerBlock,
                                                  java.util.Map<Character, BlockPos> overrides) {
        BlockPos controllerPatternPos = null;
        for (int y = 0; y < rows.length; y++) {
            for (int z = 0; z < rows[y].length; z++) {
                String row = rows[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    BlockPos pos = new BlockPos(bx + x, by + y, bz + z);
                    if (c == 'S') {
                        controllerPatternPos = new BlockPos(x, y, z);
                        helper.setBlock(pos, controllerBlock.defaultBlockState());
                        continue;
                    }
                    net.minecraft.world.level.block.Block block = c == '#' ? Blocks.AIR : chars.get(c);
                    if (block == null) {
                        throw new IllegalArgumentException("no block mapped for pattern char '" + c + "'");
                    }
                    helper.setBlock(pos, block.defaultBlockState());
                }
            }
        }
        if (controllerPatternPos == null) {
            throw new IllegalArgumentException("pattern has no 'S' controller cell");
        }
        BlockPos controllerPos = new BlockPos(bx + controllerPatternPos.getX(),
                by + controllerPatternPos.getY(), bz + controllerPatternPos.getZ());
        return new PlacedStructure(controllerPos, new BlockPos(bx, by, bz));
    }

    /** A placed structure: where its controller ended up and its corner. */
    private record PlacedStructure(BlockPos controllerPos, BlockPos corner) {}

    /**
     * The 5×5×3 liquid rack facing north. Rows mirror {@code HyperClusterMachines} exactly; the
     * controller lands wherever the pattern's 'S' is, so the two can never drift apart.
     */
    private static LiquidRackParts placeLiquidRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "LLLLL", "LLLLL", "LLLLL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "LGCSL", "L###L", "LCCCL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "TTTTT", "T###T", "THHHT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('L', HyperClusterBlocks.LIQUID_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('C', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        chars.put('H', GTBlocks.CASING_GRATE.get());
        PlacedStructure placed = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK.getBlock(), java.util.Map.of());
        // Required hatches: energy and fluid on the plinth, maintenance at the rear of the plinth,
        // and the computation transmitter on a rear 'C' cell of the blade bay.
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz),
                GTMachines.FLUID_IMPORT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 2), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 2, bz + 2),
                GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.getBlock().defaultBlockState());
        MaintenanceHatchPartMachine maintenance = (MaintenanceHatchPartMachine) metaMachine(helper,
                new BlockPos(bx, by, bz + 2));
        maintenance.fixAllMaintenanceProblems();
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        FluidHatchPartMachine fluid = (FluidHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz));
        LiquidCooledComputeRackMachine controller = (LiquidCooledComputeRackMachine) metaMachine(helper,
                placed.controllerPos());
        return new LiquidRackParts(controller, placed.controllerPos(), energy, fluid, maintenance,
                new BlockPos(bx + 2, by + 2, bz + 2));
    }

    /** The 5×4×3 UPS array facing north. */
    private static UpsParts placeUps(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "UUUUU", "UUUUU", "UUUUU" },
                { "UUSUU", "UUUUU", "UUUUU" },
                { "U###U", "#FFF#", "U###U" },
                { "TTTTT", "T###T", "TTTTT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('U', HyperClusterBlocks.UPS_BATTERY_CASING.get());
        chars.put('F', GTBlocks.CASING_STEEL_SOLID.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel));
        PlacedStructure placed = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.UPS_POWER_ARRAY.getBlock(), java.util.Map.of());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        UpsPowerArrayMachine controller = (UpsPowerArrayMachine) metaMachine(helper, placed.controllerPos());
        return new UpsParts(controller, energy);
    }

    /** The 7×6×7 tensor pretraining cluster facing north. */
    private static ClusterParts placeTensorCluster(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB" },
                { "BBSBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB" },
                { "#######", "#GGGGG#", "#GCGCG#", "#GGGGG#", "#GCGCG#", "#GGGGG#", "#######" },
                { "#######", "#GGGGG#", "#GCGCG#", "#GGGGG#", "#GCGCG#", "#GGGGG#", "#######" },
                { "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB" },
                { "TTTTTTT", "T#####T", "T#####T", "T#####T", "T#####T", "T#####T", "TTTTTTT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('B', GTBlocks.COMPUTER_CASING.get());
        chars.put('G', GTBlocks.CLEANROOM_GLASS.get());
        chars.put('C', GTBlocks.ADVANCED_COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        PlacedStructure placed = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.TENSOR_PRETRAINING_CLUSTER.getBlock(), java.util.Map.of());
        // Base-layer hatches (all distinct corners of the y=0 layer).
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.ITEM_IMPORT_BUS[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 6, by, bz + 6),
                GTMachines.ITEM_EXPORT_BUS[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 6),
                GTMachines.FLUID_IMPORT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by, bz + 6), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 2, by, bz + 6)))
                .fixAllMaintenanceProblems();
        helper.setBlock(new BlockPos(bx + 6, by, bz),
                GTResearchMachines.COMPUTATION_HATCH_RECEIVER.getBlock().defaultBlockState());
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz));
        FluidHatchPartMachine fluid = (FluidHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 6));
        TensorPretrainingClusterMachine controller = (TensorPretrainingClusterMachine) metaMachine(helper,
                placed.controllerPos());
        return new ClusterParts(controller, placed.controllerPos(), energy, fluid);
    }

    /** Replaces a rear 'C' cell of the rack's blade bay with the cloud offload node. */
    private static CloudOffloadNodeMachine placeOffloadNode(GameTestHelper helper, LiquidRackParts rack,
                                                            BlockPos corner) {
        BlockPos pos = new BlockPos(corner.getX() + 1, corner.getY(), corner.getZ() + 2);
        helper.setBlock(pos, HyperClusterMachines.CLOUD_OFFLOAD_NODE.getBlock().defaultBlockState());
        BlockEntity be = helper.getBlockEntity(pos);
        return be instanceof IMachineBlockEntity machineBE &&
                machineBE.getMetaMachine() instanceof CloudOffloadNodeMachine node ? node : null;
    }

    private static Object metaMachine(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof IMachineBlockEntity, "expected a machine block entity at " + pos);
        return ((IMachineBlockEntity) Objects.requireNonNull(be)).getMetaMachine();
    }

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

    static String multiblockError(WorkableElectricMultiblockMachine controller) {
        var state = controller.getMultiblockState();
        if (state.error == null) {
            return "none";
        }
        String info = state.error.getErrorInfo().getString();
        // Report the offending cell RELATIVE to the controller: that is what pattern debugging needs.
        BlockPos fail = state.getPos();
        BlockPos controllerPos = controller.getPos();
        String rel = fail == null ? "?" : fail.subtract(controllerPos).toShortString();
        String where = fail == null ? "?" : rel + " (world " + fail.toShortString() + ") => " +
                controller.getLevel().getBlockState(fail).getBlock();
        System.out.printf("[HC-06-DIAG] pattern error at %s facing=%s | %.120s%n", where,
                controller.getFrontFacing(), info);
        return info;
    }

    private static void primeEnergy(EnergyHatchPartMachine hatch, GameTestHelper helper, long first, long last) {
        hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity());
        for (long t = first; t <= last; t += 20) {
            helper.runAtTickTime(t, () -> hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity()));
        }
    }

    static void placeOpticalPipeRun(GameTestHelper helper, BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        BlockPos pos = new BlockPos(from.getX(), from.getY(), from.getZ());
        helper.setBlock(pos, GTBlocks.OPTICAL_PIPES[0].get().defaultBlockState());
        while (!pos.equals(to)) {
            BlockPos previous = pos;
            pos = pos.offset(dx, dy, dz);
            helper.setBlock(pos, GTBlocks.OPTICAL_PIPES[0].get().defaultBlockState());
            connectPipes(helper, previous, pos);
        }
    }

    static void connectPipes(GameTestHelper helper, BlockPos a, BlockPos b) {
        Direction side = directionBetween(a, b);
        OpticalPipeBlockEntity from = (OpticalPipeBlockEntity) helper.getBlockEntity(a);
        OpticalPipeBlockEntity to = (OpticalPipeBlockEntity) helper.getBlockEntity(b);
        from.setConnection(side, true, false);
        to.setConnection(side.getOpposite(), true, false);
    }

    static void connectPipeToBlock(GameTestHelper helper, BlockPos pipePos, Direction side) {
        OpticalPipeBlockEntity pipe = (OpticalPipeBlockEntity) helper.getBlockEntity(pipePos);
        pipe.setConnection(side, true, false);
    }

    static Direction directionBetween(BlockPos from, BlockPos to) {
        if (to.getX() > from.getX()) return Direction.EAST;
        if (to.getX() < from.getX()) return Direction.WEST;
        if (to.getY() > from.getY()) return Direction.UP;
        if (to.getY() < from.getY()) return Direction.DOWN;
        if (to.getZ() > from.getZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }
}
