package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.world.item.Item;

import dev.hypercluster.common.item.HyperClusterItems;

/** Recipe domains and host-level deduplication for the telemetry hatch. */
public final class TelemetrySettlement {

    private TelemetrySettlement() {}

    /**
     * The host recipe type's domain: {@code mining} for the ore-processing chain, {@code chemical}
     * for the chemistry chain, or {@code null} for types that never log.
     */
    public static String domainOf(GTRecipeType type) {
        if (type == null) {
            return null;
        }
        if (type == GTRecipeTypes.ORE_WASHER_RECIPES || type == GTRecipeTypes.MACERATOR_RECIPES ||
                type == GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES || type == GTRecipeTypes.CENTRIFUGE_RECIPES ||
                type == GTRecipeTypes.SIFTER_RECIPES || type == GTRecipeTypes.ELECTROMAGNETIC_SEPARATOR_RECIPES) {
            return "mining";
        }
        if (type == GTRecipeTypes.CHEMICAL_RECIPES || type == GTRecipeTypes.LARGE_CHEMICAL_RECIPES ||
                type == GTRecipeTypes.DISTILLATION_RECIPES || type == GTRecipeTypes.CRACKING_RECIPES ||
                type == GTRecipeTypes.PYROLYSE_RECIPES) {
            return "chemical";
        }
        return null;
    }

    /** The log item of a domain, or {@code null} for unknown domains. */
    public static Item logItemOf(String domain) {
        if ("mining".equals(domain)) {
            return HyperClusterItems.MINING_TELEMETRY_LOG.get();
        }
        if ("chemical".equals(domain)) {
            return HyperClusterItems.CHEMICAL_TELEMETRY_LOG.get();
        }
        return null;
    }

    /**
     * Deduplicates the telemetry parts of one host: several hatches on the same controller settle
     * one batch together and exactly one of them (the lowest-positioned one) books the log.
     */
    public static boolean shouldBook(TelemetryHatchMachine self, IMultiController controller) {
        if (controller == null) {
            return false;
        }
        TelemetryHatchMachine primary = null;
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof TelemetryHatchMachine hatch &&
                    (primary == null || hatch.getPos().compareTo(primary.getPos()) < 0)) {
                primary = hatch;
            }
        }
        return primary == self;
    }
}
