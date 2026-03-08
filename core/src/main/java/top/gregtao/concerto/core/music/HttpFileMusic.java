package top.gregtao.concerto.core.music;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.api.MusicSourceNotFoundException;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpURLInputStream;
import top.gregtao.concerto.core.music.meta.music.TimelessMusicMetaData;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.HttpUtil;

import java.io.InputStream;
import java.net.URI;

public class HttpFileMusic extends PathFileMusic implements CacheableMusic {

    public HttpFileMusic(String rawPath) {
        super(rawPath);
    }

    @Override
    public InputStream getMusicSource() throws MusicSourceNotFoundException {
        try {
            return FileUtil.createBuffered(new HttpURLInputStream(URI.create(this.getRawPath()).toURL()));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public void load() {
        this.setMusicMeta(new TimelessMusicMetaData(
                Concerto.getMinecraft().getTranslatableText("concerto.unknown"), this.getRawPath(),
                Sources.INTERNET.getName()
        ));
        super.load();
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.HTTP_FILE;
    }

    @Override
    public String getSuffix() {
        String suffix = HttpUtil.getSuffix(URI.create(this.getRawPath()).getPath());
        return suffix.contains("/") ? "mp3" : suffix;
    }

    @Override
    public Music getMusic() {
        return this;
    }
}
