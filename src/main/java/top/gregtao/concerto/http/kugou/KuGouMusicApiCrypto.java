package top.gregtao.concerto.http.kugou;

import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.util.HashUtil;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.RandomUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class KuGouMusicApiCrypto {

    public static final String RSA_PUBKEY = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB\n-----END PUBLIC KEY-----";

    public static final String LITE_RSA_PUBKEY = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB\n-----END PUBLIC KEY-----";

    private static final int[] enKey = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105};

    public static String signAndroidParams(Map<String, String> params, String data) {
        String paramsString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry ->
                        entry.getKey() + "=" + entry.getValue()
                )
                .reduce((a, b) -> a + b)
                .orElse("");
        String str = ClientConfig.INSTANCE.options.kuGouMusicLite ? "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA" : "OIlwieks28dk2k092lksi2UIkp";
        return HashUtil.md5(str + paramsString + data + str).toLowerCase();
    }

    public static String signWebParams(Map<String, String> params) {
        String str = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        String paramsString = params.entrySet().stream()
                .map(entry ->
                        entry.getKey() + "=" + entry.getValue()
                )
                .sorted()
                .reduce((a, b) -> a + b)
                .orElse("");

        return HashUtil.md5(str + paramsString + str);
    }

    public static String signRegisterParams(Map<String, String> params) {
        String paramsString = params.values().stream()
                .sorted()
                .collect(Collectors.joining());
        return HashUtil.md5("1014" + paramsString + "1014");
    }

    public static String signKey(String hash, String mid, String userid, String appid) {
        String str = ClientConfig.INSTANCE.options.kuGouMusicLite ? "185672dd44712f60bb1736df5a377e82" : "57ae12eb6890223e355ccfcb74edf70d";
        return HashUtil.md5(hash + str + appid + mid + userid).toLowerCase();
    }

    public static String signParamsKey(String data) {
        boolean isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;
        String str = isLite ? "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA" : "OIlwieks28dk2k092lksi2UIkp";
        String appid = isLite ? KuGouMusicApiClient.LITE_APPID : KuGouMusicApiClient.APPID;
        String clientVer = isLite ? KuGouMusicApiClient.LITE_CLIENT_VER : KuGouMusicApiClient.CLIENT_VER;

        return HashUtil.md5(appid + str + clientVer + data).toLowerCase();
    }

    public static String cryptoRSAEncrypt(String data) {
        boolean isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;
        String pubKey = isLite ? LITE_RSA_PUBKEY : RSA_PUBKEY;

        try {
            byte[] buffer = data.getBytes(StandardCharsets.UTF_8);
            byte[] padded = new byte[128]; // 1024bit = 128字节
            System.arraycopy(buffer, 0, padded, 0, buffer.length);

            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, loadPublicKey(pubKey));
            byte[] encrypted = cipher.doFinal(padded);

            return bytesToHex(encrypted).toUpperCase();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static PublicKey loadPublicKey(String base64Key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        base64Key = base64Key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    public static Pair<String, String> cryptoAesEncrypt(String data, String keyOpt, String ivOpt) {
        byte[] buffer = data.getBytes(StandardCharsets.UTF_8);

        String key;
        String iv;
        String tempKey = "";

        if (keyOpt != null && ivOpt != null) {
            key = keyOpt;
            iv = ivOpt;
        } else {
            tempKey = (keyOpt != null) ? keyOpt : RandomUtil.randomString(16).toLowerCase();
            key = HashUtil.md5(tempKey).substring(0, 32);
            iv = key.substring(key.length() - 16);
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(buffer);

            String hexResult = bytesToHex(encrypted);

            if (keyOpt != null && ivOpt != null) {
                return new Pair<>(hexResult, null);
            } else {
                return new Pair<>(hexResult, tempKey);
            }
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String cryptoAesDecrypt(String hexCipherText, String key, String iv) {
        if (iv == null) {
            key = HashUtil.md5(key).substring(0, 32);
        }
        iv = (iv != null) ? iv : key.substring(key.length() - 16);

        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] cipherBytes = hexToBytes(hexCipherText);
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hexToBytes(String hexCipherText) {
        int len = hexCipherText.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexCipherText.charAt(i), 16) << 4)
                    + Character.digit(hexCipherText.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    public static String decodeLyrics(String rawLyrics) {
        try {
            byte[] bytes = Base64.getDecoder().decode(rawLyrics);

            if (bytes.length <= 4) return "";
            byte[] krcBytes = new byte[bytes.length - 4];
            System.arraycopy(bytes, 4, krcBytes, 0, krcBytes.length);

            for (int i = 0; i < krcBytes.length; i++) {
                krcBytes[i] = (byte) (krcBytes[i] ^ enKey[i % enKey.length]);
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(krcBytes);
            InflaterInputStream inflater = new InflaterInputStream(bais, new Inflater());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] buf = new byte[1024];
            int len;
            while ((len = inflater.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }

            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
