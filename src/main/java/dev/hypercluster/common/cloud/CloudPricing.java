package dev.hypercluster.common.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 0.4 · closed-source cloud pricing and signal physics (pure, GameTest-assertable statics).
 *
 * <p>
 * <b>Integer Token billing.</b> Every price is an integer number of Tokens. The canonical price
 * pipeline, in the exact order it is applied (each factor multiplies the running total):
 *
 * <ol>
 * <li><b>Base</b> price of the service task: {@value #BASE_TASK_PRICE} Tokens for one round.</li>
 * <li><b>Inference tier</b> (推理档位): Medium ×1, Extra High ×2.</li>
 * <li><b>Prompt caching</b> (命中缓存): from the 2nd consecutive round with the identical cache
 * key the rate drops to <b>10%</b> (×0.1). Any dimension change of the key restarts the
 * billing at the full rate.</li>
 * <li><b>Batch mode</b> (勾选批处理): Token bill ×0.5. <b>Stacking rule:</b> caching and batch
 * stack multiplicatively — a cached batch round costs base × 0.1 × 0.5 = <b>5%</b> of the base
 * price. Batch never invalidates the cache and vice versa.</li>
 * <li><b>High altitude bonus</b> ({@code Y > 120}): a small rate optimization, ×0.95.</li>
 * </ol>
 *
 * <p>
 * <b>Duration pipeline</b> (again in order):
 *
 * <ol>
 * <li><b>Base</b> duration of the service task: {@value #BASE_TASK_TICKS} ticks.</li>
 * <li><b>Inference tier</b>: Medium ×1, Extra High ×0.5 (faster).</li>
 * <li><b>Batch mode</b>: ×2 (the batch waits for its peers).</li>
 * <li><b>Weather attenuation</b> (信号衰减): clear ×1.0, rain ×1.5, thunder ×2.0.</li>
 * <li><b>High altitude bonus</b> ({@code Y > 120}): ×0.9 (small latency optimization).</li>
 * <li><b>Local fallback mode</b>: ×4 — the fallback gateway deliberately runs the same task
 * locally at degraded speed.</li>
 * </ol>
 *
 * <p>
 * Fallback rounds are never billed in Tokens at all — they burn local EU instead.
 */
public final class CloudPricing {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Token prices //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Base price of one demo closed-source service round, in Tokens. */
    public static final long BASE_TASK_PRICE = 25_000L;

    /** Sale price of one high-purity mining dataset uploaded at a station. */
    public static final long MINING_DATASET_SALE_PRICE = 50_000L;

    /** Sale price of one high-purity chemical dataset uploaded at a station. */
    public static final long CHEMICAL_DATASET_SALE_PRICE = 50_000L;

    /** Cache-hit rate: a consecutive round with the identical cache key costs 10%. */
    public static final double CACHE_RATE = 0.1;

    /** Batch-mode rate: Token bill ×0.5. Stacks multiplicatively with the cache discount. */
    public static final double BATCH_RATE = 0.5;

    /** High-altitude ({@code Y > 120}) rate optimization: ×0.95. */
    public static final double HIGH_ALTITUDE_RATE = 0.95;

    /** The altitude (exclusive) above which the station earns the small latency/rate bonus. */
    public static final int HIGH_ALTITUDE_Y = 120;

    /** Base working time of one demo service round, in ticks. */
    public static final int BASE_TASK_TICKS = 100;

    /** Rain attenuation of the uplink (duration ×1.5). */
    public static final double RAIN_DURATION = 1.5;

    /** Thunder attenuation of the uplink (duration ×2.0). */
    public static final double THUNDER_DURATION = 2.0;

    /** High-altitude latency optimization (duration ×0.9). */
    public static final double HIGH_ALTITUDE_DURATION = 0.9;

    /** Local fallback degradation: the same task runs locally ×4 slower. */
    public static final double FALLBACK_DURATION = 4.0;

    /** EU/t the station draws while a cloud Medium round runs (LV hatch suffices). */
    public static final long MEDIUM_EU_T = 30;

    /** EU/t while a cloud Extra High round runs (MV hatch range). */
    public static final long EXTRA_HIGH_EU_T = 120;

    /** EU/t while the local fallback gateway runs the task (local compute, no Tokens). */
    public static final long FALLBACK_EU_T = 60;

    private CloudPricing() {}

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Inference tiers (推理档位) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The two user-selectable inference tiers of the closed-source service. */
    public enum InferenceTier {

        /** ×1 price, ×1 duration, {@value #MEDIUM_EU_T} EU/t. */
        MEDIUM(1.0, 1.0),
        /** ×2 price, ×0.5 duration (faster), {@value #EXTRA_HIGH_EU_T} EU/t. */
        EXTRA_HIGH(2.0, 0.5);

        private final double priceRate;
        private final double durationRate;

        InferenceTier(double priceRate, double durationRate) {
            this.priceRate = priceRate;
            this.durationRate = durationRate;
        }

        public double priceRate() {
            return priceRate;
        }

        public double durationRate() {
            return durationRate;
        }

        public long eut() {
            return this == MEDIUM ? MEDIUM_EU_T : EXTRA_HIGH_EU_T;
        }

        public static InferenceTier byOrdinal(int ordinal) {
            return ordinal == EXTRA_HIGH.ordinal() ? EXTRA_HIGH : MEDIUM;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Price pipeline //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The integer Token price of one service round.
     *
     * @param base     the service's base price (Tokens)
     * @param tier     the selected inference tier (Medium ×1 / Extra High ×2)
     * @param cacheHit true if this round's cache key equals the account's previous round key
     * @param batch    true if batch mode is checked (bill ×0.5)
     * @param highAlt  true if the station sits above {@value #HIGH_ALTITUDE_Y} (bill ×0.95)
     */
    public static long taskPrice(long base, InferenceTier tier, boolean cacheHit, boolean batch,
                                 boolean highAlt) {
        double price = base * tier.priceRate();
        if (cacheHit) {
            price *= CACHE_RATE; // 10% from the 2nd consecutive round of the same cache key.
        }
        if (batch) {
            price *= BATCH_RATE; // ×0.5; stacks multiplicatively with the cache hit (→ 5%).
        }
        if (highAlt) {
            price *= HIGH_ALTITUDE_RATE; // ×0.95 rate optimization above Y=120.
        }
        return Math.max(1L, Math.round(price));
    }

    /** The demo service's price for the given round conditions ({@link #BASE_TASK_PRICE}). */
    public static long demoTaskPrice(InferenceTier tier, boolean cacheHit, boolean batch,
                                     boolean highAlt) {
        return taskPrice(BASE_TASK_PRICE, tier, cacheHit, batch, highAlt);
    }

    /**
     * The working time of one service round, in ticks.
     *
     * @param base     the service's base duration (ticks)
     * @param tier     Medium ×1 / Extra High ×0.5
     * @param batch    batch mode ×2
     * @param weather  the uplink attenuation (1.0 clear, 1.5 rain, 2.0 thunder)
     * @param highAlt  true for the ×0.9 latency bonus above Y=120
     * @param fallback true when the local fallback gateway runs the task (×4, degraded)
     */
    public static int taskTicks(int base, InferenceTier tier, boolean batch, double weather,
                                boolean highAlt, boolean fallback) {
        double ticks = base * tier.durationRate();
        if (batch) {
            ticks *= 2.0;
        }
        ticks *= weather;
        if (highAlt) {
            ticks *= HIGH_ALTITUDE_DURATION;
        }
        if (fallback) {
            ticks *= FALLBACK_DURATION;
        }
        return Math.max(1, (int) Math.round(ticks));
    }

    /** The demo service's duration for the given round conditions. */
    public static int demoTaskTicks(InferenceTier tier, boolean batch, double weather,
                                    boolean highAlt, boolean fallback) {
        return taskTicks(BASE_TASK_TICKS, tier, batch, weather, highAlt, fallback);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Signal physics: sky / weather / altitude //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The feed horn must see open sky: any block above it that blocks motion occludes the uplink
     * (拒绝联机). Glass and other transparent blocks do not occlude.
     */
    public static boolean hasOpenSky(Level level, BlockPos hornPos) {
        for (int y = hornPos.getY() + 1; y <= level.getMaxBuildHeight(); y++) {
            BlockPos probe = new BlockPos(hornPos.getX(), y, hornPos.getZ());
            if (level.getBlockState(probe).isSolidRender(level, probe)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pure weather attenuation of the uplink: clear ×1.0, rain ×1.5, thunder ×2.0. Thunder
     * counts as rain (the storm brings both).
     */
    public static double weatherMultiplier(boolean raining, boolean thundering) {
        if (thundering) {
            return THUNDER_DURATION;
        }
        return raining ? RAIN_DURATION : 1.0;
    }

    /** The live weather attenuation at the station (rain must actually reach the horn). */
    public static double weatherAt(Level level, BlockPos hornPos) {
        boolean thundering = level.isThundering();
        boolean raining = level.isRainingAt(hornPos.above());
        return weatherMultiplier(raining, thundering);
    }

    /** True if the controller position earns the small high-altitude bonus ({@code Y > 120}). */
    public static boolean highAltitudeBonus(BlockPos pos) {
        return pos.getY() > HIGH_ALTITUDE_Y;
    }
}
