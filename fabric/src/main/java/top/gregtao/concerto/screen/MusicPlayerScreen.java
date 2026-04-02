package top.gregtao.concerto.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;

public class MusicPlayerScreen extends ConcertoScreen {

    private ButtonWidget playPauseButton;
    private ButtonWidget nextButton;
    private CyclingButtonWidget<OrderType> orderButton;

    private float rotationAngle = 0f;
    private int scrollOffset = 0;

    public MusicPlayerScreen(Screen parent) {
        super(Text.empty(), parent);
    }

    @Override
    protected void init() {
        super.init();

        int y = this.height - 30;
        int totalWidth = 4 * 60 + 3 * 2; // four buttons, 2px gaps
        int x = (this.width - totalWidth) / 2;

        ButtonWidget playlistButton = ButtonWidget.builder(
                Text.translatable("concerto.screen.general_list"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new GeneralPlaylistScreen(this));
                    }
                }
        ).position(x, y).size(60, 20).build();
        this.addDrawableChild(playlistButton);
        x += 62;

        this.playPauseButton = ButtonWidget.builder(
                Text.translatable(MusicPlayerHandler.INSTANCE.getState().get().paused ? "concerto.screen.play" : "concerto.screen.pause"),
                button -> {
                    boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
                    MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
                    button.setMessage(Text.translatable(!paused ? "concerto.screen.play" : "concerto.screen.pause"));
                }
        ).position(x, y).size(60, 20).build();
        x += 62;

        this.nextButton = ButtonWidget.builder(
                Text.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)
        ).position(x, y).size(60, 20).build();
        x += 62;

        this.orderButton = CyclingButtonWidget.builder((OrderType val) -> Text.literal(val.getName()))
                .values(OrderType.values())
                .initially(MusicPlayerHandler.INSTANCE.getOrderType())
                .build(x, y, 60, 20, Text.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));

        this.addDrawableChild(this.playPauseButton);
        this.addDrawableChild(this.nextButton);
        this.addDrawableChild(this.orderButton);

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.playPauseButton.active = PlayerPermissions.canControlPlayback();
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();

        boolean isPaused = MusicPlayerHandler.INSTANCE.getState().get().paused;
        this.playPauseButton.setMessage(Text.translatable(isPaused ? "concerto.screen.play" : "concerto.screen.pause"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (MusicPlayer.INSTANCE.isPlaying() && !MusicPlayerHandler.INSTANCE.getState().get().paused) {
            this.rotationAngle += delta * 0.3f;
            if (this.rotationAngle >= 360f) this.rotationAngle -= 360f;
        }

        int leftWidth = this.width / 2;
        int imgSize = Math.max(96, Math.min(120, leftWidth - 60));
        int imgX = (leftWidth - imgSize) / 2;
        int imgY = (this.height - imgSize) / 2 - 8;

        context.getMatrices().push();
        
        InGameHudRenderer.COVER_IMAGE.setX(imgX);
        InGameHudRenderer.COVER_IMAGE.setY(imgY);
        InGameHudRenderer.COVER_IMAGE.setSize(imgSize, imgSize);

        context.getMatrices().translate(imgX + imgSize / 2f, imgY + imgSize / 2f, 0);
        context.getMatrices().multiply(new Quaternionf().rotateZ(this.rotationAngle * (float) Math.PI / 180f));
        context.getMatrices().translate(-(imgX + imgSize / 2f), -(imgY + imgSize / 2f), 0);
        
        InGameHudRenderer.COVER_IMAGE.render(context, mouseX, mouseY, delta);

        context.getMatrices().pop();

        int rightHalfX = this.width / 2; // lyrics start at mid-screen for >= 1/2 width
        int lyricsWidth = this.width - rightHalfX - 20;

        if (MusicPlayer.INSTANCE.currentMeta != null) {
            String title = MusicPlayer.INSTANCE.currentMeta.title();
            String author = MusicPlayer.INSTANCE.currentMeta.author();
            int centerX = this.width / 2;
            int titleY = 8;
            context.drawText(this.textRenderer, title, centerX - this.textRenderer.getWidth(title) / 2, titleY, 0xFFFFFF, false);
            if (author != null && !author.isEmpty()) {
                int authorY = titleY + 12;
                context.drawText(this.textRenderer, author, centerX - this.textRenderer.getWidth(author) / 2, authorY, 0xAAAAAA, false);
            }
        }

        if (MusicPlayer.INSTANCE.currentLyrics != null && !MusicPlayer.INSTANCE.currentLyrics.isEmpty()) {
            ArrayList<Pair<MusicTimestamp, String>> lyrics = MusicPlayer.INSTANCE.currentLyrics.getLyricBody();
            ArrayList<Pair<MusicTimestamp, String>> subLyrics = MusicPlayer.INSTANCE.currentSubLyrics != null ? MusicPlayer.INSTANCE.currentSubLyrics.getLyricBody() : null;
            
            long t = (long) (MusicPlayer.INSTANCE.progressPercentage * (MusicPlayer.INSTANCE.currentMeta != null && MusicPlayer.INSTANCE.currentMeta.getDuration() != null ? MusicPlayer.INSTANCE.currentMeta.getDuration().asMilliseconds() : 0));
            
            int activeIndex = Math.max(0, MathUtil.upperBound(lyrics, Pair.of(MusicTimestamp.ofMilliseconds(t), ""), Comparator.comparing(Pair::getFirst)) - 1);

            int lineHeight = subLyrics != null ? 27 : 17;
            int startY = 45;
            int endY = this.height - 40;

            int targetScrollOffset = (activeIndex * lineHeight) - ((endY - startY) / 2) + (lineHeight / 2);
            this.scrollOffset += (int)((targetScrollOffset - this.scrollOffset) * 0.15f);

            context.enableScissor(rightHalfX, startY, this.width - 20, endY);
            for (int i = 0; i < lyrics.size(); i++) {
                String line = lyrics.get(i).getSecond();
                String subLine = null;
                if (subLyrics != null) {
                    long currentLineTime = lyrics.get(i).getFirst().asMilliseconds();
                    int subIndex = MathUtil.upperBound(subLyrics, Pair.of(MusicTimestamp.ofMilliseconds(currentLineTime), ""), Comparator.comparing(Pair::getFirst)) - 1;
                    if (subIndex >= 0 && subIndex < subLyrics.size() && Math.abs(subLyrics.get(subIndex).getFirst().asMilliseconds() - currentLineTime) < 500) {
                        subLine = subLyrics.get(subIndex).getSecond();
                    }
                }

                int y = startY + (i * lineHeight) - this.scrollOffset;
                
                if (y > startY - lineHeight && y < endY + lineHeight) {
                    boolean isActive = (i == activeIndex);
                    int alpha = Math.max(15, 255 - Math.abs(i - activeIndex) * 35);
                    
                    int color = isActive ? ((int) ClientConfig.INSTANCE.lyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0xAAAAAA);
                    
                    context.drawText(this.textRenderer, line, rightHalfX + (lyricsWidth - this.textRenderer.getWidth(line)) / 2, y, color, isActive);

                    if (subLine != null) {
                        int subColor = isActive ? ((int) ClientConfig.INSTANCE.subLyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0x888888);
                        int subY = y + 12;
                        context.drawText(this.textRenderer, subLine, rightHalfX + (lyricsWidth - this.textRenderer.getWidth(subLine)) / 2, subY, subColor, false);
                    }
                }
            }
            context.disableScissor();
        }
    }
}
