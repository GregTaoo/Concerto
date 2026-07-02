package top.gregtao.concerto.screen;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
import top.gregtao.concerto.screen.widget.VolumeButton;
import top.gregtao.concerto.screen.widget.VolumeSliderWidget;

import java.util.ArrayList;

public class MusicPlayerScreen extends ConcertoScreen {

    private Button playPauseButton;
    private Button nextButton;
    private CycleButton<OrderType> orderButton;
    private VolumeButton volumeButton;
    private VolumeSliderWidget volumeSlider;

    private float rotationAngle = 0f;
    private int scrollOffset = 0;
    private boolean volumeSliderVisible = false;
    private boolean seekingProgress = false;

    public MusicPlayerScreen(Screen parent) {
        super(Component.empty(), parent);
    }

    @Override
    protected void init() {
        super.init();

        int y = this.height - 30;
        int totalWidth = 4 * 80 + 20 + 4 * 2;
        int x = (this.width - totalWidth) / 2;

        Button playlistButton = Button.builder(
                Component.translatable("concerto.screen.general_list"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new GeneralPlaylistScreen(this));
                    }
                }
        ).pos(x, y).size(80, 20).build();
        this.addRenderableWidget(playlistButton);
        x += 82;

        this.playPauseButton = Button.builder(
                Component.translatable(MusicPlayerHandler.INSTANCE.getState().get().paused ? "concerto.screen.play" : "concerto.screen.pause"),
                button -> {
                    boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
                    MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
                    button.setMessage(Component.translatable(!paused ? "concerto.screen.play" : "concerto.screen.pause"));
                }
        ).pos(x, y).size(80, 20).build();
        x += 82;

        this.nextButton = Button.builder(
                Component.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)
        ).pos(x, y).size(80, 20).build();
        x += 82;

        this.orderButton = CycleButton.builder((OrderType val) -> Component.literal(val.getName()))
                .withValues(OrderType.values())
                .withInitialValue(MusicPlayerHandler.INSTANCE.getOrderType())
                .create(x, y, 80, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        x += 82;

        this.volumeButton = new VolumeButton(
                x, y, 20, 20,
                button -> this.setVolumeSliderVisible(!this.volumeSliderVisible)
        );

        this.volumeSlider = new VolumeSliderWidget(this.font, x, y - 86, 20, 84);
        this.volumeSlider.visible = false;

        this.addRenderableWidget(this.playPauseButton);
        this.addRenderableWidget(this.nextButton);
        this.addRenderableWidget(this.orderButton);

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.playPauseButton.active = PlayerPermissions.canControlPlayback();
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();

        boolean isPaused = MusicPlayerHandler.INSTANCE.getState().get().paused;
        this.playPauseButton.setMessage(Component.translatable(isPaused ? "concerto.screen.play" : "concerto.screen.pause"));
    }

    private void setVolumeSliderVisible(boolean visible) {
        this.volumeSliderVisible = visible;
        this.volumeSlider.visible = visible;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        MusicMetaData metaData = MusicPlayer.INSTANCE.currentMeta;
        if ((!MusicPlayer.INSTANCE.started && !MusicPlayer.INSTANCE.isSeeking() && !MusicPlayer.INSTANCE.isOpened()) || metaData == null) {
            context.drawCenteredString(this.font, Component.translatable("concerto.not_playing"), this.width / 2, this.height / 2, 0xAAAAAAFF);
            this.renderVolumeControls(context, mouseX, mouseY, delta);
            return;
        }

        if (!MusicPlayerHandler.INSTANCE.getState().get().paused) {
            this.rotationAngle += delta * 0.3f;
            if (this.rotationAngle >= 360f) this.rotationAngle -= 360f;
        }

        int leftWidth = this.width / 2;
        int imgSize = Math.max(96, Math.min(120, leftWidth - 60));
        int imgX = (leftWidth - imgSize) / 2;
        int imgY = (this.height - imgSize) / 2 - 8;

        context.pose().pushPose();

        InGameHudRenderer.COVER_IMAGE.setX(imgX);
        InGameHudRenderer.COVER_IMAGE.setY(imgY);
        InGameHudRenderer.COVER_IMAGE.setSize(imgSize, imgSize);

        context.pose().translate(imgX + imgSize / 2f, imgY + imgSize / 2f, 0);
        context.pose().mulPose(new Quaternionf().rotateZ(this.rotationAngle * (float) Math.PI / 180f)); // 旋转
        context.pose().translate(-(imgX + imgSize / 2f), -(imgY + imgSize / 2f), 0);

        InGameHudRenderer.COVER_IMAGE.render(context, mouseX, mouseY, delta);

        context.pose().popPose();

        int rightHalfX = this.width / 2; // lyrics start at mid-screen for >= 1/2 width
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

        renderTopProgressBar(context, metaData);

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
            this.scrollOffset += (int) ((targetScrollOffset - this.scrollOffset) * 0.15f);

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

                int y = startY + (i * lineHeight) - this.scrollOffset;

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
        renderSpectrum(context);
        this.renderVolumeControls(context, mouseX, mouseY, delta);
    }

    private void renderVolumeControls(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.volumeButton.render(context, mouseX, mouseY, delta);
        if (this.volumeSlider.visible) {
            this.volumeSlider.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.volumeSlider.visible && this.volumeSlider.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.volumeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.volumeSliderVisible && !this.volumeSlider.isMouseOver(mouseX, mouseY) &&
                !this.volumeButton.isMouseOver(mouseX, mouseY)) {
            this.setVolumeSliderVisible(false);
            return true;
        }
        if (button == 0 && this.isOverProgressBar(mouseX, mouseY) && this.seekProgress(mouseX, false)) {
            this.seekingProgress = true;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.volumeSlider.visible && this.volumeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
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
        if (this.volumeSlider.visible && this.volumeSlider.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverProgressBar(double mouseX, double mouseY) {
        return PlayerPermissions.canControlPlayback() && MusicPlayer.INSTANCE.canSeekCurrentMusic()
                && mouseX >= 0 && mouseX <= this.width && mouseY >= 0 && mouseY <= 8;
    }

    private boolean seekProgress(double mouseX, boolean commit) {
        MusicMetaData metaData = MusicPlayer.INSTANCE.currentMeta;
        if (metaData == null || metaData.getDuration() == null || !PlayerPermissions.canControlPlayback()
                || !MusicPlayer.INSTANCE.canSeekCurrentMusic()) {
            return false;
        }
        double progress = this.width <= 0 ? 0D : Math.max(0D, Math.min(1D, mouseX / this.width));
        long targetMs = (long) (metaData.getDuration().asMilliseconds() * progress);
        MusicPlayer.INSTANCE.updateDisplayTexts(targetMs);
        if (commit) {
            MusicPlayer.INSTANCE.seekToMillisecondsAsync(targetMs);
        }
        return true;
    }

    private void renderTopProgressBar(GuiGraphics context, MusicMetaData metaData) {
        if (metaData == null || metaData.getDuration() == null) {
            return;
        }

        int barHeight = 2;
        double progress = MusicPlayer.INSTANCE.progressPercentage;
        progress = Math.max(0.0D, Math.min(1.0D, progress));

        int bgColor = (int) ClientConfig.INSTANCE.timeProgressBgColor.getNumber();
        int progressColor = (int) ClientConfig.INSTANCE.timeProgressColor.getNumber();

        context.fill(0, 0, this.width, barHeight, bgColor);
        context.fill(0, 0, (int) Math.round(this.width * progress), barHeight, progressColor);
    }

    private static final int SPECTRUM_BAR_COUNT = 64;

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

        g.pose().pushPose();

        VertexConsumer vertexConsumer = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();

        float angleStep = 360f / barCount;
        float spanDegrees = angleStep * 0.85f;

        for (int i = 0; i < barCount; i++) {
            float value = spectrumBars[i];
            if (Float.isNaN(value)) value = 0f;

            float dynamicScale = (float) (1.3f * Math.log10(1.0 + value * 60.0));

            float barLength = dynamicScale * (maxBarLength / 1.5f);
            barLength = Math.max(2f, Math.min(barLength, maxBarLength));

            float currentOuterRadius = radiusInner + barLength;

            float alphaFactor = 0.35f + 0.65f * Math.min(dynamicScale, 1.0f);
            int a = (int) (alphaFactor * 255f);
            int r = 150, green = 200, b = 255;
            int color = (a << 24) | (r << 16) | (green << 8) | b;

            float centerAngle = (i * angleStep) - 90f;
            float a1 = (float) Math.toRadians(centerAngle - spanDegrees / 2f);
            float a2 = (float) Math.toRadians(centerAngle + spanDegrees / 2f);

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            float x1 = centerX + cos1 * radiusInner;
            float y1 = centerY + sin1 * radiusInner;
            float x2 = centerX + cos1 * currentOuterRadius;
            float y2 = centerY + sin1 * currentOuterRadius;
            float x3 = centerX + cos2 * currentOuterRadius;
            float y3 = centerY + sin2 * currentOuterRadius;
            float x4 = centerX + cos2 * radiusInner;
            float y4 = centerY + sin2 * radiusInner;

            vertexConsumer.vertex(matrix, x1, y1, 0).color(color).endVertex();
            vertexConsumer.vertex(matrix, x4, y4, 0).color(color).endVertex();
            vertexConsumer.vertex(matrix, x3, y3, 0).color(color).endVertex();
            vertexConsumer.vertex(matrix, x2, y2, 0).color(color).endVertex();
        }

        g.pose().popPose();
    }

}
