package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.enums.SearchType;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.AddMusicScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;

import java.util.Map;

public class NeteaseCloudSearchScreen extends PageScreen {
    public static String DEFAULT_KEYWORD = "";
    private final ConcertoListWidget<Music> musicList;
    private final ConcertoListWidget<NeteaseCloudPlaylist> playlistList, albumList;
    private final Map<SearchType, ConcertoListWidget<?>> listWidgetMap;
    protected TextFieldWidget searchBox;
    private SearchType searchType = SearchType.MUSIC;

    private <T extends WithMetaData> ConcertoListWidget<T> initListWidget() {
        ConcertoListWidget<T> widget = new ConcertoListWidget<>(this.width, 0, 38, this.height - 35, 18);
        widget.setRenderBackground(false);
        widget.setRenderHorizontalShadows(false);
        return widget;
    }

    public NeteaseCloudSearchScreen(Screen parent) {
        super(Text.translatable("concerto.screen.search.163"), parent);
        this.musicList = this.initListWidget();
        this.playlistList = this.initListWidget();
        this.albumList = this.initListWidget();
        this.configure(page -> this.search(this.searchBox.getText(), page), this.width / 2 - 120, this.height - 30);
        this.listWidgetMap = Map.of(
                SearchType.MUSIC, this.musicList,
                SearchType.PLAYLIST, this.playlistList,
                SearchType.ALBUM, this.albumList
        );
    }

    private void search(String keyword, int page) {
        DEFAULT_KEYWORD = keyword;
        if (keyword.isEmpty()) return;
        MusicPlayer.executeThread(() -> {
            switch (this.searchType) {
                case MUSIC -> this.musicList.reset(NeteaseCloudApiClient.INSTANCE.searchMusic(keyword, page), null);
                case PLAYLIST -> this.playlistList.reset(NeteaseCloudApiClient.INSTANCE.searchPlaylist(keyword, page), null);
                case ALBUM -> this.albumList.reset(NeteaseCloudApiClient.INSTANCE.searchAlbum(keyword, page), null);
            }
        });
    }

    private void toggleSearch() {
        this.page = 0;
        this.search(this.searchBox.getText(), 0);
    }

    private void updateSearchType(SearchType type) {
        try {
            this.remove(this.listWidgetMap.get(this.searchType));
        } catch (NullPointerException ignored) {}
        this.addSelectableChild(this.listWidgetMap.get(type));
        this.searchType = type;
        this.toggleSearch();
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 155, 18, 200, 20,
                this.searchBox, Text.translatable("concerto.screen.search"));
        this.addSelectableChild(this.searchBox);
        this.searchBox.setText(DEFAULT_KEYWORD);

        this.updateSearchType(this.searchType);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.search"),
                button -> this.toggleSearch()).position(this.width / 2 + 50, 18).size(52, 20).build());

        this.addDrawableChild(CyclingButtonWidget.builder(SearchType::getName).values(SearchType.values()).initially(this.searchType).build(
                this.width / 2 + 105, 18, 65, 20, Text.translatable("concerto.search_type"),
                (widget, type) -> this.updateSearchType(type)));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            switch (this.searchType) {
                case MUSIC: {
                    ConcertoListWidget<Music>.Entry entry = this.musicList.getSelectedOrNull();
                    if (entry != null) {
                        MusicPlayer.INSTANCE.addMusicHere(entry.item, true, () -> {
                            if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
                        });
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
        }).position(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"),
                        button -> MinecraftClient.getInstance().setScreen(new AddMusicScreen(this)))
                .position(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        switch (this.searchType) {
            case PLAYLIST -> this.playlistList.render(matrices, mouseX, mouseY, delta);
            case MUSIC -> this.musicList.render(matrices, mouseX, mouseY, delta);
            case ALBUM -> this.albumList.render(matrices, mouseX, mouseY, delta);
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
