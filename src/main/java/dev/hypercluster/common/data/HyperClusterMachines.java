package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.machine.TelemetryChemicalReactorMachine;
import dev.hypercluster.common.machine.TelemetryOreWasherMachine;
import dev.hypercluster.common.machine.datacenter.AirCooledComputeRackMachine;
import dev.hypercluster.common.machine.datacenter.IvInferenceHatchMachine;
import dev.hypercluster.common.machine.datacenter.PrecisionCracMachine;

import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.BACK;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.RIGHT;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.UP;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction;

/**
 * GregTech HyperCluster machines.
 *
 * <ul>
 * <li>{@code hypercluster:hv_micro_burner} — the HV Micro Burner burning telemetry logs/datasets
 * and blank carriers into domain edge chips.</li>
 * <li>{@code hypercluster:hv_telemetry_ore_washer} — the own HV Telemetry Ore Washer (0.1).
 * Mirrors the base wash recipe and the chip-enhanced wash recipe of
 * {@code hypercluster:telemetry_ore_washing} and keeps a precise, IO.OUT-verified telemetry log
 * buffer.</li>
 * <li>{@code hypercluster:ev_telemetry_chemical_reactor} (0.2) — the own EV Telemetry Chemical
 * Reactor. Same settlement architecture, chemical domain: every settled batch of the mirrored
 * {@code gtceu:chemical_reactor/indium_concentrate_separation} recipe (circuit #1) or its
 * chip-enhanced counterpart deposits one chemical telemetry log.</li>
 * <li>{@code hypercluster:data_scrubbing_tower} (0.2) — the EV Data Scrubbing Tower: a fixed
 * 3×5×3 GT-style multiblock (EV titanium casing + glass + standard hatches) fractionating raw
 * telemetry logs into high-purity domain datasets, clean datasets and corrupted noise, and
 * recycling corrupted noise into raw silicon.</li>
 * </ul>
 */
public final class HyperClusterMachines {

