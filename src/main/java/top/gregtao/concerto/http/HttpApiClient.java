package top.gregtao.concerto.http;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.config.CookieFile;
import top.gregtao.concerto.util.HttpUtil;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class HttpApiClient {
    public static Map<Character, Character> ESCAPE_MAP = Map.of(
            '\u00a0', ' ', '\r', '\n'
    );

    public static String escapeChars(String str) {
        for (Map.Entry<Character, Character> entry : ESCAPE_MAP.entrySet()) {
            str = str.replace(entry.getKey(), entry.getValue());
        }
        return str;
    }

    public static HttpRequest.Builder setHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach(builder::setHeader);
        return builder;
    }

    public static HttpRequest.Builder setHeaders(HttpRequest.Builder builder, Map<String, String> headers, Map<String, String> extra) {
        setHeaders(builder, headers);
        extra.forEach(builder::setHeader);
        return builder;
    }

    private HttpClient client;

    private final String name;

    protected final URL baseApi;

    protected final URI baseApiUri;

    private final Map<String, String> headers;

    private CookieManager cookieManager;

    private final CookieFile cookieFile;

    private final Logger logger;

    public HttpApiClient(String name, URL baseApi, Map<String, String> headers, List<String> cookies, CookieManager cookieManager) {
        this.name = name;
        this.logger = LoggerFactory.getLogger(name.toUpperCase() + " HTTP Client");
        this.baseApi = baseApi;
        try {
            this.baseApiUri = baseApi.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        this.headers = headers;
        this.cookieFile = new CookieFile(name);

        // I JUST WAN-NA TELL YOU HOW I'M FEELING
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Referer,User-Agent");

        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.cookieManager = cookieManager;
        this.client = HttpClient.newBuilder().cookieHandler(this.cookieManager).build();

        try {
            this.cookieFile.read(this.cookieManager);
            this.cookieManager.put(this.baseApiUri, Map.of("Set-Cookie", cookies));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public HttpApiClient(String name, URL baseApi, Map<String, String> headers, List<String> cookies) {
        this(name, baseApi, headers, cookies, new CookieManager());
    }

    public String get(String url, Map<String, String> extraHeaders, boolean redirect302) throws Exception {
        boolean crossSite = !url.startsWith("/");
        HttpRequest request = setHeaders(
                HttpRequest.newBuilder()
                        .uri(crossSite ? new URI(url) : HttpUtil.getSonOfURL(this.baseApi, url).toURI())
                        .GET(),
                crossSite ? Map.of() : this.headers, extraHeaders).build();

        HttpResponse<String> response = this.client.send(request,
                HttpResponse.BodyHandlers.ofString());

        String body;
        if (redirect302) {
            body = response.body();
        } else {
            Optional<String> location = response.headers().firstValue("Location");
            body = response.statusCode() == 302 && location.isPresent() ? location.get() : response.body();
        }

        this.logger.info(response.statusCode() + " GET " + url + " : " + body.replaceAll("\n", ""));
        this.cookieFile.write(this.cookieManager);

        return escapeChars(body);
    }

    public String get(String url, Map<String, String> extraHeaders) throws Exception {
        return this.get(url, extraHeaders, true);
    }

    public byte[] getInBytes(String url, Map<String, String> extraHeaders) throws Exception {
        boolean crossSite = !url.startsWith("/");
        HttpRequest request = setHeaders(
                HttpRequest.newBuilder()
                        .uri(crossSite ? new URI(url) : HttpUtil.getSonOfURL(this.baseApi, url).toURI())
                        .GET(),
                crossSite ? Map.of() : this.headers, extraHeaders).build();

        HttpResponse<byte[]> response = this.client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

        this.logger.info(response.statusCode() + " GET " + url + " : BINARY");
        this.cookieFile.write(this.cookieManager);

        return response.body();
    }

    public byte[] getInBytes(String url) throws Exception {
        return this.getInBytes(url, Map.of());
    }

    public String get(String url) throws Exception {
        return this.get(url, Map.of());
    }

    public String get(String url, Map<?, ?> map, Map<String, String> extraHeaders) throws Exception {
        return this.get(url + "?" + ContentType.toForm(map), extraHeaders);
    }

    public String post(String url, String data, ContentType type, boolean redirect302) throws Exception {
        HttpRequest request = setHeaders(
                HttpRequest.newBuilder()
                        .uri(HttpUtil.getSonOfURL(this.baseApi, url).toURI())
                        .setHeader("Content-Type", type.name)
                        .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8)),
                this.headers).build();

        HttpResponse<String> response = this.client.send(request,
                HttpResponse.BodyHandlers.ofString());

        String body;
        if (redirect302) {
            body = response.body();
        } else {
            Optional<String> location = response.headers().firstValue("Location");
            body = response.statusCode() == 302 && location.isPresent() ? location.get() : response.body();
        }

        this.logger.info(response.statusCode() + " POST " + url + " + " + data + " : " + body.replaceAll("\n", ""));
        this.cookieFile.write(this.cookieManager);

        return escapeChars(body);
    }

    public String post(String url, String data, ContentType type) throws Exception {
        return this.post(url, data, type, true);
    }

    public String post(String url, JsonObject object) throws Exception {
        return this.post(url, object.toString(), ContentType.JSON);
    }

    public String post(String url) throws Exception {
        return this.post(url, "", ContentType.FORM);
    }

    public String post(String url, Map<?, ?> map, ContentType type) throws Exception {
        return this.post(url, type.parser.apply(map), type);
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
    }

    public void clearCookie() {
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder().cookieHandler(this.cookieManager).build();
        this.cookieFile.write(this.cookieManager);
    }

    public String getName() {
        return this.name;
    }

    public enum ContentType {
        JSON("application/json", ContentType::toJson),
        FORM("application/x-www-form-urlencoded", ContentType::toForm);

        public final String name;
        public final Function<Map<?, ?>, String> parser;

        ContentType(String name, Function<Map<?, ?>, String> parser) {
            this.name = name;
            this.parser = parser;
        }

        public static String toJson(Map<?, ?> map) {
            return new GsonBuilder().enableComplexMapKeySerialization().create().toJson(map);
        }

        public static String toForm(Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            map.forEach((key, value) -> builder.append(key).append("=").append(value).append("&"));
            return builder.toString();
        }
    }
}
