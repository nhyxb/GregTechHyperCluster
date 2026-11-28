package dev.hypercluster.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import dev.hypercluster.HyperCluster;

/** Reports the first rendered main menu during an opted-in development client run. */
@Mod.EventBusSubscriber(modid = HyperCluster.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSmokeTest {

    private static boolean titleScreenReported;

    private ClientSmokeTest() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!FMLEnvironment.production && Boolean.getBoolean("hypercluster.clientSmoke")) {
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(ClientSmokeTest::onScreenRendered));
        }
    }

    private static void onScreenRendered(ScreenEvent.Render.Post event) {
        if (!titleScreenReported && event.getScreen() instanceof TitleScreen &&
                Minecraft.getInstance().getOverlay() == null) {
            titleScreenReported = true;
            HyperCluster.LOGGER.info("Client smoke: title screen rendered");
        }
    }
}
