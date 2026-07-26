package top.gregtao.concerto.screen.widget;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.screen.ConcertoOptionsScreen;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/* 复制原版代码使用 */
public class ConcertoOptionListWidget extends ContainerObjectSelectionList<ConcertoOptionListWidget.WidgetEntry> {
    private final ConcertoOptionsScreen optionsScreen;

    public ConcertoOptionListWidget(Minecraft client, int width, ConcertoOptionsScreen optionsScreen) {
        super(client, width, optionsScreen.height,
                optionsScreen.layout.getHeaderHeight(),
                optionsScreen.height - optionsScreen.layout.getFooterHeight(), 25);
        this.centerListVertically = false;
        this.optionsScreen = optionsScreen;
    }

    public void addSingleOptionEntry(OptionInstance<?> option) {
        this.addEntry(OptionWidgetEntry.create(this.minecraft.options, option, this.optionsScreen));
    }

    public void addAll(OptionInstance<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            OptionInstance<?> simpleOption = i < options.length - 1 ? options[i + 1] : null;
            this.addEntry(OptionWidgetEntry.create(this.minecraft.options, options[i], simpleOption, this.optionsScreen));
        }
    }

    public void addAll(List<AbstractWidget> widgets) {
        for (int i = 0; i < widgets.size(); i += 2) {
            this.addWidgetEntry(widgets.get(i), i < widgets.size() - 1 ? widgets.get(i + 1) : null);
        }
    }

    public void addWidgetEntry(AbstractWidget firstWidget, @Nullable AbstractWidget secondWidget) {
        this.addEntry(WidgetEntry.create(firstWidget, secondWidget, this.optionsScreen));
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Nullable
    public AbstractWidget getWidgetFor(OptionInstance<?> option) {
        for (WidgetEntry widgetEntry : this.children()) {
            if (widgetEntry instanceof OptionWidgetEntry optionWidgetEntry) {
                AbstractWidget clickableWidget = optionWidgetEntry.optionWidgets.get(option);
                if (clickableWidget != null) {
                    return clickableWidget;
                }
            }
        }

        return null;
    }

    public void applyAllPendingValues() {
        // 1.20.1 option sliders write straight into the OptionInstance while
        // dragging (the deferred applyUnsavedValue machinery is 1.20.2+),
        // so there is nothing pending to flush here
    }

    public Optional<GuiEventListener> getHoveredWidget(double mouseX, double mouseY) {
        for (WidgetEntry widgetEntry : this.children()) {
            for (GuiEventListener element : widgetEntry.children()) {
                if (element.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(element);
                }
            }
        }

        return Optional.empty();
    }

    protected static class OptionWidgetEntry extends WidgetEntry {
        final Map<OptionInstance<?>, AbstractWidget> optionWidgets;

        private OptionWidgetEntry(Map<OptionInstance<?>, AbstractWidget> widgets, ConcertoOptionsScreen optionsScreen) {
            super(ImmutableList.copyOf(widgets.values()), optionsScreen);
            this.optionWidgets = widgets;
        }

        public static OptionWidgetEntry create(Options gameOptions, OptionInstance<?> option, ConcertoOptionsScreen optionsScreen) {
            return new OptionWidgetEntry(ImmutableMap.of(option, option.createButton(gameOptions, 0, 0, 310)), optionsScreen);
        }

        public static OptionWidgetEntry create(
                Options gameOptions, OptionInstance<?> firstOption, @Nullable OptionInstance<?> secondOption, ConcertoOptionsScreen optionsScreen
        ) {
            AbstractWidget clickableWidget = firstOption.createButton(gameOptions, 0, 0, 150);
            return secondOption == null
                    ? new OptionWidgetEntry(ImmutableMap.of(firstOption, clickableWidget), optionsScreen)
                    : new OptionWidgetEntry(ImmutableMap.of(firstOption, clickableWidget, secondOption, secondOption.createButton(gameOptions, 0, 0, 150)), optionsScreen);
        }
    }

    protected static class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetEntry> {
        private final List<AbstractWidget> widgets;
        private final Screen screen;

        WidgetEntry(List<AbstractWidget> widgets, Screen screen) {
            this.widgets = ImmutableList.copyOf(widgets);
            this.screen = screen;
        }

        public static WidgetEntry create(List<AbstractWidget> widgets, Screen screen) {
            return new WidgetEntry(widgets, screen);
        }

        public static WidgetEntry create(AbstractWidget firstWidget, @Nullable AbstractWidget secondWidget, Screen screen) {
            return secondWidget == null
                    ? new WidgetEntry(ImmutableList.of(firstWidget), screen)
                    : new WidgetEntry(ImmutableList.of(firstWidget, secondWidget), screen);
        }

        @Override
        public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickProgress) {
            int i = 0;
            int j = this.screen.width / 2 - 155;

            for (AbstractWidget clickableWidget : this.widgets) {
                clickableWidget.setPosition(j + i, y);
                clickableWidget.render(context, mouseX, mouseY, tickProgress);
                i += 160;
            }
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }
}
