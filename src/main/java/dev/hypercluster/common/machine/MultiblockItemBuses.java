package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Controller-side bus access bypasses the deliberately one-way automation capability. */
public final class MultiblockItemBuses {

    private MultiblockItemBuses() {}

    public static ItemStack take(MultiblockControllerMachine machine, Predicate<ItemStack> matches,
                                 int amount, boolean simulate) {
        for (var part : machine.getParts()) {
            if (part instanceof ItemBusPartMachine bus && bus.getInventory().handlerIO == IO.IN) {
                var inventory = bus.getInventory();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    if (matches.test(inventory.getStackInSlot(slot))) {
                        return inventory.extractItemInternal(slot, amount, simulate);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean export(MultiblockControllerMachine machine, ItemStack stack, boolean simulate) {
        return insert(machine, IO.OUT, stack, simulate);
    }

    public static boolean returnInput(MultiblockControllerMachine machine, ItemStack stack) {
        return insert(machine, IO.IN, stack, false);
    }

    private static boolean insert(MultiblockControllerMachine machine, IO direction, ItemStack stack,
                                  boolean simulate) {
        // Each settlement produces one item. Insert into one slot so failure cannot partially settle.
        for (var part : machine.getParts()) {
            if (part instanceof ItemBusPartMachine bus && bus.getInventory().handlerIO == direction) {
                var inventory = bus.getInventory();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    if (inventory.insertItemInternal(slot, stack, true).isEmpty()) {
                        return simulate || inventory.insertItemInternal(slot, stack, false).isEmpty();
                    }
                }
            }
        }
        return false;
    }
}
