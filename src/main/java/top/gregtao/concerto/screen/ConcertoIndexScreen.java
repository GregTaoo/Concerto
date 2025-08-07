package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.screen.qq.QQMusicIndexScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;
import top.gregtao.concerto.screen.widget.PressableTextWidget;
import top.gregtao.concerto.screen.widget.TextWidget;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(this.width / 2 - 120, 20, 115, 20, new TranslatableText("concerto.screen.general_list"),
                button -> MinecraftClient.getInstance().openScreen(new GeneralPlaylistScreen(this))
        ));

        ButtonWidget widget = new ButtonWidget(this.width / 2 + 5, 20, 115, 20, new TranslatableText("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().openScreen(new MusicAuditionScreen(this))
        );
        this.addButton(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addButton(new ButtonWidget(this.width / 2 + 5, 50, 115, 20, new TranslatableText("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().openScreen(new MusicConfirmationScreen(this))
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 50, 115, 20, new TranslatableText("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().openScreen(new NeteaseCloudIndexScreen(this))
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 80, 115, 20, new TranslatableText("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().openScreen(new QQMusicIndexScreen(this))
        ));

        ButtonWidget widget1 = new ButtonWidget(this.width / 2 + 5, 80, 115, 20, new TranslatableText("concerto.screen.preset_radios"),
                button -> MinecraftClient.getInstance().openScreen(new PresetRadiosScreen(this))
        );
        this.addButton(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }

        this.addButton(new ButtonWidget(this.width / 2 - 120, 110, 115, 20, new TranslatableText("concerto.screen.add"),
                button -> MinecraftClient.getInstance().openScreen(new AddMusicScreen(this)))
        );

        this.addButton(new ButtonWidget(this.width / 2 + 5, 110, 115, 20, new TranslatableText("concerto.screen.local_playlists"),
            button -> MinecraftClient.getInstance().openScreen(new PlaylistListScreen(
                new TranslatableText("concerto.screen.local_playlists"), this, PresetPlaylistsConfig.LOCAL_PLAYLISTS.getRadios()))
        ));

        this.addButton(new ButtonWidget(this.width / 2 + 5, 140, 115, 20, new TranslatableText("concerto.report_bugs"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/issues")
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 140, 115, 20, new TranslatableText("concerto.screen.options"),
                button -> MinecraftClient.getInstance().openScreen(new ConcertoOptionsScreen(this))
        ));

        if (this.client != null) {
            switch (ConcertoClient.clientState) {
                case MUSIC_ROOM -> {
                    String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
                    Text text = new TranslatableText("concerto.screen.in_music_room", uuid);
                    int width = this.textRenderer.getWidth(text);
                    this.addButton(new PressableTextWidget(
                        (this.width - width) / 2, 185, width,
                        this.textRenderer.fontHeight, text,
                        button -> this.client.keyboard.setClipboard(uuid),
                        this.textRenderer
                    ));
                }
                case MUSIC_AGENT -> {
                    Text text = new TranslatableText("concerto.screen.in_music_agent");
                    int width = this.textRenderer.getWidth(text);
                    this.addButton(new TextWidget(
                        (this.width - width) / 2, 185, width,
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
            ConcertoScreen.drawCenteredTextWithShadow(
                matrices, this.textRenderer,
                new TranslatableText("concerto.screen.in_which_room").asOrderedText(),
                this.width / 2, 170, 0xffffffff
            );
        }
    }
}
