package dev.hypercluster.common.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public final class MachineUi {

    private static final int INSET = 4;
    private static final int MULTIBLOCK_WIDTH = 190;
    private static final int BODY_WIDTH = MULTIBLOCK_WIDTH - 2 * INSET;
    private static final int DISPLAY_HEIGHT = 54;
    private static final int CONTROLS_Y = INSET + DISPLAY_HEIGHT + 4;

    private MachineUi() {}

    public static WidgetGroup multiblockPage(IDisplayUIMachine machine, WidgetGroup controls) {
        WidgetGroup page = panel(MULTIBLOCK_WIDTH, CONTROLS_Y + controls.getSize().height + INSET);
        ComponentPanelWidget text = statusText(machine.self().getLevel().isClientSide, machine::addDisplayText)
                .clickHandler(machine::handleDisplayClick);
        page.addWidgets(display(INSET, INSET, BODY_WIDTH, DISPLAY_HEIGHT, machine.getScreenTexture(), text), controls);
        return page;
    }

    public static WidgetGroup multiblockControls(int height) {
        return new WidgetGroup(INSET, CONTROLS_Y, BODY_WIDTH, height);
    }

    public static WidgetGroup panel(int width, int height) {
        WidgetGroup page = new WidgetGroup(0, 0, width, height);
        page.setBackground(GuiTextures.BACKGROUND);
        return page;
    }

    public static ComponentPanelWidget statusText(boolean clientSide, Consumer<List<Component>> content) {
        return new ComponentPanelWidget(INSET, INSET, List.of()).textSupplier(clientSide ? null : content);
    }

    public static DraggableScrollableWidgetGroup display(int x, int y, int width, int height, IGuiTexture background,
                                                         ComponentPanelWidget text) {
        int scrollBarWidth = 6;
        DraggableScrollableWidgetGroup display = new DraggableScrollableWidgetGroup(x, y, width, height)
                .setBackground(background)
                .setYScrollBarWidth(scrollBarWidth)
                .setYBarStyle(GuiTextures.SLIDER_BACKGROUND_VERTICAL, GuiTextures.SLIDER_ICON)
                .setDraggable(false)
                .setScrollable(true)
                .setUseScissor(true);
        display.addWidget(text.setMaxWidthLimit(width - 2 * INSET - scrollBarWidth));
        return display;
    }

    public static ImageWidget label(int x, int y, int width, String translationKey) {
        return new ImageWidget(x, y, width, 10, new TextTexture(translationKey)
                .setWidth(width - 4)
                .setType(TextTexture.TextType.ROLL_ALWAYS)
                .setDropShadow(false)
                .setColor(0xff1d252b));
    }
}
