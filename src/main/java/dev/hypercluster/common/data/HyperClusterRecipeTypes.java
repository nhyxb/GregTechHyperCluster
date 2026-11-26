package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import dev.hypercluster.HyperCluster;

import static com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.LEFT_TO_RIGHT;

/**
 * GregTech HyperCluster recipe types (GTCEu 7.4.0).
 *
 * <ul>
 * <li>{@code hypercluster:micro_burner} — the HV micro burner: telemetry logs/datasets and blank
 * carriers into domain edge chips.</li>
 * <li>{@code hypercluster:telemetry_ore_washing} — the own HV telemetry ore washer recipe type.
 * Hosts the mirrored base wash recipe (circuit #1, 1000mB water) and the chip-enhanced wash recipe
 * (circuit #2 + a non-consumed mining edge chip, 100mB water). The two recipes are strictly
 * separated by their programmed circuit numbers, so the same inputs can never silently match
 * both.</li>
 * <li>{@code hypercluster:telemetry_chemical_processing} (0.2) — the own EV telemetry chemical
 * reactor recipe type. Hosts the mirrored base indium separation recipe (circuit #1, 4 aluminium
 * dust) and its chip-enhanced counterpart (circuit #2 + a non-consumed chemical edge chip, 2
 * aluminium dust — same products, half the auxiliary reagent).</li>
 * <li>{@code hypercluster:data_scrubbing} (0.2) — the EV data scrubbing tower recipe type. Raw
 * telemetry logs (mining or chemical) + distilled water fraction into a high-purity domain
 * dataset + a clean dataset + corrupted noise; corrupted noise is recycled here into raw
 * silicon.</li>
 * </ul>
 */
public final class HyperClusterRecipeTypes {

    public static GTRecipeType MICRO_BURNER_RECIPES;
    public static GTRecipeType TELEMETRY_ORE_WASHING_RECIPES;
    public static GTRecipeType TELEMETRY_CHEMICAL_PROCESSING_RECIPES;
    public static GTRecipeType DATA_SCRUBBING_RECIPES;

    private HyperClusterRecipeTypes() {}

    /**
     * Called from {@code IGTAddon#initializeAddon()}, i.e. after GTCEu registered and froze its
     * own recipe types. GTCEu's own test utility uses the same unfreeze/register/freeze pattern,
     * and GTCEu itself registers recipe types during the same construct phase.
     */
    public static void init() {
        GTRegistries.RECIPE_TYPES.unfreeze();
        GTRegistries.RECIPE_CATEGORIES.unfreeze();

        MICRO_BURNER_RECIPES = register("micro_burner")
                .setMaxIOSize(2, 1, 0, 0)
                .setEUIO(IO.IN)
                .prepareBuilder(builder -> builder.EUt(GTValues.V[GTValues.HV]).duration(128))
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, LEFT_TO_RIGHT);

        TELEMETRY_ORE_WASHING_RECIPES = register("telemetry_ore_washing")
                .setMaxIOSize(3, 3, 1, 0)
                .setEUIO(IO.IN)
                .prepareBuilder(builder -> builder.EUt(16).duration(400))
                .setSlotOverlay(false, false, GuiTextures.CRUSHED_ORE_OVERLAY)
                .setSlotOverlay(true, false, GuiTextures.DUST_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, LEFT_TO_RIGHT)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.BATH);

        TELEMETRY_CHEMICAL_PROCESSING_RECIPES = register("telemetry_chemical_processing")
                .setMaxIOSize(3, 3, 2, 1)
                .setEUIO(IO.IN)
                .prepareBuilder(builder -> builder.EUt(600).duration(50))
                .setSlotOverlay(false, false, GuiTextures.MOLECULAR_OVERLAY_1)
                .setSlotOverlay(false, false, true, GuiTextures.MOLECULAR_OVERLAY_2)
                .setSlotOverlay(true, false, GuiTextures.MOLECULAR_OVERLAY_3)
                .setSlotOverlay(true, false, true, GuiTextures.MOLECULAR_OVERLAY_4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW_MULTIPLE, LEFT_TO_RIGHT)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.CHEMICAL);

        DATA_SCRUBBING_RECIPES = register("data_scrubbing", "multiblock")
                .setMaxIOSize(4, 3, 2, 0)
                .setEUIO(IO.IN)
                .prepareBuilder(builder -> builder.EUt(GTValues.V[GTValues.EV]).duration(200))
                .setSlotOverlay(false, false, GuiTextures.MOLECULAR_OVERLAY_1)
                .setSlotOverlay(true, false, GuiTextures.PATTERN_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, LEFT_TO_RIGHT)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.BATH);

        GTRegistries.RECIPE_TYPES.freeze();
        GTRegistries.RECIPE_CATEGORIES.freeze();
    }

    private static GTRecipeType register(String name) {
        return register(name, "electric");
    }

    private static GTRecipeType register(String name, String category) {
        ResourceLocation id = HyperCluster.id(name);
        GTRecipeType type = new GTRecipeType(id, category);
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, type);
        return type;
    }
}
