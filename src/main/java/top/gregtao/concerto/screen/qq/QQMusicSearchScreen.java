package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.enums.SearchType;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.QQMusicPlaylist;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.MusicInfoScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.HashMap;
import java.util.Map;

public class QQMusicSearchScreen extends PageScreen {
    public static String DEFAULT_KEYWORD = "";
    private MetadataListWidget<Music> musicList;
    private MetadataListWidget<QQMusicPlaylist> playlistList;
    private MetadataListWidget<QQMusicPlaylist> albumList;
    private Map<SearchType, ConcertoListWidget<?>> listWidgetsMap = new HashMap<>();
    protected TextFieldWidget searchBox;
    private ButtonWidget infoButton;
    private SearchType searchType = SearchType.MUSIC;

    private <T extends WithMetaData> MetadataListWidget<T> initListsWidget() {
        MetadataListWidget<T> widget = new MetadataListWidget<>(this.width, this.height, 38, this.height - 35, 18, entry -> {
            try {
                switch (this.searchType) {
                    case MUSIC: {
                        MusicPlayer.INSTANCE.addMusicHere((Music) entry.item, true);
                        break;
                    }
                    case PLAYLIST, ALBUM: {
                        MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, this));
                        break;
                    }
                }
            } catch (ClassCastException e) {
                ConcertoClient.LOGGER.error(e.getMessage());
            }
        });
        widget.setRenderBackground(false);
        widget.setRenderHorizontalShadows(false);
        return widget;
    }

    public QQMusicSearchScreen(Screen parent) {
        super(Text.translatable("concerto.screen.search.163"), parent);
    }

    private void search(String keyword, int page) {
        DEFAULT_KEYWORD = keyword;
        if (keyword.isEmpty()) return;
        MusicPlayer.run(() -> {
            switch (this.searchType) {
                case MUSIC -> this.musicList.reset(QQMusicApiClient.INSTANCE.searchMusic(keyword, page), null);
                case PLAYLIST -> this.playlistList.reset(QQMusicApiClient.INSTANCE.searchPlaylist(keyword, page), null);
                case ALBUM -> this.albumList.reset(QQMusicApiClient.INSTANCE.searchAlbum(keyword, page), null);
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
        this.addSelectableChild(this.listWidgetsMap.get(type));
        this.searchType = type;
        this.infoButton.active = type == SearchType.MUSIC;
        this.toggleSearch();
    }

    @Override
    protected void init() {
        this.configure(page -> this.search(this.searchBox.getText(), page), this.width / 2 - 120, this.height - 30);

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
                this.searchBox, Text.translatable("concerto.screen.search"));
        this.addSelectableChild(this.searchBox);
        this.searchBox.setText(DEFAULT_KEYWORD);

        this.infoButton = ButtonWidget.builder(Text.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).position(this.width / 2 + 120, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.infoButton);

        this.updateSearchType(this.searchType);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.search"),
                button -> this.toggleSearch()).position(this.width / 2 + 50, 17).size(52, 20).build());

        this.addDrawableChild(CyclingButtonWidget.builder(SearchType::getName).values(SearchType.values()).initially(this.searchType).build(
                this.width / 2 + 105, 17, 65, 20, Text.translatable("concerto.search_type"),
                (widget, type) -> this.updateSearchType(type)));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusicHere(entry.item, true);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
                    if (entry != null) {
                        MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.albumList.getSelectedOrNull();
                    if (entry != null) {
                        MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
                    }
                }
            }
        }).position(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(entry.item);
                    }
                }
                case PLAYLIST: {
                    ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(() -> entry.item.getList(), () -> {});
                    }
                }
                case ALBUM: {
                    ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.albumList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusic(() -> entry.item.getList(), () -> {});
                    }
                }
            }
        }).position(this.width / 2 + 10, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        try {
            switch (this.searchType) {
                case PLAYLIST -> this.playlistList.render(matrices, mouseX, mouseY, delta);
                case MUSIC -> this.musicList.render(matrices, mouseX, mouseY, delta);
                case ALBUM -> this.albumList.render(matrices, mouseX, mouseY, delta);
            }
        } catch (IndexOutOfBoundsException e) {
            ConcertoClient.LOGGER.error(e.getMessage());
        }
        this.searchBox.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isSelected()) {
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
