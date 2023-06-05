package top.gregtao.concerto.screen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MusicAuditionScreen extends ConcertoScreen {

    public static Map<UUID, Music> WAIT_AUDITION = new HashMap<>();

    private final MusicWithUUIDListWidget widget;

    public MusicAuditionScreen(Screen parent) {
        super(Text.translatable("concerto.screen.audition"), parent);
        this.widget = new MusicWithUUIDListWidget(this.width, 0, 18, this.height - 35, 18);
        this.widget.setRenderHorizontalShadows(false);
        this.widget.setRenderBackground(false);
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
        this.refresh();
        this.addSelectableChild(this.widget);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.accept"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("audit " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(this.width / 2 - 160, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("audit reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(this.width / 2 - 105, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.networkHandler.sendChatCommand("audit reject all");
                this.widget.clear();
            }
        }).position(this.width / 2 - 50, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.refresh"), button -> this.refresh())
                .position(this.width / 2 + 5, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2)) {
            DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, Text.translatable("concerto.screen.audition.permission_denied"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
