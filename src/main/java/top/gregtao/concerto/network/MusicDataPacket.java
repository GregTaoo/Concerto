package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.UnsafeMusicException;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.util.JsonUtil;

import java.util.List;
import java.util.Objects;

public class MusicDataPacket {

    public static List<String> ALLOWED_SOURCES = List.of(
            Sources.NETEASE_CLOUD.asString(),
            Sources.QQ_MUSIC.asString()
    );

    public final Music music;

    public String from = null, to;

    public boolean isS2C;

    public MinecraftServer server = null;

    public MusicDataPacket(Music music, String target, boolean isS2C) throws UnsafeMusicException {
        if (!ALLOWED_SOURCES.contains(music.getJsonParser().name())) {
            throw new UnsafeMusicException("Not supported");
        }
        this.music = music;
        this.isS2C = isS2C;
        if (isS2C) this.from = target;
        else this.to = target;
    }

    public PacketByteBuf toPacket(String senderName) {
        if (this.isS2C) throw new RuntimeException("Only for C2S packet");
        PacketByteBuf buf = PacketByteBufs.create();
        JsonObject object = MusicJsonParsers.to(this.music);
        if (object != null) {
            JsonObject metaObject = object.getAsJsonObject("meta");
            String src = metaObject.get("src").getAsString();
            metaObject.addProperty("src", src + ", " + senderName);
            buf.writeString(object + "\n" + (this.isS2C ? this.from : this.to));
        }
        return buf;
    }

    public PacketByteBuf toPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        JsonObject object = MusicJsonParsers.to(this.music);
        if (object != null) {
            buf.writeString(object + "\n" + (this.isS2C ? this.from : this.to));
        }
        return buf;
    }

    public static MusicDataPacket fromPacket(PacketByteBuf buf, boolean isS2C) throws UnsafeMusicException {
        try {
            String[] strings = buf.readString(Short.MAX_VALUE).split("\n");
            if (strings.length < 2) {
                return null;
            } else {
                return new MusicDataPacket(
                        Objects.requireNonNull(MusicJsonParsers.from(JsonUtil.from(strings[0]))), strings[1], isS2C);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }
}
