package dev.hypercluster.common.cloud;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.4 · the server-side Token ledger ({@code SavedData}, one per world save, stored in the
 * overworld's data storage so every satellite station — in any dimension — shares it).
 *
 * <p>
 * <b>Accounts.</b> One account per player UUID inside the same save: a {@code long} integer
 * balance, a one-time welcome-bonus flag, and the cumulative spent amount (which decides the
 * account's concurrency tier). Every mutation happens server-side through this class.
 *
 * <p>
 * <b>Welcome bonus.</b> {@link #claimWelcomeBonus(UUID)} grants the one-time
 * {@value #WELCOME_BONUS} experience Tokens the first time an account is opened — at ANY
 * satellite station, in any dimension. The flag is persisted with the ledger, so save/reload,
 * station rebuilds and station variety can never double-grant (禁止重复发奖).
 *
 * <p>
 * <b>Order book.</b> Every paid round is an {@link Order} with a unique order id:
 * <ol>
 * <li>{@link #hold(UUID, String, long, HoldResult)} — the pre-deduction (预扣) at round start.
 * Atomically checks the balance, deducts, and books the order as {@code HELD}. An order id is
 * deduplicated: re-holding a known id never charges twice (a resumed mid-task order simply
 * keeps its existing hold).</li>
 * <li>{@link #settle(String)} — the round completed; the hold becomes {@code SETTLED} and the
 * spent total grows (consumption drives the concurrency tier).</li>
 * <li>{@link #release(String)} — the round failed or was cancelled; a {@code HELD} order is
 * refunded exactly once (balance += amount, state → {@code REFUNDED}). A {@code SETTLED} order
 * is never refunded and a {@code REFUNDED} order is never refunded twice (不吞单不双扣).</li>
 * </ol>
 * The order book shares the ledger's persistence boundary (one SavedData, one NBT), and is
 * bounded to {@value #MAX_ORDERS} entries — the oldest terminal orders are evicted first, so the
 * save grows with bounded task volume, not forever.
 *
 * <p>
 * <b>Concurrency (429).</b> While a cloud round runs, its account holds one session
 * ({@link #beginSession(UUID)} / {@link #endSession(UUID)}). The account's concurrent-session
 * ceiling comes from its cumulative spending: Tier 1 ({@value #TIER2_THRESHOLD} Tokens) allows
 * {@value #TIER1_SLOTS} session, up to Tier 5 ({@value #TIER5_THRESHOLD}+ Tokens) with
 * {@value #TIER5_SLOTS} sessions. A session request over the ceiling is refused — the caller
 * reports a 429 pause. Sessions are runtime state and intentionally <b>not</b> persisted: a
 * restart clears them instead of leaving stale locks.
 *
 * <p>
 * <b>Prompt cache pointer.</b> The last cache key plus the consecutive-hit count per account is
 * runtime state as well: a restart starts every account's cache cold (no stale discounts), and
 * any dimension change of the key restarts the billing at the full rate.
 */
public final class CloudTokenLedger extends SavedData {

    private static final String DATA_NAME = "hypercluster_cloud_ledger";

    /** The one-time experience grant for a fresh account, in Tokens. */
    public static final long WELCOME_BONUS = 5_000_000L;

    /** Orders kept in the book (oldest terminal ones evicted beyond this bound). */
    public static final int MAX_ORDERS = 512;

    // Concurrency tiers by cumulative spent Tokens (Tier 1..5 -> 1..5 concurrent sessions).
    public static final long TIER2_THRESHOLD = 500_000L;
    public static final long TIER3_THRESHOLD = 2_000_000L;
    public static final long TIER4_THRESHOLD = 5_000_000L;
    public static final long TIER5_THRESHOLD = 20_000_000L;
    public static final int TIER1_SLOTS = 1;
    public static final int TIER5_SLOTS = 5;

    /** Order lifecycle. HELD = pre-deducted, SETTLED = paid final, REFUNDED = released. */
    public enum OrderState {
        HELD,
        SETTLED,
        REFUNDED
    }

    /** Result of a hold attempt (唯一订单 ID 去重 + 余额不足拒绝). */
    public enum HoldResult {
        /** Fresh hold booked and deducted. */
        GRANTED,
        /** Known order already HELD: nothing deducted (resume), caller proceeds. */
        ALREADY_HELD,
        /** Known order already SETTLED (paid): never charged again, caller proceeds unpaid. */
        ALREADY_SETTLED,
        /** Known order already REFUNDED: terminal, the caller must NOT proceed on this id. */
        ALREADY_RELEASED,
        /** Balance below the price: refused, nothing deducted. */
        INSUFFICIENT
    }

    /** One account of a player UUID inside this save. */
    public static final class Account {

        public long balance;
        public boolean welcomeClaimed;
        public long totalSpent;

        Account() {}

        Account(CompoundTag tag) {
            this.balance = tag.getLong("balance");
            this.welcomeClaimed = tag.getBoolean("welcome");
            this.totalSpent = tag.getLong("spent");
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("balance", balance);
            tag.putBoolean("welcome", welcomeClaimed);
            tag.putLong("spent", totalSpent);
            return tag;
        }
    }

    /** One order of the order book. */
    public static final class Order {

        public final String id;
        public final UUID owner;
        public final long amount;
        public OrderState state;

        Order(String id, UUID owner, long amount, OrderState state) {
            this.id = id;
            this.owner = owner;
            this.amount = amount;
            this.state = state;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();

    // Runtime-only state: sessions (429) and the prompt-cache pointer.
    private final Map<UUID, Integer> sessions = new HashMap<>();
    private final Map<UUID, String> lastCacheKey = new HashMap<>();
    private final Map<UUID, Integer> cacheHits = new HashMap<>();

    private CloudTokenLedger() {}

    /**
     * The deserializer half of the SavedData factory. Public so GameTests can assert the
     * save/load round-trip of the ledger (the same serialization SavedData persists with).
     */
    public CloudTokenLedger(CompoundTag tag) {
        CompoundTag accountTag = tag.getCompound("accounts");
        for (String key : accountTag.getAllKeys()) {
            try {
                accounts.put(UUID.fromString(key), new Account(accountTag.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // A malformed key in the save is skipped, never crashes the ledger load.
            }
        }
        CompoundTag orderTag = tag.getCompound("orders");
        for (String key : orderTag.getAllKeys()) {
            CompoundTag entry = orderTag.getCompound(key);
            // putUUID stores an int array — read it back with getUUID (never getString).
            if (!entry.hasUUID("owner")) {
                continue; // a malformed entry is skipped, never crashes the ledger load.
            }
            orders.put(key, new Order(key, entry.getUUID("owner"), entry.getLong("amount"),
                    OrderState.values()[entry.getInt("state")]));
        }
    }

    /**
     * The shared ledger of the save: always the overworld's storage, so every station in every
     * dimension sees the same accounts, orders and flags.
     */
    public static CloudTokenLedger getOrCreate(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                CloudTokenLedger::new,
                CloudTokenLedger::new,
                DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag accountTag = new CompoundTag();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            accountTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("accounts", accountTag);
        CompoundTag orderTag = new CompoundTag();
        for (Map.Entry<String, Order> entry : orders.entrySet()) {
            CompoundTag order = new CompoundTag();
            order.putUUID("owner", entry.getValue().owner);
            order.putLong("amount", entry.getValue().amount);
            order.putInt("state", entry.getValue().state.ordinal());
            orderTag.put(entry.getKey(), order);
        }
        tag.put("orders", orderTag);
        return tag;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Accounts //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The account of the player (created empty on first touch). */
    public synchronized Account account(UUID player) {
        return accounts.computeIfAbsent(player, key -> {
            setDirty();
            return new Account();
        });
    }

    /** Opens the account at any station: grants the welcome bonus exactly once, ever. */
    public synchronized boolean claimWelcomeBonus(UUID player) {
        Account account = account(player);
        if (account.welcomeClaimed) {
            return false;
        }
        account.welcomeClaimed = true;
        account.balance += WELCOME_BONUS;
        setDirty();
        return true;
    }

    /** True once the account has claimed its welcome bonus (persisted across restarts). */
    public synchronized boolean isWelcomeClaimed(UUID player) {
        return account(player).welcomeClaimed;
    }

    public synchronized long balance(UUID player) {
        return account(player).balance;
    }

    public synchronized long totalSpent(UUID player) {
        return account(player).totalSpent;
    }

    /** Direct credit (dataset sales, card deposits). Server-side only, always integer. */
    public synchronized void credit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        account(player).balance += amount;
        setDirty();
    }

    /** Direct debit (card withdrawals). Refuses below-zero balances. */
    public synchronized boolean debit(UUID player, long amount) {
        if (amount <= 0) {
            return true;
        }
        Account account = account(player);
        if (account.balance < amount) {
            return false;
        }
        account.balance -= amount;
        setDirty();
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Concurrency tier & sessions (429) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The concurrency tier (1..5) from the cumulative spent Tokens. */
    public static int tierForSpent(long totalSpent) {
        if (totalSpent >= TIER5_THRESHOLD) return 5;
        if (totalSpent >= TIER4_THRESHOLD) return 4;
        if (totalSpent >= TIER3_THRESHOLD) return 3;
        if (totalSpent >= TIER2_THRESHOLD) return 2;
        return 1;
    }

    /** The concurrent-session ceiling of the account's tier (1..5). */
    public static int slotsForSpent(long totalSpent) {
        return TIER1_SLOTS - 1 + tierForSpent(totalSpent);
    }

    /** The account's live tier and ceiling. */
    public synchronized int accountTier(UUID player) {
        return tierForSpent(totalSpent(player));
    }

    /** Concurrent cloud sessions the account holds right now. */
    public synchronized int sessionCount(UUID player) {
        return sessions.getOrDefault(player, 0);
    }

    /**
     * Requests one concurrent cloud session for the account. Refused (false, a 429) while the
     * account's tier ceiling is already saturated.
     */
    public synchronized boolean beginSession(UUID player) {
        int current = sessionCount(player);
        if (current >= slotsForSpent(totalSpent(player))) {
            return false; // 429
        }
        sessions.put(player, current + 1);
        return true;
    }

    /** Releases one session of the account (never below zero). */
    public synchronized void endSession(UUID player) {
        int current = sessions.getOrDefault(player, 0);
        if (current <= 1) {
            sessions.remove(player);
        } else {
            sessions.put(player, current - 1);
        }
    }

    /** Drops every runtime session (used on world unload; sessions never persist). */
    public synchronized void clearSessions() {
        sessions.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Order book: hold / settle / release //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The order with the given id, or null. */
    public synchronized Order order(String id) {
        return orders.get(id);
    }

    /**
     * The pre-deduction at round start. Books a fresh {@code HELD} order and deducts the price
     * atomically; deduplicates by order id (never double-charges one order). Refused below the
     * required balance.
     */
    public synchronized HoldResult hold(UUID player, String orderId, long amount) {
        Order existing = orders.get(orderId);
        if (existing != null) {
            return switch (existing.state) {
                case HELD -> HoldResult.ALREADY_HELD;
                case SETTLED -> HoldResult.ALREADY_SETTLED;
                case REFUNDED -> HoldResult.ALREADY_RELEASED;
            };
        }
        Account account = account(player);
        if (account.balance < amount) {
            return HoldResult.INSUFFICIENT; // 余额不足拒绝启动, nothing deducted.
        }
        account.balance -= amount;
        orders.put(orderId, new Order(orderId, player, amount, OrderState.HELD));
        evictBounded();
        setDirty();
        return HoldResult.GRANTED;
    }

    /**
     * The round completed: the held amount becomes final spend. Idempotent — a settled order
     * stays settled (no double settle). Returns true when this call settled it.
     */
    public synchronized boolean settle(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || order.state != OrderState.HELD) {
            return false;
        }
        order.state = OrderState.SETTLED;
        account(order.owner).totalSpent += order.amount;
        setDirty();
        return true;
    }

    /**
     * The round failed or was cancelled: a {@code HELD} order is released exactly once
     * (balance += amount, state → REFUNDED). Settled orders are never refunded; released
     * orders are never refunded twice. Returns true when this call released it.
     */
    public synchronized boolean release(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || order.state != OrderState.HELD) {
            return false;
        }
        order.state = OrderState.REFUNDED;
        account(order.owner).balance += order.amount;
        setDirty();
        return true;
    }

    /** Bounded book: evicts the oldest terminal orders past {@value #MAX_ORDERS}. */
    private void evictBounded() {
        if (orders.size() <= MAX_ORDERS) {
            return;
        }
        Iterator<Map.Entry<String, Order>> iterator = orders.entrySet().iterator();
        while (orders.size() > MAX_ORDERS && iterator.hasNext()) {
            Order order = iterator.next().getValue();
            if (order.state != OrderState.HELD) {
                iterator.remove(); // HELD orders are never evicted — they may still be refunded.
                setDirty();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Prompt-cache pointer (runtime) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * True when this round's key equals the account's previous round key — the 2nd and further
     * consecutive rounds of one key hit the 10% cache rate. Pure query: never books anything.
     */
    public synchronized boolean isCacheHit(UUID player, String cacheKey) {
        return cacheKey.equals(lastCacheKey.get(player));
    }

    /**
     * Books the cache pointer once the round actually started (its order is held): the key
     * becomes the account's pointer and the consecutive-hit count grows. A different key
     * restarts the count at zero (任一维度变化立即失效重算).
     */
    public synchronized void recordCacheKey(UUID player, String cacheKey) {
        if (cacheKey.equals(lastCacheKey.get(player))) {
            cacheHits.merge(player, 1, Integer::sum);
        } else {
            lastCacheKey.put(player, cacheKey);
            cacheHits.put(player, 0);
        }
    }

    /** Consecutive hits booked for the account's current key (display + tests). */
    public synchronized int cacheHits(UUID player) {
        return cacheHits.getOrDefault(player, 0);
    }

    /** Drops the runtime cache pointers (used on world unload; the cache never persists). */
    public synchronized void clearCaches() {
        lastCacheKey.clear();
        cacheHits.clear();
    }

    /**
     * Builds the prompt-cache key of a round:
     * {@code (playerUuid, machinePos, recipeId, modelId, tier)} — any dimension change
     * invalidates the discount and restarts the billing at the full rate.
     */
    public static String cacheKey(UUID player, String machinePos, String recipeId, String modelId,
                                  CloudPricing.InferenceTier tier) {
        return player + "|" + machinePos + "|" + recipeId + "|" + modelId + "|" + tier.name();
    }
}
