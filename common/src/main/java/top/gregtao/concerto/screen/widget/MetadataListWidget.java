package top.gregtao.concerto.screen.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.music.meta.MetaData;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataListWidget<T extends WithMetaData> extends ConcertoListWidget<T> {

    private final Set<T> loadingSet = ConcurrentHashMap.newKeySet();

    public MetadataListWidget(int width, int height, int top, int itemHeight) {
        this(width, height, top, itemHeight, 0xffffffff);
    }

    public MetadataListWidget(int width, int height, int top, int itemHeight, int color) {
        super(width, height, top, itemHeight, color);
    }

    @Override
    public Component getNarration(int index, T t) {
        if (t.isMetaLoaded()) {
            MetaData meta = t.getMeta();
            return Component.literal(meta.title()).append("  ").append(Component.literal(meta.author()).withStyle(ChatFormatting.BOLD, ChatFormatting.GRAY));
        } else {
            if (!this.loadingSet.contains(t)) {
                this.loadingSet.add(t);
                ConcertoRunner.run(t::getMeta, () -> this.loadingSet.remove(t));
            }
            return Component.translatable("concerto.loading");
        }
    }

    @Override
    protected void renderEntry(ConcertoListWidget<T>.Entry entry, GuiGraphics graphics, int y, int x,
                               int entryWidth, int mouseX, int mouseY, boolean hovered, float delta) {
        if (!entry.item.isMetaLoaded()) {
            super.renderEntry(entry, graphics, y, x, entryWidth, mouseX, mouseY, hovered, delta);
            return;
        }

        MetaData meta = entry.item.getMeta();
        String author = meta.author();
        int textRight = x + entryWidth - 10;
        int textWidth = Math.max(0, textRight - x);
        int authorWidth = author.isEmpty() ? 0 : Math.min(this.minecraft.font.width(author) + 4, textWidth);
        String title = this.ellipsize(meta.title(), Math.max(0, textWidth - authorWidth - 4));
        graphics.drawString(this.minecraft.font, title, x, y + 3, this.color, false);
        if (!author.isEmpty()) {
            int authorX = x + this.minecraft.font.width(title) + 4;
            String visibleAuthor = this.minecraft.font.plainSubstrByWidth(author, Math.max(0, textRight - authorX));
            graphics.drawString(this.minecraft.font, Component.literal(visibleAuthor).withStyle(ChatFormatting.BOLD, ChatFormatting.GRAY),
                    authorX, y + 3, this.color, false);
        }
    }

    protected final String ellipsize(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.minecraft.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = this.minecraft.font.width(ellipsis);
        if (ellipsisWidth > maxWidth) return this.minecraft.font.plainSubstrByWidth(text, maxWidth);
        return this.minecraft.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }
}
