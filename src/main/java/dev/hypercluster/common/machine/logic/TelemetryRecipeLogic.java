package dev.hypercluster.common.machine.logic;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.machine.TelemetryMachine;

/**
 * P04/0.2 settlement logic for the own telemetry demonstration machines on GTCEu 7.4.0.
 *
 * <p>
 * Verbatim copy of {@code RecipeLogic#onRecipeFinish()} from GTCEu 7.4.0 with ONE addition:
 * the previously-ignored result of {@code handleRecipeIO(lastRecipe, IO.OUT)} is captured, and a
 * telemetry log is deposited only when the batch's outputs actually settled into the machine AND
 * the recipe is a whitelisted regular hypercluster recipe. This excludes, by construction:
 * output jams (IO.OUT fails, products dropped), interrupts (onRecipeFinish never runs), power loss
 * mid-run, idle time and recipes without regular outputs.
 *
 * <p>
 * Counting is per successfully settled batch; see TelemetryMachineTests/DataWorkshopTests
 * acceptance coverage.
 */
public class TelemetryRecipeLogic extends RecipeLogic {

    public TelemetryRecipeLogic(TelemetryMachine machine) {
        super(machine);
    }

    @Override
    public void onRecipeFinish() {
        // === GTCEu 7.4.0 RecipeLogic#onRecipeFinish, augmented (see class doc) ===
        machine.afterWorking();
        if (lastRecipe != null) {
            runAttempt = 0;
            runDelay = 0;
            consecutiveRecipes++;

            // --- HyperCluster addition: precise telemetry settlement ---
            boolean outputsSettled = handleRecipeIO(lastRecipe, IO.OUT).isSuccess();
            boolean whitelisted = isSupportedTelemetryRecipe(lastRecipe);
            if (outputsSettled && whitelisted) {
                ((TelemetryMachine) machine.self()).depositTelemetryLogs(1);
            }
            // --- end addition ---

            // Don't ready the next recipe after finish if suspend is set
            // so that the modifiers won't be applied until re-starting.
            if (suspendAfterFinish) {
                setStatus(Status.SUSPEND);
                consecutiveRecipes = 0;
                progress = 0;
                duration = 0;
                isActive = false;
                // Force a recipe recheck.
                lastRecipe = null;
                return;
            }
            if (machine.alwaysTryModifyRecipe()) {
                if (lastOriginRecipe != null) {
                    var modified = machine.fullModifyRecipe(lastOriginRecipe.copy());
                    if (modified == null) {
                        markLastRecipeDirty();
                    } else {
                        lastRecipe = modified;
                    }
                } else {
                    markLastRecipeDirty();
                }
            }
            // try it again
            var recipeCheck = checkRecipe(lastRecipe);
            if (!recipeDirty && recipeCheck.isSuccess()) {
                setupRecipe(lastRecipe);
            } else {
                setStatus(Status.IDLE);
                // 7.4.0 also assigns the private `waitingReason` here for GUI display only;
                // it is not reachable from a subclass and has no logical effect on settlement.
                if (recipeCheck.io() != IO.IN || recipeCheck.capability() == EURecipeCapability.CAP) {
                    // waiting reason intentionally omitted (private field upstream).
                }
                consecutiveRecipes = 0;
                progress = 0;
                duration = 0;
                isActive = false;
            }
        }
    }

    /**
     * Whitelist for telemetry accounting: the recipe must be defined by this addon and must
     * declare at least one regular (non-chanced) item output. Output settlement is additionally
     * required at every call site above.
     */
    public static boolean isSupportedTelemetryRecipe(GTRecipe recipe) {
        if (recipe == null || recipe.id == null) return false;
        if (!HyperCluster.MOD_ID.equals(recipe.id.getNamespace())) return false;
        return recipe.getOutputContents(ItemRecipeCapability.CAP).stream()
                .anyMatch(content -> content.chance >= content.maxChance && content.chance > 0);
    }
}
