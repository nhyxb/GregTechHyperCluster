package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

import org.joml.Vector3f;

/** Emits sparks around the isolated test pedestal. */
public class RedTeamSandboxMachine extends WorkableElectricMultiblockMachine {

    private static final DustParticleOptions ADVERSARIAL_SPARK = new DustParticleOptions(
            new Vector3f(0.85f, 0.12f, 0.35f), 1.0f);

    public RedTeamSandboxMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public void animateTick(RandomSource random) {
        super.animateTick(random);
        if (getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != RecipeLogic.Status.WORKING) {
            return;
        }
        // Structure-relative: the controller sits at local (3,0,0) — the front-wall console of
        // the floor — and the isolated sturdy pedestal column stands at local (3, 1..2, 3),
        // three blocks behind it in the hollow interior.
        Direction back = getFrontFacing().getOpposite();
        BlockPos pedestal = getPos().above(1 + random.nextInt(2)).relative(back, 3);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double x = pedestal.getX() + 0.5 + Math.cos(angle);
            double y = pedestal.getY() + 0.2 + random.nextDouble() * 0.8;
            double z = pedestal.getZ() + 0.5 + Math.sin(angle);
            if (random.nextBoolean()) {
                getLevel().addParticle(ParticleTypes.PORTAL, x, y, z,
                        (random.nextDouble() - 0.5) * 0.04, -0.02 - random.nextDouble() * 0.04,
                        (random.nextDouble() - 0.5) * 0.04);
            } else {
                getLevel().addParticle(ADVERSARIAL_SPARK, x, y, z,
                        (random.nextDouble() - 0.5) * 0.03, 0.04 + random.nextDouble() * 0.05,
                        (random.nextDouble() - 0.5) * 0.03);
            }
        }
    }
}
