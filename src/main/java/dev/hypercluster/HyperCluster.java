package dev.hypercluster;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;

import dev.hypercluster.common.item.HyperClusterItems;

@Mod(HyperCluster.MOD_ID)
public final class HyperCluster {

    public static final String MOD_ID = "hypercluster";
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public HyperCluster() {
        REGISTRATE.item("development_probe", Item::new)
                .model((context, provider) -> provider.withExistingParent(context.getName(),
                        provider.mcLoc("item/generated")).texture("layer0", provider.mcLoc("item/paper")))
                .lang("Development Probe")
                .register();
        // P01: telemetry chain items (mining telemetry log, blank carrier, mining edge chip).
        HyperClusterItems.init();
        REGISTRATE.registerRegistrate();
    }
}
