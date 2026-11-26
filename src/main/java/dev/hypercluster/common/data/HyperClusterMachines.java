package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;

import net.minecraft.network.chat.Component;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.machine.TelemetryOreWasherMachine;

import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction;

/**
 * GregTech HyperCluster machines (P03 + P04):
 * <ul>
 * <li>{@code hypercluster:hv_micro_burner} — the HV Micro Burner burning telemetry logs and blank
 * carriers into mining edge chips.</li>
 * <li>{@code hypercluster:hv_telemetry_ore_washer} — the own HV Telemetry Ore Washer. Runs the
 * mirrored base wash recipe and the chip-enhanced wash recipe of
 * {@code hypercluster:telemetry_ore_washing} and keeps a precise, IO.OUT-verified telemetry log
 * buffer.</li>
 * </ul>
 */
public final class HyperClusterMachines {

    public static MachineDefinition HV_MICRO_BURNER;
    public static MachineDefinition HV_TELEMETRY_ORE_WASHER;

    private HyperClusterMachines() {}

    /** Called from {@code IGTAddon#initializeAddon()}. */
    public static void init() {
        // GTCEu froze its MACHINES registry during GTMachines.init(); same unfreeze/refreeze
        // pattern as for recipe types and blocks/items, re-freezed after our definitions load.
        com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.unfreeze();
        try {
            initMachines();
            registerRenderStates();
        } finally {
            com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.freeze();
        }
    }

    private static void initMachines() {
        // English entries for machine tooltips, recipe type categories and buffer hints.
        // (zh_cn is maintained manually under assets/hypercluster/lang/zh_cn.json.)
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.hv_micro_burner.tooltip",
                "Burns mining telemetry logs and blank carriers into mining edge chips.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.hv_telemetry_ore_washer.tooltip",
                "Settles telemetry against real outputs. Base recipe: circuit #1. Chip recipe: circuit #2 (chip is not consumed).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.micro_burner", "Micro Burner");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_ore_washing", "Telemetry Ore Washing");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_buffer.tooltip",
                "Telemetry log buffer: %s / %s. One log per settled batch.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_buffer.full",
                "Buffer full: further settled batches drop their logs.");

        // P03 — HV Micro Burner: logging chain (telemetry log + blank carrier -> edge chip).
        HV_MICRO_BURNER = HyperCluster.REGISTRATE
                .machine("hv_micro_burner",
                        holder -> new SimpleTieredMachine(holder, GTValues.HV, defaultTankSizeFunction))
                .tier(GTValues.HV)
                .langValue("HV Micro Burner")
                .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR
                        .apply(HyperCluster.id("micro_burner"), HyperClusterRecipeTypes.MICRO_BURNER_RECIPES))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.MICRO_BURNER_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .workableTieredHullModel(HyperCluster.id("block/machines/micro_burner"))
                .tooltips(GTMachineUtils.workableTiered(GTValues.HV, GTValues.V[GTValues.HV],
                        GTValues.V[GTValues.HV] * 64L, HyperClusterRecipeTypes.MICRO_BURNER_RECIPES, 0L, true))
                .tooltips(Component.translatable("hypercluster.machine.hv_micro_burner.tooltip"))
                .register();

        // P04 — own HV Telemetry Ore Washer with precise settlement + log buffer.
        HV_TELEMETRY_ORE_WASHER = HyperCluster.REGISTRATE
                .machine("hv_telemetry_ore_washer",
                        holder -> new TelemetryOreWasherMachine(holder, GTValues.HV, defaultTankSizeFunction))
                .tier(GTValues.HV)
                .langValue("HV Telemetry Ore Washer")
                .editableUI(TelemetryOreWasherMachine.EDITABLE_UI_CREATOR
                        .apply(HyperCluster.id("telemetry_ore_washer"),
                                HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .workableTieredHullModel(HyperCluster.id("block/machines/telemetry_ore_washer"))
                .tooltips(GTMachineUtils.workableTiered(GTValues.HV, GTValues.V[GTValues.HV],
                        GTValues.V[GTValues.HV] * 64L, HyperClusterRecipeTypes.TELEMETRY_ORE_WASHING_RECIPES,
                        defaultTankSizeFunction.applyAsInt(GTValues.HV), true))
                .tooltips(Component.translatable("hypercluster.machine.hv_telemetry_ore_washer.tooltip"))
                .register();
    }

    /** Mirrors GTMachines.init(): GTCEu 7.4.0 registers render states of its own machines only. */
    private static void registerRenderStates() {
        // Addon machines register after GTMachines.init(), so their MachineRenderStates never
        // enter MachineDefinition.RENDER_STATE_REGISTRY — the render-state sync payload then
        // fails ("Can't find id ... in IdMapper") and clients never see recipe_logic_status
        // switch, leaving the front overlay on its idle texture. Register them explicitly.
        for (MachineDefinition machine : new MachineDefinition[] { HV_MICRO_BURNER,
                HV_TELEMETRY_ORE_WASHER }) {
            for (com.gregtechceu.gtceu.client.model.machine.MachineRenderState renderState : machine
                    .getStateDefinition().getPossibleStates()) {
                MachineDefinition.RENDER_STATE_REGISTRY.add(renderState);
            }
        }
    }
}
