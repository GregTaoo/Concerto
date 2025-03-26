package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
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
        super(new TranslatableText("concerto.screen.audition"), parent);
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
        this.setRenderBg(false);

        this.widget = new MusicWithUUIDListWidget(this.width, this.height, 18, this.height - 35, 18);
        this.refresh();
        this.addChild(this.widget);

        this.addButton(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.accept"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.sendChatMessage("/concerto-server audit " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }));

        this.addButton(new ButtonWidget(85, this.height - 30, 60, 20,
                new TranslatableText("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.sendChatMessage("/concerto-server audit reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }));

        this.addButton(new ButtonWidget(150, this.height - 30, 60, 20,
                new TranslatableText("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendChatMessage("/concerto-server audit reject all");
                this.widget.clear();
            }
        }));

        this.addButton(new ButtonWidget(215, this.height - 30, 60, 20,
                new TranslatableText("concerto.refresh"), button -> this.refresh()));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.hasPermissionLevel(2)) {
            ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer,
                    new TranslatableText("concerto.screen.audition.permission_denied").asOrderedText(),
                    this.width / 2, this.height / 2, 0xffffffff);
        } else {
            this.widget.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
