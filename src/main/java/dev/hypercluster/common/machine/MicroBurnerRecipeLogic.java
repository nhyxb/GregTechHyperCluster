package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import net.minecraft.world.item.ItemStack;

import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.OpenWeightModuleItem;

import java.util.List;

/**
 * 0.6.2 · the micro burner's settlement logic: the open-weight cloning route writes the source
 * module's training record onto the module it just produced.
 *
 * <p>
 * <b>Why the stamp happens here and not in the recipe.</b> A GT recipe's output content is matched
 * and copied by GT's recipe machinery, which expects a plain {@code Ingredient}/{@code ItemStack}
 * shape — putting a pre-tagged stack into the output map makes the recipe matcher cast a
 * {@code ItemStack} to {@code Ingredient} and crash the server. The record is therefore written
 * <b>after</b> the batch settled: {@link #onRecipeFinish()} runs the normal GT settlement first
 * (so the vanilla GT output path is untouched) and only then copies the tags from the module that
 * is still sitting in the input slot.
 *
 * <p>
 * Because the clone recipe declares the module {@code notConsumable}, the source is guaranteed to
 * still be there at settlement — which is also what makes the clone lossless.
 */
public class MicroBurnerRecipeLogic extends RecipeLogic {

    public MicroBurnerRecipeLogic(MicroBurnerMachine machine) {
        super(machine);
    }

    @Override
    public void onRecipeFinish() {
        // Snapshot the source BEFORE the super call: super clears lastRecipe on a failed re-check.
        GTRecipe finished = this.lastRecipe;
        boolean cloneRoute = finished != null &&
                finished.data.contains(MicroBurnerMachine.OPEN_WEIGHT_CLONE_FLAG);
        ItemStack source = cloneRoute ? burner().sourceModule() : null;

        super.onRecipeFinish();

        if (cloneRoute && source != null) {
            stampClonedRecord(source);
        }
    }

    /**
     * Copies the source module's whole training record onto the module(s) this batch produced in
     * the export inventory. Only blank modules are stamped, so a second identical clone never
     * overwrites an already-correct record.
     */
    protected void stampClonedRecord(ItemStack source) {
        MicroBurnerMachine machine = burner();
        for (int slot = 0; slot < machine.exportItems.getSlots(); slot++) {
            ItemStack produced = machine.exportItems.getStackInSlot(slot);
            if (produced.isEmpty() || !produced.is(HyperClusterItems.OPEN_WEIGHT_MODULE.get())) {
                continue;
            }
            if (OpenWeightModuleItem.hasExplicitModelId(produced) &&
                    OpenWeightModuleItem.version(produced) > 0) {
                continue; // already carries a real record
            }
            ItemStack stamped = OpenWeightModuleItem.copyWithRecord(source, produced);
            stamped.setCount(produced.getCount());
            machine.exportItems.setStackInSlot(slot, stamped);
        }
    }

    private MicroBurnerMachine burner() {
        return (MicroBurnerMachine) this.machine.self();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Test-only contract helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** True when the given recipe is the open-weight cloning route. */
    public static boolean isCloneRoute(GTRecipe recipe) {
        return recipe != null && recipe.data.contains(MicroBurnerMachine.OPEN_WEIGHT_CLONE_FLAG);
    }

    /** The output contents of a recipe, or an empty list (diagnostics/tests). */
    public static List<Content> outputsOf(GTRecipe recipe) {
        List<Content> outputs = recipe == null ? null : recipe.outputs.get(ItemRecipeCapability.CAP);
        return outputs == null ? List.of() : outputs;
    }

    /** Merely documents that the settlement path is IO.OUT-ordered like vanilla GT. */
    static IO settlementIo() {
        return IO.OUT;
    }
}
