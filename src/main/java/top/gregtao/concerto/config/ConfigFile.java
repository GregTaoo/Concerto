package top.gregtao.concerto.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ConfigFile {

    private final File file;

    public ConfigFile(File file) {
        this.file = file.getAbsoluteFile();
    }

    public ConfigFile(String path) {
        this(new File(path));
    }

    public String read() {
        try {
            if ((this.file.getParentFile().exists() || this.file.getParentFile().mkdirs()) &&
                    (this.file.exists() || this.file.createNewFile())) {
                return Files.readString(this.file.toPath(), StandardCharsets.UTF_8);
            } else {
                throw new RuntimeException("Cannot create new file");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean write(String str) {
        try {
            if ((this.file.getParentFile().exists() || this.file.getParentFile().mkdirs()) &&
                    (this.file.exists() || this.file.createNewFile())) {
                Files.writeString(this.file.toPath(), str, StandardCharsets.UTF_8);
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }
}
