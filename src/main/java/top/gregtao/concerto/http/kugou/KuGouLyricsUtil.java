package top.gregtao.concerto.http.kugou;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class KuGouLyricsUtil {
    public static Pair<String, String> krcToLrc(String krc) {
        String[] lines = krc.split("\n|\r|\r\n");
        List<Pair<String, String>> pairList = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                String[] parts = line.split("]");
                if (parts.length >= 1) {
                    String timePart = parts[0].substring(1);
                    if (parts.length > 1) {
                        String lyricPart = parts[parts.length - 1].trim();
                        if (!lyricPart.isEmpty()) {
                            pairList.add(Pair.of(timePart, lyricPart));
                        }
                    } else {
                        pairList.add(Pair.of(timePart, ""));
                    }
                }
            }
        }

        List<String> transLyricStr = new ArrayList<>();
        List<String> mainLyric = new ArrayList<>();
        List<String> transLyric = new ArrayList<>();
        int i = 0;
        for (Pair<String, String> pair : pairList) {
            String first = pair.getFirst();
            if (first.startsWith("language")) {
                String[] strings = first.split(":");
                if (strings.length == 2) {
                    transLyricStr = getTransLyric(strings[1]);
                }
            } else {
                String second = pair.getSecond();
                if (second != null && first.matches("\\d+,.+")){
                    String[] strings = first.split(",");
                    String time = strings[0];
                    String formattedTime = formatTime(Integer.parseInt(time));
                    String lyric = second.replaceAll("<\\d+,[^>]+>", "");
                    mainLyric.add(formattedTime + lyric);

                    if (transLyricStr.size() > i) {
                        transLyric.add(formattedTime + transLyricStr.get(i));
                    }
                    i++;
                } else {
                    String string = "[" + first + second + "]";
                    mainLyric.add(string);
                    transLyric.add(string);
                }
            }
        }

        return Pair.of(String.join("\n", mainLyric), String.join("\n", transLyric));
    }

    public static List<String> getTransLyric(String language) {
        String json = TextUtil.fromBase64(language);
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);

        return Optional.ofNullable(jsonObject)
                .map(obj -> obj.getAsJsonArray("content"))
                .flatMap(arr -> arr.asList().stream()
                        .filter(el -> Optional.ofNullable(el.getAsJsonObject())
                                .map(object -> object.get("type"))
                                .map(type -> type.getAsInt() == 1)
                                .orElse(false)
                        )
                        .findFirst()
                )
                .map(JsonElement::getAsJsonObject)
                .map(lyric -> lyric.getAsJsonArray("lyricContent"))
                .map(arr -> arr.asList().stream()
                        .map(JsonElement::getAsJsonArray)
                        .map(timeAndLyric -> timeAndLyric.isEmpty() ? "" : timeAndLyric.get(0).getAsString())
                        .collect(Collectors.toList())
                )
                .orElse(new ArrayList<>());
    }

    public static String formatTime(int ms) {
        int minutes = ms / 60000;
        int seconds = (ms % 60000) / 1000;
        int hundredths = (ms % 1000) / 10;
        return String.format("[%02d:%02d.%02d]", minutes, seconds, hundredths);
    }
}
