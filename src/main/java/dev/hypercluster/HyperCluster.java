package dev.hypercluster;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import dev.hypercluster.client.renderer.MoeScreenRender;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterFluids;
import dev.hypercluster.common.item.HyperClusterItems;

@Mod(HyperCluster.MOD_ID)
public final class HyperCluster {

    public static final String MOD_ID = "hypercluster";
    /** Id under which the MoE display's dynamic-render type is registered. */
    public static final ResourceLocation MOE_SCREEN_RENDER_TYPE = new ResourceLocation(MOD_ID, "moe_screen");
    public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager
            .getLogger("GregTech HyperCluster");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public HyperCluster() {
        HyperClusterFluids.registerListeners(FMLJavaModLoadingContext.get().getModEventBus());
        // P01: telemetry chain items (mining telemetry log, blank carrier, mining edge chip).
        HyperClusterItems.init();
        // 0.3: IV datacenter structure blocks (raised floor, 42U rack casings).
        HyperClusterBlocks.init();
        registerDynamicRenders();
        REGISTRATE.registerRegistrate();
    }

    /**
     * 0.6.3 · register the dynamic-render types this addon ships.
     *
     * <p>
     * Must happen before machine models are baked: the model JSON carries a
     * {@code dynamic_renders} list whose entries are decoded through
     * {@link com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager}'s type registry,
     * and data generation decodes the same codec. GTCEu does this in
     * {@code ClientProxy#init()} for its own renderers; doing it here covers the client run and
     * the data generator alike, and registering twice would throw, so it is guarded.
     */
    private static void registerDynamicRenders() {
        if (DynamicRenderManager.getType(MOE_SCREEN_RENDER_TYPE) == null) {
            DynamicRenderManager.register(MOE_SCREEN_RENDER_TYPE, MoeScreenRender.TYPE);
        }
    }
}
