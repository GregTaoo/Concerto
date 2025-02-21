package top.gregtao.concerto.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.util.JsonUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PresetRadioConfig {

    public static final PresetRadioConfig INSTANCE = new PresetRadioConfig();

    private final File folder = new File("Concerto/preset_radios");
    private final List<Playlist> radios = new ArrayList<>();

    public List<Playlist> getRadios() {
        return this.radios;
    }

    public boolean checkPath() {
        if (!this.folder.exists() || !this.folder.isDirectory()) {
            if (!this.folder.mkdirs()) {
                ConcertoServer.LOGGER.error("Cannot mkdir: {}", this.folder);
                return false;
            }
        }
        return this.folder.exists() && this.folder.isDirectory();
    }

    public void read() {
        if (!this.checkPath()) {
            ConcertoServer.LOGGER.info("No preset radios found.");
        }
        this.radios.clear();
        File[] files = this.folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null && files.length > 0) {
            for (File file : files) {
                try {
                    String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                    Playlist playlist = MusicJsonParsers.fromPlaylist(content);
                    if (playlist == null) {
                        throw new IOException();
                    } else {
                        this.radios.add(playlist);
                        ConcertoServer.LOGGER.info("Loaded preset radio: {} in {}",
                                playlist.getMeta().title(), file.getName());
                    }
                } catch (IOException e) {
                    ConcertoServer.LOGGER.error("Error reading file: {}", file.getName());
                }
            }
        } else {
            ConcertoServer.LOGGER.info("No preset radios found.");
        }
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (Playlist playlist : this.getRadios()) {
            JsonObject object1 = MusicJsonParsers.toPlaylist(playlist);
            if (object1 != null) array.add(object1);
        }
        object.add("data", array);
        return object;
    }

    public String toString() {
        return this.toJson().toString();
    }

    public static List<Playlist> fromJson(JsonObject object) {
        try {
            ArrayList<Playlist> playlists = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("data")) {
                Playlist playlist = MusicJsonParsers.fromPlaylist(element.getAsJsonObject());
                if (playlist != null) playlists.add(playlist);
            }
            return playlists;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<Playlist> fromJson(String s) {
        return fromJson(JsonUtil.from(s));
    }

    public static boolean saveToTmpFile(Playlist playlist) {
        File file = new File("Concerto/concerto_playlist_export.json");
        try {
            if (file.exists() || file.createNewFile()) {
                FileWriter writer = new FileWriter(file);
                writer.write(MusicJsonParsers.toPlaylist(playlist).toString());
                writer.close();
                return true;
            } else {
                throw new IOException();
            }
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Cannot create/open file: {}", e.toString());
            return false;
        }
    }

}