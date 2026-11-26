package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.TelemetryOreWasherMachine;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * P03/P04 acceptance gametests: HV micro burner, own HV telemetry ore washer, precise settlement
 * (buffer counts instead of afterWorking counting) and persistence of the telemetry buffer.
 *
 * Machine placement, feeding and the EU "grid" work exactly like the P02 probe tests, so the two
 * suites share the helper methods; the asserted quantity is the washer's telemetry buffer (the
 * deposited logging chain output), not a wrapped afterWorking callback.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class TelemetryMachineTests {

    public static final String P03_P04_BATCH = "hypercluster_p03_p04";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S1. ten sequential settled batches -> exactly ten logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 2000)
    public static void tenSettledBatchesTenLogs(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        feedWasher(washer, 10, 1_000, () -> true, helper, 1, 20, 1700);

        helper.runAtTickTime(1800, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 10,
                    "expected 10 deposited logs, got " + washer.getTelemetryBufferCount());
            int purified = countItem(washer, purifiedCopperOre());
            int stoneDust = countItem(washer, stoneDust());
            helper.assertTrue(purified == 10 && stoneDust == 10,
                    "expected 10 purified + 10 dust, got " + purified + "/" + stoneDust);
            helper.assertTrue(washer.recipeLogic.getStatus() == RecipeLogic.Status.IDLE,
                    "machine should be idle, status=" + washer.recipeLogic.getStatus());
            helper.assertTrue(washer.importItems.getStackInSlot(0).isEmpty(), "input should be consumed");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S2. output jam -> zero logs (products dropped, machine stalls) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 800)
    public static void jammedOutputZeroLogs(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        feedWasher(washer, 2, 1_000, () -> true, helper, 1, 20, 700);
        helper.runAtTickTime(40, () -> {
            for (int i = 0; i < washer.exportItems.getSlots(); i++) {
                washer.exportItems.setStackInSlot(i,
                        new ItemStack(net.minecraft.world.level.block.Blocks.COBBLESTONE, 64));
            }
        });
        helper.runAtTickTime(700, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 0,
                    "jammed batches must deposit no logs, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(countItem(washer, purifiedCopperOre()) == 0, "no products may settle");
            helper.assertTrue(countItem(washer, stoneDust()) == 0, "no dust may settle");
            helper.assertTrue(washer.importItems.getStackInSlot(0).getCount() == 1,
                    "one batch input should remain, got " + washer.importItems.getStackInSlot(0));
            System.out.printf("[HC-P04] jam: buffer=%d status=%s leftoverInput=%d%n", washer.getTelemetryBufferCount(),
                    washer.recipeLogic.getStatus(), washer.importItems.getStackInSlot(0).getCount());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S3. mid-run interrupt -> zero logs //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 600)
    public static void interruptedBatchZeroLogs(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        feedWasher(washer, 1, 1_000, () -> true, helper, 1, 20, 500);

        helper.runAtTickTime(50, () -> {
            helper.assertTrue(washer.recipeLogic.isWorking(), "machine should be working before interrupt");
            washer.recipeLogic.interruptRecipe();
            System.out.printf("[HC-P04] interrupt at progress=%d/%d buffer=%d status=%s%n",
                    washer.recipeLogic.getProgress(), washer.recipeLogic.getDuration(),
                    washer.getTelemetryBufferCount(), washer.recipeLogic.getStatus());
        });

        helper.runAtTickTime(500, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 0,
                    "interrupted batch must deposit no logs, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(countItem(washer, purifiedCopperOre()) == 0, "no products expected");
            helper.assertTrue(countItem(washer, stoneDust()) == 0, "no dust expected");
            helper.assertTrue(!washer.recipeLogic.isWorking(), "machine should be idle after interrupt");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S4. power loss then recovery -> exactly one log //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 1600)
    public static void powerLossOneLog(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        AtomicBoolean powerOn = new AtomicBoolean(true);
        feedWasher(washer, 1, 1_000, powerOn::get, helper, 1, 20, 1500);
        helper.runAtTickTime(40, () -> {
            powerOn.set(false);
            washer.energyContainer.removeEnergy(washer.energyContainer.getEnergyStored());
        });
        helper.runAtTickTime(90, () -> helper.assertTrue(washer.recipeLogic.isWaiting(),
                "machine should be WAITING during the outage, status=" + washer.recipeLogic.getStatus()));
        helper.runAtTickTime(320, () -> powerOn.set(true));

        helper.runAtTickTime(1500, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 1,
                    "recovered batch must deposit exactly 1 log, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(countItem(washer, purifiedCopperOre()) == 1, "expected 1 purified output");
            helper.assertTrue(countItem(washer, stoneDust()) == 1, "expected 1 stone dust");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S5. two washers finishing on the same tick -> one log each, no cross-talk //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 600)
    public static void twoWashersSameTick(GameTestHelper helper) {
        TelemetryOreWasherMachine first = placeWasher(helper, new BlockPos(2, 1, 2));
        TelemetryOreWasherMachine second = placeWasher(helper, new BlockPos(7, 1, 2));
        feedWasher(first, 1, 1_000, () -> true, helper, 1, 20, 520);
        feedWasher(second, 1, 1_000, () -> true, helper, 1, 20, 520);

        // Sample every tick around the expected finish to prove both settled on the SAME tick.
        java.util.TreeMap<Long, int[]> samples = new java.util.TreeMap<>();
        for (long t = 60; t <= 200; t++) {
            helper.runAtTickTime(t, () -> samples.put(first.self().getLevel().getGameTime(),
                    new int[] { first.getTelemetryBufferCount(), second.getTelemetryBufferCount() }));
        }

        helper.runAtTickTime(540, () -> {
            for (TelemetryOreWasherMachine washer : List.of(first, second)) {
                helper.assertTrue(washer.getTelemetryBufferCount() == 1,
                        "each washer must hold exactly 1 log, buffer=" + washer.getTelemetryBufferCount() + " pos=" +
                                washer.getPos());
                helper.assertTrue(countItem(washer, purifiedCopperOre()) == 1 && countItem(washer, stoneDust()) == 1,
                        "expected settled products per washer");
            }
            Long commonTick = null;
            for (var entry : samples.entrySet()) {
                if (entry.getValue()[0] == 1 && entry.getValue()[1] == 1) {
                    commonTick = entry.getKey();
                    break;
                }
            }
            helper.assertTrue(commonTick != null,
                    "both washers must reach their deposited log in the same sampled tick");
            if (commonTick != null) {
                for (var counts : samples.headMap(commonTick).values()) {
                    helper.assertTrue(counts[0] == 0 && counts[1] == 0,
                            "no earlier tick may hold deposited logs: " + java.util.Arrays.toString(counts));
                }
            }
            System.out.printf("[HC-P04] same-tick settle proven at gameTime=%d: buffers=%d/%d%n",
                    commonTick, first.getTelemetryBufferCount(), second.getTelemetryBufferCount());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S6. full buffer keeps the machine working (extra logs dropped, nothing overflows) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 800)
    public static void fullBufferKeepsWorking(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        // Pre-fill the buffer to capacity.
        ItemStack warm = new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get());
        warm.setCount(TelemetryOreWasherMachine.TELEMETRY_BUFFER_CAPACITY);
        washer.getTelemetryBuffer().setStackInSlot(0, warm);
        helper.assertTrue(washer.isTelemetryBufferFull(), "buffer should start full");

        feedWasher(washer, 3, 1_000, () -> true, helper, 1, 20, 700);

        helper.runAtTickTime(700, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == TelemetryOreWasherMachine.TELEMETRY_BUFFER_CAPACITY,
                    "buffer must stay at capacity, got " + washer.getTelemetryBufferCount());
            int purified = countItem(washer, purifiedCopperOre());
            helper.assertTrue(purified == 3, "machine kept working, expected 3 purified, got " + purified);
            helper.assertTrue(washer.importItems.getStackInSlot(0).isEmpty(), "all three batches must be consumed");
            System.out.printf("[HC-P04] full buffer: buffer=%d purified=%d (%d logs dropped)%n",
                    washer.getTelemetryBufferCount(), purified,
                    TelemetryOreWasherMachine.TELEMETRY_BUFFER_CAPACITY);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S7. buffer persists across BE save/load and is never duplicated //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 900)
    public static void bufferPersistenceNoDuplication(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        feedWasher(washer, 3, 1_000, () -> true, helper, 1, 20, 700);

        helper.runAtTickTime(400, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 3,
                    "precondition: 3 deposited logs, got " + washer.getTelemetryBufferCount());
            BlockEntity be = Objects.requireNonNull(helper.getBlockEntity(new BlockPos(2, 1, 2)));
            CompoundTag saved = be.saveWithFullMetadata();
            helper.assertTrue(saved.toString().contains("hypercluster:mining_telemetry_log"),
                    "saved NBT must contain the buffered log items: " + saved);

            // Simulate chunk unload / world reload through the vanilla BE load path.
            washer.getTelemetryBuffer().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(washer.getTelemetryBufferCount() == 0, "buffer should be empty after wipe");
            be.load(saved);
            helper.assertTrue(washer.getTelemetryBufferCount() == 3,
                    "buffer must restore to 3 logs, got " + washer.getTelemetryBufferCount());

            // A second load of the same tag must not duplicate anything.
            be.load(saved);
            helper.assertTrue(washer.getTelemetryBufferCount() == 3,
                    "reloading the same save may not grow the buffer, got " + washer.getTelemetryBufferCount());
            System.out.printf("[HC-P04] persistence: buffer after reload=%d (save size=%d bytes)%n",
                    washer.getTelemetryBufferCount(), saved.toString().length());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S8. the micro burner burns logs + carriers into chips //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 700)
    public static void microBurnerProducesChip(GameTestHelper helper) {
        SimpleTieredMachine burner = placeBurner(helper, new BlockPos(2, 1, 2));
        burner.importItems.setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()));
        burner.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.BLANK_CARRIER.get()));
        burner.energyContainer.addEnergy(burner.energyContainer.getEnergyCapacity());
        for (long t = 1; t <= 600; t += 20) {
            final long tick = t;
            helper.runAtTickTime(tick, () -> burner.energyContainer
                    .addEnergy(burner.energyContainer.getEnergyCapacity()));
        }

        helper.runAtTickTime(650, () -> {
            ItemStack produced = burner.exportItems.getStackInSlot(0);
            helper.assertTrue(produced.is(HyperClusterItems.MINING_EDGE_CHIP.get()),
                    "burner must produce a mining edge chip, got " + produced);
            helper.assertTrue(burner.importItems.getStackInSlot(0).isEmpty(), "log must be consumed");
            helper.assertTrue(burner.importItems.getStackInSlot(1).isEmpty(), "carrier must be consumed");
            System.out.printf("[HC-P03] burner: produced=%s status=%s%n", produced,
                    burner.recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S9. the chip wash recipe runs with 100mb water and keeps the chip retrievable //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 700)
    public static void chipWashUsesChipAndLessWater(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        // chip variant: circuit #2 + chip in the second import slot + only 100mB water.
        washer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(2));
        washer.importItems.setStackInSlot(0, crushedCopperOre(1));
        washer.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()));
        washer.importFluids.setFluidInTank(0, GTMaterials.Water.getFluid(100));
        primeEnergy(washer, helper, 1, 20, 600);

        helper.runAtTickTime(650, () -> {
            helper.assertTrue(washer.getTelemetryBufferCount() == 1,
                    "chip wash must settle and deposit 1 log, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(countItem(washer, purifiedCopperOre()) == 1, "chip variant must yield purified ore");
            helper.assertTrue(countItem(washer, stoneDust()) == 1, "chip variant must yield stone dust");
            ItemStack chipAfter = washer.importItems.getStackInSlot(1);
            helper.assertTrue(chipAfter.is(HyperClusterItems.MINING_EDGE_CHIP.get()) && chipAfter.getCount() == 1,
                    "chip must remain retrievable in the input slot, got " + chipAfter);
            helper.assertTrue(washer.importFluids.getFluidInTank(0).isEmpty(),
                    "chip recipe must consume the 100mB of water, remaining=" + washer.importFluids.getFluidInTank(0));
            System.out.printf("[HC-P03] chip wash: log=%d purified=1 dust=1 chipRetained=%s waterLeft=%s%n",
                    washer.getTelemetryBufferCount(), chipAfter, washer.importFluids.getFluidInTank(0));
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // S9b. base recipe (circuit #1) does not collide with the chip recipe //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = P03_P04_BATCH, timeoutTicks = 700)
    public static void baseAndChipRecipesStrictlySeparated(GameTestHelper helper) {
        TelemetryOreWasherMachine washer = placeWasher(helper, new BlockPos(2, 1, 2));
        // Both a chip AND circuit #1 present: the strict selector is the circuit number.
        washer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(1));
        washer.importItems.setStackInSlot(0, crushedCopperOre(1));
        washer.importItems.setStackInSlot(1, new ItemStack(HyperClusterItems.MINING_EDGE_CHIP.get()));
        washer.importFluids.setFluidInTank(0, GTMaterials.Water.getFluid(1100));
        primeEnergy(washer, helper, 1, 20, 600);

        helper.runAtTickTime(650, () -> {
            // A settled base batch is proven by the buffer plus the EXACT water consumption:
            // the base wash draws 1000mB (remain 100 of 1100); a mis-hit chip recipe would
            // only draw 100mB (remain 1000).
            long remainingWater = washer.importFluids.getFluidInTank(0).getAmount();
            helper.assertTrue(washer.getTelemetryBufferCount() == 1,
                    "settled base batch must deposit 1 log, got " + washer.getTelemetryBufferCount());
            helper.assertTrue(remainingWater == 100,
                    "base recipe must consume 1000mB (remain 100), remaining=" + remainingWater);
            helper.assertTrue(countItem(washer, purifiedCopperOre()) == 1, "base products must settle");
            ItemStack chipAfter = washer.importItems.getStackInSlot(1);
            helper.assertTrue(chipAfter.is(HyperClusterItems.MINING_EDGE_CHIP.get()),
                    "chip must not be consumed by the base recipe");
            System.out.printf(
                    "[HC-P03] strict separation: circuit=1 + chip present -> base ran (water left=%d, log=%d, chip=%s)%n",
                    remainingWater, washer.getTelemetryBufferCount(), chipAfter);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static TelemetryOreWasherMachine placeWasher(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.getBlock().defaultBlockState());
        return (TelemetryOreWasherMachine) ((com.gregtechceu.gtceu.api.machine.IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static SimpleTieredMachine placeBurner(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, HyperClusterMachines.HV_MICRO_BURNER.getBlock().defaultBlockState());
        return (SimpleTieredMachine) ((com.gregtechceu.gtceu.api.machine.IMachineBlockEntity) Objects
                .requireNonNull(helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static ItemStack crushedCopperOre(int count) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper);
        stack.setCount(count);
        return stack;
    }

    private static net.minecraft.world.item.Item purifiedCopperOre() {
        return ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper).getItem();
    }

    private static net.minecraft.world.item.Item stoneDust() {
        return ChemicalHelper.get(TagPrefix.dust, GTMaterials.Stone).getItem();
    }

    /** Base wash setup: crushed copper ore x batches + circuit #1 + water + an EU grid. */
    private static void feedWasher(TelemetryOreWasherMachine washer, int batches, int waterMbPerBatch,
                                   BooleanSupplier powerOn, GameTestHelper helper,
                                   long firstRefillTick, long refillEvery, long lastRefillTick) {
        washer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(1));
        washer.importItems.setStackInSlot(0, crushedCopperOre(batches));
        washer.importFluids.setFluidInTank(0, GTMaterials.Water.getFluid(batches * waterMbPerBatch));
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> {
                if (powerOn.getAsBoolean()) {
                    washer.energyContainer.addEnergy(washer.energyContainer.getEnergyCapacity());
                }
            });
        }
        washer.energyContainer.addEnergy(washer.energyContainer.getEnergyCapacity());
    }

    /** EU grid only (inputs are configured by the caller). */
    private static void primeEnergy(TelemetryOreWasherMachine washer, GameTestHelper helper,
                                    long firstRefillTick, long refillEvery, long lastRefillTick) {
        washer.energyContainer.addEnergy(washer.energyContainer.getEnergyCapacity());
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> washer.energyContainer
                    .addEnergy(washer.energyContainer.getEnergyCapacity()));
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
}
