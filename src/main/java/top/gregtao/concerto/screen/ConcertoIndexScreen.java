package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.screen.kugou.KuGouMusicIndexScreen;
import top.gregtao.concerto.screen.qq.QQMusicIndexScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;
import top.gregtao.concerto.screen.widget.TextWidget;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 20, 115, 20, new TranslatableText("concerto.screen.general_list"),
                button -> MinecraftClient.getInstance().setScreen(new GeneralPlaylistScreen(this))
        ));

        ButtonWidget widget = new ButtonWidget(this.width / 2 + 5, 20, 115, 20, new TranslatableText("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().setScreen(new MusicAuditionScreen(this))
        );
        this.addDrawableChild(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addDrawableChild(new ButtonWidget(this.width / 2 + 5, 50, 115, 20, new TranslatableText("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().setScreen(new MusicConfirmationScreen(this))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 50, 115, 20, new TranslatableText("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 80, 115, 20, new TranslatableText("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicIndexScreen(this))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 110, 115, 20, new TranslatableText("concerto.screen.index.kugou"),
                button -> MinecraftClient.getInstance().setScreen(new KuGouMusicIndexScreen(this))
        ));

        ButtonWidget widget1 = new ButtonWidget(this.width / 2 + 5, 80, 115, 20, new TranslatableText("concerto.screen.preset_radios"),
                button -> MinecraftClient.getInstance().setScreen(new PresetRadiosScreen(this))
        );
        this.addDrawableChild(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 140, 115, 20, new TranslatableText("concerto.screen.add"),
                button -> MinecraftClient.getInstance().setScreen(new AddMusicScreen(this)))
        );

        this.addDrawableChild(new ButtonWidget(this.width / 2 + 5, 110, 115, 20, new TranslatableText("concerto.screen.local_playlists"),
                button -> MinecraftClient.getInstance().setScreen(new PlaylistListScreen(
                        new TranslatableText("concerto.screen.local_playlists"), this, PresetPlaylistsConfig.LOCAL_PLAYLISTS.getRadios()))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 + 5, 140, 115, 20, new TranslatableText("concerto.report_bugs"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/issues")
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 170, 115, 20, new TranslatableText("concerto.screen.options"),
                button -> MinecraftClient.getInstance().setScreen(new ConcertoOptionsScreen(this))
        ));

        if (this.client != null) {
            switch (ConcertoClient.clientState) {
                case MUSIC_ROOM -> {
                    String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
                    Text text = new TranslatableText("concerto.screen.in_music_room", uuid);
                    int width = this.textRenderer.getWidth(text);
                    this.addDrawableChild(new PressableTextWidget(
                            (this.width - width) / 2, 215, width,
                            this.textRenderer.fontHeight, text,
                            button -> this.client.keyboard.setClipboard(uuid),
                            this.textRenderer
                    ));
                }
                case MUSIC_AGENT -> {
                    Text text = new TranslatableText("concerto.screen.in_music_agent");
                    int width = this.textRenderer.getWidth(text);
                    this.addDrawableChild(new TextWidget(
                            (this.width - width) / 2, 215, width,
                            this.textRenderer.fontHeight, text,
                            this.textRenderer
                    ));
                }
            }
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (ConcertoClient.clientState != ConcertoClient.ClientState.LOCAL) {
            DrawableHelper.drawCenteredTextWithShadow(
                matrices, this.textRenderer,
                new TranslatableText("concerto.screen.in_which_room").asOrderedText(),
                this.width / 2, 200, 0xffffffff
            );
        }
    }
}
