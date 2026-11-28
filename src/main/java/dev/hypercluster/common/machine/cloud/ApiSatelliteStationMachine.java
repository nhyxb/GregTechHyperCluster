package dev.hypercluster.common.machine.cloud;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.common.cloud.CloudModels;
import dev.hypercluster.common.cloud.CloudPricing;
import dev.hypercluster.common.cloud.CloudTokenLedger;
import dev.hypercluster.common.item.CredentialCardItem;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.TokenCreditCardItem;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.model.AIModelDefinition;
import dev.hypercluster.common.model.ModelRuntime;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * 0.4 · the API satellite ground station ({@code hypercluster:api_satellite_station}).
 *
 * <p>
 * A fixed 5×6×5 IV lattice-tower multiblock (a 3×3 satellite-frame base carrying the
 * controller and hatches, a stepped concave 5×5 dish of reflector panels and a two-block feed
 * horn on top) that runs the local simulation of a closed-source cloud: it opens per-player-UUID
 * cloud accounts in the shared {@link CloudTokenLedger}, buys high-purity datasets for Tokens,
 * lets Token credit cards deposit/withdraw, and runs one billable demo closed-source service:
 * <b>one clean dataset in, one high-purity mining dataset out</b> (recipe id
 * {@value #DEMO_RECIPE_ID}).
 *
 * <p>
 * <b>Low-footprint uplink.</b> The station only needs an LV or MV energy hatch: a Medium round
 * draws {@value CloudPricing#MEDIUM_EU_T} EU/t and an Extra High round
 * {@value CloudPricing#EXTRA_HIGH_EU_T} EU/t. The feed horn must see open sky — an occluded horn
 * refuses new rounds entirely (拒绝联机). Rain attenuates the round ×1.5, thunder ×2.0; above
 * {@code Y > 120} the uplink earns a small latency/rate optimization (duration ×0.9, bill ×0.95).
 *
 * <p>
 * <b>Billing contract.</b> Every cloud round is one order in the ledger: the price is computed
 * from the round conditions (tier × cache × batch × altitude, see {@link CloudPricing}), then
 * pre-deducted (held) at round start. A completed round settles; a cancelled or failed round
 * releases the hold exactly once (不吞单不双扣). The prompt-cache key is
 * {@code (playerUuid, machinePos, recipeId, modelId, tier)}; from the second consecutive round
 * of one key the Token rate drops to 10%, and any dimension change restarts the full rate.
 * Batch mode bills ×0.5 at ×2 duration and stacks multiplicatively with the cache hit (5%).
 * Over the account's concurrency ceiling a 429 pauses new rounds; a model past its Leaving-Soon
 * countdown is blocked. Both conditions — plus an insufficient balance — hand the round to an
 * attached {@link LocalFallbackGatewayMachine} instead: the same task runs locally at ×4
 * duration with no Token billing.
 */
public class ApiSatelliteStationMachine extends WorkableElectricMultiblockMachine
                                        implements com.gregtechceu.gtceu.api.machine.feature.IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ApiSatelliteStationMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Stable recipe id of the one demo closed-source service round (part of the cache key). */
    public static final String DEMO_RECIPE_ID = "hypercluster:cloud_scrub_demo";

    /** Output of the demo service: one clean dataset becomes one high-purity mining dataset. */
    public static final int DEMO_OUTPUT_PER_ROUND = 1;

    /** Why the station currently refuses to start a new cloud round (transient, UI only). */
    public enum WaitingReason {
        NONE,
        NO_CREDENTIAL,
        NO_INPUT,
        SKY_BLOCKED,
        MODEL_OFFLINE,
        RATE_LIMITED_429,
        INSUFFICIENT_FUNDS,
        LOW_POWER,
        PAUSED,
        /** 0.6.2 · GPT-1145 morally refused a whitelisted dangerous round: no jailbreak card. */
        MORAL_REFUSAL,
        /** 0.6.2 · the model has no dangerous-round capability at all (ordinary families). */
        DANGEROUS_ROUND_UNSUPPORTED
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // GUI slots (all server-side; the ledger is the single source of truth) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The bound API credential card — whose account this station operates on. */
    @Persisted
    protected final CustomItemStackHandler credentialSlot;
    /** Upload slot: high-purity datasets are auto-sold for Tokens while a credential is present. */
    @Persisted
    protected final CustomItemStackHandler sellSlot;
    /** The Token credit card for deposits / withdrawals. */
    @Persisted
    protected final CustomItemStackHandler cardSlot;
    /** Service input: clean datasets (one per round). */
    @Persisted
    protected final CustomItemStackHandler inputSlot;
    /** Service output: high-purity mining datasets (withdraw-only). */
    @Persisted
    protected final CustomItemStackHandler outputSlot;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Station settings (persisted, UI-live) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Persisted
    @DescSynced
    protected int selectedTier = CloudPricing.InferenceTier.MEDIUM.ordinal();
    @Persisted
    @DescSynced
    protected int selectedModel = CloudModels.defaultModelIndex();
    @Persisted
    @DescSynced
    protected boolean batchMode = false;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // The active round (persisted across save/reload; its order lives in the ledger) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Persisted
    @DescSynced
    protected boolean taskActive = false;
    @Persisted
    @DescSynced
    protected String taskOrderId = "";
    /** The account owner of the active round (persisted: survives reloads and card removal). */
    @Persisted
    @DescSynced
    protected String taskOwnerUuid = "";
    @Persisted
    @DescSynced
    protected long taskPrice = 0;
    @Persisted
    @DescSynced
    protected String taskCacheKey = "";
    @Persisted
    @DescSynced
    protected boolean taskCacheHit = false;
    /** 0 = cloud round (billed), 1 = local fallback round (unbilled, ×4 duration). */
    @Persisted
    @DescSynced
    protected int taskMode = 0;
    @Persisted
    @DescSynced
    protected int taskTicksTotal = 0;
    @Persisted
    @DescSynced
    protected int taskTicksRemaining = 0;
    /** 0.6.2 · the model index the running round was priced and started with (-1 = unset). */
    @Persisted
    @DescSynced
    protected int taskModelIndex = -1;

    /**
     * 0.6.2 · the jailbreak patch card slot. A {@code gpt_1145_multiverse} station refuses
     * whitelisted dangerous chemistry rounds until a card is inserted here (道德拒答), and the
     * card is not consumed.
     */
    @Persisted
    protected final CustomItemStackHandler jailbreakSlot;
    /**
     * 0.6.2 · dangerous-round switch. While armed the station runs the whitelisted dangerous
     * chemistry service instead of the ordinary scrub; whether that is <b>allowed</b> is decided
     * per model at runtime (Grok never needs a card, GPT always does, everyone else cannot).
     */
    @Persisted
    @DescSynced
    protected boolean dangerousRound = false;

    /** Runtime: does this machine currently hold one cloud session of the account (429 gate). */
    protected boolean sessionHeld = false;

    @Nullable
    protected TickableSubscription tickSubs;

    /** UI-live round-refusal status (synced, never persisted — a restart starts with NONE). */
    @DescSynced
    protected WaitingReason waitingReason = WaitingReason.NO_CREDENTIAL;
    protected boolean fallbackGatewayAttached = false;

    public ApiSatelliteStationMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.credentialSlot = new CustomItemStackHandler(1);
        this.credentialSlot.setFilter(
                stack -> stack.is(HyperClusterItems.API_CREDENTIAL_CARD.get()));
        this.sellSlot = new CustomItemStackHandler(1);
        this.sellSlot.setFilter(stack -> stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ||
                stack.is(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()));
        this.cardSlot = new CustomItemStackHandler(1);
        this.cardSlot.setFilter(stack -> stack.is(HyperClusterItems.TOKEN_CREDIT_CARD.get()));
        this.inputSlot = new CustomItemStackHandler(1);
        this.inputSlot.setFilter(stack -> stack.is(HyperClusterItems.CLEAN_DATASET.get()));
        this.outputSlot = new CustomItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }
        };
        this.outputSlot.setFilter(stack -> stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()));
        this.jailbreakSlot = new CustomItemStackHandler(1);
        this.jailbreakSlot.setFilter(stack -> stack.is(HyperClusterItems.JAILBREAK_CARD.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Ledger access //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The shared ledger of the save (overworld-backed, every dimension sees the same data). */
    protected CloudTokenLedger ledger() {
        return CloudTokenLedger.getOrCreate((ServerLevel) getLevel());
    }

    /** The bound account owner, or null without a bound credential card. */
    @Nullable
    public UUID accountOwner() {
        ItemStack card = credentialSlot.getStackInSlot(0);
        return CredentialCardItem.owner(card);
    }

    /** The world position of the feed horn (structure-relative: 5 up, 1 behind the controller). */
    public BlockPos hornPos() {
        return getPos().above(5).relative(getFrontFacing().getOpposite(), 1);
    }

    /** The current game day of the save (model lifecycles are evaluated against it). */
    public long gameDay() {
        ServerLevel overworld = ((ServerLevel) getLevel()).getServer().overworld();
        return overworld.getDayTime() / 24000L;
    }

    public CloudPricing.InferenceTier selectedTier() {
        return CloudPricing.InferenceTier.byOrdinal(selectedTier);
    }

    public AIModelDefinition selectedModel() {
        return CloudModels.byIndex(selectedModel);
    }

    /** The selected model's index in the API catalog (persisted on the round at start). */
    public int selectedModelIndex() {
        return Math.floorMod(selectedModel, CloudModels.MODELS.size());
    }

    public boolean isFallbackTask() {
        return taskActive && taskMode == 1;
    }

    /**
     * The model the <b>active</b> round was started with, captured when the round began. Reading
     * {@link #selectedModel()} at settlement would credit a model the operator only just picked.
     */
    public AIModelDefinition taskModel() {
        if (taskModelIndex >= 0) {
            return CloudModels.byIndex(taskModelIndex);
        }
        return selectedModel();
    }

    /** The account owner the active round belongs to (persisted on the round itself). */
    @Nullable
    public UUID taskOwner() {
        if (taskOwnerUuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(taskOwnerUuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public WaitingReason getWaitingReason() {
        return waitingReason;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Test & UI accessors //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public CustomItemStackHandler getCredentialSlot() {
        return credentialSlot;
    }

    public CustomItemStackHandler getSellSlot() {
        return sellSlot;
    }

    public CustomItemStackHandler getCardSlot() {
        return cardSlot;
    }

    public CustomItemStackHandler getInputSlot() {
        return inputSlot;
    }

    public CustomItemStackHandler getOutputSlot() {
        return outputSlot;
    }

    public CustomItemStackHandler getJailbreakSlot() {
        return jailbreakSlot;
    }

    /** True while the jailbreak patch card is inserted (GPT-1145's dangerous-round unlock). */
    public boolean isJailbroken() {
        return !jailbreakSlot.getStackInSlot(0).isEmpty();
    }

    public boolean isDangerousRound() {
        return dangerousRound;
    }

    public void setDangerousRound(boolean value) {
        this.dangerousRound = value;
    }

    public boolean isTaskActive() {
        return taskActive;
    }

    public String getTaskOrderId() {
        return taskOrderId;
    }

    public long getTaskPrice() {
        return taskPrice;
    }

    public int getTaskTicksTotal() {
        return taskTicksTotal;
    }

    public boolean isFallbackGatewayAttached() {
        return fallbackGatewayAttached;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Credential binding: right-click the controller with an unbound credential card //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(HyperClusterItems.API_CREDENTIAL_CARD.get())) {
            if (!level.isClientSide()) {
                // Bind the card to the using player — writing the identity is server-side.
                CredentialCardItem.bind(held, player.getUUID(), player.getName().getString());
                player.displayClientMessage(Component.translatable(
                        "hypercluster.machine.api_satellite_station.card_bound",
                        player.getName().getString()), true);
            }
            return InteractionResult.CONSUME;
        }
        return super.onUse(state, level, pos, player, hand, hit);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Structure lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.fallbackGatewayAttached = false;
        for (var part : getParts()) {
            if (part instanceof LocalFallbackGatewayMachine) {
                this.fallbackGatewayAttached = true;
            }
        }
        updateTickSubscription();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // A broken structure cancels the active round and releases the hold exactly once.
        cancelTask();
        this.fallbackGatewayAttached = false;
        updateTickSubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(this::updateTickSubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        // The chunk unloads: release the runtime session but keep the held order — the ledger
        // persists it and the round resumes on reload.
        UUID owner = accountOwner();
        if (owner != null && sessionHeld && getLevel() instanceof ServerLevel serverLevel) {
            try {
                CloudTokenLedger.getOrCreate(serverLevel).endSession(owner);
            } catch (Exception ignored) {}
        }
        this.sessionHeld = false;
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed() && getLevel() != null && !getLevel().isClientSide()) {
            this.tickSubs = subscribeServerTick(this.tickSubs, this::tick);
        } else if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    @Override
    public void onMachineRemoved() {
        cancelTask();
        clearInventory(credentialSlot);
        clearInventory(sellSlot);
        clearInventory(cardSlot);
        clearInventory(inputSlot);
        clearInventory(outputSlot);
        clearInventory(jailbreakSlot);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // The service engine //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void tick() {
        if (!isFormed() || getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        CloudTokenLedger ledger = ledger();
        UUID owner = accountOwner();

        // 1) The account touchpoints: opening the account grants the welcome bonus exactly
        // once (the ledger flag deduplicates across every station, dimension and reload),
        // and uploaded high-purity datasets auto-sell for integer Tokens.
        if (owner != null) {
            ledger.claimWelcomeBonus(owner);
            autoSell(owner, ledger);
        }

        // 2) Removing the credential card cancels the round and releases its hold (cancel path).
        if (taskActive && owner == null) {
            cancelTask();
        }

        // 3) A resumed cloud round re-acquires its runtime session (sessions never persist;
        // an already-paid round is never blocked by the 429 gate).
        UUID roundOwner = taskOwner();
        if (taskActive && taskMode == 0 && !sessionHeld && roundOwner != null) {
            sessionHeld = ledger.beginSession(roundOwner);
        }

        // 4) Run / start rounds.
        if (taskActive) {
            progressRound(ledger);
        } else {
            tryStartRound(ledger, owner);
        }
    }

    /** Upload sale: every high-purity dataset in the sell slot converts to integer Tokens. */
    protected void autoSell(UUID owner, CloudTokenLedger ledger) {
        ItemStack stack = sellSlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        long pricePer;
        if (stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get())) {
            pricePer = CloudPricing.MINING_DATASET_SALE_PRICE;
        } else if (stack.is(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get())) {
            pricePer = CloudPricing.CHEMICAL_DATASET_SALE_PRICE;
        } else {
            return;
        }
        long total = pricePer * stack.getCount();
        ledger.credit(owner, total);
        sellSlot.setStackInSlot(0, ItemStack.EMPTY);
    }

    /** One working tick of the active round: EU first, then progress, then completion. */
    protected void progressRound(CloudTokenLedger ledger) {
        if (!recipeLogic.isWorkingEnabled()) {
            waitingReason = WaitingReason.PAUSED;
            updateRenderStateFor(waitingReason);
            return;
        }
        long eut = taskMode == 1 ? CloudPricing.FALLBACK_EU_T : selectedTier().eut();
        var energy = getEnergyContainer();
        if (energy == null || energy.getEnergyStored() < eut) {
            waitingReason = WaitingReason.LOW_POWER;
            updateRenderStateFor(waitingReason);
            return;
        }
        energy.removeEnergy(eut);
        waitingReason = WaitingReason.NONE;
        if (--taskTicksRemaining <= 0) {
            finishRound(ledger);
        }
        updateRenderStateFor(waitingReason);
    }

    /** Starts the next round: cloud-first, fallback only through an attached gateway. */
    protected void tryStartRound(CloudTokenLedger ledger, @Nullable UUID owner) {
        if (!recipeLogic.isWorkingEnabled()) {
            waitingReason = WaitingReason.PAUSED;
            updateRenderStateFor(waitingReason);
            return;
        }
        if (owner == null) {
            waitingReason = WaitingReason.NO_CREDENTIAL;
            updateRenderStateFor(waitingReason);
            return;
        }
        if (inputSlot.getStackInSlot(0).isEmpty()) {
            waitingReason = WaitingReason.NO_INPUT;
            updateRenderStateFor(waitingReason);
            return;
        }
        // The uplink itself: an occluded feed horn refuses the round — cloud AND fallback.
        BlockPos horn = hornPos();
        if (!CloudPricing.hasOpenSky(getLevel(), horn)) {
            waitingReason = WaitingReason.SKY_BLOCKED;
            updateRenderStateFor(waitingReason);
            return;
        }
        // 0.6.2 · the weather the round ACTUALLY runs at: Grok-420's fragile uplink doubles the
        // rain/thunder penalty, everyone else takes the plain attenuation. Applied to duration.
        double baseWeather = CloudPricing.weatherAt(getLevel(), horn);
        AIModelDefinition model = selectedModel();
        double weather = ModelRuntime.effectiveWeather(model, baseWeather);
        boolean highAlt = CloudPricing.highAltitudeBonus(getPos());
        CloudPricing.InferenceTier tier = selectedTier();
        // 0.6.2 · the whitelisted dangerous chemistry service: the model decides at runtime.
        // GPT-1145 refuses without a jailbreak card (MORAL_REFUSAL) — a real refusal, nothing is
        // held or billed; Grok-420 runs it uncensored; any other family has no such capability.
        if (dangerousRound) {
            if (!ModelRuntime.permitsDangerousRound(model, isJailbroken())) {
                boolean refuses = model.has(AIModelDefinition.Trait.MORAL_REFUSAL);
                waitingReason = refuses ? WaitingReason.MORAL_REFUSAL :
                        WaitingReason.DANGEROUS_ROUND_UNSUPPORTED;
                updateRenderStateFor(waitingReason);
                return;
            }
        }

        // Model lifecycle: past the Leaving-Soon countdown the model is offline.
        CloudModels.Lifecycle lifecycle = CloudModels.lifecycle(model, gameDay());
        if (lifecycle == CloudModels.Lifecycle.RETIRED) {
            if (fallbackGatewayAttached) {
                startFallbackRound(owner, weather, highAlt);
            } else {
                waitingReason = WaitingReason.MODEL_OFFLINE;
                updateRenderStateFor(waitingReason);
            }
            return;
        }

        // 429 concurrency gate: over the account's ceiling, cloud rounds are paused.
        if (!ledger.beginSession(owner)) {
            if (fallbackGatewayAttached) {
                startFallbackRound(owner, weather, highAlt);
            } else {
                waitingReason = WaitingReason.RATE_LIMITED_429;
                updateRenderStateFor(waitingReason);
            }
            return;
        }

        // Price the round and pre-deduct it as one order (order-id deduplicated by the ledger).
        String key = CloudTokenLedger.cacheKey(owner, getPos().toShortString(), DEMO_RECIPE_ID,
                model.id().toString(), tier);
        boolean cacheHit = ledger.isCacheHit(owner, key);
        long price = CloudPricing.taskPrice(model.tokenCostPerRound(), tier, cacheHit, batchMode, highAlt);
        String orderId = UUID.randomUUID().toString();
        CloudTokenLedger.HoldResult hold = ledger.hold(owner, orderId, price);
        switch (hold) {
            case GRANTED, ALREADY_HELD, ALREADY_SETTLED -> {
                ledger.recordCacheKey(owner, key);
                consumeInput();
                startCloudRound(owner, orderId, price, key, cacheHit,
                        CloudPricing.demoTaskTicks(tier, batchMode, weather, highAlt, false));
                this.sessionHeld = true;
            }
            case INSUFFICIENT -> {
                ledger.endSession(owner);
                if (fallbackGatewayAttached) {
                    startFallbackRound(owner, weather, highAlt);
                } else {
                    waitingReason = WaitingReason.INSUFFICIENT_FUNDS;
                    updateRenderStateFor(waitingReason);
                }
            }
            case ALREADY_RELEASED -> {
                // Defensive: this order id was already refunded — never re-run on it.
                ledger.endSession(owner);
                waitingReason = WaitingReason.INSUFFICIENT_FUNDS;
                updateRenderStateFor(waitingReason);
            }
        }
    }

    /** Consumes one clean dataset from the input slot at round start. */
    protected void consumeInput() {
        ItemStack input = inputSlot.getStackInSlot(0);
        if (!input.isEmpty()) {
            input.shrink(1);
            if (input.getCount() <= 0) {
                inputSlot.setStackInSlot(0, ItemStack.EMPTY);
            }
        }
    }

    protected void startCloudRound(UUID owner, String orderId, long price, String cacheKey,
                                   boolean cacheHit, int ticks) {
        this.taskActive = true;
        this.taskMode = 0;
        this.taskModelIndex = selectedModelIndex();
        this.taskOrderId = orderId;
        this.taskOwnerUuid = owner.toString();
        this.taskPrice = price;
        this.taskCacheKey = cacheKey;
        this.taskCacheHit = cacheHit;
        this.taskTicksTotal = ticks;
        this.taskTicksRemaining = ticks;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
    }

    /**
     * The gateway takes over: same task, local EU, no Token billing.
     *
     * <p>
     * <b>0.6.2</b>: the duration multiplier is no longer hard-coded ×4. The attached gateway asks
     * the machine room whether a MoE router is really serving {@code qwen_9187_pro_max_ultra}: if
     * yes the local run degrades only ×1.5, otherwise the model-less ×4 of the 0.4 contract
     * applies. See {@link LocalFallbackGatewayMachine#fallbackSlowdown()}.
     */
    protected void startFallbackRound(UUID owner, double weather, boolean highAlt) {
        consumeInput();
        this.taskActive = true;
        this.taskMode = 1;
        this.taskModelIndex = selectedModelIndex();
        this.taskOrderId = "";
        this.taskOwnerUuid = owner.toString();
        this.taskPrice = 0;
        this.taskCacheKey = "";
        this.taskCacheHit = false;
        double slowdown = attachedGatewaySlowdown();
        this.taskTicksTotal = CloudPricing.taskTicks(CloudPricing.BASE_TASK_TICKS,
                CloudPricing.InferenceTier.MEDIUM, batchMode, weather, highAlt, slowdown);
        this.taskTicksRemaining = this.taskTicksTotal;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
    }

    /**
     * The slowdown the attached gateway currently offers. Without a gateway part the station never
     * reaches a fallback round at all, but the model-less ×4 stays the safe answer.
     */
    public double attachedGatewaySlowdown() {
        for (var part : getParts()) {
            if (part instanceof LocalFallbackGatewayMachine gateway) {
                return gateway.fallbackSlowdown();
            }
        }
        return ModelRuntime.BARE_FALLBACK_SLOWDOWN;
    }

    /** The round completed: settle the order (cloud) or record the fallback round. */
    protected void finishRound(CloudTokenLedger ledger) {
        if (taskMode == 0) {
            ledger.settle(taskOrderId); // HELD -> SETTLED; the hold becomes final spend.
            UUID roundOwner = taskOwner();
            if (roundOwner != null) {
                ledger.endSession(roundOwner);
            }
            this.sessionHeld = false;
        } else {
            for (var part : getParts()) {
                if (part instanceof LocalFallbackGatewayMachine gateway) {
                    gateway.recordFallbackRound();
                    break;
                }
            }
        }
        // 0.6.2 · the model's settlement bonus really lands on the output: Platinum's noise
        // suppression on chemical rounds, Xenon's zero hallucination and GPT-1145's top reagent
        // saving on every round. The dangerous chemistry round yields the chemical dataset.
        AIModelDefinition settledModel = taskModel();
        int amount = DEMO_OUTPUT_PER_ROUND +
                ModelRuntime.yieldBonus(settledModel, taskMode == 0 && dangerousRound);
        ItemStack output = new ItemStack(dangerousRound ?
                HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get() :
                HyperClusterItems.HIGH_PURITY_MINING_DATASET.get(), amount);
        insertOrDrop(output);
        this.taskActive = false;
        this.taskOrderId = "";
        this.taskOwnerUuid = "";
        this.taskPrice = 0;
        this.taskModelIndex = -1;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
    }

    /**
     * Cancels the active round: a HELD cloud order is released exactly once (精确释放预扣),
     * the session ends, and the consumed input returns to the input slot. Cancel is idempotent.
     */
    public void cancelTask() {
        if (!taskActive) {
            return;
        }
        if (getLevel() instanceof ServerLevel serverLevel && taskMode == 0 && !taskOrderId.isEmpty()) {
            CloudTokenLedger ledger = CloudTokenLedger.getOrCreate(serverLevel);
            ledger.release(taskOrderId); // only a HELD order is refunded — never a settled one
            UUID roundOwner = taskOwner();
            if (roundOwner != null) {
                ledger.endSession(roundOwner);
            }
            this.sessionHeld = false;
        }
        // The consumed input returns to the input slot (overflow drops above the controller).
        ItemStack input = new ItemStack(HyperClusterItems.CLEAN_DATASET.get());
        ItemStack existing = inputSlot.getStackInSlot(0);
        if (existing.isEmpty()) {
            inputSlot.setStackInSlot(0, input);
        } else if (ItemStack.isSameItemSameTags(existing, input) &&
                existing.getCount() < existing.getMaxStackSize()) {
                    existing.setCount(existing.getCount() + 1);
                } else {
                    getLevel().addFreshEntity(new ItemEntity(getLevel(), getPos().getX() + 0.5,
                            getPos().getY() + 1.5, getPos().getZ() + 0.5, input));
                }
        this.taskActive = false;
        this.taskOrderId = "";
        this.taskOwnerUuid = "";
        this.taskPrice = 0;
        this.taskModelIndex = -1;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
    }

    /** Inserts into the output slot; overflow drops above the controller (never silently voided). */
    protected void insertOrDrop(ItemStack stack) {
        ItemStack existing = outputSlot.getStackInSlot(0);
        if (existing.isEmpty()) {
            outputSlot.setStackInSlot(0, stack);
            return;
        }
        if (ItemStack.isSameItemSameTags(existing, stack) &&
                existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
            existing.setCount(existing.getCount() + stack.getCount());
            return;
        }
        getLevel().addFreshEntity(new ItemEntity(getLevel(), getPos().getX() + 0.5,
                getPos().getY() + 1.5, getPos().getZ() + 0.5, stack));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Card actions (UI buttons, executed server-side) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Deposit: the card's whole value moves into the bound account. */
    public void depositCard() {
        UUID owner = accountOwner();
        if (owner == null) {
            return;
        }
        ItemStack card = cardSlot.getStackInSlot(0);
        long value = TokenCreditCardItem.value(card);
        if (value <= 0) {
            return;
        }
        ledger().credit(owner, value);
        TokenCreditCardItem.setValue(card, 0);
    }

    /** Withdraw: the account's whole balance moves onto the card. */
    public void withdrawToCard() {
        UUID owner = accountOwner();
        if (owner == null) {
            return;
        }
        ItemStack card = cardSlot.getStackInSlot(0);
        if (card.isEmpty()) {
            return;
        }
        long balance = ledger().balance(owner);
        if (balance <= 0) {
            return;
        }
        if (ledger().debit(owner, balance)) {
            TokenCreditCardItem.setValue(card, TokenCreditCardItem.value(card) + balance);
        }
    }

    /** Cycles the inference tier: Medium -> Extra High -> Medium. */
    public void cycleTier() {
        this.selectedTier = (selectedTier + 1) % CloudPricing.InferenceTier.values().length;
    }

    /** Selects a specific catalog model by its definition (used by the UI and by tests). */
    public void selectModel(dev.hypercluster.common.model.AIModelDefinition definition) {
        int index = CloudModels.MODELS.indexOf(definition);
        if (index >= 0) {
            this.selectedModel = index;
        }
    }

    /** Cycles the selected cloud model. */
    public void cycleModel() {
        this.selectedModel = (selectedModel + 1) % CloudModels.MODELS.size();
    }

    public void setBatchMode(boolean batch) {
        this.batchMode = batch;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Render state & UI //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateRenderStateFor(WaitingReason reason) {
        boolean working = taskActive && reason == WaitingReason.NONE;
        MachineRenderState renderState = getRenderState();
        RecipeLogic.Status target = working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != target) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, target));
        }
    }

    @Override
    public Widget createUIWidget() {
        String uiKey = "hypercluster.machine.api_satellite_station.";
        int slotY = 13;
        int tokenY = 35;
        int settingsY = 57;
        // settings band 57..75, dangerous band 79..97 -> 101 leaves the 4 px bottom margin.
        WidgetGroup controls = MachineUi.multiblockControls(101);

        controls.addWidgets(
                MachineUi.label(0, 0, 42, uiKey + "label.credential"),
                MachineUi.label(46, 0, 44, uiKey + "label.sell"),
                MachineUi.label(94, 0, 42, uiKey + "label.input"),
                MachineUi.label(140, 0, 42, uiKey + "label.output"),
                controlSlot(credentialSlot, 12, slotY, true, uiKey + "credential_slot"),
                controlSlot(sellSlot, 59, slotY, true, uiKey + "sell_slot"),
                controlSlot(inputSlot, 106, slotY, true, uiKey + "input_slot"),
                controlSlot(outputSlot, 152, slotY, false, uiKey + "output_slot"));

        controls.addWidgets(
                controlSlot(cardSlot, 0, tokenY, true, uiKey + "card_slot"),
                MachineUi.label(22, tokenY + 4, 54, uiKey + "label.card"),
                controlButton(80, tokenY, 47, () -> Component.translatable(uiKey + "deposit"),
                        uiKey + "deposit.tooltip", this::depositCard),
                controlButton(131, tokenY, 51, () -> Component.translatable(uiKey + "withdraw"),
                        uiKey + "withdraw.tooltip", this::withdrawToCard));

        ToggleButtonWidget batch = new ToggleButtonWidget(0, settingsY, 18, 18, GuiTextures.BUTTON_BATCH,
                () -> batchMode, this::setBatchMode);
        batch.setHoverTooltips(Component.translatable(uiKey + "batch.tooltip"));
        controls.addWidgets(
                batch,
                controlButton(22, settingsY, 64,
                        () -> Component.translatable(uiKey + "tier." + selectedTier().name().toLowerCase(Locale.ROOT)),
                        uiKey + "tier.tooltip", this::cycleTier),
                controlButton(90, settingsY, 92, () -> Component.translatable(selectedModel().displayKey()),
                        uiKey + "model.tooltip", this::cycleModel));
        // 0.6.2 · the jailbreak card slot and the dangerous-round switch, on their own band so the
        // GPT refusal and the Grok bypass are both reachable from the controller UI.
        int dangerY = settingsY + 18 + 4;
        controls.addWidgets(
                controlSlot(jailbreakSlot, 0, dangerY, true, uiKey + "jailbreak_slot"),
                MachineUi.label(22, dangerY + 4, 54, uiKey + "label.jailbreak"),
                controlButton(80, dangerY, 102,
                        () -> Component.translatable(uiKey + (dangerousRound ? "dangerous.on" : "dangerous.off")),
                        uiKey + "dangerous.tooltip",
                        () -> {
                            this.dangerousRound = !this.dangerousRound;
                        }));
        return MachineUi.multiblockPage(this, controls);
    }

    private static SlotWidget controlSlot(CustomItemStackHandler inventory, int x, int y, boolean canInsert,
                                          String tooltipKey) {
        SlotWidget slot = new SlotWidget(inventory, 0, x, y, canInsert, true);
        slot.setBackground(GuiTextures.SLOT);
        slot.setHoverTooltips(Component.translatable(tooltipKey));
        return slot;
    }

    private static ButtonWidget controlButton(int x, int y, int width, Supplier<Component> label, String tooltipKey,
                                              Runnable action) {
        TextTexture text = new TextTexture(() -> label.get().getString())
                .setWidth(width - 6)
                .setType(TextTexture.TextType.ROLL_ALWAYS)
                .setDropShadow(false)
                .setColor(0xff1d252b);
        ButtonWidget button = new ButtonWidget(x, y, width, 18, new GuiTextureGroup(GuiTextures.BUTTON, text),
                click -> action.run());
        button.setHoverTooltips(Component.translatable(tooltipKey));
        return button;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(),
                        taskActive && waitingReason == WaitingReason.NONE)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        "hypercluster.machine.api_satellite_station.working")
                .addCustom(lines -> {
                    if (!isFormed() || !(getLevel() instanceof ServerLevel)) {
                        return;
                    }
                    if (waitingReason != WaitingReason.NONE) {
                        lines.add(Component
                                .translatable("hypercluster.machine.api_satellite_station.status." +
                                        waitingReason.name().toLowerCase(Locale.ROOT))
                                .withStyle(switch (waitingReason) {
                                    case LOW_POWER, RATE_LIMITED_429, MODEL_OFFLINE, INSUFFICIENT_FUNDS, SKY_BLOCKED, MORAL_REFUSAL, DANGEROUS_ROUND_UNSUPPORTED -> ChatFormatting.RED;
                                    default -> ChatFormatting.GRAY;
                                }));
                    }
                    CloudTokenLedger ledger = ledger();
                    UUID owner = accountOwner();
                    if (owner != null) {
                        lines.add(Component.translatable("hypercluster.machine.api_satellite_station.balance",
                                Component.literal(FormattingUtil.formatNumbers(ledger.balance(owner)))
                                        .withStyle(ChatFormatting.GOLD),
                                Component.literal(String.valueOf(ledger.accountTier(owner)))
                                        .withStyle(ChatFormatting.AQUA),
                                Component.literal(String.valueOf(
                                        CloudTokenLedger.slotsForSpent(ledger.totalSpent(owner))))
                                        .withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GRAY));
                    }
                    dev.hypercluster.common.model.AIModelDefinition model = selectedModel();
                    CloudModels.Lifecycle lifecycle = CloudModels.lifecycle(model, gameDay());
                    Component modelLine = Component
                            .translatable("hypercluster.machine.api_satellite_station.model_line",
                                    Component.translatable(model.displayKey()).withStyle(ChatFormatting.AQUA));
                    lines.add(lifecycle == CloudModels.Lifecycle.ACTIVE ? modelLine : Component.translatable(
                            "hypercluster.machine.api_satellite_station.model_line." +
                                    (lifecycle == CloudModels.Lifecycle.LEAVING_SOON ?
                                            "leaving_soon" : "retired"),
                            Component.translatable(model.displayKey()).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(
                                    CloudModels.leavingSoonCountdown(model, gameDay())))
                                    .withStyle(ChatFormatting.YELLOW)));
                    // 0.6.2 · what the selected family really does at runtime, in one line.
                    double liveWeather = ModelRuntime.effectiveWeather(model, CloudPricing.weatherAt(
                            getLevel(), hornPos()));
                    lines.add(Component.translatable(
                            "hypercluster.machine.api_satellite_station.family_line",
                            Component.literal(String.format(Locale.ROOT, "%.2f", liveWeather))
                                    .withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.valueOf(ModelRuntime.yieldBonus(model, dangerousRound)))
                                    .withStyle(ChatFormatting.AQUA),
                            Component.translatable(isJailbroken() ?
                                    "hypercluster.machine.api_satellite_station.jailbroken" :
                                    "hypercluster.machine.api_satellite_station.jailbreak_bare")
                                    .withStyle(isJailbroken() ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                            .withStyle(ChatFormatting.GRAY));
                    if (dangerousRound) {
                        lines.add(Component.translatable(
                                "hypercluster.machine.api_satellite_station.dangerous_line")
                                .withStyle(ModelRuntime.permitsDangerousRound(model, isJailbroken()) ?
                                        ChatFormatting.GOLD : ChatFormatting.RED));
                    }
                    if (taskActive) {
                        lines.add(Component.translatable(
                                taskMode == 1 ? "hypercluster.machine.api_satellite_station.round.fallback" :
                                        "hypercluster.machine.api_satellite_station.round.cloud",
                                Component.literal(FormattingUtil.formatNumbers(taskPrice))
                                        .withStyle(ChatFormatting.GOLD),
                                Component.literal(String.valueOf(taskTicksTotal - taskTicksRemaining))
                                        .withStyle(ChatFormatting.AQUA),
                                Component.literal(String.valueOf(taskTicksTotal))
                                        .withStyle(ChatFormatting.AQUA),
                                Component.literal(String.valueOf(Math.max(0, taskTicksRemaining)))
                                        .withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GRAY));
                    }
                })
                .addWorkingStatusLine();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Client-side WORKING ambience //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        BlockPos horn = hornPos();
        for (int i = 0; i < 3; i++) {
            double x = horn.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = horn.getY() + 1.05 + random.nextDouble() * 0.3;
            double z = horn.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            // SCRAPE and WAX_ON convert this to 0.12–0.20 blocks per tick.
            getLevel().addParticle(random.nextBoolean() ? ParticleTypes.SCRAPE : ParticleTypes.WAX_ON,
                    x, y, z, 0.0, 12.0 + random.nextDouble() * 8.0, 0.0);
        }
    }
}
