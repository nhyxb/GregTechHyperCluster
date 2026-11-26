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
import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Tables;
import dev.hypercluster.common.item.HyperClusterItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;

/**
 * The own HV telemetry ore washer (P04).
 *
 * <p>
 * A {@link SimpleTieredMachine} at HV tier with two additions over the plain GT washer:
 * <ul>
 * <li>a per-machine {@code createRecipeLogic} override returning
 * {@link dev.hypercluster.common.machine.logic.TelemetryRecipeLogic}, which only ever accounts a
 * batch after its outputs really settled (no mixins, no afterWorking counting);</li>
 * <li>a persisted, capacity-limited telemetry log buffer: every settled batch deposits one
 * {@code hypercluster:mining_telemetry_log}. The buffer is a single withdraw-only GUI slot with
 * room for one full stack (64). While full the machine keeps working and extra logs are dropped.
 * Removing the machine returns the buffered logs to the world once; a re-placed machine starts
 * with an empty buffer.</li>
 * </ul>
 */
public class TelemetryOreWasherMachine extends SimpleTieredMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TelemetryOreWasherMachine.class, SimpleTieredMachine.MANAGED_FIELD_HOLDER);

    public static final int TELEMETRY_BUFFER_CAPACITY = 64;

    /** Persisted buffer of mining telemetry logs; a single withdraw-only GUI slot. */
    @Persisted
    protected final CustomItemStackHandler telemetryBuffer;

    public TelemetryOreWasherMachine(IMachineBlockEntity holder, int tier,
                                     it.unimi.dsi.fastutil.ints.Int2IntFunction tankScalingFunction, Object... args) {
        super(holder, tier, tankScalingFunction, args);
        this.telemetryBuffer = new CustomItemStackHandler() {

            @Override
            public int getSlotLimit(int slot) {
                return TELEMETRY_BUFFER_CAPACITY;
            }
        };
        this.telemetryBuffer.setFilter(stack -> stack.is(HyperClusterItems.MINING_TELEMETRY_LOG.get()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new dev.hypercluster.common.machine.logic.TelemetryRecipeLogic(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Telemetry buffer //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Deposits up to {@code amount} logs; anything past the capacity is dropped and uncounted. */
    public int depositTelemetryLogs(int amount) {
        ItemStack stored = telemetryBuffer.getStackInSlot(0);
        int room = stored.isEmpty() ? TELEMETRY_BUFFER_CAPACITY : TELEMETRY_BUFFER_CAPACITY - stored.getCount();
        int deposited = Math.min(amount, room);
        if (deposited > 0) {
            ItemStack updated = stored.isEmpty() ? new ItemStack(HyperClusterItems.MINING_TELEMETRY_LOG.get()) :
                    stored.copy();
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

    /** {@link SimpleTieredMachine#EDITABLE_UI_CREATOR} with one extra buffer slot. */
    public static final BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, recipeType) -> new EditableMachineUI("telemetry", path, () -> {
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
                if (machine instanceof TelemetryOreWasherMachine washer) {
                    var storages = com.google.common.collect.Tables.<IO, com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>, Object>newCustomTable(
                            new EnumMap<>(IO.class), LinkedHashMap::new);
                    storages.put(IO.IN, ItemRecipeCapability.CAP, washer.importItems.storage);
                    storages.put(IO.OUT, ItemRecipeCapability.CAP, washer.exportItems.storage);
                    storages.put(IO.IN, FluidRecipeCapability.CAP, washer.importFluids);
                    storages.put(IO.OUT, FluidRecipeCapability.CAP, washer.exportFluids);
                    storages.put(IO.IN, CWURecipeCapability.CAP, washer.importComputation);
                    storages.put(IO.OUT, CWURecipeCapability.CAP, washer.exportComputation);

                    washer.getRecipeType().getRecipeUI().createEditableUITemplate(false, false).setupUI(template,
                            new GTRecipeTypeUI.RecipeHolder(washer.recipeLogic::getProgressPercent,
                                    storages, new CompoundTag(), Collections.emptyList(), false, false));
                    SimpleTieredMachine.createBatterySlot().setupUI(template, washer);
                    createTelemetryBufferSlot().setupUI(template, washer);
                }
            }));

    private static EditableUI<SlotWidget, TelemetryOreWasherMachine> createTelemetryBufferSlot() {
        return new EditableUI<>("telemetry_buffer_slot", SlotWidget.class, SlotWidget::new,
                (slotWidget, machine) -> {
                    slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
                    slotWidget.setHandlerSlot(machine.telemetryBuffer, 0);
                    slotWidget.setCanPutItems(false);
                    slotWidget.setCanTakeItems(true);
                    slotWidget.setOnAddedTooltips((slot, tooltips) -> {
                        tooltips.add(Component.translatable("hypercluster.telemetry_buffer.tooltip",
                                machine.getTelemetryBufferCount(), TELEMETRY_BUFFER_CAPACITY));
                        if (machine.isTelemetryBufferFull()) {
                            tooltips.add(Component.translatable("hypercluster.telemetry_buffer.full"));
                        }
                    });
                });
    }
}
