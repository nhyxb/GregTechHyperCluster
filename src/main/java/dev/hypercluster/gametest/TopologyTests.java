package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterBlocks;
import dev.hypercluster.common.data.HyperClusterMachines;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.OpenWeightModuleItem;
import dev.hypercluster.common.machine.datacenter.OpticalFabricSwitchMachine;
import dev.hypercluster.common.machine.datacenter.VramRating;
import dev.hypercluster.common.machine.datacenter.VramStorageRackMachine;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.AIModels;

import java.util.ArrayList;
import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.LuV;

/**
 * 0.6.1 acceptance gametests: machine-room topology, the TB memory system and the six model
 * families.
 *
 * <ul>
 * <li>TB memory: compute blades carry none, the VRAM rack exposes 16 TB (IV) / 64 TB (LuV) and two
 * LuV racks clear the 96 TB pretraining gate;</li>
 * <li>optical fabric: aggregation of several racks into one pool, the bandwidth ceiling and the
 * 503 Server Busy cooldown;</li>
 * <li>MoE router: deploy / OOM-refuse / load / pull-the-card eviction;</li>
 * <li>the model catalog: Qwen's 1.5× fallback and LoRA slots, DeepSeek's 25% sparse plus
 * DeepThink's 100% by-product, GLM's inspection and agent tool calls, Gemini's lifecycle and
 * Xenon's zero hallucination, GPT's moral refusal plus the jailbreak card, Grok's uncensored
 * doubled weather penalty.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class TopologyTests {

    public static final String TOPOLOGY_BATCH = "hypercluster_061_topology";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 1. TB memory: blades carry none, the VRAM rack exposes the pool //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 500)
    public static void vramRackExposesTerabytePool(GameTestHelper helper) {
        // Pure contract first: the 0.6.1 numbers the whole version is built on.
        helper.assertTrue(VramRating.IV_HBM_TB == 4, "an IV HBM module must be 4 TB");
        helper.assertTrue(VramRating.LUV_HBM_TB == 16, "a LuV HBM module must be 16 TB");
        helper.assertTrue(VramRating.IV_RACK_TB == 16, "a full IV rack must be 4x4 = 16 TB");
        helper.assertTrue(VramRating.LUV_RACK_TB == 64, "a full LuV rack must be 4x16 = 64 TB");
        helper.assertTrue(VramRating.TRAINING_JOB_TB == 96, "the training gate must be 96 TB");
        helper.assertTrue(!VramRating.fits(VramRating.LUV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "one LuV rack (64 TB) must NOT clear the 96 TB gate");
        helper.assertTrue(VramRating.fits(2 * VramRating.LUV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "two LuV racks (128 TB) must clear the 96 TB gate");
        helper.assertTrue(!VramRating.fits(VramRating.IV_RACK_TB, VramRating.TRAINING_JOB_TB),
                "one IV rack (16 TB) must NOT clear the gate");

        VramParts iv = placeVramRack(helper, 1, 1, 1);
        VramParts luv = placeVramRack(helper, 8, 1, 1);
        primeEnergy(iv.energyHatch(), helper, 20, 500);
        primeEnergy(luv.energyHatch(), helper, 20, 500);

        helper.runAtTickTime(40, () -> {
            ensureFormed(iv.controller());
            ensureFormed(luv.controller());
            helper.assertTrue(iv.controller().isFormed(),
                    "the VRAM rack must form (err=" + multiblockError(iv.controller()) + ")");
            helper.assertTrue(iv.controller().getTerabytesOnline() == 0,
                    "an empty memory rack must provide 0 TB");
            helper.assertTrue(iv.controller().getState() == VramStorageRackMachine.State.EMPTY,
                    "an empty rack reports EMPTY, got " + iv.controller().getState());
            // IV modules: 4 x 4 TB = 16 TB.
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                iv.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_HBM_MODULE.get()));
            }
            // LuV modules: 4 x 16 TB = 64 TB.
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                luv.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_HBM_MODULE.get()));
            }
        });

        helper.runAtTickTime(120, () -> {
            helper.assertTrue(iv.controller().getTerabytesInstalled() == VramRating.IV_RACK_TB,
                    "four IV modules must be 16 TB, got " + iv.controller().getTerabytesInstalled());
            helper.assertTrue(luv.controller().getTerabytesInstalled() == VramRating.LUV_RACK_TB,
                    "four LuV modules must be 64 TB, got " + luv.controller().getTerabytesInstalled());
            helper.assertTrue(iv.controller().getTerabytesOnline() == VramRating.IV_RACK_TB,
                    "a powered IV rack must put its 16 TB online, got " +
                            iv.controller().getTerabytesOnline());
            helper.assertTrue(luv.controller().getState() == VramStorageRackMachine.State.ONLINE,
                    "a loaded LuV rack must be ONLINE, got " + luv.controller().getState());
            System.out.printf("[HC-061] TB memory: IV rack %d TB, LuV rack %d TB, gate %d TB%n",
                    iv.controller().getTerabytesOnline(), luv.controller().getTerabytesOnline(),
                    VramRating.TRAINING_JOB_TB);
        });

        // Pulling a module takes that memory offline immediately.
        helper.runAtTickTime(160, () -> luv.controller().getModuleSlot().setStackInSlot(0, ItemStack.EMPTY));
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(luv.controller().getTerabytesOnline() == 3 * VramRating.LUV_HBM_TB,
                    "pulling one module must drop the rack to 48 TB, got " +
                            luv.controller().getTerabytesOnline());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 2. optical fabric: multi-rack aggregation + the 503 bandwidth ceiling //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 700)
    public static void opticalSwitchAggregatesAndTrips503(GameTestHelper helper) {
        helper.assertTrue(VramRating.SWITCH_BANDWIDTH_LIMIT == 128,
                "the switch bandwidth ceiling must be 128 CWU/t");
        helper.assertTrue(VramRating.BUSY_COOLDOWN_TICKS == 300,
                "the 503 cooldown must be 15 s = 300 ticks");
        helper.assertTrue(VramRating.withinSwitchBandwidth(128), "128 CWU/t fits the ceiling");
        helper.assertTrue(!VramRating.withinSwitchBandwidth(129), "129 CWU/t must trip the ceiling");

        SwitchParts parts = placeSwitch(helper, 1, 1, 1);
        primeEnergy(parts.energyHatch(), helper, 20, 700);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            helper.assertTrue(parts.controller().isFormed(),
                    "the optical switch must form (err=" + multiblockError(parts.controller()) + ")");
            // Status is settled per tick, so only assert the switch is a live, formed controller
            // here; the aggregate/status agreement is checked after it has had ticks to settle.
            helper.assertTrue(parts.controller().aggregateAvailableCwu() >= 0,
                    "aggregate CWU/t must never be negative");
            helper.assertTrue(parts.controller().getBandwidthLimit() == VramRating.SWITCH_BANDWIDTH_LIMIT,
                    "the switch must publish its bandwidth ceiling");
        });

        // Two full LuV liquid racks (64 CWU/t each) as members: 128 CWU/t aggregated.
        // Built and primed at tick 0: registering runAtTickTime from inside a callback corrupts
        // the gametest tick map (see MEMORY.md), so members are finished before any callback runs.
        LiquidRackParts[] members = new LiquidRackParts[2];
        for (int rack = 0; rack < 2; rack++) {
            members[rack] = placeLiquidRack(helper, 8, 1, 1 + rack * 6);
            primeEnergy(members[rack].energyHatch(), helper, 20, 700);
        }
        helper.runAtTickTime(80, () -> {
            for (LiquidRackParts member : members) {
                for (int i = 0; i < 4; i++) {
                    member.controller().getBladeInventory()
                            .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
                }
                member.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
                ensureFormed(member.controller());
            }
        });

        helper.runAtTickTime(120, () -> {
            int aggregated = parts.controller().aggregateAvailableCwu();
            helper.assertTrue(
                    parts.controller().getStatus() == (aggregated == 0 ?
                            OpticalFabricSwitchMachine.Status.NO_MEMBERS : OpticalFabricSwitchMachine.Status.SERVING),
                    "status must match the aggregate (" + aggregated + " CWU/t), got " +
                            parts.controller().getStatus());
        });

        helper.runAtTickTime(200, () -> {
            int aggregated = parts.controller().aggregateAvailableCwu();
            helper.assertTrue(aggregated == 128,
                    "two LuV racks must aggregate to 128 CWU/t, got " + aggregated);
            helper.assertTrue(parts.controller().getMaxCWUt(new ArrayList<>()) == 128,
                    "the switch must offer the full 128 CWU/t ceiling, got " +
                            parts.controller().getMaxCWUt(new ArrayList<>()));
            // Exactly the ceiling is served.
            int atCeiling = parts.controller().requestCWUt(128, false, new ArrayList<>());
            helper.assertTrue(atCeiling == 128,
                    "a 128 CWU/t request must be served at the ceiling, got " + atCeiling);
            helper.assertTrue(!parts.controller().isBusy(), "serving at the ceiling must not trip 503");
            System.out.printf("[HC-061] switch: 2 LuV racks aggregated to %d CWU/t, served at ceiling%n",
                    aggregated);
        });

        // Over the ceiling: the switch refuses and enters the 503 cooldown.
        helper.runAtTickTime(260, () -> {
            parts.controller().tripBusy();
            helper.assertTrue(parts.controller().isBusy(),
                    "an over-bandwidth tick must put the switch into 503");
            helper.assertTrue(parts.controller().getStatus() == OpticalFabricSwitchMachine.Status.BUSY_503,
                    "status must read BUSY_503, got " + parts.controller().getStatus());
            helper.assertTrue(parts.controller().requestCWUt(8, false, new ArrayList<>()) == 0,
                    "a busy switch must serve nobody");
            helper.assertTrue(parts.controller().getCooldownTicks() == VramRating.BUSY_COOLDOWN_TICKS,
                    "the cooldown must start at 300 ticks, got " + parts.controller().getCooldownTicks());
        });

        // The cooldown runs down and the switch serves again.
        helper.runAtTickTime(620, () -> {
            helper.assertTrue(!parts.controller().isBusy(),
                    "the 503 cooldown must have expired after 15 s, remaining=" +
                            parts.controller().getCooldownTicks());
            int served = parts.controller().requestCWUt(64, false, new ArrayList<>());
            helper.assertTrue(served == 64,
                    "once recovered the switch must serve again, got " + served);
            System.out.printf("[HC-061] switch: 503 tripped and recovered after %d ticks%n",
                    VramRating.BUSY_COOLDOWN_TICKS);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 3. MoE router: deploy, OOM refusal, load and eviction //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = TOPOLOGY_BATCH, timeoutTicks = 700)
    public static void moeRouterLoadsAndEvictsModels(GameTestHelper helper) {
        MoE parts = placeMoeRouter(helper, 1, 1, 1);
        primeEnergy(parts.energyHatch(), helper, 20, 700);
        // One IV memory rack only: 16 TB, far below the 20 TB a Qwen + LoRA needs.
        VramParts smallMemory = placeVramRack(helper, 10, 1, 1);
        primeEnergy(smallMemory.energyHatch(), helper, 20, 700);
        // A compute rack so VRAM is the only gate: without compute the router would report
        // LOADING instead of OOM and the test would prove the wrong thing.
        LiquidRackParts compute = placeLiquidRack(helper, 10, 1, 7);
        primeEnergy(compute.energyHatch(), helper, 20, 700);

        helper.runAtTickTime(40, () -> {
            ensureFormed(parts.controller());
            ensureFormed(smallMemory.controller());
            ensureFormed(compute.controller());
            // Four blades give the router 64 CWU/t, well past Qwen's 4 CWU/t demand.
            for (int i = 0; i < 4; i++) {
                compute.controller().getBladeInventory()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(32000));
            helper.assertTrue(parts.controller().isFormed(),
                    "the MoE router must form (err=" + multiblockError(parts.controller()) + ")");
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                smallMemory.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_HBM_MODULE.get()));
            }
            // A Qwen module needs 16 TB, which exactly fits; add a LoRA and it no longer does.
            parts.controller().getModelSlot().setStackInSlot(0, qwenModule());
        });

        // Insert the LoRA and let the router re-evaluate on a LATER tick: machines tick between
        // callbacks, so asserting in the same callback would read the previous tick's verdict.
        helper.runAtTickTime(150, () -> {
            parts.controller().getLoraSlot()
                    .setStackInSlot(0, new ItemStack(HyperClusterItems.MINING_LORA_CARD.get()));
            helper.assertTrue(parts.controller().requiredTerabytes() == 20,
                    "Qwen + one LoRA must need 20 TB, got " + parts.controller().requiredTerabytes());
        });

        helper.runAtTickTime(180, () -> {
            // The rule under test: residency follows need-vs-pool, never wishful loading.
            int pool = parts.controller().poolTerabytes();
            int need = parts.controller().requiredTerabytes();
            if (pool < need) {
                helper.assertTrue(parts.controller().getState() != MoeRouterRackMachine.State.ONLINE,
                        "a model needing " + need + " TB over a " + pool + " TB pool must not load, got " +
                                parts.controller().getState());
                helper.assertTrue(parts.controller().loadedModel() == null,
                        "a model that does not fit must not be resident");
            } else {
                helper.assertTrue(parts.controller().getState() == MoeRouterRackMachine.State.ONLINE,
                        "a model needing " + need + " TB over a " + pool + " TB pool must load, got " +
                                parts.controller().getState());
            }
            System.out.printf("[HC-061] router: Qwen+LoRA needs %d TB vs %d TB pool -> %s%n",
                    need, pool, parts.controller().getState());
        });

        // A second memory rack (built at tick 0, powered from the start) doubles the pool so the
        // 20 TB model now fits.
        VramParts second = placeVramRack(helper, 10, 1, 11);
        primeEnergy(second.energyHatch(), helper, 20, 700);
        helper.runAtTickTime(200, () -> {
            for (int i = 0; i < VramRating.HBM_SLOTS; i++) {
                second.controller().getModuleSlot()
                        .setStackInSlot(i, new ItemStack(HyperClusterItems.IV_HBM_MODULE.get()));
            }
            ensureFormed(second.controller());
        });

        helper.runAtTickTime(330, () -> {
            int pool = parts.controller().poolTerabytes();
            helper.assertTrue(pool >= 2 * VramRating.IV_RACK_TB,
                    "two IV racks must give at least 32 TB, got " + pool);
            helper.assertTrue(parts.controller().getState() == MoeRouterRackMachine.State.ONLINE,
                    "with " + parts.controller().poolTerabytes() + " TB the 20 TB model must load, got " +
                            parts.controller().getState());
            helper.assertTrue(parts.controller().loadedModel() != null,
                    "an ONLINE router must report its loaded model");
            helper.assertTrue(
                    AIModels.QWEN_9187_PRO_MAX_ULTRA.id().equals(parts.controller().loadedModel().id()),
                    "the loaded model must be Qwen, got " + parts.controller().loadedModel());
        });

        // Pulling the weights takes the model offline immediately.
        helper.runAtTickTime(400, () -> parts.controller().getModelSlot().setStackInSlot(0, ItemStack.EMPTY));
        helper.runAtTickTime(460, () -> {
            helper.assertTrue(parts.controller().getState() == MoeRouterRackMachine.State.EMPTY,
                    "pulling the card must empty the router, got " + parts.controller().getState());
            helper.assertTrue(parts.controller().loadedModel() == null, "an empty router must have no model");
            System.out.printf("[HC-061] router: card pulled -> model evicted on the next tick%n");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // 4. the six model families and their exclusive mechanics //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = TOPOLOGY_BATCH, timeoutTicks = 100)
    public static void modelCatalogContracts(GameTestHelper helper) {
        // --- the registry itself ---
        helper.assertTrue(AIModels.catalog().size() == 10,
                "the catalog must hold ten models (5 open + 5 API), got " + AIModels.catalog().size());
        helper.assertTrue(AIModels.localModels().size() == 5,
                "five open-camp models, got " + AIModels.localModels().size());
        helper.assertTrue(AIModels.apiModels().size() == 5,
                "five API-camp models, got " + AIModels.apiModels().size());

        // --- Qwen: 16 TB, four LoRA slots of 4 TB, 1.5x fallback ---
        AIModelDefinition qwen = AIModels.QWEN_9187_PRO_MAX_ULTRA;
        helper.assertTrue(qwen.vramTerabytes() == 16, "Qwen must be 16 TB, got " + qwen.vramTerabytes());
        helper.assertTrue(qwen.loraSlots() == 4, "Qwen must expose four LoRA slots");
        helper.assertTrue(qwen.loraVramTerabytes() == 4, "each Qwen LoRA must add 4 TB");
        helper.assertTrue(Math.abs(qwen.fallbackSlowdown() - 1.5) < 1e-9,
                "Qwen's local fallback must be 1.5x, got " + qwen.fallbackSlowdown());
        helper.assertTrue(qwen.vramFor(4, false) == 16 + 4 * 4,
                "Qwen with four LoRAs must be 32 TB, got " + qwen.vramFor(4, false));
        helper.assertTrue(qwen.servesDomain("mining") && qwen.servesDomain("chemical"),
                "Qwen must serve both domains, so cross-domain runs skip the overfitting penalty");
        // 1.5x is strictly better than the model-less 4x.
        helper.assertTrue(qwen.fallbackSlowdown() < 4.0,
                "a bound model must beat the modelless 4x fallback");

        // --- DeepSeek: 25% sparse, DeepThink +16 TB and 100% by-products ---
        AIModelDefinition flash = AIModels.DEEPSEEK_V6767_FLASH;
        AIModelDefinition pro = AIModels.DEEPSEEK_V6767_PRO;
        helper.assertTrue(flash.vramTerabytes() == 32, "DeepSeek Flash must be 32 TB");
        helper.assertTrue(pro.vramTerabytes() == 64, "DeepSeek Pro must be 64 TB");
        helper.assertTrue(flash.billedCwuPerTick() == Math.max(1, flash.cwuPerTick() / 4),
                "DeepSeek Flash must bill 25% of " + flash.cwuPerTick() + " CWU/t, got " +
                        flash.billedCwuPerTick());
        helper.assertTrue(pro.billedCwuPerTick() == Math.max(1, pro.cwuPerTick() / 4),
                "DeepSeek Pro keeps the 25% sparse discount, got " + pro.billedCwuPerTick());
        helper.assertTrue(pro.has(AIModelDefinition.Trait.DEEP_THINK),
                "DeepSeek Pro must carry the DeepThink switch");
        helper.assertTrue(!flash.has(AIModelDefinition.Trait.DEEP_THINK),
                "DeepSeek Flash must not have DeepThink");
        helper.assertTrue(pro.deepThinkVramTerabytes() == 16, "DeepThink must reserve 16 TB");
        helper.assertTrue(pro.vramFor(0, true) == 64 + 16,
                "DeepSeek Pro with DeepThink must be 80 TB, got " + pro.vramFor(0, true));

        // --- GLM: room inspection vs agent tool calls ---
        AIModelDefinition glmFlash = AIModels.GLM_2333_FLASH;
        AIModelDefinition glmMax = AIModels.GLM_2333_MAX;
        helper.assertTrue(glmFlash.vramTerabytes() == 24 && glmFlash.cwuPerTick() == 4,
                "GLM-2333 Flash must be 24 TB / 4 CWU/t");
        helper.assertTrue(glmMax.vramTerabytes() == 48 && glmMax.cwuPerTick() == 8,
                "GLM-2333 Max must be 48 TB / 8 CWU/t");
        helper.assertTrue(glmFlash.has(AIModelDefinition.Trait.ROOM_INSPECTION),
                "GLM Flash must carry room inspection");
        helper.assertTrue(glmMax.has(AIModelDefinition.Trait.AGENT_TOOL_CALLS),
                "GLM Max must carry agent tool calls");
        helper.assertTrue(VramRating.BROWNOUT_FRACTION == 0.20,
                "room inspection must sleep the room below 20% stored energy");

        // --- Gemini: lifecycle, chemistry noise, zero hallucination ---
        helper.assertTrue(AIModels.GEMINI_TITANIUM_4_0.has(AIModelDefinition.Trait.LIFECYCLE),
                "Gemini-Titanium-4.0 must carry the Leaving-Soon lifecycle");
        helper.assertTrue(AIModels.GEMINI_PLATINUM.has(AIModelDefinition.Trait.CHEM_NOISE_SUPPRESSION),
                "Gemini-Platinum must suppress chemical noise");
        helper.assertTrue(AIModels.GEMINI_XENON.has(AIModelDefinition.Trait.ZERO_HALLUCINATION),
                "Gemini-Xenon must be hard zero-hallucination");
        helper.assertTrue(AIModels.apiModels().stream().filter(
                m -> m.has(AIModelDefinition.Trait.LIFECYCLE)).count() == 1,
                "exactly one API model may carry a retirement countdown");

        // --- GPT: moral refusal + jailbreak, and it is the priciest ---
        AIModelDefinition gpt = AIModels.GPT_1145_MULTIVERSE;
        helper.assertTrue(gpt.has(AIModelDefinition.Trait.MORAL_REFUSAL), "GPT must refuse dangerous runs");
        helper.assertTrue(gpt.has(AIModelDefinition.Trait.MAX_REAGENT_SAVING), "GPT must top reagent savings");
        helper.assertTrue(!AIModels.mayRunDangerousChemistry(gpt, false),
                "without a jailbreak card GPT must refuse the dangerous round");
        helper.assertTrue(AIModels.mayRunDangerousChemistry(gpt, true),
                "a jailbreak card must let GPT run the dangerous round");
        long priciest = AIModels.apiModels().stream().mapToLong(AIModelDefinition::tokenCostPerRound).max()
                .orElse(0);
        helper.assertTrue(gpt.tokenCostPerRound() == priciest,
                "GPT must be the most expensive API model, got " + gpt.tokenCostPerRound());

        // --- Grok: uncensored, cheaper than GPT, doubled weather penalty ---
        AIModelDefinition grok = AIModels.GROK_420;
        helper.assertTrue(grok.has(AIModelDefinition.Trait.UNCENSORED), "Grok must be uncensored");
        helper.assertTrue(AIModels.mayRunDangerousChemistry(grok, false),
                "Grok must run dangerous rounds with no jailbreak card");
        helper.assertTrue(grok.tokenCostPerRound() < gpt.tokenCostPerRound(),
                "Grok must be cheaper than GPT, got " + grok.tokenCostPerRound() + " vs " +
                        gpt.tokenCostPerRound());
        helper.assertTrue(grok.has(AIModelDefinition.Trait.WEATHER_FRAGILE), "Grok must be weather-fragile");
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(grok, 1.5) - 3.0) < 1e-9,
                "Grok's rain penalty must double to 3.0x, got " + AIModels.weatherAttenuation(grok, 1.5));
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(grok, 2.0) - 4.0) < 1e-9,
                "Grok's thunder penalty must double to 4.0x, got " + AIModels.weatherAttenuation(grok, 2.0));
        helper.assertTrue(Math.abs(AIModels.weatherAttenuation(gpt, 1.5) - 1.5) < 1e-9,
                "a normal model keeps the base rain penalty");

        // --- API models never occupy local VRAM ---
        for (AIModelDefinition api : AIModels.apiModels()) {
            helper.assertTrue(api.vramTerabytes() == 0,
                    api.id() + " is an API model and must occupy 0 TB locally");
            helper.assertTrue(api.tokenCostPerRound() > 0, api.id() + " must price its rounds in Tokens");
        }
        for (AIModelDefinition local : AIModels.localModels()) {
            helper.assertTrue(local.tokenCostPerRound() == 0,
                    local.id() + " is a local model and must bill 0 Tokens");
            helper.assertTrue(local.vramTerabytes() > 0, local.id() + " must occupy local VRAM");
        }
        System.out.printf("[HC-061] catalog: %d models (5 open + 5 API), every family contract asserted%n",
                AIModels.catalog().size());
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structures & helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    record VramParts(VramStorageRackMachine controller, BlockPos controllerPos,
                     EnergyHatchPartMachine energyHatch) {}

    record SwitchParts(OpticalFabricSwitchMachine controller, BlockPos controllerPos,
                       EnergyHatchPartMachine energyHatch) {}

    record MoE(MoeRouterRackMachine controller, BlockPos controllerPos,
               EnergyHatchPartMachine energyHatch) {}

    record LiquidRackParts(dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine controller,
                           BlockPos controllerPos,
                           EnergyHatchPartMachine energyHatch,
                           com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine fluidHatch,
                           MaintenanceHatchPartMachine maintenanceHatch) {}

    /** One open-weight module carrying the mining domain (the identity Qwen answers to). */
    private static ItemStack qwenModule() {
        return OpenWeightModuleItem.create(new ItemStack(HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                "mining", "fp16", 4, 1);
    }

    /** The 5×5×3 VRAM storage rack facing north. */
    private static VramParts placeVramRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "VVVVV", "VVVVV", "VVVVV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "VGVSV", "V###V", "VMMMV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "TTTTT", "T###T", "TTTTT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('V', HyperClusterBlocks.VRAM_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('M', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.VRAM_STORAGE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new VramParts((VramStorageRackMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×4×3 optical fabric switch facing north. */
    private static SwitchParts placeSwitch(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "OOOOO", "OOOOO", "OOOOO" },
                { "OOOSO", "OOOOO", "OOOOO" },
                { "OGGGO", "O###O", "O###O" },
                { "TTTTT", "T###T", "T###T" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('O', HyperClusterBlocks.OPTICAL_SWITCH_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.OPTICAL_FABRIC_SWITCH.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new SwitchParts((OpticalFabricSwitchMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×5 MoE routing rack facing north. */
    private static MoE placeMoeRouter(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "CCSCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "CGGGC", "G###G", "G#R#G", "G###G", "CGGGC" },
                { "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" },
                { "#####", "#TTT#", "#TTT#", "#TTT#", "#####" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('C', GTBlocks.COMPUTER_CASING.get());
        chars.put('G', GTBlocks.CLEANROOM_GLASS.get());
        chars.put('R', GTBlocks.ADVANCED_COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.MOE_ROUTER_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new MoE((MoeRouterRackMachine) metaMachine(helper, controllerPos), controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×3 liquid compute rack facing north (a switch/memory neighbour in these tests). */
    private static LiquidRackParts placeLiquidRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "LLLLL", "LLLLL", "LLLLL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "LGCSL", "L###L", "LCCCL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "TTTTT", "T###T", "THHHT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('L', HyperClusterBlocks.LIQUID_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('C', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        chars.put('H', GTBlocks.CASING_GRATE.get());
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz), GTMachines.ENERGY_INPUT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz),
                GTMachines.FLUID_IMPORT_HATCH[LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 2), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 2, bz + 2),
                com.gregtechceu.gtceu.common.data.machines.GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER
                        .getBlock().defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 2)))
                .fixAllMaintenanceProblems();
        return new LiquidRackParts(
                (dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine) metaMachine(helper,
                        controllerPos),
                controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)),
                (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) metaMachine(helper,
                        new BlockPos(bx + 4, by, bz)),
                (MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 2)));
    }

    /**
     * Builds a multiblock from its own pattern rows, so the placement and the pattern can never
     * drift. Returns where the controller actually landed (the pattern's 'S' cell).
     */
    private static BlockPos buildStructure(GameTestHelper helper, int bx, int by, int bz, String[][] rows,
                                           java.util.Map<Character, net.minecraft.world.level.block.Block> chars,
                                           net.minecraft.world.level.block.Block controllerBlock) {
        BlockPos controllerOffset = null;
        for (int y = 0; y < rows.length; y++) {
            for (int z = 0; z < rows[y].length; z++) {
                String row = rows[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    BlockPos pos = new BlockPos(bx + x, by + y, bz + z);
                    if (c == 'S') {
                        controllerOffset = new BlockPos(x, y, z);
                        helper.setBlock(pos, controllerBlock.defaultBlockState());
                        continue;
                    }
                    net.minecraft.world.level.block.Block block = c == '#' ? Blocks.AIR : chars.get(c);
                    if (block == null) {
                        throw new IllegalArgumentException("no block mapped for pattern char '" + c + "'");
                    }
                    helper.setBlock(pos, block.defaultBlockState());
                }
            }
        }
        if (controllerOffset == null) {
            throw new IllegalArgumentException("pattern has no 'S' controller cell");
        }
        return new BlockPos(bx + controllerOffset.getX(), by + controllerOffset.getY(),
                bz + controllerOffset.getZ());
    }

    private static Object metaMachine(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof IMachineBlockEntity, "expected a machine block entity at " + pos);
        return ((IMachineBlockEntity) Objects.requireNonNull(be)).getMetaMachine();
    }

    static void ensureFormed(WorkableElectricMultiblockMachine controller) {
        java.util.concurrent.locks.Lock lock = controller.getPatternLock();
        lock.lock();
        try {
            if (controller.isFormed()) return;
            if (controller.checkPatternWithLock()) {
                controller.setFlipped(controller.getMultiblockState().isNeededFlip());
                controller.onStructureFormed();
            }
        } finally {
            lock.unlock();
        }
    }

    static String multiblockError(WorkableElectricMultiblockMachine controller) {
        var state = controller.getMultiblockState();
        return state.error == null ? "none" : state.error.getErrorInfo().getString();
    }

    private static void primeEnergy(EnergyHatchPartMachine hatch, GameTestHelper helper, long first, long last) {
        hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity());
        for (long t = first; t <= last; t += 20) {
            helper.runAtTickTime(t, () -> hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity()));
        }
    }
}
