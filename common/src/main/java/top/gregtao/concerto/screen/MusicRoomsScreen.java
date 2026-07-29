package top.gregtao.concerto.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.network.room.MusicRoomManager;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Music room hub. List rows and every control row share the standard
 * {@link ConcertoScreen} content boundary.
 */
public class MusicRoomsScreen extends ConcertoScreen {

    private static final int ROW_H = 24;
    private static final int BUTTON_H = 20;

    private MusicRoom.ClientState builtState;
    private int builtPermission;
    private ConcertoListWidget<MusicRoom.RoomSummary> roomListWidget;
    private ConcertoListWidget<Pair<String, Integer>> memberListWidget;
    private EditBox nameBox;
    private Button joinButton;
    private Button opButton;
    private CycleButton<Boolean> visibleButton, joinableButton;
    private Runnable listListener;
    private int lastMembersHash = 0;
    private int listTop, listBottom;

    public MusicRoomsScreen(Screen parent) {
        super(Component.translatable("concerto.screen.rooms"), parent);
    }


    @Override
    protected void init() {
        super.init();
        this.roomListWidget = null;
        this.memberListWidget = null;
        this.nameBox = null;
        this.joinButton = null;
        this.opButton = null;
        this.visibleButton = this.joinableButton = null;
        this.builtState = MusicRoom.clientGetState();
        this.builtPermission = MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.permission : 0;
        if (this.builtState == MusicRoom.ClientState.LOCAL) {
            this.initDiscovery();
        } else {
            this.initManagement();
        }
    }

    // ---- Discovery: browse visible rooms, join, create ----

