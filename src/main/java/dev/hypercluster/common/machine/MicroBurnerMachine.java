package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.world.item.ItemStack;

import dev.hypercluster.common.item.HyperClusterItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import javax.annotation.Nullable;

/**
 * 0.6.2 · the HV micro burner ({@code hypercluster:hv_micro_burner}).
 *
 * <p>
 * The 0.1/0.2 chip route (telemetry log or high-purity dataset plus a blank carrier, burned into
 * a domain edge chip) is plain recipe data and needs nothing from this class. What it adds is the
 * <b>open-weight cloning route</b> of chapter six's manual:
 *
 * <ul>
 * <li>one {@code open_weight_module} in the input slot — declared {@code notConsumable}, so
 * <b>the original stays in the machine</b>;</li>
 * <li>one {@code blank_carrier}, which is consumed;</li>
 * <li>a modest amount of HV power;</li>
 * <li>one {@code open_weight_module} out, carrying a <b>byte-for-byte copy of the source
 * record</b>: {@code hc_model_id}, {@code hc_domain}, {@code hc_precision}, {@code hc_version}
 * and the {@code hc_runs} lineage.</li>
 * </ul>
 *
 * <p>
 * <b>Why the NBT is stamped here and not in the recipe.</b> A recipe's output content is static
 * data shared by every machine in the world; it cannot know which module sits in this burner's
 * slot. {@link #fullModifyRecipe} is the one hook GTCEu calls per candidate recipe with the machine
 * in hand, so the copy is written there: the recipe that actually runs carries a real
 * {@link ItemStack} whose tag is the source module's, and the normal {@code handleRecipeIO(OUT)}
 * path then lands it like any other output. No side channel, no post-hoc inventory poke.
 *
 * <p>
 * A second deployment of the same model therefore costs one carrier and some EU instead of a full
 * 2400-tick training run, which is exactly what the manual promises (实装与手册一致).
 */
public class MicroBurnerMachine extends SimpleTieredMachine {

    protected static final com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder MANAGED_FIELD_HOLDER = new com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder(
            MicroBurnerMachine.class, SimpleTieredMachine.MANAGED_FIELD_HOLDER);

    /** {@link GTRecipe#data} flag marking the open-weight cloning route (1 = clone). */
    public static final String OPEN_WEIGHT_CLONE_FLAG = "hc_open_weight_clone";

    public MicroBurnerMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction,
                              Object... args) {
        super(holder, tier, tankScalingFunction, args);
    }

    @Override
    public com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * The open-weight module currently sitting in this burner's inputs, or null. The clone recipe
     * declares it non-consumed, so it is still there while the batch runs — which is what makes
     * cloning lossless.
     */
    @Nullable
    public ItemStack sourceModule() {
        if (importItems == null) {
            return null;
        }
        for (int slot = 0; slot < importItems.getSlots(); slot++) {
            ItemStack stack = importItems.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(HyperClusterItems.OPEN_WEIGHT_MODULE.get())) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 0.6.2 · the cloning settlement: the source module's whole training record is written onto the
     * produced module after the batch settles (see {@link MicroBurnerRecipeLogic}). The recipe's own
     * output content stays a plain, GT-matchable module so recipe matching never touches NBT.
     */
    @Override
    protected com.gregtechceu.gtceu.api.machine.trait.RecipeLogic createRecipeLogic(Object... args) {
        return new MicroBurnerRecipeLogic(this);
    }
}
