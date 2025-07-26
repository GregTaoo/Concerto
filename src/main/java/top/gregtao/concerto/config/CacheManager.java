package top.gregtao.concerto.config;

import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class CacheManager {

    public static String CACHE_ROOT_FOLDER = "Concerto/cache/";
    public static CacheManager IMAGE_CACHE_MANAGER = new CacheManager("images");

    private final File folder;
    private int maxSize = 1000 * 1000 * 100; // 100 MB

    public CacheManager(String name) {
        this.folder = new File(CACHE_ROOT_FOLDER + name);
        if (!this.folder.exists() && !this.folder.mkdirs()) {
            throw new RuntimeException("Cannot mkdir");
        }
    }

    public CacheManager(String name, int maxSize) {
        this(name);
        this.maxSize = maxSize;
    }

    public static void cleanAllCache() {
        Path cacheRoot = Paths.get(CACHE_ROOT_FOLDER);

        if (!Files.exists(cacheRoot) || !Files.isDirectory(cacheRoot)) {
            ConcertoServer.LOGGER.error("Cache folder does not exist or is not a directory: {}", cacheRoot);
            return;
        }

        try (Stream<Path> walk = Files.walk(cacheRoot, 1)) {
            walk.filter(path -> !path.equals(cacheRoot)).forEach(CacheManager::deleteRecursively);
        } catch (IOException e) {
            ConcertoServer.LOGGER.error("Failed to scan cache folder: {}", cacheRoot, e);
        }
    }

    private static void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    ConcertoServer.LOGGER.error("Failed to delete: {}", p, e);
                }
            });
        } catch (IOException e) {
            ConcertoServer.LOGGER.error("Failed to traverse path: {}", path, e);
        }
    }

    public File getChild(String child) {
        return new File(this.folder.getAbsolutePath() + "/" + child);
    }

    public long getTotalSize() {
        long size = 0;
        for (File file : Objects.requireNonNull(this.folder.listFiles())) {
            size += file.isFile() ? file.length() : 0;
        }
        return size;
    }

    public void removeEarliest() {
        File[] files = this.folder.listFiles();
        if (files == null || files.length == 0) return;
        AtomicLong size = new AtomicLong();
        List<Pair<Pair<File, Long>, Long>> list = Arrays.stream(files).filter(File::isFile).map(file -> {
            long len = file.length();
            size.addAndGet(len);
            try {
                BasicFileAttributes attributes = Files.getFileAttributeView(
                        file.toPath(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
                return Pair.of(Pair.of(file, len), attributes.creationTime().toMillis());
            } catch (IOException e) {
                ConcertoClient.LOGGER.warn("Error occurs while trying removing a file", e);
                return Pair.of(Pair.of(file, len), 0L);
            }
        }).sorted(Comparator.comparingLong(Pair::getSecond)).toList();
        long finalSize = size.get();
        int pos = 0;
        while (finalSize > this.maxSize && pos < list.size()) {
            Pair<File, Long> pair = list.get(pos).getFirst();
            if (pair.getFirst().delete()) {
                finalSize -= pair.getSecond();
            } else {
                ConcertoClient.LOGGER.warn("Cannot remove file {}", pair.getFirst().getAbsolutePath());
            }
            ++pos;
        }
    }

    public boolean exists(String filename) {
        File file = this.getChild(filename);
        return file.exists() && !file.isDirectory();
    }

    public void addFile(String filename, InputStream inputStream) throws IOException {
        File file = this.getChild(filename);
        if (file.exists() || (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) || !file.createNewFile()) return;
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(inputStream.readAllBytes());
        }
        inputStream.close();
        if (this.getTotalSize() > this.maxSize) MusicPlayer.run(this::removeEarliest);
    }
}
