package top.gregtao.concerto.screen.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.MetaData;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.Pair;

import java.util.List;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class MainPlaylistWidget extends MetadataListWidget<MainPlaylistWidget.Entry> {
    private static final int ACTION_BUTTON_WIDTH = 14;
    private final Consumer<Entry> titleEditor;
    private int actionStartX = -1;
    private int actionY = -1;
    private int actionCount;


    public record Entry(UUID index, Music music) implements WithMetaData {

        @Override
        public MetaData getMeta() {
            return this.music.getMeta();
        }

        @Override
        public boolean isMetaLoaded() {
            return this.music.isMetaLoaded();
        }
    }

    public MainPlaylistWidget(int width, int height, int top, int itemHeight, Consumer<Entry> titleEditor) {
        super(width, height, top, itemHeight);
        this.titleEditor = titleEditor;
        this.reset();
    }

    @Override
    public void onDoubleClicked(ConcertoListWidget<Entry>.Entry entry) {
        MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index);
    }


    @Override
    protected boolean handleEntryClick(ConcertoListWidget<Entry>.Entry entry, double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < this.actionStartX || mouseY < this.actionY || mouseY >= this.actionY + ACTION_BUTTON_WIDTH) return false;
        int actionIndex = (int) (mouseX - this.actionStartX) / ACTION_BUTTON_WIDTH;
        if (actionIndex < 0 || actionIndex >= this.actionCount) return false;
        if (actionIndex == 0) {
            MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index);
            return true;
        }
        if (actionIndex == 1 && PlayerPermissions.canModifyMusicList()) {
            this.titleEditor.accept(entry.item);
            return true;
        }
        if (actionIndex == 2 && PlayerPermissions.canModifyMusicList()) {
            MusicPlayerHandler.INSTANCE.removeAsync(entry.item.index, () -> {
            });
            return true;
        }
        return false;
    }

    @Override
    protected void renderEntry(ConcertoListWidget<Entry>.Entry entry, GuiGraphics graphics, int y, int x,
                               int entryWidth, int mouseX, int mouseY, boolean hovered, float delta) {
        if (!entry.item.isMetaLoaded()) {
            super.renderEntry(entry, graphics, y, x, entryWidth, mouseX, mouseY, hovered, delta);
            return;
        }

        String author = entry.item.getMeta().author();
        this.actionCount = this.actionCount();
        int actionRightX = this.getRowRight() - 3; // Vanilla's one-pixel inner selection edge.
        int actionStartX = actionRightX - this.actionCount * ACTION_BUTTON_WIDTH;
        int authorWidth = author.isEmpty() ? 0 : this.minecraft.font.width(author) + 4;
        int titleWidth = Math.max(0, actionStartX - x - authorWidth - 4);
        String title = this.ellipsize(entry.item.getMeta().title(), titleWidth);
        graphics.drawString(this.minecraft.font, title, x, y + 3, 0xffffffff, false);
        if (!author.isEmpty()) {
            graphics.drawString(this.minecraft.font, Component.literal(author).withStyle(ChatFormatting.BOLD, ChatFormatting.GRAY),
                    x + this.minecraft.font.width(title) + 4, y + 3, 0xffffffff, false);
        }

        if (hovered && this.actionCount > 0) {
            this.actionStartX = actionStartX;
            this.actionY = y;
            this.renderPlayButton(graphics, this.actionStartX, y, this.isActionHovered(mouseX, mouseY, this.actionStartX, y));
            if (this.actionCount > 1) {
                int editX = this.actionStartX + ACTION_BUTTON_WIDTH;
                int trashX = editX + ACTION_BUTTON_WIDTH;
                this.renderEditButton(graphics, editX, y, this.isActionHovered(mouseX, mouseY, editX, y));
                this.renderTrashButton(graphics, trashX, y, this.isActionHovered(mouseX, mouseY, trashX, y));
            }
        }
    }



    private int actionCount() {
        return PlayerPermissions.canModifyMusicList() ? 3 : 1;
    }

    private boolean isActionHovered(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + ACTION_BUTTON_WIDTH
                && mouseY >= y && mouseY < y + ACTION_BUTTON_WIDTH;
    }

    private void renderActionBackground(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.fill(x, y, x + ACTION_BUTTON_WIDTH, y + ACTION_BUTTON_WIDTH,
                hovered ? 0xb0303030 : 0x80606060);
    }

    private void renderPlayButton(GuiGraphics graphics, int x, int y, boolean hovered) {
        this.renderActionBackground(graphics, x, y, hovered);
        graphics.drawCenteredString(this.minecraft.font, Component.literal("▶"), x + ACTION_BUTTON_WIDTH / 2, y + 3,
                0xffffffff);
    }

    private void renderEditButton(GuiGraphics graphics, int x, int y, boolean hovered) {
        this.renderActionBackground(graphics, x, y, hovered);
        graphics.drawCenteredString(this.minecraft.font, Component.literal("✎"), x + ACTION_BUTTON_WIDTH / 2, y + 3,
                0xffffffff);
    }

    private void renderTrashButton(GuiGraphics graphics, int x, int y, boolean hovered) {
        this.renderActionBackground(graphics, x, y, hovered);
        graphics.drawCenteredString(this.minecraft.font, Component.literal("🗑"), x + ACTION_BUTTON_WIDTH / 2, y + 3,
                0xffffffff);
    }

    public static Pair<List<Entry>, Entry> loadFromMusicList(ConcertoPlayerList list, UUID current) {
        AtomicReference<Entry> entry = new AtomicReference<>(null);
        return Pair.of(list.stream().map((pair) -> {
            Entry newEntry = new Entry(pair.getFirst(), pair.getSecond());
            if (pair.getFirst().equals(current)) {
                entry.set(newEntry);
            }
            return newEntry;
        }).toList(), entry.get());
    }

    public static Pair<List<Entry>, Entry> loadFromMusicList() {
        return loadFromMusicList(MusicPlayerHandler.INSTANCE.getMusicList(), MusicPlayerHandler.INSTANCE.getCurrentIndex());
    }

    public void reset() {
        Pair<List<Entry>, Entry> pair = loadFromMusicList();
        super.reset(pair.getFirst(), pair.getSecond());
    }

    public void reset(String keyword) {
        Pair<List<Entry>, Entry> pair = loadFromMusicList();
        super.reset(pair.getFirst(), pair.getSecond(), keyword);
    }

    public void setSelected(UUID uuid) {
        this.children().stream()
                .filter(child -> child.item.index().equals(uuid))
                .findFirst()
                .ifPresent((entry) -> {
                    this.setSelected(entry);
                    this.centerScrollOn(entry);
                });
    }
}
