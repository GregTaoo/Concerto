package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;
import top.gregtao.concerto.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MusicAuditionScreen extends ConcertoScreen {

    public static final Map<UUID, Music> WAIT_AUDITION = new HashMap<>();

    private MusicWithUUIDListWidget widget;

    public MusicAuditionScreen(Screen parent) {
        super(Text.translatable("concerto.screen.audition"), parent);
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
        this.addSelectableChild(this.widget);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.accept"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("audit " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(20, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("audit reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(85, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.networkHandler.sendChatCommand("audit reject all");
                this.widget.clear();
            }
        }).position(150, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.refresh"), button -> this.refresh())
                .position(215, this.height - 30).size(60, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2)) {
            matrices.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("concerto.screen.audition.permission_denied"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
