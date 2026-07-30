package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.util.List;

/**
 * The single landing screen for a music service: account, search, playlists,
 * and service-specific actions share one layout instead of a service index and
 * a separate user screen.
 */
public abstract class UserPlaylistScreen<T extends Playlist> extends ConcertoScreen {
    private static final int ACCOUNT_TOP = 16;
    private static final int AVATAR_SIZE = 32;
    private static final int LIST_LABEL_Y = 50;
    private static final int LIST_TOP = 61;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HEADER_BUTTON_WIDTH = 92;
    private static final int AVATAR_TEXT_MARGIN = 4;

    private MetadataListWidget<T> playlistList;
    private URLImageWidget avatar;
    private int page;
    private Button pageLabel;

    protected UserPlaylistScreen(Component title, Screen parent) {
        super(title, parent);
    }

    protected abstract boolean isLoggedIn();

    protected abstract boolean refreshLoginStatus();

    protected abstract List<T> getUserPlaylists(int page);

    protected abstract Screen createLoginScreen(Screen parent);

    protected abstract Screen createSearchScreen(Screen parent);

    protected abstract Component getAccountSummary();

    protected abstract Component getLoggedOutSummary();

    protected abstract String getAvatarUrl();

    protected abstract void logout();

    protected boolean hasPagination() {
        return true;
    }

    protected int serviceActionCount() {
        return 0;
    }

    protected void addServiceActions(int x, int y, int width) {
    }

