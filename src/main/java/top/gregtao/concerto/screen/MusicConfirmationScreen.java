package top.gregtao.concerto.screen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;

import java.util.UUID;

public class MusicConfirmationScreen extends ConcertoScreen {

    private final MusicWithUUIDListWidget widget;

    public MusicConfirmationScreen(Screen parent) {
        super(Text.translatable("concerto.screen.confirmation"), parent);
        this.widget = new MusicWithUUIDListWidget(this.width, 0, 18, this.height - 35, 18);
        this.widget.setRenderHorizontalShadows(false);
        this.widget.setRenderBackground(false);
    }

    public void refresh() {
        this.widget.reset(ClientMusicNetworkHandler.WAIT_CONFIRMATION.entrySet().stream().map(
                entry -> Pair.of(entry.getValue().music, entry.getKey())).toList(), null);
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
                player.networkHandler.sendChatCommand("sharemusic accept " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(this.width / 2 - 160, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelectedOrNull();
            if (player != null && entry != null) {
                player.networkHandler.sendChatCommand("sharemusic reject " + entry.item.getSecond());
                this.widget.removeEntryWithoutScrolling(entry);
            }
        }).position(this.width / 2 - 105, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.reject.all"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.networkHandler.sendChatCommand("sharemusic reject all");
                this.widget.clear();
            }
        }).position(this.width / 2 - 50, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.refresh"), button -> this.refresh())
                .position(this.width / 2 + 5, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
