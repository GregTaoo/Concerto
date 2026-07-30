package top.gregtao.concerto.core.http.netease;

import com.google.gson.Gson;
import top.gregtao.concerto.core.util.HashUtil;
import top.gregtao.concerto.core.util.RandomUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

final class NeteaseCloudApiCrypto {

    private static final Gson GSON = new Gson();
    private static final String IV = "0102030405060708";
    private static final String WEAPI_PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String WEAPI_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private NeteaseCloudApiCrypto() {
    }

    static String weapiForm(Map<String, ?> data) {
        String secretKey = RandomUtil.randomString(16, BASE62);
        String params = aesCbcBase64(aesCbcBase64(GSON.toJson(data), WEAPI_PRESET_KEY), secretKey);
        String encSecKey = rsaEncryptNoPadding(new StringBuilder(secretKey).reverse().toString());
        return form(Map.of("params", params, "encSecKey", encSecKey));
    }

    static String eapiForm(String path, Map<String, ?> data) {
        String text = GSON.toJson(data);
        String digest = HashUtil.md5("nobody" + path + "use" + text + "md5forencrypt");
        String payload = path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        return form(Map.of("params", aesEcbHex(payload)));
    }

    private static String aesCbcBase64(String text, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
            return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt NetEase weapi request", e);
        }
    }

    private static String aesEcbHex(String text) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            return hex(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt NetEase eapi request", e);
        }
    }

    private static String rsaEncryptNoPadding(String text) {
        try {
            byte[] plain = new byte[128];
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(textBytes, 0, plain, plain.length - textBytes.length, textBytes.length);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, loadWeapiPublicKey());
            return hex(cipher.doFinal(plain));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt NetEase weapi secret", e);
        }
    }

    private static PublicKey loadWeapiPublicKey() throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(WEAPI_PUBLIC_KEY)));
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
