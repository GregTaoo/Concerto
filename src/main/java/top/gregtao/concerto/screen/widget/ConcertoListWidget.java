package top.gregtao.concerto.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ConcertoListWidget<T> extends AlwaysSelectedEntryListWidget<ConcertoListWidget<T>.Entry> {
    private int color = 0xffffffff;
    private final BiFunction<T, Integer, Text> narrationSupplier;
    private final Consumer<Entry> onDoubleClicked;

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight,
                              BiFunction<T, Integer, Text> narrationSupplier, Consumer<Entry> onDoubleClicked) {
        super(MinecraftClient.getInstance(), width, height, top, bottom, itemHeight);
        this.narrationSupplier = narrationSupplier;
        this.onDoubleClicked = onDoubleClicked;
    }

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight,
                              BiFunction<T, Integer, Text> narrationSupplier, Consumer<Entry> onDoubleClicked, int color) {
        this(width, height, top, bottom, itemHeight, narrationSupplier, onDoubleClicked);
        this.color = color;
    }

    public void reset(List<T> list, T selected) {
        this.clearEntries();
        for (int i = 0; i < list.size(); ++i) {
            T music = list.get(i);
            Entry entry = new Entry(music, i);
            this.addEntry(entry);
            if (music == selected) {
                this.setSelected(entry);
                this.centerScrollOn(entry);
            }
        }
    }

    public void clear() {
        super.clearEntries();
    }

    @Override
    public boolean removeEntryWithoutScrolling(Entry entry) {
        ListIterator<Entry> iterator = this.children().listIterator(entry.index);
        while (iterator.hasNext()) {
            iterator.next().index--;
        }
        return super.removeEntryWithoutScrolling(entry);
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.width - 10;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
    }

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        public T item;
        public int index;
        private long lastClickTime = 0;

        public Entry(T item, int index) {
            this.item = item;
            this.index = index;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                if (Util.getMeasuringTimeMs() - this.lastClickTime < 250) {
                    ConcertoListWidget.this.onDoubleClicked.accept(this);
                } else {
                    ConcertoListWidget.this.setSelected(this);
                }
                this.lastClickTime = Util.getMeasuringTimeMs();
                return true;
            }
            return false;
        }

        @Override
        public Text getNarration() {
            return ConcertoListWidget.this.narrationSupplier.apply(this.item, this.index);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawText(MinecraftClient.getInstance().textRenderer, this.getNarration(), x, y + 3, ConcertoListWidget.this.color, false);
        }
    }
}
