package top.gregtao.concerto.experimental.music;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.experimental.http.qq.QQMusicApiClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicSource;
import top.gregtao.concerto.music.MusicSourceNotFoundException;
import top.gregtao.concerto.music.lyric.Lyrics;
import top.gregtao.concerto.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.music.meta.music.UnknownMusicMeta;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class QQMusic extends Music {

    public String mid, mediaMid;

    public QQMusic(String mid) {
        this.mid = mid;
    }

    public MusicMetaData parseMetaData(JsonObject object) {
        JsonObject trackInfo = object.getAsJsonObject("track_info");
        String title = trackInfo.get("name").getAsString();
        List<String> singers = new ArrayList<>();
        trackInfo.getAsJsonArray("singer").forEach(element -> singers.add(element.getAsJsonObject().get("name").getAsString()));
        this.mediaMid = trackInfo.getAsJsonObject("file").get("media_mid").getAsString();
        return new BasicMusicMetaData(String.join(", ", singers), title, Sources.QQ_MUSIC.getName().getString(), trackInfo.get("interval").getAsLong() * 1000);
    }

    @Override
    public void load() {
        try {
            JsonObject object = QQMusicApiClient.U.getMusicDetail(this.mid)
                    .getAsJsonObject("songinfo").getAsJsonObject("data");
            this.setMusicMeta(this.parseMetaData(object));
        } catch (Exception e) {
            this.setMusicMeta(new UnknownMusicMeta(Sources.QQ_MUSIC.getName().getString()));
        }
        super.load();
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyric() {
        try {
            Lyrics lyrics = QQMusicApiClient.C.getLyric(this.mid);
            return Pair.of(lyrics.isEmpty() ? null : lyrics, null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.QQ_MUSIC;
    }

    @Override
    public MusicSource getMusicSource() throws MusicSourceNotFoundException {
        try {
            return MusicSource.of(new URL(this.getRawPath()));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    public String getRawPath() throws Exception {
        return QQMusicApiClient.U.getMusicLink(this.mid, this.mediaMid);
    }
}