    @Override
    protected void init() {
        super.init();
        int contentX = this.standardContentX();
        int contentWidth = this.standardContentWidth();
        boolean loggedIn = this.isLoggedIn();
        int accountActionX = contentX + contentWidth - HEADER_BUTTON_WIDTH;
        if (loggedIn) {
            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.switch_account"), button -> {
                this.logout();
                Minecraft.getInstance().gui.setScreen(this.createLoginScreen(this));
            }).pos(accountActionX, this.headerActionY()).size(HEADER_BUTTON_WIDTH, BUTTON_HEIGHT).build());
            this.initAvatar(contentX);
            this.initPlaylistArea(contentX);
        } else {
            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.go_login"),
                    button -> Minecraft.getInstance().gui.setScreen(this.createLoginScreen(this)))
                    .pos(accountActionX, this.headerActionY()).size(HEADER_BUTTON_WIDTH, BUTTON_HEIGHT).build());
            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                    button -> Minecraft.getInstance().gui.setScreen(this.createSearchScreen(this)))
                    .pos(contentX, this.standardBottomActionY()).size(contentWidth, BUTTON_HEIGHT).build());
        }
    }

    private void initAvatar(int x) {
        String avatarUrl = this.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isEmpty()) return;
        this.avatar = new URLImageWidget(AVATAR_SIZE, AVATAR_SIZE, x, ACCOUNT_TOP, avatarUrl, false);
        this.avatar.loadImageAsync(true, true);
    }
    private void initPlaylistArea(int contentX) {
        int footerY = this.standardBottomActionY();
        int listHeight = footerY - STANDARD_ACTION_GAP * 2 - LIST_TOP;
        this.playlistList = new MetadataListWidget<>(this.width, listHeight, LIST_TOP, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                Minecraft.getInstance().gui.setScreen(new PlaylistPreviewScreen(entry.item, UserPlaylistScreen.this));
            }
        };
        this.addRenderableWidget(this.playlistList);
        this.addWidget(this.playlistList);

        int actionX = contentX;
        if (this.hasPagination()) actionX = this.addPaginationControls(contentX, footerY);
        this.addActions(actionX, footerY, this.standardContentRight());
        this.loadPage();
    }

    private int addPaginationControls(int x, int y) {
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.previous_page"), button -> {
            if (this.page > 0) {
                --this.page;
                this.pageLabel.setMessage(Component.translatable("concerto.screen.page", this.page + 1));
                this.loadPage();
            }
        }).pos(x, y).size(20, BUTTON_HEIGHT).build());
        x += 20 + STANDARD_ACTION_GAP;
        int labelWidth = this.font.width(Component.translatable("concerto.screen.page", 999));
        this.pageLabel = Button.builder(Component.translatable("concerto.screen.page", this.page + 1), button -> {
        }).pos(x, y).size(labelWidth, BUTTON_HEIGHT).build();
        this.pageLabel.active = false;
        this.addRenderableWidget(this.pageLabel);
        x += labelWidth + STANDARD_ACTION_GAP;
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.next_page"), button -> {
            ++this.page;
            this.pageLabel.setMessage(Component.translatable("concerto.screen.page", this.page + 1));
            this.loadPage();
        }).pos(x, y).size(20, BUTTON_HEIGHT).build());
        return x + 20 + STANDARD_ACTION_GAP;
    }
    private void addActions(int x, int y, int right) {
        int count = this.serviceActionCount() + 2;
        int actionWidth = (right - x - STANDARD_ACTION_GAP * (count - 1)) / count;
        if (this.serviceActionCount() > 0) {
            this.addServiceActions(x, y, actionWidth);
            x += (actionWidth + STANDARD_ACTION_GAP) * this.serviceActionCount();
        }
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                button -> Minecraft.getInstance().gui.setScreen(this.createSearchScreen(this)))
                .pos(x, y).size(actionWidth, BUTTON_HEIGHT).build());
        x += actionWidth + STANDARD_ACTION_GAP;
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.open_selected_playlist"), button -> {
            ConcertoListWidget<T>.Entry entry = this.playlistList.getSelected();
            if (entry != null) Minecraft.getInstance().gui.setScreen(new PlaylistPreviewScreen(entry.item, this));
        }).pos(x, y).size(right - x, BUTTON_HEIGHT).build());
    }

    private void loadPage() {
        ConcertoRunner.run(() -> {
            if (!this.refreshLoginStatus()) {
                Minecraft.getInstance().execute(() -> {
                    this.rebuildWidgets();
                });
                return;
            }
            List<T> playlists = this.getUserPlaylists(this.page);
            Minecraft.getInstance().execute(() -> {
                if (this.playlistList != null) this.playlistList.reset(playlists, null);
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentX = this.standardContentX();
        int contentRight = this.standardContentRight();
        if (this.isLoggedIn()) {
            if (this.avatar != null) this.avatar.extractRenderState(graphics, mouseX, mouseY, delta);
            int textX = this.avatar == null ? contentX : contentX + AVATAR_SIZE + AVATAR_TEXT_MARGIN;
            int actionStart = contentRight - HEADER_BUTTON_WIDTH;
            String summary = this.ellipsize(this.getAccountSummary().getString(), Math.max(0, actionStart - STANDARD_ACTION_GAP - textX));
            graphics.text(this.font, summary, textX, this.accountTextY(), 0xffffffff, false);
            graphics.text(this.font, Component.translatable("concerto.screen.section.my_playlists"), contentX,
                    LIST_LABEL_Y, 0xffa0a0a0, false);
        } else {
            int actionStart = contentRight - HEADER_BUTTON_WIDTH;
            String summary = this.ellipsize(this.getLoggedOutSummary().getString(), Math.max(0, actionStart - STANDARD_ACTION_GAP - contentX));
            graphics.text(this.font, summary, contentX, this.accountTextY(), 0xffffffff, false);
            graphics.centeredText(this.font, Component.translatable("concerto.screen.playlists.login_required"), this.width / 2,
                    this.height / 2, 0xffaaaaaa);
        }
    }

    private int headerActionY() {
        return ACCOUNT_TOP + (AVATAR_SIZE - BUTTON_HEIGHT) / 2;
    }

    private int accountTextY() {
        return ACCOUNT_TOP + (AVATAR_SIZE - this.font.lineHeight) / 2;
    }

    private String ellipsize(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) return this.font.plainSubstrByWidth(text, maxWidth);
        return this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.avatar != null) this.avatar.close();
    }
}
