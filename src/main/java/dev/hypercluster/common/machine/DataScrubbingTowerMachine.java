package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Shows the rising flow around the scrubbing tower’s titanium pipe. */
public class DataScrubbingTowerMachine extends WorkableElectricMultiblockMachine {

    public DataScrubbingTowerMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        // The pipe starts one layer above and two blocks behind the controller.
        Direction back = getFrontFacing().getOpposite();
        for (int i = 0; i < 3; i++) {
            BlockPos pipe = getPos().above(1 + random.nextInt(5)).relative(back, 2);
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos cell = pipe.relative(side);
            double x = cell.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = cell.getY() + 0.2 + random.nextDouble() * 0.6;
            double z = cell.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            if (random.nextBoolean()) {
                getLevel().addParticle(ParticleTypes.SCRAPE, x, y, z,
                        0.0, 6.0 + random.nextDouble() * 4.0, 0.0);
            } else {
                getLevel().addParticle(ParticleTypes.END_ROD, x, y, z,
                        (random.nextDouble() - 0.5) * 0.02, 0.05 + random.nextDouble() * 0.05,
                        (random.nextDouble() - 0.5) * 0.02);
            }
        }
    }
}
