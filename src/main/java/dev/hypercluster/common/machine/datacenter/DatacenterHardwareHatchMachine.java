package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

/** Stores installed hardware and exposes the slots supported by the attached controller. */
public class DatacenterHardwareHatchMachine extends MultiblockPartMachine implements IMachineLife {

    private static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            DatacenterHardwareHatchMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);
    private static final int MAX_HARDWARE_SLOTS = 5;

    public interface HardwareHost extends IMultiController {

        IItemHandlerModifiable legacyHardwareInventory();

        String hardwareSlotKey(int slot);

        default void onHardwareChanged() {}

        default IItemHandlerModifiable hardwareInventory() {
            if (isFormed()) {
                for (var part : getParts()) {
                    if (part instanceof DatacenterHardwareHatchMachine hatch && hatch.hardwareHost(null) == this) {
                        return hatch.hardwareView(this, null);
                    }
                }
            }
            return legacyHardwareInventory();
        }
    }

    @Persisted
    private final CustomItemStackHandler inventory = componentSlots(MAX_HARDWARE_SLOTS);

    public DatacenterHardwareHatchMachine(IMachineBlockEntity holder) {
        super(holder);
        inventory.setOnContentsChanged(() -> {
            if (getLevel() instanceof ServerLevel) {
                markDirty();
                var host = hardwareHost(null);
                if (host != null) {
                    host.onHardwareChanged();
                }
            }
        });
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public static CustomItemStackHandler componentSlots(int slots) {
        return new CustomItemStackHandler(slots) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    /** A hatch can keep items from a previous rack, but only compatible items can contribute. */
    public static ItemStack installedStack(IItemHandler slots, int slot) {
        ItemStack stack = slots.getStackInSlot(slot);
        return !stack.isEmpty() && slots.isItemValid(slot, stack) ? stack : ItemStack.EMPTY;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (getLevel() instanceof ServerLevel && controller instanceof HardwareHost host) {
            migrateLegacyHardware(host);
        }
    }

    private void migrateLegacyHardware(HardwareHost host) {
        var legacy = host.legacyHardwareInventory();
        for (int slot = 0; slot < legacy.getSlots(); slot++) {
            ItemStack stack = legacy.getStackInSlot(slot);
            if (!stack.isEmpty() && legacy.isItemValid(slot, stack) && inventory.getStackInSlot(slot).isEmpty()) {
                ItemStack remainder = inventory.insertItem(slot, stack, false);
                legacy.setStackInSlot(slot, remainder);
                host.self().markDirty();
            }
        }
    }

    @Override
    public void onMachineRemoved() {
        if (getLevel() instanceof ServerLevel) {
            clearInventory(inventory);
        }
    }

    @Override
    @Nullable
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        HardwareHost host = hardwareHost(side);
        return host == null ? null : hardwareView(host, side);
    }

    private IItemHandlerModifiable hardwareView(HardwareHost host, @Nullable Direction side) {
        var layout = host.legacyHardwareInventory();
        return new CombinedInvWrapper(inventory) {

            @Override
            public int getSlots() {
                return layout.getSlots();
            }

            @Override
            protected int getIndexForSlot(int slot) {
                // A pipe or AE2 may retain the capability after the part is detached.
                return slot < getSlots() && hardwareHost(side) == host ? super.getIndexForSlot(slot) : -1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return super.isItemValid(slot, stack) && layout.isItemValid(slot, stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
    }

    @Nullable
    private HardwareHost hardwareHost(@Nullable Direction side) {
        var level = getLevel();
        if ((side != null && side != getFrontFacing()) || level == null || isInValid() ||
                !level.hasChunkAt(getPos()) || level.getBlockEntity(getPos()) != getHolder()) {
            return null;
        }
        for (var controller : getControllers()) {
            var machine = controller.self();
            if (controller instanceof HardwareHost host && controller.isFormed() && !machine.isInValid() &&
                    level.hasChunkAt(machine.getPos()) && MetaMachine.getMachine(level, machine.getPos()) == machine) {
                return host;
            }
        }
        return null;
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup page = new WidgetGroup(0, 0, 120, 30);
        var host = hardwareHost(null);
        // Both sides of the menu must register the same slots even if the controller detaches.
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            int slotIndex = slot;
            var widget = new SlotWidget(inventory, slot, 5 + 23 * slot, 6, true, true) {

                @Override
                public boolean canPutStack(ItemStack stack) {
                    var currentHost = hardwareHost(null);
                    if (currentHost == null || !super.canPutStack(stack)) {
                        return false;
                    }
                    var layout = currentHost.legacyHardwareInventory();
                    return slotIndex < layout.getSlots() && layout.isItemValid(slotIndex, stack);
                }
            };
            widget.setBackground(GuiTextures.SLOT);
            if (host != null && slot < host.legacyHardwareInventory().getSlots()) {
                widget.setHoverTooltips(Component.translatable(host.hardwareSlotKey(slot)));
            }
            page.addWidget(widget);
        }
        return page;
    }
}
