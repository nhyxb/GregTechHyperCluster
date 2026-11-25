package dev.hypercluster.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A plain stackable item that appends one translatable tooltip line below its name.
 * The tooltip key is derived from the registry id: {@code item.<modid>.<name>.tooltip}.
 */
public class TooltipItem extends Item {

    public TooltipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this);
        tooltip.add(Component.translatable("item." + id.getNamespace() + "." + id.getPath() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
