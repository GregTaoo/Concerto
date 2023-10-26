package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.experimental.screen.qq.QQMusicIndexScreen;
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
        ).position(this.width / 2 - 50, 40).size(100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ).position(this.width / 2 - 50, 70).size(100, 20).build());

        ButtonWidget widget = ButtonWidget.builder(Text.translatable("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().setScreen(new MusicAuditionScreen(this))
        ).position(this.width / 2 - 50, 100).size(100, 20).build();
        this.addDrawableChild(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().setScreen(new MusicConfirmationScreen(this))
        ).position(this.width / 2 - 50, 130).size(100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"),
                        button -> MinecraftClient.getInstance().setScreen(new AddMusicScreen(this)))
                .position(this.width / 2 - 50, 160).size(100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicIndexScreen(this))
        ).position(this.width / 2 - 50, 200).size(100, 20).build());
    }
}
