package dev.hypercluster.common.machine.datacenter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Draws airflow just outside the face of a three-block-wide vent. */
public final class VentParticles {

    private VentParticles() {}

    public static void emit(Level level, RandomSource random, BlockPos vent, Direction outward, double halfHeight,
                            int count) {
        Direction across = outward.getClockWise();
        for (int i = 0; i < count; i++) {
            double sideways = (random.nextDouble() - 0.5) * 2.6;
            double x = vent.getX() + 0.5 + outward.getStepX() * 0.6 + across.getStepX() * sideways;
            double y = vent.getY() + 0.5 + (random.nextDouble() * 2.0 - 1.0) * halfHeight;
            double z = vent.getZ() + 0.5 + outward.getStepZ() * 0.6 + across.getStepZ() * sideways;
            level.addParticle(random.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE,
                    x, y, z, outward.getStepX() * 0.05, 0.005, outward.getStepZ() * 0.05);
        }
    }
}
