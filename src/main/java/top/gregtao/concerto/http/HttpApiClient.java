package top.gregtao.concerto.http;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.config.CookieFile;
import top.gregtao.concerto.util.JsonUtil;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class HttpApiClient {

    private HttpClient client;
    private CookieManager cookieManager;
    private final CookieFile cookieFile;
    private final String name;
    private final Logger logger;
    private final Map<String, String> defaultHeaders;

    public HttpApiClient(String name, Map<String, String> defaultHeaders, Map<String, List<String>> initCookies) {
        this.name = name;
        this.logger = LoggerFactory.getLogger(name.toUpperCase() + " HTTP Client");
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Referer,User-Agent");
        this.cookieFile = new CookieFile(name);
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder().cookieHandler(this.cookieManager).build();
        this.defaultHeaders = defaultHeaders;
        this.cookieFile.read(this.cookieManager);
        initCookies.forEach((url, list) -> {
            try {
                this.cookieManager.put(new URI(url), Map.of("Set-Cookie", list));
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public HttpRequestBuilder open() {
        HttpRequestBuilder builder = HttpRequestBuilder.open(this);
        builder.addFixedHeaders(this.defaultHeaders);
        return builder;
    }

    public static JsonObject parseJson(HttpResponse<String> response) {
        return response.statusCode() == 200 ? JsonUtil.from(escapeChars(response.body())) : null;
    }

    public HttpClient getClient() {
        return this.client;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public String getName() {
        return this.name;
    }

    public void updateCookie() {
        this.cookieFile.write(this.cookieManager);
    }

    public void clearCookie() {
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder().cookieHandler(this.cookieManager).build();
        this.cookieFile.write(this.cookieManager);
    }

    public String getCookie(String url, String key) throws IOException, URISyntaxException {
        List<String> cookies = this.cookieManager.get(new URI(url), Map.of()).get("Cookie");
        for (String s : cookies) {
            int index = s.indexOf('=');
            if (index > 0 && s.substring(0, index).equals(key)) {
                return s.substring(index + 1);
            }
        }
        return "";
    }

    public void setCookie(String url, String key, String value) throws IOException, URISyntaxException {
        this.cookieManager.put(new URI(url), Map.of("Set-Cookie", List.of(key + "=" + value)));
        this.updateCookie();
    }

    public static Map<Character, Character> ESCAPE_MAP = Map.of(
            '\u00a0', ' ', '\r', '\n'
    ); // escape illegal spaces

    public static String escapeChars(String string) {
        for (Map.Entry<Character, Character> entry : ESCAPE_MAP.entrySet()) {
            string = string.replace(entry.getKey(), entry.getValue());
        }
        return string;
    }

}
