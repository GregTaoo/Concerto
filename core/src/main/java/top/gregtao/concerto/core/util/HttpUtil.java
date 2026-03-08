package top.gregtao.concerto.core.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class HttpUtil {

    public static URL createCorrectURL(String url) {
        if (url.charAt(url.length() - 1) == '/') {
            url = url.substring(0, url.length() - 1);
        }
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static URL getSonOfURL(URL father, String son) throws MalformedURLException {
        return new URL(father.toString() + (son.charAt(0) != '/' ? "/" + son : son));
    }

    public static String get302RedirectedUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        return conn.getHeaderField("Location");
    }

    public static String getRawPathWithoutSuffix(String rawPath) {
        int index = rawPath.lastIndexOf('.');
        if (index == -1) return rawPath;
        return rawPath.substring(0, index);
    }

    public static String getSuffix(String rawPath) {
        int index = rawPath.lastIndexOf('.');
        if (index == -1) return rawPath;
        return rawPath.substring(index);
    }

    private static final Pattern SENSITIVE_PARAM = Pattern.compile("(?i)(token|userid)=([^&]+)");

    /**
     * 过滤 URL 中的敏感参数
     */
    public static String maskUrl(String url) {
        if (url == null) return null;
        return SENSITIVE_PARAM.matcher(url).replaceAll("$1=***");
    }
}
