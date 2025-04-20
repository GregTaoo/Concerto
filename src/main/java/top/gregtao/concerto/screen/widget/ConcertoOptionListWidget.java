package top.gregtao.concerto.screen.widget;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.screen.ConcertoOptionsScreen;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/* 复制原版代码使用 */
public class ConcertoOptionListWidget extends ElementListWidget<ConcertoOptionListWidget.WidgetEntry> {
    private final ConcertoOptionsScreen optionsScreen;

    public ConcertoOptionListWidget(MinecraftClient client, int width, ConcertoOptionsScreen optionsScreen) {
        super(client, width, optionsScreen.layout.getContentHeight(), optionsScreen.layout.getHeaderHeight(), 25);
        this.centerListVertically = false;
        this.optionsScreen = optionsScreen;
    }

    public void addSingleOptionEntry(SimpleOption<?> option) {
        this.addEntry(OptionWidgetEntry.create(this.client.options, option, this.optionsScreen));
    }

    public void addAll(SimpleOption<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            SimpleOption<?> simpleOption = i < options.length - 1 ? options[i + 1] : null;
            this.addEntry(OptionWidgetEntry.create(this.client.options, options[i], simpleOption, this.optionsScreen));
        }
    }

    public void addAll(List<ClickableWidget> widgets) {
        for (int i = 0; i < widgets.size(); i += 2) {
            this.addWidgetEntry(widgets.get(i), i < widgets.size() - 1 ? widgets.get(i + 1) : null);
        }
    }

    public void addWidgetEntry(ClickableWidget firstWidget, @Nullable ClickableWidget secondWidget) {
        this.addEntry(WidgetEntry.create(firstWidget, secondWidget, this.optionsScreen));
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Nullable
    public ClickableWidget getWidgetFor(SimpleOption<?> option) {
        for (WidgetEntry widgetEntry : this.children()) {
            if (widgetEntry instanceof OptionWidgetEntry optionWidgetEntry) {
                ClickableWidget clickableWidget = optionWidgetEntry.optionWidgets.get(option);
                if (clickableWidget != null) {
                    return clickableWidget;
                }
            }
        }

        return null;
    }

    public void applyAllPendingValues() {
        for (WidgetEntry widgetEntry : this.children()) {
            if (widgetEntry instanceof OptionWidgetEntry optionWidgetEntry) {
                for (ClickableWidget clickableWidget : optionWidgetEntry.optionWidgets.values()) {
                    if (clickableWidget instanceof SimpleOption.OptionSliderWidgetImpl<?> optionSliderWidgetImpl) {
                        optionSliderWidgetImpl.applyPendingValue();
                    }
                }
            }
        }
    }

    public Optional<Element> getHoveredWidget(double mouseX, double mouseY) {
        for (WidgetEntry widgetEntry : this.children()) {
            for (Element element : widgetEntry.children()) {
                if (element.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(element);
                }
            }
        }

        return Optional.empty();
    }
    
    protected static class OptionWidgetEntry extends WidgetEntry {
        final Map<SimpleOption<?>, ClickableWidget> optionWidgets;

        private OptionWidgetEntry(Map<SimpleOption<?>, ClickableWidget> widgets, ConcertoOptionsScreen optionsScreen) {
            super(ImmutableList.copyOf(widgets.values()), optionsScreen);
            this.optionWidgets = widgets;
        }

        public static OptionWidgetEntry create(GameOptions gameOptions, SimpleOption<?> option, ConcertoOptionsScreen optionsScreen) {
            return new OptionWidgetEntry(ImmutableMap.of(option, option.createWidget(gameOptions, 0, 0, 310)), optionsScreen);
        }

        public static OptionWidgetEntry create(
                GameOptions gameOptions, SimpleOption<?> firstOption, @Nullable SimpleOption<?> secondOption, ConcertoOptionsScreen optionsScreen
        ) {
            ClickableWidget clickableWidget = firstOption.createWidget(gameOptions);
            return secondOption == null
                    ? new OptionWidgetEntry(ImmutableMap.of(firstOption, clickableWidget), optionsScreen)
                    : new OptionWidgetEntry(ImmutableMap.of(firstOption, clickableWidget, secondOption, secondOption.createWidget(gameOptions)), optionsScreen);
        }
    }

    protected static class WidgetEntry extends ElementListWidget.Entry<WidgetEntry> {
        private final List<ClickableWidget> widgets;
        private final Screen screen;

        WidgetEntry(List<ClickableWidget> widgets, Screen screen) {
            this.widgets = ImmutableList.copyOf(widgets);
            this.screen = screen;
        }

        public static WidgetEntry create(List<ClickableWidget> widgets, Screen screen) {
            return new WidgetEntry(widgets, screen);
        }

        public static WidgetEntry create(ClickableWidget firstWidget, @Nullable ClickableWidget secondWidget, Screen screen) {
            return secondWidget == null
                    ? new WidgetEntry(ImmutableList.of(firstWidget), screen)
                    : new WidgetEntry(ImmutableList.of(firstWidget, secondWidget), screen);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickProgress) {
            int i = 0;
            int j = this.screen.width / 2 - 155;

            for (ClickableWidget clickableWidget : this.widgets) {
                clickableWidget.setPosition(j + i, y);
                clickableWidget.render(context, mouseX, mouseY, tickProgress);
                i += 160;
            }
        }

        @Override
        public List<? extends Element> children() {
            return this.widgets;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return this.widgets;
        }
    }
}
