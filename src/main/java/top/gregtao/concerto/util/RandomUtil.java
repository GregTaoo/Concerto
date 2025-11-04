package top.gregtao.concerto.util;

import java.util.Random;

public class RandomUtil {
    private static final String KEY_STRING = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final char[] KEY_ARRAY = KEY_STRING.toCharArray();
    private static final Random RANDOM = new Random();

    public static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int ceil = (int) Math.ceil((KEY_ARRAY.length - 1) * RANDOM.nextDouble());
            sb.append(KEY_ARRAY[ceil]);
        }
        return sb.toString();
    }
}
