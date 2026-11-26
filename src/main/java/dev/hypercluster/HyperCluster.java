package dev.hypercluster;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.item.HyperClusterItems;

@Mod(HyperCluster.MOD_ID)
public final class HyperCluster {

    public static final String MOD_ID = "hypercluster";
    public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager
            .getLogger("GregTech HyperCluster");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public HyperCluster() {
        // P01: telemetry chain items (mining telemetry log, blank carrier, mining edge chip).
        HyperClusterItems.init();
        // 0.3: IV datacenter structure blocks (raised floor, 42U rack casings).
        HyperClusterBlocks.init();
        REGISTRATE.registerRegistrate();
    }
}
