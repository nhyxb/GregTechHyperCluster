package dev.hypercluster.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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

    /** Training domain of the weights ({@code mining} / {@code chemical}). */
    public static final String DOMAIN_TAG = "hc_domain";
    /** Numeric precision of the weights ({@code fp16}). */
    public static final String PRECISION_TAG = "hc_precision";
    /** Training version / epoch the module was exported from. */
    public static final String VERSION_TAG = "hc_version";
    /** Completed-run lineage counter. */
    public static final String RUNS_TAG = "hc_runs";

    public static final String DEFAULT_PRECISION = "fp16";

    public OpenWeightModuleItem(Properties properties) {
        super(properties);
    }

    /** Builds a freshly trained module for one domain at the given training version. */
    public static ItemStack create(ItemStack base, String domain, String precision, int version, int runs) {
        ItemStack stack = base.copy();
        stack.setCount(1);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(DOMAIN_TAG, domain);
        tag.putString(PRECISION_TAG, precision == null || precision.isEmpty() ? DEFAULT_PRECISION : precision);
        tag.putInt(VERSION_TAG, Math.max(1, version));
        tag.putInt(RUNS_TAG, Math.max(1, runs));
        return stack;
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
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
