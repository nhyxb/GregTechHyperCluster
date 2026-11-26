package dev.hypercluster.common.data;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

import com.tterrag.registrate.util.entry.BlockEntry;
import dev.hypercluster.HyperCluster;

/**
 * GregTech HyperCluster structure blocks (0.3 · IV datacenter).
 *
 * <ul>
 * <li>{@code hypercluster:raised_floor} — the anti-static raised floor tile. Top and bottom use
 * {@code block/datacenter/raised_floor_top}, the four sides use
 * {@code block/datacenter/raised_floor_side}. Laying it under the whole footprint of an
 * {@code hypercluster:air_cooled_compute_rack} (plus open front/back air channels and a running
 * {@code hypercluster:precision_crac} nearby) upgrades the room from Tier I to Tier II.</li>
 * <li>{@code hypercluster:rack_casing_intake} — the 42U rack front (cold aisle face, perforated
 * intake door with the ice-blue cold-aisle stripe).</li>
 * <li>{@code hypercluster:rack_casing_exhaust} — the 42U rack back (hot aisle face, louvered
 * exhaust grille with the orange-red hot-aisle stripe).</li>
 * </ul>
 */
public final class HyperClusterBlocks {

    public static final BlockEntry<Block> RAISED_FLOOR = HyperCluster.REGISTRATE
            .block("raised_floor", Block::new)
            .properties(properties -> properties.mapColor(MapColor.METAL)
                    .strength(3.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL))
            .lang("Anti-Static Raised Floor")
            .blockstate((context, provider) -> provider.simpleBlock(context.getEntry(),
                    provider.models()
                            .cubeBottomTop(context.getName(),
                                    provider.modLoc("block/datacenter/raised_floor_side"),
                                    provider.modLoc("block/datacenter/raised_floor_top"),
                                    provider.modLoc("block/datacenter/raised_floor_top"))))
            .item()
            .model((context, provider) -> provider.withExistingParent(context.getName(),
                    provider.modLoc("block/raised_floor")))
            .build()
            .register();

    public static final BlockEntry<Block> RACK_CASING_INTAKE = HyperCluster.REGISTRATE
            .block("rack_casing_intake", Block::new)
            .properties(properties -> properties.mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL))
            .lang("42U Rack Casing (Intake Face)")
            .blockstate((context, provider) -> provider.simpleBlock(context.getEntry(),
                    provider.models()
                            .cubeAll(context.getName(),
                                    provider.modLoc("block/datacenter/rack_casing_front_intake"))))
            .item()
            .model((context, provider) -> provider.withExistingParent(context.getName(),
                    provider.modLoc("block/rack_casing_intake")))
            .build()
            .register();

    public static final BlockEntry<Block> RACK_CASING_EXHAUST = HyperCluster.REGISTRATE
            .block("rack_casing_exhaust", Block::new)
            .properties(properties -> properties.mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL))
            .lang("42U Rack Casing (Exhaust Face)")
            .blockstate((context, provider) -> provider.simpleBlock(context.getEntry(),
                    provider.models()
                            .cubeAll(context.getName(),
                                    provider.modLoc("block/datacenter/rack_casing_back_exhaust"))))
            .item()
            .model((context, provider) -> provider.withExistingParent(context.getName(),
                    provider.modLoc("block/rack_casing_exhaust")))
            .build()
            .register();

    private HyperClusterBlocks() {}

    /** Called from the mod constructor; loads the static entries into the registrate. */
    public static void init() {}
}
