package dev.hypercluster.common.model;

import dev.hypercluster.common.model.AIModelDefinition.Trait;

/**
 * 0.6.2 · the six model families' <b>runtime</b> effect table (pure, GameTest-assertable).
 *
 * <p>
 * {@link AIModels} declares what a family <i>is</i>; this class resolves what a family
 * <b>actually does</b> when a machine settles a round with it loaded. Keeping the table here — one
 * place, no world access, no side effects — is what lets the acceptance tests assert the contract
 * directly while every machine calls the very same function. A behaviour can therefore never drift
 * between the manual, the tests and the three machines that honour it.
 *
 * <p>
 * <b>Ranks are ordered, not equal.</b> The reagent-saving bonus rises with the price of the
 * model: Platinum only helps the chemical domain, Xenon helps everything a little, GPT-1145 helps
 * everything the most — which is exactly what its {@code MAX_REAGENT_SAVING} name claims.
 */
public final class ModelRuntime {

    /** Extra output a Platinum chemical round yields (noise suppressed → no rework loss). */
    public static final int PLATINUM_YIELD_BONUS = 1;

    /** Extra output a Xenon round yields (hard zero hallucination → no discard). */
    public static final int XENON_YIELD_BONUS = 1;

    /** Extra output a GPT-1145 round yields (the top-tier reagent saving). */
    public static final int GPT_YIELD_BONUS = 2;

    /** Fallback slowdown while a Qwen router is online in the cluster. */
    public static final double QWEN_FALLBACK_SLOWDOWN = 1.5;

    /** Fallback slowdown with no model bound (the 0.4 contract). */
    public static final double BARE_FALLBACK_SLOWDOWN = 4.0;

    /** Stored-energy fraction below which a room-inspection model sleeps the room (<20%). */
    public static final double BROWNOUT_FRACTION = 0.20;

    private ModelRuntime() {}

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Settlement yield & maintenance //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extra regular outputs one settled round of this model yields on the ground station.
     *
     * <ul>
     * <li>{@code gemini_platinum} — {@value #PLATINUM_YIELD_BONUS}, <b>chemical domain only</b>
     * ({@code noiseSuppressed}); its noise suppression has nothing to suppress on a mining round.
     * </li>
     * <li>{@code gemini_xenon} — {@value #XENON_YIELD_BONUS} on every round.</li>
     * <li>{@code gpt_1145_multiverse} — {@value #GPT_YIELD_BONUS} on every round, the priciest but
     * the biggest saving.</li>
     * </ul>
     */
    public static int yieldBonus(AIModelDefinition model, boolean chemicalRound) {
        if (model == null) {
            return 0;
        }
        if (model.has(Trait.CHEM_NOISE_SUPPRESSION)) {
            return chemicalRound ? PLATINUM_YIELD_BONUS : 0;
        }
        if (model.has(Trait.MAX_REAGENT_SAVING)) {
            return GPT_YIELD_BONUS;
        }
        if (model.has(Trait.ZERO_HALLUCINATION)) {
            return XENON_YIELD_BONUS;
        }
        return 0;
    }

    /**
     * True when the model makes its host ignore maintenance problems entirely: Xenon's
     * zero-hallucination contract and GLM-2333 Flash's room inspection both waive maintenance.
     */
    public static boolean waivesMaintenance(AIModelDefinition model) {
        return model != null &&
                (model.has(Trait.ZERO_HALLUCINATION) || model.has(Trait.ROOM_INSPECTION));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Dangerous rounds (morally grey whitelist) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * May this model run a whitelisted dangerous chemistry round?
     *
     * <ul>
     * <li>{@code grok_420} — uncensored: runs it with <b>no jailbreak card at all</b>.</li>
     * <li>{@code gpt_1145_multiverse} — morally refusing: runs it <b>only</b> with a jailbreak
     * card, and refuses at runtime (the round never starts, nothing is billed) without one.</li>
     * <li>every other family — has no dangerous-round capability at all.</li>
     * </ul>
     */
    public static boolean permitsDangerousRound(AIModelDefinition model, boolean jailbroken) {
        return AIModels.mayRunDangerousChemistry(model, jailbroken);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Weather //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The weather multiplier a round of this model really suffers. Grok-420's
     * {@code WEATHER_FRAGILE} trait doubles the rain/thunder penalty; every other family takes the
     * plain uplink attenuation.
     */
    public static double effectiveWeather(AIModelDefinition model, double base) {
        return AIModels.weatherAttenuation(model, base);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Fallback gateway //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The local-fallback slowdown the gateway must offer: with a Qwen router online anywhere in the
     * cluster the local run degrades only ×1.5; with no Qwen deployed it falls back to the
     * model-less ×4.
     */
    public static double fallbackSlowdown(boolean qwenOnline) {
        return qwenOnline ? QWEN_FALLBACK_SLOWDOWN : BARE_FALLBACK_SLOWDOWN;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Machine-room protection (GLM) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** True when {@code stored}/{@code capacity} is under the protective-sleep threshold. */
    public static boolean isBrownout(long stored, long capacity) {
        if (capacity <= 0) {
            return false;
        }
        return stored < capacity * BROWNOUT_FRACTION;
    }

    /** True when this model protects the room by sleeping through a brownout (GLM-2333 Flash). */
    public static boolean sleepsOnBrownout(AIModelDefinition model) {
        return model != null && model.has(Trait.ROOM_INSPECTION);
    }

    /** True when this model stops / restarts associated multiblocks on jam or starvation. */
    public static boolean drivesAgentToolCalls(AIModelDefinition model) {
        return model != null && model.has(Trait.AGENT_TOOL_CALLS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Projections onto associated multiblocks (Qwen LoRA / DeepSeek DeepThink) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The by-product force applied to an associated multiblock's settlement: DeepSeek-V6767 Pro
     * with {@code DeepThink} charged forces every probability by-product to <b>100%</b>. Every
     * other model leaves chances alone.
     */
    public static boolean forcesByproducts(AIModelDefinition model, boolean deepThink) {
        return model != null && deepThink && model.has(Trait.DEEP_THINK);
    }

    /** Domains in which the model's LoRA extensions are boosting by-products. */
    public static boolean loraBoostsDomain(AIModelDefinition model, String domain) {
        return model != null && model.has(Trait.LORA_SLOTS) && model.servesDomain(domain);
    }
}
