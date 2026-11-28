package dev.hypercluster.common.sound;

import com.gregtechceu.gtceu.api.sound.AutoReleasedSound;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;

/**
 * 0.6.3 · one machine's work loop, driven through GTCEu's own {@link AutoReleasedSound}.
 *
 * <p>
 * GTCEu's {@code RecipeLogic#updateSound()} plays work loops for recipe-driven machines by
 * handing an {@link AutoReleasedSound} to the sound manager. The datacenter racks and the
 * satellite station run on {@code DUMMY_RECIPES} (their work is a tick contract, not a recipe),
 * so {@code RecipeLogic} never reports them as working and would never start a loop. This
 * helper drives the identical mechanism for them: same sound class, same predicate contract,
 * same auto-release on stop.
 *
 * <p>
 * <b>Stopping and unloading are the framework's job.</b> {@link AutoReleasedSound} polls its
 * predicate every client tick and releases itself the moment the predicate turns false, so power
 * loss, a broken structure, a chunk unload or the player leaving range all end the loop with no
 * bookkeeping here. The predicate also mirrors GTCEu's own guards ({@code !isInValid()}, level
 * loaded, the same machine still at that position) so a stale instance can never outlive its
 * block entity.
 *
 * <p>
 * <b>Muting is safe.</b> Nothing here touches the audio device directly; the instance obeys the
 * vanilla category volume, including a zeroed "Blocks" slider, exactly like GTCEu's own machine
 * loops.
 */
@OnlyIn(Dist.CLIENT)
public final class MachineLoopSound {

    private final SoundEntry entry;
    private final BlockPos pos;
    private AutoReleasedSound current;

    public MachineLoopSound(SoundEntry entry, BlockPos pos) {
        this.entry = entry;
        this.pos = pos.immutable();
    }

    /**
     * Advances the loop: starts it when {@code shouldLoop} is true, stops it otherwise. Safe to
     * call every client tick.
     *
     * @param shouldLoop machine is working right now
     * @param alive      re-checked by the sound manager; false releases the instance
     */
    public void update(boolean shouldLoop, BooleanSupplier alive) {
        if (!shouldLoop) {
            release();
            return;
        }
        // Already looping this entry and still audible: nothing to do.
        if (current != null && current.soundEntry == entry && !current.isStopped()) {
            return;
        }
        release();
        this.current = entry.playAutoReleasedSound(alive, pos, true, 0, 1.0F, 1.0F);
    }

    /** Explicitly stops the loop (machine removed, structure broken). */
    public void release() {
        if (current != null) {
            current.release();
            current = null;
        }
    }

    public boolean isPlaying() {
        return current != null && !current.isStopped();
    }
}
