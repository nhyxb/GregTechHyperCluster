package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.registry.registrate.SoundEntryBuilder;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import dev.hypercluster.HyperCluster;

/**
 * 0.6.3 · data generation for the addon.
 *
 * <p>
 * Only one provider is needed: GTCEu's own {@link SoundEntryBuilder.SoundEntryProvider} walks
 * {@code GTRegistries.SOUNDS} and writes every entry belonging to this mod id, which keeps
 * {@code assets/hypercluster/sounds.json} in lock-step with {@link HyperClusterSounds}. Writing
 * the JSON by hand would let the two drift apart silently.
 *
 * <p>
 * The provider only runs when the {@code registerSounds()} hook has already populated the
 * registry; in a data run that hook fires as part of {@code GTSoundEntries.init()}.
 */
@Mod.EventBusSubscriber(modid = HyperCluster.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HyperClusterDataGenerators {

    private HyperClusterDataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        if (event.includeClient()) {
            generator.addProvider(true, new SoundEntryBuilder.SoundEntryProvider(packOutput, HyperCluster.MOD_ID));
        }
    }
}
