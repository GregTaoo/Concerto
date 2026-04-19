package top.gregtao.concerto.core.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TextUtil {

    public static boolean isDigit(String str) {
        for (char ch : str.toCharArray()) {
            if (!Character.isDigit(ch)) return false;
        }
        return true;
    }

    public static String getCurrentTime() {
        return String.valueOf(System.currentTimeMillis());
    }

    public static String trimSurrounding(String s, String r1, String r2) {
        s = s.trim();
        if (s.startsWith(r1) && s.endsWith(r2)) {
            if (s.length() <= r1.length() + r2.length()) return "";
            return s.substring(r1.length(), s.length() - r2.length());
        }
        return s;
    }

    public static String toBase64(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }
}
