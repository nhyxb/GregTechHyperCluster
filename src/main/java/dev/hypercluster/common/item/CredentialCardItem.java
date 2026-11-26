package dev.hypercluster.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * 0.4 · the API credential card ({@code hypercluster:api_credential_card}).
 *
 * <p>
 * A stackable identity card that binds, once, to a player UUID: right-click it against any
 * {@code hypercluster:api_satellite_station} controller to write the using player's UUID into
 * the card. Inserting a bound card into a station's credential slot opens that player's cloud
 * account in the shared ledger (the first open anywhere grants the one-time welcome Tokens).
 * The card itself never stores a balance — the ledger is the single source of truth.
 */
public class CredentialCardItem extends Item {

    public static final String OWNER_TAG = "hc_cloud_owner";
    public static final String OWNER_NAME_TAG = "hc_cloud_owner_name";

    public CredentialCardItem(Properties properties) {
        super(properties);
    }

    /** Writes the player binding into the card (called by the station's use hook). */
    public static void bind(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(OWNER_TAG, owner);
        tag.putString(OWNER_NAME_TAG, ownerName);
    }

    /** The bound player UUID, or null while the card is still blank. */
    @Nullable
    public static UUID owner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(OWNER_TAG)) {
            return null;
        }
        try {
            return tag.getUUID(OWNER_TAG);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String ownerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(OWNER_NAME_TAG);
    }

    public static boolean isBound(ItemStack stack) {
        return owner(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        UUID owner = owner(stack);
        if (owner != null) {
            String name = ownerName(stack);
            tooltip.add(Component.translatable("item.hypercluster.api_credential_card.bound",
                    name.isEmpty() ? owner.toString() : name).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.hypercluster.api_credential_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.hypercluster.api_credential_card.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
