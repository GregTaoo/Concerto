package top.gregtao.concerto.music;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jflac.sound.spi.FlacAudioFileReader;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.*;
import top.gregtao.concerto.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.meta.music.TimelessMusicMetaData;
import top.gregtao.concerto.player.streamplayer.enums.AudioType;
import top.gregtao.concerto.player.streamplayer.tools.TimeTool;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.HttpUtil;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.TextUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalFileMusic extends PathFileMusic {
    public static List<String> FORMATS = List.of("mp3", "ogg", "wav", "flac", "aac");

    public LocalFileMusic(String rawPath) throws UnsafeMusicException {
        super(new File(TextUtil.trimSurrounding(rawPath, "\"", "\"")).getAbsolutePath());
        String suffix = HttpUtil.getSuffix(this.getRawPath()).substring(1).toLowerCase();
        if (!FORMATS.contains(suffix)) {
            ConcertoClient.LOGGER.warn("Unsupported source: {}", suffix);
            throw new UnsafeMusicException("Unsupported source: " + suffix);
        }
    }

    @Override
    public InputStream getMusicSource() {
        try {
            InputStream stream = FileUtil.createBuffered(new FileInputStream(this.getRawPath()));
            try {
                FlacAudioFileReader reader = new FlacAudioFileReader();
                return FileUtil.createBuffered(reader.getAudioInputStream(stream));
            } catch (UnsupportedAudioFileException e) {
                return stream;
            } catch (IOException e) {
                return new ByteArrayInputStream(stream.readAllBytes());
            }
        } catch (IOException e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() throws IOException {
        Lyrics lyrics = null;
        try {
            lyrics = new DefaultFormatLyrics().load(String.join("\n",
                    Files.readAllLines(Path.of(HttpUtil.getRawPathWithoutSuffix(this.getRawPath()) + ".lrc"))));
        } catch (NoSuchFileException e) {
            try {
                lyrics = new DefaultFormatLyrics().load(FileUtil.getLocalAudioLyrics(
                        AudioFileIO.read(new File(this.getRawPath()))));
            } catch (IOException | CannotReadException | TagException | InvalidAudioFrameException | ReadOnlyFileException e1) {
                ConcertoClient.LOGGER.warn("Error occurs while loading file: '{}'", this.getRawPath());
            }
        }
        return Pair.of(lyrics, null);
    }

    @Override
    public void load() {
        String author, title;
        try {
            AudioFile file = AudioFileIO.read(new File(this.getRawPath()));
            Tag tag = file.getTagAndConvertOrCreateDefault();
            title = tag.getFirst(FieldKey.TITLE);
            author = FileUtil.getLocalAudioAuthors(file);
        } catch (Exception e) {
            author = title = null;
        }
        long duration = TimeTool.durationInMilliseconds(new File(this.getRawPath()).getAbsolutePath(), AudioType.FILE);
        if (duration <= 0) {
            this.setMusicMeta(new TimelessMusicMetaData(
                    author == null || author.isEmpty() ? TextUtil.getTranslatable("concerto.unknown") : author,
                    title == null || title.isEmpty() ? this.getRawPath() : title,
                    Sources.LOCAL_FILE.getName().getString()
            ));
        } else {
            this.setMusicMeta(new BasicMusicMetaData(
                    author == null || author.isEmpty() ? TextUtil.getTranslatable("concerto.unknown") : author,
                    title == null || title.isEmpty() ? this.getRawPath() : title,
                    Sources.LOCAL_FILE.getName().getString(),
                    TimeTool.durationInMilliseconds(new File(this.getRawPath()).getAbsolutePath(), AudioType.FILE)
            ));
        }
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