    public static MachineDefinition HV_MICRO_BURNER;
    public static MachineDefinition HV_TELEMETRY_ORE_WASHER;
    public static MachineDefinition EV_TELEMETRY_CHEMICAL_REACTOR;
    public static MultiblockMachineDefinition DATA_SCRUBBING_TOWER;
    public static MultiblockMachineDefinition LORA_ENGRAVING_CHAMBER;
    public static MultiblockMachineDefinition RED_TEAM_SANDBOX;
    public static MultiblockMachineDefinition AIR_COOLED_COMPUTE_RACK;
    public static MultiblockMachineDefinition PRECISION_CRAC;
    public static MachineDefinition IV_INFERENCE_HATCH;

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
                "Burns edge chips from telemetry logs and high-purity domain datasets on blank carriers.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.hv_telemetry_ore_washer.tooltip",
                "Settles telemetry against real outputs. Base recipe: circuit #1. Chip recipe: circuit #2 (chip is not consumed).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.ev_telemetry_chemical_reactor.tooltip",
                "Settles chemical telemetry against real outputs. Base recipe: circuit #1. Chip recipe: circuit #2 (chip is not consumed, half the aluminium dust).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.data_scrubbing_tower.tooltip",
                "Fractions raw telemetry logs into high-purity datasets. Also formats corrupted noise back into raw silicon.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.micro_burner", "Micro Burner");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_ore_washing", "Telemetry Ore Washing");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_chemical_processing",
                "Telemetry Chemical Processing");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.data_scrubbing", "Data Scrubbing");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.lora_engraving_chamber.tooltip",
                "Engraves domain LoRA cards from high-purity datasets plus golden samples on blank carriers.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.red_team_sandbox.tooltip",
                "Red-teams ordinary edge chips against corrupted noise. Validated chips come out aligned.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.lora_engraving", "LoRA Engraving");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.red_team_testing", "Red-Team Testing");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_buffer.tooltip",
                "Telemetry log buffer: %s / %s. One log per settled batch.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.telemetry_buffer.full",
                "Buffer full: further settled batches drop their logs.");

        // 0.3 — IV datacenter machines, slots and settlement lines.
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.tooltip",
                "Tier I air-cooled compute rack: 4 IV compute blades give up to 24 CWU/t through optical pipes.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.blade_slot",
                "IV compute blade slot: 6 CWU/t per blade.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.snack_slot",
                "Green snack pack: keep the machine room tidy (-5% total EU/t, config-gated).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.providing",
                "Providing computation");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.computation",
                "Computation: %s / %s CWU/t");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.room",
                "Room rating: Tier %s (environment %s)");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.pue",
                "PUE: %s (IT %s EU/t + cooling %s EU/t)");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.standby",
                "Standby: %s");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.snack_bonus",
                "Snack pack bonus active: -5% total EU/t.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.derate.none",
                "Providing computation.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.derate.paused",
                "Derated to 0 CWU/t: rack paused (soft mallet).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.derate.maintenance",
                "Derated to 0 CWU/t: maintenance problems unfixed.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.derate.no_blades",
                "Derated to 0 CWU/t: no compute blades inserted.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.air_cooled_compute_rack.derate.low_power",
                "Derated to 0 CWU/t: not enough energy.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.iv_inference_hatch.tooltip",
                "IV inference coprocessor hatch: pulls real CWU/t through optical pipes to run boosted tower recipes (25%/50% less EU/t).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.iv_inference_hatch.chip_slot",
                "Inference chip: ordinary domain edge chips serve 2 CWU/t recipes, aligned chips up to 8 CWU/t.");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.precision_crac.tooltip",
                "Precision CRAC: a running unit within 8 blocks upgrades a compute rack room to Tier II (PUE 1.45).");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.precision_crac.running",
                "Cooling");
        HyperCluster.REGISTRATE.addRawLang("hypercluster.machine.precision_crac.power",
                "Blower draw: %s");

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

        // 0.2 — own EV Telemetry Chemical Reactor: same settlement architecture, chemical domain.
        // Benchmark whitelist recipe: the mirrored indium_concentrate_separation (circuit #1),
        // chip variant on circuit #2 keeps the chip retrievable and halves the aluminium dust.
        EV_TELEMETRY_CHEMICAL_REACTOR = HyperCluster.REGISTRATE
                .machine("ev_telemetry_chemical_reactor",
                        holder -> new TelemetryChemicalReactorMachine(holder, GTValues.EV,
                                defaultTankSizeFunction))
                .tier(GTValues.EV)
                .langValue("EV Telemetry Chemical Reactor")
                .editableUI(TelemetryChemicalReactorMachine.EDITABLE_UI_CREATOR
                        .apply(HyperCluster.id("telemetry_chemical_reactor"),
                                HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .workableTieredHullModel(HyperCluster.id("block/machines/telemetry_chemical_reactor"))
                .tooltips(GTMachineUtils.workableTiered(GTValues.EV, GTValues.V[GTValues.EV],
                        GTValues.V[GTValues.EV] * 64L, HyperClusterRecipeTypes.TELEMETRY_CHEMICAL_PROCESSING_RECIPES,
                        defaultTankSizeFunction.applyAsInt(GTValues.EV), true))
                .tooltips(Component.translatable("hypercluster.machine.ev_telemetry_chemical_reactor.tooltip"))
                .register();

        // 0.2 — EV Data Scrubbing Tower: a fixed 3×5×3 GT-style multiblock.
        DATA_SCRUBBING_TOWER = HyperCluster.REGISTRATE
                .multiblock("data_scrubbing_tower",
                        holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("Data Scrubbing Tower")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.DATA_SCRUBBING_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.MACHINE_CASING_EV)
                .pattern(definition -> FactoryBlockPattern.start(RIGHT, BACK, UP)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XGX", "X#X", "XGX")
                        .aisle("XGX", "X#X", "XGX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XSX", "XXX", "XXX")
                        .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                        .where('X', Predicates.blocks(GTBlocks.MACHINE_CASING_EV.get())
                                .or(Predicates
                                        .abilities(
                                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(
                                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(
                                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(
                                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_ENERGY)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(
                                        HyperClusterPartAbilities.INFERENCE_COMPUTATION)
                                        .setMinGlobalLimited(0).setMaxGlobalLimited(1))
                                .or(Predicates.autoAbilities(true, false, false)))
                        .where('G', Predicates.blocks(Blocks.GLASS))
                        .where('#', Predicates.air())
                        .build())
                .allowExtendedFacing(false)
                .workableCasingModel(new ResourceLocation("gtceu", "block/casings/voltage/ev/side"),
                        HyperCluster.id("block/machines/data_scrubbing_tower"))
                .tooltips(Component.translatable("hypercluster.machine.data_scrubbing_tower.tooltip"))
                .register();

        // 0.2.1 — LoRA Engraving Chamber: a fixed 3×3×3 EV multiblock turning high-purity
        // datasets + golden samples on blank carriers into domain LoRA cards. Same shape as the
        // red-team sandbox (verified placement semantics of the 3×5×3 tower aisle order).
        LORA_ENGRAVING_CHAMBER = HyperCluster.REGISTRATE
                .multiblock("lora_engraving_chamber",
                        holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("LoRA Engraving Chamber")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.LORA_ENGRAVING_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.MACHINE_CASING_EV)
                .pattern(definition -> chamberPattern(definition))
                .allowExtendedFacing(false)
                .workableCasingModel(new ResourceLocation("gtceu", "block/casings/voltage/ev/side"),
                        HyperCluster.id("block/machines/lora_engraving_chamber"))
                .tooltips(Component.translatable("hypercluster.machine.lora_engraving_chamber.tooltip"))
                .register();

        // 0.2.1 — Red-Team Sandbox: a fixed 3×3×3 EV multiblock validating ordinary edge chips
        // against corrupted noise into aligned ones.
        RED_TEAM_SANDBOX = HyperCluster.REGISTRATE
                .multiblock("red_team_sandbox",
                        holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("Red-Team Sandbox")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(HyperClusterRecipeTypes.RED_TEAM_TESTING_RECIPES)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.MACHINE_CASING_EV)
                .pattern(definition -> chamberPattern(definition))
                .allowExtendedFacing(false)
                .workableCasingModel(new ResourceLocation("gtceu", "block/casings/voltage/ev/side"),
                        HyperCluster.id("block/machines/red_team_sandbox"))
                .tooltips(Component.translatable("hypercluster.machine.red_team_sandbox.tooltip"))
                .register();

        // 0.3 — Tier I air-cooled compute rack: a fixed 3×5×2 IV multiblock (intake wall in
        // front, exhaust wall behind) offering up to 24 CWU/t through GTCEu's native computation
        // transmission hatch. No PCB coolant required.
        AIR_COOLED_COMPUTE_RACK = HyperCluster.REGISTRATE
                .multiblock("air_cooled_compute_rack", AirCooledComputeRackMachine::new)
                .langValue("Air-Cooled Compute Rack")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(HyperClusterBlocks.RACK_CASING_INTAKE)
                .pattern(definition -> FactoryBlockPattern.start(RIGHT, UP, BACK)
                        .aisle("III", "III", "ISI", "III", "III")
                        .aisle("EEE", "XXX", "EEE", "EEE", "EEE")
                        .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                        .where('I', Predicates.blocks(HyperClusterBlocks.RACK_CASING_INTAKE.get()))
                        .where('E', Predicates.blocks(HyperClusterBlocks.RACK_CASING_EXHAUST.get()))
                        // The hatch row of the exhaust wall: plain casing, the energy hatch(es),
                        // GTCEu's native computation transmission hatch and the maintenance hatch.
                        .where('X', Predicates.blocks(HyperClusterBlocks.RACK_CASING_EXHAUST.get())
                                .or(Predicates.abilities(PartAbility.INPUT_ENERGY)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION)
                                        .setExactLimit(1))
                                .or(Predicates.autoAbilities(true, false, false)))
                        .build())
                .allowExtendedFacing(false)
                .workableCasingModel(HyperCluster.id("block/datacenter/rack_casing_front_intake"),
                        HyperCluster.id("block/machines/air_cooled_compute_rack"))
                .tooltips(Component.translatable("hypercluster.machine.air_cooled_compute_rack.tooltip"))
                .register();

        // 0.3 — Tier II precision CRAC: a compact 3×3×1 IV blower unit whose running presence
        // upgrades nearby compute rack rooms to Tier II.
        PRECISION_CRAC = HyperCluster.REGISTRATE
                .multiblock("precision_crac", PrecisionCracMachine::new)
                .langValue("Precision CRAC")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(GTBlocks.MACHINE_CASING_IV)
                .pattern(definition -> FactoryBlockPattern.start(RIGHT, UP, BACK)
                        .aisle("CCC", "CSC", "CCC")
                        .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                        .where('C', Predicates.blocks(GTBlocks.MACHINE_CASING_IV.get())
                                .or(Predicates.abilities(PartAbility.INPUT_ENERGY)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(Predicates.autoAbilities(true, false, false)))
                        .build())
                .allowExtendedFacing(false)
                .workableCasingModel(new ResourceLocation("gtceu", "block/casings/voltage/iv/side"),
                        HyperCluster.id("block/machines/precision_crac"))
                .tooltips(Component.translatable("hypercluster.machine.precision_crac.tooltip"))
                .register();

        // 0.3 — IV inference coprocessor hatch: an optical computation receiver part with an
        // inference chip slot, attachable to the data scrubbing tower.
        IV_INFERENCE_HATCH = HyperCluster.REGISTRATE
                .machine("iv_inference_hatch", holder -> new IvInferenceHatchMachine(holder))
                .tier(GTValues.IV)
                .langValue("IV Inference Hatch")
                .rotationState(RotationState.ALL)
                .abilities(HyperClusterPartAbilities.INFERENCE_COMPUTATION)
                .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                .workableTieredHullModel(HyperCluster.id("block/machines/inference_hatch"))
                .tooltips(Component.translatable("hypercluster.machine.iv_inference_hatch.tooltip"))
                .register();
    }

    /**
     * The shared 3×3×3 chamber shape of the 0.2.1 multiblocks: bottom layer
     * {@code "XSX","XXX","XXX"} (controller at local (1,0,0), hatches on the bottom ring), middle
     * layer {@code "XGX","X#X","XGX"} (glass windows around an air core), solid casing cap on top.
     */
    private static com.gregtechceu.gtceu.api.pattern.BlockPattern chamberPattern(
                                                                                 MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RIGHT, BACK, UP)
                .aisle("XSX", "XXX", "XXX")
                .aisle("XGX", "X#X", "XGX")
                .aisle("XXX", "XXX", "XXX")
                .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where('X', Predicates.blocks(GTBlocks.MACHINE_CASING_EV.get())
                        .or(Predicates.abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_ENERGY)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.autoAbilities(true, false, false)))
                .where('G', Predicates.blocks(Blocks.GLASS))
                .where('#', Predicates.air())
                .build();
    }

    /** Mirrors GTMachines.init(): GTCEu 7.4.0 registers render states of its own machines only. */
    private static void registerRenderStates() {
        // Addon machines register after GTMachines.init(), so their MachineRenderStates never
        // enter MachineDefinition.RENDER_STATE_REGISTRY — the render-state sync payload then
        // fails ("Can't find id ... in IdMapper") and clients never see recipe_logic_status
        // switch, leaving the front overlay on its idle texture. Register them explicitly
        // (every machine added to this addon MUST be listed here — asserted by SurvivalCraftingTests).
        for (MachineDefinition machine : new MachineDefinition[] { HV_MICRO_BURNER, HV_TELEMETRY_ORE_WASHER,
                EV_TELEMETRY_CHEMICAL_REACTOR, DATA_SCRUBBING_TOWER, LORA_ENGRAVING_CHAMBER,
                RED_TEAM_SANDBOX, AIR_COOLED_COMPUTE_RACK, PRECISION_CRAC, IV_INFERENCE_HATCH }) {
            for (com.gregtechceu.gtceu.client.model.machine.MachineRenderState renderState : machine
                    .getStateDefinition().getPossibleStates()) {
                MachineDefinition.RENDER_STATE_REGISTRY.add(renderState);
            }
        }
    }
}
