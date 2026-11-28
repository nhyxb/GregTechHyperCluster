package dev.hypercluster.common.model;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.6.1 · the data-driven AI model registry.
 *
 * <p>
 * Every model in the pack is one {@link AIModelDefinition}: an identity, a deployment camp
 * ({@link Camp#LOCAL open-source weights} or {@link Camp#API closed-source cloud}), the VRAM and
 * CWU/t it costs, the Token billing formula it uses, its domain tags and its event hooks.
 *
 * <p>
 * <b>Camps decide who pays.</b> A {@link Camp#LOCAL} model is the identity of an
 * {@code open_weight_module} produced by the tensor pretraining cluster: it is deployed into the
 * MoE router rack, consumes cluster VRAM and CWU/t, and is billed <b>zero Tokens</b>. A
 * {@link Camp#API} model never occupies local VRAM at all — it is dialled through the API
 * satellite ground station and billed in Tokens per round.
 *
 * <p>
 * <b>This is a runtime registry, not a Minecraft registry.</b> Definitions are declared once in
 * {@link AIModels} and looked up by id; the JSON-driven pass later in this version re-uses the same
 * objects. Keeping them out of the vanilla registry means a datapack-less install still has every
 * model, and a GameTest can assert the whole catalog without a world.
 */
public final class AIModelDefinition {

    /** Which camp the model belongs to, which decides how it is paid for. */
    public enum Camp {
        /** Open weights: deployed locally, zero Tokens, consumes cluster VRAM + CWU/t. */
        LOCAL,
        /** Closed-source: dialled over the API station, zero local VRAM, billed in Tokens. */
        API
    }

    /** Feature flags a model may carry. Each one is honoured by a named machine hook. */
    public enum Trait {
        /** Supports the Llama-style LoRA extension slots in the MoE router rack. */
        LORA_SLOTS,
        /** MoE sparse activation: only {@code sparseCwuPercent}% of the nominal CWU/t is billed. */
        MOE_SPARSE,
        /** Unlocks the DeepThink charge switch (adversarial by-products forced to 100%). */
        DEEP_THINK,
        /** Machine-room inspection: brownout auto-protective sleep and waived maintenance. */
        ROOM_INSPECTION,
        /** Agent tool calls: stops / restarts associated multiblocks on jam or starvation. */
        AGENT_TOOL_CALLS,
        /** Chemistry noise suppression: cheaper reagents on chemical recipes. */
        CHEM_NOISE_SUPPRESSION,
        /** Hard zero hallucination; also waives maintenance entirely. */
        ZERO_HALLUCINATION,
        /** Carries a Leaving-Soon retirement countdown. */
        LIFECYCLE,
        /** Highest Token price for the best reagent savings and by-product bonus. */
        MAX_REAGENT_SAVING,
        /** Refuses whitelisted dangerous chemistry rounds (morally grey trigger). */
        MORAL_REFUSAL,
        /** Completely uncensored: runs dangerous rounds without a jailbreak card. */
        UNCENSORED,
        /** Weather attenuation and duration penalty doubled (rain / thunder). */
        WEATHER_FRAGILE
    }

    private final ResourceLocation id;
    private final String displayKey;
    private final Camp camp;
    private final int vramTerabytes;
    private final int cwuPerTick;
    private final long tokenCostPerRound;
    private final List<String> domainTags;
    private final List<Trait> traits;
    private final int loraSlots;
    private final int loraVramTerabytes;
    private final int loraCwuPerTick;
    private final int deepThinkVramTerabytes;
    private final int sparseCwuPercent;
    private final double fallbackSlowdown;
    private final int trainingVramTerabytes;
    private final int trainingCwuPerTick;

    private AIModelDefinition(Builder builder) {
        this.id = builder.id;
        this.displayKey = builder.displayKey;
        this.camp = builder.camp;
        this.vramTerabytes = builder.vramTerabytes;
        this.cwuPerTick = builder.cwuPerTick;
        this.tokenCostPerRound = builder.tokenCostPerRound;
        this.domainTags = List.copyOf(builder.domainTags);
        this.traits = List.copyOf(builder.traits);
        this.loraSlots = builder.loraSlots;
        this.loraVramTerabytes = builder.loraVramTerabytes;
        this.loraCwuPerTick = builder.loraCwuPerTick;
        this.deepThinkVramTerabytes = builder.deepThinkVramTerabytes;
        this.sparseCwuPercent = builder.sparseCwuPercent;
        this.fallbackSlowdown = builder.fallbackSlowdown;
        this.trainingVramTerabytes = builder.trainingVramTerabytes;
        this.trainingCwuPerTick = builder.trainingCwuPerTick;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Identity & camp //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public ResourceLocation id() {
        return id;
    }

    public String displayKey() {
        return displayKey;
    }

    public Camp camp() {
        return camp;
    }

    public boolean isLocal() {
        return camp == Camp.LOCAL;
    }

    public boolean isApi() {
        return camp == Camp.API;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Cost model //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** VRAM the model occupies while loaded (TB). API models never occupy local VRAM. */
    public int vramTerabytes() {
        return isApi() ? 0 : vramTerabytes;
    }

    /** Nominal CWU/t the model demands before the MoE sparse discount. */
    public int cwuPerTick() {
        return cwuPerTick;
    }

    /** CWU/t actually billed: MoE-sparse models only pay {@code sparseCwuPercent}%. */
    public int billedCwuPerTick() {
        if (!has(Trait.MOE_SPARSE) || sparseCwuPercent <= 0) {
            return cwuPerTick;
        }
        return Math.max(1, Math.round(cwuPerTick * sparseCwuPercent / 100.0f));
    }

    /** Tokens one API round of this model costs before any discount. */
    public long tokenCostPerRound() {
        return isApi() ? tokenCostPerRound : 0L;
    }

    /** Extra VRAM DeepThink reserves while charged (TB); 0 for models without the trait. */
    public int deepThinkVramTerabytes() {
        return has(Trait.DEEP_THINK) ? deepThinkVramTerabytes : 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // LoRA extension (Qwen-style) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public int loraSlots() {
        return has(Trait.LORA_SLOTS) ? loraSlots : 0;
    }

    public int loraVramTerabytes() {
        return has(Trait.LORA_SLOTS) ? loraVramTerabytes : 0;
    }

    public int loraCwuPerTick() {
        return has(Trait.LORA_SLOTS) ? loraCwuPerTick : 0;
    }

    /**
     * Total VRAM for a loaded instance with {@code loras} LoRA cards and optional DeepThink.
     * The single place every VRAM gate must ask, so OOM can never drift between machines.
     */
    public int vramFor(int loras, boolean deepThink) {
        int total = vramTerabytes();
        int cards = Math.min(Math.max(0, loras), loraSlots());
        total += cards * loraVramTerabytes();
        if (deepThink) {
            total += deepThinkVramTerabytes();
        }
        return total;
    }

    /**
     * The local-fallback slowdown multiplier: how much slower the fallback gateway's local run is
     * than the cloud round. A local model bound to the gateway behaves better than no model at all.
     */
    public double fallbackSlowdown() {
        return fallbackSlowdown > 0 ? fallbackSlowdown : 4.0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Training gates (0.6.2) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * VRAM (TB) a pretraining run for this model must see online before it may start. Each
     * open-weight family declares its own gate, so choosing a bigger target in the cluster's
     * selector really costs more memory — it is not a cosmetic label. A model that declares no
     * gate of its own keeps the version-wide {@code VramRating.TRAINING_JOB_TB} default.
     */
    public int trainingVramTerabytes() {
        if (!isLocal()) {
            return 0;
        }
        return trainingVramTerabytes > 0 ? trainingVramTerabytes :
                dev.hypercluster.common.machine.datacenter.VramRating.TRAINING_JOB_TB;
    }

    /**
     * CWU/t a pretraining run for this model pulls from the optical feed every tick. A bigger
     * target trains on a wider tensor-parallel mesh, so it demands more computation per tick. A
     * model that declares no gate of its own keeps the {@code TrainingRating.TRAINING_CWUT}
     * default.
     */
    public int trainingCwuPerTick() {
        if (!isLocal()) {
            return 0;
        }
        return trainingCwuPerTick > 0 ? trainingCwuPerTick :
                dev.hypercluster.common.machine.training.TrainingRating.TRAINING_CWUT;
    }

    /** True when {@code availableTb} memory holds a pretraining run of this model. */
    public boolean fitsTraining(int availableTb) {
        return Math.max(0, availableTb) >= trainingVramTerabytes();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Domains & traits //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public List<String> domainTags() {
        return domainTags;
    }

    public boolean servesDomain(String domain) {
        return domain != null && domainTags.contains(domain);
    }

    public List<Trait> traits() {
        return traits;
    }

    public boolean has(Trait trait) {
        return traits.contains(trait);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Builder //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public static Builder builder(String path, String displayKey, Camp camp) {
        return new Builder(path, displayKey, camp);
    }

    /** Fluent builder; every default is the "no special behaviour" case. */
    public static final class Builder {

        private final ResourceLocation id;
        private final String displayKey;
        private final Camp camp;
        private int vramTerabytes;
        private int cwuPerTick;
        private long tokenCostPerRound;
        private final List<String> domainTags = new ArrayList<>();
        private final List<Trait> traits = new ArrayList<>();
        private int loraSlots;
        private int loraVramTerabytes;
        private int loraCwuPerTick;
        private int deepThinkVramTerabytes;
        private int sparseCwuPercent = 100;
        private double fallbackSlowdown;
        private int trainingVramTerabytes;
        private int trainingCwuPerTick;

        private Builder(String path, String displayKey, Camp camp) {
            this.id = new ResourceLocation("hypercluster", path);
            this.displayKey = displayKey;
            this.camp = camp;
        }

        public Builder vram(int terabytes) {
            this.vramTerabytes = terabytes;
            return this;
        }

        public Builder cwu(int perTick) {
            this.cwuPerTick = perTick;
            return this;
        }

        public Builder tokens(long perRound) {
            this.tokenCostPerRound = perRound;
            return this;
        }

        public Builder domains(String... tags) {
            Collections.addAll(this.domainTags, tags);
            return this;
        }

        public Builder traits(Trait... flags) {
            Collections.addAll(this.traits, flags);
            return this;
        }

        public Builder loraSlots(int slots, int vramTerabytes, int cwuPerTick) {
            this.loraSlots = slots;
            this.loraVramTerabytes = vramTerabytes;
            this.loraCwuPerTick = cwuPerTick;
            return this;
        }

        public Builder deepThinkVram(int terabytes) {
            this.deepThinkVramTerabytes = terabytes;
            return this;
        }

        /** MoE sparse activation: bill only this percentage of the nominal CWU/t. */
        public Builder sparseCwuPercent(int percent) {
            this.sparseCwuPercent = percent;
            return this;
        }

        /** Local-fallback slowdown multiplier for this model (default 4.0 = no model). */
        public Builder fallbackSlowdown(double multiplier) {
            this.fallbackSlowdown = multiplier;
            return this;
        }

        /**
         * Pretraining gate of this model: the cluster memory (TB) and the optical CWU/t one run
         * needs. Both are validated live by the pretraining cluster's start gate.
         */
        public Builder trainingGate(int vramTerabytes, int cwuPerTick) {
            this.trainingVramTerabytes = vramTerabytes;
            this.trainingCwuPerTick = cwuPerTick;
            return this;
        }

        public AIModelDefinition build() {
            return new AIModelDefinition(this);
        }
    }

    @Override
    public String toString() {
        return "AIModelDefinition[" + id + ", " + camp + ", " + vramTerabytes() + "TB, " +
                billedCwuPerTick() + " CWU/t]";
    }

    /** Order-preserving index of the registry, used by machine cycling buttons. */
    public static final class Catalog {

        private final Map<ResourceLocation, AIModelDefinition> byId = new LinkedHashMap<>();

        public Catalog add(AIModelDefinition definition) {
            byId.put(definition.id(), definition);
            return this;
        }

        public AIModelDefinition get(ResourceLocation id) {
            return byId.get(id);
        }

        public AIModelDefinition get(String id) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key == null ? null : byId.get(key);
        }

        public List<AIModelDefinition> all() {
            return List.copyOf(byId.values());
        }

        public List<AIModelDefinition> ofCamp(Camp camp) {
            return byId.values().stream().filter(d -> d.camp() == camp).toList();
        }

        public int size() {
            return byId.size();
        }

        public AIModelDefinition byIndex(int index, Camp camp) {
            List<AIModelDefinition> pool = ofCamp(camp);
            if (pool.isEmpty()) {
                return null;
            }
            return pool.get(Math.floorMod(index, pool.size()));
        }
    }
}
