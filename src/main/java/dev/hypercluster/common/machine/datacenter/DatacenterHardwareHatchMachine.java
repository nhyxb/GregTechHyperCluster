package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

/** An automation and maintenance port for the attached controller's installed hardware. */
public class DatacenterHardwareHatchMachine extends MultiblockPartMachine {

    public interface HardwareHost {

        IItemHandlerModifiable hardwareInventory();

        String hardwareSlotKey(int slot);
    }

    public DatacenterHardwareHatchMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    public static CustomItemStackHandler componentSlots(int slots) {
        return new CustomItemStackHandler(slots) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    @Nullable
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        HardwareHost host = hardwareHost(side);
        if (host == null) {
            return null;
        }
        return new CombinedInvWrapper(host.hardwareInventory()) {

            @Override
            protected int getIndexForSlot(int slot) {
                // A pipe or AE2 may retain the capability after the part is detached.
                return hardwareHost(side) == host ? super.getIndexForSlot(slot) : -1;
            }
        };
    }

    @Nullable
    private HardwareHost hardwareHost(@Nullable Direction side) {
        if ((side != null && side != getFrontFacing()) || getLevel() == null ||
                !getLevel().hasChunkAt(getPos()) || getLevel().getBlockEntity(getPos()) != getHolder()) {
            return null;
        }
        for (var controller : getControllers()) {
            if (controller.isFormed() && controller instanceof HardwareHost host) {
                return host;
            }
        }
        return null;
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup page = new WidgetGroup(0, 0, 120, 30);
        var host = hardwareHost(null);
        if (host != null) {
            var inventory = getItemHandlerCap(null, false);
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var widget = new SlotWidget(inventory, slot, 5 + 23 * slot, 6, true, true);
                widget.setBackground(GuiTextures.SLOT);
                widget.setHoverTooltips(Component.translatable(host.hardwareSlotKey(slot)));
                page.addWidget(widget);
            }
        }
        return page;
    }
}
