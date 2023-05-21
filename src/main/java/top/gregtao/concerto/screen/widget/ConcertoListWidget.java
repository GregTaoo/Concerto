package top.gregtao.concerto.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.MetaData;

import java.util.List;

public class ConcertoListWidget<T extends WithMetaData> extends AlwaysSelectedEntryListWidget<ConcertoListWidget<T>.Entry> {
    private int color = 0xffffffff;

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight) {
        super(MinecraftClient.getInstance(), width, height, top, bottom, itemHeight);
    }

    public ConcertoListWidget(int width, int height, int top, int bottom, int itemHeight, int color) {
        this(width, height, top, bottom, itemHeight);
        this.color = color;
    }

    public void reset(List<T> list, Music selected) {
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

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.width - 10;
    }

    @Override
    protected void renderBackground(MatrixStack matrices) {
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
    }

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        public T item;
        public int index;
        public Entry(T item, int index) {
            this.item = item;
            this.index = index;
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient.getInstance().textRenderer.draw(matrices, this.getNarration(), x, y + 3, ConcertoListWidget.this.color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                this.onPressed();
                return true;
            }
            return false;
        }

        private void onPressed() {
            ConcertoListWidget.this.setSelected(this);
        }

        @Override
        public Text getNarration() {
            MetaData meta = this.item.getMeta();
            return Text.literal(meta.title() + " - " + meta.author());
        }
    }
}
