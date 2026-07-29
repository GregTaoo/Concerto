package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PlaylistPreviewScreen extends ConcertoScreen {
    private final Playlist playlist;
    private MetadataListWidget<Music> widget;
    private Button addPlaylistButton;
    private Button playButton;
    private Button addButton;
    private Button requestButton;

    public PlaylistPreviewScreen(Playlist playlist, Screen parent) {
        super(Component.literal(Component.translatable("concerto." + (playlist.isAlbum() ? "album" : "playlist")).getString() +
                ": " + playlist.getMeta().title() + " - " + playlist.getMeta().author()), parent);
        this.playlist = playlist;
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<Music>.Entry entry) {
                if (PlayerPermissions.canModifyMusicList()) {
                    MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true);
                }
            }
        };
        this.addWidget(this.widget);
        ConcertoRunner.run(() -> this.widget.reset(this.playlist.getList(), null));

        int y = this.standardBottomActionY();
        int x = this.standardContentX();
        int buttonW = (this.standardContentWidth() - STANDARD_ACTION_GAP * 5) / 6;

        this.addPlaylistButton = Button.builder(Component.translatable("concerto.screen.playlist.add"), button ->
                        MusicPlayerHandler.INSTANCE.addMusicAsync(this.playlist.getList(), true))
                .pos(x, y).size(buttonW, 20).build();
        this.addRenderableWidget(this.addPlaylistButton);
        x += buttonW + STANDARD_ACTION_GAP;

        this.playButton = Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true);
            }
        }).pos(x, y).size(buttonW, 20).build();
        this.addRenderableWidget(this.playButton);
        x += buttonW + STANDARD_ACTION_GAP;

        this.addButton = Button.builder(Component.translatable("concerto.screen.add"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.addMusicAsync(entry.item, false);
            }
        }).pos(x, y).size(buttonW, 20).build();
        this.addRenderableWidget(this.addButton);
        x += buttonW + STANDARD_ACTION_GAP;
        this.requestButton = Button.builder(Component.translatable("concerto.screen.request"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                ServerMusicAgentManager.clientAddMusic(entry.item);
            }
        }).pos(x, y).size(buttonW, 20).build();
        this.addRenderableWidget(this.requestButton);
        x += buttonW + STANDARD_ACTION_GAP;


        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).pos(x, y).size(buttonW, 20).build());
        x += buttonW + STANDARD_ACTION_GAP;

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.playlist.save_local"), button -> {
            Component text = PresetPlaylistsConfig.saveToLocalPlaylists(this.playlist) ? Component.translatable("concerto.playlist.save_local.success") :
                    Component.translatable("concerto.playlist.save_local.fail");
            this.displayAlert(text);
        }).pos(x, y).size(this.standardContentRight() - x, 20).build());

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        boolean canModifyMusicList = PlayerPermissions.canModifyMusicList();
        this.addPlaylistButton.active = canModifyMusicList;
        this.playButton.active = canModifyMusicList;
        this.addButton.active = canModifyMusicList;
        this.requestButton.active = MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT
                && this.widget.getSelected() != null;
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.updateButtonStates();
        matrices.drawCenteredString(this.font, this.title, this.width / 2, 5, 0xffffffff);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
