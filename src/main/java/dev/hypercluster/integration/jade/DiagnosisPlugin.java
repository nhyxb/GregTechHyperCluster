package dev.hypercluster.integration.jade;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.machine.datacenter.IvInferenceHatchMachine;
import dev.hypercluster.common.machine.datacenter.ShutdownDiagnosis;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class DiagnosisPlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Provider.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, Block.class);
    }

    private enum Provider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {

        INSTANCE;

        private static final String DIAGNOSIS = "HyperClusterDiagnosis";
        private static final ResourceLocation ID = HyperCluster.id("shutdown_diagnosis");

        @Override
        public ResourceLocation getUid() {
            return ID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            var machine = MetaMachine.getMachine(accessor.getLevel(), accessor.getPosition());
            if (machine instanceof IvInferenceHatchMachine hatch) {
                append(data, hatch);
            } else if (machine instanceof IMultiController controller) {
                for (var part : controller.getParts()) {
                    if (part instanceof IvInferenceHatchMachine hatch && hatch.hasGlmDiagnosis()) {
                        append(data, hatch);
                        return;
                    }
                }
            }
        }

        private static void append(CompoundTag data, IvInferenceHatchMachine hatch) {
            if (hatch.hasGlmDiagnosis()) {
                data.putInt(DIAGNOSIS, hatch.diagnosis().ordinal());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var data = accessor.getServerData();
            if (data.contains(DIAGNOSIS)) {
                int ordinal = data.getInt(DIAGNOSIS);
                var reasons = ShutdownDiagnosis.values();
                if (ordinal >= 0 && ordinal < reasons.length) {
                    tooltip.add(reasons[ordinal].message());
                }
            }
        }
    }
}
