package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;
import top.gregtao.concerto.core.util.Pair;

import java.util.UUID;

public class MusicConfirmationScreen extends ConcertoScreen {

    private MusicWithUUIDListWidget widget;

    public MusicConfirmationScreen(Screen parent) {
        super(Text.translatable("concerto.screen.confirmation"), parent);
    }

    public void refresh() {
        this.widget.reset(ClientMusicNetworkHandler.WAIT_CONFIRMATION.entrySet().stream().map(
                entry -> Pair.of(entry.getValue().music, entry.getKey())).toList(), null);
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
                player.networkHandler.sendChatCommand("sharemusic accept " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(20, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("sharemusic reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(85, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.networkHandler.sendChatCommand("sharemusic reject all");
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
    }
}
