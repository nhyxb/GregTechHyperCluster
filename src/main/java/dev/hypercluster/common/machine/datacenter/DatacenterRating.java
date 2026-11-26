package dev.hypercluster.common.machine.datacenter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * 0.3 · IV datacenter rating math — the PUE settlement contract of the air-cooled compute rack.
 *
 * <p>
 * The settlement formula (IMPLEMENTATION_PLAN.md, 算力和机房契约):
 *
 * <pre>{@code
 * 总耗电 = IT 耗电 + 基础制冷耗电 × 环境系数 × 气流系数
 * PUE    = 总耗电 / IT 耗电          (IT 零负载时显示待机功耗)
 * }</pre>
 *
 * <p>
 * Constants chosen so the goal's reference points hold exactly in a neutral environment:
 * <ul>
 * <li>Tier I (default): airflow 1.00 → PUE = 1 + 0.80 = <b>1.80</b>;</li>
 * <li>Tier II (raised floor + open channels + a running precision CRAC): airflow 0.5625 →
 * PUE = 1 + 0.45 = <b>1.45</b>;</li>
 * <li>natural cold source (snowy/frozen/deep-ocean biome, or Y &lt; 20): environment ×0.6
 * (cooling −40%); the Nether or a hot desert: environment ×2.0 (cooling doubled).</li>
 * </ul>
 *
 * All functions are pure and GameTest-assertable.
 */
public final class DatacenterRating {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Rack nameplate constants //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Compute blades the Tier I rack accepts in its GUI slots. */
    public static final int BLADE_SLOTS = 4;

    /** Computation one blade contributes: 4 × 6 = 24 CWU/t at full load (Tier I nameplate). */
    public static final int CWU_PER_BLADE = 6;

    /** IT power one blade draws per tick (EU/t). */
    public static final long BLADE_EU_T = 256;

    /**
     * Base cooling power as a fraction of IT power ({@code 基础制冷耗电}). 0.8 keeps Tier I at
     * PUE 1.80 and Tier II (airflow 0.5625) at 1.45.
     */
    public static final double BASE_COOLING_RATIO = 0.80;

    /** Idle draw of a formed rack with no blades inserted (the standby line in the UI). */
    public static final long STANDBY_EU_T = 64;

    /** Airflow coefficient of a Tier I machine room (default, no raised-floor plenum). */
    public static final double AIRFLOW_TIER_I = 1.0;

    /** Airflow coefficient of a Tier II room (raised-floor cold plenum + containment). */
    public static final double AIRFLOW_TIER_II = 0.5625;

    /** Natural cold source multiplier: cooling power ×0.6 (−40%). */
    public static final double ENVIRONMENT_COLD = 0.6;

    /** Hostile environment multiplier: cooling power ×2.0 (Nether, hot desert). */
    public static final double ENVIRONMENT_HOT = 2.0;

    /** Green-snack-pack stability bonus: the whole room draws 5% less EU. */
    public static final double SNACK_BONUS = 0.05;

    private DatacenterRating() {}

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Pure settlement math //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** IT power draw of {@code blades} inserted blades (EU/t); 0 for an empty rack. */
    public static long itEUt(int blades) {
        return Math.max(0, blades) * BLADE_EU_T;
    }

    /** Total CWU/t the rack can offer with {@code blades} inserted blades (0..24, linear). */
    public static int maxCWUt(int blades) {
        return Math.max(0, blades) * CWU_PER_BLADE;
    }

    /** Base cooling power (EU/t) for an IT load of {@code itEUt} (0 when idle). */
    public static long baseCoolingEUt(long itEUt) {
        return Math.round(itEUt * BASE_COOLING_RATIO);
    }

    /** Settled cooling power: 基础制冷耗电 × 环境系数 × 气流系数 (EU/t). */
    public static long coolingEUt(long itEUt, double environment, double airflow) {
        return Math.round(baseCoolingEUt(itEUt) * environment * airflow);
    }

    /**
     * Total power draw of the rack (EU/t). With no blades the rack shows/draws the standby line
     * (optionally with the snack bonus); with blades it is
     * {@code IT + cooling × environment × airflow}, optionally ×0.95 with a snack pack.
     */
    public static long totalEUt(int blades, double environment, double airflow, boolean snackBonus) {
        if (blades <= 0) {
            return snackApply(STANDBY_EU_T, snackBonus);
        }
        long it = itEUt(blades);
        return snackApply(it + coolingEUt(it, environment, airflow), snackBonus);
    }

    private static long snackApply(long eu, boolean snackBonus) {
        if (snackBonus && DatacenterConfigs.snackPackBonus) {
            return Math.round(eu * (1.0 - SNACK_BONUS));
        }
        return eu;
    }

    /**
     * Live PUE = 总耗电 / IT 耗电. Returns {@code -1} for an idle rack (IT zero) — the UI then
     * shows the standby power instead of a PUE number.
     */
    public static double pue(int blades, double environment, double airflow) {
        if (blades <= 0) {
            return -1.0;
        }
        long it = itEUt(blades);
        return (it + coolingEUt(it, environment, airflow)) / (double) it;
    }

    /** Airflow coefficient of the given machine-room tier (1 = Tier I, 2 = Tier II). */
    public static double airflowMultiplier(int roomTier) {
        return roomTier >= 2 ? AIRFLOW_TIER_II : AIRFLOW_TIER_I;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Environment coefficient (环境冷源) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Dimension part of the environment coefficient: the Nether doubles cooling power. */
    public static double dimensionMultiplier(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) ? ENVIRONMENT_HOT : 1.0;
    }

    /** Biome part of the environment coefficient: natural cold sources cut cooling by 40%. */
    public static double biomeMultiplier(Holder<Biome> biome, BlockPos pos) {
        if (pos.getY() < 20) {
            return ENVIRONMENT_COLD;
        }
        Biome value = biome.value();
        Biome.Precipitation precipitation = value.getPrecipitationAt(pos);
        if (precipitation == Biome.Precipitation.SNOW) {
            return ENVIRONMENT_COLD;
        }
        if (precipitation == Biome.Precipitation.NONE && value.getModifiedClimateSettings().temperature() >= 1.5F) {
            return ENVIRONMENT_HOT;
        }
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isPresent() && key.get().location().getPath().contains("deep_ocean")) {
            return ENVIRONMENT_COLD;
        }
        return 1.0;
    }

    /** Full environment coefficient at a world position: dimension × biome × altitude rules. */
    public static double environmentMultiplier(Level level, BlockPos pos) {
        double dimension = dimensionMultiplier(level.dimension());
        Holder<Biome> biome = level.getBiome(pos);
        return dimension * biomeMultiplier(biome, pos);
    }
}
