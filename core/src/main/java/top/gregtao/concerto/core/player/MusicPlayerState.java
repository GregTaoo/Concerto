package top.gregtao.concerto.core.player;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.api.MusicSerializer;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.network.ClientRemoteRecord;
import top.gregtao.concerto.core.network.LocalRecord;
import top.gregtao.concerto.core.network.ServerRemoteRecord;

import java.util.ArrayList;
import java.util.function.Consumer;

public class MusicPlayerState implements Copyable<MusicPlayerState> {

    public ArrayList<Music> musicList = new ArrayList<>();
    public int currentIndex = -1;
    public OrderType orderType = OrderType.NORMAL;
    public boolean paused = false;

    public MusicPlayerState(ArrayList<Music> musicList, int currentIndex, OrderType orderType,  boolean paused) {
        this.musicList = musicList;
        this.currentIndex = currentIndex;
        this.orderType = orderType;
        this.paused = paused;
    }

    public MusicPlayerState() {}

    public static ClientRemoteRecord<MusicPlayerState> createClientRecord(MusicPlayerState init, Consumer<JsonObject> packageSender) {
        return new ClientRemoteRecord<>(init, (gson) -> gson.registerTypeAdapter(Music.class, new MusicSerializer())) {
            @Override
            protected void sendPackage(JsonObject patch) {
                packageSender.accept(patch);
            }
        };
    }

    public static ServerRemoteRecord createServerRecord(MusicPlayerState init, Consumer<JsonObject> broadcaster) {
        return new ServerRemoteRecord() {
            @Override
            protected void broadcastPatch(JsonObject patch) {
                broadcaster.accept(patch);
            }
        };
    }

    public static LocalRecord<MusicPlayerState> createLocalRecord(MusicPlayerState init) {
        return new LocalRecord<>(init);
    }

    public MusicPlayerState copy() {
        return new MusicPlayerState(new ArrayList<>(this.musicList), this.currentIndex, this.orderType, this.paused);
    }
}
