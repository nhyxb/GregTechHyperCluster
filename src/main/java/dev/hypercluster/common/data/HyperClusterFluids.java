package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;

import net.minecraftforge.eventbus.api.IEventBus;

import dev.hypercluster.HyperCluster;

public final class HyperClusterFluids {

    private HyperClusterFluids() {}

    public static Material HOT_PCB_COOLANT;

    public static void registerListeners(IEventBus bus) {
        bus.addListener(HyperClusterFluids::registerRegistry);
        bus.addListener(HyperClusterFluids::registerMaterials);
    }

    private static void registerRegistry(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(HyperCluster.MOD_ID);
    }

    private static void registerMaterials(MaterialEvent event) {
        HOT_PCB_COOLANT = new Material.Builder(HyperCluster.id("hot_pcb_coolant"))
                .fluid()
                .color(0xD56A36)
                .langValue("Hot PCB Coolant")
                .buildAndRegister();
    }
}
