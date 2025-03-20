package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.screen.qq.QQMusicIndexScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 40, 115, 20, new TranslatableText("concerto.screen.general_list"),
                button -> MinecraftClient.getInstance().setScreen(new GeneralPlaylistScreen(this))
        ));

        ButtonWidget widget = new ButtonWidget(this.width / 2 + 20, 40, 115, 20, new TranslatableText("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().setScreen(new MusicAuditionScreen(this))
        );
        this.addDrawableChild(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addDrawableChild(new ButtonWidget(this.width / 2 + 20, 70, 115, 20, new TranslatableText("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().setScreen(new MusicConfirmationScreen(this))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 130, 115, 20, new TranslatableText("concerto.screen.add"),
                        button -> MinecraftClient.getInstance().setScreen(new AddMusicScreen(this))));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 70, 115, 20, new TranslatableText("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 120, 100, 115, 20, new TranslatableText("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicIndexScreen(this))
        ));

        ButtonWidget widget1 = new ButtonWidget(this.width / 2 + 20, 100, 115, 20, new TranslatableText("concerto.screen.preset_radios"),
                button -> MinecraftClient.getInstance().setScreen(new PresetRadiosScreen(this))
        );
        this.addDrawableChild(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }
    }
}
