package dev.hypercluster.common.item;

import net.minecraft.network.chat.MutableComponent;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.hypercluster.HyperCluster;

/**
 * All items of GregTech HyperCluster.
 *
 * <p>
 * The three telemetry-chain items are plain, stackable (64) items with one translatable
 * tooltip line each: they are the identity anchors for the whole 0.1 release (P01).
 */
public final class HyperClusterItems {

    public static final ItemEntry<TooltipItem> MINING_TELEMETRY_LOG = register("mining_telemetry_log",
            "Mining Telemetry Log", "Recorded by an ore-processing machine when a tracked recipe batch settles.");
    public static final ItemEntry<TooltipItem> BLANK_CARRIER = register("blank_carrier",
            "Blank Carrier", "Read-only carrier used to burn telemetry logs into an edge chip.");
    public static final ItemEntry<TooltipItem> MINING_EDGE_CHIP = register("mining_edge_chip",
            "Mining Edge Chip", "Edge-inference chip burned from telemetry. Unlocks enhanced ore-processing recipes.");

    private HyperClusterItems() {}

    public static void init() {}

    private static ItemEntry<TooltipItem> register(String name, String englishName, String englishTooltip) {
        MutableComponent lang = HyperCluster.REGISTRATE.addRawLang("item.hypercluster." + name + ".tooltip",
                englishTooltip);
        assert lang != null;
        return HyperCluster.REGISTRATE
                .item(name, TooltipItem::new)
                .model((context, provider) -> provider.withExistingParent(context.getName(),
                        provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("item/" + context.getName())))
                .lang(englishName)
                .register();
    }
}
