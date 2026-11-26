package dev.hypercluster.common.cloud;

import dev.hypercluster.HyperCluster;

/**
 * 0.4 · the closed-source model catalog with a game-day based lifecycle (型号生命周期).
 *
 * <p>
 * Model families are display text over stable IDs (per the implementation plan: the family names
 * are flavour, the registry IDs stay independent). Two models ship with 0.4:
 *
 * <ul>
 * <li>{@code hypercluster:omni_flash} — OmniFlash-72B, the stable flagship. Never retires.</li>
 * <li>{@code hypercluster:legacy_llama} — LegacyLlama-8B, the lifecycle demonstrator. It goes
 * {@code Leaving Soon} {@link CloudConfigs#leavingSoonDays} game days before
 * {@link CloudConfigs#legacyModelRetireDay} and is blocked (offline, 下线) from that day
 * on.</li>
 * </ul>
 *
 * <p>
 * The lifecycle is evaluated against the <b>game day of the save</b>
 * ({@code overworld.dayTime() / 24000}), so it is per-save and survives restarts, and it is
 * configurable through {@link CloudConfigs} (system properties). A {@code Leaving Soon} model is
 * still usable — the UI shows the countdown; a {@code RETIRED} model refuses new cloud rounds
 * (the local fallback gateway then takes over if one is attached).
 */
public final class CloudModels {

    /** Lifecycle status of a model, evaluated per game day. */
    public enum Lifecycle {
        /** Fully online. */
        ACTIVE,
        /** Countdown to retirement (still usable, warned). */
        LEAVING_SOON,
        /** Offline — new cloud rounds with this model are blocked (到期拦截). */
        RETIRED
    }

    /** One catalog entry: stable registry id + display name key. */
    public record CloudModel(String id, String displayKey) {}

    public static final CloudModel OMNI_FLASH = new CloudModel(HyperCluster.id("omni_flash").toString(),
            "hypercluster.cloud.model.omni_flash");

    public static final CloudModel LEGACY_LLAMA = new CloudModel(HyperCluster.id("legacy_llama").toString(),
            "hypercluster.cloud.model.legacy_llama");

    /** The selectable catalog, in cycling order. */
    public static final CloudModel[] MODELS = { OMNI_FLASH, LEGACY_LLAMA };

    private CloudModels() {}

    public static CloudModel byIndex(int index) {
        return MODELS[Math.floorMod(index, MODELS.length)];
    }

    /**
     * The lifecycle of a model on the given game day.
     *
     * @param model   the catalog entry
     * @param gameDay the current game day ({@code dayTime / 24000})
     */
    public static Lifecycle lifecycle(CloudModel model, long gameDay) {
        if (model == LEGACY_LLAMA) {
            long retireDay = CloudConfigs.legacyModelRetireDay;
            if (gameDay >= retireDay) {
                return Lifecycle.RETIRED;
            }
            long window = Math.max(0, CloudConfigs.leavingSoonDays);
            if (gameDay >= retireDay - window) {
                return Lifecycle.LEAVING_SOON;
            }
        }
        return Lifecycle.ACTIVE;
    }

    /**
     * The number of game days left before the model goes offline ({@code <= 0} once retired,
     * {@code -1} for a never-retiring model).
     */
    public static long leavingSoonCountdown(CloudModel model, long gameDay) {
        if (model != LEGACY_LLAMA) {
            return -1;
        }
        return CloudConfigs.legacyModelRetireDay - gameDay;
    }
}
