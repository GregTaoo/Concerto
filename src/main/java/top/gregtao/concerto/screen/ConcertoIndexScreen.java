package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.screen.kugou.KuGouMusicIndexScreen;
import top.gregtao.concerto.screen.qq.QQMusicIndexScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(Text.translatable("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.general_list"),
                button -> MinecraftClient.getInstance().setScreen(new GeneralPlaylistScreen(this))
        ).position(this.width / 2 - 120, 20).size(115, 20).build());

        ButtonWidget widget = ButtonWidget.builder(Text.translatable("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().setScreen(new MusicAuditionScreen(this))
        ).position(this.width / 2 + 5, 20).size(115, 20).build();
        this.addDrawableChild(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().setScreen(new MusicConfirmationScreen(this))
        ).position(this.width / 2 + 5, 50).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ).position(this.width / 2 - 120, 50).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicIndexScreen(this))
        ).position(this.width / 2 - 120, 80).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.kugou"),
                button -> MinecraftClient.getInstance().setScreen(new KuGouMusicIndexScreen(this))
        ).position(this.width / 2 - 120, 110).size(115, 20).build());

        ButtonWidget widget1 = ButtonWidget.builder(Text.translatable("concerto.screen.preset_radios"),
                button -> MinecraftClient.getInstance().setScreen(new PresetRadiosScreen(this))
        ).position(this.width / 2 + 5, 80).size(115, 20).build();
        this.addDrawableChild(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"),
                button -> MinecraftClient.getInstance().setScreen(new AddMusicScreen(this)))
            .position(this.width / 2 - 120, 140).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.local_playlists"),
            button -> MinecraftClient.getInstance().setScreen(new PlaylistListScreen(
                Text.translatable("concerto.screen.local_playlists"), this, PresetPlaylistsConfig.LOCAL_PLAYLISTS.getRadios()))
        ).position(this.width / 2 + 5, 110).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.report_bugs"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/issues")
        ).position(this.width / 2 + 5, 140).size(115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.options"),
                button -> MinecraftClient.getInstance().setScreen(new ConcertoOptionsScreen(this))
        ).position(this.width / 2 - 120, 170).size(115, 20).build());

        if (this.client != null) {
            switch (ConcertoClient.clientState) {
                case MUSIC_ROOM -> {
                    String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
                    Text text = Text.translatable("concerto.screen.in_music_room", uuid);
                    int width = this.textRenderer.getWidth(text);
                    this.addDrawableChild(new PressableTextWidget(
                        (this.width - width) / 2, 215, width,
                        this.textRenderer.fontHeight, text,
                        button -> this.client.keyboard.setClipboard(uuid),
                        this.textRenderer
                    ));
                }
                case MUSIC_AGENT -> {
                    Text text = Text.translatable("concerto.screen.in_music_agent");
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
                Text.translatable("concerto.screen.in_which_room"),
                this.width / 2, 200, 0xffffffff
            );
        }
    }
}
