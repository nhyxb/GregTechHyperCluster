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
 * 0.4 · the Token credit card ({@code hypercluster:token_credit_card}).
 *
 * <p>
 * A stackable card carrying an integer number of Tokens in its NBT. Insert it into a satellite
 * station's card slot and use the deposit / withdraw actions: deposit moves the card's whole
 * value into the bound account, withdraw moves the account's whole balance onto the card. The
 * card is a convenience carrier only — the server-side ledger stays the single source of truth
 * for balances, and card actions are executed by the station machine on the server.
 */
public class TokenCreditCardItem extends Item {

    public static final String VALUE_TAG = "hc_token_value";

    public TokenCreditCardItem(Properties properties) {
        super(properties);
    }

    /** The Token value the card carries (0 while blank). */
    public static long value(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(VALUE_TAG);
    }

    /** Writes the card's Token value. */
    public static void setValue(ItemStack stack, long value) {
        if (value <= 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(VALUE_TAG);
            }
            return;
        }
        stack.getOrCreateTag().putLong(VALUE_TAG, value);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.hypercluster.token_credit_card.value",
                String.format("%,d", value(stack))).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.hypercluster.token_credit_card.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
