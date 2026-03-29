package top.gregtao.concerto.core.music;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.*;
import top.gregtao.concerto.core.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.meta.music.TimelessMusicMetaData;
import top.gregtao.concerto.core.player.streamplayer.enums.AudioType;
import top.gregtao.concerto.core.player.streamplayer.tools.TimeTool;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.HttpUtil;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.core.util.TextUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalFileMusic extends PathFileMusic {
    public static List<String> FORMATS = List.of("mp3", "ogg", "wav", "flac", "aac", "m4a");

    public LocalFileMusic(String rawPath) throws UnsafeMusicException {
        super(new File(TextUtil.trimSurrounding(rawPath, "\"", "\"")).getAbsolutePath());
        String suffix = HttpUtil.getSuffix(this.getRawPath()).substring(1).toLowerCase();
        if (!FORMATS.contains(suffix)) {
            Concerto.getLogger().warn("Unsupported source: {}", suffix);
            throw new UnsafeMusicException("Unsupported source: " + suffix);
        }
    }

    @Override
    public InputStream getMusicSource() {
        try {
            // 读取文件字节
            byte[] fileBytes = Files.readAllBytes(Path.of(this.getRawPath()));

            // 计算需要对齐的字节数
            int alignedLength = (fileBytes.length + 4095) / 4096 * 4096; // 对齐到下一个 4096 字节

            // 创建新的字节数组，对齐到 4096 字节
            byte[] alignedBytes = new byte[alignedLength];
            System.arraycopy(fileBytes, 0, alignedBytes, 0, fileBytes.length);

            return new ByteArrayInputStream(alignedBytes);
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
                Concerto.getLogger().warn("Error occurs while loading file: '{}'", this.getRawPath());
            }
        }
        return Pair.of(lyrics, null);
    }

    @Override
    public void load() {
        String author, title, coverImg;
        try {
            AudioFile file = AudioFileIO.read(new File(this.getRawPath()));
            Tag tag = file.getTagAndConvertOrCreateDefault();
            title = tag.getFirst(FieldKey.TITLE);
            author = FileUtil.getLocalAudioAuthors(file);
            coverImg = FileUtil.getCoverAsObjectURL(file);
        } catch (Exception e) {
            author = title = coverImg = null;
        }
        long duration = TimeTool.durationInMilliseconds(new File(this.getRawPath()).getAbsolutePath(), AudioType.FILE);
        if (duration <= 0) {
            this.setMusicMeta(new TimelessMusicMetaData(
                    author == null || author.isEmpty() ? Concerto.getCoreBridge().getTranslatableText("concerto.unknown") : author,
                    title == null || title.isEmpty() ? this.getRawPath() : title,
                    Sources.LOCAL_FILE.getName()
            ));
        } else {
            this.setMusicMeta(new BasicMusicMetaData(
                    author == null || author.isEmpty() ? Concerto.getCoreBridge().getTranslatableText("concerto.unknown") : author,
                    title == null || title.isEmpty() ? this.getRawPath() : title,
                    Sources.LOCAL_FILE.getName(),
                    TimeTool.durationInMilliseconds(new File(this.getRawPath()).getAbsolutePath(), AudioType.FILE),
                    coverImg == null ? "" : coverImg
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
