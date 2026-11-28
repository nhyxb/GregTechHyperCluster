package dev.hypercluster.common.machine.training;

import dev.hypercluster.common.machine.datacenter.LiquidDatacenterRating;

/**
 * 0.6 · tensor pretraining and outsourced-compute contract (pure, GameTest-assertable).
 *
 * <p>
 * <b>Training job.</b> One run is a long, multi-epoch annealing pass over a domain dataset in a
 * {@code hypercluster:tensor_pretraining_cluster}. The contract this class encodes:
 *
 * <ul>
 * <li><b>VRAM gate (OOM).</b> A job declares the VRAM it needs; a cluster whose inserted blades
 * bring less online must <b>refuse to start</b> — never silently degrade.</li>
 * <li><b>Checkpoints.</b> Progress is written to NBT every {@value #CHECKPOINT_EVERY_PERCENT}%
 * of the run. A checkpoint is the only thing that survives a hard loss of power or coolant once
 * the UPS buffer is exhausted.</li>
 * <li><b>UPS protection.</b> A running {@code hypercluster:ups_power_array} keeps the cluster
 * alive through a brownout for {@value #UPS_BUFFER_TICKS} ticks (60 s). If grid power returns
 * inside that window the run resumes from its last checkpoint without losing progress. Without
 * UPS cover — or once the buffer runs out — the unsaved progress between the last checkpoint and
 * the failure is lost (清零未保存进度).</li>
 * <li><b>Export.</b> A completed run mints one {@code hypercluster:open_weight_module} carrying
 * the domain / precision / version NBT triple.</li>
 * </ul>
 *
 * <p>
 * <b>Outsourced compute.</b> A {@code hypercluster:cloud_offload_node} rents the CWU/t a nearby
 * datacenter has spare and pays the bound player's cloud account integer Tokens per tick. The
 * rate is {@code CWU/t × PUE coefficient × base rate}: the hotter the room runs, the less the
 * lessor is paid, so a Tier III room earns more than a Tier I one for the same computation.
 */
public final class TrainingRating {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Training job nameplate //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Progress milestones (percent) at which a checkpoint is written to NBT. */
    public static final int CHECKPOINT_EVERY_PERCENT = 25;

    /** Ticks of UPS cover a running power array provides: 60 s. */
    public static final int UPS_BUFFER_TICKS = 1200;

    /** One UPS array holds this much buffered EU while float-charging. */
    public static final long UPS_CAPACITY_EU = 4_000_000L;

    /** Float-charge acceptance of one UPS array while the grid is up (EU/t). */
    public static final long UPS_CHARGE_EU_T = 4096L;

    /** Discharge a UPS array can deliver into the protected cluster (EU/t). */
    public static final long UPS_DISCHARGE_EU_T = 8192L;

    /** Chebyshev reach (blocks) inside which a UPS array protects a cluster. */
    public static final int UPS_RANGE = 12;

    /** The training domain of the mining chain. */
    public static final String DOMAIN_MINING = "mining";

    /** The training domain of the chemical chain. */
    public static final String DOMAIN_CHEMICAL = "chemical";

    /** The only precision this version exports (the plan's fixed-weight template). */
    public static final String PRECISION = "fp16";

    /** Base tick length of a full one-epoch training run (before overclock-style modifiers). */
    public static final int BASE_TRAINING_TICKS = 2400;

    /** Distilled water a running cluster drinks per tick (mB/t). */
    public static final int COOLANT_PER_TICK = 1;

    /** Minimum tick rate the cluster bills for a run (EU/t) — one LuV amp at the ready. */
    public static final long TRAINING_EU_T = 32_768L;

    /** CWU/t a training run pulls from the optical switch every tick. */
    public static final int TRAINING_CWUT = 32;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Outsourced compute nameplate //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Tokens one CWU/t·tick is worth before the PUE coefficient (integer billing base). */
    public static final long OFFLOAD_TOKEN_PER_CWU = 40L;

    /** Highest CWU/t one offload node may rent per tick, whatever the host offers. */
    public static final int OFFLOAD_MAX_CWUT = 64;

    private TrainingRating() {}

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Training progression //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The checkpoint milestone (percent) a run of {@code totalTicks} reaches at {@code elapsed}. */
    public static int checkpointPercent(int elapsed, int totalTicks) {
        if (totalTicks <= 0) {
            return 0;
        }
        int percent = (int) ((long) elapsed * 100L / totalTicks);
        return Math.min(100, Math.max(0, percent)) / CHECKPOINT_EVERY_PERCENT * CHECKPOINT_EVERY_PERCENT;
    }

    /** The last checkpoint percent a saved run may resume from. */
    public static int resumePercent(int savedCheckpointPercent) {
        int clamped = Math.min(100, Math.max(0, savedCheckpointPercent));
        return clamped / CHECKPOINT_EVERY_PERCENT * CHECKPOINT_EVERY_PERCENT;
    }

    /** The ticks a run that resumes from {@code checkpointPercent} still needs. */
    public static int remainingTicks(int totalTicks, int checkpointPercent) {
        int from = resumePercent(checkpointPercent);
        return Math.max(0, (int) Math.round(totalTicks * (100 - from) / 100.0));
    }

    /** True when {@code elapsed} just crossed a checkpoint milestone (write the checkpoint now). */
    public static boolean crossesCheckpoint(int elapsedBefore, int elapsedAfter, int totalTicks) {
        if (totalTicks <= 0) {
            return false;
        }
        int before = checkpointPercent(elapsedBefore, totalTicks);
        int after = checkpointPercent(elapsedAfter, totalTicks);
        return after > before;
    }

    /** The version an exported module carries: the run's completed epoch count. */
    public static int moduleVersion(int checkpointPercent) {
        return Math.max(1, resumePercent(checkpointPercent) / CHECKPOINT_EVERY_PERCENT);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Outsourced compute payout //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Tokens paid per tick for renting {@code cwut} CWU/t out of a room rated {@code pue}.
     * Inverse-PUE: a cooler room (lower PUE) pays more per CWU/t for the same silicon.
     */
    public static long offloadTokensPerTick(int cwut, double pue) {
        if (cwut <= 0) {
            return 0L;
        }
        double coefficient = pue <= 0 ? 1.0 : (LiquidDatacenterRating.PUE_TIER_III / pue);
        double tokens = cwut * OFFLOAD_TOKEN_PER_CWU * coefficient;
        return Math.max(1L, Math.round(tokens));
    }

    /** The CWU/t an offload node should actually rent: clamped to the node and host ceilings. */
    public static int rentableCWUt(int hostAvailable, int requested) {
        int wanted = Math.min(Math.max(0, requested), OFFLOAD_MAX_CWUT);
        return Math.min(wanted, Math.max(0, hostAvailable));
    }
}
