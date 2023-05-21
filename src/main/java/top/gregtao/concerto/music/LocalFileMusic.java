package top.gregtao.concerto.music;

import com.goxr3plus.streamplayer.enums.AudioType;
import com.goxr3plus.streamplayer.tools.TimeTool;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import top.gregtao.concerto.api.*;
import top.gregtao.concerto.music.lyric.BrokenLyricException;
import top.gregtao.concerto.music.lyric.LRCFormatLyric;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.music.meta.music.BasicMusicMeta;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.HttpUtil;
import top.gregtao.concerto.util.TextUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalFileMusic extends PathFileMusic {
    public static List<String> FORMATS = List.of("mp3", "ogg", "wav", "flac", "aac");

    public LocalFileMusic(String rawPath) {
        super(new File(rawPath).getAbsolutePath());
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return FileUtil.createBuffered(new FileInputStream(this.getRawPath()));
    }

    @Override
    public Lyric getLyric() throws IOException, BrokenLyricException {
        return new LRCFormatLyric().load(String.join("\n",
                Files.readAllLines(Path.of(HttpUtil.getRawPathWithoutSuffix(this.getRawPath()) + ".lrc"))));
    }

    @Override
    public void load() {
        String author, title;
        try {
            AudioFile file = AudioFileIO.read(new File(this.getRawPath()));
            title = file.getTag().getFirst(FieldKey.TITLE);
            author = FileUtil.getLocalAudioAuthors(file);
        } catch (Exception e) {
            author = title = null;
        }
        this.setMusicMeta(new BasicMusicMeta(
                author == null || author.isEmpty() ? TextUtil.getTranslatable("concerto.unknown") : author,
                title == null || title.isEmpty() ? this.getRawPath() : title,
                Sources.LOCAL_FILE.getName().getString(),
                TimeTool.durationInMilliseconds(new File(this.getRawPath()).getAbsolutePath(), AudioType.FILE)
        ));
        super.load();
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.LOCAL_FILE;
    }

    public static ArrayList<Music> getMusicsInFolder(File file) {
        ArrayList<Music> list = new ArrayList<>();
        if (!file.isDirectory()) return list;
        File[] files = file.listFiles((dir, name) -> FORMATS.contains(FileUtil.getSuffix(name).toLowerCase()));
        if (files == null) return list;
        for (File file1 : files) {
            list.add(new LocalFileMusic(file1.getAbsolutePath()));
        }
        return list;
    }
}
