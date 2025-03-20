package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;
import top.gregtao.concerto.util.Pair;

import java.util.UUID;

public class MusicConfirmationScreen extends ConcertoScreen {

    private MusicWithUUIDListWidget widget;

    public MusicConfirmationScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.confirmation"), parent);
    }

    public void refresh() {
        this.widget.reset(ClientMusicNetworkHandler.WAIT_CONFIRMATION.entrySet().stream().map(
                entry -> Pair.of(entry.getValue().music, entry.getKey())).toList(), null);
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MusicWithUUIDListWidget(this.width, this.height, 18, this.height - 35, 18);
        this.refresh();
        this.addDrawableChild(this.widget);
        this.addSelectableChild(this.widget);

        this.addDrawableChild(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.accept"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.sendChatMessage("sharemusic accept " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }));

        this.addDrawableChild(new ButtonWidget(85, this.height - 30, 60, 20, new TranslatableText("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.sendChatMessage("sharemusic reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }));

        this.addDrawableChild(new ButtonWidget(150, this.height - 30, 60, 20,
                new TranslatableText("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendChatMessage("sharemusic reject all");
                this.widget.clear();
            }
        }));

        this.addDrawableChild(new ButtonWidget(215, this.height - 30, 60, 20,
                new TranslatableText("concerto.refresh"), button -> this.refresh()));
    }
}
