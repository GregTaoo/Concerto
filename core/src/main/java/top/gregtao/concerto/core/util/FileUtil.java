package top.gregtao.concerto.core.util;

import java.io.BufferedInputStream;
import java.io.InputStream;

public class FileUtil {

    public static BufferedInputStream createBuffered(InputStream inputStream) {
        return new BufferedInputStream(inputStream, 2 << 18); // 256 KB
    }

    public static String getSuffix(String name) {
        int idx = name.lastIndexOf(".");
        return idx > 0 ? name.substring(idx + 1) : name;
    }
}
