package top.gregtao.concerto.util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    public static String getLocalAudioAuthors(AudioFile file) {
        try {
            Tag tag = file.getTag();
            List<String> list = new ArrayList<>();
            list.add(getTagValueOrElse(tag, FieldKey.ARTISTS, ""));
            list.add(getTagValueOrElse(tag, FieldKey.ARTIST, ""));
            list.add(getTagValueOrElse(tag, FieldKey.ORIGINAL_ARTIST, ""));
            list.add(getTagValueOrElse(tag, FieldKey.ALBUM_ARTISTS, ""));
            list.add(getTagValueOrElse(tag, FieldKey.ALBUM_ARTIST, ""));
            return String.join(", ", list.stream().filter(s -> !s.isEmpty()).toList());
        } catch (Exception e) {
            return "";
        }
    }

    public static String getTagValueOrElse(Tag tag, FieldKey key, String orElse) {
        try {
            String value = tag.getFirst(key);
            return value == null || value.isEmpty() ? orElse : value;
        } catch (KeyNotFoundException e) {
            return orElse;
        }
    }

    public static BufferedInputStream createBuffered(InputStream inputStream) {
        return new BufferedInputStream(inputStream, 16384);
    }

    public static String getSuffix(String name) {
        String[] strings = name.split("\\.");
        if (strings.length == 1) return "";
        else return strings[strings.length - 1];
    }
}
