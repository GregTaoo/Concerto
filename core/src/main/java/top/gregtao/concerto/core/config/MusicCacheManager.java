package top.gregtao.concerto.core.config;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.api.MusicSourceNotFoundException;
import top.gregtao.concerto.core.util.HashUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public class MusicCacheManager extends CacheManager {

    public static MusicCacheManager INSTANCE = new MusicCacheManager();

    protected MusicCacheManager() {
        super("musics");
    }

    protected MusicCacheManager(int maxSize) {
        super("musics", maxSize);
    }

    public File getChild(CacheableMusic music) {
        JsonObject json = MusicJsonParsers.to(music.getMusic(), false);
        if (json == null) return null;
        String filename = HashUtil.md5(json.toString()) + "." + music.getSuffix();
        return this.exists(filename) ? super.getChild(filename) : null;
    }

    public void addMusic(CacheableMusic music) throws MusicSourceNotFoundException, IOException, UnsupportedAudioFileException {
        JsonObject json = MusicJsonParsers.to(music.getMusic(), false);
        if (json == null) return;
        this.addFile(HashUtil.md5(json.toString()) + "." + music.getSuffix(), music.getMusic().getMusicSource());
    }
}
