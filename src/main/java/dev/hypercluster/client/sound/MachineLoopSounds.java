package dev.hypercluster.client.sound;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.hypercluster.common.sound.MachineLoopSound;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 0.6.3 · client-side ownership of the work loops for machines that are not recipe-driven.
 *
 * <p>
 * The air rack, the liquid rack and the satellite station all run {@code DUMMY_RECIPES}: their
 * "working" flag is a tick contract, so {@code RecipeLogic#updateSound()} never fires for them
 * and GTCEu would stay silent. {@link MachineLoopSound} supplies the identical
 * {@code AutoReleasedSound} mechanism; this registry simply keeps one loop instance per machine
 * and decides, once per client tick, whether it should be running.
 *
 * <p>
 * The instance map is weak-keyed and keyed on the {@link MetaMachine} itself, so a block entity
 * that is unloaded or replaced cannot leak a loop: the entry disappears with the machine object
 * and the sound manager's predicate releases the audio as soon as the machine stops reporting
 * work.
 *
 * <p>
 * Keying on the position instead would be wrong — two different machines can briefly share a
 * position across a chunk reload, and the stale loop would survive into the new machine.
 */
@OnlyIn(Dist.CLIENT)
public final class MachineLoopSounds {

    private static final Map<MetaMachine, MachineLoopSound> LOOPS = new WeakHashMap<>();

    private MachineLoopSounds() {}

    /**
     * Advances (or starts/stops) the loop for one machine. Called from the machine's
     * {@code animateTick}, which the client invokes only for machines inside the render/nearby
     * range.
     *
     * @param working the machine's own synced "I am working right now" signal
     */
    public static void tick(MetaMachine machine, boolean working,
                            java.util.function.Supplier<com.gregtechceu.gtceu.api.sound.SoundEntry> entry) {
        if (machine.getLevel() == null) {
            return;
        }
        MachineLoopSound loop = LOOPS.get(machine);
        var soundEntry = entry.get();
        if (soundEntry == null) {
            return;
        }
        if (loop == null) {
            BlockPos pos = machine.getPos();
            loop = new MachineLoopSound(soundEntry, pos);
            LOOPS.put(machine, loop);
        }
        Level level = machine.getLevel();
        BlockPos pos = machine.getPos();
        // Mirrors GTCEu's own RecipeLogic guard set so a loop can never outlive its machine.
        // shouldWorkingPlaySound() is an IRecipeLogicMachine default: it honours the GT
        // "machine sounds" config and the muffler upgrade.
        boolean soundsEnabled = !(machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine rlm) ||
                rlm.shouldWorkingPlaySound();
        boolean alive = soundsEnabled && !machine.isInValid() && level.isLoaded(pos) &&
                MetaMachine.getMachine(level, pos) == machine;
        loop.update(working && soundsEnabled, () -> alive);
    }

    /** Stops the loop for a machine that is being removed or has gone idle for good. */
    public static void stop(MetaMachine machine) {
        MachineLoopSound loop = LOOPS.remove(machine);
        if (loop != null) {
            loop.release();
        }
    }

    /** Convenience: is this machine's render state reporting WORKING? */
    public static boolean renderStateWorking(MetaMachine machine) {
        var state = machine.getRenderState();
        return state.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                state.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) == RecipeLogic.Status.WORKING;
    }
}
