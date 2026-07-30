package top.gregtao.concerto.screen.kugou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.enums.SearchType;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.KuGouMusicPlaylist;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.MusicInfoScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.HashMap;
import java.util.Map;

public class KuGouMusicSearchScreen extends PageScreen {
    public static String DEFAULT_KEYWORD = "";
    private MetadataListWidget<Music> musicList;
    private MetadataListWidget<KuGouMusicPlaylist> playlistList;
    private MetadataListWidget<KuGouMusicPlaylist> albumList;
    private Map<SearchType, ConcertoListWidget<?>> listWidgetsMap = new HashMap<>();
    protected EditBox searchBox;
    private Button infoButton;
    private Button playButton;
    private Button addButton;
    private SearchType searchType = SearchType.MUSIC;

    private <T extends WithMetaData> MetadataListWidget<T> initListsWidget() {
        return new MetadataListWidget<>(this.width, this.height - 75, 40, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                try {
                    switch (KuGouMusicSearchScreen.this.searchType) {
                        case MUSIC: {
                            if (PlayerPermissions.canModifyMusicList()) {
                                MusicPlayerHandler.INSTANCE.addMusicHereAsync((Music) entry.item, true, () -> {
                                });
                            }
                            break;
                        }
                        case PLAYLIST, ALBUM: {
                            Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, KuGouMusicSearchScreen.this));
                            break;
                        }
                    }
                } catch (ClassCastException e) {
                    ConcertoClient.LOGGER.error(e.getMessage());
                }
            }
        };
    }

    public KuGouMusicSearchScreen(Screen parent) {
        super(Component.translatable("concerto.screen.search.kugou"), parent);
    }

    private void search(String keyword, int page) {
        DEFAULT_KEYWORD = keyword;
        if (keyword.isEmpty()) return;
        ConcertoRunner.run(() -> {
            switch (this.searchType) {
                case MUSIC -> this.musicList.reset(KuGouMusicApiClient.INSTANCE.searchMusic(keyword, page), null);
                case PLAYLIST ->
                        this.playlistList.reset(KuGouMusicApiClient.INSTANCE.searchPlaylist(keyword, page), null);
                case ALBUM -> this.albumList.reset(KuGouMusicApiClient.INSTANCE.searchAlbum(keyword, page), null);
            }
            this.listWidgetsMap.get(this.searchType).setScrollAmount(0);
        });
    }

    private void toggleSearch() {
        this.page = 0;
        this.search(this.searchBox.getValue(), 1);
    }

    private void updateSearchType(SearchType type) {
        try {
            this.removeWidget(this.listWidgetsMap.get(this.searchType));
        } catch (NullPointerException ignored) {
        }
        this.addWidget(this.listWidgetsMap.get(type));
        this.searchType = type;
        this.infoButton.active = type == SearchType.MUSIC;
        this.updateActionButtons();
        this.toggleSearch();
    }

    private void updateActionButtons() {
        boolean canModifyMusicList = PlayerPermissions.canModifyMusicList();
        if (this.playButton != null) {
            boolean requestInAgent = this.searchType == SearchType.MUSIC
                    && MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT;
            this.playButton.active = canModifyMusicList || requestInAgent;
            this.playButton.setMessage(Component.translatable(requestInAgent
                    ? "concerto.screen.request" : "concerto.screen.play"));
        }
        if (this.addButton != null) {
            this.addButton.active = canModifyMusicList;
        }
    }

    @Override
    public void onPageTurned(int page) {
        this.search(this.searchBox.getValue(), page + 1);
    }

    @Override
    protected void init() {
        super.init();
        int actionX = this.actionBarX();
        int actionWidth = (this.actionBarWidth() - STANDARD_ACTION_GAP * 2) / 3;
        this.musicList = this.initListsWidget();
        this.playlistList = this.initListsWidget();
        this.albumList = this.initListsWidget();

        this.listWidgetsMap = Map.of(
                SearchType.MUSIC, this.musicList,
                SearchType.PLAYLIST, this.playlistList,
                SearchType.ALBUM, this.albumList
        );

        int searchX = this.standardContentX();
        int searchButtonX = this.standardContentRight() - 52;
        int searchTypeX = searchButtonX - STANDARD_ACTION_GAP - 65;
        this.searchBox = new EditBox(this.font, searchX, 17, searchTypeX - STANDARD_ACTION_GAP - searchX, 20,
                this.searchBox, Component.translatable("concerto.screen.search"));
        this.addRenderableWidget(this.searchBox);
        this.searchBox.setValue(DEFAULT_KEYWORD);

        this.infoButton = Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).pos(actionX + (actionWidth + STANDARD_ACTION_GAP) * 2, this.bottomBarY())
                .size(this.actionBarWidth() - (actionWidth + STANDARD_ACTION_GAP) * 2, 20).build();
        this.addRenderableWidget(this.infoButton);

        this.updateSearchType(this.searchType);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                button -> this.toggleSearch()).pos(searchButtonX, 17).size(52, 20).build());

        this.addRenderableWidget(CycleButton.builder((SearchType type) -> Component.literal(type.getName()))
                .withValues(SearchType.values()).withInitialValue(this.searchType).create(
                        searchTypeX, 17, 65, 20, Component.translatable("concerto.search_type"),
                        (widget, type) -> this.updateSearchType(type)));

        this.playButton = Button.builder(Component.translatable("concerto.screen.play"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
                    if (entry != null) {
                        if (MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT) {
                            ServerMusicAgentManager.clientAddMusic(entry.item);
                        } else {
                            MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true, () -> { });
                        }
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.playlistList.getSelected();
                    if (entry != null) {
                        Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.albumList.getSelected();
                    if (entry != null) {
                        Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
            }
        }).pos(actionX + actionWidth + STANDARD_ACTION_GAP, this.bottomBarY()).size(actionWidth, 20).build();
        this.addRenderableWidget(this.playButton);

        this.addButton = Button.builder(Component.translatable("concerto.screen.add"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(entry.item, false, () -> {
                        });
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.playlistList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(() -> entry.item.getList(), false, () -> {
                        });
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.albumList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(() -> entry.item.getList(), false, () -> {
                        });
                    }
                }
            }
        }).pos(actionX, this.bottomBarY()).size(actionWidth, 20).build();
        this.addRenderableWidget(this.addButton);

        this.updateActionButtons();
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        switch (this.searchType) {
            case PLAYLIST -> this.playlistList.render(matrices, mouseX, mouseY, delta);
            case MUSIC -> this.musicList.render(matrices, mouseX, mouseY, delta);
            case ALBUM -> this.albumList.render(matrices, mouseX, mouseY, delta);
        }
        this.renderWidgets(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isHoveredOrFocused()) {
            this.toggleSearch();
            return true;
        }
        return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.searchBox.charTyped(chr, modifiers);
    }
}