    private void initDiscovery() {
        int row2 = this.standardBottomActionY();
        int row1 = row2 - ROW_H;
        int x = this.standardContentX();
        int contentWidth = this.standardContentWidth();
        int fieldWidth = (contentWidth - STANDARD_ACTION_GAP) * 2 / 3;
        int actionWidth = (contentWidth - STANDARD_ACTION_GAP * 2) / 3;

        this.listTop = 20;
        this.listBottom = row1 - STANDARD_ACTION_GAP;
        this.roomListWidget = new ConcertoListWidget<>(this.width, this.listBottom - this.listTop, this.listTop, 18) {
            @Override
            public Component getNarration(int index, MusicRoom.RoomSummary room) {
                Component base = Component.translatable("concerto.room.list.entry",
                        room.name(), room.owner(), room.memberCount());
                if (room.joinable()) return base;
                return base.copy().append(" ")
                        .append(Component.translatable("concerto.room.list.locked").withStyle(ChatFormatting.GRAY));
            }

            @Override
            public void onDoubleClicked(ConcertoListWidget<MusicRoom.RoomSummary>.Entry entry) {
                MusicRoomsScreen.this.joinRoom(entry.item);
            }
        };
        this.addWidget(this.roomListWidget);
        this.roomListWidget.reset(MusicRoom.clientRoomList, null);

        this.nameBox = new EditBox(this.font, x, row1, fieldWidth, BUTTON_H,
                Component.translatable("concerto.room.name_hint"));
        this.nameBox.setHint(Component.translatable("concerto.room.name_hint"));
        this.nameBox.setMaxLength(MusicRoom.MAX_ROOM_NAME_LENGTH);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.create"),
                button -> MusicRoomManager.clientCreate(this.nameBox.getValue())
        ).pos(x + fieldWidth + STANDARD_ACTION_GAP, row1).size(contentWidth - fieldWidth - STANDARD_ACTION_GAP, BUTTON_H).build());

        this.joinButton = Button.builder(Component.translatable("concerto.room.list.join"),
                button -> {
                    ConcertoListWidget<MusicRoom.RoomSummary>.Entry entry = this.roomListWidget.getSelected();
                    if (entry != null) this.joinRoom(entry.item);
                }
        ).pos(x, row2).size(actionWidth, BUTTON_H).build();
        this.addRenderableWidget(this.joinButton);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"),
                button -> MusicRoomManager.clientRequestList()
        ).pos(x + actionWidth + STANDARD_ACTION_GAP, row2).size(actionWidth, BUTTON_H).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.agent.join"),
                button -> ServerMusicAgentManager.clientJoin()
        ).pos(x + (actionWidth + STANDARD_ACTION_GAP) * 2, row2)
                .size(this.standardContentRight() - x - (actionWidth + STANDARD_ACTION_GAP) * 2, BUTTON_H).build());

        // The LIST reply arrives on the network thread; refresh on the render thread
        this.listListener = () -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen == this && this.roomListWidget != null) {
                this.roomListWidget.reset(MusicRoom.clientRoomList, null);
            }
        });
        MusicRoom.clientRoomListListener = this.listListener;
        MusicRoomManager.clientRequestList();
    }

    private void joinRoom(MusicRoom.RoomSummary room) {
        if (room == null) return;
        if (!room.joinable()) {
            this.displayAlert(Component.translatable("concerto.room.join.denied"));
            return;
        }
        MusicRoomManager.clientJoin(room.uuid().toString());
    }

    // ---- Management: current room info, members, settings ----

    private void initManagement() {
        boolean agent = this.builtState == MusicRoom.ClientState.MUSIC_AGENT;
        MusicRoom.MusicRoomState state = MusicRoom.clientGetRoomState();
        int perm = this.builtPermission;
        boolean canEdit = !agent && perm >= 2 && state != null;

        // Bottom-up: quit/op row always present; toggles and rename only for ops
        int rowQuit = this.standardBottomActionY();
        int rowToggles = rowQuit - ROW_H;
        int rowName = rowToggles - ROW_H;
        int x = this.standardContentX();
        int contentWidth = this.standardContentWidth();
        int fieldWidth = (contentWidth - STANDARD_ACTION_GAP) * 2 / 3;
        int halfWidth = (contentWidth - STANDARD_ACTION_GAP) / 2;

        this.listBottom = (canEdit ? rowName : rowQuit) - STANDARD_ACTION_GAP;
        this.memberListWidget = new ConcertoListWidget<>(this.width, this.listBottom - this.listTop, this.listTop, 18) {
            @Override
            public Component getNarration(int index, Pair<String, Integer> member) {
                return Component.literal(member.getFirst() + "  ")
                        .append(Component.translatable(permKey(member.getSecond())).withStyle(ChatFormatting.GRAY));
            }
        };
        this.addWidget(this.memberListWidget);
        this.resetMemberList(state);

        if (canEdit) {
            this.nameBox = new EditBox(this.font, x, rowName, fieldWidth, BUTTON_H,
                    Component.translatable("concerto.room.name_hint"));
            this.nameBox.setHint(Component.translatable("concerto.room.name_hint"));
            this.nameBox.setMaxLength(MusicRoom.MAX_ROOM_NAME_LENGTH);
            this.nameBox.setValue(state.roomName);
            this.addRenderableWidget(this.nameBox);

            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.save"),
                    button -> this.pushRoomInfo(this.nameBox.getValue())
            ).pos(x + fieldWidth + STANDARD_ACTION_GAP, rowName).size(contentWidth - fieldWidth - STANDARD_ACTION_GAP, BUTTON_H).build());

            this.visibleButton = CycleButton.onOffBuilder(state.visible).create(
                    x, rowToggles, halfWidth, BUTTON_H,
                    Component.translatable("concerto.room.visible"),
                    (button, value) -> this.pushRoomInfo(null));
            this.addRenderableWidget(this.visibleButton);

            this.joinableButton = CycleButton.onOffBuilder(state.joinable).create(
                    x + halfWidth + STANDARD_ACTION_GAP, rowToggles, contentWidth - halfWidth - STANDARD_ACTION_GAP, BUTTON_H,
                    Component.translatable("concerto.room.joinable"),
                    (button, value) -> this.pushRoomInfo(null));
            this.addRenderableWidget(this.joinableButton);
        }

        boolean owner = !agent && perm >= 3;
        Button quitButton = Button.builder(
                Component.translatable(owner ? "concerto.room.dissolve" : "concerto.screen.quit"),
                button -> {
                    if (agent) {
                        ServerMusicAgentManager.clientQuit();
                    } else {
                        MusicRoomManager.clientQuit();
                    }
                }
        ).pos(x, rowQuit).size(owner ? halfWidth : contentWidth, BUTTON_H).build();
        this.addRenderableWidget(quitButton);

        if (owner) {
            this.opButton = Button.builder(Component.translatable("concerto.room.op.set"),
                    button -> {
                        ConcertoListWidget<Pair<String, Integer>>.Entry entry = this.memberListWidget.getSelected();
                        if (entry != null) MusicRoomManager.clientSetOp(entry.item.getFirst());
                    }
            ).pos(x + halfWidth + STANDARD_ACTION_GAP, rowQuit).size(contentWidth - halfWidth - STANDARD_ACTION_GAP, BUTTON_H).build();
            this.addRenderableWidget(this.opButton);
        }
    }

    private static String permKey(int permission) {
        return switch (permission) {
            case 3 -> "concerto.room.perm.owner";
            case 2 -> "concerto.room.perm.op";
            default -> "concerto.room.perm.member";
        };
    }

    private void pushRoomInfo(String newName) {
        boolean visible = this.visibleButton == null || this.visibleButton.getValue();
        boolean joinable = this.joinableButton == null || this.joinableButton.getValue();
        MusicRoom.clientSetRoomInfo(newName, visible, joinable);
    }

    private void resetMemberList(MusicRoom.MusicRoomState state) {
        if (this.memberListWidget == null) return;
        List<Pair<String, Integer>> members = new ArrayList<>();
        if (state != null) {
            state.members.forEach((name, permission) -> members.add(Pair.of(name, permission)));
            members.sort(Comparator.<Pair<String, Integer>>comparingInt(p -> -p.getSecond())
                    .thenComparing(Pair::getFirst));
            this.lastMembersHash = state.members.hashCode();
        }
        this.memberListWidget.reset(members, null);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Joining/quitting flips the client state on the network thread, and a
        // permission change (op granted/revoked) changes which controls exist;
        // the screen follows by rebuilding into the matching mode
        int perm = MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.permission : 0;
        if (MusicRoom.clientGetState() != this.builtState || perm != this.builtPermission) {
            this.rebuildWidgets();
            return;
        }

        if (this.builtState == MusicRoom.ClientState.LOCAL) {
            this.roomListWidget.render(context, mouseX, mouseY, delta);
            if (this.roomListWidget.children().isEmpty()) {
                context.drawCenteredString(this.font, Component.translatable("concerto.room.list.empty"),
                        this.width / 2, (this.listTop + this.listBottom) / 2 - this.font.lineHeight / 2, 0xFFAAAAAA);
            }
            ConcertoListWidget<MusicRoom.RoomSummary>.Entry selected = this.roomListWidget.getSelected();
            this.joinButton.active = selected != null && selected.item.joinable();
        } else {
            MusicRoom.MusicRoomState state = MusicRoom.clientGetRoomState();
            Component header = this.builtState == MusicRoom.ClientState.MUSIC_AGENT
                    ? Component.translatable("concerto.screen.in_music_agent")
                    : Component.translatable("concerto.room.current",
                            state == null || state.roomName.isEmpty()
                                    ? (MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.uuid.toString() : "?")
                                    : state.roomName);
            context.drawCenteredString(this.font, header, this.width / 2, 20, 0xFFFFFFFF);
            if (this.builtState == MusicRoom.ClientState.MUSIC_ROOM && MusicRoom.CLIENT_ROOM != null) {
                String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
                boolean hoveringUuid = this.isHoveringRoomUuid(mouseX, mouseY, uuid);
                context.drawCenteredString(this.font,
                        Component.literal(uuid).withStyle(style -> style
                                .withColor(ChatFormatting.DARK_GRAY)
                                .withUnderlined(hoveringUuid)),
                        this.width / 2, 32, 0xFF888888);
            }

            if (state != null && state.members.hashCode() != this.lastMembersHash) {
                this.resetMemberList(state);
            }
            this.memberListWidget.render(context, mouseX, mouseY, delta);

            if (this.opButton != null) {
                ConcertoListWidget<Pair<String, Integer>>.Entry entry = this.memberListWidget.getSelected();
                boolean valid = entry != null && entry.item.getSecond() < 3;
                this.opButton.active = valid;
                this.opButton.setMessage(Component.translatable(
                        entry != null && entry.item.getSecond() == 2 ? "concerto.room.op.unset" : "concerto.room.op.set"));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.builtState == MusicRoom.ClientState.MUSIC_ROOM && MusicRoom.CLIENT_ROOM != null) {
            String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
            if (this.isHoveringRoomUuid(mouseX, mouseY, uuid)) {
                Concerto.getCoreBridge().setClientClipboard(uuid);
                Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.room.uuid_copied", false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHoveringRoomUuid(double mouseX, double mouseY, String uuid) {
        int textWidth = this.font.width(uuid);
        int x = this.width / 2 - textWidth / 2;
        return mouseX >= x && mouseX < x + textWidth && mouseY >= 32 && mouseY < 32 + this.font.lineHeight;
    }

    @Override
    public void removed() {
        super.removed();
        if (MusicRoom.clientRoomListListener == this.listListener) {
            MusicRoom.clientRoomListListener = null;
        }
    }
}
