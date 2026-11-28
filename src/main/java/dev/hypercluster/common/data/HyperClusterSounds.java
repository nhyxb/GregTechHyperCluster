package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.minecraft.sounds.SoundSource;

import dev.hypercluster.HyperCluster;

/**
 * 0.6.3 · the eight industrial sound effects of the compute cluster.
 *
 * <p>
 * Every entry is a plain GTCEu {@link SoundEntry} registered through
 * {@code HyperCluster.REGISTRATE.sound(...)}; the {@code .ogg} files live in
 * {@code assets/hypercluster/sounds/} and the matching {@code sounds.json} is written by the same
 * data generator GTCEu uses for its own sounds
 * ({@code SoundEntryBuilder.SoundEntryProvider}), so the JSON and the registry can never drift
 * apart.
 *
 * <p>
 * <b>Registration window.</b> {@code IGTAddon#registerSounds()} is called from
 * {@code GTSoundEntries.init()} <em>before</em> GTCEu runs {@code SoundEntry::prepare} and
 * freezes {@link com.gregtechceu.gtceu.api.registry.GTRegistries#SOUNDS}. That is the only
 * window in which an addon may add sounds; {@code initializeAddon()} runs later and would hit
 * the frozen registry.
 *
 * <p>
 * Six entries are machine work loops, two are one-shots. Category is {@link SoundSource#BLOCKS}
 * so the vanilla "Blocks" volume slider governs them, and the attenuation distance is set so a
 * large cluster is audible across a machine hall but not across the world.
 */
public final class HyperClusterSounds {

    /** Attenuation (blocks) for the wide-area machine loops. */
    public static final int LOOP_ATTENUATION = 24;
    /** Attenuation (blocks) for the short one-shot cues. */
    public static final int ONE_SHOT_ATTENUATION = 16;

    // Work loops: the six machines that run continuously.
    public static SoundEntry DATACENTER_AIR_HUM;
    public static SoundEntry DATACENTER_LIQUID_HUM;
    public static SoundEntry PRETRAINING_CLUSTER;
    public static SoundEntry SATELLITE_UPLINK;
    public static SoundEntry LASER_BURN;
    public static SoundEntry RED_TEAM_SCAN;

    // One-shots.
    public static SoundEntry RACK_CLICK;
    public static SoundEntry TERMINAL_ALERT;

    private HyperClusterSounds() {}

    /** Called from {@code IGTAddon#registerSounds()}. */
    public static void init() {
        DATACENTER_AIR_HUM = register("datacenter_air_hum", LOOP_ATTENUATION);
        DATACENTER_LIQUID_HUM = register("datacenter_liquid_hum", LOOP_ATTENUATION);
        PRETRAINING_CLUSTER = register("pretraining_cluster", LOOP_ATTENUATION);
        SATELLITE_UPLINK = register("satellite_uplink", LOOP_ATTENUATION);
        LASER_BURN = register("laser_burn", LOOP_ATTENUATION);
        RED_TEAM_SCAN = register("red_team_scan", LOOP_ATTENUATION);
        RACK_CLICK = register("rack_click", ONE_SHOT_ATTENUATION);
        TERMINAL_ALERT = register("terminal_alert", ONE_SHOT_ATTENUATION);
    }

    private static SoundEntry register(String name, int attenuation) {
        // SoundEntry#getSubtitleKey() builds "<namespace>.subtitle.<path>" itself; the subtitle
        // string only marks the entry as subtitled, so it is set to that same key.
        return HyperCluster.REGISTRATE.sound(HyperCluster.id(name))
                .subtitle("hypercluster.subtitle." + name)
                .category(SoundSource.BLOCKS)
                .attenuationDistance(attenuation)
                .build();
    }

    /** Every registered entry, in a stable order (used by the registration GameTest). */
    public static SoundEntry[] all() {
        return new SoundEntry[] { DATACENTER_AIR_HUM, DATACENTER_LIQUID_HUM, PRETRAINING_CLUSTER,
                SATELLITE_UPLINK, LASER_BURN, RED_TEAM_SCAN, RACK_CLICK, TERMINAL_ALERT };
    }
}
