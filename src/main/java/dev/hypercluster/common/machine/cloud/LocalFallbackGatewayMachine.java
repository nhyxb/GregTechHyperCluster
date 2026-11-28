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

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import dev.hypercluster.common.machine.MachineUi;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import dev.hypercluster.common.model.ModelRuntime;

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
 *
 * <p>
 * <b>0.6.2 · the cluster really decides the fallback speed.</b> The gateway asks the machine room
 * whether a MoE router in range currently has {@code qwen_9187_pro_max_ultra} <b>online</b>:
 *
 * <ul>
 * <li><b>Qwen online</b> — the local run degrades only <b>×1.5</b>, because the open-weight
 * flagship really serves the same domains locally;</li>
 * <li><b>no Qwen deployed, or it went offline (OOM / evicted / pulled)</b> — the model-less
 * <b>×4</b> of the 0.4 contract.</li>
 * </ul>
 *
 * <p>
 * The lookup is live: pulling the weights or starving the memory pool drops the gateway back to
 * ×4 on the very next round, and no cached "was online once" flag can hide that.
 */
public class LocalFallbackGatewayMachine extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LocalFallbackGatewayMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    /** Chebyshev reach (blocks) inside which the gateway looks for a Qwen-serving MoE router. */
    public static final int ROUTER_RANGE = 16;

    /** The model whose online presence upgrades the fallback from x4 to x1.5. */
    public static final String ACCELERATING_MODEL_ID = "qwen_9187_pro_max_ultra";

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

    /**
     * True when a MoE router in range currently holds {@code qwen_9187_pro_max_ultra}
     * <b>{@code ONLINE}</b>. The check is live every time it is asked: a pulled module, an OOM or
     * a dimmed memory cabinet answers false immediately.
     */
    public boolean isQwenOnline() {
        return qwenRouter() != null;
    }

    /** The nearest router really serving Qwen, or null. */
    @javax.annotation.Nullable
    public MoeRouterRackMachine qwenRouter() {
        if (getLevel() == null || getLevel().isClientSide()) {
            return null;
        }
        int r = ROUTER_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(getPos().offset(-r, -r, -r), getPos().offset(r, r, r))) {
            BlockEntity be = getLevel().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                    machineBE.getMetaMachine() instanceof MoeRouterRackMachine router &&
                    router.getState() == MoeRouterRackMachine.State.ONLINE &&
                    router.loadedModel() != null &&
                    ACCELERATING_MODEL_ID.equals(router.loadedModel().id().getPath())) {
                return router;
            }
        }
        return null;
    }

    /**
     * The fallback slowdown this gateway must apply right now: {@code ×1.5} with Qwen online,
     * {@code ×4} otherwise. The single place the station asks, so the two can never disagree.
     */
    public double fallbackSlowdown() {
        return ModelRuntime.fallbackSlowdown(isQwenOnline());
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
                    boolean qwen = isQwenOnline();
                    lines.add(Component.translatable(
                            uiKey + (qwen ? "speed.accelerated" : "speed.bare"),
                            String.format(java.util.Locale.ROOT, "%.1f", fallbackSlowdown())));
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
