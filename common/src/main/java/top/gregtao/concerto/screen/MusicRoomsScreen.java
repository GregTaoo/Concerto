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
 * A room hub with one stable visual rhythm: a contextual header, one list, and
 * a footer action area. The layout never mixes two- and four-pixel gaps.
 */
public class MusicRoomsScreen extends ConcertoScreen {
    private static final int BUTTON_H = 20;
    private static final int DISCOVERY_LIST_TOP = 34;
    private static final int ROOM_LIST_TOP = 50;
    private static final int AGENT_LIST_TOP = 39;
    private static final int SECTION_LABEL_COLOR = 0xffa0a0a0;

    private MusicRoom.ClientState builtState;
    private int builtPermission;
    private ConcertoListWidget<MusicRoom.RoomSummary> roomList;
    private ConcertoListWidget<Pair<String, Integer>> memberList;
    private Button joinButton;
    private Button opButton;
    private CycleButton<Boolean> visibleButton;
    private CycleButton<Boolean> joinableButton;
    private Runnable listListener;
    private int lastMembersHash;
    private int listTop;
    private int listBottom;

    public MusicRoomsScreen(Screen parent) {
        super(Component.translatable("concerto.screen.rooms"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.roomList = null;
        this.memberList = null;
        this.joinButton = null;
        this.opButton = null;
        this.visibleButton = null;
        this.joinableButton = null;
        this.builtState = MusicRoom.clientGetState();
        this.builtPermission = MusicRoom.CLIENT_ROOM == null ? 0 : MusicRoom.CLIENT_ROOM.permission;
        if (this.builtState == MusicRoom.ClientState.LOCAL) {
            this.initDiscovery();
        } else {
            this.initRoom();
        }
    }

    private void initDiscovery() {
        int x = this.standardContentX();
        int width = this.standardContentWidth();
        int footerY = this.standardBottomActionY();
        int createY = footerY - BUTTON_H - STANDARD_ACTION_GAP;
        this.listTop = DISCOVERY_LIST_TOP;
        this.listBottom = createY - STANDARD_ACTION_GAP * 2;

        this.roomList = new ConcertoListWidget<>(this.width, this.listBottom - this.listTop, this.listTop, 18) {
            @Override
            public Component getNarration(int index, MusicRoom.RoomSummary room) {
                Component text = Component.translatable("concerto.room.list.entry", room.name(), room.owner(), room.memberCount());
                return room.joinable() ? text : text.copy().append(" ")
                        .append(Component.translatable("concerto.room.list.locked").withStyle(ChatFormatting.GRAY));
            }

            @Override
            public void onDoubleClicked(ConcertoListWidget<MusicRoom.RoomSummary>.Entry entry) {
                MusicRoomsScreen.this.joinRoom(entry.item);
            }
        };
        this.addWidget(this.roomList);
        this.roomList.reset(MusicRoom.clientRoomList, null);

        int createWidth = (width - STANDARD_ACTION_GAP) * 2 / 3;
        EditBox nameBox = new EditBox(this.font, x, createY, createWidth, BUTTON_H,
                Component.translatable("concerto.room.name_hint"));
        nameBox.setHint(Component.translatable("concerto.room.name_hint"));
        nameBox.setMaxLength(MusicRoom.MAX_ROOM_NAME_LENGTH);
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.create"),
                button -> MusicRoomManager.clientCreate(nameBox.getValue()))
                .pos(x + createWidth + STANDARD_ACTION_GAP, createY).size(width - createWidth - STANDARD_ACTION_GAP, BUTTON_H).build());

        int actionWidth = (width - STANDARD_ACTION_GAP * 2) / 3;
        this.joinButton = Button.builder(Component.translatable("concerto.room.list.join"), button -> {
            ConcertoListWidget<MusicRoom.RoomSummary>.Entry entry = this.roomList.getSelected();
            if (entry != null) this.joinRoom(entry.item);
        }).pos(x, footerY).size(actionWidth, BUTTON_H).build();
        this.addRenderableWidget(this.joinButton);
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"),
                button -> MusicRoomManager.clientRequestList())
                .pos(x + actionWidth + STANDARD_ACTION_GAP, footerY).size(actionWidth, BUTTON_H).build());
        int agentX = x + (actionWidth + STANDARD_ACTION_GAP) * 2;
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.agent.join"),
                button -> ServerMusicAgentManager.clientJoin())
                .pos(agentX, footerY).size(x + width - agentX, BUTTON_H).build());

        this.listListener = () -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen == this && this.roomList != null) {
                this.roomList.reset(MusicRoom.clientRoomList, null);
            }
        });
        MusicRoom.clientRoomListListener = this.listListener;
        MusicRoomManager.clientRequestList();
    }

    private void initRoom() {
        boolean agent = this.builtState == MusicRoom.ClientState.MUSIC_AGENT;
        MusicRoom.MusicRoomState room = MusicRoom.clientGetRoomState();
        boolean canEdit = !agent && this.builtPermission >= 2 && room != null;
        int x = this.standardContentX();
        int width = this.standardContentWidth();
        int footerY = this.standardBottomActionY();
        int settingsY = footerY - BUTTON_H - STANDARD_ACTION_GAP;
        int nameY = settingsY - BUTTON_H - STANDARD_ACTION_GAP;
        this.listTop = agent ? AGENT_LIST_TOP : ROOM_LIST_TOP;
        this.listBottom = (canEdit ? nameY : footerY) - STANDARD_ACTION_GAP * 2;

        this.memberList = new ConcertoListWidget<>(this.width, this.listBottom - this.listTop, this.listTop, 18) {
            @Override
            public Component getNarration(int index, Pair<String, Integer> member) {
                return Component.literal(member.getFirst() + "  ")
                        .append(Component.translatable(permissionKey(member.getSecond())).withStyle(ChatFormatting.GRAY));
            }
        };
        this.addWidget(this.memberList);
        this.resetMembers(room);

        if (canEdit) {
            int nameWidth = (width - STANDARD_ACTION_GAP) * 2 / 3;
            EditBox nameBox = new EditBox(this.font, x, nameY, nameWidth, BUTTON_H,
                    Component.translatable("concerto.room.name_hint"));
            nameBox.setHint(Component.translatable("concerto.room.name_hint"));
            nameBox.setMaxLength(MusicRoom.MAX_ROOM_NAME_LENGTH);
            nameBox.setValue(room.roomName);
            this.addRenderableWidget(nameBox);
            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.save"),
                    button -> this.pushRoomInfo(nameBox.getValue()))
                    .pos(x + nameWidth + STANDARD_ACTION_GAP, nameY).size(width - nameWidth - STANDARD_ACTION_GAP, BUTTON_H).build());

            int toggleWidth = (width - STANDARD_ACTION_GAP) / 2;
            this.visibleButton = CycleButton.onOffBuilder(room.visible).create(x, settingsY, toggleWidth, BUTTON_H,
                    Component.translatable("concerto.room.visible"), (button, value) -> this.pushRoomInfo(null));
            this.addRenderableWidget(this.visibleButton);
            this.joinableButton = CycleButton.onOffBuilder(room.joinable).create(x + toggleWidth + STANDARD_ACTION_GAP, settingsY,
                    width - toggleWidth - STANDARD_ACTION_GAP, BUTTON_H, Component.translatable("concerto.room.joinable"),
                    (button, value) -> this.pushRoomInfo(null));
            this.addRenderableWidget(this.joinableButton);
        }

        boolean owner = !agent && this.builtPermission >= 3;
        int quitWidth = owner ? (width - STANDARD_ACTION_GAP) / 2 : width;
        this.addRenderableWidget(Button.builder(Component.translatable(owner ? "concerto.room.dissolve" : "concerto.screen.quit"),
                button -> {
                    if (agent) ServerMusicAgentManager.clientQuit();
                    else MusicRoomManager.clientQuit();
                }).pos(x, footerY).size(quitWidth, BUTTON_H).build());
        if (owner) {
            this.opButton = Button.builder(Component.translatable("concerto.room.op.set"), button -> {
                ConcertoListWidget<Pair<String, Integer>>.Entry entry = this.memberList.getSelected();
                if (entry != null) MusicRoomManager.clientSetOp(entry.item.getFirst());
            }).pos(x + quitWidth + STANDARD_ACTION_GAP, footerY).size(width - quitWidth - STANDARD_ACTION_GAP, BUTTON_H).build();
            this.addRenderableWidget(this.opButton);
        }
    }

    private void joinRoom(MusicRoom.RoomSummary room) {
        if (room == null) return;
        if (!room.joinable()) {
            this.displayAlert(Component.translatable("concerto.room.join.denied"));
            return;
        }
        MusicRoomManager.clientJoin(room.uuid().toString());
    }

    private static String permissionKey(int permission) {
        return switch (permission) {
            case 3 -> "concerto.room.perm.owner";
            case 2 -> "concerto.room.perm.op";
            default -> "concerto.room.perm.member";
        };
    }

    private void pushRoomInfo(String name) {
        MusicRoom.clientSetRoomInfo(name, this.visibleButton == null || this.visibleButton.getValue(),
                this.joinableButton == null || this.joinableButton.getValue());
    }

    private void resetMembers(MusicRoom.MusicRoomState room) {
        if (this.memberList == null) return;
        List<Pair<String, Integer>> members = new ArrayList<>();
        if (room != null) {
            room.members.forEach((name, permission) -> members.add(Pair.of(name, permission)));
            members.sort(Comparator.<Pair<String, Integer>>comparingInt(member -> -member.getSecond())
                    .thenComparing(Pair::getFirst));
            this.lastMembersHash = room.members.hashCode();
        }
        this.memberList.reset(members, null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int permission = MusicRoom.CLIENT_ROOM == null ? 0 : MusicRoom.CLIENT_ROOM.permission;
        if (MusicRoom.clientGetState() != this.builtState || permission != this.builtPermission) {
            this.rebuildWidgets();
            return;
        }
        if (this.builtState == MusicRoom.ClientState.LOCAL) {
            this.renderDiscovery(graphics, mouseX, mouseY, delta);
        } else {
            this.renderRoom(graphics, mouseX, mouseY, delta);
        }
    }

    private void renderDiscovery(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.drawString(this.font, Component.translatable("concerto.room.list.title"), this.standardContentX(), 22,
                SECTION_LABEL_COLOR, false);
        this.roomList.render(graphics, mouseX, mouseY, delta);
        if (this.roomList.children().isEmpty()) {
            this.drawListMessage(graphics, Component.translatable("concerto.room.list.empty"));
        }
        ConcertoListWidget<MusicRoom.RoomSummary>.Entry entry = this.roomList.getSelected();
        this.joinButton.active = entry != null && entry.item.joinable();
    }

    private void renderRoom(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        MusicRoom.MusicRoomState room = MusicRoom.clientGetRoomState();
        if (this.builtState == MusicRoom.ClientState.MUSIC_AGENT) {
            graphics.drawCenteredString(this.font, Component.translatable("concerto.screen.in_music_agent"), this.width / 2, 17,
                    0xffffffff);
            graphics.drawString(this.font, Component.translatable("concerto.room.members.title"), this.standardContentX(), 28,
                    SECTION_LABEL_COLOR, false);
        } else {
            Component name = Component.translatable("concerto.room.current",
                    room == null || room.roomName.isEmpty() ? MusicRoom.CLIENT_ROOM.uuid.toString() : room.roomName);
            graphics.drawCenteredString(this.font, name, this.width / 2, 17, 0xffffffff);
            String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
            boolean hovering = this.isHoveringUuid(mouseX, mouseY, uuid);
            graphics.drawCenteredString(this.font, Component.literal(uuid).withStyle(style -> style
                    .withColor(ChatFormatting.DARK_GRAY).withUnderlined(hovering)), this.width / 2, 28, 0xff888888);
            graphics.drawString(this.font, Component.translatable("concerto.room.members.title"), this.standardContentX(), 39,
                    SECTION_LABEL_COLOR, false);
        }
        if (room != null && room.members.hashCode() != this.lastMembersHash) this.resetMembers(room);
        this.memberList.render(graphics, mouseX, mouseY, delta);
        if (this.memberList.children().isEmpty()) this.drawListMessage(graphics, Component.translatable("concerto.room.members.empty"));
        if (this.opButton != null) {
            ConcertoListWidget<Pair<String, Integer>>.Entry entry = this.memberList.getSelected();
            this.opButton.active = entry != null && entry.item.getSecond() < 3;
            this.opButton.setMessage(Component.translatable(entry != null && entry.item.getSecond() == 2
                    ? "concerto.room.op.unset" : "concerto.room.op.set"));
        }
    }

    private void drawListMessage(GuiGraphics graphics, Component text) {
        graphics.drawCenteredString(this.font, text, this.width / 2,
                (this.listTop + this.listBottom) / 2 - this.font.lineHeight / 2, 0xffaaaaaa);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.builtState == MusicRoom.ClientState.MUSIC_ROOM && MusicRoom.CLIENT_ROOM != null) {
            String uuid = MusicRoom.CLIENT_ROOM.uuid.toString();
            if (this.isHoveringUuid(mouseX, mouseY, uuid)) {
                Concerto.getCoreBridge().setClientClipboard(uuid);
                Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.room.uuid_copied", false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHoveringUuid(double mouseX, double mouseY, String uuid) {
        int textWidth = this.font.width(uuid);
        int x = this.width / 2 - textWidth / 2;
        return mouseX >= x && mouseX < x + textWidth && mouseY >= 28 && mouseY < 28 + this.font.lineHeight;
    }

    @Override
    public void removed() {
        super.removed();
        if (MusicRoom.clientRoomListListener == this.listListener) MusicRoom.clientRoomListListener = null;
    }
}
