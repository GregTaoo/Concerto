package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.room.MusicRoom.ClientState;

public class PlayerPermissions {

    /**
     * Gets the current effective permission level for the client.
     * 3 = Owner / Local (Full permissions)
     * 2 = Operator (Can modify list, control playback)
     * 1 = Member (Can only listen)
     * 0 = None / Guest
     */
    public static int getPermissionLevel() {
        if (MusicRoom.clientGetState() == ClientState.LOCAL) {
            return 3;
        }
        if (MusicRoom.CLIENT_ROOM != null) {
            return MusicRoom.CLIENT_ROOM.permission;
        }
        return 0;
    }

    public static boolean canModifyMusicList() {
        return getPermissionLevel() >= 2;
    }

    public static boolean canReorderMusicList() {
        return getPermissionLevel() >= 2;
    }

    public static boolean canChangeOrderType() {
        return getPermissionLevel() >= 2;
    }

    public static boolean canControlPlayback() {
        return getPermissionLevel() >= 2;
    }

    public static boolean canChangeMusicIndex() {
        return getPermissionLevel() >= 2;
    }
}
