package top.gregtao.concerto.core.util;

import java.util.Random;

public class RandomUtil {
    private static final Random RANDOM = new Random();

    public static String randomString(int len) {
        return randomString(len, "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }

    public static String randomString(int len, String candidates) {
        char[] keyArray = candidates.toCharArray();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int ceil = RANDOM.nextInt(keyArray.length);
            sb.append(keyArray[ceil]);
        }
        return sb.toString();
    }
}
