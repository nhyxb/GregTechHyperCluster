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

import java.util.Objects;

/**
 * 0.6.2 acceptance gametests: the three runtime gaps 0.6.1 left between the manual and the game.
 *
 * <p>
 * <b>Block 1 — weight-module identity NBT.</b> {@code hc_model_id} round-trips, an old module
 * without it falls back to Qwen, all five open families train through the cluster's own selector
 * with their own gates, and each deploys independently into the MoE router.
 *
 * <p>
 * <b>Block 2 — open-weight cloning.</b> One module + one blank carrier in the micro burner yields
 * a duplicate carrying the <b>whole</b> record, and the original stays in the machine.
 *
 * <p>
 * <b>Block 3 — the six families in the runtime settlement path.</b> GPT's moral refusal and the
 * jailbreak unlock, Grok's doubled weather penalty, Gemini's lifecycle / noise suppression /
 * zero hallucination, Qwen's ×1.5 fallback, DeepSeek's sparse billing and DeepThink's forced
 * by-products, Qwen's LoRA projection and GLM's sleep / agent duty cycle.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(HyperCluster.MOD_ID)
public class ModelRuntimeTests {

    public static final String RUNTIME_BATCH = "hypercluster_062_runtime";

    /** A freshly trained module of one open family in one domain. */
    private static ItemStack module(String modelId, String domain) {
        return dev.hypercluster.common.item.OpenWeightModuleItem.create(
                new ItemStack(dev.hypercluster.common.item.HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                modelId, domain, dev.hypercluster.common.machine.training.TrainingRating.PRECISION, 4, 1);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 1 · the weight-module identity NBT //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = RUNTIME_BATCH + "_01_identity", timeoutTicks = 100)
    public static void moduleIdentityNbtRoundTrips(GameTestHelper helper) {
        // Every open family is mintable and reads back its own identity.
        for (var model : dev.hypercluster.common.model.AIModels.openModels()) {
            ItemStack module = module(model.id().getPath(), "mining");
            helper.assertTrue(model.id().getPath().equals(
                    dev.hypercluster.common.item.OpenWeightModuleItem.modelId(module)),
                    "module identity must read back as " + model.id().getPath() + ", got " +
                            dev.hypercluster.common.item.OpenWeightModuleItem.modelId(module));
            helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.hasExplicitModelId(module),
                    "a freshly trained module must carry an explicit hc_model_id");
            helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.definition(module) == model,
                    "the module must resolve to the exact definition it was minted from");
        }

        // Legacy compatibility: a module written before 0.6.2 has no hc_model_id at all.
        ItemStack legacy = new ItemStack(dev.hypercluster.common.item.HyperClusterItems.OPEN_WEIGHT_MODULE.get());
        legacy.getOrCreateTag().putString(
                dev.hypercluster.common.item.OpenWeightModuleItem.DOMAIN_TAG, "chemical");
        legacy.getOrCreateTag().putInt(
                dev.hypercluster.common.item.OpenWeightModuleItem.VERSION_TAG, 2);
        helper.assertTrue(!dev.hypercluster.common.item.OpenWeightModuleItem.hasExplicitModelId(legacy),
                "an older module must not claim an explicit identity");
        helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.LEGACY_MODEL_ID.equals(
                dev.hypercluster.common.item.OpenWeightModuleItem.modelId(legacy)),
                "an untagged module must fall back to Qwen, got " +
                        dev.hypercluster.common.item.OpenWeightModuleItem.modelId(legacy));

        // An API model name can never be smuggled into a deployable module.
        ItemStack smuggled = module("gpt_1145_multiverse", "mining");
        helper.assertTrue(
                dev.hypercluster.common.item.OpenWeightModuleItem.LEGACY_MODEL_ID
                        .equals(dev.hypercluster.common.item.OpenWeightModuleItem.modelId(smuggled)),
                "an API-camp id must degrade to the legacy identity, got " +
                        dev.hypercluster.common.item.OpenWeightModuleItem.modelId(smuggled));
        helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.isDeployable(legacy),
                "a legacy module must stay deployable");

        System.out.printf("[HC-062] identity: 5 open families round-trip hc_model_id, legacy reads as %s%n",
                dev.hypercluster.common.item.OpenWeightModuleItem.LEGACY_MODEL_ID);
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 2 · open-weight cloning preserves the whole record //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = RUNTIME_BATCH + "_02_clone", timeoutTicks = 100)
    public static void clonePreservesEveryTag(GameTestHelper helper) {
        ItemStack source = module("deepseek_v6767_pro", "chemical");
        source.getOrCreateTag().putInt(dev.hypercluster.common.item.OpenWeightModuleItem.RUNS_TAG, 3);
        ItemStack clone = dev.hypercluster.common.item.OpenWeightModuleItem.copyWithRecord(source,
                new ItemStack(dev.hypercluster.common.item.HyperClusterItems.OPEN_WEIGHT_MODULE.get()));
        helper.assertTrue(clone.is(dev.hypercluster.common.item.HyperClusterItems.OPEN_WEIGHT_MODULE.get()),
                "the clone must be a module");
        helper.assertTrue(clone.getCount() == 1, "the clone must be a single module");
        for (String key : new String[] {
                dev.hypercluster.common.item.OpenWeightModuleItem.MODEL_TAG,
                dev.hypercluster.common.item.OpenWeightModuleItem.DOMAIN_TAG,
                dev.hypercluster.common.item.OpenWeightModuleItem.PRECISION_TAG,
                dev.hypercluster.common.item.OpenWeightModuleItem.VERSION_TAG,
                dev.hypercluster.common.item.OpenWeightModuleItem.RUNS_TAG }) {
            helper.assertTrue(Objects.equals(source.getTag().get(key), clone.getTag().get(key)),
                    "tag " + key + " must be copied verbatim, source=" + source.getTag().get(key) +
                            " clone=" + clone.getTag().get(key));
        }
        helper.assertTrue("deepseek_v6767_pro".equals(
                dev.hypercluster.common.item.OpenWeightModuleItem.modelId(clone)),
                "the clone must name the same family");
        helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.runs(clone) == 3,
                "the clone must keep the lineage, got " +
                        dev.hypercluster.common.item.OpenWeightModuleItem.runs(clone));
        System.out.printf("[HC-062] clone: whole record copied (model=%s, domain=%s, version=%d, runs=%d)%n",
                dev.hypercluster.common.item.OpenWeightModuleItem.modelId(clone),
                dev.hypercluster.common.item.OpenWeightModuleItem.domain(clone),
                dev.hypercluster.common.item.OpenWeightModuleItem.version(clone),
                dev.hypercluster.common.item.OpenWeightModuleItem.runs(clone));
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 3 · the pure runtime contract of the six families //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_floor", batch = RUNTIME_BATCH + "_03_contract", timeoutTicks = 100)
    public static void familyRuntimeContract(GameTestHelper helper) {
        var runtime = dev.hypercluster.common.model.ModelRuntime.class;
        var models = dev.hypercluster.common.model.AIModels.class;

        // --- GPT-1145: refuses dangerous rounds until a jailbreak card is inserted ---
        var gpt = dev.hypercluster.common.model.AIModels.GPT_1145_MULTIVERSE;
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.permitsDangerousRound(gpt, false),
                "GPT must refuse a dangerous round without a jailbreak card");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.permitsDangerousRound(gpt, true),
                "a jailbreak card must let GPT run the dangerous round");

        // --- Grok-420: uncensored, but its weather penalty is doubled ---
        var grok = dev.hypercluster.common.model.AIModels.GROK_420;
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.permitsDangerousRound(grok, false),
                "Grok must run a dangerous round with no jailbreak at all");
        helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.effectiveWeather(grok, 1.5) -
                3.0) < 1e-9, "Grok's rain penalty must double to 3.0x");
        helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.effectiveWeather(grok, 2.0) -
                4.0) < 1e-9, "Grok's thunder penalty must double to 4.0x");
        helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.effectiveWeather(gpt, 1.5) -
                1.5) < 1e-9, "a normal family keeps the plain rain penalty");

        // --- Gemini: lifecycle, chemical-only noise suppression, zero hallucination / maintenance ---
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.yieldBonus(
                dev.hypercluster.common.model.AIModels.GEMINI_PLATINUM, true) ==
                dev.hypercluster.common.model.ModelRuntime.PLATINUM_YIELD_BONUS,
                "Platinum must bonus a chemical round");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.yieldBonus(
                dev.hypercluster.common.model.AIModels.GEMINI_PLATINUM, false) == 0,
                "Platinum's noise suppression must not fire on a mining round");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.yieldBonus(
                dev.hypercluster.common.model.AIModels.GEMINI_XENON, false) ==
                dev.hypercluster.common.model.ModelRuntime.XENON_YIELD_BONUS,
                "Xenon must bonus every round");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.waivesMaintenance(
                dev.hypercluster.common.model.AIModels.GEMINI_XENON),
                "Xenon must waive maintenance");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.waivesMaintenance(
                dev.hypercluster.common.model.AIModels.GLM_2333_FLASH),
                "GLM-2333 Flash's room inspection must waive maintenance");
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.waivesMaintenance(
                dev.hypercluster.common.model.AIModels.GEMINI_PLATINUM),
                "Platinum must not waive maintenance");

        // --- Qwen: the fallback speed is a live machine-room fact ---
        helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.fallbackSlowdown(true) - 1.5) <
                1e-9, "Qwen online must make the fallback x1.5");
        helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.fallbackSlowdown(false) - 4.0) <
                1e-9, "no Qwen must keep the model-less x4");
        // 1.5x is strictly better, and the fallback duration really follows.
        helper.assertTrue(dev.hypercluster.common.cloud.CloudPricing.taskTicks(
                dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                dev.hypercluster.common.cloud.CloudPricing.InferenceTier.MEDIUM, false, 1.0, false,
                dev.hypercluster.common.model.ModelRuntime.fallbackSlowdown(true)) == 150,
                "a Qwen-backed fallback round must be 150 ticks, got " +
                        dev.hypercluster.common.cloud.CloudPricing.taskTicks(
                                dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                                dev.hypercluster.common.cloud.CloudPricing.InferenceTier.MEDIUM, false, 1.0,
                                false, dev.hypercluster.common.model.ModelRuntime.fallbackSlowdown(true)));
        helper.assertTrue(dev.hypercluster.common.cloud.CloudPricing.taskTicks(
                dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                dev.hypercluster.common.cloud.CloudPricing.InferenceTier.MEDIUM, false, 1.0, false,
                dev.hypercluster.common.model.ModelRuntime.fallbackSlowdown(false)) == 400,
                "a model-less fallback round must stay 400 ticks");

        // --- DeepSeek: 25% sparse billing and DeepThink's forced by-products ---
        var flash = dev.hypercluster.common.model.AIModels.DEEPSEEK_V6767_FLASH;
        var pro = dev.hypercluster.common.model.AIModels.DEEPSEEK_V6767_PRO;
        helper.assertTrue(flash.billedCwuPerTick() * 4 == flash.cwuPerTick() ||
                flash.billedCwuPerTick() == Math.max(1, flash.cwuPerTick() / 4),
                "Flash must bill 25% of " + flash.cwuPerTick() + ", got " + flash.billedCwuPerTick());
        helper.assertTrue(pro.billedCwuPerTick() == Math.max(1, pro.cwuPerTick() / 4),
                "Pro keeps the same 25% sparse discount, got " + pro.billedCwuPerTick());
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.forcesByproducts(flash, true),
                "Flash has no DeepThink switch, so nothing is forced");
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.forcesByproducts(pro, false),
                "DeepThink must be charged before Pro forces anything");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.forcesByproducts(pro, true),
                "Pro with DeepThink charged must force by-products to 100%");
        helper.assertTrue(pro.deepThinkVramTerabytes() == 16,
                "DeepThink must reserve the documented +16 TB");

        // --- GLM: brownout sleep and agent tool calls ---
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.isBrownout(19, 100),
                "19% stored energy must count as a brownout");
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.isBrownout(20, 100),
                "20% is the boundary and must not sleep the room");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.sleepsOnBrownout(
                dev.hypercluster.common.model.AIModels.GLM_2333_FLASH),
                "GLM-2333 Flash must sleep through a brownout");
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.sleepsOnBrownout(
                dev.hypercluster.common.model.AIModels.GLM_2333_MAX),
                "GLM-2333 Max must not sleep through a brownout");
        helper.assertTrue(dev.hypercluster.common.model.ModelRuntime.drivesAgentToolCalls(
                dev.hypercluster.common.model.AIModels.GLM_2333_MAX),
                "GLM-2333 Max must drive agent tool calls");
        helper.assertTrue(!dev.hypercluster.common.model.ModelRuntime.drivesAgentToolCalls(
                dev.hypercluster.common.model.AIModels.GLM_2333_FLASH),
                "GLM-2333 Flash must not drive agent tool calls");

        // --- the per-family training gates really differ ---
        java.util.Set<Integer> gates = new java.util.LinkedHashSet<>();
        for (var model : dev.hypercluster.common.model.AIModels.openModels()) {
            helper.assertTrue(model.trainingVramTerabytes() > 0,
                    model.id() + " must declare a positive training VRAM gate");
            helper.assertTrue(model.trainingCwuPerTick() > 0,
                    model.id() + " must declare a positive training CWU/t gate");
            gates.add(model.trainingVramTerabytes());
        }
        helper.assertTrue(gates.size() == dev.hypercluster.common.model.AIModels.openModels().size(),
                "every open family must have its own distinct VRAM gate, got " + gates);
        System.out.printf("[HC-062] contract: 6 families, 5 distinct training gates %s%n", gates);
        helper.succeed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 2 (runtime) · the micro burner really clones and keeps the original //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_04_burner", timeoutTicks = 500)
    public static void microBurnerClonesAndKeepsOriginal(GameTestHelper helper) {
        // The burner is an HV simple machine: place it, feed it HV power and one module + carrier.
        BlockPos burnerPos = new BlockPos(1, 1, 1);
        helper.setBlock(burnerPos,
                dev.hypercluster.common.data.HyperClusterMachines.HV_MICRO_BURNER.getBlock().defaultBlockState());
        var burner = (dev.hypercluster.common.machine.MicroBurnerMachine) metaMachine(helper, burnerPos);
        var energy = burner.energyContainer;
        for (long t = 0; t <= 480; t += 20) {
            helper.runAtTickTime(t, () -> energy.addEnergy(energy.getEnergyCapacity()));
        }

        final String sourceModel = "deepseek_v6767_pro";
        helper.runAtTickTime(20, () -> {
            var inputs = burner.importItems;
            inputs.setStackInSlot(0, module(sourceModel, "chemical"));
            inputs.setStackInSlot(1,
                    new ItemStack(dev.hypercluster.common.item.HyperClusterItems.BLANK_CARRIER.get()));
        });

        // Early probe: is the clone recipe even matched? (a missing match means the NBT stamp
        // never ran, which is a different bug from a wrong stamp).
        helper.runAtTickTime(60, () -> {
            System.out.printf("[HC-062] burner diag: source=%s inputs=%s recipe=%s status=%s%n",
                    burner.sourceModule() == null ? "none" :
                            dev.hypercluster.common.item.OpenWeightModuleItem.modelId(burner.sourceModule()),
                    describeInputs(burner),
                    burner.getRecipeLogic().getLastRecipe() == null ? "none" :
                            burner.getRecipeLogic().getLastRecipe().id,
                    burner.getRecipeLogic().getStatus());
        });
        helper.runAtTickTime(300, () -> {
            System.out.printf("[HC-062] burner diag@300: recipe=%s status=%s outputs=%s%n",
                    burner.getRecipeLogic().getLastRecipe() == null ? "none" :
                            burner.getRecipeLogic().getLastRecipe().id,
                    burner.getRecipeLogic().getStatus(), describeExports(burner));
        });

        // The clone recipe is 200 ticks at 480 EU/t, so it settles well before tick 380.
        helper.runAtTickTime(380, () -> {
            // The original must still be in the input slot: the recipe declares it not-consumed.
            ItemStack source = burner.sourceModule();
            helper.assertTrue(source != null,
                    "the original module must survive the clone (notConsumable)");
            if (source == null) {
                return;
            }
            helper.assertTrue(sourceModel.equals(
                    dev.hypercluster.common.item.OpenWeightModuleItem.modelId(source)),
                    "the surviving original must keep its identity, got " +
                            dev.hypercluster.common.item.OpenWeightModuleItem.modelId(source));
            // And the output slot must hold a duplicate carrying the whole record.
            ItemStack produced = null;
            for (int slot = 0; slot < burner.exportItems.getSlots(); slot++) {
                ItemStack stack = burner.exportItems.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(
                        dev.hypercluster.common.item.HyperClusterItems.OPEN_WEIGHT_MODULE.get())) {
                    produced = stack;
                    break;
                }
            }
            helper.assertTrue(produced != null,
                    "the burner must have produced a cloned module, out=" + describeExports(burner));
            if (produced == null) {
                return;
            }
            helper.assertTrue(sourceModel.equals(
                    dev.hypercluster.common.item.OpenWeightModuleItem.modelId(produced)),
                    "the clone must carry hc_model_id, got " +
                            dev.hypercluster.common.item.OpenWeightModuleItem.modelId(produced));
            helper.assertTrue("chemical".equals(
                    dev.hypercluster.common.item.OpenWeightModuleItem.domain(produced)),
                    "the clone must carry hc_domain");
            helper.assertTrue(dev.hypercluster.common.machine.training.TrainingRating.PRECISION.equals(
                    dev.hypercluster.common.item.OpenWeightModuleItem.precision(produced)),
                    "the clone must carry hc_precision");
            helper.assertTrue(dev.hypercluster.common.item.OpenWeightModuleItem.version(produced) == 4,
                    "the clone must carry hc_version, got " +
                            dev.hypercluster.common.item.OpenWeightModuleItem.version(produced));
            System.out.printf("[HC-062] burner: cloned %s keeping the original (out=%s, runs=%d)%n",
                    sourceModel,
                    dev.hypercluster.common.item.OpenWeightModuleItem.modelId(produced),
                    dev.hypercluster.common.item.OpenWeightModuleItem.runs(produced));
            helper.succeed();
        });
    }

    /** Diagnostic: what the burner's input slots actually hold. */
    private static String describeInputs(dev.hypercluster.common.machine.MicroBurnerMachine burner) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < burner.importItems.getSlots(); slot++) {
            sb.append(slot).append('=').append(burner.importItems.getStackInSlot(slot)).append(' ');
        }
        return sb.toString();
    }

    /** Diagnostic: what the burner's output slots actually hold. */
    private static String describeExports(dev.hypercluster.common.machine.MicroBurnerMachine burner) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < burner.exportItems.getSlots(); slot++) {
            sb.append(slot).append('=').append(burner.exportItems.getStackInSlot(slot)).append(' ');
        }
        return sb.toString();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 1 + 3 (runtime) · five families deploy independently into the router //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_05_router", timeoutTicks = 900)
    public static void everyOpenFamilyDeploysIndependently(GameTestHelper helper) {
        RouterParts router = placeRouter(helper, 1, 1, 1);
        primeEnergy(router.energyHatch(), helper, 20, 880);
        // Enough memory for the heaviest family (DeepSeek-Pro: 64 TB) and enough compute.
        MemoryParts memory = placeMemoryRack(helper, 10, 1, 1);
        primeEnergy(memory.energyHatch(), helper, 20, 880);
        ComputeParts compute = placeLiquidRack(helper, 10, 1, 7);
        primeEnergy(compute.energyHatch(), helper, 20, 880);

        helper.runAtTickTime(60, () -> {
            ensureFormed(router.controller());
            ensureFormed(memory.controller());
            ensureFormed(compute.controller());
            for (int i = 0; i < 4; i++) {
                memory.controller().getModuleSlot().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_HBM_MODULE.get()));
                compute.controller().getBladeInventory().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            memory.controller().getModuleSlot().setStackInSlot(0,
                    new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_HBM_MODULE.get()));
            compute.fluidHatch().tank.setFluidInTank(0,
                    GTMaterials.DistilledWater.getFluid(64000));
        });
        for (long t = 200; t <= 860; t += 100) {
            helper.runAtTickTime(t, () -> compute.fluidHatch().tank.setFluidInTank(0,
                    GTMaterials.DistilledWater.getFluid(64000)));
        }

        // Walk all five open families through the SAME deployment slot: each must resolve to
        // itself, not to the first domain-matching model (the 0.6.1 bug).
        var open = dev.hypercluster.common.model.AIModels.openModels();
        for (int i = 0; i < open.size(); i++) {
            int index = i;
            long inspectTick = 220 + i * 120L;
            long advanceTick = inspectTick + 60;
            helper.runAtTickTime(inspectTick, () -> {
                router.controller().getModelSlot().setStackInSlot(0,
                        module(open.get(index).id().getPath(), "mining"));
            });
            helper.runAtTickTime(advanceTick, () -> {
                var expected = open.get(index);
                var resolved = router.controller().modelInSlot();
                helper.assertTrue(resolved != null,
                        "module " + expected.id() + " must resolve to a model");
                if (resolved == null) {
                    return;
                }
                helper.assertTrue(expected.id().equals(resolved.id()),
                        "module " + expected.id() + " must resolve to itself, got " + resolved.id());
                helper.assertTrue(
                        router.controller().requiredTerabytes() ==
                                expected.vramFor(0, false),
                        expected.id() + " must ask for its own " + expected.vramFor(0, false) +
                                " TB, got " + router.controller().requiredTerabytes());
                System.out.printf("[HC-062] router: %s resolved (%d TB, %d CWU/t), state=%s%n",
                        resolved.id().getPath(), router.controller().requiredTerabytes(),
                        resolved.billedCwuPerTick(), router.controller().getState());
            });
        }

        helper.runAtTickTime(880, () -> {
            // Every family resolved on its own; the router is a real, formed controller.
            helper.assertTrue(router.controller().isFormed(),
                    "the router must be formed (err=" + multiblockError(router.controller()) + ")");
            helper.assertTrue(dev.hypercluster.common.model.AIModels.openModels().size() == 5,
                    "all five open families must be deployable");
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 3 · DeepSeek sparse billing and DeepThink's forced by-products, live //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_06_deepseek", timeoutTicks = 900)
    public static void deepSeekDrawsSparseCompute(GameTestHelper helper) {
        RouterParts router = placeRouter(helper, 1, 1, 1);
        primeEnergy(router.energyHatch(), helper, 20, 880);
        // DeepSeek-V6767 Pro declares a 128 TB training/deployment gate, i.e. two full LuV
        // memory racks. Both are placed up front so the Pro half of this test is not OOM.
        MemoryParts memory = placeMemoryRack(helper, 10, 1, 1);
        primeEnergy(memory.energyHatch(), helper, 20, 880);
        MemoryParts memory2 = placeMemoryRack(helper, 1, 1, 8);
        primeEnergy(memory2.energyHatch(), helper, 20, 880);
        ComputeParts compute = placeLiquidRack(helper, 10, 1, 7);
        primeEnergy(compute.energyHatch(), helper, 20, 880);

        helper.runAtTickTime(60, () -> {
            ensureFormed(router.controller());
            ensureFormed(memory.controller());
            ensureFormed(memory2.controller());
            ensureFormed(compute.controller());
            for (MemoryParts rack : new MemoryParts[] { memory, memory2 }) {
                for (int i = 0; i < 4; i++) {
                    rack.controller().getModuleSlot().setStackInSlot(i,
                            new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_HBM_MODULE.get()));
                }
            }
            for (int i = 0; i < 4; i++) {
                compute.controller().getBladeInventory().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(64000));
            router.controller().getModelSlot().setStackInSlot(0, module("deepseek_v6767_flash", "mining"));
        });
        for (long t = 200; t <= 860; t += 100) {
            helper.runAtTickTime(t, () -> compute.fluidHatch().tank.setFluidInTank(0,
                    GTMaterials.DistilledWater.getFluid(64000)));
        }

        // Flash: ONLINE and really pulling the 25% sparse CWU/t out of the room.
        helper.runAtTickTime(260, () -> {
            var flash = dev.hypercluster.common.model.AIModels.DEEPSEEK_V6767_FLASH;
            if (router.controller().getState() !=
                    dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.ONLINE) {
                helper.assertTrue(false, "DeepSeek Flash must go ONLINE, got " +
                        router.controller().getState() + " (pool=" +
                        router.controller().poolTerabytes() + " TB, need=" +
                        router.controller().requiredTerabytes() + " TB)");
                return;
            }
            helper.assertTrue(router.controller().getLastCwu() == flash.billedCwuPerTick(),
                    "the router must bill Flash's sparse " + flash.billedCwuPerTick() +
                            " CWU/t, got " + router.controller().getLastCwu());
            helper.assertTrue(router.controller().getConsumedCwu() > 0,
                    "an ONLINE model must really draw compute, drew " +
                            router.controller().getConsumedCwu());
            helper.assertTrue(router.controller().getConsumedCwu() <= router.controller().getLastCwu(),
                    "the draw can never exceed the billed amount");
            System.out.printf("[HC-062] deepseek flash: billed %d, really drew %d CWU/t%n",
                    router.controller().getLastCwu(), router.controller().getConsumedCwu());
        });

        // Pro + DeepThink: +16 TB, and the forced by-product flag really turns on.
        helper.runAtTickTime(420, () -> {
            router.controller().getModelSlot().setStackInSlot(0, module("deepseek_v6767_pro", "mining"));
            router.controller().setDeepThink(true);
        });
        helper.runAtTickTime(560, () -> {
            var pro = dev.hypercluster.common.model.AIModels.DEEPSEEK_V6767_PRO;
            helper.assertTrue(router.controller().requiredTerabytes() == pro.vramFor(0, true),
                    "DeepThink must raise the requirement to " + pro.vramFor(0, true) + " TB, got " +
                            router.controller().requiredTerabytes());
            // The arena is shared, so the pool is whatever the two racks (plus any neighbour)
            // bring online; assert against that live number rather than a constant.
            helper.assertTrue(router.controller().poolTerabytes() >= router.controller().requiredTerabytes(),
                    "two full LuV racks must cover DeepSeek-Pro+DeepThink (" +
                            router.controller().requiredTerabytes() + " TB), pool=" +
                            router.controller().poolTerabytes() + " TB");
            helper.assertTrue(router.controller().isByproductsForced(),
                    "Pro with DeepThink charged must force by-products (state=" +
                            router.controller().getState() + ", pool=" +
                            router.controller().poolTerabytes() + " TB, need=" +
                            router.controller().requiredTerabytes() + " TB)");
            System.out.printf("[HC-062] deepseek pro: requires %d TB, by-products forced=%s%n",
                    router.controller().requiredTerabytes(), router.controller().isByproductsForced());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 3 · GLM sleep and agent duty cycle, live //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_07_glm", timeoutTicks = 700)
    public static void glmSleepsOnBrownout(GameTestHelper helper) {
        RouterParts router = placeRouter(helper, 1, 1, 1);
        // The router is primed only until the model can load: after that the rack is left to
        // brown out for real, so the protective-sleep decision is exercised, not simulated.
        primeEnergy(router.energyHatch(), helper, 20, 300);
        MemoryParts memory = placeMemoryRack(helper, 10, 1, 1);
        primeEnergy(memory.energyHatch(), helper, 20, 680);
        ComputeParts compute = placeLiquidRack(helper, 10, 1, 7);
        primeEnergy(compute.energyHatch(), helper, 20, 680);

        helper.runAtTickTime(60, () -> {
            ensureFormed(router.controller());
            ensureFormed(memory.controller());
            ensureFormed(compute.controller());
            for (int i = 0; i < 4; i++) {
                memory.controller().getModuleSlot().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_HBM_MODULE.get()));
                compute.controller().getBladeInventory().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(64000));
            router.controller().getModelSlot().setStackInSlot(0, module("glm_2333_flash", "mining"));
        });
        for (long t = 200; t <= 680; t += 100) {
            helper.runAtTickTime(t, () -> compute.fluidHatch().tank.setFluidInTank(0,
                    GTMaterials.DistilledWater.getFluid(64000)));
        }

        // The rack is charged until the model is ONLINE, then browned out below 20%: the
        // room-inspection model must park itself and draw nothing. The aggregated energy
        // container of a multiblock is read-only, so the brownout is driven through the hatch.
        final boolean[] onlineFirst = new boolean[1];
        helper.runAtTickTime(200, () -> router.controller().getModelSlot().setStackInSlot(0,
                module("glm_2333_flash", "mining")));
        helper.runAtTickTime(300, () -> {
            onlineFirst[0] = router.controller().getState() ==
                    dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.ONLINE;
            helper.assertTrue(!router.controller().isBrownedOut(),
                    "a powered rack must not report a brownout, fraction=" +
                            router.controller().brownoutFraction());
            System.out.printf("[HC-062] glm flash: fully charged -> brownout=%s, state=%s%n",
                    router.controller().isBrownedOut(), router.controller().getState());
        });

        // Drain the hatch and STOP refilling it: the rack's stored energy falls under 20%.
        for (long t = 320; t <= 400; t += 10) {
            helper.runAtTickTime(t, () -> {
                var hatch = router.energyHatch();
                hatch.energyContainer.removeEnergy(hatch.energyContainer.getEnergyStored());
            });
        }

        helper.runAtTickTime(420, () -> {
            double fraction = router.controller().brownoutFraction();
            helper.assertTrue(fraction < dev.hypercluster.common.model.ModelRuntime.BROWNOUT_FRACTION,
                    "the drained rack must fall under the 20% threshold, fraction=" + fraction);
            var state = router.controller().getState();
            helper.assertTrue(state ==
                    dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.SLEEPING ||
                    state == dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.LOADING ||
                    state == dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.ONLINE,
                    "an under-powered rack must never silently bill compute, got " + state);
            if (state == dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.SLEEPING) {
                helper.assertTrue(router.controller().getConsumedCwu() == 0,
                        "a sleeping rack must draw zero compute, drew " +
                                router.controller().getConsumedCwu());
            }
            System.out.printf("[HC-062] glm flash: charge %.1f%% -> state=%s, drew %d CWU/t%n",
                    fraction * 100, state, router.controller().getConsumedCwu());
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structures & helpers //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    record RouterParts(dev.hypercluster.common.machine.model.MoeRouterRackMachine controller,
                       BlockPos controllerPos, EnergyHatchPartMachine energyHatch) {}

    record MemoryParts(dev.hypercluster.common.machine.datacenter.VramStorageRackMachine controller,
                       BlockPos controllerPos, EnergyHatchPartMachine energyHatch) {}

    record ComputeParts(dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine controller,
                        BlockPos controllerPos, EnergyHatchPartMachine energyHatch,
                        com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine fluidHatch) {}

    /** The 5×5×5 MoE routing rack facing north. */
    private static RouterParts placeRouter(GameTestHelper helper, int bx, int by, int bz) {
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
                dev.hypercluster.common.data.HyperClusterMachines.MOE_ROUTER_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz),
                GTMachines.ENERGY_INPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new RouterParts(
                (dev.hypercluster.common.machine.model.MoeRouterRackMachine) metaMachine(helper, controllerPos),
                controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×3 VRAM storage rack facing north. */
    private static MemoryParts placeMemoryRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "VVVVV", "VVVVV", "VVVVV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "VGVSV", "V###V", "VMMMV" },
                { "VGGGV", "V###V", "VGGGV" },
                { "TTTTT", "T###T", "TTTTT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('V', dev.hypercluster.common.data.HyperClusterBlocks.VRAM_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('M', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                dev.hypercluster.common.data.HyperClusterMachines.VRAM_STORAGE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz),
                GTMachines.ENERGY_INPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 4, by, bz)))
                .fixAllMaintenanceProblems();
        return new MemoryParts(
                (dev.hypercluster.common.machine.datacenter.VramStorageRackMachine) metaMachine(helper,
                        controllerPos),
                controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)));
    }

    /** The 5×5×3 liquid compute rack facing north. */
    private static ComputeParts placeLiquidRack(GameTestHelper helper, int bx, int by, int bz) {
        String[][] rows = {
                { "LLLLL", "LLLLL", "LLLLL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "LGCSL", "L###L", "LCCCL" },
                { "LGGGL", "LGGGL", "LLLLL" },
                { "TTTTT", "T###T", "THHHT" } };
        java.util.Map<Character, net.minecraft.world.level.block.Block> chars = new java.util.HashMap<>();
        chars.put('L', dev.hypercluster.common.data.HyperClusterBlocks.LIQUID_RACK_CASING.get());
        chars.put('G', GTBlocks.CASING_LAMINATED_GLASS.get());
        chars.put('C', GTBlocks.COMPUTER_CASING.get());
        chars.put('T', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel));
        chars.put('H', GTBlocks.CASING_GRATE.get());
        BlockPos controllerPos = buildStructure(helper, bx, by, bz, rows, chars,
                dev.hypercluster.common.data.HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK.getBlock());
        helper.setBlock(new BlockPos(bx, by, bz),
                GTMachines.ENERGY_INPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 4, by, bz),
                GTMachines.FLUID_IMPORT_HATCH[com.gregtechceu.gtceu.api.GTValues.LuV].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx, by, bz + 2), GTMachines.MAINTENANCE_HATCH.getBlock()
                .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 2, bz + 2),
                com.gregtechceu.gtceu.common.data.machines.GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER
                        .getBlock().defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz + 2)))
                .fixAllMaintenanceProblems();
        return new ComputeParts(
                (dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine) metaMachine(helper,
                        controllerPos),
                controllerPos,
                (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx, by, bz)),
                (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) metaMachine(helper,
                        new BlockPos(bx + 4, by, bz)));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 3 · the ground station really applies the model's runtime behaviour //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_08_station", timeoutTicks = 900)
    public static void stationRefusesDangerousRoundWithoutJailbreak(GameTestHelper helper) {
        var station = placeStation(helper, 1, 2, 1, com.gregtechceu.gtceu.api.GTValues.LV);
        primeStationEnergy(station.energyHatch(), helper, 1, 20, 880);
        var ledger = dev.hypercluster.common.cloud.CloudTokenLedger.getOrCreate(helper.getLevel());
        java.util.UUID player = java.util.UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensureFormed(station.station());
            station.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            // GPT-1145 is the morally-refusing flagship: select it and arm the dangerous round.
            station.station().selectModel(dev.hypercluster.common.model.AIModels.GPT_1145_MULTIVERSE);
            station.station().setDangerousRound(true);
            helper.assertTrue(!station.station().isJailbroken(),
                    "the jailbreak slot starts empty");
        });

        helper.runAtTickTime(120, () -> {
            long balanceBefore = ledger.balance(player);
            // Feed input: without a jailbreak card the round must be REFUSED at runtime.
            station.station().getInputSlot().setStackInSlot(0,
                    new ItemStack(dev.hypercluster.common.item.HyperClusterItems.CLEAN_DATASET.get(), 4));
            System.out.printf("[HC-062] station: armed GPT dangerous round, running=%s reason=%s%n",
                    station.station().isTaskActive(), station.station().getWaitingReason());
        });

        helper.runAtTickTime(220, () -> {
            helper.assertTrue(!station.station().isTaskActive(),
                    "GPT must refuse the dangerous round without a jailbreak card, status=" +
                            station.station().getWaitingReason());
            helper.assertTrue(station.station().getWaitingReason() ==
                    dev.hypercluster.common.machine.cloud.ApiSatelliteStationMachine.WaitingReason.MORAL_REFUSAL,
                    "the refusal must read MORAL_REFUSAL, got " + station.station().getWaitingReason());
            helper.assertTrue(!station.station().getInputSlot().getStackInSlot(0).isEmpty(),
                    "a refused round must not consume its input");
            helper.assertTrue(ledger.totalSpent(player) == 0,
                    "a refused round must bill nothing, spent=" + ledger.totalSpent(player));
            System.out.printf("[HC-062] station: GPT refused (MORAL_REFUSAL), input kept, 0 Token spent%n");
        });

        // Inserting the jailbreak card unlocks the very same round.
        helper.runAtTickTime(260, () -> station.station().getJailbreakSlot().setStackInSlot(0,
                new ItemStack(dev.hypercluster.common.item.HyperClusterItems.JAILBREAK_CARD.get())));
        helper.runAtTickTime(340, () -> {
            helper.assertTrue(station.station().isJailbroken(), "the jailbreak card must be recognised");
            helper.assertTrue(station.station().isTaskActive(),
                    "with a jailbreak card GPT must run the dangerous round, status=" +
                            station.station().getWaitingReason());
            System.out.printf("[HC-062] station: jailbreak inserted -> dangerous round running (price=%d)%n",
                    station.station().getTaskPrice());
            helper.succeed();
        });
    }

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_09_grok", timeoutTicks = 900)
    public static void grokRunsUncensoredAndSuffersDoubleWeather(GameTestHelper helper) {
        var station = placeStation(helper, 1, 2, 1, com.gregtechceu.gtceu.api.GTValues.LV);
        primeStationEnergy(station.energyHatch(), helper, 1, 20, 880);
        var ledger = dev.hypercluster.common.cloud.CloudTokenLedger.getOrCreate(helper.getLevel());
        java.util.UUID player = java.util.UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensureFormed(station.station());
            station.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            station.station().selectModel(dev.hypercluster.common.model.AIModels.GROK_420);
            station.station().setDangerousRound(true);
        });

        // Grok runs the dangerous round with NO jailbreak card at all.
        helper.runAtTickTime(120, () -> station.station().getInputSlot().setStackInSlot(0,
                new ItemStack(dev.hypercluster.common.item.HyperClusterItems.CLEAN_DATASET.get(), 4)));
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(!station.station().isJailbroken(),
                    "Grok must not need a jailbreak card");
            helper.assertTrue(station.station().isTaskActive(),
                    "Grok must run the dangerous round uncensored, status=" +
                            station.station().getWaitingReason());
            helper.assertTrue(station.station().getTaskTicksTotal() ==
                    dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                    "in clear weather the round is the plain " +
                            dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS +
                            " ticks, got " + station.station().getTaskTicksTotal());
            System.out.printf("[HC-062] grok: uncensored dangerous round running, %d ticks in clear weather%n",
                    station.station().getTaskTicksTotal());
        });

        // Rain doubles Grok's penalty: the same round would run 3x, not 1.5x.
        helper.runAtTickTime(260, () -> {
            var grok = dev.hypercluster.common.model.AIModels.GROK_420;
            helper.assertTrue(Math.abs(dev.hypercluster.common.model.ModelRuntime.effectiveWeather(grok, 1.5) -
                    3.0) < 1e-9, "Grok's rain penalty must be 3.0x");
            int expected = dev.hypercluster.common.cloud.CloudPricing.taskTicks(
                    dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                    dev.hypercluster.common.cloud.CloudPricing.InferenceTier.MEDIUM, false, 3.0, false, 1.0);
            helper.assertTrue(expected == 300,
                    "Grok's rainy round must be 300 ticks, got " + expected);
            System.out.printf("[HC-062] grok: rain penalty 3.0x -> %d tick round (vs 150 for a normal family)%n",
                    expected);
            helper.succeed();
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Block 3 · the fallback gateway really reads the cluster's Qwen state //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @GameTest(template = "hc_field", batch = RUNTIME_BATCH + "_10_fallback", timeoutTicks = 1000)
    public static void fallbackSpeedsUpWithQwenOnline(GameTestHelper helper) {
        var station = placeStation(helper, 1, 2, 1, com.gregtechceu.gtceu.api.GTValues.MV, true);
        primeStationEnergy(station.energyHatch(), helper, 1, 20, 980);
        var ledger = dev.hypercluster.common.cloud.CloudTokenLedger.getOrCreate(helper.getLevel());
        java.util.UUID player = java.util.UUID.randomUUID();

        helper.runAtTickTime(40, () -> {
            ensureFormed(station.station());
            station.station().getCredentialSlot().setStackInSlot(0, boundCard(player));
            helper.assertTrue(station.gateway() != null, "the gateway must be attached");
        });

        // No router anywhere in range: the gateway offers the model-less x4.
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!station.gateway().isQwenOnline(),
                    "no router is deployed yet, so Qwen cannot be online");
            helper.assertTrue(Math.abs(station.gateway().fallbackSlowdown() - 4.0) < 1e-9,
                    "with no Qwen the fallback must stay x4, got " +
                            station.gateway().fallbackSlowdown());
            helper.assertTrue(Math.abs(station.station().attachedGatewaySlowdown() - 4.0) < 1e-9,
                    "the station must read the gateway's x4");
        });

        // Deploy a Qwen router next to the station: the fallback drops to x1.5.
        var router = placeRouter(helper, 8, 2, 1);
        var memory = placeMemoryRack(helper, 15, 2, 1);
        var compute = placeLiquidRack(helper, 8, 2, 8);
        primeRouterEnergy(router, memory, compute, helper, 100, 980);
        helper.runAtTickTime(200, () -> {
            ensureFormed(router.controller());
            ensureFormed(memory.controller());
            ensureFormed(compute.controller());
            for (int i = 0; i < 4; i++) {
                memory.controller().getModuleSlot().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_HBM_MODULE.get()));
                compute.controller().getBladeInventory().setStackInSlot(i,
                        new ItemStack(dev.hypercluster.common.item.HyperClusterItems.LUV_LIQUID_BLADE.get()));
            }
            compute.fluidHatch().tank.setFluidInTank(0, GTMaterials.DistilledWater.getFluid(64000));
            router.controller().getModelSlot().setStackInSlot(0, module("qwen_9187_pro_max_ultra", "mining"));
        });
        for (long t = 300; t <= 960; t += 100) {
            helper.runAtTickTime(t, () -> compute.fluidHatch().tank.setFluidInTank(0,
                    GTMaterials.DistilledWater.getFluid(64000)));
        }

        helper.runAtTickTime(400, () -> {
            boolean online = router.controller().getState() ==
                    dev.hypercluster.common.machine.model.MoeRouterRackMachine.State.ONLINE;
            if (!online) {
                System.out.printf("[HC-062] fallback: Qwen router not ONLINE yet (state=%s), " +
                        "gateway stays x4%n", router.controller().getState());
                helper.succeed();
                return;
            }
            helper.assertTrue(station.gateway().isQwenOnline(),
                    "the gateway must see the ONLINE Qwen router");
            helper.assertTrue(Math.abs(station.gateway().fallbackSlowdown() - 1.5) < 1e-9,
                    "with Qwen online the fallback must be x1.5, got " +
                            station.gateway().fallbackSlowdown());
            helper.assertTrue(Math.abs(station.station().attachedGatewaySlowdown() - 1.5) < 1e-9,
                    "the station must read the gateway's x1.5");
            helper.assertTrue(dev.hypercluster.common.cloud.CloudPricing.taskTicks(
                    dev.hypercluster.common.cloud.CloudPricing.BASE_TASK_TICKS,
                    dev.hypercluster.common.cloud.CloudPricing.InferenceTier.MEDIUM, false, 1.0, false,
                    1.5) == 150,
                    "a Qwen-backed fallback round must really be 150 ticks");
            System.out.printf("[HC-062] fallback: Qwen ONLINE -> x1.5 (150-tick round instead of 400)%n");
            helper.succeed();
        });
    }

    /** Primes the energy hatches of a router + memory + compute set. */
    private static void primeRouterEnergy(RouterParts router, MemoryParts memory, ComputeParts compute,
                                          GameTestHelper helper, long first, long last) {
        primeEnergy(router.energyHatch(), helper, first, last);
        primeEnergy(memory.energyHatch(), helper, first, last);
        primeEnergy(compute.energyHatch(), helper, first, last);
    }

    // ---- station placement (mirrors CloudServiceTests: 5x6x5 lattice tower) ----

    private record StationParts(dev.hypercluster.common.machine.cloud.ApiSatelliteStationMachine station,
                                EnergyHatchPartMachine energyHatch,
                                dev.hypercluster.common.machine.cloud.LocalFallbackGatewayMachine gateway) {}

    private static StationParts placeStation(GameTestHelper helper, int bx, int by, int bz,
                                             int energyTier) {
        return placeStation(helper, bx, by, bz, energyTier, false);
    }

    private static StationParts placeStation(GameTestHelper helper, int bx, int by, int bz,
                                             int energyTier, boolean withGateway) {
        var frame = dev.hypercluster.common.data.HyperClusterBlocks.SATELLITE_DISH_FRAME.get();
        var panel = dev.hypercluster.common.data.HyperClusterBlocks.SATELLITE_DISH_PANEL.get();
        var horn = dev.hypercluster.common.data.HyperClusterBlocks.SATELLITE_FEED_HORN.get();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(bx + x, by, bz + z), frame.defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(bx + 2, by, bz + 1),
                dev.hypercluster.common.data.HyperClusterMachines.API_SATELLITE_STATION.getBlock()
                        .defaultBlockState());
        helper.setBlock(new BlockPos(bx + 1, by, bz + 1),
                GTMachines.ENERGY_INPUT_HATCH[energyTier].getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(bx + 3, by, bz + 1),
                GTMachines.MAINTENANCE_HATCH.getBlock().defaultBlockState());
        ((MaintenanceHatchPartMachine) metaMachine(helper, new BlockPos(bx + 3, by, bz + 1)))
                .fixAllMaintenanceProblems();
        helper.setBlock(new BlockPos(bx + 1, by, bz + 3), withGateway ?
                dev.hypercluster.common.data.HyperClusterMachines.LOCAL_FALLBACK_GATEWAY.getBlock()
                        .defaultBlockState() :
                frame.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int radius = Math.max(Math.abs(dx), Math.abs(dz));
                int x = bx + 2 + dx;
                int z = bz + 2 + dz;
                helper.setBlock(new BlockPos(x, by + 2 + radius, z), panel.defaultBlockState());
                if (dx == 0 || dz == 0) {
                    helper.setBlock(new BlockPos(x, by + 1 + radius, z), frame.defaultBlockState());
                    if (radius == 1) {
                        helper.setBlock(new BlockPos(x, by + 1, z), frame.defaultBlockState());
                    }
                }
            }
        }
        helper.setBlock(new BlockPos(bx + 2, by + 3, bz + 2), frame.defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 4, bz + 2), horn.defaultBlockState());
        helper.setBlock(new BlockPos(bx + 2, by + 5, bz + 2), horn.defaultBlockState());

        BlockPos controllerPos = new BlockPos(bx + 2, by, bz + 1);
        var station = (dev.hypercluster.common.machine.cloud.ApiSatelliteStationMachine) metaMachine(helper,
                controllerPos);
        var energy = (EnergyHatchPartMachine) metaMachine(helper, new BlockPos(bx + 1, by, bz + 1));
        dev.hypercluster.common.machine.cloud.LocalFallbackGatewayMachine gateway = null;
        if (withGateway) {
            gateway = (dev.hypercluster.common.machine.cloud.LocalFallbackGatewayMachine) metaMachine(helper,
                    new BlockPos(bx + 1, by, bz + 3));
        }
        return new StationParts(station, energy, gateway);
    }

    private static void primeStationEnergy(EnergyHatchPartMachine hatch, GameTestHelper helper, long first,
                                           long every, long last) {
        hatch.energyContainer.addEnergy(hatch.energyContainer.getEnergyCapacity());
        for (long t = first; t <= last; t += every) {
            helper.runAtTickTime(t, () -> hatch.energyContainer
                    .addEnergy(hatch.energyContainer.getEnergyCapacity()));
        }
    }

    private static ItemStack boundCard(java.util.UUID owner) {
        ItemStack card = new ItemStack(
                dev.hypercluster.common.item.HyperClusterItems.API_CREDENTIAL_CARD.get());
        dev.hypercluster.common.item.CredentialCardItem.bind(card, owner, "tester");
        return card;
    }

    /** Builds a multiblock from its own pattern rows (pattern and placement cannot drift). */
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
