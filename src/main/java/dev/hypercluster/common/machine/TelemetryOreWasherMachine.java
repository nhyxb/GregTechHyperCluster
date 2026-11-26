package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import dev.hypercluster.common.item.HyperClusterItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import java.util.function.Supplier;

/**
 * The own HV telemetry ore washer (P04, 0.1).
 *
 * <p>
 * A {@link TelemetryMachine} at HV tier mining domain: every settled batch of the mirrored
 * wash recipe deposits one {@code hypercluster:mining_telemetry_log} into the persisted buffer
 * (see the base class for the settlement contract and buffer semantics).
 */
public class TelemetryOreWasherMachine extends TelemetryMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            TelemetryOreWasherMachine.class, TelemetryMachine.MANAGED_FIELD_HOLDER);

    public TelemetryOreWasherMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction,
                                     Object... args) {
        super(holder, tier, tankScalingFunction, miningLogSupplier(), args);
    }

    private static Supplier<net.minecraft.world.item.Item> miningLogSupplier() {
        return () -> HyperClusterItems.MINING_TELEMETRY_LOG.get();
    }

    /** {@link TelemetryMachine#telemetryEditableUi(String)} with the washer's own UI instance. */
    public static final java.util.function.BiFunction<net.minecraft.resources.ResourceLocation, com.gregtechceu.gtceu.api.recipe.GTRecipeType, com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI> EDITABLE_UI_CREATOR = TelemetryMachine
            .telemetryEditableUi("telemetry");

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
