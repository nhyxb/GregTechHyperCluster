package dev.hypercluster.common.machine.cloud;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.common.machine.MachineUi;

/**
 * 0.4 · the local fallback gateway ({@code hypercluster:local_fallback_gateway}).
 *
 * <p>
 * An IV-tier passive multiblock part of the API satellite station: attach (at most) one to the
 * station's frame and it stands by as the local escape hatch of the closed-source cloud. When a
 * cloud round is blocked by a 429 concurrency limit, an insufficient Token balance or a model
 * whose lifecycle went offline (Leaving Soon countdown expired), the station switches that
 * round into <b>local degraded-speed fallback</b> and the gateway keeps the line alive — the
 * same task runs to completion locally at ×4 duration, powered by local EU instead of Tokens
 * (no billing, no session). The station never hard-stalls (避免产线硬卡死).
 *
 * <p>
 * A physically blocked uplink (feed horn occluded) is <b>not</b> a fallback trigger — with no
 * line to the cloud at all there is nothing to fall back from; the station simply refuses new
 * rounds until the horn sees sky again.
 */
public class LocalFallbackGatewayMachine extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LocalFallbackGatewayMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    /** Fallback rounds the gateway has carried to completion since it was attached. */
    @Persisted
    @DescSynced
    protected long fallbackRounds = 0;

    public LocalFallbackGatewayMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** Called by the host station when a fallback round this gateway carried completes. */
    public void recordFallbackRound() {
        this.fallbackRounds++;
    }

    public long getFallbackRounds() {
        return fallbackRounds;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return true;
    }

    @Override
    public Widget createUIWidget() {
        String uiKey = "hypercluster.machine.local_fallback_gateway.";
        WidgetGroup group = MachineUi.panel(190, 88);
        group.addWidget(new ImageWidget((group.getSize().width - 20) / 2, 6, 20, 20, getTabIcon()));
        group.addWidget(MachineUi.display(4, 30, group.getSize().width - 8, 54, GuiTextures.DISPLAY,
                MachineUi.statusText(getLevel().isClientSide, lines -> {
                    lines.add(Component.translatable(uiKey + (isFormed() ? "standby" : "idle")));
                    lines.add(Component.translatable(uiKey + "rounds", fallbackRounds));
                }).setCenter(true)));
        return group;
    }

    @Override
    public void addedToController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.addedToController(controller);
    }

    @Override
    public void removedFromController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.removedFromController(controller);
    }
}
