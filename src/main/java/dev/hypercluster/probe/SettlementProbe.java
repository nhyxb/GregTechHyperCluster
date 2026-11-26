package dev.hypercluster.probe;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P02 settlement probe for GTCEu 7.4.0.
 *
 * <p>
 * Wraps {@code MachineDefinition#afterWorking} of a target machine definition with a counting
 * recorder so {@link RecipeLogic} settlement behavior becomes observable from a reproducible
 * Forge GameTest, without mixins and without touching the running machines otherwise.
 *
 * <p>
 * Recording key facts per invocation (P02 deliverable): machine position, machine offset tick,
 * world game time, recipe id, progress vs duration (to separate "finish" from "interrupt" calls),
 * and the connection count.
 *
 * <p>
 * <b>Mount-scope guarantee (P05):</b> the only call site of {@link #install} is the
 * {@code hypercluster_probe} GameTest batch ({@code SettlementProbeTests#prepareProbe}), which runs
 * exclusively under the {@code runGametest} task. Neither the {@code @Mod} constructor nor
 * {@code IGTAddon#initializeAddon} touches this class, so ordinary single-player or dedicated
 * server startups never wrap or otherwise alter any GregTech machine definition
 * (including {@code gtceu:hv_ore_washer}).
 */
public final class SettlementProbe {

    /** A single recorded `afterWorking()` invocation. */
    public record ProbeEvent(BlockPos pos, long machineTick, long gameTime, String recipe, int progress,
                             int duration, int consecutiveRecipes, String status) {

        /** An `afterWorking` fired from `onRecipeFinish` sees progress == duration; an interrupt sees less. */
        public boolean looksLikeFinish() {
            return duration > 0 && progress >= duration;
        }
    }

    private static final Map<BlockPos, List<ProbeEvent>> EVENTS = new ConcurrentHashMap<>();
    private static final Set<InstallKey> INSTALLED = Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    private SettlementProbe() {}

    /** Tracks already-wrapped definitions so repeated installs stay idempotent. */
    private record InstallKey(Object definitionId) {}

    /**
     * Wraps {@code definition.setAfterWorking} with the recorder. Idempotent per definition instance.
     */
    public static void install(com.gregtechceu.gtceu.api.machine.MachineDefinition definition) {
        if (!INSTALLED.add(new InstallKey(definition.getId()))) return;
        var original = definition.getAfterWorking();
        definition.setAfterWorking(machine -> {
            record(machine);
            original.accept(machine);
        });
    }

    private static void record(IRecipeLogicMachine machine) {
        RecipeLogic logic = machine.getRecipeLogic();
        String recipe = logic.getLastRecipe() == null ? "null" : String.valueOf(logic.getLastRecipe().id);
        ProbeEvent event = new ProbeEvent(
                machine.self().getPos().immutable(),
                machine.self().getOffsetTimer(),
                machine.self().getLevel().getGameTime(),
                recipe,
                logic.getProgress(),
                logic.getDuration(),
                logic.getConsecutiveRecipes(),
                String.valueOf(logic.getStatus()));
        EVENTS.computeIfAbsent(event.pos(), p -> Collections.synchronizedList(new ArrayList<>())).add(event);
        // Log line consumed by the acceptance report; keep it cheap and single-line.
        System.out.printf(
                "[HC-PROBE] afterWorking pos=%s tick=%d gameTime=%d recipe=%s progress=%d duration=%d consecutive=%d status=%s%n",
                event.pos(), event.machineTick(), event.gameTime(), event.recipe(), event.progress(),
                event.duration(), event.consecutiveRecipes(), event.status());
    }

    /** Clears recorded events; call at batch start so tests see only their own machines. */
    public static void reset() {
        EVENTS.clear();
    }

    public static List<ProbeEvent> events(BlockPos pos) {
        return EVENTS.getOrDefault(pos, Collections.emptyList());
    }

    public static int count(BlockPos pos) {
        return events(pos).size();
    }
}
