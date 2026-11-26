package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * GregTech HyperCluster multiblock part abilities (0.3).
 *
 * <ul>
 * <li>{@code inference_computation} — the IV inference coprocessor hatch. Accepted (optionally,
 * at most one) by the data scrubbing tower's structure so real CWU/t-consuming recipes can run
 * there.</li>
 * </ul>
 */
public final class HyperClusterPartAbilities {

    public static final PartAbility INFERENCE_COMPUTATION = new PartAbility("inference_computation");

    private HyperClusterPartAbilities() {}
}
