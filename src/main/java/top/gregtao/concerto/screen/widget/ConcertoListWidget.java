package top.gregtao.concerto.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;
import java.util.ListIterator;

public class ConcertoListWidget<T> extends AlwaysSelectedEntryListWidget<ConcertoListWidget<T>.Entry> {
    private int color = 0xffffffff;

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight) {
        super(MinecraftClient.getInstance(), width, height, top, bottom, itemHeight);
    }

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight, int color) {
        this(width, height, top, bottom, itemHeight);
        this.color = color;
    }

    public Text getNarration(int index, T t) {
        return new LiteralText(String.valueOf(index));
    }

    public void onDoubleClicked(Entry entry) {}

    public void reset(List<T> list, T selected, String key) {
        this.clearEntries();
        key = key.toLowerCase();
        for (int i = 0, j = 0; i < list.size(); ++i) {
            T music = list.get(i);
            if (key.isEmpty() || this.getNarration(i, music).getString().toLowerCase().matches(".*" + key + ".*")) {
                Entry entry = new Entry(music, i, j++);
                this.addEntry(entry);
                if (music == selected) {
                    this.setSelected(entry);
                    this.centerScrollOn(entry);
                }
            }
        }
    }

    public void reset(List<T> list, T selected) {
        this.reset(list, selected, "");
    }

    public void setSelected(int index) {
        Entry entry = this.children().get(index);
        this.setSelected(entry);
        this.centerScrollOn(entry);
    }

    public void clear() {
        super.clearEntries();
    }

    public boolean removeEntryWithoutScrolling(Entry entry) {
        ListIterator<Entry> iterator = this.children().listIterator(entry.entryIndex + 1);
        while (iterator.hasNext()) {
            iterator.next().index--;
        }
        this.setScrollAmount(entry.entryIndex * this.itemHeight + this.headerHeight);
        return super.removeEntry(entry);
    }

    @Override
    public int getRowWidth() {
        return this.width - 35;
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.width - 10;
    }

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        public T item;
        public int index, entryIndex;
        private long lastClickTime = 0;

        public Entry(T item, int index, int entryIndex) {
            this.item = item;
            this.index = index;
            this.entryIndex = entryIndex;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                if (Util.getMeasuringTimeMs() - this.lastClickTime < 250) {
                    ConcertoListWidget.this.onDoubleClicked(this);
                } else {
                    ConcertoListWidget.this.setSelected(this);
                }
                this.lastClickTime = Util.getMeasuringTimeMs();
                return true;
            }
            return false;
        }

        public Text getNarration() {
            return ConcertoListWidget.this.getNarration(this.index, this.item);
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient.getInstance().textRenderer.draw(matrices, this.getNarration(), x, y + 3, ConcertoListWidget.this.color);
        }
    }
}
