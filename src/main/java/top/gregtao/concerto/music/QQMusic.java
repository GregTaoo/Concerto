package top.gregtao.concerto.music;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.music.meta.music.BasicMusicMeta;
import top.gregtao.concerto.music.meta.music.MusicMeta;
import top.gregtao.concerto.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.util.FileUtil;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class QQMusic extends Music {

    public String mid, mediaMid;

    public QQMusic(String mid) {
        this.mid = mid;
    }

    public MusicMeta parseMetaData(JsonObject object) {
        JsonObject trackInfo = object.getAsJsonObject("track_info");
        String title = trackInfo.get("name").getAsString();
        List<String> singers = new ArrayList<>();
        trackInfo.getAsJsonArray("singer").forEach(element -> singers.add(element.getAsJsonObject().get("name").getAsString()));
        this.mediaMid = trackInfo.getAsJsonObject("file").get("media_mid").getAsString();
        return new BasicMusicMeta(String.join(", ", singers), title, Sources.QQ_MUSIC.getName().getString(), trackInfo.get("interval").getAsLong() * 1000);
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
    public Lyric getLyric() {
        try {
            Lyric lyric = QQMusicApiClient.C.getLyric(this.mid);
            return lyric.isEmpty() ? null : lyric;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.QQ_MUSIC;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return FileUtil.createBuffered(new URL(this.getRawPath()).openStream());
    }

    public String getRawPath() throws Exception {
        return QQMusicApiClient.U.getMusicLink(this.mid, this.mediaMid);
    }
}
