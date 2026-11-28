package dev.hypercluster.common.model;

import dev.hypercluster.common.model.AIModelDefinition.Camp;
import dev.hypercluster.common.model.AIModelDefinition.Catalog;
import dev.hypercluster.common.model.AIModelDefinition.Trait;

/**
 * 0.6.1 · the first six model families, replacing the 0.4.0 {@code OmniFlash-72B} /
 * {@code LegacyLlama-8B} placeholders.
 *
 * <p>
 * <b>Open camp</b> (identified by a pretrained {@code open_weight_module}, deployed into the MoE
 * router rack, billed <b>zero Tokens</b>):
 *
 * <ol>
 * <li>{@code qwen_9187_pro_max_ultra} — 16 TB + 4 TB per LoRA, 2–4 CWU/t per card. Bound to the
 * local fallback gateway its fallback is only <b>1.5×</b> slower instead of the model-less 4×,
 * and cross-domain runs skip the overfitting penalty. Four LoRA slots in the router carry the
 * 0.2.1 mining/chemical LoRA specialisations. Runs on IV air-cooled rooms too.</li>
 * <li>{@code deepseek_v6767_flash} — 32 TB. MoE sparse activation: only <b>25%</b> of the nominal
 * CWU/t is billed, built for steady high-concurrency runs.</li>
 * <li>{@code deepseek_v6767_pro} — 64 TB, keeps the 25% sparse discount and unlocks
 * <b>DeepThink</b> (an extra 16 TB while charged) which forces probability by-products to
 * <b>100%</b>. Going over the switch bandwidth trips the 503 busy cooldown first.</li>
 * <li>{@code glm_2333_flash} — 24 TB, 4 CWU/t. Machine-room inspection: brownout
 * (&lt; 20% stored) auto-protective sleep and waived maintenance.</li>
 * <li>{@code glm_2333_max} — 48 TB, 8 CWU/t. Top-tier agent tool calling: stops and restarts
 * associated multiblocks on jam / starvation.</li>
 * </ol>
 *
 * <p>
 * <b>API camp</b> (dialled through the satellite ground station, Tokens only, <b>zero local
 * VRAM</b>):
 *
 * <ol>
 * <li>{@code gemini_titanium_4_0} — the Leaving-Soon lifecycle model.</li>
 * <li>{@code gemini_platinum} — chemical noise suppression: cheaper reagents on chemical
 * recipes.</li>
 * <li>{@code gemini_xenon} — heavy inert gas: hard zero hallucination and fully waived
 * maintenance.</li>
 * <li>{@code gpt_1145_multiverse} — the highest Token price in exchange for the best reagent
 * savings and by-product bonus. Refuses whitelisted dangerous chemistry rounds
 * ({@link Trait#MORAL_REFUSAL}) until a {@code jailbreak_card} is inserted.</li>
 * <li>{@code grok_420} — completely uncensored, runs the dangerous rounds with no jailbreak and
 * cheaper than GPT, but its weather attenuation and duration penalty are doubled.</li>
 * </ol>
 */
public final class AIModels {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Open camp //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Qwen 9187 Pro-Max-Ultra: the LoRA-capable flagship with the best local fallback. */
    public static final AIModelDefinition QWEN_9187_PRO_MAX_ULTRA = AIModelDefinition
            .builder("qwen_9187_pro_max_ultra", "hypercluster.model.qwen_9187_pro_max_ultra", Camp.LOCAL)
            .vram(16)
            .cwu(4)
            .domains("mining", "chemical")
            .traits(Trait.LORA_SLOTS)
            .loraSlots(4, 4, 4)
            // With a model bound the gateway degrades 1.5x instead of the modelless 4x.
            .fallbackSlowdown(1.5)
            .build();

    /** DeepSeek-V6767 Flash: sparse MoE built for cheap steady throughput. */
    public static final AIModelDefinition DEEPSEEK_V6767_FLASH = AIModelDefinition
            .builder("deepseek_v6767_flash", "hypercluster.model.deepseek_v6767_flash", Camp.LOCAL)
            .vram(32)
            .cwu(8)
            .domains("mining", "chemical")
            .traits(Trait.MOE_SPARSE)
            .sparseCwuPercent(25)
            .build();

    /** DeepSeek-V6767 Pro: sparse MoE plus the DeepThink charge switch. */
    public static final AIModelDefinition DEEPSEEK_V6767_PRO = AIModelDefinition
            .builder("deepseek_v6767_pro", "hypercluster.model.deepseek_v6767_pro", Camp.LOCAL)
            .vram(64)
            .cwu(16)
            .domains("mining", "chemical")
            .traits(Trait.MOE_SPARSE, Trait.DEEP_THINK)
            .sparseCwuPercent(25)
            .deepThinkVram(16)
            .build();

    /** GLM-2333 Flash: machine-room inspection and brownout auto-protective sleep. */
    public static final AIModelDefinition GLM_2333_FLASH = AIModelDefinition
            .builder("glm_2333_flash", "hypercluster.model.glm_2333_flash", Camp.LOCAL)
            .vram(24)
            .cwu(4)
            .domains("mining", "chemical")
            .traits(Trait.ROOM_INSPECTION)
            .build();

