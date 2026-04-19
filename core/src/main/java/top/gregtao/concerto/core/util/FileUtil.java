package top.gregtao.concerto.core.util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class FileUtil {

    public static String getLocalAudioAuthors(AudioFile file) {
        try {
            Tag tag = file.getTagAndConvertOrCreateDefault();
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

    public static String getLocalAudioLyrics(AudioFile file) {
        try {
            Tag tag = file.getTagAndConvertOrCreateDefault();
            return getTagValueOrElse(tag, FieldKey.LYRICS, "");
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

    public static String getCoverAsObjectURL(AudioFile file) {
        Tag tag = file.getTagAndConvertOrCreateDefault();

        try {
            if (tag != null && tag.getFirstArtwork() != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork.isLinked()) {
                    return artwork.getImageUrl();
                } else {
                    byte[] imageData = artwork.getBinaryData();
                    String mimeType = artwork.getMimeType();
                    String base64 = Base64.getEncoder().encodeToString(imageData);
                    return "data:" + mimeType + ";base64," + base64;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public static BufferedInputStream createBuffered(InputStream inputStream) {
        return new BufferedInputStream(inputStream, 2 << 18); // 256 KB
    }

    public static String getSuffix(String name) {
        int idx = name.lastIndexOf(".");
        return idx > 0 ? name.substring(idx + 1) : name;
    }
}
