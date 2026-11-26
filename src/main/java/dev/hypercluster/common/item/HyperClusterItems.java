package dev.hypercluster.common.item;

import net.minecraft.network.chat.MutableComponent;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.hypercluster.HyperCluster;

/**
 * All items of GregTech HyperCluster.
 *
 * <p>
 * The telemetry-chain items are plain, stackable (64) items with one translatable tooltip line
 * each: they are the identity anchors of the 0.1 release (P01) and the 0.2 data workshop.
 */
public final class HyperClusterItems {

    // 0.1 — mining telemetry chain.
    public static final ItemEntry<TooltipItem> MINING_TELEMETRY_LOG = register("mining_telemetry_log",
            "Mining Telemetry Log", "Recorded by an ore-processing machine when a tracked recipe batch settles.");
    public static final ItemEntry<TooltipItem> BLANK_CARRIER = register("blank_carrier",
            "Blank Carrier", "Read-only carrier used to burn telemetry logs into an edge chip.");
    public static final ItemEntry<TooltipItem> MINING_EDGE_CHIP = register("mining_edge_chip",
            "Mining Edge Chip", "Edge-inference chip burned from telemetry. Unlocks enhanced ore-processing recipes.");

    // 0.2 — chemical telemetry + data workshop chain.
    public static final ItemEntry<TooltipItem> CHEMICAL_TELEMETRY_LOG = register("chemical_telemetry_log",
            "Chemical Telemetry Log",
            "Recorded by a chemical machine when a tracked recipe batch settles. Scrub it in the data tower.");
    public static final ItemEntry<TooltipItem> CLEAN_DATASET = register("clean_dataset",
            "Clean Dataset", "Interference-free residue of telemetry scrubbing. Formats blank carriers.");
    public static final ItemEntry<TooltipItem> HIGH_PURITY_MINING_DATASET = register("high_purity_mining_dataset",
            "High-Purity Mining Dataset", "Domain dataset refined from mining telemetry. Burns mining chips fast.");
    public static final ItemEntry<TooltipItem> HIGH_PURITY_CHEMICAL_DATASET = register(
            "high_purity_chemical_dataset",
            "High-Purity Chemical Dataset", "Domain dataset refined from chemical telemetry. Burns chemical chips.");
    public static final ItemEntry<TooltipItem> CORRUPTED_NOISE = register("corrupted_noise",
            "Corrupted Noise", "Unreadable telemetry garbage. Recycle it in the data tower for raw silicon.");
    public static final ItemEntry<TooltipItem> CHEMICAL_EDGE_CHIP = register("chemical_edge_chip",
            "Chemical Edge Chip", "Edge-inference chip for the chemical domain. Unlocks reagent-saving recipes.");

    // 0.2.1 — fine-tuning (LoRA) + red-team alignment chain.
    public static final ItemEntry<TooltipItem> GOLDEN_DATASET = register("golden_dataset",
            "Golden Dataset", "Golden-labelled samples exhaled by the scrubbing tower. Perfect engraving grade.");
    public static final ItemEntry<TooltipItem> MINING_LORA_CARD = register("mining_lora_card",
            "Mining LoRA Card", "Domain fine-tune card engraved from high-purity mining data. Circuit #3 recipes.");
    public static final ItemEntry<TooltipItem> CHEMICAL_LORA_CARD = register("chemical_lora_card",
            "Chemical LoRA Card",
            "Domain fine-tune card engraved from high-purity chemical data. Circuit #3 recipes.");
    public static final ItemEntry<TooltipItem> ALIGNED_MINING_EDGE_CHIP = register("aligned_mining_edge_chip",
            "Aligned Mining Edge Chip",
            "Passed red-team adversarial validation. Stable circuit #4 mining recipes at 25% less energy.");
    public static final ItemEntry<TooltipItem> ALIGNED_CHEMICAL_EDGE_CHIP = register(
            "aligned_chemical_edge_chip",
            "Aligned Chemical Edge Chip",
            "Passed red-team adversarial validation. Stable circuit #4 chemical recipes at 25% less energy.");

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
