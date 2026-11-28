package dev.hypercluster;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;

import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.data.HyperClusterRecipeTypes;
import dev.hypercluster.common.data.HyperClusterRecipes;
import dev.hypercluster.common.data.HyperClusterSounds;

@GTAddon
public final class HyperClusterAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return HyperCluster.REGISTRATE;
    }

    /**
     * Sounds must be added before GTCEu calls {@code SoundEntry::prepare} and freezes
     * {@code GTRegistries.SOUNDS} — i.e. from this hook, which {@code GTSoundEntries.init()}
     * invokes. {@link #initializeAddon()} runs after that freeze.
     */
    @Override
    public void registerSounds() {
        HyperClusterSounds.init();
    }

    @Override
    public void initializeAddon() {
        // Runs after GTCEu registered and froze its own content (see CommonProxy.init in 7.4.0).
        HyperCluster.LOGGER.info("initializing content (recipe types + machines)");
        HyperClusterRecipeTypes.init();
        HyperClusterMachines.init();
        dev.hypercluster.common.machine.datacenter.IndustrialInference.installRecipeModifiers();
        HyperCluster.LOGGER
                .info("content ready: micro burner + washer/chemical reactor telemetry chains + scrubbing tower");
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
