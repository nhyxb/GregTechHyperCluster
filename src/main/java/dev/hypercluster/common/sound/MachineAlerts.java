package dev.hypercluster.common.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import dev.hypercluster.common.data.HyperClusterSounds;

/**
 * 0.6.3 · the throttled {@code terminal_alert} cue.
 *
 * <p>
 * One {@code terminal_alert} fires per {@value #THROTTLE_TICKS} ticks per machine, so a cluster
 * that flaps between states (429 → recover → 429, OOM every tick, coolant starved and refilled in
 * a loop) cannot turn the mix into a siren. Each machine owns its own {@code long} stamp.
 *
 * <p>
 * The alert covers the five operational failures the machines can report:
 * <ul>
 * <li><b>429</b> — the cloud account's concurrency ceiling was hit;</li>
 * <li><b>503</b> — the optical switch is congested over its bandwidth ceiling;</li>
 * <li><b>OOM</b> — the VRAM pool cannot hold the deployment;</li>
 * <li><b>coolant starvation</b> — the liquid rack ran dry;</li>
 * <li><b>UPS backup</b> — a protected cluster switched to battery cover.</li>
 * </ul>
 *
 * <p>
 * Server-side only: {@code SoundEntry#playOnServer} broadcasts to every nearby player, and the
 * call is a no-op on the client, so the state machines can call it without distribution checks.
 */
public final class MachineAlerts {

    /** Minimum ticks between two alerts from the same machine (3 s). */
    public static final int THROTTLE_TICKS = 60;

    private MachineAlerts() {}

    /**
     * Plays the alert if the machine's throttle window has elapsed.
     *
     * @param lastTick the machine's stored stamp, or {@link #NEVER} if it has never alerted
     * @return the new stamp to store (unchanged when the cue was suppressed)
     */
    public static long play(Level level, BlockPos pos, long lastTick, long now) {
        if (level == null || level.isClientSide()) {
            return lastTick;
        }
        if (lastTick != NEVER && now - lastTick < THROTTLE_TICKS) {
            return lastTick;
        }
        HyperClusterSounds.TERMINAL_ALERT.playOnServer(level, pos, 0.9F, 1.0F);
        return now;
    }

    /** Sentinel for "this machine has never alerted". */
    public static final long NEVER = Long.MIN_VALUE;

    /** Traditional throttle check: is a new cue allowed at {@code now}? */
    public static boolean allows(long lastTick, long now) {
        return lastTick == NEVER || now - lastTick >= THROTTLE_TICKS;
    }
}
