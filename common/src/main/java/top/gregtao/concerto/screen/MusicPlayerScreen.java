package top.gregtao.concerto.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.enums.TextAlignment;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.mixin.GuiGraphicsAccessor;
import top.gregtao.concerto.screen.widget.VolumeControlWidget;

import java.util.ArrayList;

public class MusicPlayerScreen extends ConcertoScreen {

    private static final int MODE_BUTTON_W = 80;
    private static final int PLAYLIST_BUTTON_W = 96;
    private static final int ORDER_BUTTON_W = 80;
    private static final int ICON_BUTTON_W = 20;
    private static final int BUTTON_GAP = 2;

    private CycleButton<PlayerView> playerViewButton;
    private Button previousButton;
    private Button playPauseButton;
    private Button nextButton;
    private CycleButton<OrderType> orderButton;
    private VolumeControlWidget volumeControl;

    private float rotationAngle = 0f;
    private float scrollOffset = 0f;
    private PlayerView playerView = PlayerView.COVER;
    private boolean seekingProgress = false;
    // Drag preview target; -1 when not dragging. Rendering-only: the real
    // display state (lyrics cursor, progress) is untouched until commit, so
    // the bar doesn't fight the engine's position updates while dragging.
    private long dragPreviewMillis = -1;

    public MusicPlayerScreen(Screen parent) {
        super(Component.empty(), parent);
    }

