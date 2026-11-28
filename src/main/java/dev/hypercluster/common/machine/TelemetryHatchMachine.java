package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/** HV export bus with a separate withdraw-only buffer for settled recipes' domain logs. */
public class TelemetryHatchMachine extends ItemBusPartMachine implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TelemetryHatchMachine.class, ItemBusPartMachine.MANAGED_FIELD_HOLDER);

    /** Same buffer depth as the own telemetry demonstrators. */
    public static final int TELEMETRY_BUFFER_CAPACITY = 64;

    /** The buffer holds one domain's logs; the other domain's logs wait for it to drain. */
    @Persisted
    protected final CustomItemStackHandler telemetryBuffer;

    @Nullable
    protected TickableSubscription tickSubs;

    private final List<ISubscription> outputObservers = new ArrayList<>();

    public TelemetryHatchMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, com.gregtechceu.gtceu.api.GTValues.HV, IO.OUT);
        this.telemetryBuffer = new CustomItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return TELEMETRY_BUFFER_CAPACITY;
            }
        };
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Telemetry buffer (identical rules to TelemetryMachine) //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Deposits up to {@code amount} logs; anything past the capacity is dropped. */
    public int depositTelemetryLogs(Item logItem, int amount) {
        if (logItem == null || amount <= 0) {
            return 0;
        }
        ItemStack stored = telemetryBuffer.getStackInSlot(0);
        if (!stored.isEmpty() && !stored.is(logItem)) {
            return 0; // single-slot buffer already holds the other domain's logs
        }
        int count = stored.isEmpty() ? 0 : stored.getCount();
        int deposited = Math.min(amount, TELEMETRY_BUFFER_CAPACITY - count);
        if (deposited > 0) {
            ItemStack updated = stored.isEmpty() ? new ItemStack(logItem) : stored.copy();
            updated.setCount(count + deposited);
            telemetryBuffer.setStackInSlot(0, updated);
        }
        return deposited;
    }

    public int getTelemetryBufferCount() {
        return telemetryBuffer.getStackInSlot(0).getCount();
    }

    public CustomItemStackHandler getTelemetryBuffer() {
        return telemetryBuffer;
    }

    public boolean isTelemetryBufferFull() {
        return getTelemetryBufferCount() >= TELEMETRY_BUFFER_CAPACITY;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(telemetryBuffer);
        super.onMachineRemoved();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Settlement: the vanilla afterWorking part hook, zero mixins //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean afterWorking(IWorkableMultiController controller) {
        if (getLevel() != null && !isRemote() && TelemetrySettlement.shouldBook(this, controller)) {
            observeHostOutput(controller);
        }
        return super.afterWorking(controller);
    }

    private void observeHostOutput(IWorkableMultiController host) {
        var logic = host.getRecipeLogic();
        GTRecipe finished = logic.getLastRecipe();
        // GT calls afterWorking both before the actual output write and when interrupting.
        if (finished == null || logic.getDuration() <= 0 || logic.getProgress() < logic.getDuration()) {
            return;
        }
        Item log = TelemetrySettlement.logItemOf(TelemetrySettlement.domainOf(finished.getType()));
        if (log == null || !RecipeHelper.handleRecipe(new PhysicalOutputs(host), finished, IO.OUT, finished.outputs,
                Collections.emptyMap(), false, true).isSuccess()) {
            return;
        }
        var items = regularOutputs(finished, ItemRecipeCapability.CAP);
        var fluids = regularOutputs(finished, FluidRecipeCapability.CAP);
        if (items.isEmpty() && fluids.isEmpty()) {
            return;
        }
        // Notifications run synchronously inside the native output write, before auto-export.
        boolean[] recorded = { false };
        for (var capability : List.of(ItemRecipeCapability.CAP, FluidRecipeCapability.CAP)) {
            var outputs = capability == ItemRecipeCapability.CAP ? items : fluids;
            if (outputs.isEmpty()) {
                continue;
            }
            for (var handler : host.getCapabilitiesFlat(IO.OUT, capability)) {
                if (handler instanceof NotifiableRecipeHandlerTrait<?> observable) {
                    long before = matchingAmount(capability, outputs, handler.getContents());
                    outputObservers.add(observable.addChangedListener(() -> {
                        if (!recorded[0] && logic.getLastRecipe() == finished && logic.getDuration() > 0 &&
                                logic.getProgress() >= logic.getDuration() &&
                                matchingAmount(capability, outputs, handler.getContents()) > before) {
                            recorded[0] = true;
                            depositTelemetryLogs(log, 1);
                        }
                    }));
                }
            }
        }
        if (!outputObservers.isEmpty()) {
            tickSubs = subscribeServerTick(tickSubs, this::clearOutputObservers);
        }
    }

    /** A jammed bus never earns telemetry, even when the controller may void its excess. */
    private record PhysicalOutputs(IWorkableMultiController host) implements IRecipeCapabilityHolder {

        @Override
        public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
            return host.getCapabilitiesProxy();
        }

        @Override
        public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
            return host.getCapabilitiesFlat();
        }
    }

    private static List<Content> regularOutputs(GTRecipe recipe, RecipeCapability<?> capability) {
        return recipe.getOutputContents(capability).stream().filter(content -> content.chance == content.maxChance &&
                content.chance > 0).toList();
    }

    private static long matchingAmount(RecipeCapability<?> capability, List<Content> outputs, List<Object> contents) {
        long amount = 0;
        for (var content : contents) {
            if (capability == ItemRecipeCapability.CAP && content instanceof ItemStack stack &&
                    outputs.stream().anyMatch(output -> ItemRecipeCapability.CAP.of(output.content).test(stack))) {
                amount += stack.getCount();
            } else if (capability == FluidRecipeCapability.CAP && content instanceof FluidStack stack &&
                    outputs.stream().anyMatch(output -> FluidRecipeCapability.CAP.of(output.content).test(stack))) {
                        amount += stack.getAmount();
                    }
        }
        return amount;
    }

    private void clearOutputObservers() {
        outputObservers.forEach(ISubscription::unsubscribe);
        outputObservers.clear();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    @Override
    public void onUnload() {
        clearOutputObservers();
        super.onUnload();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // UI: the export-bus template plus a withdraw-only telemetry buffer slot //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        Widget bus = super.createUIWidget();
        WidgetGroup group = MachineUi.panel(164, 70 + bus.getSize().height);
        group.addWidget(MachineUi.label(16, 21, 132, "hypercluster.machine.hv_telemetry_hatch.label.buffer"));
        SlotWidget buffer = new SlotWidget(telemetryBuffer, 0, (group.getSize().width - 18) / 2, 35, true, true);
        buffer.setBackground(GuiTextures.SLOT);
        buffer.setCanPutItems(false);
        buffer.setCanTakeItems(true);
        buffer.setOnAddedTooltips((slot, tooltips) -> {
            // Multiplayer fix: the count must come from the slot's own synced stack.
            int displayed = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
            tooltips.add(Component.translatable("hypercluster.telemetry_buffer.tooltip",
                    displayed, TELEMETRY_BUFFER_CAPACITY));
            if (displayed >= TELEMETRY_BUFFER_CAPACITY && !slot.getItem().isEmpty()) {
                tooltips.add(Component.translatable("hypercluster.telemetry_buffer.full"));
            }
        });
        group.addWidget(buffer);
        bus.setSelfPosition(new Position((group.getSize().width - bus.getSize().width) / 2, 62));
        group.addWidget(bus);
        return group;
    }
}
