package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        // 0.2.1 entries: the LoRA chamber and the red-team sandbox are craftable.
        CraftingRecipe chamber = craftingRecipe(helper, "shaped/lora_engraving_chamber");
        helper.assertTrue(
                chamber.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.LORA_ENGRAVING_CHAMBER.getBlock().asItem()),
                "lora_engraving_chamber recipe output mismatch: " +
                        chamber.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, chamber, "gtceu:ev_sensor", "the LoRA chamber must use an EV sensor");
        CraftingRecipe sandbox = craftingRecipe(helper, "shaped/red_team_sandbox");
        helper.assertTrue(
                sandbox.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterMachines.RED_TEAM_SANDBOX.getBlock().asItem()),
                "red_team_sandbox recipe output mismatch: " +
                        sandbox.getResultItem(helper.getLevel().registryAccess()));
        assertRecipeUses(helper, sandbox, "gtceu:ev_emitter", "the red-team sandbox must use an EV emitter");
        // Clean datasets have a purpose: an alternative blank-carrier recipe.
        CraftingRecipe carrierFromDataset = craftingRecipe(helper, "shaped/blank_carrier_from_clean_dataset");
        helper.assertTrue(
                carrierFromDataset.getResultItem(helper.getLevel().registryAccess())
                        .is(HyperClusterItems.BLANK_CARRIER.get()),
                "blank_carrier_from_clean_dataset output mismatch: " +
                        carrierFromDataset.getResultItem(helper.getLevel().registryAccess()));
        helper.assertTrue(carrierFromDataset.getResultItem(helper.getLevel().registryAccess()).getCount() == 4,
                "clean-dataset carriers must yield 4 per craft, got " +
                        carrierFromDataset.getResultItem(helper.getLevel().registryAccess()).getCount());

        // The ingredients themselves must exist in the registries (survival-obtainable chain).
        for (String id : new String[] { "gtceu:silicon_wafer", "gtceu:hv_sensor", "gtceu:hv_emitter",
                "gtceu:hv_machine_hull", "gtceu:advanced_integrated_circuit", "gtceu:hv_ore_washer",
                "gtceu:ev_sensor", "gtceu:ev_machine_hull", "gtceu:ev_chemical_reactor",
                "gtceu:ev_emitter" }) {
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

    /**
     * 0.3 · IV datacenter survival recipes: the whole chain is craftable without /give, and the
     * zero-HPCA deadlock check holds — no ingredient may be an HPCA component or a research
     * station / data bank / network switch / computation-hatch product, so an IV player can
     * build the machine room before any HPCA or research loop exists.
     */
    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void datacenterSurvivalRecipesAndZeroHpcaChain(GameTestHelper helper) {
        // 1) structure blocks + items.
        CraftingRecipe floor = craftingRecipe(helper, "shaped/raised_floor");
        helper.assertTrue(floor.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.RAISED_FLOOR.get().asItem()), "raised_floor recipe output mismatch");
        helper.assertTrue(floor.getResultItem(helper.getLevel().registryAccess()).getCount() == 8,
                "raised_floor must yield 8 per craft");
        assertRecipeUses(helper, floor, "gtceu:steel_plate", "raised_floor must use steel plates");

        CraftingRecipe intake = craftingRecipe(helper, "shaped/rack_casing_intake");
        helper.assertTrue(intake.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.RACK_CASING_INTAKE.get().asItem()),
                "rack_casing_intake recipe output mismatch");
        helper.assertTrue(intake.getResultItem(helper.getLevel().registryAccess()).getCount() == 4,
                "rack_casing_intake must yield 4 per craft");
        CraftingRecipe exhaust = craftingRecipe(helper, "shaped/rack_casing_exhaust");
        helper.assertTrue(exhaust.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.RACK_CASING_EXHAUST.get().asItem()),
                "rack_casing_exhaust recipe output mismatch");

        CraftingRecipe blade = craftingRecipe(helper, "shaped/iv_compute_blade");
        helper.assertTrue(blade.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterItems.IV_COMPUTE_BLADE.get()), "iv_compute_blade recipe output mismatch");
        assertRecipeUses(helper, blade, "gtceu:silicon_wafer", "the blade must use silicon wafers");

        CraftingRecipe snack = craftingRecipe(helper, "shaped/green_snack_pack");
        helper.assertTrue(snack.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterItems.GREEN_SNACK_PACK.get()), "green_snack_pack recipe output mismatch");
        helper.assertTrue(snack.getResultItem(helper.getLevel().registryAccess()).getCount() == 2,
                "green_snack_pack must yield 2 per craft");

        // 2) machine controllers and the inference hatch.
        CraftingRecipe rack = craftingRecipe(helper, "shaped/air_cooled_compute_rack");
        helper.assertTrue(rack.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.AIR_COOLED_COMPUTE_RACK.getBlock().asItem()),
                "air_cooled_compute_rack recipe output mismatch");
        assertRecipeUses(helper, rack, "hypercluster:rack_casing_intake",
                "the rack must be built from its own intake casings");
        assertRecipeUses(helper, rack, "hypercluster:rack_casing_exhaust",
                "the rack must be built from its own exhaust casings");

        CraftingRecipe hatch = craftingRecipe(helper, "shaped/iv_inference_hatch");
        helper.assertTrue(hatch.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.IV_INFERENCE_HATCH.getBlock().asItem()),
                "iv_inference_hatch recipe output mismatch");
        assertRecipeUses(helper, hatch, "gtceu:iv_sensor", "the inference hatch must use an IV sensor");

        CraftingRecipe telemetry = craftingRecipe(helper, "shaped/hv_telemetry_hatch");
        helper.assertTrue(telemetry.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.HV_TELEMETRY_HATCH.getBlock().asItem()),
                "hv_telemetry_hatch recipe output mismatch");
        assertRecipeUses(helper, telemetry, "gtceu:hv_output_bus", "telemetry hatch must reuse an HV export bus");

        CraftingRecipe crac = craftingRecipe(helper, "shaped/precision_crac");
        helper.assertTrue(crac.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.PRECISION_CRAC.getBlock().asItem()),
                "precision_crac recipe output mismatch");

        // 3) zero-HPCA / zero-research deadlock check on every 0.3 recipe ingredient.
        String[] recipes = { "shaped/raised_floor", "shaped/rack_casing_intake", "shaped/rack_casing_exhaust",
                "shaped/iv_compute_blade", "shaped/green_snack_pack", "shaped/air_cooled_compute_rack",
                "shaped/iv_inference_hatch", "shaped/precision_crac" };
        Set<String> ingredients = new HashSet<>();
        for (String id : recipes) {
            CraftingRecipe recipe = craftingRecipe(helper, id);
            for (var ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    ingredients.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        helper.assertTrue(!ingredients.isEmpty(), "the 0.3 recipes must have ingredients");
        for (String ingredient : ingredients) {
            String path = ingredient.replace("gtceu:", "").replace("minecraft:", "").replace("hypercluster:", "");
            helper.assertTrue(!path.startsWith("hpca_"),
                    "zero-HPCA deadlock violated: " + ingredient + " is an HPCA component");
            helper.assertTrue(
                    !path.contains("research_station") && !path.contains("data_bank") &&
                            !path.contains("network_switch") && !path.contains("object_holder") &&
                            !path.contains("computation_") && !path.contains("data_access") &&
                            !path.contains("_data_hatch") && !path.contains("creative_"),
                    "zero-HPCA deadlock violated: " + ingredient +
                            " is a research-station / computation-hatch product");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(ingredient)),
                    "ingredient not registered: " + ingredient);
        }
        System.out.printf("[HC-03] 0.3 survival recipes verified: %d distinct ingredients, all zero-HPCA%n",
                ingredients.size());
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void renderStatesRegisteredForSync(GameTestHelper helper) {
        // Regression for the working-overlay bug: addon machines register after GTMachines.init(),
        // so their MachineRenderStates must be added to MachineDefinition.RENDER_STATE_REGISTRY
        // explicitly, otherwise the render-state payload fails to serialize and clients never
        // switch recipe_logic_status (front overlay stuck on overlay_front).
        for (MachineDefinition machine : new MachineDefinition[] {
                HyperClusterMachines.HV_MICRO_BURNER, HyperClusterMachines.HV_TELEMETRY_ORE_WASHER,
                HyperClusterMachines.HV_TELEMETRY_HATCH,
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR, HyperClusterMachines.DATA_SCRUBBING_TOWER,
                HyperClusterMachines.LORA_ENGRAVING_CHAMBER, HyperClusterMachines.RED_TEAM_SANDBOX,
                HyperClusterMachines.AIR_COOLED_COMPUTE_RACK, HyperClusterMachines.PRECISION_CRAC,
                HyperClusterMachines.IV_INFERENCE_HATCH, HyperClusterMachines.API_SATELLITE_STATION,
                HyperClusterMachines.LOCAL_FALLBACK_GATEWAY,
                // 0.6 — LuV smart compute and training.
                HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK, HyperClusterMachines.UPS_POWER_ARRAY,
                HyperClusterMachines.TENSOR_PRETRAINING_CLUSTER,
                HyperClusterMachines.CLOUD_OFFLOAD_NODE,
                // 0.6.1 — machine-room topology, TB memory and model deployment.
                HyperClusterMachines.VRAM_STORAGE_RACK, HyperClusterMachines.OPTICAL_FABRIC_SWITCH,
                HyperClusterMachines.MOE_ROUTER_RACK, HyperClusterMachines.DATACENTER_HARDWARE_HATCH }) {
            int expected = 0;
            int registered = 0;
            for (com.gregtechceu.gtceu.client.model.machine.MachineRenderState state : machine
                    .getStateDefinition().getPossibleStates()) {
                expected++;
                int id = MachineDefinition.RENDER_STATE_REGISTRY.getId(state);
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
    public static void machineDescriptionTooltipsNotDuplicated(GameTestHelper helper) {
        // Regression for the doubled-description bug: MetaMachineBlock#appendHoverText and
        // MetaMachine#onAddFancyInformationTooltip auto-insert the description from the
        // "<modid>.machine.<name>.tooltip" lang key, so also passing that exact key through an
        // explicit .tooltips(Component.translatable(...)) rendered the description twice in
        // inventory and EMI hovers. The machine registrations no longer pass it explicitly;
        // the description must resolve, appear exactly once, and no two lines may read alike.
        for (MachineDefinition machine : new MachineDefinition[] {
                HyperClusterMachines.HV_MICRO_BURNER, HyperClusterMachines.HV_TELEMETRY_ORE_WASHER,
                HyperClusterMachines.EV_TELEMETRY_CHEMICAL_REACTOR, HyperClusterMachines.DATA_SCRUBBING_TOWER,
                HyperClusterMachines.LORA_ENGRAVING_CHAMBER, HyperClusterMachines.RED_TEAM_SANDBOX,
                HyperClusterMachines.AIR_COOLED_COMPUTE_RACK, HyperClusterMachines.PRECISION_CRAC,
                HyperClusterMachines.IV_INFERENCE_HATCH, HyperClusterMachines.API_SATELLITE_STATION,
                HyperClusterMachines.LOCAL_FALLBACK_GATEWAY,
                // 0.6 — LuV smart compute and training.
                HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK, HyperClusterMachines.UPS_POWER_ARRAY,
                HyperClusterMachines.TENSOR_PRETRAINING_CLUSTER,
                HyperClusterMachines.CLOUD_OFFLOAD_NODE,
                // 0.6.1 — machine-room topology, TB memory and model deployment.
                HyperClusterMachines.VRAM_STORAGE_RACK, HyperClusterMachines.OPTICAL_FABRIC_SWITCH,
                HyperClusterMachines.MOE_ROUTER_RACK }) {
            String mainKey = String.format("%s.machine.%s.tooltip", machine.getId().getNamespace(),
                    machine.getId().getPath());
            helper.assertTrue(Language.getInstance().has(mainKey),
                    machine.getId() + " main tooltip key missing from loaded lang: " + mainKey);
            String description = Component.translatable(mainKey).getString();
            List<Component> lines = new ArrayList<>();
            // The real client seeds hover tooltips with the display name at index 0 before
            // Block#appendHoverText runs — GTCEu's auto-insert then lands at index 1.
            lines.add(machine.asStack(1).getHoverName());
            machine.getBlock().appendHoverText(machine.asStack(1), null, lines,
                    TooltipFlag.NORMAL);
            long hits = lines.stream().filter(line -> line.getString().equals(description)).count();
            helper.assertTrue(hits == 1, machine.getId() + " description tooltip appears " + hits +
                    "x (expected exactly 1 — GTCEu already auto-inserts " + mainKey + ")");
            long distinct = lines.stream().map(Component::getString).distinct().count();
            helper.assertTrue(distinct == lines.size(), machine.getId() + " tooltip lines contain duplicates: " +
                    lines.stream().map(Component::getString).toList());
            System.out.printf("[HC-TOOLTIP] %s: description x%d, %d lines all distinct%n", machine.getId(), hits,
                    lines.size());
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

    /**
     * 0.4 · the closed-source cloud survival chain: the dish frame, panels, feed horn, both
     * cards, the station controller and the fallback gateway are all craftable from vanilla
     * GTCEu IV-era materials — zero HPCA, zero research-station, zero computation products —
     * so the cloud unlocks before any research loop exists.
     */
    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void cloudSurvivalRecipesAndZeroHpcaChain(GameTestHelper helper) {
        // 1) structure blocks + cards.
        CraftingRecipe frame = craftingRecipe(helper, "shaped/satellite_dish_frame");
        helper.assertTrue(frame.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.SATELLITE_DISH_FRAME.get().asItem()),
                "satellite_dish_frame recipe output mismatch");
        helper.assertTrue(frame.getResultItem(helper.getLevel().registryAccess()).getCount() == 4,
                "satellite_dish_frame must yield 4 per craft");
        assertRecipeUses(helper, frame, "gtceu:steel_plate", "the dish frame must use steel plates");

        CraftingRecipe panel = craftingRecipe(helper, "shaped/satellite_dish_panel");
        helper.assertTrue(panel.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.SATELLITE_DISH_PANEL.get().asItem()),
                "satellite_dish_panel recipe output mismatch");
        assertRecipeUses(helper, panel, "gtceu:aluminium_plate",
                "the dish panels must use aluminium plates");

        CraftingRecipe horn = craftingRecipe(helper, "shaped/satellite_feed_horn");
        helper.assertTrue(horn.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterBlocks.SATELLITE_FEED_HORN.get().asItem()),
                "satellite_feed_horn recipe output mismatch");
        assertRecipeUses(helper, horn, "gtceu:iv_sensor", "the feed horn must use an IV sensor");

        CraftingRecipe creditCard = craftingRecipe(helper, "shaped/token_credit_card");
        helper.assertTrue(creditCard.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterItems.TOKEN_CREDIT_CARD.get()),
                "token_credit_card recipe output mismatch");
        CraftingRecipe credentialCard = craftingRecipe(helper, "shaped/api_credential_card");
        helper.assertTrue(credentialCard.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterItems.API_CREDENTIAL_CARD.get()),
                "api_credential_card recipe output mismatch");
        assertRecipeUses(helper, credentialCard, "gtceu:iv_sensor",
                "the credential card must use an IV sensor");

        // 2) the machines.
        CraftingRecipe station = craftingRecipe(helper, "shaped/api_satellite_station");
        helper.assertTrue(station.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.API_SATELLITE_STATION.getBlock().asItem()),
                "api_satellite_station recipe output mismatch");
        assertRecipeUses(helper, station, "hypercluster:satellite_dish_frame",
                "the station must be built from its own dish frames");
        assertRecipeUses(helper, station, "hypercluster:satellite_feed_horn",
                "the station must consume a feed horn");
        assertRecipeUses(helper, station, "gtceu:iv_machine_hull",
                "the station must use an IV machine hull");

        CraftingRecipe gateway = craftingRecipe(helper, "shaped/local_fallback_gateway");
        helper.assertTrue(gateway.getResultItem(helper.getLevel().registryAccess())
                .is(HyperClusterMachines.LOCAL_FALLBACK_GATEWAY.getBlock().asItem()),
                "local_fallback_gateway recipe output mismatch");
        assertRecipeUses(helper, gateway, "gtceu:iv_machine_hull",
                "the fallback gateway must use an IV machine hull");

        // 3) zero-HPCA / zero-research deadlock check on every 0.4 recipe ingredient.
        String[] recipes = { "shaped/satellite_dish_frame", "shaped/satellite_dish_panel",
                "shaped/satellite_feed_horn", "shaped/token_credit_card",
                "shaped/api_credential_card", "shaped/api_satellite_station",
                "shaped/local_fallback_gateway" };
        Set<String> ingredients = new HashSet<>();
        for (String id : recipes) {
            CraftingRecipe recipe = craftingRecipe(helper, id);
            for (var ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    ingredients.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        helper.assertTrue(!ingredients.isEmpty(), "the 0.4 recipes must have ingredients");
        for (String ingredient : ingredients) {
            String path = ingredient.replace("gtceu:", "").replace("minecraft:", "")
                    .replace("hypercluster:", "");
            helper.assertTrue(!path.startsWith("hpca_"),
                    "zero-HPCA deadlock violated: " + ingredient + " is an HPCA component");
            helper.assertTrue(
                    !path.contains("research_station") && !path.contains("data_bank") &&
                            !path.contains("network_switch") && !path.contains("object_holder") &&
                            !path.contains("computation_") && !path.contains("data_access") &&
                            !path.contains("_data_hatch") && !path.contains("creative_"),
                    "zero-HPCA deadlock violated: " + ingredient +
                            " is a research-station / computation-hatch product");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(ingredient)),
                    "ingredient not registered: " + ingredient);
        }
        System.out.printf("[HC-04] 0.4 cloud survival recipes verified: %d distinct ingredients, all zero-HPCA%n",
                ingredients.size());
        helper.succeed();
    }

    /**
     * Patchouli soft-integration contract: zero hard dependency when Patchouli is absent (recipe
     * skipped cleanly by forge:mod_loaded), craftable guide book when Patchouli is loaded, and all
     * book definition/model/texture/bilingual entry JSONs resolve cleanly on the classpath.
     */
    @GameTest(template = "hc_floor", batch = SURVIVAL_BATCH, timeoutTicks = 100)
    public static void patchouliManualSoftIntegrationAndResourcesValid(GameTestHelper helper) {
        boolean patchouliLoaded = ModList.get().isLoaded("patchouli");
        Recipe<?> manualRecipe = helper.getLevel().getRecipeManager()
                .byKey(HyperCluster.id("hypercluster_manual")).orElse(null);
        if (!patchouliLoaded) {
            helper.assertTrue(manualRecipe == null,
                    "hypercluster_manual recipe must be skipped when Patchouli is absent");
        } else {
            helper.assertTrue(manualRecipe instanceof CraftingRecipe,
                    "hypercluster_manual recipe must load when Patchouli is present");
            CraftingRecipe crafting = (CraftingRecipe) manualRecipe;
            ItemStack output = crafting.getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(
                    BuiltInRegistries.ITEM.getKey(output.getItem())
                            .equals(new ResourceLocation("patchouli", "guide_book")),
                    "manual recipe must output patchouli:guide_book, got " + output);
            helper.assertTrue(output.hasTag() &&
                    "hypercluster:hypercluster_manual".equals(output.getTag().getString("patchouli:book")),
                    "manual output NBT patchouli:book mismatch: " + output.getTag());
            assertRecipeUses(helper, crafting, "minecraft:book", "manual recipe must consume minecraft:book");
            assertRecipeUses(helper, crafting, "hypercluster:mining_telemetry_log",
                    "manual recipe must accept mining_telemetry_log");
            assertRecipeUses(helper, crafting, "hypercluster:blank_carrier",
                    "manual recipe must accept blank_carrier");
        }

        JsonObject book = readJsonResource(helper,
                "data/hypercluster/patchouli_books/hypercluster_manual/book.json");
        helper.assertTrue(book.get("use_resource_pack").getAsBoolean(),
                "Patchouli 1.20.1 mod books require use_resource_pack=true");
        helper.assertTrue("hypercluster:hypercluster_manual".equals(book.get("model").getAsString()),
                "book.json model must resolve to assets/hypercluster/models/item/hypercluster_manual.json");
        for (String langKey : new String[] { "name", "subtitle", "landing_text" }) {
            helper.assertTrue(Language.getInstance().has(book.get(langKey).getAsString()),
                    "missing translation key for book.json " + langKey + ": " + book.get(langKey));
        }

        JsonObject model = readJsonResource(helper, "assets/hypercluster/models/item/hypercluster_manual.json");
        helper.assertTrue("hypercluster:item/hypercluster_manual"
                .equals(model.getAsJsonObject("textures").get("layer0").getAsString()),
                "manual model layer0 texture mismatch");
        try (InputStream tex = SurvivalCraftingTests.class.getClassLoader()
                .getResourceAsStream("assets/hypercluster/textures/item/hypercluster_manual.png")) {
            helper.assertTrue(tex != null, "missing assets/hypercluster/textures/item/hypercluster_manual.png");
        } catch (Exception e) {
            helper.assertTrue(false, "failed reading manual texture: " + e.getMessage());
        }

        String[] bookFiles = {
                "categories/01_hv_start.json",
                "categories/02_ev_workshop.json",
                "categories/03_iv_datacenter.json",
                "categories/04_iv_cloud.json",
                "categories/05_luv_training.json",
                "categories/06_topology.json",
                "entries/01_hv_start/telemetry_ore_washer.json",
                "entries/01_hv_start/telemetry_hatch.json",
                "entries/01_hv_start/micro_burner_and_circuits.json",
                "entries/02_ev_workshop/telemetry_chemical_reactor.json",
                "entries/02_ev_workshop/data_scrubbing_tower.json",
                "entries/02_ev_workshop/lora_engraving_chamber.json",
                "entries/02_ev_workshop/red_team_sandbox.json",
                "entries/03_iv_datacenter/air_cooled_compute_rack.json",
                "entries/03_iv_datacenter/crac_and_pue.json",
                "entries/03_iv_datacenter/inference_hatch_and_snack.json",
                "entries/04_iv_cloud/satellite_station_and_accounts.json",
                "entries/04_iv_cloud/cloud_billing_and_cache.json",
                "entries/04_iv_cloud/rate_limit_and_fallback.json",
                // 0.6 — chapter 5 documents only what this version actually ships.
                "entries/05_luv_training/liquid_cooled_compute_rack.json",
                "entries/05_luv_training/ups_power_array.json",
                "entries/05_luv_training/tensor_pretraining_cluster.json",
                "entries/05_luv_training/cloud_offload_node.json",
                // 0.6.1 — chapter 6 documents the TB memory system and the model families.
                "entries/06_topology/vram_and_fabric.json",
                "entries/06_topology/moe_router.json",
                "entries/06_topology/model_families.json"
        };
        for (String lang : new String[] { "zh_cn", "en_us" }) {
            for (String rel : bookFiles) {
                JsonObject obj = readJsonResource(helper,
                        "assets/hypercluster/patchouli_books/hypercluster_manual/" + lang + "/" + rel);
                helper.assertTrue(obj.has("name") && !obj.get("name").getAsString().isEmpty(),
                        "missing name in " + lang + "/" + rel);
                if (rel.startsWith("entries/")) {
                    // The icon must be a registered item, or Patchouli renders a missing sprite.
                    String icon = obj.get("icon").getAsString();
                    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(icon)),
                            "entry icon not registered: " + icon + " (" + lang + "/" + rel + ")");
                    // The category must be one of the book's own chapters.
                    String category = obj.get("category").getAsString()
                            .substring(obj.get("category").getAsString().indexOf(':') + 1);
                    JsonObject cat = readJsonResource(helper,
                            "assets/hypercluster/patchouli_books/hypercluster_manual/" + lang + "/categories/" +
                                    category + ".json");
                    helper.assertTrue(cat.has("name"), "missing category file " + lang + "/" + category);
                    // Every crafting page must point at a recipe this mod really registered.
                    for (var page : obj.getAsJsonArray("pages")) {
                        JsonObject p = page.getAsJsonObject();
                        for (String key : new String[] { "recipe", "recipe2" }) {
                            if (p.has(key)) {
                                helper.assertTrue(
                                        helper.getLevel().getRecipeManager()
                                                .byKey(ResourceLocation.tryParse(p.get(key).getAsString()))
                                                .isPresent(),
                                        "manual page references a missing recipe: " + p.get(key) +
                                                " (" + lang + "/" + rel + ")");
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("[HC-MANUAL] Patchouli soft integration verified (patchouliLoaded=%s, %d bilingual files)%n",
                patchouliLoaded, bookFiles.length);
        helper.succeed();
    }

    private static JsonObject readJsonResource(GameTestHelper helper, String path) {
        try (InputStream stream = SurvivalCraftingTests.class.getClassLoader().getResourceAsStream(path)) {
            helper.assertTrue(stream != null, "missing resource on classpath: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            helper.assertTrue(false, "invalid JSON at " + path + ": " + e.getMessage());
            return new JsonObject();
        }
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
