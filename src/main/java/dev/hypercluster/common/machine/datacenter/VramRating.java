package dev.hypercluster.common.machine.datacenter;

/**
 * 0.6.1 · the TB-scale cluster memory contract.
 *
 * <p>
 * 0.6.0 measured memory in arbitrary "GB" units and let the compute blades carry it. 0.6.1 splits
 * the two concerns the way a real machine room does: a compute blade is <b>pure compute</b>, and
 * all cluster memory lives in dedicated {@code hypercluster:vram_storage_rack} cabinets loaded
 * with HBM modules.
 *
 * <ul>
 * <li>{@code hypercluster:iv_hbm_module} — {@value #IV_HBM_TB} TB per module.</li>
 * <li>{@code hypercluster:luv_hbm_module} — {@value #LUV_HBM_TB} TB per module.</li>
 * <li>A rack has {@value #HBM_SLOTS} slots, so a full IV rack is {@value #IV_RACK_TB} TB and a
 * full LuV rack is {@value #LUV_RACK_TB} TB.</li>
 * <li>A pretraining job needs {@value #TRAINING_JOB_TB} TB, i.e. two full LuV racks (or any mix
 * that reaches the target) — the 0.6.1 replacement for 0.6.0's 96 GB gate.</li>
 * </ul>
 */
public final class VramRating {

    /** Memory one IV HBM module provides (TB). */
    public static final int IV_HBM_TB = 4;

    /** Memory one LuV HBM module provides (TB). */
    public static final int LUV_HBM_TB = 16;

    /** HBM module slots of one vram_storage_rack. */
    public static final int HBM_SLOTS = 4;

    /** A rack fully loaded with IV modules. */
    public static final int IV_RACK_TB = HBM_SLOTS * IV_HBM_TB;

    /** A rack fully loaded with LuV modules: 64 TB, i.e. two racks clear the training gate. */
    public static final int LUV_RACK_TB = HBM_SLOTS * LUV_HBM_TB;

    /** VRAM a single tensor pretraining run needs (TB). */
    public static final int TRAINING_JOB_TB = 96;

    /** Chebyshev reach (blocks) inside which an optical switch aggregates racks. */
    public static final int SWITCH_RANGE = 16;

    /** Total CWU/t one optical fabric switch may hand out per tick before 503. */
    public static final int SWITCH_BANDWIDTH_LIMIT = 128;

    /** Ticks the switch stays in "503 Server Busy" after a bandwidth blowout (15 s). */
    public static final int BUSY_COOLDOWN_TICKS = 300;

    /** CWU/t one IV air-cooled rack's blade contributes (0.3 contract, re-stated for aggregation). */
    public static final int IV_BLADE_CWU = 6;

    /** CWU/t one LuV liquid blade contributes (0.6 contract, re-stated for aggregation). */
    public static final int LUV_BLADE_CWU = 16;

    /** Stored-energy fraction below which a room-inspection model sleeps the room (<20%). */
    public static final double BROWNOUT_FRACTION = 0.20;

    private VramRating() {}

    /** Memory one HBM module of the given kind provides (TB); 0 for a non-module. */
    public static int moduleTerabytes(boolean luv) {
        return luv ? LUV_HBM_TB : IV_HBM_TB;
    }

    /** True when {@code availableTb} holds a {@code requiredTb} job. */
    public static boolean fits(int availableTb, int requiredTb) {
        return Math.max(0, availableTb) >= Math.max(0, requiredTb);
    }

    /** The switch's 503 gate: can this tick's aggregate demand fit the switch bandwidth? */
    public static boolean withinSwitchBandwidth(int demand) {
        return demand >= 0 && demand <= SWITCH_BANDWIDTH_LIMIT;
    }
}