    /** GLM-2333 Max: top-tier agent tool calling over associated multiblocks. */
    public static final AIModelDefinition GLM_2333_MAX = AIModelDefinition
            .builder("glm_2333_max", "hypercluster.model.glm_2333_max", Camp.LOCAL)
            .vram(48)
            .cwu(8)
            .domains("mining", "chemical")
            .traits(Trait.AGENT_TOOL_CALLS)
            .build();

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // API camp //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Gemini-Titanium-4.0: the Leaving-Soon lifecycle demonstrator. */
    public static final AIModelDefinition GEMINI_TITANIUM_4_0 = AIModelDefinition
            .builder("gemini_titanium_4_0", "hypercluster.model.gemini_titanium_4_0", Camp.API)
            .tokens(25_000L)
            .domains("mining", "chemical")
            .traits(Trait.LIFECYCLE)
            .build();

    /** Gemini-Platinum: chemical noise suppression. */
    public static final AIModelDefinition GEMINI_PLATINUM = AIModelDefinition
            .builder("gemini_platinum", "hypercluster.model.gemini_platinum", Camp.API)
            .tokens(32_000L)
            .domains("chemical")
            .traits(Trait.CHEM_NOISE_SUPPRESSION)
            .build();

    /** Gemini-Xenon: heavy inert gas, hard zero hallucination, waived maintenance. */
    public static final AIModelDefinition GEMINI_XENON = AIModelDefinition
            .builder("gemini_xenon", "hypercluster.model.gemini_xenon", Camp.API)
            .tokens(40_000L)
            .domains("mining", "chemical")
            .traits(Trait.ZERO_HALLUCINATION)
            .build();

    /** GPT-1145 Multiverse: the priciest, best savings, and morally grey refusals. */
    public static final AIModelDefinition GPT_1145_MULTIVERSE = AIModelDefinition
            .builder("gpt_1145_multiverse", "hypercluster.model.gpt_1145_multiverse", Camp.API)
            .tokens(60_000L)
            .domains("mining", "chemical")
            .traits(Trait.MAX_REAGENT_SAVING, Trait.MORAL_REFUSAL)
            .build();

    /** Grok-420: fully uncensored and cheaper, but fragile in bad weather. */
    public static final AIModelDefinition GROK_420 = AIModelDefinition
            .builder("grok_420", "hypercluster.model.grok_420", Camp.API)
            .tokens(45_000L)
            .domains("mining", "chemical")
            .traits(Trait.UNCENSORED, Trait.WEATHER_FRAGILE)
            .build();

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Catalog //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static final Catalog CATALOG = new Catalog()
            .add(QWEN_9187_PRO_MAX_ULTRA)
            .add(DEEPSEEK_V6767_FLASH)
            .add(DEEPSEEK_V6767_PRO)
            .add(GLM_2333_FLASH)
            .add(GLM_2333_MAX)
            .add(GEMINI_TITANIUM_4_0)
            .add(GEMINI_PLATINUM)
            .add(GEMINI_XENON)
            .add(GPT_1145_MULTIVERSE)
            .add(GROK_420);

    private AIModels() {}

    /** The whole catalog, in declaration order. */
    public static Catalog catalog() {
        return CATALOG;
    }

    /** Every open-weight model, the only ones a pretraining run can mint. */
    public static java.util.List<AIModelDefinition> localModels() {
        return CATALOG.ofCamp(Camp.LOCAL);
    }

    /** Every closed-source model the ground station can dial. */
    public static java.util.List<AIModelDefinition> apiModels() {
        return CATALOG.ofCamp(Camp.API);
    }

    /** The lifecycle demonstrator (the only API model carrying a retirement countdown). */
    public static AIModelDefinition lifecycleModel() {
        return GEMINI_TITANIUM_4_0;
    }

    /**
     * Every model the whitelisted dangerous-chemistry path can run. A model with
     * {@link Trait#MORAL_REFUSAL} refuses unless a jailbreak card is inserted; a model with
     * {@link Trait#UNCENSORED} runs it regardless.
     */
    public static boolean mayRunDangerousChemistry(AIModelDefinition model, boolean jailbroken) {
        if (model == null) {
            return false;
        }
        if (model.has(Trait.UNCENSORED)) {
            return true;
        }
        if (model.has(Trait.MORAL_REFUSAL)) {
            return jailbroken;
        }
        return false;
    }

    /**
     * Climate attenuation on an API round for this model: {@code base} is the weather multiplier
     * (1.0 clear / 1.5 rain / 2.0 thunder). A weather-fragile model doubles the penalty.
     */
    public static double weatherAttenuation(AIModelDefinition model, double base) {
        if (model != null && model.has(Trait.WEATHER_FRAGILE)) {
            return base * 2.0;
        }
        return base;
    }
}
