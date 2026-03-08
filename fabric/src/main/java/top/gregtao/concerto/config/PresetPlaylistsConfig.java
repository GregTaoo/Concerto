package top.gregtao.concerto.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.util.JsonUtil;
import top.gregtao.concerto.network.room.ServerMusicAgent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PresetPlaylistsConfig {

    public static final PresetPlaylistsConfig PRESET_RADIOS = new PresetPlaylistsConfig("preset_radios");
    public static final PresetPlaylistsConfig LOCAL_PLAYLISTS = new PresetPlaylistsConfig("local_playlists");

    private final File folder;
    private final List<Playlist> radios = new ArrayList<>();

    public PresetPlaylistsConfig(String folder) {
        this.folder = new File("Concerto/" + folder);
    }

    public List<Playlist> getRadios() {
        return this.radios;
    }

    public boolean checkPath() {
        if (!this.folder.exists() || !this.folder.isDirectory()) {
            if (!this.folder.mkdirs()) {
                Concerto.getLogger().error("Cannot mkdir: {}", this.folder);
                return false;
            }
        }
        return this.folder.exists() && this.folder.isDirectory();
    }

    public void read() {
        if (!this.checkPath()) {
            Concerto.getLogger().info("No preset radios found.");
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
                        if (file.getName().toLowerCase().endsWith("music_agent.json")) {
                            ServerMusicAgent.INSTANCE.freeTimePlaylist.addAll(playlist.getList());
                            Concerto.getLogger().info("Free time playlist for server music agent is loaded.");
                        }
                        this.radios.add(playlist);
                        Concerto.getLogger().info("Loaded preset radio: {} in {}",
                                playlist.getMeta().title(), file.getName());
                    }
                } catch (IOException e) {
                    Concerto.getLogger().error("Error reading file: {}", file.getName());
                }
            }
        } else {
            Concerto.getLogger().info("No preset radios found.");
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

    public static boolean saveToLocalPlaylists(Playlist playlist) {
        File file = new File("Concerto/local_playlists/" + System.currentTimeMillis() + ".json");
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
            Concerto.getLogger().error("Cannot create/open file: {}", e.toString());
            return false;
        }
    }

}
