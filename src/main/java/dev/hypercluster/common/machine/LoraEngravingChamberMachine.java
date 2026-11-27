package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Projects a beam from the suspended engraving head onto the filter stage. */
public class LoraEngravingChamberMachine extends WorkableElectricMultiblockMachine {

    public LoraEngravingChamberMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        // The two open blocks below the head end at the top face of the filter stage.
        for (int i = 0; i < 3; i++) {
            double x = getPos().getX() + 0.5 + (random.nextDouble() - 0.5) * 0.16;
            double y = getPos().getY() - 0.1 - random.nextDouble() * 1.8;
            double z = getPos().getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.16;
            // Both vanilla glow providers scale the supplied speed by 0.01.
            getLevel().addParticle(random.nextBoolean() ? ParticleTypes.SCRAPE : ParticleTypes.WAX_ON,
                    x, y, z, 0.0, -10.0, 0.0);
        }
    }
}