    @Override
    protected void init() {
        super.init();

        int y = this.height - 30;
        int totalWidth = MODE_BUTTON_W + PLAYLIST_BUTTON_W + 4 * ICON_BUTTON_W + ORDER_BUTTON_W + 6 * BUTTON_GAP;
        int x = (this.width - totalWidth) / 2;

        this.previousButton = Button.builder(
                Component.literal("⏮"),
                button -> MusicPlayerHandler.INSTANCE.playPreviousAsync()
        ).pos(x, y).size(ICON_BUTTON_W, 20).build();
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.playPauseButton = Button.builder(
                this.playPauseLabel(),
                button -> MusicPlayerHandler.INSTANCE.tryForcePause(!MusicPlayerHandler.INSTANCE.isPaused())
        ).pos(x, y).size(ICON_BUTTON_W, 20).build();
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.nextButton = Button.builder(
                Component.literal("⏭"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)
        ).pos(x, y).size(ICON_BUTTON_W, 20).build();
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.playerViewButton = CycleButton.<PlayerView>builder(view -> Component.translatable(
                        view == PlayerView.COVER ? "concerto.screen.player_view.cover" : "concerto.screen.player_view.lyrics"))
                .withValues(PlayerView.values())
                .withInitialValue(this.playerView)
                .create(x, y, MODE_BUTTON_W, 20, Component.translatable("concerto.screen.player_view"), (button, view) -> {
                    this.playerView = view;
                    this.scrollOffset = 0;
                });
        this.addRenderableWidget(this.playerViewButton);
        x += MODE_BUTTON_W + BUTTON_GAP;

        Button playlistButton = Button.builder(
                Component.translatable("concerto.screen.main_list"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MainPlaylistScreen(this));
                    }
                }
        ).pos(x, y).size(PLAYLIST_BUTTON_W, 20).build();
        this.addRenderableWidget(playlistButton);
        x += PLAYLIST_BUTTON_W + BUTTON_GAP;

        this.orderButton = CycleButton.builder((OrderType val) -> Component.literal(val.getName()))
                .withValues(OrderType.values())
                .withInitialValue(MusicPlayerHandler.INSTANCE.getOrderType())
                .create(x, y, ORDER_BUTTON_W, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        x += ORDER_BUTTON_W + BUTTON_GAP;

        this.volumeControl = new VolumeControlWidget(this.font, x, y, ICON_BUTTON_W, 20);
        this.addWidget(this.volumeControl);

        this.addRenderableWidget(this.previousButton);
        this.addRenderableWidget(this.playPauseButton);
        this.addRenderableWidget(this.nextButton);
        this.addRenderableWidget(this.orderButton);

        this.updateButtonStates();
    }

    // Called every frame: hotkey pauses, room-driven pauses and permission
    // changes must reach an already-open screen (init-only refresh went stale)
    private void updateButtonStates() {
        this.playPauseButton.active = PlayerPermissions.canControlPlayback();
        this.previousButton.active = PlayerPermissions.canChangeMusicIndex()
                && MusicPlayerHandler.INSTANCE.canPlayPrevious();
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex()
                && !MusicPlayerHandler.INSTANCE.isEmpty();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();

        boolean isPaused = MusicPlayerHandler.INSTANCE.isPaused();
        this.playPauseButton.setMessage(this.playPauseLabel());
    }

    private Component playPauseLabel() {
        return Component.literal(MusicPlayerHandler.INSTANCE.isPaused() ? "▶" : "⏸");
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.updateButtonStates();

        MusicMetaData metaData = MusicPlayer.INSTANCE.currentMeta;
        boolean hasTrack = (MusicPlayer.INSTANCE.started || MusicPlayer.INSTANCE.isOpened()) && metaData != null;
        if (!hasTrack) {
            Component text = MusicPlayer.INSTANCE.isPreparing()
                    ? Component.translatable("concerto.player.preparing")
                    : Component.translatable("concerto.not_playing");
            context.drawCenteredString(this.font, text, this.width / 2, this.height / 2, 0xAAAAAAFF);
            this.volumeControl.render(context, mouseX, mouseY, delta);
            return;
        }

        // The disc only spins while audio is actually advancing — not while
        // paused (incl. force-pause) or stalled in a buffer wait
        if (MusicPlayer.INSTANCE.isPlaying() && !MusicPlayer.INSTANCE.isSeeking()
                && !MusicPlayerHandler.INSTANCE.isPaused()) {
            this.rotationAngle += delta * 0.3f;
            if (this.rotationAngle >= 360f) this.rotationAngle -= 360f;
        }

        boolean lyricsOnly = this.playerView == PlayerView.LYRICS;
        if (!lyricsOnly) {
            int leftWidth = this.width / 2;
            int imgSize = Math.max(96, Math.min(120, leftWidth - 60));
            int imgX = (leftWidth - imgSize) / 2;
            int imgY = (this.height - imgSize) / 2 - 8;

            Matrix3x2fStack matrices = context.pose();
            matrices.pushMatrix();

            InGameHudRenderer.COVER_IMAGE.setX(imgX);
            InGameHudRenderer.COVER_IMAGE.setY(imgY);
            InGameHudRenderer.COVER_IMAGE.setSize(imgSize, imgSize);

            matrices.translate(imgX + imgSize / 2f, imgY + imgSize / 2f);
            matrices.rotate(this.rotationAngle * (float) Math.PI / 180f); // 旋转
            matrices.translate(-(imgX + imgSize / 2f), -(imgY + imgSize / 2f));

            InGameHudRenderer.COVER_IMAGE.render(context, mouseX, mouseY, delta);

            matrices.popMatrix();
        }

        int rightHalfX = lyricsOnly ? 20 : this.width / 2;
        int lyricsWidth = this.width - rightHalfX - 20;

        String title = metaData.title();
        String author = metaData.author();
        int centerX = this.width / 2;
        int titleY = 8;
        context.drawString(this.font, title, centerX - this.font.width(title) / 2, titleY, 0xFFFFFFFF, false);
        if (author != null && !author.isEmpty()) {
            int authorY = titleY + 12;
            context.drawString(this.font, author, centerX - this.font.width(author) / 2, authorY, 0xFFAAAAAA, false);
        }

        renderTopProgressBar(context, mouseX, mouseY);
        renderStatusHint(context);

        Lyrics currentLyrics = MusicPlayer.INSTANCE.currentLyrics;
        Lyrics currentSubLyrics = MusicPlayer.INSTANCE.currentSubLyrics;
        if (currentLyrics != null && !currentLyrics.isEmpty()) {
            ArrayList<Pair<MusicTimestamp, String>> lyrics = currentLyrics.getLyricBody();
            ArrayList<Pair<MusicTimestamp, String>> subLyrics = currentSubLyrics != null ? currentSubLyrics.getLyricBody() : null;

            long currentTime = MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();

            int activeIndex = currentLyrics.getCurrentIndex();

            int lineHeight = subLyrics != null ? 27 : 17;
            int startY = 45;
            int endY = this.height - 40;

            int targetScrollOffset = (activeIndex * lineHeight) - ((endY - startY) / 2) + (lineHeight / 2);
            this.scrollOffset += (targetScrollOffset - this.scrollOffset) * 0.15f;

            context.enableScissor(rightHalfX, startY, this.width - 20, endY);
            for (int i = 0; i < lyrics.size(); i++) {
                String line = lyrics.get(i).getSecond();
                String subLine = null;
                int subIndex = -1;
                int[] subLyricsMapping = MusicPlayer.INSTANCE.currentSubLyricsMapping;
                if (subLyrics != null && i < subLyricsMapping.length) {
                    subIndex = subLyricsMapping[i];
                    if (subIndex >= 0 && subIndex < subLyrics.size()) {
                        subLine = subLyrics.get(subIndex).getSecond();
                    }
                }

                int y = startY + (i * lineHeight) - (int) this.scrollOffset;

                if (y > startY - lineHeight && y < endY + lineHeight) {
                    boolean isActive = (i == activeIndex);
                    int alpha = Math.max(15, 255 - Math.abs(i - activeIndex) * 35);

                    int color = isActive ? ((int) ClientConfig.INSTANCE.lyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0xAAAAAA);
                    long lineStart = currentLyrics.getLineStartMilliseconds(i);
                    long lineEnd = currentLyrics.getLineEndMilliseconds(i, metaData.getDuration());
                    InGameHudRenderer.renderTimedScrollableText(context, Component.literal(line),
                            TextAlignment.CENTER,
                            rightHalfX + lyricsWidth / 2, y, rightHalfX, lyricsWidth,
                            lineStart, currentTime, lineEnd, color, isActive);

                    if (subLine != null) {
                        int subColor = isActive ? ((int) ClientConfig.INSTANCE.subLyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0x888888);
                        int subY = y + 12;
                        long subLineStart = currentSubLyrics.getLineStartMilliseconds(subIndex);
                        long subLineEnd = currentSubLyrics.getLineEndMilliseconds(subIndex, metaData.getDuration());
                        InGameHudRenderer.renderTimedScrollableText(context, Component.literal(subLine),
                                TextAlignment.CENTER,
                                rightHalfX + lyricsWidth / 2, subY, rightHalfX, lyricsWidth,
                                subLineStart, currentTime, subLineEnd, subColor, false);
                    }
                }
            }
            context.disableScissor();
        } else {
            int placeholderY = this.height / 2;
            context.drawCenteredString(this.font, Component.translatable("concerto.no_subtitle"), rightHalfX + lyricsWidth / 2, placeholderY, 0xFFAAAAAA);
        }
        if (!lyricsOnly) renderSpectrum(context);
        this.volumeControl.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.volumeControl.collapseIfClickedOutside(mouseX, mouseY);
        if (button == 0 && this.isOverProgressBar(mouseX, mouseY) && this.seekProgress(mouseX, false)) {
            this.seekingProgress = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.seekingProgress && button == 0) {
            return this.seekProgress(mouseX, false);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.seekingProgress && button == 0) {
            this.seekingProgress = false;
            return this.seekProgress(mouseX, true);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverProgressBar(double mouseX, double mouseY) {
        return PlayerPermissions.canControlPlayback() && MusicPlayer.INSTANCE.canSeekCurrentMusic()
                && mouseX >= 0 && mouseX <= this.width && mouseY >= 0 && mouseY <= 8;
    }

    private boolean seekProgress(double mouseX, boolean commit) {
        long durationMs = MusicPlayer.INSTANCE.getEffectiveDurationMillis();
        if (durationMs <= 0 || !PlayerPermissions.canControlPlayback()
                || !MusicPlayer.INSTANCE.canSeekCurrentMusic()) {
            this.dragPreviewMillis = -1;
            return false;
        }
        double progress = this.width <= 0 ? 0D : Math.max(0D, Math.min(1D, mouseX / this.width));
        long targetMs = (long) (durationMs * progress);
        if (commit) {
            this.dragPreviewMillis = -1;
            MusicPlayer.INSTANCE.seekToMillisecondsAsync(targetMs);
        } else {
            this.dragPreviewMillis = targetMs;
        }
        return true;
    }

    private void renderTopProgressBar(GuiGraphics context, int mouseX, int mouseY) {
        long durationMs = MusicPlayer.INSTANCE.getEffectiveDurationMillis();

        // Time readout is useful even without a bar (unknown duration)
        long shownMs = this.dragPreviewMillis >= 0
                ? this.dragPreviewMillis : MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();
        String timeText = MusicTimestamp.ofMilliseconds(shownMs).toShortString()
                + (durationMs > 0 ? " / " + MusicTimestamp.ofMilliseconds(durationMs).toShortString() : "");
        context.drawString(this.font, timeText, 6, 8, 0xFFAAAAAA, false);

        if (durationMs <= 0) return;

        boolean hovered = this.seekingProgress || this.isOverProgressBar(mouseX, mouseY);
        int barHeight = hovered ? 4 : 2;
        double progress = this.dragPreviewMillis >= 0
                ? (double) this.dragPreviewMillis / durationMs
                : MusicPlayer.INSTANCE.progressPercentage;
        progress = Math.max(0.0D, Math.min(1.0D, progress));

        int bgColor = (int) ClientConfig.INSTANCE.timeProgressBgColor.getNumber();
        int progressColor = (int) ClientConfig.INSTANCE.timeProgressColor.getNumber();

        context.fill(0, 0, this.width, barHeight, bgColor);
        float buffered = MusicPlayer.INSTANCE.getBufferedPercentage();
        if (buffered > 0) {
            int bufferedColor = (progressColor & 0x00FFFFFF) | 0x66000000;
            context.fill(0, 0, (int) Math.round(this.width * buffered), barHeight, bufferedColor);
        }
        context.fill(0, 0, (int) Math.round(this.width * progress), barHeight, progressColor);

        if (hovered) {
            long targetMs = this.seekingProgress && this.dragPreviewMillis >= 0
                    ? this.dragPreviewMillis
                    : (long) (durationMs * Math.max(0D, Math.min(1D, (double) mouseX / this.width)));
            int thumbX = (int) Math.round(this.width * (this.seekingProgress ? progress : (double) targetMs / durationMs));
            context.fill(thumbX - 1, 0, thumbX + 1, barHeight + 3, 0xFFFFFFFF);
            String target = MusicTimestamp.ofMilliseconds(targetMs).toShortString();
            int textX = Math.max(2, Math.min(this.width - this.font.width(target) - 2, thumbX - this.font.width(target) / 2));
            context.fill(textX - 2, barHeight + 3, textX + this.font.width(target) + 2,
                    barHeight + this.font.lineHeight + 7, 0xC0101010);
            context.drawString(this.font, target, textX, barHeight + 5, 0xFF55FFFF, false);
        }
    }

    private void renderStatusHint(GuiGraphics context) {
        Component hint = null;
        if (MusicPlayer.INSTANCE.isPreparing()) {
            hint = Component.translatable("concerto.player.preparing");
        } else if (MusicPlayer.INSTANCE.isSeeking()) {
            hint = Component.translatable("concerto.player.buffering");
        }
        if (hint != null) {
            context.drawCenteredString(this.font, hint, this.width / 2, 32, 0xFFAAAAAA);
        }
    }

    private static final int SPECTRUM_BAR_COUNT = 64;

    private enum PlayerView {
        COVER,
        LYRICS
    }

    private void renderSpectrum(GuiGraphics g) {
        MusicPlayer.INSTANCE.audioSpectrum.update();
        float[] spectrumBars = MusicPlayer.INSTANCE.audioSpectrum.getSpectrum(SPECTRUM_BAR_COUNT);
        int barCount = spectrumBars.length;

        int leftWidth = this.width / 2;
        int imageSize = Math.max(96, Math.min(120, leftWidth - 60));
        int imageX = (leftWidth - imageSize) / 2;
        int imageY = (this.height - imageSize) / 2 - 8;
        float centerX = imageX + imageSize / 2f;
        float centerY = imageY + imageSize / 2f;

        float radiusInner = imageSize / 2f + 8f;
        float radiusOuter = radiusInner + 38f;
        float maxBarLength = radiusOuter - radiusInner;

        float angleStep = 360f / barCount;
        float spanDegrees = angleStep * 0.85f;

        GuiGraphicsAccessor accessor = (GuiGraphicsAccessor) g;
        Matrix3x2fStack matrix = g.pose();

        for (int i = 0; i < barCount; i++) {
            float value = spectrumBars[i];

            accessor.getGuiRenderState().submitGuiElement(
                    new SpectrumQuadRenderState(
                            RenderPipelines.GUI,
                            TextureSetup.noTexture(),
                            new Matrix3x2f(matrix),
                            i, value, centerX, centerY, radiusInner, maxBarLength, angleStep, spanDegrees
                    )
            );
        }
    }

    public record SpectrumQuadRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            int index, float value, float centerX, float centerY, float radiusInner, float maxBarLength,
            float angleStep, float spanDegrees,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds
    ) implements GuiElementRenderState {

        public SpectrumQuadRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, int index, float value, float centerX, float centerY, float radiusInner, float maxBarLength, float angleStep, float spanDegrees) {
            this(pipeline, textureSetup, pose, index, value, centerX, centerY, radiusInner, maxBarLength, angleStep, spanDegrees, null,
                    new ScreenRectangle((int) (centerX - (radiusInner + maxBarLength) - 10), (int) (centerY - (radiusInner + maxBarLength) - 10), (int) ((radiusInner + maxBarLength) * 2 + 20), (int) ((radiusInner + maxBarLength) * 2 + 20)));
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer, float f) {
            float val = Float.isNaN(this.value) ? 0f : this.value;

            float dynamicScale = (float) (1.3f * Math.log10(1.0 + val * 60.0));

            float barLength = dynamicScale * (this.maxBarLength / 1.5f);
            barLength = Math.max(2f, Math.min(barLength, this.maxBarLength));

            float currentOuterRadius = this.radiusInner + barLength;

            float alphaFactor = 0.35f + 0.65f * Math.min(dynamicScale, 1.0f);
            int a = (int) (alphaFactor * 255f);
            int r = 150, green = 200, b = 255;
            int color = (a << 24) | (r << 16) | (green << 8) | b;

            float centerAngle = (this.index * this.angleStep) - 90f;
            float a1 = (float) Math.toRadians(centerAngle - this.spanDegrees / 2f);
            float a2 = (float) Math.toRadians(centerAngle + this.spanDegrees / 2f);

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            float x1 = this.centerX + cos1 * this.radiusInner;
            float y1 = this.centerY + sin1 * this.radiusInner;
            float x2 = this.centerX + cos1 * currentOuterRadius;
            float y2 = this.centerY + sin1 * currentOuterRadius;
            float x3 = this.centerX + cos2 * currentOuterRadius;
            float y3 = this.centerY + sin2 * currentOuterRadius;
            float x4 = this.centerX + cos2 * this.radiusInner;
            float y4 = this.centerY + sin2 * this.radiusInner;

            vertexConsumer.addVertexWith2DPose(this.pose(), x1, y1, f).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x4, y4, f).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x3, y3, f).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x2, y2, f).setColor(color);
        }
    }

}
