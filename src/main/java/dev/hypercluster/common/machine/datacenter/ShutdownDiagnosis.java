package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

public enum ShutdownDiagnosis {

    NONE,
    INSUFFICIENT_ENERGY,
    MISSING_INPUT_OR_NO_RECIPE,
    OUTPUT_FULL,
    MAINTENANCE_ISSUE,
    SUSPENDED;

    public Component message() {
        return Component.translatable("hypercluster.diagnosis." + name().toLowerCase(Locale.ROOT));
    }

    public static ShutdownDiagnosis inspect(IWorkableMultiController host) {
        var logic = host.getRecipeLogic();
        if (logic.isWorking()) {
            return NONE;
        }
        GTRecipe active = logic.getDuration() > 0 ? logic.getLastRecipe() : null;
        if (logic.isSuspend()) {
            // GT automatically suspends after repeated energy failures as well as on manual pause.
            return active != null && lacksEnergy(host, active) ? INSUFFICIENT_ENERGY : SUSPENDED;
        }
        for (var part : host.getParts()) {
            if (part instanceof IMaintenanceMachine maintenance && maintenance.hasMaintenanceProblems()) {
                return MAINTENANCE_ISSUE;
            }
        }
        if (active != null) {
            if (lacksEnergy(host, active)) {
                return INSUFFICIENT_ENERGY;
            }
            if (!fits(host, active, IO.OUT, false) || !fits(host, active, IO.OUT, true)) {
                return OUTPUT_FULL;
            }
        }

        boolean outputBlocked = false;
        // The native search already excludes full outputs. Reuse its input index with only
        // the input match and recipe conditions so a physically blocked recipe still surfaces.
        var candidates = host.getRecipeType().searchRecipe(host,
                recipe -> fits(host, recipe, IO.IN, false) && RecipeHelper.checkConditions(recipe, logic).isSuccess());
        while (candidates.hasNext()) {
            GTRecipe candidate = candidates.next();
            if (candidate == null) {
                continue;
            }
            // Full output can stop the parallel calculation itself. Test one raw batch in that case.
            GTRecipe modified = host.fullModifyRecipe(candidate.copy());
            if (modified == null) {
                outputBlocked |= !fits(host, candidate, IO.OUT, false) || !fits(host, candidate, IO.OUT, true);
            } else if (lacksEnergy(host, modified)) {
                return INSUFFICIENT_ENERGY;
            } else {
                outputBlocked |= !fits(host, modified, IO.OUT, false) || !fits(host, modified, IO.OUT, true);
            }
        }
        if (outputBlocked) {
            return OUTPUT_FULL;
        }
        if (host instanceof WorkableElectricMultiblockMachine electric &&
                electric.getEnergyContainer().getEnergyStored() == 0) {
            return INSUFFICIENT_ENERGY;
        }
        return MISSING_INPUT_OR_NO_RECIPE;
    }

    private static boolean lacksEnergy(IWorkableMultiController host, GTRecipe recipe) {
        var energy = recipe.getTickInputContents(EURecipeCapability.CAP);
        return !energy.isEmpty() && !RecipeHelper.handleRecipe(host, recipe, IO.IN,
                Map.of(EURecipeCapability.CAP, energy), Map.of(), true, true).isSuccess();
    }

    private static boolean fits(IWorkableMultiController host, GTRecipe recipe, IO direction, boolean tick) {
        var contents = tick ? recipe.tickOutputs : direction == IO.IN ? recipe.inputs : recipe.outputs;
        return RecipeHelper.handleRecipe(host, recipe, direction, contents, Map.of(), tick, true).isSuccess();
    }
}
