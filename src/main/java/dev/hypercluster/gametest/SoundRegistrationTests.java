package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterSounds;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 0.6.3 · the eight industrial sound effects must be registered, wired into
 * {@code sounds.json}, and backed by real {@code .ogg} files.
 *
 * <p>
 * These tests catch the three ways a sound can silently fail: the entry never reaching
 * {@code GTRegistries.SOUNDS} (wrong registration hook), the JSON and the registry drifting
 * apart, and a JSON entry pointing at a file that is not in the jar.
 */
@GameTestHolder(HyperCluster.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoundRegistrationTests {

    private static final String BATCH = "hypercluster_sound";

    /** Every entry this mod ships, with the file name each maps to. */
    private static final String[] NAMES = { "datacenter_air_hum", "datacenter_liquid_hum",
            "pretraining_cluster", "satellite_uplink", "laser_burn", "red_team_scan", "rack_click",
            "terminal_alert" };

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void allEightSoundEntriesRegistered(GameTestHelper helper) {
        SoundEntry[] entries = HyperClusterSounds.all();
        helper.assertTrue(entries.length == 8, "expected eight sound entries, got " + entries.length);
        for (int i = 0; i < entries.length; i++) {
            SoundEntry entry = entries[i];
            helper.assertTrue(entry != null, "sound entry " + NAMES[i] + " was never initialised");
            ResourceLocation id = entry.getId();
            helper.assertTrue(id.getNamespace().equals(HyperCluster.MOD_ID),
                    NAMES[i] + " must live in the hypercluster namespace, got " + id);
            helper.assertTrue(id.getPath().equals(NAMES[i]),
                    "sound entry " + i + " should be " + NAMES[i] + ", got " + id.getPath());
            helper.assertTrue(GTRegistries.SOUNDS.containKey(id),
                    NAMES[i] + " is missing from GTRegistries.SOUNDS");
        }
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void soundEventsAreRegisteredWithForge(GameTestHelper helper) {
        for (String name : NAMES) {
            ResourceLocation id = HyperCluster.id(name);
            SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(id);
            helper.assertTrue(event != null, "no Forge sound event registered for " + id);
            helper.assertTrue(event.getLocation().equals(id),
                    "sound event " + id + " reports the wrong location " + event.getLocation());
        }
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void soundsJsonMatchesRegistry(GameTestHelper helper) {
        JsonObject json = readJson(helper, "sounds.json");
        helper.assertTrue(json != null, "assets/hypercluster/sounds.json is missing from the jar");
        for (String name : NAMES) {
            helper.assertTrue(json.has(name), "sounds.json has no entry for " + name);
            JsonObject entry = json.getAsJsonObject(name);
            helper.assertTrue(entry.has("sounds"), name + " has no sounds array");
            var list = entry.getAsJsonArray("sounds");
            helper.assertTrue(list.size() == 1, name + " should map to exactly one sound file");
            JsonObject first = list.get(0).getAsJsonObject();
            helper.assertTrue(first.has("name"), name + " sound file entry has no name");
            helper.assertTrue(first.get("name").getAsString().equals(HyperCluster.MOD_ID + ":" + name),
                    name + " points at the wrong file " + first.get("name").getAsString());
            helper.assertTrue("file".equals(first.get("type").getAsString()),
                    name + " must be a file sound, not " + first.get("type").getAsString());
        }
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void everyOggFileExists(GameTestHelper helper) {
        for (String name : NAMES) {
            String path = "/assets/" + HyperCluster.MOD_ID + "/sounds/" + name + ".ogg";
            try (var stream = HyperCluster.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null, "missing sound file " + path);
                byte[] header = stream.readNBytes(4);
                // Ogg container magic: "OggS".
                helper.assertTrue(header.length == 4 && header[0] == 'O' && header[1] == 'g' &&
                        header[2] == 'g' && header[3] == 'S',
                        path + " is not a valid Ogg stream");
            } catch (Exception exception) {
                helper.assertTrue(false, "could not read " + path + ": " + exception.getMessage());
            }
        }
        helper.succeed();
    }

    /**
     * Reads a top-level asset JSON from this mod's own classpath.
     *
     * <p>
     * Deliberately <em>not</em> the server's {@code ResourceManager}: that one is built for
     * {@code PackType.SERVER_DATA} and only ever sees {@code data/}, never {@code assets/}. The
     * class loader sees the mod's real resources — {@code build/resources/main} in a dev run and
     * the packaged jar in production — which is exactly what ships to players.
     */
    private static JsonObject readJson(GameTestHelper helper, String path) {
        String resource = "/assets/" + HyperCluster.MOD_ID + "/" + path;
        try (var stream = HyperCluster.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element.getAsJsonObject();
            }
        } catch (Exception exception) {
            helper.assertTrue(false, "could not parse " + resource + ": " + exception.getMessage());
            return null;
        }
    }
}
