package top.gregtao.concerto.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.screen.kugou.KuGouMusicIndexScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;
import top.gregtao.concerto.screen.qq.QQMusicIndexScreen;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.general_list"),
                button -> Minecraft.getInstance().setScreen(new GeneralPlaylistScreen(this))
        ).pos(this.width / 2 - 120, 20).size(115, 20).build());

        Button widget = Button.builder(Component.translatable("concerto.screen.audition"),
                button -> Minecraft.getInstance().setScreen(new MusicAuditionScreen(this))
        ).pos(this.width / 2 + 5, 20).size(115, 20).build();
        this.addRenderableWidget(widget);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasPermissions(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.confirmation"),
                button -> Minecraft.getInstance().setScreen(new MusicConfirmationScreen(this))
        ).pos(this.width / 2 + 5, 50).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.index.163"),
                button -> Minecraft.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ).pos(this.width / 2 - 120, 50).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.index.qq"),
                button -> Minecraft.getInstance().setScreen(new QQMusicIndexScreen(this))
        ).pos(this.width / 2 - 120, 80).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.index.kugou"),
                button -> Minecraft.getInstance().setScreen(new KuGouMusicIndexScreen(this))
        ).pos(this.width / 2 - 120, 110).size(115, 20).build());

        Button widget1 = Button.builder(Component.translatable("concerto.screen.preset_radios"),
                button -> Minecraft.getInstance().setScreen(new PresetRadiosScreen(this))
        ).pos(this.width / 2 + 5, 80).size(115, 20).build();
        this.addRenderableWidget(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.add"),
                        button -> Minecraft.getInstance().setScreen(new AddMusicScreen(this)))
                .pos(this.width / 2 - 120, 140).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.local_playlists"),
                button -> Minecraft.getInstance().setScreen(new PlaylistListScreen(
                        Component.translatable("concerto.screen.local_playlists"), this, PresetPlaylistsConfig.LOCAL_PLAYLISTS.getRadios()))
        ).pos(this.width / 2 + 5, 110).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.report_bugs"),
                button -> Util.getPlatform().openUri("https://github.com/GregTaoo/Concerto/issues")
        ).pos(this.width / 2 + 5, 140).size(115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.options"),
                button -> Minecraft.getInstance().setScreen(new ConcertoOptionsScreen(this))
        ).pos(this.width / 2 - 120, 170).size(115, 20).build());

        if (this.minecraft != null) {
            switch (MusicRoom.clientGetState()) {
                case MUSIC_ROOM -> {
                    String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
                    Component text = Component.translatable("concerto.screen.in_music_room", uuid);
                    int width = this.font.width(text);
                    this.addRenderableWidget(new PlainTextButton(
                            (this.width - width) / 2, 215, width,
                            this.font.lineHeight, text,
                            button -> this.minecraft.keyboardHandler.setClipboard(uuid),
                            this.font
                    ));
                }
                case MUSIC_AGENT -> {
                    Component text = Component.translatable("concerto.screen.in_music_agent");
                    int width = this.font.width(text);
                    this.addRenderableWidget(new StringWidget(
                            (this.width - width) / 2, 215, width,
                            this.font.lineHeight, text,
                            this.font
                    ));
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (MusicRoom.clientGetState() != MusicRoom.ClientState.LOCAL) {
            matrices.drawCenteredString(
                    this.font,
                    Component.translatable("concerto.screen.in_which_room"),
                    this.width / 2, 200, 0xffffffff
            );
        }
    }
}
