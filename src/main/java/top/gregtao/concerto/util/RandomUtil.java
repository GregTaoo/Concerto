package top.gregtao.concerto.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private static String e() {
        int value = (int) (65536 * (1 + RANDOM.nextDouble()));
        return Integer.toHexString(value).substring(1);
    }

    public static String getGuid() {
        return e() + e() + "-" +
                e() + "-" +
                e() + "-" +
                e() + "-" +
                e() + e() + e();
    }

    public static String calculateMid(String guid) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(guid.getBytes(StandardCharsets.UTF_8));
            BigInteger bigInteger = new BigInteger(1, bytes);
            return bigInteger.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
}
