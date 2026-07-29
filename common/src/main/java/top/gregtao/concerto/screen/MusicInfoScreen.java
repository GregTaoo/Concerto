package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.util.ArrayList;
import java.util.List;

public class MusicInfoScreen extends ConcertoScreen {

    private static final int COVER_SIZE = 104;
    private static final int PANEL_PADDING = 10;
    private static final int CONTENT_TOP = 22;
    private static final int CONTENT_BOTTOM = 35;
    private static final int LYRICS_HEADER_HEIGHT = 22;
    private static final int SCROLLBAR_GAP = 4;
    private static final int ACTION_COUNT = 4;

    private URLImageWidget headPicture;
    private final Music music;
    private Button playButton;
    private Button addButton;
    private MusicMetaData metadata;
    private Lyrics lyrics;
    private Lyrics subLyrics;
    private List<PreviewLine> wrappedLyrics = List.of();
    private Lyrics wrappedMainLyrics;
    private Lyrics wrappedSubLyrics;
    private int wrappedLyricsWidth = -1;
    private final AbstractScrollArea lyricScrollbar = new AbstractScrollArea(0, 0, 0, 0, Component.empty()) {
        @Override
        protected int contentHeight() {
            return MusicInfoScreen.this.wrappedLyrics.size() * 10 + 8;
        }

        @Override
        protected double scrollRate() {
            return 12;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.renderScrollbar(graphics);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    };
    private int lyricPanelX;
    private int lyricPanelY;
    private int lyricPanelWidth;
    private int lyricPanelHeight;

    private record PreviewLine(FormattedCharSequence text, int color) {
    }

    public MusicInfoScreen(Music music, Screen parent) {
        super(Component.translatable("concerto.screen.info"), parent);
        this.music = music;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = this.standardContentWidth();
        int infoPanelWidth = Math.max(COVER_SIZE + PANEL_PADDING * 2, (contentWidth - STANDARD_ACTION_GAP) / 3);
        int coverX = this.standardContentX() + (infoPanelWidth - COVER_SIZE) / 2;
        this.headPicture = new URLImageWidget(COVER_SIZE, COVER_SIZE, coverX, CONTENT_TOP + PANEL_PADDING, null, false);
        ConcertoRunner.run(this::loadInfo);
        ConcertoRunner.run(this::loadLyrics);

        int y = this.standardBottomActionY();
        int actionWidth = (this.standardContentWidth() - STANDARD_ACTION_GAP * (ACTION_COUNT - 1)) / ACTION_COUNT;
        this.playButton = Button.builder(Component.translatable("concerto.screen.play"),
                button -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(this.music, true, () -> {
                })).pos(this.standardContentX(), y).size(actionWidth, 20).build();
        this.addRenderableWidget(this.playButton);

        this.addButton = Button.builder(Component.translatable("concerto.screen.add"),
                button -> MusicPlayerHandler.INSTANCE.addMusicAsync(this.music, false, () -> {
                })).pos(this.standardContentX() + actionWidth + STANDARD_ACTION_GAP, y).size(actionWidth, 20).build();
        this.addRenderableWidget(this.addButton);
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.request"), button ->
                ServerMusicAgentManager.clientAddMusic(this.music)).pos(this.standardContentX() + (actionWidth + STANDARD_ACTION_GAP) * 2, y)
                .size(actionWidth, 20).build());


        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.copy_link"), button -> {
            if (this.minecraft != null) {
                this.minecraft.keyboardHandler.setClipboard(this.music.getLink());
            }
        }).pos(this.standardContentX() + (actionWidth + STANDARD_ACTION_GAP) * 3, y)
                .size(this.standardContentRight() - this.standardContentX() - (actionWidth + STANDARD_ACTION_GAP) * 3, 20).build());

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.playButton.active = PlayerPermissions.canModifyMusicList();
        this.addButton.active = PlayerPermissions.canModifyMusicList();
    }

    private void loadInfo() {
        MusicMetaData loadedMeta = this.music.getMeta();
        Minecraft.getInstance().execute(() -> {
            if (this.minecraft == null || this.minecraft.screen != this) return;
            this.metadata = loadedMeta;
            if (!loadedMeta.headPictureUrl().isEmpty()) {
                this.headPicture.setUrl(loadedMeta.headPictureUrl());
                this.headPicture.loadImageAsync(false, ClientConfig.INSTANCE.options.coverImgInCircle);
            }
        });
    }

    private void loadLyrics() {
        Lyrics loadedLyrics = null;
        Lyrics loadedSubLyrics = null;
        try {
            Pair<Lyrics, Lyrics> loaded = this.music.getLyrics();
            if (loaded != null) {
                loadedLyrics = loaded.getFirst();
                loadedSubLyrics = loaded.getSecond();
            }
        } catch (Exception ignored) {
        }
        Lyrics mainLyrics = loadedLyrics;
        Lyrics translatedLyrics = loadedSubLyrics;
        Minecraft.getInstance().execute(() -> {
            if (this.minecraft == null || this.minecraft.screen != this) return;
            this.lyrics = mainLyrics;
            this.subLyrics = translatedLyrics;
            this.wrappedLyrics = List.of();
            this.wrappedMainLyrics = null;
            this.wrappedSubLyrics = null;
            this.wrappedLyricsWidth = -1;
            this.lyricScrollbar.setScrollAmount(0);
        });
    }

    @Override
    public void onClose() {
        super.onClose();
        this.headPicture.close();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        this.updateButtonStates();

        int contentWidth = this.standardContentWidth();
        int infoPanelWidth = Math.max(COVER_SIZE + PANEL_PADDING * 2, (contentWidth - STANDARD_ACTION_GAP) / 3);
        int panelHeight = Math.max(20, this.height - CONTENT_TOP - CONTENT_BOTTOM);
        this.lyricPanelX = this.standardContentX() + infoPanelWidth + STANDARD_ACTION_GAP;
        this.lyricPanelY = CONTENT_TOP;
        this.lyricPanelWidth = this.standardContentRight() - this.lyricPanelX;
        this.lyricPanelHeight = panelHeight;

        this.renderInfoPanel(graphics, this.standardContentX(), CONTENT_TOP, infoPanelWidth, panelHeight);
        this.headPicture.render(graphics, mouseX, mouseY, delta);
        this.renderLyrics(graphics);
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xff606060);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x80000000);
    }

    private void renderInfoPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        this.renderPanel(graphics, x, y, width, height);
        int textX = x + PANEL_PADDING;
        int textWidth = width - PANEL_PADDING * 2;
        int textBottom = y + height - PANEL_PADDING;
        int textY = this.headPicture.getY() + COVER_SIZE + 8;
        MusicMetaData meta = this.metadata;
        if (meta == null) {
            graphics.drawString(this.font, Component.translatable("concerto.screen.loading"), textX, textY, 0xffaaaaaa, false);
            return;
        }

        graphics.drawString(this.font, this.font.plainSubstrByWidth(meta.getSource(), textWidth), textX, textY, 0xff55ffff, false);
        textY += 14;
        int titleBottom = textBottom - (meta.author().isEmpty() ? 0 : 12);
        for (FormattedCharSequence line : this.font.split(Component.literal(meta.title()), textWidth)) {
            if (textY >= titleBottom) break;
            graphics.drawString(this.font, line, textX, textY, 0xffffffff);
            textY += 10;
        }
        if (!meta.author().isEmpty()) {
            textY = Math.min(textY + 2, textBottom - 10);
            graphics.drawString(this.font, this.ellipsize(meta.author(), textWidth), textX, textY, 0xffaaaaaa, false);
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= this.lyricPanelX && mouseX < this.lyricPanelX + this.lyricPanelWidth
                && mouseY >= this.lyricPanelY && mouseY < this.lyricPanelY + this.lyricPanelHeight) {
            return this.lyricScrollbar.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.lyricScrollbar.updateScrolling(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.lyricScrollbar.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.lyricScrollbar.onRelease(mouseX, mouseY);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int lyricContentY() {
        return this.lyricPanelY + LYRICS_HEADER_HEIGHT;
    }

    private int lyricContentHeight() {
        return this.lyricPanelHeight - LYRICS_HEADER_HEIGHT - PANEL_PADDING;
    }

    private String ellipsize(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width(ellipsis))) + ellipsis;
    }

    private void rebuildWrappedLyrics(int innerWidth) {
        List<PreviewLine> lines = new ArrayList<>();
        if (this.lyrics != null && !this.lyrics.isEmpty()) {
            List<Pair<MusicTimestamp, String>> mainLines = this.lyrics.getLyricBody();
            List<Pair<MusicTimestamp, String>> translatedLines = this.subLyrics == null ? List.of() : this.subLyrics.getLyricBody();
            for (int i = 0; i < mainLines.size(); i++) {
                for (FormattedCharSequence line : this.font.split(Component.literal(mainLines.get(i).getSecond()), innerWidth)) {
                    lines.add(new PreviewLine(line, 0xffdddddd));
                }
                if (i < translatedLines.size() && !translatedLines.get(i).getSecond().isBlank()) {
                    for (FormattedCharSequence line : this.font.split(Component.literal(translatedLines.get(i).getSecond()), innerWidth)) {
                        lines.add(new PreviewLine(line, 0xff999999));
                    }
                }
            }
        }
        this.wrappedLyrics = List.copyOf(lines);
        this.wrappedMainLyrics = this.lyrics;
        this.wrappedSubLyrics = this.subLyrics;
        this.wrappedLyricsWidth = innerWidth;
    }

    private void renderLyrics(GuiGraphics graphics) {
        this.renderPanel(graphics, this.lyricPanelX, this.lyricPanelY, this.lyricPanelWidth, this.lyricPanelHeight);
        int contentX = this.lyricPanelX + PANEL_PADDING;
        int contentY = this.lyricContentY();
        int contentWidth = this.lyricPanelWidth - PANEL_PADDING * 2;
        int contentHeight = this.lyricContentHeight();
        int lyricTextWidth = contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH - SCROLLBAR_GAP;
        graphics.drawString(this.font, Component.translatable("concerto.screen.lyrics_preview"), contentX,
                this.lyricPanelY + 7, 0xffaaaaaa, false);
        graphics.fill(contentX, this.lyricPanelY + LYRICS_HEADER_HEIGHT - 4, contentX + contentWidth,
                this.lyricPanelY + LYRICS_HEADER_HEIGHT - 3, 0xff606060);

        if (lyricTextWidth <= 0 || contentHeight <= 0) return;
        if (this.wrappedLyricsWidth != lyricTextWidth || this.wrappedMainLyrics != this.lyrics || this.wrappedSubLyrics != this.subLyrics) {
            this.rebuildWrappedLyrics(lyricTextWidth);
        }
        this.lyricScrollbar.setRectangle(AbstractScrollArea.SCROLLBAR_WIDTH, contentHeight,
                contentX + contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH, contentY);
        this.lyricScrollbar.refreshScrollAmount();
        if (this.wrappedLyrics.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("concerto.no_subtitle"),
                    contentX + lyricTextWidth / 2, contentY + contentHeight / 2 - 4, 0xffaaaaaa);
            return;
        }

        graphics.enableScissor(contentX, contentY, contentX + lyricTextWidth, contentY + contentHeight);
        for (int i = 0; i < this.wrappedLyrics.size(); i++) {
            int y = contentY + 4 + i * 10 - (int) this.lyricScrollbar.scrollAmount();
            if (y > contentY - 10 && y < contentY + contentHeight) {
                PreviewLine line = this.wrappedLyrics.get(i);
                graphics.drawString(this.font, line.text(), contentX, y, line.color());
            }
        }
        graphics.disableScissor();
        this.lyricScrollbar.render(graphics, 0, 0, 0);
    }

}
