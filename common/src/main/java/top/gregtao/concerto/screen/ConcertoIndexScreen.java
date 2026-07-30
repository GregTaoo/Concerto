package top.gregtao.concerto.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.screen.kugou.KuGouMusicUserScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudUserScreen;
import top.gregtao.concerto.screen.qq.QQMusicUserScreen;

public class ConcertoIndexScreen extends ConcertoScreen {
    private static final int SECTION_LABEL_COLOR = 0xffa0a0a0;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int COLUMN_GAP = 4;

    private int contentX;
    private int contentWidth;
    private int onlineMusicY;
    private int serverActionsY;

    public ConcertoIndexScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.contentWidth = Math.min(460, this.standardContentWidth());
        this.contentX = (this.width - this.contentWidth) / 2;
        int buttonWidth = (this.contentWidth - COLUMN_GAP) / 2;
        int leftX = this.contentX;
        int rightX = leftX + buttonWidth + COLUMN_GAP;
        int myPlaybackY = 33;
        this.onlineMusicY = myPlaybackY + BUTTON_HEIGHT * 2 + ROW_GAP * 2 + 9;
        this.serverActionsY = this.onlineMusicY + BUTTON_HEIGHT * 2 + ROW_GAP * 2 + 9;

        this.addButton(Component.translatable("concerto.screen.main_list"), leftX, myPlaybackY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new MainPlaylistScreen(this)));
        this.addButton(Component.translatable("concerto.screen.local_playlists"), rightX, myPlaybackY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new PlaylistListScreen(
                        Component.translatable("concerto.screen.local_playlists"), this,
                        PresetPlaylistsConfig.LOCAL_PLAYLISTS.getRadios())));
        this.addButton(Component.translatable("concerto.screen.add_music"), leftX, myPlaybackY + BUTTON_HEIGHT + ROW_GAP, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new AddMusicScreen(this)));
        this.addButton(Component.translatable("concerto.screen.options"), rightX, myPlaybackY + BUTTON_HEIGHT + ROW_GAP, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new ConcertoOptionsScreen(this)));

        this.addButton(Component.translatable("concerto.screen.index.163"), leftX, this.onlineMusicY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new NeteaseCloudUserScreen(this)));
        this.addButton(Component.translatable("concerto.screen.index.qq"), rightX, this.onlineMusicY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new QQMusicUserScreen(this)));
        this.addButton(Component.translatable("concerto.screen.index.kugou"), leftX, this.onlineMusicY + BUTTON_HEIGHT + ROW_GAP, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new KuGouMusicUserScreen(this)));

        LocalPlayer player = Minecraft.getInstance().player;
        boolean serverAvailable = player != null && ConcertoClient.isServerAvailable();
        this.addButton(Component.translatable("concerto.screen.preset_radios"), leftX, this.serverActionsY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new PresetRadiosScreen(this))).active = serverAvailable;
        this.addButton(Component.translatable("concerto.screen.audition"), rightX, this.serverActionsY, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new MusicAuditionScreen(this))).active =
                serverAvailable && player.hasPermissions(2);
        this.addButton(Component.translatable("concerto.screen.confirmation"), leftX,
                this.serverActionsY + BUTTON_HEIGHT + ROW_GAP, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new MusicConfirmationScreen(this)));
        this.addButton(Component.translatable("concerto.screen.rooms"), rightX,
                this.serverActionsY + BUTTON_HEIGHT + ROW_GAP, buttonWidth,
                button -> Minecraft.getInstance().setScreen(new MusicRoomsScreen(this))).active = serverAvailable;

        Component reportBugs = Component.translatable("concerto.report_bugs");
        int reportWidth = this.font.width(reportBugs);
        this.addRenderableWidget(new PlainTextButton(this.contentX + this.contentWidth - reportWidth, this.height - 14,
                reportWidth, this.font.lineHeight, reportBugs,
                button -> Util.getPlatform().openUri("https://github.com/GregTaoo/Concerto/issues"), this.font));

        if (this.minecraft != null && MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_ROOM) {
            String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
            Component text = Component.translatable("concerto.screen.in_music_room", uuid);
            int textWidth = this.font.width(text);
            this.addRenderableWidget(new PlainTextButton(
                    (this.width - textWidth) / 2, this.serverActionsY + BUTTON_HEIGHT * 2 + ROW_GAP + 9,
                    textWidth, this.font.lineHeight, text,
                    button -> this.minecraft.keyboardHandler.setClipboard(uuid), this.font));
        }
    }

    private Button addButton(Component text, int x, int y, int width, Button.OnPress onPress) {
        return this.addRenderableWidget(Button.builder(text, onPress).pos(x, y).size(width, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        matrices.drawString(this.font, Component.translatable("concerto.screen.section.my_playback"),
                this.contentX, 22, SECTION_LABEL_COLOR, false);
        matrices.drawString(this.font, Component.translatable("concerto.screen.section.online_music"),
                this.contentX, this.onlineMusicY - 11, SECTION_LABEL_COLOR, false);
        matrices.drawString(this.font, Component.translatable("concerto.screen.section.server_management"),
                this.contentX, this.serverActionsY - 11, SECTION_LABEL_COLOR, false);
        if (MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT) {
            matrices.drawCenteredString(this.font, Component.translatable("concerto.screen.in_music_agent"), this.width / 2,
                    this.serverActionsY + BUTTON_HEIGHT * 2 + ROW_GAP + 9, 0xffffffff);
        }
    }
}
