package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.probe.SettlementProbe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * P02 settlement probe, reproducible via a Forge GameTest on GTCEu 7.4.0.
 *
 * <p>
 * Baseline machine: the HV Electric Ore Washer ({@code gtceu:hv_ore_washer}). Baseline recipe:
 * {@code gtceu:ore_washer/wash_<material>_crushed_ore_to_purified_ore}. Recorded scenarios:
 * <ol>
 * <li>ten sequential normal batches,</li>
 * <li>output jam mid-run,</li>
 * <li>manual interrupt mid-run (plus pause-after-finish for comparison),</li>
 * <li>power loss then recovery,</li>
 * <li>two machines finishing on the same tick.</li>
 * </ol>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class SettlementProbeTests {

    public static final String PROBE_BATCH = "hypercluster_probe";

    /** The HV Electric Ore Washer machine definition under test. */
    public static final MachineDefinition WASHER = GTMachines.ORE_WASHER[GTValues.HV];

    /** Expected per-batch values of the benchmark recipe after HV non-perfect overclock. */
    public static final int OC_DURATION = 100;
    public static final long OC_EUT = 256;

    @BeforeBatch(batch = PROBE_BATCH)
    public static void prepareProbe(ServerLevel level) {
        SettlementProbe.install(WASHER);
        SettlementProbe.reset();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P01: registry, stacking, tooltips, i18n //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 100)
    public static void telemetryItemsRegistered(GameTestHelper helper) {
        for (String name : List.of("mining_telemetry_log", "blank_carrier", "mining_edge_chip")) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(HyperCluster.MOD_ID, name));
            helper.assertTrue(item != Items.AIR, "item hypercluster:" + name + " is not registered");
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.getMaxStackSize() == 64,
                    "item hypercluster:" + name + " max stack size is " + stack.getMaxStackSize() + ", expected 64");
            // Two same-type partial stacks must merge into one stack of 64.
            SimpleContainer slots = new SimpleContainer(1);
            slots.setItem(0, new ItemStack(item, 30));
            ItemStack leftover = slots.addItem(new ItemStack(item, 34));
            helper.assertTrue(slots.getItem(0).getCount() == 64 && leftover.isEmpty(),
                    "item hypercluster:" + name + " does not stack: merged=" + slots.getItem(0).getCount() +
                            " leftover=" + leftover);
            // One translatable tooltip line keyed by "item.hypercluster.<name>.tooltip".
            List<Component> tooltipList = new ArrayList<>();
            item.appendHoverText(stack, null, tooltipList, TooltipFlag.Default.NORMAL);
            String tooltipKey = "item.hypercluster." + name + ".tooltip";
            boolean hasTooltipKey = tooltipList.stream()
                    .anyMatch(c -> c
                            .getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc &&
                            tooltipKey.equals(tc.getKey()));
            helper.assertTrue(hasTooltipKey,
                    "item hypercluster:" + name + " tooltip lacks translatable key " + tooltipKey + " (got " +
                            tooltipList + ")");
            String nameKey = "item.hypercluster." + name;
            String resolved = Language.getInstance().has(nameKey) ? Language.getInstance().getOrDefault(nameKey) : null;
            System.out.printf("[HC-PROBE] item=%s maxStack=64 merged=64 tooltipKey=%s enUs=%s%n",
                    name, tooltipKey, resolved == null ? "<server lang not loaded>" : resolved);
            helper.succeed();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 0: record the benchmark recipe from the live RecipeLogic //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 500)
    public static void benchmarkRecipeRecorded(GameTestHelper helper) {
        SimpleTieredMachine machine = placeWasher(helper, new BlockPos(2, 1, 2));
        // 3 batches: two finished and the third still running at the assert tick, so
        // lastRecipe/lastOriginRecipe stay populated (an idle machine nulls them on the next search).
        startMachine(machine, 3, 10_000, () -> true, helper, 1, 20, 400);

        helper.runAtTickTime(300, () -> {
            RecipeLogic logic = machine.recipeLogic;
            GTRecipe origin = logic.getLastOriginRecipe();
            GTRecipe modified = logic.getLastRecipe();
            helper.assertTrue(origin != null, "lastOriginRecipe is null");
            helper.assertTrue(modified != null, "lastRecipe is null");
            if (origin != null) {
                System.out.printf(
                        "[HC-PROBE] benchmark origin recipe id=%s duration=%d EUt=%d inputs=%s fluids=%s outputs=%s%n",
                        origin.id, origin.duration, RecipeHelper.getRealEUt(origin).getTotalEU(),
                        RecipeHelper.getInputItems(origin), RecipeHelper.getInputFluids(origin),
                        RecipeHelper.getOutputItems(origin));
                helper.assertTrue(String.valueOf(origin.id).contains("wash_copper"),
                        "unexpected matched recipe id " + origin.id);
                helper.assertTrue(String.valueOf(origin.id).contains("to_purified_ore"),
                        "unexpected matched recipe id " + origin.id);
            }
            if (modified != null) {
                System.out.printf(
                        "[HC-PROBE] benchmark modified recipe id=%s duration=%d EUt=%d outputs=%s tickInputs=%s%n",
                        modified.id, modified.duration, RecipeHelper.getRealEUt(modified).getTotalEU(),
                        RecipeHelper.getOutputItems(modified), modified.tickInputs);
                helper.assertTrue(modified.duration == OC_DURATION,
                        "HV OC duration expected " + OC_DURATION + " but got " + modified.duration);
                helper.assertTrue(RecipeHelper.getRealEUt(modified).getTotalEU() == OC_EUT,
                        "HV OC EUt expected " + OC_EUT + " but got " + RecipeHelper.getRealEUt(modified).getTotalEU());
            }
            int probeCount = SettlementProbe.count(machine.getPos());
            helper.assertTrue(probeCount == 2,
                    "expected 2 finished afterWorking events by now, got " + probeCount);
            int purified = countItem(machine, purifiedCopperOre());
            int stoneDust = countItem(machine, stoneDust());
            helper.assertTrue(purified == 2 && stoneDust == 2,
                    "two settled batches expected purified=" + purified + " stoneDust=" + stoneDust);
            helper.assertTrue(logic.isWorking(),
                    "the third batch should still be running, status=" + logic.getStatus());
            System.out.printf(
                    "[HC-PROBE] benchmark settled: probeCount=%d purified=%d stoneDust=%d batch3Progress=%d/%d status=%s%n",
                    probeCount, purified, stoneDust, logic.getProgress(), logic.getDuration(), logic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 1: ten sequential batches settle, one per batch, no duplicates //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 2000)
    public static void tenSequentialBatches(GameTestHelper helper) {
        SimpleTieredMachine machine = placeWasher(helper, new BlockPos(2, 1, 2));
        startMachine(machine, 10, 10_000, () -> true, helper, 1, 20, 1700);

        helper.runAtTickTime(1800, () -> {
            BlockPos worldPos = machine.getPos();
            List<SettlementProbe.ProbeEvent> events = SettlementProbe.events(worldPos);
            helper.assertTrue(events.size() == 10,
                    "expected 10 afterWorking events, got " + events.size() + " -> " + events);
            for (SettlementProbe.ProbeEvent event : events) {
                helper.assertTrue(event.looksLikeFinish(),
                        "afterWorking fired outside a finish state: " + event);
            }
            // Consecutive finishes must be spaced by the OC'd recipe duration (~100 ticks).
            long firstTick = events.get(0).gameTime();
            long lastTick = events.get(events.size() - 1).gameTime();
            System.out.printf("[HC-PROBE] ten batches first=%d last=%d span=%d (expected ~%d)%n",
                    firstTick, lastTick, lastTick - firstTick, 9L * OC_DURATION);
            int purified = countItem(machine, purifiedCopperOre());
            int stoneDust = countItem(machine, stoneDust());
            helper.assertTrue(purified == 10, "expected 10 purified output, got " + purified);
            helper.assertTrue(stoneDust == 10, "expected 10 stone dust byproduct, got " + stoneDust);
            helper.assertTrue(machine.importItems.getStackInSlot(0).isEmpty(), "input was not fully consumed");
            helper.assertTrue(machine.recipeLogic.getStatus() == RecipeLogic.Status.IDLE,
                    "machine should be idle after the last batch, status=" + machine.recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 2: output slots stuffed mid-run //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 800)
    public static void outputJamLosesBatch(GameTestHelper helper) {
        SimpleTieredMachine machine = placeWasher(helper, new BlockPos(2, 1, 2));
        startMachine(machine, 2, 10_000, () -> true, helper, 1, 20, 700);

        // Stuff all three output slots while the first batch is mid-run.
        helper.runAtTickTime(40, () -> {
            for (int i = 0; i < machine.exportItems.getSlots(); i++) {
                machine.exportItems.setStackInSlot(i, new ItemStack(Blocks.COBBLESTONE, 64));
            }
        });

        helper.runAtTickTime(700, () -> {
            BlockPos worldPos = machine.getPos();
            List<SettlementProbe.ProbeEvent> events = SettlementProbe.events(worldPos);
            helper.assertTrue(events.size() == 1,
                    "expected 1 afterWorking event after jam, got " + events.size() + " -> " + events);
            if (!events.isEmpty()) {
                helper.assertTrue(events.get(0).looksLikeFinish(),
                        "the jammed batch event must still look like a finish: " + events.get(0));
            }
            helper.assertTrue(machine.exportItems.getStackInSlot(0).is(Items.COBBLESTONE),
                    "output slots should still hold the jamming items");
            int purified = countItem(machine, purifiedCopperOre());
            int stoneDust = countItem(machine, stoneDust());
            helper.assertTrue(purified == 0, "expected 0 purified output after jam, got " + purified);
            helper.assertTrue(stoneDust == 0, "expected 0 stone dust after jam, got " + stoneDust);
            ItemStack remaining = machine.importItems.getStackInSlot(0);
            helper.assertTrue(remaining.getCount() == 1, "remaining crushed input expected 1, got " + remaining);
            helper.assertTrue(machine.recipeLogic.getStatus() == RecipeLogic.Status.IDLE,
                    "machine should be idle after jam, status=" + machine.recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 3: abort mid-run vs pause-after-finish //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 900)
    public static void interruptMidRunVsPause(GameTestHelper helper) {
        SimpleTieredMachine aborted = placeWasher(helper, new BlockPos(2, 1, 2));
        startMachine(aborted, 1, 10_000, () -> true, helper, 1, 20, 800);

        // Abort the running batch halfway (the machine-controller-cover abort path).
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(aborted.recipeLogic.isWorking(),
                    "machine should be working before interrupt, status=" + aborted.recipeLogic.getStatus());
            aborted.recipeLogic.interruptRecipe();
            System.out.printf("[HC-PROBE] interrupted at progress=%d/%d probe=%d status=%s%n",
                    aborted.recipeLogic.getProgress(), aborted.recipeLogic.getDuration(),
                    SettlementProbe.count(aborted.getPos()), aborted.recipeLogic.getStatus());
        });

        SimpleTieredMachine paused = placeWasher(helper, new BlockPos(7, 1, 2));
        startMachine(paused, 1, 10_000, () -> true, helper, 1, 20, 800);
        // Pause (the UI toggle path) mid-run: setWorkingEnabled(false) only suspends AFTER the batch finishes.
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(paused.recipeLogic.isWorking(),
                    "paused machine should be working before pause request");
            paused.setWorkingEnabled(false);
        });

        helper.runAtTickTime(800, () -> {
            // Aborted machine: afterWorking fired once, nothing produced, input consumed.
            List<SettlementProbe.ProbeEvent> abortedEvents = SettlementProbe.events(aborted.getPos());
            helper.assertTrue(abortedEvents.size() == 1,
                    "interrupt must fire afterWorking exactly once, got " + abortedEvents);
            if (!abortedEvents.isEmpty()) {
                helper.assertTrue(!abortedEvents.get(0).looksLikeFinish(),
                        "the interrupted event must not look like a finish: " + abortedEvents.get(0));
            }
            helper.assertTrue(!aborted.recipeLogic.isWorking(), "aborted machine must not be working");
            helper.assertTrue(countItem(aborted, purifiedCopperOre()) == 0,
                    "interrupted batch produced output! count=" + countItem(aborted, purifiedCopperOre()));
            helper.assertTrue(countItem(aborted, stoneDust()) == 0, "interrupted batch produced dust");
            helper.assertTrue(aborted.importItems.getStackInSlot(0).isEmpty(),
                    "interrupted batch must still have consumed its input");

            // Paused machine: completed the batch normally (products settled), then suspended.
            List<SettlementProbe.ProbeEvent> pausedEvents = SettlementProbe.events(paused.getPos());
            helper.assertTrue(pausedEvents.size() == 1,
                    "pause-after-finish must complete exactly one batch, got " + pausedEvents);
            helper.assertTrue(countItem(paused, purifiedCopperOre()) == 1,
                    "paused machine purified output=" + countItem(paused, purifiedCopperOre()));
            helper.assertTrue(countItem(paused, stoneDust()) == 1,
                    "paused machine dust output=" + countItem(paused, stoneDust()));
            helper.assertTrue(paused.recipeLogic.isSuspend(),
                    "paused machine should be suspended after finishing, status=" + paused.recipeLogic.getStatus());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 4: power loss then recovery //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 1600)
    public static void powerLossThenRecovery(GameTestHelper helper) {
        SimpleTieredMachine machine = placeWasher(helper, new BlockPos(2, 1, 2));
        AtomicBoolean powerOn = new AtomicBoolean(true);
        startMachine(machine, 1, 10_000, powerOn::get, helper, 1, 20, 1500);

        helper.runAtTickTime(40, () -> {
            powerOn.set(false);
            machine.energyContainer.removeEnergy(machine.energyContainer.getEnergyStored());
        });
        helper.runAtTickTime(90, () -> {
            RecipeLogic logic = machine.recipeLogic;
            helper.assertTrue(logic.isWaiting(),
                    "expected WAITING during power loss, status=" + logic.getStatus() + " reason=" +
                            logic.showFancyTooltip());
            helper.assertTrue(logic.getProgress() <= 1,
                    "progress must regress to <=1 while waiting (regressWhenWaiting), progress=" + logic.getProgress());
            System.out.printf("[HC-PROBE] powerOut status=%s progress=%d energy=%d reason=%s%n",
                    logic.getStatus(), logic.getProgress(), machine.energyContainer.getEnergyStored(),
                    logic.showFancyTooltip());
        });
        helper.runAtTickTime(320, () -> powerOn.set(true));

        helper.runAtTickTime(1500, () -> {
            List<SettlementProbe.ProbeEvent> events = SettlementProbe.events(machine.getPos());
            helper.assertTrue(events.size() == 1,
                    "power loss + recovery must settle exactly once, got " + events.size() + " -> " + events);
            helper.assertTrue(events.stream().allMatch(SettlementProbe.ProbeEvent::looksLikeFinish),
                    "power-loss events must all be finish-shaped: " + events);
            System.out.printf("[HC-PROBE] powerRecovered finish at gameTime=%d machineTick=%d status=%s%n",
                    events.get(0).gameTime(), events.get(0).machineTick(), machine.recipeLogic.getStatus());
            int purified = countItem(machine, purifiedCopperOre());
            int stoneDust = countItem(machine, stoneDust());
            helper.assertTrue(purified == 1 && stoneDust == 1,
                    "recovered batch output purified=" + purified + " dust=" + stoneDust);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // P02 scenario 5: two machines finishing on the same tick //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = PROBE_BATCH, timeoutTicks = 600)
    public static void twoMachinesSameTick(GameTestHelper helper) {
        SimpleTieredMachine first = placeWasher(helper, new BlockPos(2, 1, 2));
        SimpleTieredMachine second = placeWasher(helper, new BlockPos(7, 1, 2));
        startMachine(first, 1, 10_000, () -> true, helper, 1, 20, 520);
        startMachine(second, 1, 10_000, () -> true, helper, 1, 20, 520);

        helper.runAtTickTime(540, () -> {
            List<SettlementProbe.ProbeEvent> firstEvents = SettlementProbe.events(first.getPos());
            List<SettlementProbe.ProbeEvent> secondEvents = SettlementProbe.events(second.getPos());
            helper.assertTrue(firstEvents.size() == 1 && secondEvents.size() == 1,
                    "each machine must finish exactly once: " + firstEvents.size() + "/" + secondEvents.size());
            if (!firstEvents.isEmpty() && !secondEvents.isEmpty()) {
                helper.assertTrue(firstEvents.get(0).gameTime() == secondEvents.get(0).gameTime(),
                        "machines were expected to finish on the same tick: " + firstEvents.get(0).gameTime() + " vs " +
                                secondEvents.get(0).gameTime());
                System.out.printf(
                        "[HC-PROBE] same-tick finish ok: gameTimeA=%d gameTimeB=%d offsetDelta=%d posA=%s posB=%s%n",
                        firstEvents.get(0).gameTime(), secondEvents.get(0).gameTime(),
                        Math.abs(firstEvents.get(0).machineTick() - secondEvents.get(0).machineTick()),
                        first.getPos(), second.getPos());
            }
            for (SimpleTieredMachine machine : List.of(first, second)) {
                int purified = countItem(machine, purifiedCopperOre());
                int stoneDust = countItem(machine, stoneDust());
                helper.assertTrue(purified == 1 && stoneDust == 1,
                        "machine " + machine.getPos() + " purified=" + purified + " dust=" + stoneDust);
            }
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static SimpleTieredMachine placeWasher(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, WASHER.getBlock().defaultBlockState());
        return (SimpleTieredMachine) ((com.gregtechceu.gtceu.api.machine.IMachineBlockEntity) Objects.requireNonNull(
                helper.getBlockEntity(relativePos))).getMetaMachine();
    }

    private static Item purifiedCopperOre() {
        return ChemicalHelper.get(TagPrefix.crushedPurified, GTMaterials.Copper).getItem();
    }

    private static Item stoneDust() {
        return ChemicalHelper.get(TagPrefix.dust, GTMaterials.Stone).getItem();
    }

    /**
     * Feeds {@code batches}x crushed copper ore + programmed circuit #1 + water, primes the energy
     * container, and schedules an EU "grid" that tops the container up while the power switch is on.
     */
    private static void startMachine(SimpleTieredMachine machine, int batches, int waterMb,
                                     BooleanSupplier powerOn, GameTestHelper helper,
                                     long firstRefillTick, long refillEvery, long lastRefillTick) {
        ItemStack crushed = ChemicalHelper.get(TagPrefix.crushed, GTMaterials.Copper);
        crushed.setCount(batches);
        machine.importItems.setStackInSlot(0, crushed);
        machine.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(1));
        machine.importFluids.setFluidInTank(0, GTMaterials.Water.getFluid(waterMb));
        machine.energyContainer.addEnergy(machine.energyContainer.getEnergyCapacity());
        for (long t = firstRefillTick; t <= lastRefillTick; t += refillEvery) {
            helper.runAtTickTime(t, () -> {
                if (powerOn.getAsBoolean()) {
                    machine.energyContainer.addEnergy(machine.energyContainer.getEnergyCapacity());
                }
            });
        }
    }

    private static int countItem(SimpleTieredMachine machine, Item item) {
        int found = 0;
        for (int i = 0; i < machine.exportItems.getSlots(); i++) {
            ItemStack stack = machine.exportItems.getStackInSlot(i);
            if (stack.getItem() == item) found += stack.getCount();
        }
        return found;
    }
}
