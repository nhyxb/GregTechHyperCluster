package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterBlocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Server-side index of formed controllers resting on complete rectangular raised-floor components. */
@Mod.EventBusSubscriber(modid = HyperCluster.MOD_ID)
public final class DatacenterFloorNetwork {

    private static final int MIN_SIDE = 5;
    private static final int MAX_SIDE = 24;
    private static final int MAX_ASPECT_RATIO = 3;

    private static final Map<ServerLevel, DatacenterFloorNetwork> NETWORKS = new HashMap<>();

    private final Map<BlockPos, Optional<Room>> rooms = new HashMap<>();
    private final Map<MultiblockControllerMachine, List<BlockPos>> footprints = new LinkedHashMap<>();
    private final Map<MultiblockControllerMachine, List<MultiblockControllerMachine>> peers = new HashMap<>();
    private boolean membershipDirty = true;

    public record Room(BlockPos min, BlockPos max) {}

    private DatacenterFloorNetwork() {}

    private static DatacenterFloorNetwork network(ServerLevel level) {
        return NETWORKS.computeIfAbsent(level, ignored -> new DatacenterFloorNetwork());
    }

    public static void formed(MultiblockControllerMachine machine) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // GT's matched positions are already rotated and mirrored. Capture the actual plinth once.
        Collection<BlockPos> positions = machine.getMultiblockState().getCache();
        int bottom = positions.stream().mapToInt(BlockPos::getY).min().orElse(machine.getPos().getY());
        List<BlockPos> footprint = positions.stream()
                .filter(pos -> pos.getY() == bottom && !level.getBlockState(pos).isAir())
                .map(BlockPos::below).toList();
        var network = network(level);
        network.footprints.put(machine, footprint);
        network.membershipDirty = true;
    }

    public static void detached(MultiblockControllerMachine machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            var network = NETWORKS.get(level);
            if (network != null && network.footprints.remove(machine) != null) {
                network.membershipDirty = true;
            }
        }
    }

    public static void floorChanged(ServerLevel level) {
        var network = NETWORKS.get(level);
        if (network != null) {
            network.rooms.clear();
            network.membershipDirty = true;
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.getServer().isSameThread()) {
                floorChanged(level);
            } else {
                level.getServer().execute(() -> floorChanged(level));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            floorChanged(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NETWORKS.remove(level);
        }
    }

    public static Optional<Room> roomAt(ServerLevel level, BlockPos pos) {
        var network = network(level);
        BlockPos key = pos.immutable();
        Optional<Room> cached = network.rooms.get(key);
        return cached == null ? network.validate(level, key) : cached;
    }

    public static <T> List<T> members(MultiblockControllerMachine machine, Class<T> type) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        var network = network(level);
        if (network.membershipDirty) {
            network.rebuildMembership(level);
        }
        return network.peers.getOrDefault(machine, List.of()).stream()
                .filter(peer -> peer != machine && peer.isFormed() && type.isInstance(peer))
                .map(type::cast).toList();
    }

    private void rebuildMembership(ServerLevel level) {
        peers.clear();
        Map<Room, List<MultiblockControllerMachine>> byRoom = new HashMap<>();
        for (var entry : footprints.entrySet()) {
            List<BlockPos> footprint = entry.getValue();
            if (!footprint.isEmpty()) {
                roomAt(level, footprint.get(0)).filter(room -> footprint.stream().allMatch(pos -> contains(room, pos)))
                        .ifPresent(room -> {
                            var members = byRoom.computeIfAbsent(room, ignored -> new ArrayList<>());
                            members.add(entry.getKey());
                            peers.put(entry.getKey(), members);
                        });
            }
        }
        byRoom.values().forEach(members -> members.sort(Comparator.comparing(MultiblockControllerMachine::getPos)));
        membershipDirty = false;
    }

    private static boolean contains(Room room, BlockPos pos) {
        return pos.getY() == room.min().getY() &&
                pos.getX() >= room.min().getX() && pos.getX() <= room.max().getX() &&
                pos.getZ() >= room.min().getZ() && pos.getZ() <= room.max().getZ();
    }

    private Optional<Room> validate(ServerLevel level, BlockPos seed) {
        if (!level.hasChunkAt(seed) || !level.getBlockState(seed).is(HyperClusterBlocks.RAISED_FLOOR.get())) {
            rooms.put(seed, Optional.empty());
            return Optional.empty();
        }

        Set<BlockPos> tiles = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        tiles.add(seed);
        pending.add(seed);
        int minX = seed.getX(), maxX = seed.getX(), minZ = seed.getZ(), maxZ = seed.getZ();
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            if (maxX - minX >= MAX_SIDE || maxZ - minZ >= MAX_SIDE) {
                return cache(tiles, Optional.empty());
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(direction);
                if (!level.hasChunkAt(neighbor)) {
                    return cache(tiles, Optional.empty());
                }
                if (level.getBlockState(neighbor).is(HyperClusterBlocks.RAISED_FLOOR.get()) && tiles.add(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int shortest = Math.min(width, depth);
        int longest = Math.max(width, depth);
        if (shortest < MIN_SIDE || longest > shortest * MAX_ASPECT_RATIO || tiles.size() != width * depth) {
            return cache(tiles, Optional.empty());
        }
        return cache(tiles, Optional.of(new Room(new BlockPos(minX, seed.getY(), minZ),
                new BlockPos(maxX, seed.getY(), maxZ))));
    }

    private Optional<Room> cache(Set<BlockPos> tiles, Optional<Room> room) {
        tiles.forEach(pos -> rooms.put(pos, room));
        return room;
    }
}
