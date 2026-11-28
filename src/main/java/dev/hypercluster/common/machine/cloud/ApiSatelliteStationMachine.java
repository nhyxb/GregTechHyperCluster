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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.client.sound.MachineLoopSounds;
import dev.hypercluster.common.cloud.CloudModels;
import dev.hypercluster.common.cloud.CloudPricing;
import dev.hypercluster.common.cloud.CloudTokenLedger;
import dev.hypercluster.common.data.HyperClusterSounds;
import dev.hypercluster.common.item.CredentialCardItem;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.item.TokenCreditCardItem;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.MultiblockItemBuses;
import dev.hypercluster.common.sound.MachineAlerts;

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
        OUTPUT_BLOCKED,
        SKY_BLOCKED,
        MODEL_OFFLINE,
        RATE_LIMITED_429,
        INSUFFICIENT_FUNDS,
        LOW_POWER,
        PAUSED
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // GUI slots (all server-side; the ledger is the single source of truth) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** The bound API credential card — whose account this station operates on. */
    @Persisted
    protected final CustomItemStackHandler credentialSlot;
    /** Input retained for worlds with datasets previously uploaded through the controller. */
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
    @Persisted
    protected int refundedDatasets;

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

    /** Runtime: does this machine currently hold one cloud session of the account (429 gate). */
    protected boolean sessionHeld = false;

    @Nullable
    protected TickableSubscription tickSubs;

    /** UI-live round-refusal status (synced, never persisted — a restart starts with NONE). */
    @DescSynced
    protected WaitingReason waitingReason = WaitingReason.NO_CREDENTIAL;

    /** 0.6.3: stamp of the last terminal_alert cue, throttled by {@link MachineAlerts}. */
    protected long lastAlertTick = MachineAlerts.NEVER;
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

    public dev.hypercluster.common.model.AIModelDefinition selectedModel() {
        return CloudModels.byIndex(selectedModel);
    }

    public boolean isFallbackTask() {
        return taskActive && taskMode == 1;
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

    public CustomItemStackHandler getCardSlot() {
        return cardSlot;
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
        if (isRemote()) {
            MachineLoopSounds.stop(this);
        }
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
        if (refundedDatasets > 0) {
            var recovery = new CustomItemStackHandler(1);
            recovery.setStackInSlot(0, new ItemStack(HyperClusterItems.CLEAN_DATASET.get(), refundedDatasets));
            clearInventory(recovery);
            refundedDatasets = 0;
        }
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
        ItemStack legacyOutput = outputSlot.getStackInSlot(0);
        if (!legacyOutput.isEmpty() && MultiblockItemBuses.export(this, legacyOutput.copyWithCount(1), false)) {
            outputSlot.extractItem(0, 1, false);
        }

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

    protected void autoSell(UUID owner, CloudTokenLedger ledger) {
        ItemStack legacy = sellSlot.extractItem(0, 64, false);
        if (!legacy.isEmpty()) {
            creditSale(owner, ledger, legacy);
        }

        ItemStack stack;
        while (!(stack = MultiblockItemBuses.take(this,
                candidate -> candidate.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ||
                        candidate.is(HyperClusterItems.HIGH_PURITY_CHEMICAL_DATASET.get()),
                64, false)).isEmpty()) {
            creditSale(owner, ledger, stack);
        }
    }

    private void creditSale(UUID owner, CloudTokenLedger ledger, ItemStack stack) {
        long pricePer = stack.is(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get()) ?
                CloudPricing.MINING_DATASET_SALE_PRICE : CloudPricing.CHEMICAL_DATASET_SALE_PRICE;
        ledger.credit(owner, pricePer * stack.getCount());
    }

    /** One working tick of the active round: EU first, then progress, then completion. */
    protected void progressRound(CloudTokenLedger ledger) {
        if (!recipeLogic.isWorkingEnabled()) {
            waitingReason = WaitingReason.PAUSED;
            updateRenderStateFor(waitingReason);
            return;
        }
        if (taskTicksRemaining <= 0) {
            finishRound(ledger);
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
        if (!hasDemoInput()) {
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
        double weather = CloudPricing.weatherAt(getLevel(), horn);
        boolean highAlt = CloudPricing.highAltitudeBonus(getPos());
        dev.hypercluster.common.model.AIModelDefinition model = selectedModel();
        CloudPricing.InferenceTier tier = selectedTier();

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

    private boolean hasDemoInput() {
        return refundedDatasets > 0 || !inputSlot.getStackInSlot(0).isEmpty() ||
                !demoBusInput(true).isEmpty();
    }

    protected void consumeInput() {
        if (refundedDatasets > 0) {
            refundedDatasets--;
        } else if (inputSlot.extractItem(0, 1, false).isEmpty()) {
            demoBusInput(false);
        }
    }

    private ItemStack demoBusInput(boolean simulate) {
        return MultiblockItemBuses.take(this, stack -> stack.is(HyperClusterItems.CLEAN_DATASET.get()), 1, simulate);
    }

    protected void startCloudRound(UUID owner, String orderId, long price, String cacheKey,
                                   boolean cacheHit, int ticks) {
        this.taskActive = true;
        this.taskMode = 0;
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

    /** The gateway takes over: same task, ×4 duration, local EU, no Token billing. */
    protected void startFallbackRound(UUID owner, double weather, boolean highAlt) {
        consumeInput();
        this.taskActive = true;
        this.taskMode = 1;
        this.taskOrderId = "";
        this.taskOwnerUuid = owner.toString();
        this.taskPrice = 0;
        this.taskCacheKey = "";
        this.taskCacheHit = false;
        this.taskTicksTotal = CloudPricing.demoTaskTicks(CloudPricing.InferenceTier.MEDIUM, batchMode,
                weather, highAlt, true);
        this.taskTicksRemaining = this.taskTicksTotal;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
    }

    /** The round completed: settle the order (cloud) or record the fallback round. */
    protected void finishRound(CloudTokenLedger ledger) {
        ItemStack output = new ItemStack(HyperClusterItems.HIGH_PURITY_MINING_DATASET.get(),
                DEMO_OUTPUT_PER_ROUND);
        if (!MultiblockItemBuses.export(this, output, false)) {
            waitingReason = WaitingReason.OUTPUT_BLOCKED;
            updateRenderStateFor(waitingReason);
            return;
        }
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
        this.taskActive = false;
        this.taskOrderId = "";
        this.taskOwnerUuid = "";
        this.taskPrice = 0;
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
        // Keep the refund in saved state if import is full or the part is detached.
        if (!MultiblockItemBuses.returnInput(this, new ItemStack(HyperClusterItems.CLEAN_DATASET.get()))) {
            refundedDatasets++;
        }
        this.taskActive = false;
        this.taskOrderId = "";
        this.taskOwnerUuid = "";
        this.taskPrice = 0;
        this.waitingReason = WaitingReason.NONE;
        updateRenderStateFor(waitingReason);
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
        // 0.6.3: one terminal_alert per throttle window while the uplink is blocked by 429
        // concurrency or a retired model. Every state transition funnels through this method, so
        // the cue cannot be missed by a path that forgot to report it.
        if (reason == WaitingReason.RATE_LIMITED_429 || reason == WaitingReason.MODEL_OFFLINE) {
            this.lastAlertTick = MachineAlerts.play(getLevel(), getPos(), this.lastAlertTick,
                    getOffsetTimer());
        }
    }

    @Override
    public Widget createUIWidget() {
        String uiKey = "hypercluster.machine.api_satellite_station.";
        int tokenY = 13;
        int settingsY = 35;
        WidgetGroup controls = MachineUi.multiblockControls(53);

        controls.addWidgets(
                MachineUi.label(0, 0, 66, uiKey + "label.credential"),
                controlSlot(credentialSlot, 12, tokenY, true, uiKey + "credential_slot"),
                MachineUi.label(70, 0, 58, uiKey + "label.card"),
                controlSlot(cardSlot, 70, tokenY, true, uiKey + "card_slot"),
                controlButton(92, tokenY, 42, () -> Component.translatable(uiKey + "deposit"),
                        uiKey + "deposit.tooltip", this::depositCard),
                controlButton(138, tokenY, 44, () -> Component.translatable(uiKey + "withdraw"),
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
                                    case LOW_POWER, RATE_LIMITED_429, MODEL_OFFLINE, INSUFFICIENT_FUNDS, SKY_BLOCKED -> ChatFormatting.RED;
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
        // 0.6.3: the work loop. These machines run DUMMY_RECIPES, so RecipeLogic never reports
        // them as working and GTCEu's own sound hook stays silent; drive the same
        // AutoReleasedSound mechanism explicitly instead.
        MachineLoopSounds.tick(this, HyperClusterSounds.SATELLITE_UPLINK);
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
