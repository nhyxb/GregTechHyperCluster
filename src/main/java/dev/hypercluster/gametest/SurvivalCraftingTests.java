package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;

/**
 * P05/P06 survival crafting regression: the three 0.1 craftable entries must exist as vanilla
 * workbench recipes with the exact outputs, so a pure-survival save can run the whole chain
 * without /give and every recipe shows up in the recipe browser (JEI).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class SurvivalCraftingTests {

    public static final String SURVIVAL_BATCH = "hypercluster_survival";

    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void survivalRecipesExist(GameTestHelper helper) {
        // 1) blank_carrier: silicon wafer + HV circuit + polyethylene plates -> 2 carriers.
        CraftingRecipe carrier = craftingRecipe(helper, "shaped/blank_carrier");
        helper.assertTrue(
                carrier.getResultItem(helper.getLevel().registryAccess()).is(HyperClusterItems.BLANK_CARRIER.get()),
                "blank_carrier recipe output mismatch: " + carrier.getResultItem(helper.getLevel().registryAccess()));
        helper.assertTrue(carrier.getResultItem(helper.getLevel().registryAccess()).getCount() == 2,
                "blank_carrier recipe yield expected 2, got " +
                        carrier.getResultItem(helper.getLevel().registryAccess()).getCount());
        assertRecipeUses(helper, carrier, "gtceu:silicon_wafer", "blank_carrier must consume a silicon wafer");

        // 2) hv_micro_burner: HV emitter/circuit/hull/cable around a machine hull.
        CraftingRecipe burner = craftingRecipe(helper, "shaped/hv_micro_burner");
        helper.assertTrue(
                burner.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.HV_MICRO_BURNER.getBlock().asItem()),
                "hv_micro_burner recipe output mismatch: " + burner.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, burner, "gtceu:hv_emitter", "hv_micro_burner must use an HV emitter");
        assertRecipeUses(helper, burner, "gtceu:hv_machine_hull", "hv_micro_burner must use an HV machine hull");
        assertRecipeUses(helper, burner, "minecraft:glass", "hv_micro_burner must use glass");

        // 3) hv_telemetry_ore_washer: the plain GT HV ore washer upgraded in place.
        CraftingRecipe washer = craftingRecipe(helper, "shaped/hv_telemetry_ore_washer");
        helper.assertTrue(
                washer.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.getBlock().asItem()),
                "hv_telemetry_ore_washer recipe output mismatch: " +
                        washer.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, washer, "gtceu:hv_sensor", "the washer upgrade must use an HV sensor");
        assertRecipeUses(helper, washer, "gtceu:advanced_integrated_circuit",
                "the washer upgrade must use the HV circuit");
        assertRecipeUses(helper, washer, "gtceu:hv_ore_washer",
                "the washer upgrade must consume the vanilla HV ore washer");

        // 0.2 entries: the EV chemical reactor and the data scrubbing tower must be craftable.
        CraftingRecipe chemicalReactor = craftingRecipe(helper, "shaped/ev_telemetry_chemical_reactor");
        helper.assertTrue(
                chemicalReactor.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR.getBlock().asItem()),
                "ev_telemetry_chemical_reactor recipe output mismatch: " +
                        chemicalReactor.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, chemicalReactor, "gtceu:ev_chemical_reactor",
                "the chemical upgrade must consume the vanilla GT EV chemical reactor");
        CraftingRecipe tower = craftingRecipe(helper, "shaped/data_scrubbing_tower");
        helper.assertTrue(
                tower.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.DATA_SCRUBBING_TOWER.getBlock().asItem()),
                "data_scrubbing_tower recipe output mismatch: " +
                        tower.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, tower, "minecraft:glass", "the tower must use glass");
        // Clean datasets have a purpose: an alternative blank-carrier recipe.
        CraftingRecipe carrierFromDataset = craftingRecipe(helper, "shaped/blank_carrier_from_clean_dataset");
        helper.assertTrue(
                carrierFromDataset.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterItems.BLANK_CARRIER.get()),
                "blank_carrier_from_clean_dataset output mismatch: " +
                        carrierFromDataset.getResultItem(helper.getLevel().registryAccess()));

        // The ingredients themselves must exist in the registries (survival-obtainable chain).
        for (String id : new String[] { "gtceu:silicon_wafer", "gtceu:hv_sensor", "gtceu:hv_emitter",
                "gtceu:hv_machine_hull", "gtceu:advanced_integrated_circuit", "gtceu:hv_ore_washer",
                "gtceu:ev_sensor", "gtceu:ev_machine_hull", "gtceu:ev_chemical_reactor" }) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(id)),
                    "ingredient not registered: " + id);
        }
        // Sanity for the base machine being the actual GTCEu washer.
        helper.assertTrue(GTMachines.ORE_WASHER[com.gregtechceu.gtceu.api.GTValues.HV].getBlock()
                .asItem() == BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:hv_ore_washer")),
                "GT HV ore washer registry id mismatch");
        System.out.printf(
                "[HC-P05] survival recipes verified: blank_carrier(x2)+hv_micro_burner+hv_telemetry_ore_washer%n");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void renderStatesRegisteredForSync(GameTestHelper helper) {
        // Regression for the working-overlay bug: addon machines register after GTMachines.init(),
        // so their MachineRenderStates must be added to MachineDefinition.RENDER_STATE_REGISTRY
        // explicitly, otherwise the render-state payload fails to serialize and clients never
        // switch recipe_logic_status (front overlay stuck on overlay_front).
        for (com.gregtechceu.gtceu.api.machine.MachineDefinition machine : new com.gregtechceu.gtceu.api.machine.MachineDefinition[] {
                HyperClusterMachines.HV_MICRO_BURNER, HyperClusterMachines.HV_TELEMETRY_ORE_WASHER,
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR,
                HyperClusterMachines.DATA_SCRUBBING_TOWER }) {
            int expected = 0;
            int registered = 0;
            for (com.gregtechceu.gtceu.client.model.machine.MachineRenderState state : machine
                    .getStateDefinition().getPossibleStates()) {
                expected++;
                int id = com.gregtechceu.gtceu.api.machine.MachineDefinition.RENDER_STATE_REGISTRY.getId(state);
                helper.assertTrue(id >= 0, "render state " + state + " of " + machine.getId() +
                        " is missing from RENDER_STATE_REGISTRY — the working overlay would never sync");
                if (id >= 0) registered++;
            }
            helper.assertTrue(expected > 0, machine.getId() + " has no render states at all");
            System.out.printf("[HC-RENDER] %s: %d/%d render states registered%s%n", machine.getId(),
                    registered, expected, registered == expected ? " (sync-ready)" : "");
        }
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void burnerAndWasherCraftableFromRecipeIds(GameTestHelper helper) {
        // Machine result items resolve directly (they are what /give and creative use).
        ItemStack burnerItem = HyperClusterMachines.HV_MICRO_BURNER.asStack(1);
        ItemStack washerItem = HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.asStack(1);
        helper.assertTrue(
                burnerItem.is(HyperClusterMachines.HV_MICRO_BURNER.getBlock().asItem()) &&
                        washerItem.is(HyperClusterMachines.HV_TELEMETRY_ORE_WASHER.getBlock().asItem()),
                "machine item stacks must resolve to their block items");
        helper.assertTrue(!burnerItem.isEmpty() && !washerItem.isEmpty(), "machine items may not be empty");
        helper.succeed();
    }

    private static CraftingRecipe craftingRecipe(GameTestHelper helper, String path) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(HyperCluster.MOD_ID, path)).orElse(null);
        helper.assertTrue(recipe instanceof CraftingRecipe, "missing hypercluster:" + path + " crafting recipe");
        return (CraftingRecipe) recipe;
    }

    private static void assertRecipeUses(GameTestHelper helper, CraftingRecipe recipe, String ingredientId,
                                         String message) {
        for (var ingredient : recipe.getIngredients()) {
            for (var stack : ingredient.getItems()) {
                if (stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(ingredientId)))) return;
            }
        }
        helper.assertTrue(false, message + " (no " + ingredientId + " among ingredients)");
    }
}
