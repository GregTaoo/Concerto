package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.screen.netease.NeteaseCloudIndexScreen;

public class ConcertoIndexScreen extends ConcertoScreen {
    public ConcertoIndexScreen(Screen parent) {
        super(Text.translatable("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.index.163"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudIndexScreen(this))
        ).position(this.width / 2 - 50, 40).size(100, 20).build());
    }
}
