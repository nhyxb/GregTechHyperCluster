package dev.hypercluster.common.machine.datacenter;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;

import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import dev.hypercluster.common.data.HyperClusterFluids;

import javax.annotation.Nullable;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

public final class RackHeatRecovery {

    public static final int STEAM_PER_WATER = 160;
    // The base steam fuel yields 0.5 EU/mB. An endgame rotor gives 250% and a tier-13 holder in an HV turbine doubles
    // it.
    // Round the resulting 2.5 EU/mB up, including high-tier GT configurations.
    public static final int MAX_STEAM_EU_PER_MB = 3;

    private RackHeatRecovery() {}

    public static int steamLimit(long itEUt) {
        return itEUt <= 0 ? 0 : (int) ((itEUt - 1) / (4 * MAX_STEAM_EU_PER_MB));
    }

    public static void recoverAir(MultiblockControllerMachine rack, long itEUt) {
        var output = outputTank(rack);
        int limit = steamLimit(itEUt);
        if (output == null || limit == 0) {
            return;
        }
        var steam = GTMaterials.Steam.getFluid(limit);
        int room = output.fillInternal(steam, SIMULATE);
        if (room == 0) {
            return;
        }
        int waterNeeded = (room + STEAM_PER_WATER - 1) / STEAM_PER_WATER;
        for (var part : rack.getParts()) {
            if (part instanceof FluidHatchPartMachine hatch && hatch.tank.handlerIO == IO.IN) {
                int drained = hatch.tank.drainInternal(new FluidStack(Fluids.WATER, waterNeeded), EXECUTE)
                        .getAmount();
                if (drained < waterNeeded) {
                    drained += hatch.tank.drainInternal(GTMaterials.DistilledWater.getFluid(waterNeeded - drained),
                            EXECUTE).getAmount();
                }
                if (drained > 0) {
                    steam.setAmount(Math.min(room, drained * STEAM_PER_WATER));
                    output.fillInternal(steam, EXECUTE);
                }
                return;
            }
        }
    }

    public static void recoverLiquid(MultiblockControllerMachine rack, long itEUt, FluidStack coolant) {
        if (itEUt <= 0 || coolant.isEmpty()) {
            return;
        }
        var output = outputTank(rack);
        if (output == null) {
            return;
        }
        if (coolant.getFluid() == GTMaterials.PCBCoolant.getFluid()) {
            output.fillInternal(HyperClusterFluids.HOT_PCB_COOLANT.getFluid(coolant.getAmount()), EXECUTE);
        } else if (coolant.getFluid() == GTMaterials.DistilledWater.getFluid()) {
            int steam = Math.min(coolant.getAmount() * STEAM_PER_WATER, steamLimit(itEUt));
            if (steam > 0) {
                output.fillInternal(GTMaterials.Steam.getFluid(steam), EXECUTE);
            }
        }
    }

    @Nullable
    private static NotifiableFluidTank outputTank(MultiblockControllerMachine rack) {
        for (var part : rack.getParts()) {
            if (part instanceof FluidHatchPartMachine hatch && hatch.tank.handlerIO == IO.OUT) {
                return hatch.tank;
            }
        }
        return null;
    }
}
