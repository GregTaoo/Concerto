package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
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
        this.addButton(new ButtonWidget(this.width / 2 - 120, 40, 115, 20, new TranslatableText("concerto.screen.general_list"),
                button -> MinecraftClient.getInstance().openScreen(new GeneralPlaylistScreen(this))
        ));

        ButtonWidget widget = new ButtonWidget(this.width / 2 + 20, 40, 115, 20, new TranslatableText("concerto.screen.audition"),
                button -> MinecraftClient.getInstance().openScreen(new MusicAuditionScreen(this))
        );
        this.addButton(widget);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2) || !ConcertoClient.isServerAvailable()) {
            widget.active = false;
        }

        this.addButton(new ButtonWidget(this.width / 2 + 20, 70, 115, 20, new TranslatableText("concerto.screen.confirmation"),
                button -> MinecraftClient.getInstance().openScreen(new MusicConfirmationScreen(this))
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 130, 115, 20, new TranslatableText("concerto.screen.add"),
                        button -> MinecraftClient.getInstance().openScreen(new AddMusicScreen(this))));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 70, 115, 20, new TranslatableText("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().openScreen(new NeteaseCloudIndexScreen(this))
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 100, 115, 20, new TranslatableText("concerto.screen.index.qq"),
                button -> MinecraftClient.getInstance().openScreen(new QQMusicIndexScreen(this))
        ));

        ButtonWidget widget1 = new ButtonWidget(this.width / 2 + 20, 100, 115, 20, new TranslatableText("concerto.screen.preset_radios"),
                button -> MinecraftClient.getInstance().openScreen(new PresetRadiosScreen(this))
        );
        this.addButton(widget1);
        if (player == null || !ConcertoClient.isServerAvailable()) {
            widget1.active = false;
        }

        this.addButton(new ButtonWidget(this.width / 2 + 20, 130, 115, 20, new TranslatableText("concerto.report_bugs"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/issues")
        ));

        this.addButton(new ButtonWidget(this.width / 2 - 120, 160, 115, 20, new TranslatableText("concerto.screen.options"),
                button -> MinecraftClient.getInstance().openScreen(new ConcertoOptionsScreen(this))
        ));
    }
}
