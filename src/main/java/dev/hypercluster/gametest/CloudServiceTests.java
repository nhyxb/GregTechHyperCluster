package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.cloud.CloudConfigs;
import dev.hypercluster.common.cloud.CloudModels;
import dev.hypercluster.common.cloud.CloudPricing;
import dev.hypercluster.common.cloud.CloudTokenLedger;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.CredentialCardItem;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.TokenCreditCardItem;
import dev.hypercluster.common.machine.cloud.ApiSatelliteStationMachine;
import dev.hypercluster.common.machine.cloud.LocalFallbackGatewayMachine;

import java.util.Objects;
import java.util.UUID;

/**
 * 0.4 acceptance gametests: the IV closed-source cloud (API satellite ground station, Token
 * ledger, billing contract and the local fallback gateway).
 *
 * <p>
 * <b>Isolation.</b> Every gametest batch runs its tests in parallel inside one shared world save
 * — and the whole point of the ledger is that it is per-save global state. Each test therefore
 * runs in its <b>own singleton batch</b> (sequential) with its <b>own fresh account UUID</b>, so
 * balances, welcome flags, sessions and cache pointers can never bleed between tests.
 *
 * <ul>
 * <li>the ledger contract: welcome bonus exactly once, order hold/settle/release with
 * unique-order-id dedup (never double-charge, never double-refund), insufficient balance
 * refuses, tier/slot thresholds and the NBT round-trip;</li>
 * <li>the station: opening an account at a second station (or after a reload) never re-grants
 * the welcome Tokens, dataset uploads sell for integer Tokens, and the credit card deposits
 * and withdraws whole balances;</li>
 * <li>the uplink: LV/MV energy drives the rounds, an occluded feed horn refuses new rounds,
 * rain/thunder attenuate ×1.5/×2.0 and {@code Y > 120} earns the small latency/rate bonus;</li>
 * <li>the billing contract live: insufficient balance refuses to start (nothing charged),
 * a cancelled round releases its pre-deduction exactly, a completed round settles once — and a
 * save/load mid-round resumes and still settles exactly once;</li>
 * <li>prompt caching: the second consecutive round of one
 * {@code (playerUuid, machinePos, recipeId, modelId, tier)} key bills 10%, any key dimension
 * change restarts the full rate; batch mode bills ×0.5 at ×2 duration and stacks
 * multiplicatively with the cache (5%);</li>
 * <li>429: over the account's concurrency ceiling a second station is paused without billing
 * until the first session ends;</li>
 * <li>model lifecycle: Leaving Soon stays usable with the countdown, the expired model blocks
 * cloud rounds without billing;</li>
 * <li>the local fallback gateway: rounds blocked by insufficient funds (and 429 / retired
 * models) continue locally at ×4 duration with no Token billing, and the station never
 * stalls.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class CloudServiceTests {

    public static final String CLOUD_BATCH = "hypercluster_04_cloud";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. the ledger contract (pure, no blocks) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = CLOUD_BATCH + "_01_ledger", timeoutTicks = 100)
    public static void ledgerContractOrdersAndPersistence(GameTestHelper helper) {
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        // Welcome bonus: granted once per account, never repeated.
        helper.assertTrue(ledger.claimWelcomeBonus(playerA), "first claim must grant");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                "welcome balance must be " + CloudTokenLedger.WELCOME_BONUS + ", got " +
                        ledger.balance(playerA));
        helper.assertTrue(!ledger.claimWelcomeBonus(playerA), "second claim must be refused");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                "a refused claim must not change the balance");
        helper.assertTrue(ledger.claimWelcomeBonus(playerB),
                "a second player gets their own bonus");

        // Orders: hold -> settle / release with unique-id dedup. (Random ids: the gametest
        // world save persists across runs, so fixed ids would collide with earlier runs.)
        String o1 = "o-" + UUID.randomUUID();
        String o2 = "o-" + UUID.randomUUID();
        String o3 = "o-" + UUID.randomUUID();
        helper.assertTrue(ledger.hold(playerA, o1, 100_000) == CloudTokenLedger.HoldResult.GRANTED,
                "a funded hold must be granted");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS - 100_000,
                "the hold must pre-deduct");
        helper.assertTrue(
                ledger.hold(playerA, o1, 100_000) == CloudTokenLedger.HoldResult.ALREADY_HELD,
                "the same order id must dedup to ALREADY_HELD");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS - 100_000,
                "the dedup hold must not charge twice");
        helper.assertTrue(ledger.release(o1), "the held order must release");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                "the release must refund exactly the held amount");
        helper.assertTrue(!ledger.release(o1), "a released order must never refund twice");
        helper.assertTrue(ledger.order(o1).state == CloudTokenLedger.OrderState.REFUNDED,
                "the released order must be REFUNDED");

        helper.assertTrue(ledger.hold(playerA, o2, 100_000) == CloudTokenLedger.HoldResult.GRANTED,
                "o2 must be granted");
        helper.assertTrue(ledger.settle(o2), "o2 must settle");
        helper.assertTrue(ledger.totalSpent(playerA) == 100_000,
                "settling must add to the cumulative spend");
        helper.assertTrue(!ledger.settle(o2), "a settled order must not settle twice");
        helper.assertTrue(!ledger.release(o2), "a settled order must never be refunded");
        helper.assertTrue(ledger.order(o2).state == CloudTokenLedger.OrderState.SETTLED,
                "the settled order must stay SETTLED");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS - 100_000,
                "a settled hold stays deducted");

        helper.assertTrue(
                ledger.hold(playerA, o3, CloudTokenLedger.WELCOME_BONUS) == CloudTokenLedger.HoldResult.INSUFFICIENT,
                "a hold beyond the balance must be refused");
        helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS - 100_000,
                "a refused hold must not change the balance");

        // Concurrency tiers (累计消费决定 Tier 1-5 并发上限).
        helper.assertTrue(CloudTokenLedger.tierForSpent(0) == 1, "0 spent is Tier 1");
        helper.assertTrue(CloudTokenLedger.tierForSpent(CloudTokenLedger.TIER2_THRESHOLD) == 2,
                "the Tier 2 threshold must be Tier 2");
        helper.assertTrue(CloudTokenLedger.tierForSpent(CloudTokenLedger.TIER3_THRESHOLD) == 3,
                "the Tier 3 threshold must be Tier 3");
        helper.assertTrue(CloudTokenLedger.tierForSpent(CloudTokenLedger.TIER4_THRESHOLD) == 4,
                "the Tier 4 threshold must be Tier 4");
        helper.assertTrue(CloudTokenLedger.tierForSpent(CloudTokenLedger.TIER5_THRESHOLD) == 5,
                "the Tier 5 threshold must be Tier 5");
        helper.assertTrue(CloudTokenLedger.slotsForSpent(0) == CloudTokenLedger.TIER1_SLOTS,
                "Tier 1 allows exactly " + CloudTokenLedger.TIER1_SLOTS + " session");

        // Sessions: one live session of a Tier 1 account, the second is a 429.
        helper.assertTrue(ledger.beginSession(playerB), "the first session must be granted");
        helper.assertTrue(!ledger.beginSession(playerB),
                "a Tier 1 account must refuse the second concurrent session (429)");
        ledger.endSession(playerB);
        helper.assertTrue(ledger.beginSession(playerB), "after the release the session frees");
        ledger.endSession(playerB); // never leak a session into other tests

        // Prompt-cache pointer: consecutive hits, invalidated by any key change (runtime only).
        ledger.recordCacheKey(playerB, "K1");
        helper.assertTrue(ledger.isCacheHit(playerB, "K1"), "the same key must hit");
        helper.assertTrue(!ledger.isCacheHit(playerB, "K2"), "a changed key must miss");
        ledger.recordCacheKey(playerB, "K1");
        helper.assertTrue(ledger.cacheHits(playerB) == 1, "the hit count must be 1");

        // The persistence boundary: the whole contract round-trips through the ledger NBT.
        CompoundTag saved = ledger.save(new CompoundTag());
        CloudTokenLedger reloaded = new CloudTokenLedger(saved);
        helper.assertTrue(reloaded.balance(playerA) == CloudTokenLedger.WELCOME_BONUS - 100_000,
                "balances must survive the save/load round-trip");
        helper.assertTrue(reloaded.totalSpent(playerA) == 100_000, "spent totals must persist");
        helper.assertTrue(!reloaded.claimWelcomeBonus(playerA),
                "the welcome flag must persist — no re-grant after a reload");
        helper.assertTrue(!reloaded.claimWelcomeBonus(playerB),
                "the second player's flag must persist too");
        helper.assertTrue(reloaded.order(o2) != null &&
                reloaded.order(o2).state == CloudTokenLedger.OrderState.SETTLED,
                "the order book must persist");
        helper.assertTrue(!reloaded.isCacheHit(playerB, "K1"),
                "the cache pointer is runtime state — a reload starts cold");

        System.out.printf("[HC-04] ledger: welcome-once + order dedup/refund + tiers + NBT round-trip%n");
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. the welcome bonus never repeats across stations //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_02_welcome", timeoutTicks = 400)
    public static void welcomeBonusOnceAcrossStations(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        StationParts b = placeStation(helper, 7, 2, 7, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 350);
        primeStationEnergy(b, helper, 1, 20, 350);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            ensure(helper, b);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(playerA));
        });
        helper.runAtTickTime(70, () -> helper.assertTrue(
                ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                "the first station must grant the welcome bonus, got " + ledger.balance(playerA)));

        // The same account card at a second station: no re-grant.
        helper.runAtTickTime(90, () -> b.station().getCredentialSlot()
                .setStackInSlot(0, boundCard(playerA)));
        helper.runAtTickTime(120, () -> helper.assertTrue(
                ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                "a second station must never re-grant, got " + ledger.balance(playerA)));

        // A different player opens their own account at the second station.
        helper.runAtTickTime(140, () -> b.station().getCredentialSlot()
                .setStackInSlot(0, boundCard(playerB)));
        helper.runAtTickTime(170, () -> {
            helper.assertTrue(ledger.balance(playerB) == CloudTokenLedger.WELCOME_BONUS,
                    "the second player must get their own welcome bonus");
            helper.assertTrue(ledger.balance(playerA) == CloudTokenLedger.WELCOME_BONUS,
                    "player A's balance must stay untouched");
        });

        // The flag survives the ledger's own persistence boundary.
        helper.runAtTickTime(190, () -> {
            CompoundTag savedTag = ledger.save(new CompoundTag());
            CloudTokenLedger reloaded = new CloudTokenLedger(savedTag);
            helper.assertTrue(!reloaded.claimWelcomeBonus(playerA),
                    "a reloaded ledger must never re-grant the welcome bonus");
            helper.assertTrue(!reloaded.claimWelcomeBonus(playerB),
                    "a reloaded ledger must never re-grant for the second player either");
            System.out.printf("[HC-04] welcome bonus: one grant per account across stations and reloads%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. dataset sales + the token credit card //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_03_sale", timeoutTicks = 500)
    public static void datasetSaleAndTokenCardFlows(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 450);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
        });

        // Sell 2 high-purity mining datasets: +2 x 50,000.
        helper.runAtTickTime(70, () -> a.station().getSellSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get(), 2)));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(ledger.balance(player) == CloudTokenLedger.WELCOME_BONUS + 100_000,
                    "two mining datasets must sell for 100,000, balance=" + ledger.balance(player));
            helper.assertTrue(a.station().getSellSlot().getStackInSlot(0).isEmpty(),
                    "the sell slot must be emptied by the sale");
        });

        // Sell 3 chemical datasets: +3 x 50,000.
        helper.runAtTickTime(120, () -> a.station().getSellSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get(), 3)));
        helper.runAtTickTime(150, () -> helper.assertTrue(
                ledger.balance(player) == CloudTokenLedger.WELCOME_BONUS + 250_000,
                "three chemical datasets must sell for 150,000, balance=" + ledger.balance(player)));

        // The token credit card: deposit the card value, then withdraw the whole balance back.
        long balanceBeforeCard = CloudTokenLedger.WELCOME_BONUS + 250_000;
        helper.runAtTickTime(170, () -> {
            ItemStack card = new ItemStack(HyperClusterItems.TOKEN_CREDIT_CARD.get());
            TokenCreditCardItem.setValue(card, 500_000);
            a.station().getCardSlot().setStackInSlot(0, card);
            a.station().depositCard();
            helper.assertTrue(ledger.balance(player) == balanceBeforeCard + 500_000,
                    "the deposit must credit the card's value, balance=" + ledger.balance(player));
            helper.assertTrue(TokenCreditCardItem.value(a.station().getCardSlot().getStackInSlot(0)) == 0,
                    "the deposited card must be emptied");
        });
        helper.runAtTickTime(200, () -> {
            a.station().withdrawToCard();
            long onCard = TokenCreditCardItem.value(a.station().getCardSlot().getStackInSlot(0));
            helper.assertTrue(ledger.balance(player) == 0,
                    "the withdraw must move the whole balance off the account");
            helper.assertTrue(onCard == balanceBeforeCard + 500_000,
                    "the card must carry the withdrawn balance, got " + onCard);
        });
        helper.runAtTickTime(230, () -> {
            a.station().depositCard();
            helper.assertTrue(
                    ledger.balance(player) == balanceBeforeCard + 500_000,
                    "re-depositing the card must restore the account");
            System.out.printf("[HC-04] datasets sold for 250k + card deposit/withdraw round-trip%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. LV power drives rounds; the sky check; signal factors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_04_uplink", timeoutTicks = 900)
    public static void lvPowerDrivesRoundsAndSkyCheck(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 850);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 3));
        });

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(a.station().isTaskActive(),
                    "an LV hatch must drive the first cloud round, status=" +
                            a.station().getWaitingReason());
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE,
                    "the first Medium round must bill " + CloudPricing.BASE_TASK_PRICE + ", got " +
                            a.station().getTaskPrice());
            helper.assertTrue(a.station().getTaskTicksTotal() == CloudPricing.BASE_TASK_TICKS,
                    "the first Medium round must run " + CloudPricing.BASE_TASK_TICKS + " ticks");
        });

        // Round 1 settles; round 2 (cached, 10%) runs on.
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(ledger.totalSpent(player) == CloudPricing.BASE_TASK_PRICE,
                    "round 1 must have settled " + CloudPricing.BASE_TASK_PRICE + ", spent=" +
                            ledger.totalSpent(player));
            helper.assertTrue(a.station().isTaskActive(), "round 2 must be running");
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE / 10,
                    "round 2 must bill the cached 10% rate, got " + a.station().getTaskPrice());
        });

        // Occlude the feed horn: the RUNNING round finishes, the next one is refused.
        // (GameTestHelper.setBlock takes ARENA-relative positions; hornPos() is world-absolute.)
        BlockPos hornRel = a.station().hornPos().subtract(helper.absolutePos(BlockPos.ZERO));
        final long[] balanceWhileBlocked = new long[1];
        helper.runAtTickTime(240, () -> helper.setBlock(hornRel.above(),
                Blocks.STONE.defaultBlockState()));
        helper.runAtTickTime(340, () -> {
            helper.assertTrue(!a.station().isTaskActive(),
                    "after the horn is occluded no new round may start, status=" +
                            a.station().getWaitingReason());
            helper.assertTrue(a.station().getWaitingReason() ==
                    ApiSatelliteStationMachine.WaitingReason.SKY_BLOCKED,
                    "the occluded horn must report SKY_BLOCKED, got " + a.station().getWaitingReason());
            balanceWhileBlocked[0] = ledger.balance(player);
        });
        helper.runAtTickTime(370, () -> helper.assertTrue(
                ledger.balance(player) == balanceWhileBlocked[0],
                "a blocked round must not bill anything"));

        // Clear the sky: the next round starts again.
        helper.runAtTickTime(390, () -> helper.setBlock(hornRel.above(), Blocks.AIR.defaultBlockState()));
        helper.runAtTickTime(440, () -> helper.assertTrue(a.station().isTaskActive(),
                "clearing the sky must restart the rounds, status=" + a.station().getWaitingReason()));

        // Pure signal factors: weather attenuation and the high-altitude bonus.
        helper.runAtTickTime(460, () -> {
            helper.assertTrue(CloudPricing.weatherMultiplier(false, false) == 1.0,
                    "clear weather must not attenuate");
            helper.assertTrue(CloudPricing.weatherMultiplier(true, false) == 1.5,
                    "rain must attenuate x1.5");
            helper.assertTrue(CloudPricing.weatherMultiplier(true, true) == 2.0,
                    "thunder must attenuate x2.0");
            helper.assertTrue(!CloudPricing.highAltitudeBonus(new BlockPos(0, 120, 0)),
                    "Y=120 must not earn the altitude bonus");
            helper.assertTrue(CloudPricing.highAltitudeBonus(new BlockPos(0, 121, 0)),
                    "Y=121 must earn the altitude bonus");
            helper.assertTrue(CloudPricing.demoTaskTicks(CloudPricing.InferenceTier.MEDIUM, false,
                    1.5, false, false) == 150, "rain must stretch a round to 150 ticks");
            helper.assertTrue(CloudPricing.demoTaskTicks(CloudPricing.InferenceTier.MEDIUM, false,
                    2.0, false, false) == 200, "thunder must stretch a round to 200 ticks");
            helper.assertTrue(CloudPricing.demoTaskPrice(CloudPricing.InferenceTier.MEDIUM, false,
                    false, true) == 23_750, "the altitude rate bonus must be x0.95");
            helper.assertTrue(CloudPricing.demoTaskTicks(CloudPricing.InferenceTier.MEDIUM, false,
                    1.0, true, false) == 90, "the altitude latency bonus must be x0.9");
            System.out.printf("[HC-04] LV power + open-sky check + weather/altitude factors verified%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 5. insufficient balance refuses; cancel refunds exactly //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_05_billing", timeoutTicks = 900)
    public static void insufficientBalanceRefusesAndCancelRefunds(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 850);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
        });

        // Drain the account dry BEFORE any input exists, so no round can ever start.
        helper.runAtTickTime(70, () -> ledger.debit(player, ledger.balance(player)));
        helper.runAtTickTime(90, () -> a.station().getInputSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 2)));
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(ledger.balance(player) == 0, "the account must be drained");
            helper.assertTrue(!a.station().isTaskActive(),
                    "an empty account must refuse to start the round");
            helper.assertTrue(a.station().getWaitingReason() ==
                    ApiSatelliteStationMachine.WaitingReason.INSUFFICIENT_FUNDS,
                    "the refusal must read INSUFFICIENT_FUNDS, got " + a.station().getWaitingReason());
        });

        // Fund exactly one round: it starts, pre-deducts and holds the order.
        helper.runAtTickTime(150, () -> ledger.credit(player, CloudPricing.BASE_TASK_PRICE));
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(a.station().isTaskActive(), "the funded round must start, status=" +
                    a.station().getWaitingReason());
            helper.assertTrue(ledger.balance(player) == 0,
                    "the started round must have pre-deducted its price");
            String orderId = a.station().getTaskOrderId();
            helper.assertTrue(!orderId.isEmpty(), "the round must carry its order id");
            helper.assertTrue(ledger.order(orderId) != null &&
                    ledger.order(orderId).state == CloudTokenLedger.OrderState.HELD,
                    "the order must be HELD mid-round");
        });

        // Cancel mid-round: the hold is released exactly once and the input returns — all
        // asserted in the same tick, then the input slot is emptied so the very next tick does
        // not simply start another (cache-hit) round on the refunded balance.
        helper.runAtTickTime(210, () -> {
            a.station().cancelTask();
            helper.assertTrue(ledger.balance(player) == CloudPricing.BASE_TASK_PRICE,
                    "the cancel must refund the exact pre-deduction, got " + ledger.balance(player));
            helper.assertTrue(ledger.totalSpent(player) == 0,
                    "a cancelled round must not count as spend");
            helper.assertTrue(a.station().getTaskOrderId().isEmpty(),
                    "the cancelled round must clear its order reference");
            helper.assertTrue(a.station().getInputSlot().getStackInSlot(0).getCount() == 2,
                    "the cancelled round must return its consumed input, got " +
                            a.station().getInputSlot().getStackInSlot(0).getCount());
            a.station().getInputSlot().setStackInSlot(0, ItemStack.EMPTY);
        });

        // Restart on a changed key (Extra High tier): settles exactly once on completion.
        helper.runAtTickTime(270, () -> {
            a.station().cycleTier(); // MEDIUM -> EXTRA_HIGH: a new cache key, full rate.
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1));
            ledger.credit(player, CloudPricing.BASE_TASK_PRICE * 2);
        });
        helper.runAtTickTime(300, () -> {
            helper.assertTrue(a.station().isTaskActive(), "the Extra High round must start");
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE * 2,
                    "the Extra High tier must bill x2, got " + a.station().getTaskPrice());
            helper.assertTrue(a.station().getTaskTicksTotal() == CloudPricing.BASE_TASK_TICKS / 2,
                    "the Extra High tier must halve the duration, got " +
                            a.station().getTaskTicksTotal());
        });
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(!a.station().isTaskActive(), "the Extra High round must be done");
            helper.assertTrue(ledger.balance(player) == CloudPricing.BASE_TASK_PRICE,
                    "only the settled round's price may stay deducted, balance=" + ledger.balance(player));
            helper.assertTrue(ledger.totalSpent(player) == CloudPricing.BASE_TASK_PRICE * 2,
                    "the settled round must add to the spend");
            helper.assertTrue(!a.station().getOutputSlot().getStackInSlot(0).isEmpty(),
                    "the settled round must produce its output");
            System.out.printf("[HC-04] billing: refuse on empty, exact refund on cancel, settle on finish%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 6. prompt caching: 10% from the 2nd consecutive round; key changes reset //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_06_cache", timeoutTicks = 900)
    public static void promptCacheDiscountAndKeyChange(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        StationParts b = placeStation(helper, 7, 2, 7, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 850);
        primeStationEnergy(b, helper, 1, 20, 850);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            ensure(helper, b);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            b.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 6));
        });

        // Round 1 full price; round 2 (same key) bills the 10% cache rate.
        helper.runAtTickTime(80, () -> helper.assertTrue(
                a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE,
                "round 1 must bill full price"));
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE / 10,
                    "round 2 must bill the 10% cache rate, got " + a.station().getTaskPrice());
            helper.assertTrue(ledger.cacheHits(player) == 1, "exactly one cache hit must be booked");
        });

        // Change one key dimension (the tier): round 3 restarts at the full Extra High rate.
        // Rounds 4-5 hit their own new key (10% again); B's round below then moves the account
        // pointer, so A's round 6 must pay the full rate once more.
        helper.runAtTickTime(230, () -> a.station().cycleTier()); // applies to the next round
        helper.runAtTickTime(260, () -> helper.assertTrue(
                a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE * 2,
                "a changed tier must restart the full (Extra High x2) rate, got " +
                        a.station().getTaskPrice()));

        // Change the machinePos dimension: the same account at another station pays full price.
        helper.runAtTickTime(360, () -> b.station().getInputSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1)));
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(b.station().isTaskActive(), "station B must run its round");
            helper.assertTrue(b.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE,
                    "another machine position must restart the full rate, got " +
                            b.station().getTaskPrice());
        });

        // Station B's round moved the account's cache pointer: A's next round pays full again.
        helper.runAtTickTime(430, () -> helper.assertTrue(
                a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE * 2,
                "after B's round A must restart at its own full (tier) rate, got " +
                        a.station().getTaskPrice()));
        System.out.printf("[HC-04] prompt cache: 10%% from round 2; tier/pos changes reset the rate%n");
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 7. batch mode: x0.5 bill at x2 duration, stacking with the cache //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_07_batch", timeoutTicks = 700)
    public static void batchModeHalfBillAndStacking(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 650);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().setBatchMode(true);
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 2));
        });

        // Batch round 1: half the bill, double the time.
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(a.station().isTaskActive(), "the batch round must start, status=" +
                    a.station().getWaitingReason());
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE / 2,
                    "batch must bill x0.5, got " + a.station().getTaskPrice());
            helper.assertTrue(a.station().getTaskTicksTotal() == CloudPricing.BASE_TASK_TICKS * 2,
                    "batch must run x2 duration, got " + a.station().getTaskTicksTotal());
        });

        // Batch round 2 with the cache hit: the documented stacking — x0.1 x x0.5 = x0.05.
        helper.runAtTickTime(330, () -> {
            helper.assertTrue(a.station().isTaskActive(), "the cached batch round must run");
            helper.assertTrue(a.station().getTaskPrice() == CloudPricing.BASE_TASK_PRICE / 20,
                    "cache + batch must stack to 5% (" + CloudPricing.BASE_TASK_PRICE / 20 +
                            "), got " + a.station().getTaskPrice());
            helper.assertTrue(a.station().getTaskTicksTotal() == CloudPricing.BASE_TASK_TICKS * 2,
                    "the cached batch round still runs x2 duration");
        });
        helper.runAtTickTime(380, () -> {
            helper.assertTrue(ledger.totalSpent(player) == CloudPricing.BASE_TASK_PRICE / 2,
                    "the first batch round must have settled, spent=" + ledger.totalSpent(player));
            System.out.printf("[HC-04] batch: x0.5 bill / x2 time; stacked with cache = 5%%%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 8. 429 concurrency across stations //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_08_429", timeoutTicks = 700)
    public static void concurrency429AcrossStations(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        StationParts b = placeStation(helper, 7, 2, 7, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 650);
        primeStationEnergy(b, helper, 1, 20, 650);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            ensure(helper, b);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            b.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1));
        });

        // A holds the account's only Tier 1 session; B is 429-paused without billing.
        helper.runAtTickTime(70, () -> b.station().getInputSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1)));
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(a.station().isTaskActive(), "station A must hold the session");
            helper.assertTrue(!b.station().isTaskActive(), "station B must be 429-paused");
            helper.assertTrue(b.station().getWaitingReason() ==
                    ApiSatelliteStationMachine.WaitingReason.RATE_LIMITED_429,
                    "B must report the 429, got " + b.station().getWaitingReason());
        });
        long balanceWhile429 = ledger.balance(player);
        helper.runAtTickTime(140, () -> helper.assertTrue(
                ledger.balance(player) == balanceWhile429,
                "the 429-paused round must not bill anything"));

        // A's round finishes and frees the session: B's round starts.
        helper.runAtTickTime(250, () -> helper.assertTrue(b.station().isTaskActive(),
                "B must start once A's session frees, status=" + b.station().getWaitingReason()));
        helper.runAtTickTime(280, () -> helper.assertTrue(
                ledger.sessionCount(player) == 1 && ledger.balance(player) ==
                        CloudTokenLedger.WELCOME_BONUS - 2 * CloudPricing.BASE_TASK_PRICE,
                "exactly one session must run and both rounds must be billed"));
        System.out.printf("[HC-04] 429: Tier 1 ceiling holds one session; B waits unbilled, then runs%n");
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 9. model lifecycle: Leaving Soon countdown, expired models block //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_09_lifecycle", timeoutTicks = 900)
    public static void leavingSoonExpiryBlocksCloud(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 850);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();
        int originalRetireDay = CloudConfigs.legacyModelRetireDay;

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().cycleModel(); // select the lifecycle demonstrator: legacy_llama
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 4));
        });

        long day = a.station().gameDay();
        helper.runAtTickTime(60, () -> {
            CloudConfigs.legacyModelRetireDay = (int) (day + 2); // ACTIVE today
            helper.assertTrue(CloudModels.lifecycle(CloudModels.LEGACY_LLAMA, day) ==
                    CloudModels.Lifecycle.ACTIVE, "day+2 must be ACTIVE");
            helper.assertTrue(CloudModels.lifecycle(CloudModels.OMNI_FLASH, day) ==
                    CloudModels.Lifecycle.ACTIVE, "the flagship never retires");
        });
        helper.runAtTickTime(100, () -> helper.assertTrue(a.station().isTaskActive(),
                "an ACTIVE model must accept cloud rounds, status=" + a.station().getWaitingReason()));

        // LEAVING_SOON: new rounds still START (round 2 begins under the countdown).
        helper.runAtTickTime(150, () -> {
            CloudConfigs.legacyModelRetireDay = (int) (day + 1); // LEAVING_SOON today
            helper.assertTrue(CloudModels.lifecycle(CloudModels.LEGACY_LLAMA, day) ==
                    CloudModels.Lifecycle.LEAVING_SOON, "day+1 must be LEAVING_SOON");
            helper.assertTrue(
                    CloudModels.leavingSoonCountdown(CloudModels.LEGACY_LLAMA, day) == 1,
                    "the countdown must show 1 day");
        });
        helper.runAtTickTime(280, () -> helper.assertTrue(a.station().isTaskActive(),
                "round 3 (started 243, after the LEAVING_SOON switch) must run: the model stays usable under the countdown"));

        // RETIRED: the expired model blocks new rounds without billing.
        helper.runAtTickTime(320, () -> CloudConfigs.legacyModelRetireDay = (int) day);
        // (Round 3, which started under LEAVING_SOON, keeps the right to finish and settle.)
        final long[] balanceAtExpiry = new long[1];
        helper.runAtTickTime(370, () -> balanceAtExpiry[0] = ledger.balance(player));
        helper.runAtTickTime(420, () -> {
            helper.assertTrue(CloudModels.lifecycle(CloudModels.LEGACY_LLAMA, day) ==
                    CloudModels.Lifecycle.RETIRED, "day must be RETIRED");
            helper.assertTrue(!a.station().isTaskActive(),
                    "the expired model must block the next round");
            helper.assertTrue(a.station().getWaitingReason() ==
                    ApiSatelliteStationMachine.WaitingReason.MODEL_OFFLINE,
                    "the block must read MODEL_OFFLINE, got " + a.station().getWaitingReason());
            helper.assertTrue(ledger.balance(player) == balanceAtExpiry[0],
                    "the blocked round must not bill");
            CloudConfigs.legacyModelRetireDay = originalRetireDay; // restore the default
            System.out.printf("[HC-04] lifecycle: ACTIVE -> LEAVING_SOON(usable) -> RETIRED(blocks)%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 10. the fallback gateway takes over blocked rounds locally //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_10_fallback", timeoutTicks = 1100)
    public static void fallbackGatewayTakesOverBlockedRounds(GameTestHelper helper) {
        // Station with an MV hatch and the fallback gateway attached to its frame.
        StationParts a = placeStation(helper, 1, 2, 1, true, GTValues.MV);
        primeStationEnergy(a, helper, 1, 20, 1050);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            helper.assertTrue(a.station().isFallbackGatewayAttached(),
                    "the gateway must be attached to the formed station");
        });

        // Drain the account BEFORE any input exists, then feed the input: instead of stalling
        // with INSUFFICIENT_FUNDS, the round switches to local fallback through the gateway.
        helper.runAtTickTime(70, () -> ledger.debit(player, ledger.balance(player)));
        helper.runAtTickTime(90, () -> a.station().getInputSlot()
                .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1)));
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(a.station().isTaskActive(),
                    "the station must continue (fallback) instead of stalling, status=" +
                            a.station().getWaitingReason());
            helper.assertTrue(a.station().isFallbackTask(), "the round must run in fallback mode");
            helper.assertTrue(a.station().getTaskPrice() == 0, "a fallback round bills nothing");
            helper.assertTrue(a.station().getTaskTicksTotal() == CloudPricing.BASE_TASK_TICKS * 4,
                    "the fallback round must run the documented x4 duration, got " +
                            a.station().getTaskTicksTotal());
        });

        // The fallback round completes locally: output produced, no Token billed, gateway counts.
        helper.runAtTickTime(560, () -> {
            helper.assertTrue(!a.station().isTaskActive(), "the fallback round must complete");
            helper.assertTrue(ledger.balance(player) == 0,
                    "the fallback round must never bill Tokens");
            helper.assertTrue(!a.station().getOutputSlot().getStackInSlot(0).isEmpty(),
                    "the fallback round must still produce the output");
            helper.assertTrue(a.gateway().getFallbackRounds() == 1,
                    "the gateway must have carried exactly one round, got " +
                            a.gateway().getFallbackRounds());
            System.out.printf("[HC-04] fallback: empty balance -> local x4 round, output kept, zero billing%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 11. order dedup: a save/load mid-round resumes and settles exactly once //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = CLOUD_BATCH + "_11_dedup", timeoutTicks = 700)
    public static void orderDedupResumeAcrossSaveLoad(GameTestHelper helper) {
        StationParts a = placeStation(helper, 1, 2, 1, false, GTValues.LV);
        primeStationEnergy(a, helper, 1, 20, 650);
        CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(helper.getLevel());
        UUID player = UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensure(helper, a);
            a.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            a.station().getInputSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), 1));
        });
        final String[] orderId = new String[1];
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(a.station().isTaskActive(), "the round must be running, status=" +
                    a.station().getWaitingReason());
            orderId[0] = a.station().getTaskOrderId();
            helper.assertTrue(!orderId[0].isEmpty(), "the round must carry its order id");
        });

        // The save/load round-trip mid-round (the P03/P04 block-entity pattern).
        helper.runAtTickTime(120, () -> {
            BlockEntity be = helper.getBlockEntity(a.controllerPos());
            CompoundTag saved = be.saveWithFullMetadata();
            be.load(saved);
        });
        ApiSatelliteStationMachine resumed = station(helper, a.controllerPos());
        helper.runAtTickTime(135, () -> {
            helper.assertTrue(resumed.isTaskActive(), "the round must resume after the reload");
            helper.assertTrue(resumed.getTaskOrderId().equals(orderId[0]),
                    "the resumed round must keep its order id");
            helper.assertTrue(ledger.order(orderId[0]) != null &&
                    ledger.order(orderId[0]).state == CloudTokenLedger.OrderState.HELD,
                    "the order must stay HELD across the reload");
        });

        // The resumed round settles exactly once.
        helper.runAtTickTime(320, () -> {
            helper.assertTrue(!resumed.isTaskActive(), "the resumed round must finish");
            helper.assertTrue(ledger.order(orderId[0]).state == CloudTokenLedger.OrderState.SETTLED,
                    "the order must settle once");
            helper.assertTrue(ledger.totalSpent(player) == CloudPricing.BASE_TASK_PRICE,
                    "the save/load must never double-charge, spent=" + ledger.totalSpent(player));
            helper.assertTrue(ledger.balance(player) ==
                    CloudTokenLedger.WELCOME_BONUS - CloudPricing.BASE_TASK_PRICE,
                    "the balance must reflect exactly one billed round");
            // Dedup by the same order id: nothing may ever be charged or refunded again.
            helper.assertTrue(ledger.hold(player, orderId[0], CloudPricing.BASE_TASK_PRICE) ==
                    CloudTokenLedger.HoldResult.ALREADY_SETTLED,
                    "the settled order id must dedup as ALREADY_SETTLED");
            helper.assertTrue(!ledger.release(orderId[0]),
                    "the settled order must refuse any release");
            System.out.printf("[HC-04] order dedup: mid-round save/load resumes, settles exactly once%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The parts a station test drives. */
    private record StationParts(ApiSatelliteStationMachine station, BlockPos controllerPos,
                                EnergyHatchPartMachine energyHatch,
                                LocalFallbackGatewayMachine gateway) {}

    private static ItemStack boundCard(UUID owner) {
        ItemStack card = new ItemStack(HyperClusterItems.API_CREDENTIAL_CARD.get());
        CredentialCardItem.bind(card, owner, "tester");
        return card;
    }

    /**
     * Places the 5×6×5 lattice-tower station: the 3×3 satellite-frame base at the bottom (the
     * controller at ({@code bx+2}, {@code by}, {@code bz+1}, the LV/MV energy hatch and the
     * maintenance hatch on the base cells, the optional local fallback gateway one cell back),
     * above it the stepped concave 5×5 dish (rim, 3×3 ring, bowl bottom) and the two-block feed
     * horn column at ({@code bx+2}, {@code by+4..5}, {@code bz+2}).
     */
    private static StationParts placeStation(GameTestHelper helper, int bx, int by, int bz,
                                             boolean withGateway, int energyTier) {
        net.minecraft.world.level.block.Block frame = HyperClusterBlocks.SATELLITE_DISH_FRAME.get();
        net.minecraft.world.level.block.Block panel = HyperClusterBlocks.SATELLITE_DISH_PANEL.get();
        net.minecraft.world.level.block.Block horn = HyperClusterBlocks.SATELLITE_FEED_HORN.get();
        // y=0: the 3×3 steel-frame base; the controller sits front-centre, the energy and
        // maintenance hatches flank it, the optional gateway takes the back-left cell.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(bx + x, by, bz + z), frame.defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(bx + 2, by, bz + 1),
                HyperClusterMachines.API_SATELLITE_STATION.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 1, by, bz + 1),
                GTMachines.ENERGY_INPUT_HATCH[energyTier].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 3, by, bz + 1), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        // GTCEu convention: a fresh maintenance hatch starts with ALL problems; fix like a player.
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 3, by, bz + 1)))
                .fixAllMaintenanceProblems();
        helper.setBlock(new BlockPos(bx + 1, by, bz + 3), withGateway ?
                HyperClusterMachines.LOCAL_FALLBACK_GATEWAY.getBlock().defaultBlockState() : frame.defaultBlockState());
        // Build the rising dish independently of the production pattern. Its center panel
        // sits two layers below its outer rim, and a frame mast connects it to the horn.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int radius = Math.max(Math.abs(dx), Math.abs(dz));
                int x = bx + 2 + dx;
                int z = bz + 2 + dz;
                setCell(helper, x, by + 2 + radius, z, panel);
                if (dx == 0 || dz == 0) {
                    setCell(helper, x, by + 1 + radius, z, frame);
                    if (radius == 1) setCell(helper, x, by + 1, z, frame);
                }
            }
        }
        setCell(helper, bx + 2, by + 3, bz + 2, frame);
        setCell(helper, bx + 2, by + 4, bz + 2, horn);
        setCell(helper, bx + 2, by + 5, bz + 2, horn);

        BlockPos controllerPos = new BlockPos(bx + 2, by, bz + 1);
        ApiSatelliteStationMachine station = station(helper, controllerPos);
        EnergyHatchPartMachine energy = (EnergyHatchPartMachine) metaMachine(helper,
                new BlockPos(bx + 1, by, bz + 1));
        LocalFallbackGatewayMachine gateway = null;
        if (withGateway) {
            gateway = (LocalFallbackGatewayMachine) metaMachine(helper,
                    new BlockPos(bx + 1, by, bz + 3));
        }
        return new StationParts(station, controllerPos, energy, gateway);
    }

    private static void setCell(GameTestHelper helper, int x, int y, int z,
                                net.minecraft.world.level.block.Block block) {
        helper.setBlock(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static void primeStationEnergy(StationParts parts, GameTestHelper helper, long first,
                                           long every, long last) {
        parts.energyHatch().energyContainer.addEnergy(parts.energyHatch().energyContainer
                .getEnergyCapacity());
        for (long t = first; t <= last; t += every) {
            helper.runAtTickTime(t, () -> parts.energyHatch().energyContainer
                    .addEnergy(parts.energyHatch().energyContainer.getEnergyCapacity()));
        }
    }

    /**
     * Mirrors the DatacenterTests synchronous structure-forming helper, with a diagnostic print
     * of the pattern error when the structure refuses to form.
     */
    private static void ensure(GameTestHelper helper, StationParts parts) {
        var controller = parts.station();
        java.util.concurrent.locks.Lock lock = controller.getPatternLock();
        lock.lock();
        try {
            if (controller.isFormed()) return;
            if (controller.checkPatternWithLock()) {
                controller.setFlipped(controller.getMultiblockState().isNeededFlip());
                controller.onStructureFormed();
            } else {
                var state = controller.getMultiblockState();
                BlockPos origin = helper.absolutePos(BlockPos.ZERO);
                BlockPos failPos = state.getPos();
                System.out.printf(
                        "[HC-04-DIAG] station not formed: origin=%s failWorld=%s failRel=%s found=%s err=%s%n",
                        origin, failPos,
                        failPos == null ? null : failPos.subtract(origin),
                        failPos == null ? null : helper.getLevel().getBlockState(failPos),
                        state.error == null ? "none" : state.error.getErrorInfo().getString());
            }
        } finally {
            lock.unlock();
        }
    }

    private static ApiSatelliteStationMachine station(GameTestHelper helper, BlockPos pos) {
        Object machine = metaMachine(helper, pos);
        helper.assertTrue(machine instanceof ApiSatelliteStationMachine,
                "expected an api_satellite_station controller at " + pos);
        return (ApiSatelliteStationMachine) machine;
    }

    private static Object metaMachine(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof IMachineBlockEntity,
                "expected a machine block entity at " + pos);
        return ((IMachineBlockEntity) Objects.requireNonNull(be)).getMetaMachine();
    }
}
