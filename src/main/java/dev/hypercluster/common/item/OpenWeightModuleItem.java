package dev.hypercluster.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;

import java.util.List;

import javax.annotation.Nullable;

/**
 * 0.6 · the open-weight module ({@code hypercluster:open_weight_module}).
 *
 * <p>
 * The artifact of a finished tensor pretraining run: the trained weights of one domain, written
 * by the {@code hypercluster:tensor_pretraining_cluster}. The item is a plain stackable item
 * whose NBT carries the training record, so a module minted by one machine stays readable in an
 * anvil, a chest, an ME drive or a data-pack recipe:
 *
 * <ul>
 * <li>{@value #MODEL_TAG} — the open-weight model identity the module was trained as
 * ({@code qwen_9187_pro_max_ultra}, {@code deepseek_v6767_pro}, …). <b>Added in 0.6.2</b>; a
 * module written before that has no such key and reads back {@link #LEGACY_MODEL_ID}.</li>
 * <li>{@value #DOMAIN_TAG} — the training domain ({@code mining} or {@code chemical});</li>
 * <li>{@value #PRECISION_TAG} — the precision the run settled at ({@code fp16});</li>
 * <li>{@value #VERSION_TAG} — the checkpoint epoch the module was exported from (1-based);</li>
 * <li>{@value #RUNS_TAG} — how many completed runs this module descends from (1 for a fresh
 * export).</li>
 * </ul>
 *
 * <p>
 * The tag names are the long-lived on-disk contract: renaming any of them needs a migration that
 * reads the old key (project rule: registries and persisted keys never silently change).
 */
public class OpenWeightModuleItem extends Item {

    /**
     * Open-weight model identity carried by the module (0.6.2). Modules minted by 0.6.1 and
     * earlier lack this key entirely and fall back to {@link #LEGACY_MODEL_ID}.
     */
    public static final String MODEL_TAG = "hc_model_id";
    /** Training domain of the weights ({@code mining} / {@code chemical}). */
    public static final String DOMAIN_TAG = "hc_domain";
    /** Numeric precision of the weights ({@code fp16}). */
    public static final String PRECISION_TAG = "hc_precision";
    /** Training version / epoch the module was exported from. */
    public static final String VERSION_TAG = "hc_version";
    /** Completed-run lineage counter. */
    public static final String RUNS_TAG = "hc_runs";

    public static final String DEFAULT_PRECISION = "fp16";

    /**
     * The identity a pre-0.6.2 module reads as: 0.6.1 could only ever mint Qwen weights, so an
     * untagged module keeps working instead of vanishing from the router.
     */
    public static final String LEGACY_MODEL_ID = "qwen_9187_pro_max_ultra";

    public OpenWeightModuleItem(Properties properties) {
        super(properties);
    }

    /** Builds a freshly trained module for one model identity, domain and training version. */
    public static ItemStack create(ItemStack base, String modelId, String domain, String precision,
                                   int version, int runs) {
        ItemStack stack = base.copy();
        stack.setCount(1);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(MODEL_TAG, normalizedModelId(modelId));
        tag.putString(DOMAIN_TAG, domain);
        tag.putString(PRECISION_TAG, precision == null || precision.isEmpty() ? DEFAULT_PRECISION : precision);
        tag.putInt(VERSION_TAG, Math.max(1, version));
        tag.putInt(RUNS_TAG, Math.max(1, runs));
        return stack;
    }

    /**
     * 0.6.1-compatible factory: a module without a model identity. Kept so older call sites (and
     * datapack recipes) keep compiling; the model reads back as {@link #LEGACY_MODEL_ID}.
     */
    public static ItemStack create(ItemStack base, String domain, String precision, int version, int runs) {
        return create(base, LEGACY_MODEL_ID, domain, precision, version, runs);
    }

    /**
     * The module's open-weight model identity. A module written before 0.6.2 — or a plain stack
     * that never went through {@link #create} — reads back as {@link #LEGACY_MODEL_ID}.
     */
    public static String modelId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MODEL_TAG, net.minecraft.nbt.Tag.TAG_STRING)) {
            return LEGACY_MODEL_ID;
        }
        return normalizedModelId(tag.getString(MODEL_TAG));
    }

    /** True when the module carries an explicit 0.6.2+ identity instead of the legacy default. */
    public static boolean hasExplicitModelId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(MODEL_TAG, net.minecraft.nbt.Tag.TAG_STRING) &&
                !tag.getString(MODEL_TAG).isEmpty();
    }

    /** The catalog definition this module names, or null for an unknown id. */
    @Nullable
    public static AIModelDefinition definition(ItemStack stack) {
        return byPath(modelId(stack));
    }

    /**
     * Resolves a catalog model by its <b>bare path</b> ({@code qwen_9187_pro_max_ultra}), which is
     * what the NBT contract stores. {@link AIModels.Catalog#get(String)} parses a full
     * {@code namespace:path} and would resolve a bare path into {@code minecraft:...}, so the
     * lookup walks the catalog instead of guessing a namespace.
     */
    @Nullable
    private static AIModelDefinition byPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (AIModelDefinition definition : AIModels.catalog().all()) {
            if (definition.id().getPath().equals(path)) {
                return definition;
            }
        }
        return null;
    }

    /** Accepts only known open-weight ids; anything else degrades to the legacy Qwen identity. */
    private static String normalizedModelId(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return LEGACY_MODEL_ID;
        }
        AIModelDefinition definition = byPath(candidate);
        return definition != null && definition.isLocal() ? definition.id().getPath() : LEGACY_MODEL_ID;
    }

    /** The module's training domain, or an empty string for an untagged stack. */
    public static String domain(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(DOMAIN_TAG);
    }

    /** The module's numeric precision, or an empty string for an untagged stack. */
    public static String precision(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(PRECISION_TAG);
    }

    /** The module's training version (0 for an untagged stack). */
    public static int version(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(VERSION_TAG);
    }

    /** The module's completed-run lineage (0 for an untagged stack). */
    public static int runs(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(RUNS_TAG);
    }

    /**
     * A faithful copy of the module's whole training record (model identity, domain, precision,
     * version and lineage). Used by the cloning recipe so a duplicate is byte-identical in every
     * key that matters — never a blank module that only happens to look the same.
     */
    public static ItemStack copyWithRecord(ItemStack source, ItemStack base) {
        return create(base, modelId(source), domain(source), precision(source), version(source),
                runs(source));
    }

    /**
     * Migration hook for {@link #create}: models a module identity must be a LOCAL-camp model, so
     * an API model name can never be smuggled into a router slot through NBT.
     */
    public static boolean isDeployable(ItemStack stack) {
        AIModelDefinition definition = definition(stack);
        return definition != null && definition.isLocal();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        AIModelDefinition definition = definition(stack);
        if (definition != null) {
            tooltip.add(Component.translatable("item.hypercluster.open_weight_module.model",
                    Component.translatable(definition.displayKey())
                            .withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GOLD));
        }
        String domain = domain(stack);
        if (!domain.isEmpty()) {
            tooltip.add(Component.translatable("item.hypercluster.open_weight_module.summary",
                    Component.translatable("hypercluster.training.domain." + domain),
                    precision(stack).toUpperCase(java.util.Locale.ROOT),
                    String.valueOf(version(stack)))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("item.hypercluster.open_weight_module.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
