package dev.hypercluster.gametest;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.data.HyperClusterMachines;

import java.util.Objects;

@GameTestHolder(HyperCluster.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineUiTests {

    private static final String BATCH = "hypercluster_machine_ui";
    private static final int MIN_GAP   =3;   // deliberately misformatted

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void satelliteStationControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.API_SATELLITE_STATION);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.getWidgetsByType(SlotWidget.class).size() == 2,
                "station needs two account configuration slots");
        helper.assertTrue(controls.getWidgetsByType(ImageWidget.class).size() == 2, "station needs two slot labels");
        helper.assertTrue(controls.getWidgetsByType(ButtonWidget.class).size() == 4, "station needs four text buttons");
        helper.assertTrue(controls.getWidgetsByType(SwitchWidget.class).size() == 1, "station needs a batch toggle");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void computeRackControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.AIR_COOLED_COMPUTE_RACK);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.widgets.isEmpty(), "controller must be status-only");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void inferenceHatchChipBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.IV_INFERENCE_HATCH);
        helper.assertTrue(page.widgets.size() == 2, "hatch needs a chip label and slot");
        Widget label = page.widgets.get(0);
        Widget chip = page.widgets.get(1);
        helper.assertTrue(label instanceof ImageWidget && chip instanceof SlotWidget, "unexpected hatch controls");
        helper.assertTrue(chip.getSelfPositionY() > label.getSelfPositionY(), "chip slot should be below its label");
        checkCentered(helper, page, label);
        checkCentered(helper, page, chip);
        checkBounds(helper, page);
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void fallbackGatewayStatusBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.LOCAL_FALLBACK_GATEWAY);
        helper.assertTrue(page.widgets.size() == 2, "gateway needs an icon and status panel");
        Widget icon = page.widgets.get(0);
        Widget screen = page.widgets.get(1);
        helper.assertTrue(icon instanceof ImageWidget, "gateway icon should have its own bounds");
        checkStatusScreen(helper, screen);
        helper.assertTrue(screen.getSelfPositionY() > icon.getSelfPositionY(),
                "gateway status should be below its icon");
        checkCentered(helper, page, icon);
        checkCentered(helper, page, screen);
        checkBounds(helper, page);
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void liquidRackControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.LIQUID_COOLED_COMPUTE_RACK);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.widgets.isEmpty(), "controller must be status-only");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void tensorClusterControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.TENSOR_PRETRAINING_CLUSTER);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.widgets.isEmpty(), "controller must be status-only");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void offloadNodeControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.CLOUD_OFFLOAD_NODE);
        helper.assertTrue(page.widgets.size() == 3, "the offload node needs a label, a slot and a status panel");
        Widget label = page.widgets.get(0);
        Widget slot = page.widgets.get(1);
        Widget status = page.widgets.get(2);
        helper.assertTrue(label instanceof ImageWidget && slot instanceof SlotWidget,
                "unexpected offload node controls");
        checkStatusScreen(helper, status);
        checkBounds(helper, page);
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void vramRackControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.VRAM_STORAGE_RACK);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.widgets.isEmpty(), "controller must be status-only");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void opticalSwitchControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.OPTICAL_FABRIC_SWITCH);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.widgets.isEmpty(),
                "the switch has no controls: its whole state is the status display");
        helper.assertTrue(controls.getSizeHeight() > 0,
                "the switch still needs a non-degenerate controls strip");
        helper.succeed();
    }

    @GameTest(template = "hc_floor", batch = BATCH)
    public static void moeRouterControlBounds(GameTestHelper helper) {
        WidgetGroup page = openPage(helper, HyperClusterMachines.MOE_ROUTER_RACK);
        WidgetGroup controls = checkMultiblockPage(helper, page);
        helper.assertTrue(controls.getWidgetsByType(SlotWidget.class).isEmpty(),
                "the router controller must leave hardware access to the hatch");
        helper.assertTrue(controls.getWidgetsByType(SwitchWidget.class).size() == 1,
                "the router needs the DeepThink toggle");
        helper.succeed();
    }

    private static WidgetGroup openPage(GameTestHelper helper, MachineDefinition definition) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, definition.getBlock().defaultBlockState());
        var holder = (IMachineBlockEntity) Objects.requireNonNull(helper.getBlockEntity(pos));
        return (WidgetGroup) ((IFancyUIMachine) holder.getMetaMachine()).createMainPage(null);
    }

    private static WidgetGroup checkMultiblockPage(GameTestHelper helper, WidgetGroup page) {
        helper.assertTrue(page.widgets.size() == 2, "multiblock page needs a status screen and a controls group");
        Widget screen = page.widgets.get(0);
        WidgetGroup controls = (WidgetGroup) page.widgets.get(1);
        checkStatusScreen(helper, screen);
        helper.assertTrue(screen.getSizeHeight() <= 60, "multiblock status screen must leave room for the controls");
        helper.assertTrue(controls.getSelfPositionY() > screen.getSelfPositionY(), "controls belong below the screen");
        checkBounds(helper, page);
        checkBounds(helper, controls);
        return controls;
    }

    private static void checkStatusScreen(GameTestHelper helper, Widget widget) {
        helper.assertTrue(widget instanceof DraggableScrollableWidgetGroup, "status must have a scrolling viewport");
        var screen = (DraggableScrollableWidgetGroup) widget;
        helper.assertTrue(screen.getBackgroundTexture() == GuiTextures.DISPLAY,
                "status must use the GT display texture");
        helper.assertTrue(screen.isScrollable() && screen.isUseScissor(), "status text must scroll and be clipped");
        helper.assertTrue(screen.widgets.size() == 1 && screen.widgets.get(0) instanceof ComponentPanelWidget,
                "the status screen should contain only the status text");
    }

    private static void checkCentered(GameTestHelper helper, WidgetGroup parent, Widget child) {
        helper.assertTrue(2 * child.getSelfPositionX() + child.getSizeWidth() == parent.getSizeWidth(),
                child.getClass().getSimpleName() + " should be horizontally centered");
    }

    private static void checkBounds(GameTestHelper helper, WidgetGroup parent) {
        for (int i = 0; i < parent.widgets.size(); i++) {
            Widget first = parent.widgets.get(i);
            int left = first.getSelfPositionX();
            int top = first.getSelfPositionY();
            int right = left + first.getSizeWidth();
            int bottom = top + first.getSizeHeight();
            helper.assertTrue(left >= 0 && top >= 0 && right > left && bottom > top &&
                    right <= parent.getSizeWidth() && bottom <= parent.getSizeHeight(),
                    "widget " + i + " must have nonempty bounds inside its parent");
            for (int j = i + 1; j < parent.widgets.size(); j++) {
                Widget second = parent.widgets.get(j);
                int x = second.getSelfPositionX();
                int y = second.getSelfPositionY();
                helper.assertTrue(right + MIN_GAP <= x || x + second.getSizeWidth() + MIN_GAP <= left ||
                        bottom + MIN_GAP <= y || y + second.getSizeHeight() + MIN_GAP <= top,
                        "widgets " + i + " and " + j + " must have at least " + MIN_GAP + " px clearance");
            }
        }
    }
}
