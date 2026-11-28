package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Combines the local plinth index with GTCEu's directed, cached optical pipe routes. */
public final class DatacenterOpticalNetwork {

    private DatacenterOpticalNetwork() {}

    public record Link(MetaMachine source, IOpticalComputationProvider provider) {

        private boolean visited(Collection<IOpticalComputationProvider> seen) {
            return seen.contains(provider) ||
                    source instanceof IOpticalComputationProvider direct && seen.contains(direct);
        }
    }

    public static List<Link> providers(MultiblockControllerMachine machine, boolean useRoomSwitch) {
        if (!machine.isFormed()) {
            return List.of();
        }

        Map<MetaMachine, Link> links = new LinkedHashMap<>();
        var switches = useRoomSwitch ? DatacenterFloorNetwork.members(machine, OpticalFabricSwitchMachine.class) :
                List.<OpticalFabricSwitchMachine>of();
        if (!switches.isEmpty()) {
            var fabric = switches.get(0);
            links.put(fabric, new Link(fabric, fabric));
        } else {
            for (var rack : DatacenterFloorNetwork.members(machine, AirCooledComputeRackMachine.class)) {
                links.put(rack, new Link(rack, rack));
            }
            for (var rack : DatacenterFloorNetwork.members(machine, LiquidCooledComputeRackMachine.class)) {
                links.put(rack, new Link(rack, rack));
            }
        }
        for (Link link : opticalInputs(machine)) {
            links.putIfAbsent(link.source(), link);
        }
        return List.copyOf(links.values());
    }

    private static List<Link> opticalInputs(MultiblockControllerMachine machine) {
        if (!(machine.getLevel() instanceof ServerLevel level) || !machine.isFormed()) {
            return List.of();
        }

        Map<MetaMachine, Link> links = new LinkedHashMap<>();
        for (var part : machine.getParts()) {
            if (!(part instanceof OpticalComputationHatchMachine receiver) || receiver.isTransmitter()) {
                continue;
            }
            for (Direction side : Direction.values()) {
                var pipePos = receiver.getPos().relative(side);
                var towardReceiver = side.getOpposite();
                if (!level.hasChunkAt(pipePos) ||
                        !(level.getBlockEntity(pipePos) instanceof OpticalPipeBlockEntity pipe) ||
                        !pipe.isConnected(towardReceiver)) {
                    continue;
                }
                var net = pipe.getOpticalPipeNet();
                var route = net == null ? null : net.getNetData(pipePos, towardReceiver);
                if (route == null || !route.getTargetPipe().isConnected(route.getTargetFacing())) {
                    continue;
                }
                var targetPos = route.getTargetPipePos().relative(route.getTargetFacing());
                if (!level.hasChunkAt(targetPos) ||
                        !(level.getBlockEntity(targetPos) instanceof IMachineBlockEntity holder) ||
                        !(holder.getMetaMachine() instanceof OpticalComputationHatchMachine transmitter) ||
                        !transmitter.isTransmitter()) {
                    continue;
                }
                var endpoint = route.getComputationHatch();
                if (!(endpoint instanceof IOpticalComputationHatch nativeHatch) || !nativeHatch.isTransmitter()) {
                    continue;
                }
                for (var controller : transmitter.getControllers()) {
                    if (controller.isFormed() && controller.self() != machine) {
                        var source = controller.self();
                        links.putIfAbsent(source, new Link(source, endpoint));
                    }
                }
            }
        }
        return List.copyOf(links.values());
    }

    public static int capacity(List<Link> links, Collection<IOpticalComputationProvider> seen) {
        int total = 0;
        for (Link link : links) {
            if (!link.visited(seen)) {
                total += link.provider().getMaxCWUt(seen);
            }
        }
        return total;
    }

    public static int request(List<Link> links, int demand, boolean simulate,
                              Collection<IOpticalComputationProvider> seen) {
        int remaining = demand;
        for (Link link : links) {
            if (remaining <= 0) {
                break;
            }
            if (!link.visited(seen)) {
                remaining -= link.provider().requestCWUt(remaining, simulate, seen);
            }
        }
        return demand - remaining;
    }

    public static int available(MultiblockControllerMachine machine) {
        return capacity(providers(machine, true), new ArrayList<>());
    }

    public static int request(MultiblockControllerMachine machine, int demand, boolean simulate) {
        return request(providers(machine, true), demand, simulate, new ArrayList<>());
    }

    public static List<VramStorageRackMachine> memoryRacks(MultiblockControllerMachine machine) {
        Set<VramStorageRackMachine> memory = new LinkedHashSet<>();
        collectMemory(machine, memory, new LinkedHashSet<>());
        // A consumer can receive a source room through its local switch's optical uplinks.
        if (!(machine instanceof OpticalFabricSwitchMachine)) {
            var switches = DatacenterFloorNetwork.members(machine, OpticalFabricSwitchMachine.class);
            if (!switches.isEmpty()) {
                collectMemory(switches.get(0), memory, new LinkedHashSet<>(List.of(machine)));
            }
        }
        return List.copyOf(memory);
    }

    private static void collectMemory(MultiblockControllerMachine machine, Set<VramStorageRackMachine> memory,
                                      Set<MultiblockControllerMachine> seen) {
        if (!machine.isFormed() || !seen.add(machine)) {
            return;
        }
        memory.addAll(DatacenterFloorNetwork.members(machine, VramStorageRackMachine.class));
        for (Link link : opticalInputs(machine)) {
            if (link.source() instanceof OpticalFabricSwitchMachine fabric && fabric.canForward()) {
                collectMemory(fabric, memory, seen);
            }
        }
    }
}
