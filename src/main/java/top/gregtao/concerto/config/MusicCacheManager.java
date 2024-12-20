package top.gregtao.concerto.config;

import com.google.gson.JsonObject;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.CacheableMusic;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.MusicSourceNotFoundException;
import top.gregtao.concerto.util.HashUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;

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

    public void addMusic(CacheableMusic music, String oldSuffix) throws UnsupportedAudioFileException, IOException, InterruptedException {
//        JsonObject json = MusicJsonParsers.to(music.getMusic(), false);
//        if (json == null) return;
//        String md5 = HashUtil.md5(json.toString()), filename = md5 + "." + oldSuffix;
//        this.addFile(filename, music.getMusic().getMusicSource());
//        File oldFile = super.getChild(filename), newFile = super.getChild(md5 + "." + music.getSuffix());
//
//        try {
//            ProcessBuilder processBuilder = new ProcessBuilder("ffmpeg", "-y", "-i", "\"" + oldFile.getAbsolutePath() + "\"", "\"" + newFile.getAbsolutePath() + "\"");
//            Process process = processBuilder.start();
//            Thread errorReader = new Thread(() -> {
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        System.out.println(line);
//                    }
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            errorReader.start();
//            int exitCode = process.waitFor();
//            errorReader.join();
//            System.out.println("Exit code: " + exitCode);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        if (!oldFile.delete()) ConcertoClient.LOGGER.error("Error occurs when deleting a cached file");
        throw new UnsupportedAudioFileException();
    }
}
