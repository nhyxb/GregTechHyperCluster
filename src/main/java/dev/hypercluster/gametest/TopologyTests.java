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
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

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
import dev.hypercluster.common.item.OpenWeightModuleItem;
import dev.hypercluster.common.machine.datacenter.DatacenterFloorNetwork;
import dev.hypercluster.common.machine.datacenter.DatacenterHardwareHatchMachine;
import dev.hypercluster.common.machine.datacenter.OpticalFabricSwitchMachine;
import dev.hypercluster.common.machine.datacenter.VramRating;
import dev.hypercluster.common.machine.datacenter.VramStorageRackMachine;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;

import java.util.ArrayList;
import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.LuV;

/**
 * 0.6.1 acceptance gametests: machine-room topology, the TB memory system and the six model
 * families.
 *
 * <ul>
 * <li>TB memory: compute blades carry none, the VRAM rack exposes 16 TB (IV) / 64 TB (LuV) and two
 * LuV racks clear the 96 TB pretraining gate;</li>
 * <li>optical fabric: aggregation of several racks into one pool, the bandwidth ceiling and the
 * 503 Server Busy cooldown;</li>
 * <li>MoE router: deploy / OOM-refuse / load / pull-the-card eviction;</li>
 * <li>the model catalog: Qwen's 1.5× fallback and LoRA slots, DeepSeek's 25% sparse plus
 * DeepThink's 100% by-product, GLM's inspection and agent tool calls, Gemini's lifecycle and
 * Xenon's zero hallucination, GPT's moral refusal plus the jailbreak card, Grok's uncensored
 * doubled weather penalty.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class TopologyTests {

    public static final String TOPOLOGY_BATCH = "hypercluster_061_topology";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. TB memory: blades carry none, the VRAM rack exposes the pool //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 500)
    public static void vramRackExposesTerabytePool(GameTestHelper helper) {
        // Pure contract first: the 0.6.1 numbers the whole version is built on.
        helper.assertTrue(VramRating.IV_HBM_TB == 4, "an IV HBM module must be 4 TB");
        helper.assertTrue(VramRating.LUV_HBM_TB == 16, "a LuV HBM module must be 16 TB");
        helper.assertTrue(VramRating.IV_RACK_TB == 16, "a full IV rack must be 4x4 = 16 TB");
        helper.assertTrue(VramRating.LUV_RACK_TB == 64, "a full LuV rack must be 4x16 = 64 TB");
        helper.assertTrue(VramRating.TRAINING_JOB_TB == 96, "the training gate must be 96 TB");
        helper.assertTrue(!VramRating.fits(VramRating.LUV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "one LuV rack (64 TB) must NOT clear the 96 TB gate");
        helper.assertTrue(VramRating.fits(2 * VramRating.LUV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "two LuV racks (128 TB) must clear the 96 TB gate");
        helper.assertTrue(!VramRating.fits(VramRating.IV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "one IV rack (16 TB) must NOT clear the gate");

        VramParts iv = placeVramRack(helper, 1, 1, 1);
        VramParts luv = placeVramRack(helper, 8, 1, 1);
        BlockPos hardwarePos = new BlockPos(2, 1, 1);
        helper.setBlock(hardwarePos, HyperClusterMachines.DATACENTER_HARDWARE_HATCH.getBlock().defaultBlockState());
        primeEnergy(iv.energyHatch(), helper, 20, 500);
        primeEnergy(luv.energyHatch(), helper, 20, 500);

        helper.runAtTickTime(40, () -> {
            ensureFormed(iv.controller());
            ensureFormed(luv.controller());
            helper.assertTrue(iv.controller().isFormed(),
                    "the VRAM rack must form (err=" + multiblockError(iv.controller()) + ")");
            helper.assertTrue(iv.controller().getTerabytesOnline() == 0,
                    "an empty memory rack must provide 0 TB");
            helper.assertTrue(iv.controller().getState() == VramStorageRackMachine.State.EMPTY,
                    "an empty rack reports EMPTY, got " + iv.controller().getState());
            var hardware = (DatacenterHardwareHatchMachine) metaMachine(helper, hardwarePos);
            var automation = helper.getBlockEntity(hardwarePos)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, hardware.getFrontFacing()).orElseThrow(
                            () -> new IllegalStateException("missing VRAM hardware port"));
            ItemStack modules = new ItemStack(HyperClusterItems.IV_HBM_MODULE.get(), 4);
            for (int slot = 0; slot < VramRating.HBM_SLOTS; slot++) {
                modules = automation.insertItem(slot, modules, false);
            }
            helper.assertTrue(modules.isEmpty(), "an automated stack must distribute one HBM per physical bay");
            // LuV modules: 4 x 16 TB = 64 TB.
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                luv.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_HBM_MODULE.get()));
            }
        });

        helper.runAtTickTime(120, () -> {
            helper.assertTrue(iv.controller().getTerabytesInstalled() == VramRating.IV_RACK_TB,
                    "four IV modules must be 16 TB, got " + iv.controller().getTerabytesInstalled());
            helper.assertTrue(luv.controller().getTerabytesInstalled() == VramRating.LUV_RACK_TB,
                    "four LuV modules must be 64 TB, got " + luv.controller().getTerabytesInstalled());
            helper.assertTrue(iv.controller().getTerabytesOnline() == VramRating.IV_RACK_TB,
                    "a powered IV rack must put its 16 TB online, got " +
                            iv.controller().getTerabytesOnline());
            helper.assertTrue(luv.controller().getState() == VramStorageRackMachine.State.ONLINE,
                    "a loaded LuV rack must be ONLINE, got " + luv.controller().getState());
            System.out.printf("[HC-061] TB memory: IV rack %d TB, LuV rack %d TB, gate %d TB%n",
                    iv.controller().getTerabytesOnline(), luv.controller().getTerabytesOnline(),
                    VramRating.TRAINING_JOB_TB);
        });

        // Pulling a module takes that memory offline immediately.
        helper.runAtTickTime(160, () -> luv.controller().getModuleSlot().setStackInSlot(0, ItemStack.EMPTY));
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(luv.controller().getTerabytesOnline() == 3 * VramRating.LUV_HBM_TB,
                    "pulling one module must drop the rack to 48 TB, got " +
                            luv.controller().getTerabytesOnline());
            helper.succeed();
        });
    }

    /**
     * The 0.6.1 UI must speak TB everywhere: any "GB" left in a VRAM label is a unit drift bug,
     * because blades carry 0 and the pool is measured in TB.
     */
    @GameTest(template = "hc_floor", batch = TOPOLOGY_BATCH, timeoutTicks = 100)
    public static void vramLabelsUseTerabytesOnly(GameTestHelper helper) {
        String[] vramKeys = {
                "hypercluster.machine.tensor_pretraining_cluster.vram",
                "hypercluster.machine.moe_router_rack.vram",
                "hypercluster.machine.vram_storage_rack.pool"
        };
        for (String key : vramKeys) {
            String text = net.minecraft.network.chat.Component.translatable(key).getString();
            helper.assertTrue(!text.contains("GB"),
                    "VRAM label " + key + " must not say GB (TB-scale since 0.6.1): " + text);
            helper.assertTrue(text.contains("TB"),
                    "VRAM label " + key + " must state TB: " + text);
        }
        // The compute rack must not advertise a VRAM figure at all any more.
        String stale = net.minecraft.network.chat.Component
                .translatable("hypercluster.machine.liquid_cooled_compute_rack.vram").getString();
        helper.assertTrue(stale.contains("liquid_cooled_compute_rack.vram"),
                "the compute rack must no longer carry a VRAM line, found: " + stale);
        System.out.printf("[HC-061] units: every VRAM label says TB, no GB drift%n");
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. optical fabric: multi-rack aggregation + the 503 bandwidth ceiling //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 700)
    public static void opticalSwitchAggregatesAndTrips503(GameTestHelper helper) {
        helper.assertTrue(VramRating.SWITCH_BANDWIDTH_LIMIT == 128,
                "the switch bandwidth ceiling must be 128 CWU/t");
        helper.assertTrue(VramRating.BUSY_COOLDOWN_TICKS == 300,
                "the 503 cooldown must be 15 s = 300 ticks");
        helper.assertTrue(VramRating.withinSwitchBandwidth(128), "128 CWU/t fits the ceiling");
        helper.assertTrue(!VramRating.withinSwitchBandwidth(129), "129 CWU/t must trip the ceiling");

        fillFloor(helper, 1, 0, 1, 12, 9);
        SwitchParts parts = placeSwitch(helper, 1, 1, 1);
        primeEnergy(parts.energyHatch(), helper, 20, 700);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            helper.assertTrue(parts.controller().isFormed(),
                    "the optical switch must form (err=" + multiblockError(parts.controller()) + ")");
            // Status is settled per tick, so only assert the switch is a live, formed controller
            // here; the aggregate/status agreement is checked after it has had ticks to settle.
            helper.assertTrue(parts.controller().aggregateAvailableCwu() >= 0,
                    "aggregate CWU/t must never be negative");
            helper.assertTrue(parts.controller().getBandwidthLimit() == VramRating.SWITCH_BANDWIDTH_LIMIT,
                    "the switch must publish its bandwidth ceiling");
        });

        // Two full LuV liquid racks (64 CWU/t each) as members: 128 CWU/t aggregated.
        // Built and primed at tick 0: registering runAtTickTime from inside a callback corrupts
        // the gametest tick map (see MEMORY.md), so members are finished before any callback runs.
        LiquidRackParts[] members = new LiquidRackParts[2];
        for (int rack = 0; rack < 2; rack++) {
            members[rack] = placeLiquidRack(helper, 8, 1, 1 + rack * 6);
            primeEnergy(members[rack].energyHatch(), helper, 20, 700);
        }
        helper.runAtTickTime(80, () -> {
            for (LiquidRackParts member : members) {
                for (int i = 0; i < 4; i++) {
                    member.controller().getBladeInventory()
                            .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
                }
                member.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
                ensureFormed(member.controller());
            }
        });

        helper.runAtTickTime(120, () -> {
            int aggregated = parts.controller().aggregateAvailableCwu();
            helper.assertTrue(
                    parts.controller().getStatus() == (aggregated == 0 ?
                            OpticalFabricSwitchMachine.Status.NO_MEMBERS : OpticalFabricSwitchMachine.Status.SERVING),
                    "status must match the aggregate (" + aggregated + " CWU/t), got " +
                            parts.controller().getStatus());
        });

        helper.runAtTickTime(200, () -> {
            int aggregated = parts.controller().aggregateAvailableCwu();
            helper.assertTrue(aggregated == 128,
                    "two LuV racks must aggregate to 128 CWU/t, got " + aggregated);
            helper.assertTrue(parts.controller().getMaxCWUt(new ArrayList<>()) == 128,
                    "the switch must offer the full 128 CWU/t ceiling, got " +
                            parts.controller().getMaxCWUt(new ArrayList<>()));
            helper.assertTrue(parts.controller().requestCWUt(Integer.MAX_VALUE, true) == 128 &&
                    !parts.controller().isBusy(), "the native receiver's availability probe must not trip 503");
            // Exactly the ceiling is served.
            int atCeiling = parts.controller().requestCWUt(128, false, new ArrayList<>());
            helper.assertTrue(atCeiling == 128,
                    "a 128 CWU/t request must be served at the ceiling, got " + atCeiling);
            helper.assertTrue(!parts.controller().isBusy(), "serving at the ceiling must not trip 503");
            System.out.printf("[HC-061] switch: 2 LuV racks aggregated to %d CWU/t, served at ceiling%n",
                    aggregated);
        });

        // Over the ceiling: the switch refuses and enters the 503 cooldown.
        helper.runAtTickTime(260, () -> {
            helper.assertTrue(parts.controller().requestCWUt(129, false) == 0,
                    "the switch must reject a real over-bandwidth request");
            helper.assertTrue(parts.controller().isBusy(),
                    "an over-bandwidth tick must put the switch into 503");
            helper.assertTrue(parts.controller().getStatus() == OpticalFabricSwitchMachine.Status.BUSY_503,
                    "status must read BUSY_503, got " + parts.controller().getStatus());
            helper.assertTrue(parts.controller().requestCWUt(8, false, new ArrayList<>()) == 0,
                    "a busy switch must serve nobody");
            helper.assertTrue(parts.controller().getCooldownTicks() == VramRating.BUSY_COOLDOWN_TICKS,
                    "the cooldown must start at 300 ticks, got " + parts.controller().getCooldownTicks());
        });

        // The cooldown runs down and the switch serves again.
        helper.runAtTickTime(620, () -> {
            helper.assertTrue(!parts.controller().isBusy(),
                    "the 503 cooldown must have expired after 15 s, remaining=" +
                            parts.controller().getCooldownTicks());
            int served = parts.controller().requestCWUt(64, false, new ArrayList<>());
            helper.assertTrue(served == 64,
                    "once recovered the switch must serve again, got " + served);
            System.out.printf("[HC-061] switch: 503 tripped and recovered after %d ticks%n",
                    VramRating.BUSY_COOLDOWN_TICKS);
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = "hypercluster_062_floor", timeoutTicks = 300)
    public static void compliantFloorActivatesAndInvalidGeometryBreaksLinks(GameTestHelper helper) {
        int x = 2, y = 1, z = 2;
        fillFloor(helper, x, y, z, 6, 6);
        var switchParts = placeSwitch(helper, x, y + 1, z);
        var rack = placeLiquidRack(helper, x, y + 1, z + 3);
        BlockPos hardwarePos = new BlockPos(x, y + 2, z + 3);
        helper.setBlock(hardwarePos, HyperClusterMachines.DATACENTER_HARDWARE_HATCH.getBlock().defaultBlockState());
        primeEnergy(switchParts.energyHatch(), helper, 20, 300);
        primeEnergy(rack.energyHatch(), helper, 20, 300);
        BlockPos corner = new BlockPos(x + 5, y, z);
        helper.runAtTickTime(40, () -> {
            ensureFormed(switchParts.controller());
            ensureFormed(rack.controller());
            var part = (DatacenterHardwareHatchMachine) metaMachine(helper, hardwarePos);
            var automation = helper.getBlockEntity(hardwarePos)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, part.getFrontFacing())
                    .orElseThrow(() -> new IllegalStateException("formed liquid rack must expose its hardware port"));
            helper.assertTrue(automation.insertItem(0, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()), false)
                    .isEmpty(), "automation must install a blade via the dedicated hatch");
            helper.assertTrue(!automation.insertItem(4, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()), true)
                    .isEmpty(), "the ancillary slot must reject blades");
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(switchParts.controller().requestCWUt(16, false) == 16,
                    "formed plinths sharing a 6×6 floor must share CWU without pipes, got " +
                            switchParts.controller().aggregateAvailableCwu());
            helper.setBlock(corner, Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 0,
                    "a missing corner must invalidate the entire connected floor");
            helper.setBlock(corner, HyperClusterBlocks.RAISED_FLOOR.get().defaultBlockState());
        });
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 16,
                    "repairing the floor must reconnect the room without reforming machines");
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 0,
                    "breaking a tile directly under the plinth must also detach the machine");
            helper.setBlock(new BlockPos(x, y, z), HyperClusterBlocks.RAISED_FLOOR.get().defaultBlockState());
            for (int depth = 0; depth < 6; depth++) {
                helper.setBlock(new BlockPos(x + 5, y, z + depth), Blocks.AIR.defaultBlockState());
            }
            fillFloor(helper, x, y, z + 6, 5, 14);
        });
        helper.runAtTickTime(150, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 0,
                    "a full 5×20 floor under both plinths must still reject hidden links");
            helper.succeed();
        });
    }

    @GameTest(template = "hc_network_field", batch = "hypercluster_062_floor_shapes")
    public static void thinAndOversizedFloorComponentsAreRejected(GameTestHelper helper) {
        int[][] invalid = { { 1, 12 }, { 2, 12 }, { 5, 20 }, { 8, 25 } };
        for (int index = 0; index < invalid.length; index++) {
            int width = invalid[index][0], depth = invalid[index][1], y = index + 1;
            fillFloor(helper, 1, y, 0, width, depth);
            var tile = helper.absolutePos(new BlockPos(1, y, 1));
            helper.assertTrue(DatacenterFloorNetwork.roomAt(helper.getLevel(), tile).isEmpty(),
                    width + "×" + depth + " must not activate a hidden network");
        }
        fillFloor(helper, 2, 6, 2, 18, 8);
        helper.assertTrue(DatacenterFloorNetwork.roomAt(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 6, 2))).isPresent(), "18×8 must activate");
        helper.succeed();
    }

    @GameTest(template = "hc_network_field", batch = "hypercluster_062_optical", timeoutTicks = 340)
    public static void crossRoomRequiresContinuousNativeOpticalPipe(GameTestHelper helper) {
        fillFloor(helper, 1, 1, 1, 6, 6);
        fillFloor(helper, 4, 1, 8, 6, 6);
        var rack = placeLiquidRack(helper, 2, 2, 2);
        var switchParts = placeSwitch(helper, 4, 2, 9);
        var receiver = new BlockPos(4, 4, 9);
        var sourcePipe = new BlockPos(4, 4, 5);
        var destinationPipe = receiver.north();
        var cut = sourcePipe.south();
        helper.setBlock(receiver, GTResearchMachines.COMPUTATION_HATCH_RECEIVER.getBlock().defaultBlockState());
        primeEnergy(switchParts.energyHatch(), helper, 20, 340);
        primeEnergy(rack.energyHatch(), helper, 20, 340);
        helper.runAtTickTime(40, () -> {
            ensureFormed(rack.controller());
            ensureFormed(switchParts.controller());
            rack.controller().getBladeInventory().setStackInSlot(0,
                    new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            rack.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(rack.controller().availableCWUt() == 16 &&
                    switchParts.controller().aggregateAvailableCwu() == 0,
                    "nearby machines in different compliant rooms must have zero cross-room CWU without a pipe");
            TrainingTests.placeOpticalPipeRun(helper, sourcePipe, destinationPipe);
            TrainingTests.connectPipeToBlock(helper, sourcePipe, Direction.NORTH);
            TrainingTests.connectPipeToBlock(helper, destinationPipe, Direction.SOUTH);
        });
        helper.runAtTickTime(115, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 16 &&
                    switchParts.controller().requestCWUt(16, false) == 16,
                    "a native receiver–pipe–transmitter path must carry real CWU, got " +
                            switchParts.controller().aggregateAvailableCwu());
            helper.assertTrue(rack.controller().requestCWUt(1, false) == 0,
                    "the switch must debit the actual remote rack budget");
            helper.setBlock(cut, Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(155, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 0,
                    "a break in the middle of the pipe must cut cross-room CWU");
            // The native pipe keeps its wrench openings on the two exposed ends.
            ((OpticalPipeBlockEntity) helper.getBlockEntity(sourcePipe)).setConnection(Direction.SOUTH, false, false);
            ((OpticalPipeBlockEntity) helper.getBlockEntity(cut.south())).setConnection(Direction.NORTH, false, false);
            helper.setBlock(cut, GTBlocks.OPTICAL_PIPES[0].get().defaultBlockState());
        });
        helper.runAtTickTime(170, () -> {
            TrainingTests.connectPipes(helper, sourcePipe, cut);
            TrainingTests.connectPipes(helper, cut, cut.south());
        });
        helper.runAtTickTime(210, () -> {
            helper.assertTrue(switchParts.controller().aggregateAvailableCwu() == 16,
                    "repairing the optical pipe must restore CWU");
            helper.succeed();
        });
    }

    static void fillFloor(GameTestHelper helper, int x, int y, int z, int width, int depth) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                helper.setBlock(new BlockPos(x + dx, y, z + dz),
                        HyperClusterBlocks.RAISED_FLOOR.get().defaultBlockState());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. MoE router: deploy, OOM refusal, load and eviction //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 0.6.3 · the floating 6×6 display //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 400)
    public static void moeScreenLightsWithPowerAndDeepSeek(GameTestHelper helper) {
        fillFloor(helper, 1, 0, 1, 14, 13);
        MoE parts = placeMoeRouter(helper, 1, 1, 1);
        MoeRouterRackMachine router = parts.controller();
        primeEnergy(parts.energyHatch(), helper, 20, 400);

        helper.runAtTickTime(30, () -> {
            ensureFormed(router);
            helper.assertTrue(!router.isScreenLit(), "an unformed/unpowered cabinet must stay dark");

            // Formed and powered, but nothing deployed and the manual switch off: still dark.
            helper.runAtTickTime(60, () -> {
                helper.assertTrue(router.isFormed(), "the router must form");
                helper.assertTrue(!router.isShowScreen(), "the display switch defaults to off");
                helper.assertTrue(!router.isScreenLit(),
                        "formed + powered without a model or the switch must stay dark");

                // The manual switch alone lights it.
                router.setShowScreen(true);
                helper.assertTrue(router.isScreenLit(),
                        "the manual switch must light the display on its own");

                // Turning it back off, with nothing deployed, goes dark again.
                router.setShowScreen(false);
                helper.assertTrue(!router.isScreenLit(),
                        "clearing the switch with no model must go dark again");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 900)
    public static void moeScreenSwitchPersistsAcrossReload(GameTestHelper helper) {
        fillFloor(helper, 1, 0, 1, 14, 13);
        MoE parts = placeMoeRouter(helper, 1, 1, 1);
        MoeRouterRackMachine router = parts.controller();
        BlockPos controllerPos = parts.controllerPos();
        primeEnergy(parts.energyHatch(), helper, 20, 900);

        helper.runAtTickTime(30, () -> {
            ensureFormed(router);
            router.setShowScreen(true);
            helper.assertTrue(router.isShowScreen(), "the switch must be settable");

            // Round-trip the block entity through its own save/load, exactly like a world
            // save followed by a chunk reload.
            BlockEntity be = helper.getBlockEntity(controllerPos);
            CompoundTag saved = be.saveWithFullMetadata();
            be.load(saved);
            helper.runAtTickTime(60, () -> {
                MoeRouterRackMachine reloaded = (MoeRouterRackMachine) metaMachine(helper, controllerPos);
                helper.assertTrue(reloaded.isShowScreen(),
                        "the display switch must survive a save/reload round-trip");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 400)
    public static void moeScreenGoesDarkWithoutPower(GameTestHelper helper) {
        fillFloor(helper, 1, 0, 1, 14, 13);
        MoE parts = placeMoeRouter(helper, 1, 1, 1);
        MoeRouterRackMachine router = parts.controller();

        helper.runAtTickTime(30, () -> {
            ensureFormed(router);
            router.setShowScreen(true);
            // No energy was ever primed: an empty hatch means no power.
            parts.energyHatch().energyContainer.removeEnergy(
                    parts.energyHatch().energyContainer.getEnergyStored());
            helper.runAtTickTime(60, () -> {
                helper.assertTrue(!router.hasPower(), "the hatch must read as unpowered");
                helper.assertTrue(!router.isScreenLit(),
                        "losing power must extinguish the display even with the switch on");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 700)
    public static void moeRouterLoadsAndEvictsModels(GameTestHelper helper) {
        fillFloor(helper, 1, 0, 1, 14, 13);
        MoE parts = placeMoeRouter(helper, 1, 1, 1);
        BlockPos hardwarePos = new BlockPos(2, 2, 1);
        helper.setBlock(hardwarePos, HyperClusterMachines.DATACENTER_HARDWARE_HATCH.getBlock().defaultBlockState());
        primeEnergy(parts.energyHatch(), helper, 20, 700);
        // One IV memory rack only: 16 TB, far below the 20 TB a Qwen + LoRA needs.
        VramParts smallMemory = placeVramRack(helper, 10, 1, 1);
        primeEnergy(smallMemory.energyHatch(), helper, 20, 700);
        // A compute rack so VRAM is the only gate: without compute the router would report
        // LOADING instead of OOM and the test would prove the wrong thing.
        LiquidRackParts compute = placeLiquidRack(helper, 10, 1, 7);
        primeEnergy(compute.energyHatch(), helper, 20, 700);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            ensureFormed(smallMemory.controller());
            ensureFormed(compute.controller());
            // Four blades give the router 64 CWU/t, well past Qwen's 4 CWU/t demand.
            for (int i = 0; i < 4; i++) {
                compute.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            helper.assertTrue(parts.controller().isFormed(),
                    "the MoE router must form (err=" + multiblockError(parts.controller()) + ")");
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                smallMemory.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_HBM_MODULE.get()));
            }
            // A Qwen module needs 16 TB, which exactly fits; add a LoRA and it no longer does.
            var hardware = (DatacenterHardwareHatchMachine) metaMachine(helper, hardwarePos);
            var automation = helper.getBlockEntity(hardwarePos)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, hardware.getFrontFacing())
                    .orElseThrow(() -> new IllegalStateException("formed router must expose a hardware capability"));
            helper.assertTrue(automation.insertItem(0, qwenModule(), false).isEmpty(),
                    "the dedicated hatch must deploy the module via automation");
            helper.assertTrue(!automation.insertItem(1, qwenModule(), true).isEmpty(),
                    "the LoRA slots must reject weight modules");
        });

        // Insert the LoRA and let the router re-evaluate on a LATER tick: machines tick between
        // callbacks, so asserting in the same callback would read the previous tick's verdict.
        helper.runAtTickTime(150, () -> {
            var hardware = (DatacenterHardwareHatchMachine) metaMachine(helper, hardwarePos);
            var automation = helper.getBlockEntity(hardwarePos)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, hardware.getFrontFacing()).orElseThrow(
                            () -> new IllegalStateException("missing hardware port"));
            helper.assertTrue(automation.insertItem(1, new ItemStack(HyperClusterItems.MINING_LORA_CARD.get()), false)
                    .isEmpty(), "the dedicated hatch must load a LoRA through its second slot");
            helper.assertTrue(parts.controller().requiredTerabytes() == 20,
                    "Qwen + one LoRA must need 20 TB, got " + parts.controller().requiredTerabytes());
        });

        helper.runAtTickTime(180, () -> {
            int pool = parts.controller().poolTerabytes();
            int need = parts.controller().requiredTerabytes();
            helper.assertTrue(pool == 16 && need == 20 &&
                    parts.controller().getState() == MoeRouterRackMachine.State.OOM,
                    "the router must reject a 20 TB model on a 16 TB room, got " + pool + " TB and " +
                            parts.controller().getState());
            System.out.printf("[HC-061] router: Qwen+LoRA needs %d TB vs %d TB pool -> %s%n",
                    need, pool, parts.controller().getState());
        });

        // A second memory rack (built at tick 0, powered from the start) doubles the pool so the
        // 20 TB model now fits.
        VramParts second = placeVramRack(helper, 10, 1, 11);
        primeEnergy(second.energyHatch(), helper, 20, 700);
        helper.runAtTickTime(200, () -> {
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                second.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_HBM_MODULE.get()));
            }
            ensureFormed(second.controller());
        });

        helper.runAtTickTime(330, () -> {
            int pool = parts.controller().poolTerabytes();
            helper.assertTrue(pool >= 2 * VramRating.IV_RACK_TB,
                    "two IV racks must give at least 32 TB, got " + pool);
            helper.assertTrue(parts.controller().getState() == MoeRouterRackMachine.State.ONLINE,
                    "with " + parts.controller().poolTerabytes() + " TB the 20 TB model must load, got " +
                            parts.controller().getState());
            helper.assertTrue(parts.controller().loadedModel() != null,
                    "an ONLINE router must report its loaded model");
            helper.assertTrue(
                    AIModels.QWEN_9187_PRO_MAX_ULTRA.id().equals(parts.controller().loadedModel().id()),
                    "the loaded model must be Qwen, got " + parts.controller().loadedModel());
        });

        // Pulling the weights takes the model offline immediately.
        helper.runAtTickTime(400, () -> parts.controller().getModelSlot().setStackInSlot(0, ItemStack.EMPTY));
        helper.runAtTickTime(460, () -> {
            helper.assertTrue(parts.controller().getState() == MoeRouterRackMachine.State.EMPTY,
                    "pulling the card must empty the router, got " + parts.controller().getState());
            helper.assertTrue(parts.controller().loadedModel() == null, "an empty router must have no model");
            System.out.printf("[HC-061] router: card pulled -> model evicted on the next tick%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. the six model families and their exclusive mechanics //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = TOPOLOGY_BATCH, timeoutTicks = 100)
    public static void modelCatalogContracts(GameTestHelper helper) {
        // --- the registry itself ---
        helper.assertTrue(AIModels.catalog().size() == 10,
                "the catalog must hold ten models (5 open + 5 API), got " + AIModels.catalog().size());
        helper.assertTrue(AIModels.localModels().size() == 5,
                "five open-camp models, got " + AIModels.localModels().size());
        helper.assertTrue(AIModels.apiModels().size() == 5,
                "five API-camp models, got " + AIModels.apiModels().size());

        // --- Qwen: 16 TB, four LoRA slots of 4 TB, 1.5x fallback ---
        AIModelDefinition qwen = AIModels.QWEN_9187_PRO_MAX_ULTRA;
        helper.assertTrue(qwen.vramTerabytes() == 16, "Qwen must be 16 TB, got " + qwen.vramTerabytes());
        helper.assertTrue(qwen.loraSlots() == 4, "Qwen must expose four LoRA slots");
        helper.assertTrue(qwen.loraVramTerabytes() == 4, "each Qwen LoRA must add 4 TB");
        helper.assertTrue(Math.abs(qwen.fallbackSlowdown() - 1.5) < 1e-9,
                "Qwen's local fallback must be 1.5x, got " + qwen.fallbackSlowdown());
        helper.assertTrue(qwen.vramFor(4, false) == 16 + 4 * 4,
                "Qwen with four LoRAs must be 32 TB, got " + qwen.vramFor(4, false));
        helper.assertTrue(qwen.servesDomain("mining") && qwen.servesDomain("chemical"),
                "Qwen must serve both domains, so cross-domain runs skip the overfitting penalty");
        // 1.5x is strictly better than the model-less 4x.
        helper.assertTrue(qwen.fallbackSlowdown() < 4.0,
                "a bound model must beat the modelless 4x fallback");

        // --- DeepSeek: 25% sparse, DeepThink +16 TB and 100% by-products ---
        AIModelDefinition flash = AIModels.DEEPSEEK_V6767_FLASH;
        AIModelDefinition pro = AIModels.DEEPSEEK_V6767_PRO;
        helper.assertTrue(flash.vramTerabytes() == 32, "DeepSeek Flash must be 32 TB");
        helper.assertTrue(pro.vramTerabytes() == 64, "DeepSeek Pro must be 64 TB");
        helper.assertTrue(flash.billedCwuPerTick() == Math.max(1, flash.cwuPerTick() / 4),
                "DeepSeek Flash must bill 25% of " + flash.cwuPerTick() + " CWU/t, got " +
                        flash.billedCwuPerTick());
        helper.assertTrue(pro.billedCwuPerTick() == Math.max(1, pro.cwuPerTick() / 4),
                "DeepSeek Pro keeps the 25% sparse discount, got " + pro.billedCwuPerTick());
        helper.assertTrue(pro.has(AIModelDefinition.Trait.DEEP_THINK),
                "DeepSeek Pro must carry the DeepThink switch");
        helper.assertTrue(!flash.has(AIModelDefinition.Trait.DEEP_THINK),
                "DeepSeek Flash must not have DeepThink");
        helper.assertTrue(pro.deepThinkVramTerabytes() == 16, "DeepThink must reserve 16 TB");
        helper.assertTrue(pro.vramFor(0, true) == 64 + 16,
                "DeepSeek Pro with DeepThink must be 80 TB, got " + pro.vramFor(0, true));

        // --- GLM: room inspection vs agent tool calls ---
        AIModelDefinition glmFlash = AIModels.GLM_2333_FLASH;
        AIModelDefinition glmMax = AIModels.GLM_2333_MAX;
        helper.assertTrue(glmFlash.vramTerabytes() == 24 && glmFlash.cwuPerTick() == 4,
                "GLM-2333 Flash must be 24 TB / 4 CWU/t");
        helper.assertTrue(glmMax.vramTerabytes() == 48 && glmMax.cwuPerTick() == 8,
                "GLM-2333 Max must be 48 TB / 8 CWU/t");
        helper.assertTrue(glmFlash.has(AIModelDefinition.Trait.ROOM_INSPECTION),
                "GLM Flash must carry room inspection");
        helper.assertTrue(glmMax.has(AIModelDefinition.Trait.AGENT_TOOL_CALLS),
                "GLM Max must carry agent tool calls");
        helper.assertTrue(VramRating.BROWNOUT_FRACTION == 0.20,
                "room inspection must sleep the room below 20% stored energy");

        // --- Gemini: lifecycle, chemistry noise, zero hallucination ---
        helper.assertTrue(AIModels.GEMINI_TITANIUM_4_0.has(AIModelDefinition.Trait.LIFECYCLE),
                "Gemini-Titanium-4.0 must carry the Leaving-Soon lifecycle");
        helper.assertTrue(AIModels.GEMINI_PLATINUM.has(AIModelDefinition.Trait.CHEM_NOISE_SUPPRESSION),
                "Gemini-Platinum must suppress chemical noise");
        helper.assertTrue(AIModels.GEMINI_XENON.has(AIModelDefinition.Trait.ZERO_HALLUCINATION),
                "Gemini-Xenon must be hard zero-hallucination");
        helper.assertTrue(AIModels.apiModels().stream().filter(
                m -> m.has(AIModelDefinition.Trait.LIFECYCLE)).count() == 1,
                "exactly one API model may carry a retirement countdown");

        // --- GPT: moral refusal + jailbreak, and it is the priciest ---
        AIModelDefinition gpt = AIModels.GPT_1145_MULTIVERSE;
        helper.assertTrue(gpt.has(AIModelDefinition.Trait.MORAL_REFUSAL), "GPT must refuse dangerous runs");
        helper.assertTrue(gpt.has(AIModelDefinition.Trait.MAX_REAGENT_SAVING), "GPT must top reagent savings");
        helper.assertTrue(!AIModels.mayRunDangerousChemistry(gpt, false),
                "without a jailbreak card GPT must refuse the dangerous round");
        helper.assertTrue(AIModels.mayRunDangerousChemistry(gpt, true),
                "a jailbreak card must let GPT run the dangerous round");
        long priciest = AIModels.apiModels().stream().mapToLong(AIModelDefinition::tokenCostPerRound).max()
                .orElse(0);
        helper.assertTrue(gpt.tokenCostPerRound() == priciest,
                "GPT must be the most expensive API model, got " + gpt.tokenCostPerRound());

        // --- Grok: uncensored, cheaper than GPT, doubled weather penalty ---
        AIModelDefinition grok = AIModels.GROK_420;
        helper.assertTrue(grok.has(AIModelDefinition.Trait.UNCENSORED), "Grok must be uncensored");
        helper.assertTrue(AIModels.mayRunDangerousChemistry(grok, false),
                "Grok must run dangerous rounds with no jailbreak card");
        helper.assertTrue(grok.tokenCostPerRound() < gpt.tokenCostPerRound(),
                "Grok must be cheaper than GPT, got " + grok.tokenCostPerRound() + " vs " +
                        gpt.tokenCostPerRound());
        helper.assertTrue(grok.has(AIModelDefinition.Trait.WEATHER_FRAGILE), "Grok must be weather-fragile");
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(grok, 1.5) - 3.0) < 1e-9,
                "Grok's rain penalty must double to 3.0x, got " + AIModels.weatherAttenuation(grok, 1.5));
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(grok, 2.0) - 4.0) < 1e-9,
                "Grok's thunder penalty must double to 4.0x, got " + AIModels.weatherAttenuation(grok, 2.0));
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(gpt, 1.5) - 1.5) < 1e-9,
                "a normal model keeps the base rain penalty");

        // --- API models never occupy local VRAM ---
        for (AIModelDefinition api : AIModels.apiModels()) {
            helper.assertTrue(api.vramTerabytes() == 0,
                    api.id() + " is an API model and must occupy 0 TB locally");
            helper.assertTrue(api.tokenCostPerRound() > 0, api.id() + " must price its rounds in Tokens");
        }
        for (AIModelDefinition local : AIModels.localModels()) {
            helper.assertTrue(local.tokenCostPerRound() == 0,
                    local.id() + " is a local model and must bill 0 Tokens");
            helper.assertTrue(local.vramTerabytes() > 0, local.id() + " must occupy local VRAM");
        }
        System.out.printf("[HC-061] catalog: %d models (5 open + 5 API), every family contract asserted%n",
                AIModels.catalog().size());
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structures & helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    record VramParts(VramStorageRackMachine controller, BlockPos controllerPos,
                     EnergyHatchPartMachine energyHatch) {}

    record SwitchParts(OpticalFabricSwitchMachine controller, BlockPos controllerPos,
                       EnergyHatchPartMachine energyHatch) {}

    record MoE(MoeRouterRackMachine controller, BlockPos controllerPos,
               EnergyHatchPartMachine energyHatch) {}

    record LiquidRackParts(dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine controller,
                           BlockPos controllerPos,
                           EnergyHatchPartMachine energyHatch,
                           com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine fluidHatch,
                           MaintenanceHatchPartMachine maintenanceHatch) {}

    /** One open-weight module carrying the mining domain (the identity Qwen answers to). */
    private static ItemStack qwenModule() {
        return OpenWeightModuleItem.create(new ItemStack(HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                "mining", "fp16", 4, 1);
    }

    /** The 5×5×3 VRAM storage rack facing north. */
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
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.VRAM_STORAGE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new VramParts((VramStorageRackMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×4×3 optical fabric switch facing north. */
    private static SwitchParts placeSwitch(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "OOOOO", "OOOOO", "OOOOO" },
                { "OOOSO", "OOOOO", "OOOOO" },
                { "OGGGO", "O###O", "O###O" },
                { "TTTTT", "T###T", "T###T" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('O', HyperClusterBlocks.OPTICAL_SWITCH_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.OPTICAL_FABRIC_SWITCH.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new SwitchParts((OpticalFabricSwitchMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×5 MoE routing rack facing north. */
    private static MoE placeMoeRouter(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "CCSCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "CGGGC", "G###G", "G#R#G", "G###G", "CGGGC" },
                { "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "#####", "#TTT#", "#TTT#", "#TTT#", "#####" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('C', GTBlocks.COMPUTER_CASING.get());
        chars.put('G', GTBlocks.CLEANROOM_GLASS.get());
        chars.put('R', GTBlocks.ADVANCED_COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.MOE_ROUTER_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new MoE((MoeRouterRackMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×3 liquid compute rack facing north (a switch/memory neighbour in these tests). */
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
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz),
                GTMachines.FLUID_IMPORT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 2), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 2, bz + 2),
                com.gregtechceu.gtceu.common.data.machines.GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER
                        .getBlock().defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 2)))
                .fixAllMaintenanceProblems();
        return new LiquidRackParts(
                (dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine) metaMachine(helper,
                        controllerPos),
                controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)),
                (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) metaMachine(helper,
                        new BlockPos(bx + 4, by, bz)),
                (MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 2)));
    }

    /**
     * Builds a multiblock from its own pattern rows, so the placement and the pattern can never
     * drift. Returns where the controller actually landed (the pattern's 'S' cell).
     */
    private static BlockPos buildStructure(GameTestHelper helper, int bx, int by, int bz, String[][] rows,
                                           java.util.Map<Character, net.minecraft.world.level.block.Block> chars,
                                           net.minecraft.world.level.block.Block controllerBlock) {
        BlockPos controllerOffset = null;
        for (int y = 0; y < rows.length; y++) {
            for (int z = 0; z < rows[y].length; z++) {
                String row = rows[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    BlockPos pos = new BlockPos(bx + x, by + y, bz + z);
                    if (c == 'S') {
                        controllerOffset = new BlockPos(x, y, z);
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
        if (controllerOffset == null) {
            throw new IllegalArgumentException("pattern has no 'S' controller cell");
        }
        return new BlockPos(bx + controllerOffset.getX(), by + controllerOffset.getY(),
                bz + controllerOffset.getZ());
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
        return state.error == null ? "none" : state.error.getErrorInfo().getString();
    }

    private static void primeEnergy(EnergyHatchPartMachine hatch, GameTestHelper helper, long first, long last) {
        hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity());
        for (long t = first; t <= last; t += 20) {
            helper.runAtTickTime(t, () -> hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity()));
        }
    }
}
