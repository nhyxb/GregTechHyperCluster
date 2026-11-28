package dev.hypercluster.client.sound;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.hypercluster.common.sound.MachineLoopSound;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-side work loops for the air rack, liquid rack and satellite station. */
@OnlyIn(Dist.CLIENT)
public final class MachineLoopSounds {

    private static final Map<MetaMachine, MachineLoopSound> LOOPS = new WeakHashMap<>();

    private MachineLoopSounds() {}

    /** Starts nearby work loops from the machine's client animate tick. */
    public static void tick(MetaMachine machine, SoundEntry entry) {
        if (entry == null || !canPlay(machine)) {
            stop(machine);
            return;
        }
        MachineLoopSound loop = LOOPS.computeIfAbsent(machine, key -> new MachineLoopSound(entry, key.getPos()));
        // The sound manager keeps polling after animateTick stops firing.
        var machineRef = new WeakReference<>(machine);
        loop.update(() -> {
            MetaMachine current = machineRef.get();
            return current != null && canPlay(current);
        });
    }

    private static boolean canPlay(MetaMachine machine) {
        Level level = machine.getLevel();
        if (level == null || level != Minecraft.getInstance().level || machine.isInValid()) {
            return false;
        }
        BlockPos pos = machine.getPos();
        return renderStateWorking(machine) &&
                (!(machine instanceof IRecipeLogicMachine recipeMachine) || recipeMachine.shouldWorkingPlaySound()) &&
                level.hasChunkAt(pos) && MetaMachine.getMachine(level, pos) == machine;
    }

    /** Stops the loop for a machine that is being removed or has gone idle for good. */
    public static void stop(MetaMachine machine) {
        MachineLoopSound loop = LOOPS.remove(machine);
        if (loop != null) {
            loop.release();
        }
    }

    /** Convenience: is this machine's render state reporting WORKING? */
    private static boolean renderStateWorking(MetaMachine machine) {
        var state = machine.getRenderState();
        return state.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                state.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) == RecipeLogic.Status.WORKING;
    }
}
