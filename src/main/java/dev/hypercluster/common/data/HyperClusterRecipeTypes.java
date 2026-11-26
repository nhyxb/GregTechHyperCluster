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
 * <li>{@code hypercluster:micro_burner} — the HV micro burner: mines telemetry logs and blank
 * carriers into mining edge chips.</li>
 * <li>{@code hypercluster:telemetry_ore_washing} — the own HV telemetry ore washer recipe type.
 * Hosts the mirrored base wash recipe (circuit #1, 1000mB water) and the chip-enhanced wash recipe
 * (circuit #2 + a non-consumed mining edge chip, 100mB water). The two recipes are strictly
 * separated by their programmed circuit numbers, so the same inputs can never silently match
 * both.</li>
 * </ul>
 */
public final class HyperClusterRecipeTypes {

    public static GTRecipeType MICRO_BURNER_RECIPES;
    public static GTRecipeType TELEMETRY_ORE_WASHING_RECIPES;

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
                .setMaxIOSize(2, 3, 1, 0)
                .setEUIO(IO.IN)
                .prepareBuilder(builder -> builder.EUt(16).duration(400))
                .setSlotOverlay(false, false, GuiTextures.CRUSHED_ORE_OVERLAY)
                .setSlotOverlay(true, false, GuiTextures.DUST_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, LEFT_TO_RIGHT)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.BATH);

        GTRegistries.RECIPE_TYPES.freeze();
        GTRegistries.RECIPE_CATEGORIES.freeze();
    }

    private static GTRecipeType register(String name) {
        ResourceLocation id = HyperCluster.id(name);
        GTRecipeType type = new GTRecipeType(id, "electric");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, type);
        return type;
    }
}
