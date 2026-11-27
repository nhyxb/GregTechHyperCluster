package dev.hypercluster.common.machine.training;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.common.cloud.CloudTokenLedger;
import dev.hypercluster.common.item.CredentialCardItem;
import dev.hypercluster.common.item.HyperClusterItems;
import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.datacenter.LiquidCooledComputeRackMachine;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * 0.6 · cloud compute offload node ({@code hypercluster:cloud_offload_node}).
 *
 * <p>
 * The economic bridge that closes the loop "open-source datacenter feeds the closed-source API":
 * an LuV optical part attached to a {@link LiquidCooledComputeRackMachine}'s structure, carrying a
 * player's {@code hypercluster:api_credential_card}. Every tick it <b>really rents</b>
 * {@link TrainingRating#OFFLOAD_MAX_CWUT}-capped CWU/t out of the host rack's spare optical
 * computation and pays the bound player's cloud account integer Tokens at
 * {@code CWU/t × (Tier III PUE / room PUE)} — a cooler room earns more for the same silicon.
 *
 * <p>
 * <b>Strict anti-double-spend.</b> The rented CWU/t is a real allocation in the host rack's
 * same-tick budget ({@code requestCWUt(..., simulate=false)}), so a research station or a
 * pretraining cluster consuming the same rack in the same tick can only ever get the remainder.
 * Real allocations are released at the end of the tick by the rack's own budget window; nothing
 * is cached here, so a cut cable, a coolant loss, a structure break or a chunk reload all stop
 * the payout on the very next tick.
 */
public class CloudOffloadNodeMachine extends MultiblockPartMachine implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CloudOffloadNodeMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    /** Credential card slot: which cloud account the payout goes to. */
    @Persisted
    protected final com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler credentialSlot;

    /** CWU/t actually rented on the last settled tick. */
    @Persisted
    @DescSynced
    protected int rentedCWUt = 0;
    /** Tokens paid on the last settled tick. */
    @Persisted
    @DescSynced
    protected long lastPayout = 0;
    /** Lifetime Tokens this node has paid out. */
    @Persisted
    @DescSynced
    protected long lifetimeTokens = 0;
    /** Lifetime CWU/t·ticks rented. */
    @Persisted
    @DescSynced
    protected long lifetimeCwuTicks = 0;
    /** Why the node is not renting right now. */
    @DescSynced
    protected String idleReason = "no_credential";

    @javax.annotation.Nullable
    protected TickableSubscription tickSubs;

    public CloudOffloadNodeMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
        this.credentialSlot = new com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler(1);
        this.credentialSlot.setFilter(stack -> stack.is(HyperClusterItems.API_CREDENTIAL_CARD.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addedToController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.addedToController(controller);
        this.tickSubs = subscribeServerTick(this.tickSubs, this::tick);
    }

    @Override
    public void removedFromController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.removedFromController(controller);
        stopRenting("detached");
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        stopRenting("unloaded");
        if (this.tickSubs != null) {
            this.tickSubs.unsubscribe();
            this.tickSubs = null;
        }
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return true;
    }

    public com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler getCredentialSlot() {
        return credentialSlot;
    }

    public int getRentedCWUt() {
        return rentedCWUt;
    }

    public long getLastPayout() {
        return lastPayout;
    }

    public long getLifetimeTokens() {
        return lifetimeTokens;
    }

    public String getIdleReason() {
        return idleReason;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // The rental tick //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void tick() {
        if (getLevel() == null || getLevel().isClientSide() || !isFormed()) {
            stopRenting("unformed");
            return;
        }
        UUID owner = CredentialCardItem.owner(credentialSlot.getStackInSlot(0));
        if (owner == null) {
            stopRenting("no_credential");
            return;
        }
        LiquidCooledComputeRackMachine host = hostRack();
        if (host == null) {
            stopRenting("no_host");
            return;
        }
        IOpticalComputationProvider link = hostReceiver();
        if (link == null) {
            stopRenting("no_link");
            return;
        }
        // Rent for real: a real allocation is booked in the host's same-tick budget, so no other
        // consumer of this tick can be handed the same CWU/t.
        int available = link.requestCWUt(TrainingRating.OFFLOAD_MAX_CWUT, true);
        int rentable = TrainingRating.rentableCWUt(available, TrainingRating.OFFLOAD_MAX_CWUT);
        if (rentable <= 0) {
            stopRenting("no_computation");
            return;
        }
        int granted = link.requestCWUt(rentable, false);
        if (granted <= 0) {
            stopRenting("no_computation");
            return;
        }
        this.rentedCWUt = granted;
        this.lifetimeCwuTicks += granted;
        long payout = TrainingRating.offloadTokensPerTick(granted, host.getPUE());
        this.lastPayout = payout;
        this.lifetimeTokens += payout;
        this.idleReason = "renting";
        if (getLevel() instanceof ServerLevel serverLevel) {
            CloudTokenLedger.getOrCreate(serverLevel).credit(owner, payout);
        }
    }

    protected void stopRenting(String reason) {
        this.rentedCWUt = 0;
        this.lastPayout = 0;
        this.idleReason = reason;
    }

    /** The host liquid rack this node is a part of, or null. */
    @Nullable
    protected LiquidCooledComputeRackMachine hostRack() {
        for (var controller : getControllers()) {
            if (controller instanceof LiquidCooledComputeRackMachine rack) {
                return rack;
            }
        }
        return null;
    }

    /** The optical receiver feeding this node (a computation reception hatch on the same frame). */
    @Nullable
    protected IOpticalComputationProvider hostReceiver() {
        for (var controller : getControllers()) {
            for (var part : controller.getParts()) {
                if (part != this && part instanceof IOpticalComputationReceiver receiver) {
                    IOpticalComputationProvider provider = receiver.getComputationProvider();
                    if (provider != null) {
                        return provider;
                    }
                }
            }
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // UI //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        String uiKey = "hypercluster.machine.cloud_offload_node.";
        WidgetGroup group = MachineUi.panel(190, 96);
        group.addWidget(MachineUi.label(14, 16, 88, uiKey + "label.credential"));
        SlotWidget slot = new SlotWidget(credentialSlot, 0, 12, 30, true, true);
        slot.setBackground(GuiTextures.SLOT);
        slot.setHoverTooltips(Component.translatable(uiKey + "credential_slot"));
        group.addWidget(slot);
        group.addWidget(MachineUi.display(108, 16, 74, 74, GuiTextures.DISPLAY,
                MachineUi.statusText(getLevel().isClientSide, lines -> {
                    lines.add(Component.translatable(uiKey + "renting",
                            Component.literal(String.valueOf(rentedCWUt)).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "payout",
                            Component.literal(String.format(Locale.ROOT, "%,d", lastPayout))
                                    .withStyle(ChatFormatting.GOLD))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "lifetime",
                            Component.literal(String.format(Locale.ROOT, "%,d", lifetimeTokens))
                                    .withStyle(ChatFormatting.GOLD))
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable(uiKey + "reason." + idleReason)
                            .withStyle(("renting".equals(idleReason) || "standby".equals(idleReason)) ?
                                    ChatFormatting.GREEN : ChatFormatting.RED));
                }).setCenter(true)));
        return group;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(credentialSlot);
    }
}
