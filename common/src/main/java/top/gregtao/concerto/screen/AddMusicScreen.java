package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.UnsafeMusicException;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.music.*;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

public class AddMusicScreen extends ApplyDraggedFileScreen {
    private static final int ROW_HEIGHT = 25;
    private AddMusicList inputList;

    public AddMusicScreen(Screen parent) {
        super(Component.translatable("concerto.screen.manual_add"), parent);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            this.displayAlert(Component.translatable("concerto.fail"));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.inputList = new AddMusicList(this.width, this.height - 65, 30);
        this.addWidget(this.inputList);
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.local_file"), Component.empty(), str -> {
            try {
                MusicPlayerHandler.INSTANCE.addMusicHere(new LocalFileMusic(str), true);
            } catch (UnsafeMusicException e) {
                this.displayAlert(Component.translatable("concerto.error.invalid_path"));
            }
        });
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.local_file.folder"), Component.empty(),
                str -> ConcertoRunner.run(() -> {
                    ArrayList<Music> list = LocalFileMusic.getMusicsInFolder(new File(str));
                    MusicPlayerHandler.INSTANCE.addMusicAsync(list, true, () -> {
                    });
                }));
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.internet"), Component.empty(),
                str -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(new HttpFileMusic(str), true, () -> {
                }));
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.netease_cloud"), Component.empty(),
                str -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(new NeteaseCloudMusic(str,
                        ClientConfig.INSTANCE.options.neteaseMusicQuality), true, () -> {
                }));
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.netease_cloud.playlist"), Component.empty(), str -> {
            NeteaseCloudPlaylist playlist = new NeteaseCloudPlaylist(str, false);
            playlist.load(() -> Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)));
        });
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.netease_cloud.album"), Component.empty(), str -> {
            NeteaseCloudPlaylist playlist = new NeteaseCloudPlaylist(str, false);
            playlist.load(() -> Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)));
        });
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.qq"), Component.empty(),
                str -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(new QQMusic(str), true, () -> {
                }));
        this.inputList.addInputRow(Component.translatable("concerto.screen.add.kugou"), Component.empty(),
                str -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(new KuGouMusic(str, true), true, () -> {
                }));
        this.inputList.addBilibiliRow();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        this.inputList.render(graphics, mouseX, mouseY, delta);
        this.renderWidgets(graphics, mouseX, mouseY, delta);
    }

    private class AddMusicList extends ContainerObjectSelectionList<AddMusicRow> {
        private static final int FIELD_WIDTH = 115;
        private static final int BUTTON_WIDTH = 60;

        private AddMusicList(int width, int height, int top) {
            super(Minecraft.getInstance(), width, height, top, top + height, ROW_HEIGHT);
            this.centerListVertically = false;
        }

        private EditBox createInput(Component hint, int width) {
            EditBox input = new EditBox(AddMusicScreen.this.font, 0, 0, width, 20, hint);
            input.setHint(hint);
            input.setMaxLength(1024);
            return input;
        }

        private void addInputRow(Component label, Component hint, Consumer<String> onAdd) {
            EditBox input = this.createInput(hint, FIELD_WIDTH);
            Button button = Button.builder(Component.translatable("concerto.screen.add"),
                    ignored -> AddMusicScreen.this.runSafely(() -> onAdd.accept(input.getValue().trim())))
                    .size(BUTTON_WIDTH, 20).build();
            button.active = PlayerPermissions.canModifyMusicList();
            this.addEntry(new AddMusicRow(label, List.of(input, button), List.of(0, FIELD_WIDTH + 5)));
        }

        private void addBilibiliRow() {
            EditBox bvidInput = this.createInput(Component.translatable("concerto.screen.add.bilibili.bvid_hint"), 75);
            EditBox pageInput = this.createInput(Component.translatable("concerto.screen.add.bilibili.page_hint"), 35);
            Button button = Button.builder(Component.translatable("concerto.screen.add"), ignored -> AddMusicScreen.this.runSafely(() -> {
                String bvid = bvidInput.getValue().trim();
                if (bvid.isEmpty()) throw new IllegalArgumentException("BVID is empty");
                String pageText = pageInput.getValue().trim();
                Integer page = pageText.isEmpty() ? null : Integer.parseInt(pageText.replaceFirst("(?i)^p", ""));
                if (page != null && page < 1) throw new IllegalArgumentException("Bilibili page must be positive");
                MusicPlayerHandler.INSTANCE.addMusicHereAsync(new BilibiliMusic(bvid, page), true, () -> {
                });
            })).size(BUTTON_WIDTH, 20).build();
            button.active = PlayerPermissions.canModifyMusicList();
            this.addEntry(new AddMusicRow(Component.translatable("concerto.screen.add.bilibili"),
                    List.of(bvidInput, pageInput, button), List.of(0, 80, FIELD_WIDTH + 5)));
        }

        @Override
        public int getRowWidth() {
            return 350;
        }
    }

    private class AddMusicRow extends ContainerObjectSelectionList.Entry<AddMusicRow> {
        private final Component label;
        private final List<AbstractWidget> widgets;
        private final List<Integer> offsets;

        private AddMusicRow(Component label, List<? extends AbstractWidget> widgets, List<Integer> offsets) {
            this.label = label;
            this.widgets = List.copyOf(widgets);
            this.offsets = List.copyOf(offsets);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            int fieldX = AddMusicScreen.this.width / 2 - 20;
            graphics.drawString(AddMusicScreen.this.font, this.label,
                    fieldX - 8 - AddMusicScreen.this.font.width(this.label), y + 6, 0xffffffff, false);
            for (int widgetIndex = 0; widgetIndex < this.widgets.size(); widgetIndex++) {
                AbstractWidget widget = this.widgets.get(widgetIndex);
                widget.setPosition(fieldX + this.offsets.get(widgetIndex), y);
                widget.render(graphics, mouseX, mouseY, delta);
            }
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }
}
