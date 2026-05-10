package top.gregtao.concerto.screen.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.enums.SearchType;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.MusicInfoScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.HashMap;
import java.util.Map;

public class NeteaseCloudSearchScreen extends PageScreen {
    public static String DEFAULT_KEYWORD = "";
    private MetadataListWidget<Music> musicList;
    private MetadataListWidget<NeteaseCloudPlaylist> playlistList;
    private MetadataListWidget<NeteaseCloudPlaylist> albumList;
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
                    switch (NeteaseCloudSearchScreen.this.searchType) {
                        case MUSIC: {
                            if (PlayerPermissions.canModifyMusicList()) {
                                MusicPlayerHandler.INSTANCE.addMusicHereAsync((Music) entry.item, true, () -> {
                                });
                            }
                            break;
                        }
                        case PLAYLIST, ALBUM: {
                            Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, NeteaseCloudSearchScreen.this));
                            break;
                        }
                    }
                } catch (ClassCastException e) {
                    ConcertoClient.LOGGER.error(e.getMessage());
                }
            }
        };
    }

    public NeteaseCloudSearchScreen(Screen parent) {
        super(Component.translatable("concerto.screen.search.163"), parent);
    }

    private void search(String keyword, int page) {
        DEFAULT_KEYWORD = keyword;
        if (keyword.isEmpty()) return;
        ConcertoRunner.run(() -> {
            switch (this.searchType) {
                case MUSIC -> this.musicList.reset(NeteaseCloudApiClient.INSTANCE.searchMusic(keyword, page), null);
                case PLAYLIST ->
                        this.playlistList.reset(NeteaseCloudApiClient.INSTANCE.searchPlaylist(keyword, page), null);
                case ALBUM -> this.albumList.reset(NeteaseCloudApiClient.INSTANCE.searchAlbum(keyword, page), null);
            }
            this.listWidgetsMap.get(this.searchType).setScrollAmount(0);
        });
    }

    private void toggleSearch() {
        this.page = 0;
        this.search(this.searchBox.getValue(), 0);
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
            this.playButton.active = canModifyMusicList;
        }
        if (this.addButton != null) {
            this.addButton.active = canModifyMusicList;
        }
    }

    @Override
    public void onPageTurned(int page) {
        this.search(this.searchBox.getValue(), page);
    }

    @Override
    protected void init() {
        super.init();
        this.musicList = this.initListsWidget();
        this.playlistList = this.initListsWidget();
        this.albumList = this.initListsWidget();

        this.listWidgetsMap = Map.of(
                SearchType.MUSIC, this.musicList,
                SearchType.PLAYLIST, this.playlistList,
                SearchType.ALBUM, this.albumList
        );

        this.searchBox = new EditBox(this.font, this.width / 2 - 155, 17, 200, 20,
                this.searchBox, Component.translatable("concerto.screen.search"));
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);
        this.searchBox.setValue(DEFAULT_KEYWORD);

        this.infoButton = Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).pos(this.width / 2 + 120, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.infoButton);

        this.updateSearchType(this.searchType);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                button -> this.toggleSearch()).pos(this.width / 2 + 50, 17).size(52, 20).build());

        this.addRenderableWidget(CycleButton.builder((SearchType type) -> Component.literal(type.getName()), this.searchType)
                .withValues(SearchType.values()).create(
                        this.width / 2 + 105, 17, 65, 20, Component.translatable("concerto.search_type"),
                        (widget, type) -> this.updateSearchType(type)));

        this.playButton = Button.builder(Component.translatable("concerto.screen.play"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelected();
                    if (entry != null) {
                        Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.albumList.getSelected();
                    if (entry != null) {
                        Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
            }
        }).pos(this.width / 2 + 65, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.playButton);

        this.addButton = Button.builder(Component.translatable("concerto.screen.add"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(entry.item, false);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(() -> entry.item.getList(), false);
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.albumList.getSelected();
                    if (entry != null) {
                        MusicPlayerHandler.INSTANCE.addMusicAsync(() -> entry.item.getList(), false);
                    }
                }
            }
        }).pos(this.width / 2 + 10, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.addButton);

        this.updateActionButtons();
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        switch (this.searchType) {
            case PLAYLIST -> this.playlistList.extractRenderState(matrices, mouseX, mouseY, delta);
            case MUSIC -> this.musicList.extractRenderState(matrices, mouseX, mouseY, delta);
            case ALBUM -> this.albumList.extractRenderState(matrices, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.searchBox.isHoveredOrFocused()) {
            this.toggleSearch();
            return true;
        }
        return this.searchBox.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        return this.searchBox.charTyped(event);
    }
}
