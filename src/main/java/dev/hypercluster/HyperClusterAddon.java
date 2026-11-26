package dev.hypercluster;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;

import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.data.HyperClusterRecipeTypes;
import dev.hypercluster.common.data.HyperClusterRecipes;

@GTAddon
public final class HyperClusterAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return HyperCluster.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        // Runs after GTCEu registered and froze its own content (see CommonProxy.init in 7.4.0).
        HyperCluster.LOGGER.info("initializing content (recipe types + machines)");
        HyperClusterRecipeTypes.init();
        HyperClusterMachines.init();
        HyperCluster.LOGGER.info("content ready: micro burner + telemetry ore washer chain");
    }

    @Override
    public String addonModId() {
        return HyperCluster.MOD_ID;
    }

    @Override
    public void addRecipes(java.util.function.Consumer<FinishedRecipe> provider) {
        HyperClusterRecipes.init(provider);
    }

    @Override
    public void removeRecipes(java.util.function.Consumer<ResourceLocation> provider) {}
}
