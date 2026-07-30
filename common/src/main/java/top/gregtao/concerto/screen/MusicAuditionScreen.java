package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;

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
        int actionX = this.standardContentX();
        int actionY = this.standardBottomActionY();
        int actionWidth = (this.standardContentWidth() - STANDARD_ACTION_GAP * 3) / 4;

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.accept"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("concerto-server audit " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(actionX, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("concerto-server audit reject " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(actionX + actionWidth + STANDARD_ACTION_GAP, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject.all"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("concerto-server audit reject all");
                this.widget.clear();
            }
        }).pos(actionX + (actionWidth + STANDARD_ACTION_GAP) * 2, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"), button -> this.refresh())
                .pos(actionX + (actionWidth + STANDARD_ACTION_GAP) * 3, actionY)
                .size(this.standardContentRight() - actionX - (actionWidth + STANDARD_ACTION_GAP) * 3, 20).build());
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        this.widget.extractRenderState(matrices, mouseX, mouseY, delta);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            matrices.centeredText(this.font, Component.translatable("concerto.screen.audition.permission_denied"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
