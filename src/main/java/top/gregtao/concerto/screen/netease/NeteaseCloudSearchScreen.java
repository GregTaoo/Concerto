package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.TranslatableText;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.enums.SearchType;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
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
    protected TextFieldWidget searchBox;
    private ButtonWidget infoButton, searchButton, playButton, addButton;
    private CyclingButtonWidget<SearchType> typeButton;
    private SearchType searchType = SearchType.MUSIC;

    private <T extends WithMetaData> MetadataListWidget<T> initListsWidget() {
        return new MetadataListWidget<>(NeteaseCloudSearchScreen.this.width, NeteaseCloudSearchScreen.this.height, 45, NeteaseCloudSearchScreen.this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                try {
                    switch (NeteaseCloudSearchScreen.this.searchType) {
                        case MUSIC: {
                            MusicPlayer.INSTANCE.addMusicHere((Music) entry.item, true);
                            break;
                        }
                        case PLAYLIST, ALBUM: {
                            MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, NeteaseCloudSearchScreen.this));
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
        super(new TranslatableText("concerto.screen.search.163"), parent);
    }

    private void search(String keyword, int page) {
        DEFAULT_KEYWORD = keyword;
        if (keyword.isEmpty()) return;
        MusicPlayer.run(() -> {
            switch (this.searchType) {
                case MUSIC -> this.musicList.reset(NeteaseCloudApiClient.INSTANCE.searchMusic(keyword, page), null);
                case PLAYLIST -> this.playlistList.reset(NeteaseCloudApiClient.INSTANCE.searchPlaylist(keyword, page), null);
                case ALBUM -> this.albumList.reset(NeteaseCloudApiClient.INSTANCE.searchAlbum(keyword, page), null);
            }
            this.listWidgetsMap.get(this.searchType).setScrollAmount(0);
        });
    }

    private void toggleSearch() {
        this.page = 0;
        this.search(this.searchBox.getText(), 0);
    }

    private void updateSearchType(SearchType type) {
        try {
            this.remove(this.listWidgetsMap.get(this.searchType));
        } catch (NullPointerException ignored) {}
        this.addDrawableChild(this.listWidgetsMap.get(type));
        this.refreshDrawable();
        this.searchType = type;
        this.infoButton.active = type == SearchType.MUSIC;
        this.toggleSearch();
    }

    @Override
    public void refreshDrawable() {
        try {
            super.refreshDrawable();
            this.remove(this.searchBox);
            this.addDrawableChild(this.searchBox);
            this.addSelectableChild(this.searchBox);
            this.remove(this.infoButton);
            this.addDrawableChild(this.infoButton);
            this.remove(this.searchButton);
            this.addDrawableChild(this.searchButton);
            this.remove(this.typeButton);
            this.addDrawableChild(this.typeButton);
            this.remove(this.playButton);
            this.addDrawableChild(this.playButton);
            this.remove(this.addButton);
            this.addDrawableChild(this.addButton);
        } catch (NullPointerException ignored) {}
    }

    @Override
    public void onPageTurned(int page) {
        this.search(this.searchBox.getText(), page);
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

        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 155, 17, 200, 20,
                this.searchBox, new TranslatableText("concerto.screen.search"));
        this.searchBox.setText(DEFAULT_KEYWORD);

        this.infoButton = new ButtonWidget(this.width / 2 + 120, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        });

        this.searchButton = new ButtonWidget(this.width / 2 + 50, 17, 52, 20,
                new TranslatableText("concerto.screen.search"), button -> this.toggleSearch());

        this.typeButton = CyclingButtonWidget.builder(SearchType::getName).values(SearchType.values()).initially(this.searchType).build(
                this.width / 2 + 105, 17, 65, 20, new TranslatableText("concerto.search_type"),
                (widget, type) -> this.updateSearchType(type));

        this.playButton = new ButtonWidget(this.width / 2 + 65, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusicHere(entry.item, true);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
                    if (entry != null) {
                        MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.albumList.getSelectedOrNull();
                    if (entry != null) {
                        MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
            }
        });

        this.addButton = new ButtonWidget(this.width / 2 + 10, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.add"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(entry.item);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(() -> entry.item.getList(), () -> {});
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.albumList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(() -> entry.item.getList(), () -> {});
                    }
                }
            }
        });
        this.updateSearchType(this.searchType);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isActive()) {
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
