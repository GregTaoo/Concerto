package top.gregtao.concerto.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.meta.music.TimelessMusicMeta;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.TextUtil;

import java.io.InputStream;
import java.net.URL;

public class HttpFileMusic extends PathFileMusic {

    public HttpFileMusic(String rawPath) {
        super(rawPath);
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return FileUtil.createBuffered(new URL(this.getRawPath()).openStream());
    }

    @Override
    public void load() {
        this.setMusicMeta(new TimelessMusicMeta(
                TextUtil.getTranslatable("ah.unknown"), this.getRawPath(),
                Sources.INTERNET.getName().getString()
        ));
        super.load();
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.HTTP_FILE;
    }
}
