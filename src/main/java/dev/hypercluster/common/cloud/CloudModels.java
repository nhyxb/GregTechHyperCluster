package dev.hypercluster.common.cloud;

import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;

/**
 * 0.4 · the closed-source model catalog, re-based onto the 0.6.1 data-driven registry.
 *
 * <p>
 * The old {@code OmniFlash-72B} / {@code LegacyLlama-8B} placeholders are gone: the ground station
 * now dials the real API-camp families from {@link AIModels} — Gemini-Titanium-4.0 (the
 * Leaving-Soon lifecycle model), Gemini-Platinum, Gemini-Xenon, GPT-1145 Multiverse and Grok-420.
 *
 * <p>
 * The lifecycle is evaluated against the <b>game day of the save</b>
 * ({@code overworld.dayTime() / 24000}), so it is per-save and survives restarts; the retirement
 * day and the Leaving-Soon window stay configurable through {@link CloudConfigs}. A Leaving Soon
 * model is still usable — the UI shows the countdown; a RETIRED model refuses new cloud rounds
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

    /** The API-camp models the ground station can cycle through, in declaration order. */
    public static final java.util.List<AIModelDefinition> MODELS = AIModels.apiModels();

    private CloudModels() {}

    /**
     * The model a fresh station dials: the first API model that is <b>not</b> on a lifecycle
     * countdown. A station must never boot into a model that has already retired in this save —
     * the operator picks the countdown model deliberately.
     */
    public static AIModelDefinition defaultModel() {
        for (AIModelDefinition model : MODELS) {
            if (!model.has(AIModelDefinition.Trait.LIFECYCLE)) {
                return model;
            }
        }
        return MODELS.get(0);
    }

    /** Index of {@link #defaultModel()} inside {@link #MODELS}. */
    public static int defaultModelIndex() {
        return MODELS.indexOf(defaultModel());
    }

    public static AIModelDefinition byIndex(int index) {
        return MODELS.get(Math.floorMod(index, MODELS.size()));
    }

    /** The model carrying the Leaving-Soon lifecycle (Gemini-Titanium-4.0). */
    public static AIModelDefinition lifecycleModel() {
        return AIModels.lifecycleModel();
    }

    /**
     * The lifecycle of a model on the given game day. Only models carrying
     * {@link AIModelDefinition.Trait#LIFECYCLE} ever retire; everything else stays ACTIVE.
     */
    public static Lifecycle lifecycle(AIModelDefinition model, long gameDay) {
        if (model == null || !model.has(AIModelDefinition.Trait.LIFECYCLE)) {
            return Lifecycle.ACTIVE;
        }
        long retireDay = CloudConfigs.legacyModelRetireDay;
        if (gameDay >= retireDay) {
            return Lifecycle.RETIRED;
        }
        long window = Math.max(0, CloudConfigs.leavingSoonDays);
        if (gameDay >= retireDay - window) {
            return Lifecycle.LEAVING_SOON;
        }
        return Lifecycle.ACTIVE;
    }

    /**
     * The number of game days left before the model goes offline ({@code <= 0} once retired,
     * {@code -1} for a never-retiring model).
     */
    public static long leavingSoonCountdown(AIModelDefinition model, long gameDay) {
        if (model == null || !model.has(AIModelDefinition.Trait.LIFECYCLE)) {
            return -1;
        }
        return CloudConfigs.legacyModelRetireDay - gameDay;
    }
}
