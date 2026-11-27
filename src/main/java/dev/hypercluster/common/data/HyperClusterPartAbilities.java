package dev.hypercluster.common.data;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * GregTech HyperCluster multiblock part abilities.
 *
 * <ul>
 * <li>{@code inference_computation} (0.3) — the IV inference coprocessor hatch. Accepted
 * (optionally, at most one) by the data scrubbing tower's structure so real CWU/t-consuming
 * recipes can run there.</li>
 * <li>{@code local_fallback_gateway} (0.4) — the IV local fallback gateway. Accepted
 * (optionally, at most one) by the API satellite station's structure; while attached it takes
 * over cloud rounds that hit 429 concurrency limits, insufficient Token balance or a retired
 * model, and continues them locally at degraded speed (no Token billing).</li>
 * </ul>
 */
public final class HyperClusterPartAbilities {

    public static final PartAbility INFERENCE_COMPUTATION = new PartAbility("inference_computation");

    public static final PartAbility LOCAL_FALLBACK_GATEWAY = new PartAbility("local_fallback_gateway");

    /**
     * 0.6 — the LuV cloud compute offload node. Accepted (optionally, at most one) by the Tier III
     * liquid-cooled compute rack's structure: it rents the rack's spare CWU/t and pays Tokens.
     */
    public static final PartAbility CLOUD_OFFLOAD = new PartAbility("cloud_offload");

    private HyperClusterPartAbilities() {}
}
