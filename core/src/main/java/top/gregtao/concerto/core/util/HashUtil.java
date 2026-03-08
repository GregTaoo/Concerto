package top.gregtao.concerto.core.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            String md5 = new BigInteger(1, digest.digest()).toString(16);
            if (md5.length() < 32) md5 = "0".repeat(32 - md5.length()) + md5;
            return md5;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such md5 algorithm");
        }
    }

    public static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            String sha1 = new BigInteger(1, digest.digest()).toString(16);
            if (sha1.length() < 40) sha1 = "0".repeat(40 - sha1.length()) + sha1;
            return sha1;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such sha1 algorithm");
        }
    }
}
