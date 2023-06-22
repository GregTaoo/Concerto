package top.gregtao.concerto.config;

import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.player.MusicPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

// TODO: Use WatchService to rewrite
public class CacheManager {

    public static String CACHE_ROOT_FOLDER = "Concerto/cache/";

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

    // Those codes suck, I will rewrite
    public void removeEarliest() {
        File[] files = this.folder.listFiles();
        if (files == null) return;
        AtomicLong size = new AtomicLong();
        List<Pair<Pair<File, Long>, Long>> list = Arrays.stream(files).filter(File::isFile).map(file -> {
            long len = file.length();
            size.addAndGet(len);
            try {
                BasicFileAttributes attributes = Files.getFileAttributeView(
                        file.toPath(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
                return Pair.of(Pair.of(file, len), attributes.creationTime().toMillis());
            } catch (IOException e) {
                return Pair.of(Pair.of(file, len), 0L);
            }
        }).sorted(Comparator.comparingLong(Pair::getSecond)).toList();
        long finalSize = size.get();
        int pos = 0;
        while (finalSize > this.maxSize && pos < list.size()) {
            Pair<File, Long> pair = list.get(pos).getFirst();
            if (pair.getFirst().delete()) {
                finalSize -= pair.getSecond();
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
        if (file.exists() || !file.createNewFile()) return;
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(inputStream.readAllBytes());
        }
        inputStream.close();
        if (this.getTotalSize() > this.maxSize) MusicPlayer.run(this::removeEarliest);
    }
}
