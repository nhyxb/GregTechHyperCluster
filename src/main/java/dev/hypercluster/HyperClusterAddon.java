package dev.hypercluster;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@GTAddon
public final class HyperClusterAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return HyperCluster.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return HyperCluster.MOD_ID;
    }
}
