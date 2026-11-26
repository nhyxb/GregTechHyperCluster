package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.EditableUI;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Tables;
import dev.hypercluster.common.machine.logic.TelemetryRecipeLogic;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Common base of the own telemetry demonstration machines (0.1 HV washer + 0.2 EV chemical
 * reactor).
 *
 * <p>
 * Two additions over a plain {@link SimpleTieredMachine}, shared by every telemetry demonstrator:
 * <ul>
 * <li>a per-machine {@code createRecipeLogic} override returning {@link TelemetryRecipeLogic},
 * which only ever accounts a batch after its outputs really settled (no mixins, no afterWorking
 * counting);</li>
 * <li>a persisted, capacity-limited telemetry log buffer for the machine's domain log item
 * ({@code Supplier<Item> logItem}). Every settled batch deposits exactly one log. The buffer is a
 * single withdraw-only GUI slot with room for one full stack (64). While full the machine keeps
 * working and extra logs are dropped. Removing the machine returns the buffered logs to the world
 * once; a re-placed machine starts with an empty buffer.</li>
 * </ul>
 */
public abstract class TelemetryMachine extends SimpleTieredMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TelemetryMachine.class, SimpleTieredMachine.MANAGED_FIELD_HOLDER);

    public static final int TELEMETRY_BUFFER_CAPACITY = 64;

    /** The domain log item this demonstrator deposits (mining log / chemical log / ...). */
    protected final Supplier<Item> logItem;

    /** Persisted buffer of domain telemetry logs; a single withdraw-only GUI slot. */
    @Persisted
    protected final CustomItemStackHandler telemetryBuffer;

    protected TelemetryMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction,
                               Supplier<Item> logItem, Object... args) {
        super(holder, tier, tankScalingFunction, args);
        this.logItem = logItem;
        this.telemetryBuffer = new CustomItemStackHandler() {

            @Override
            public int getSlotLimit(int slot) {
                return TELEMETRY_BUFFER_CAPACITY;
            }
        };
        this.telemetryBuffer.setFilter(stack -> stack.is(logItem.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new TelemetryRecipeLogic(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Telemetry buffer //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Deposits up to {@code amount} domain logs; anything past the capacity is dropped and uncounted. */
    public int depositTelemetryLogs(int amount) {
        ItemStack stored = telemetryBuffer.getStackInSlot(0);
        int room = stored.isEmpty() ? TELEMETRY_BUFFER_CAPACITY : TELEMETRY_BUFFER_CAPACITY - stored.getCount();
        int deposited = Math.min(amount, room);
        if (deposited > 0) {
            ItemStack updated = stored.isEmpty() ? new ItemStack(logItem.get()) : stored.copy();
            updated.setCount(stored.getCount() + deposited);
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
        super.onMachineRemoved();
        // Return buffered logs once; a re-placed machine starts with an empty buffer.
        clearInventory(telemetryBuffer);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Machine UI: the simple tiered template plus a withdraw-only telemetry buffer slot //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Creates the EditableMachineUI factory for a telemetry demonstrator (template + buffer slot). */
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> telemetryEditableUi(String key) {
        return Util.memoize((path, recipeType) -> new EditableMachineUI(key, path, () -> {
            var template = recipeType.getRecipeUI().createEditableUITemplate(false, false).createDefault();
            SlotWidget batterySlot = SimpleTieredMachine.createBatterySlot().createDefault();
            SlotWidget bufferSlot = createTelemetryBufferSlot().createDefault();
            var group = new com.lowdragmc.lowdraglib.gui.widget.WidgetGroup(0, 0, template.getSize().width,
                    Math.max(template.getSize().height, 78));
            template.setSelfPosition(new Position(0, (group.getSize().height - template.getSize().height) / 2));
            batterySlot.setSelfPosition(new Position(group.getSize().width / 2 - 9, group.getSize().height - 18));
            bufferSlot.setSelfPosition(new Position(group.getSize().width / 2 + 13, group.getSize().height - 18));
            group.addWidget(batterySlot);
            group.addWidget(bufferSlot);
            group.addWidget(template);
            return group;
        }, (template, machine) -> {
            if (machine instanceof TelemetryMachine demonstrator) {
                var storages = Tables.<IO, com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>, Object>newCustomTable(
                        new EnumMap<>(IO.class), LinkedHashMap::new);
                storages.put(IO.IN, ItemRecipeCapability.CAP, demonstrator.importItems.storage);
                storages.put(IO.OUT, ItemRecipeCapability.CAP, demonstrator.exportItems.storage);
                storages.put(IO.IN, FluidRecipeCapability.CAP, demonstrator.importFluids);
                storages.put(IO.OUT, FluidRecipeCapability.CAP, demonstrator.exportFluids);
                storages.put(IO.IN, CWURecipeCapability.CAP, demonstrator.importComputation);
                storages.put(IO.OUT, CWURecipeCapability.CAP, demonstrator.exportComputation);

                var machineRecipeType = demonstrator.getRecipeType();
                machineRecipeType.getRecipeUI().createEditableUITemplate(false, false).setupUI(template,
                        new GTRecipeTypeUI.RecipeHolder(demonstrator.recipeLogic::getProgressPercent,
                                storages, new CompoundTag(), Collections.emptyList(), false, false));
                SimpleTieredMachine.createBatterySlot().setupUI(template, demonstrator);
                createTelemetryBufferSlot().setupUI(template, demonstrator);
            }
        }));
    }

    private static EditableUI<SlotWidget, TelemetryMachine> createTelemetryBufferSlot() {
        return new EditableUI<>("telemetry_buffer_slot", SlotWidget.class, SlotWidget::new,
                (slotWidget, machine) -> {
                    // Plain slot background: the circuit overlay confused players with the
                    // programmed circuit slot (P05 UI fix).
                    slotWidget.setBackground(GuiTextures.SLOT);
                    slotWidget.setHandlerSlot(machine.telemetryBuffer, 0);
                    slotWidget.setCanPutItems(false);
                    slotWidget.setCanTakeItems(true);
                    slotWidget.setOnAddedTooltips((slot, tooltips) -> {
                        // P05 multiplayer fix: tooltips render on the client; the count must come
                        // from the slot's own synced stack, not from the server-side machine.
                        int displayed = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
                        tooltips.add(Component.translatable("hypercluster.telemetry_buffer.tooltip",
                                displayed, TELEMETRY_BUFFER_CAPACITY));
                        if (displayed >= TELEMETRY_BUFFER_CAPACITY && !slot.getItem().isEmpty()) {
                            tooltips.add(Component.translatable("hypercluster.telemetry_buffer.full"));
                        }
                    });
                });
    }
}
