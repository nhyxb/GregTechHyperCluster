package dev.hypercluster.common.machine.datacenter;

/**
 * 0.6 · Tier III liquid-cooling datacenter rating contract.
 *
 * <p>
 * The 0.6 cold-plate rack is the LuV step above the 0.3 air-cooled rack. It keeps the 0.3
 * settlement shape ({@code 总耗电 = IT 耗电 + 基础制冷耗电 × 环境系数 × 气流系数}) but adds the two
 * hard budgets a training cluster has to respect:
 *
 * <ul>
 * <li><b>Switch bandwidth</b> (交换机带宽上限) — the CWU/t one rack may hand out per tick
 * through the optical switch. Asking for more is not an error: the surplus simply queues.</li>
 * <li><b>VRAM budget</b> (显存容量预算) — how large a training job the rack's blades can hold. A
 * job that needs more VRAM than the rack has must refuse to start (OOM) instead of silently
 * degrading.</li>
 * </ul>
 *
 * <p>
 * All functions are pure and GameTest-assertable; the machine only feeds them live state.
 */
public final class LiquidDatacenterRating {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Rack nameplate constants //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Liquid blade slots of the Tier III rack. */
    public static final int BLADE_SLOTS = 4;

    /** Computation one LuV liquid blade contributes: 4 × 16 = 64 CWU/t at full load. */
    public static final int CWU_PER_BLADE = 16;

    /** IT power one liquid blade draws per tick (EU/t). */
    public static final long BLADE_EU_T = 1024;

    /** Idle draw of a formed liquid rack with no blades inserted. */
    public static final long STANDBY_EU_T = 128;

    /**
     * Base cooling power as a fraction of IT power. 0.20 is what makes a Tier III room settle at
     * PUE 1.20 in a neutral environment: {@code 1 + 0.20 = 1.20}.
     */
    public static final double BASE_COOLING_RATIO = 0.20;

    /** Airflow coefficient of a Tier III cold-plate room (liquid cooling needs no air handling). */
    public static final double AIRFLOW_TIER_III = 1.0;

    /** The PUE a Tier III room settles at in a neutral environment ({@code 1 + 0.20}). */
    public static final double PUE_TIER_III = 1.20;

    /** Switch bandwidth ceiling one rack may hand out per tick (CWU/t). */
    public static final int SWITCH_BANDWIDTH_LIMIT = 64;

    /**
     * 0.6.1 — blades no longer carry VRAM. Cluster memory is a separate budget supplied by
     * {@code hypercluster:vram_storage_rack}'s HBM modules, so a compute rack is pure compute.
     */
    public static final int VRAM_PER_BLADE = 0;

    /** Coolant the rack drinks per tick while providing computation (mB/t). */
    public static final int COOLANT_PER_TICK = 1;

    /** Paused-for-cold ticks after which the rack derates to 0 CWU/t (no coolant = no compute). */
    public static final int COOLANT_STARVE_TICKS = 20;

    /** Cold-plate coolant temperatures: distilled water works, PCB coolant is the premium fill. */
    public static final double DISTILLED_WATER_EFFICIENCY = 1.0;

    /** PCB coolant keeps the cold plates cooler: 15% less cooling power. */
    public static final double PCB_COOLANT_EFFICIENCY = 0.85;

    /** The one time the acceptance asks to prove a single rack drives the vanilla research loop. */
    public static final int RESEARCH_STATION_CWUT = 32;

    private LiquidDatacenterRating() {}

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Pure settlement math //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** IT power draw of {@code blades} inserted blades (EU/t); 0 for an empty rack. */
    public static long itEUt(int blades) {
        return Math.max(0, blades) * BLADE_EU_T;
    }

    /** Total CWU/t the rack can offer with {@code blades} inserted blades (0..64, linear). */
    public static int maxCWUt(int blades) {
        return Math.max(0, blades) * CWU_PER_BLADE;
    }

    /**
     * Total VRAM (TB) the inserted blades bring online — always 0 since 0.6.1, kept so callers
     * read the hardware that actually holds memory ({@code VramStorageRackMachine}).
     */
    public static int vramCapacity(int blades) {
        return Math.max(0, blades) * VRAM_PER_BLADE;
    }

    /** Base cooling power (EU/t) for an IT load of {@code itEUt} (0 when idle). */
    public static long baseCoolingEUt(long itEUt) {
        return Math.round(itEUt * BASE_COOLING_RATIO);
    }

    /** Settled cooling power: 基础制冷耗电 × 环境系数 × 气流系数 × 冷板效率 (EU/t). */
    public static long coolingEUt(long itEUt, double environment, double airflow, double coolantEfficiency) {
        return Math.round(baseCoolingEUt(itEUt) * environment * airflow * coolantEfficiency);
    }

    /** Total power draw of the rack (EU/t), including the optional snack-pack bonus. */
    public static long totalEUt(int blades, double environment, double airflow, double coolantEfficiency,
                                boolean snackBonus) {
        if (blades <= 0) {
            return snackApply(STANDBY_EU_T, snackBonus);
        }
        long it = itEUt(blades);
        return snackApply(it + coolingEUt(it, environment, airflow, coolantEfficiency), snackBonus);
    }

    private static long snackApply(long eu, boolean snackBonus) {
        if (snackBonus && DatacenterConfigs.snackPackBonus) {
            return Math.round(eu * (1.0 - DatacenterRating.SNACK_BONUS));
        }
        return eu;
    }

    /** Live PUE = 总耗电 / IT 耗电; {@code -1} for an idle rack (the UI shows standby instead). */
    public static double pue(int blades, double environment, double airflow, double coolantEfficiency) {
        if (blades <= 0) {
            return -1.0;
        }
        long it = itEUt(blades);
        return (it + coolingEUt(it, environment, airflow, coolantEfficiency)) / (double) it;
    }

    /** True when the requested CWU/t fits the rack's nameplate switch bandwidth. */
    public static boolean withinBandwidth(int requested) {
        return requested >= 0 && requested <= SWITCH_BANDWIDTH_LIMIT;
    }

    /** True when the pool holds {@code hostVram} TB for a job needing {@code requiredVram} TB. */
    public static boolean fitsVram(int hostVram, int requiredVram) {
        return Math.max(0, hostVram) >= Math.max(0, requiredVram);
    }

    /** Convenience: do {@code blades} (0 TB since 0.6.1) hold a job needing {@code requiredVram} TB? */
    public static boolean bladesFitVram(int blades, int requiredVram) {
        return fitsVram(vramCapacity(blades), requiredVram);
    }

    /**
     * The cold-plate efficiency of a coolant fluid id: PCB coolant runs 15% cooler than distilled
     * water, anything else is not a valid cold-plate fill (efficiency 0 = cannot provide).
     */
    public static double coolantEfficiency(String fluidId) {
        if (fluidId == null) {
            return 0.0;
        }
        if (fluidId.endsWith(":pcb_coolant") || fluidId.endsWith(":pcb_coolant_")) {
            return PCB_COOLANT_EFFICIENCY;
        }
        if (fluidId.endsWith(":distilled_water")) {
            return DISTILLED_WATER_EFFICIENCY;
        }
        return 0.0;
    }
}
