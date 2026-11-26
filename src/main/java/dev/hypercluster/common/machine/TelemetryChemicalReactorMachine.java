package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import dev.hypercluster.common.item.HyperClusterItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import java.util.function.Supplier;

/**
 * The own EV telemetry chemical reactor (0.2).
 *
 * <p>
 * A {@link TelemetryMachine} at EV tier in the chemical domain: every settled batch of the
 * mirrored {@code gtceu:chemical_reactor/indium_concentrate_separation} recipe (and its
 * chip-enhanced counterpart) deposits one {@code hypercluster:chemical_telemetry_log} into the
 * persisted buffer. Settlement and buffer semantics are inherited from the base class — the
 * same IO.OUT-verified architecture proven by the 0.1 washer, without mixins.
 */
public class TelemetryChemicalReactorMachine extends TelemetryMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TelemetryChemicalReactorMachine.class, TelemetryMachine.MANAGED_FIELD_HOLDER);

    public TelemetryChemicalReactorMachine(IMachineBlockEntity holder, int tier,
                                           Int2IntFunction tankScalingFunction, Object... args) {
        super(holder, tier, tankScalingFunction, chemicalLogSupplier(), args);
    }

    private static Supplier<net.minecraft.world.item.Item> chemicalLogSupplier() {
        return () -> HyperClusterItems.CHEMICAL_TELEMETRY_LOG.get();
    }

    /** {@link TelemetryMachine#telemetryEditableUi(String)} with the reactor's own UI instance. */
    public static final java.util.function.BiFunction<net.minecraft.resources.ResourceLocation, com.gregtechceu.gtceu.api.recipe.GTRecipeType, com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI> EDITABLE_UI_CREATOR = TelemetryMachine
            .telemetryEditableUi("chemical_telemetry");

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
