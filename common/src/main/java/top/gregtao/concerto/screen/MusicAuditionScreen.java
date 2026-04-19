package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;
import top.gregtao.concerto.core.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MusicAuditionScreen extends ConcertoScreen {

    public static final Map<UUID, Music> WAIT_AUDITION = new HashMap<>();

    private MusicWithUUIDListWidget widget;

    public MusicAuditionScreen(Screen parent) {
        super(Component.translatable("concerto.screen.audition"), parent);
    }

    private static List<Pair<Music, UUID>> toPairList(Map<UUID, Music> map) {
        return map.entrySet().stream().map(entry -> Pair.of(entry.getValue(), entry.getKey())).toList();
    }

    public void refresh() {
        this.widget.reset(toPairList(WAIT_AUDITION), null);
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MusicWithUUIDListWidget(this.width, this.height - 55, 20, 18);
        this.refresh();
        this.addWidget(this.widget);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.accept"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("concerto-server audit " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(20, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("concerto-server audit reject " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(85, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject.all"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("concerto-server audit reject all");
                this.widget.clear();
            }
        }).pos(150, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"), button -> this.refresh())
                .pos(215, this.height - 30).size(60, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasPermissions(2)) {
            matrices.drawCenteredString(this.font, Component.translatable("concerto.screen.audition.permission_denied"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
