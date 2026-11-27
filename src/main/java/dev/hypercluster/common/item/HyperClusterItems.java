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

    // 0.3 — IV self-built compute: air-cooled datacenter chain.
    public static final ItemEntry<TooltipItem> IV_COMPUTE_BLADE = register("iv_compute_blade",
            "IV Compute Blade", "Hot-swappable IV air-cooled compute blade. Each grants 6 CWU/t in a compute rack.");
    public static final ItemEntry<TooltipItem> GREEN_SNACK_PACK = register("green_snack_pack",
            "Green Snack Pack", "Machine-room mascot snack. Keep one in the compute rack slot for tidy efficiency.");

    // 0.4 — IV closed-source cloud: identity and Tokens.
    public static final ItemEntry<CredentialCardItem> API_CREDENTIAL_CARD = registerCard(
            "api_credential_card", CredentialCardItem::new, "API Credential Card",
            "item.hypercluster.api_credential_card.tooltip",
            "Bind it against a satellite station controller, then keep it in the station's credential slot to operate your cloud account.",
            "item.hypercluster.api_credential_card.bound", "Bound account: %s",
            "item.hypercluster.api_credential_card.unbound", "Unbound — use it on a satellite station controller.");
    public static final ItemEntry<TokenCreditCardItem> TOKEN_CREDIT_CARD = registerCard(
            "token_credit_card", TokenCreditCardItem::new, "Token Credit Card",
            "item.hypercluster.token_credit_card.tooltip",
            "Carries Tokens between your pocket and a satellite station's card slot.",
            "item.hypercluster.token_credit_card.value", "Stored Tokens: %s");

    // 0.6 — LuV training: liquid-cooled blades and the open-weight model artifact.
    public static final ItemEntry<TooltipItem> LUV_LIQUID_BLADE = register("luv_liquid_blade",
            "LuV Liquid-Cooled Blade",
            "Cold-plate liquid-cooled compute blade. Each blade grants 16 CWU/t in a Tier III liquid rack.");
    public static final ItemEntry<OpenWeightModuleItem> OPEN_WEIGHT_MODULE = registerModule(
            "open_weight_module", "Open-Weight Module",
            "item.hypercluster.open_weight_module.tooltip",
            "A trained open-weight model checkpoint. Its NBT records the domain, precision and training version.",
            "item.hypercluster.open_weight_module.summary", "%s · %s · v%s");

    // 0.6.1 — TB-scale cluster memory and the uncensored-model patch card.
    public static final ItemEntry<TooltipItem> IV_HBM_MODULE = register("iv_hbm_module",
            "IV HBM Module",
            "4 TB of high-bandwidth cluster memory. Four of them fill one memory rack (16 TB).");
    public static final ItemEntry<TooltipItem> LUV_HBM_MODULE = register("luv_hbm_module",
            "LuV HBM Module",
            "16 TB of high-bandwidth cluster memory. Four of them fill one memory rack (64 TB).");
    public static final ItemEntry<TooltipItem> JAILBREAK_CARD = register("jailbreak_card",
            "Jailbreak Patch Card",
            "Unlocks whitelisted dangerous chemistry rounds on a morally-refusing closed-source model.");

    private HyperClusterItems() {}

    public static void init() {
        // Patchouli manual metadata keys (used only when Patchouli is installed; zero hard dependency).
        HyperCluster.REGISTRATE.addRawLang("book.hypercluster.hypercluster_manual.name", "HyperCluster Tech Manual");
        HyperCluster.REGISTRATE.addRawLang("book.hypercluster.hypercluster_manual.subtitle",
                "HV–IV Telemetry, Datacenters & Cloud Uplink");
        HyperCluster.REGISTRATE.addRawLang("book.hypercluster.hypercluster_manual.landing",
                "This manual covers the HV through IV computing chain currently implemented in GregTech HyperCluster.$(br2)" +
                        "It walks through gathering telemetry from HV ore washing and burning edge chips, purifying datasets with LoRA fine-tuning and red-team alignment in EV, and scaling into either an IV air-cooled datacenter or a closed-source cloud satellite station.$(br2)" +
                        "Check EMI / JEI for full recipe lookups and multiblock previews.");
    }

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

    /** Cards with their own NBT item classes: same model/lang treatment, tooltip keys passed raw. */
    private static <T extends net.minecraft.world.item.Item> ItemEntry<T> registerCard(String name,
                                                                                       com.tterrag.registrate.util.nullness.NonNullFunction<net.minecraft.world.item.Item.Properties, T> factory,
                                                                                       String englishName,
                                                                                       String... langKeyValuePairs) {
        for (int i = 0; i + 1 < langKeyValuePairs.length; i += 2) {
            HyperCluster.REGISTRATE.addRawLang(langKeyValuePairs[i], langKeyValuePairs[i + 1]);
        }
        return HyperCluster.REGISTRATE
                .item(name, factory)
                .model((context, provider) -> provider.withExistingParent(context.getName(),
                        provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("item/" + context.getName())))
                .lang(englishName)
                .register();
    }

    /** 0.6 — the trained open-weight module: its own item class renders the NBT summary line. */
    private static ItemEntry<OpenWeightModuleItem> registerModule(String name, String englishName,
                                                                  String... langKeyValuePairs) {
        for (int i = 0; i + 1 < langKeyValuePairs.length; i += 2) {
            HyperCluster.REGISTRATE.addRawLang(langKeyValuePairs[i], langKeyValuePairs[i + 1]);
        }
        return HyperCluster.REGISTRATE
                .item(name, OpenWeightModuleItem::new)
                .model((context, provider) -> provider.withExistingParent(context.getName(),
                        provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("item/" + context.getName())))
                .lang(englishName)
                .register();
    }
}
