package top.gregtao.concerto.http;

import com.google.gson.GsonBuilder;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.util.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HttpRequestBuilder {

    private final HttpApiClient client;
    private final HttpRequest.Builder builder;
    private String url;
    private final Map<String, String> fixedHeaders = new HashMap<>();

    private HttpRequestBuilder(HttpApiClient client) {
        this.builder = HttpRequest.newBuilder();
        this.client = client;
    }

    public static HttpRequestBuilder open(HttpApiClient client) {
        return new HttpRequestBuilder(client);
    }

    public HttpRequestBuilder url(String url) {
        try {
            this.builder.uri(new URI(url));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.url = url;
        return this;
    }

    public HttpRequestBuilder url(String url, String params) {
        return this.url(url + "?" + params);
    }

    public HttpRequestBuilder url(String url, Map<?, ?> params) {
        return this.url(url + "?" + ContentType.toForm(params));
    }

    public HttpRequestBuilder setHeader(String key, String value) {
        this.builder.setHeader(key, value);
        return this;
    }

    public HttpRequestBuilder setHeaders(Map<String, String> map) {
        map.forEach(this::setHeader);
        return this;
    }

    public HttpRequestBuilder addFixedHeader(String key, String value) {
        this.fixedHeaders.put(key, value);
        return this;
    }

    public HttpRequestBuilder addFixedHeaders(Map<String, String> map) {
        this.fixedHeaders.putAll(map);
        return this;
    }

    public HttpRequestBuilder setFixedReferer(String referer) {
        return this.addFixedHeader("Referer", referer);
    }

    public HttpRequestBuilder setContentType(String contentType) {
        return this.setHeader("Content-Type", contentType);
    }

    public HttpResponse<InputStream> openStream() {
        this.builder.GET();
        this.setHeaders(this.fixedHeaders);
        HttpRequest request = this.builder.setHeader("Cookie", this.client.getCookieFile().readAsHeader()).build();
        String logUrl = TextUtil.maskUrl(this.url);
        try {
            HttpResponse<InputStream> response = this.client.getClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            this.client.getLogger().info("STREAM {} GET {}", response.statusCode(), logUrl);
            this.client.writeCookie();
            return response;
        } catch (IOException | InterruptedException e) {
            this.client.getLogger().error("ERROR GET STREAM {} : {}", logUrl, e.getMessage());
            return null;
        }
    }

    public <T> HttpResponse<T> get(HttpResponse.BodyHandler<T> bodyHandler) {
        this.builder.GET();
        this.setHeaders(this.fixedHeaders);
        HttpRequest request = this.builder.setHeader("Cookie", this.client.getCookieFile().readAsHeader()).build();
        String logUrl = TextUtil.maskUrl(this.url);
        try {
            HttpResponse<T> response = this.client.getClient().send(request, bodyHandler);
            if (ClientConfig.INSTANCE.options.printRequestResults && bodyHandler == HttpResponse.BodyHandlers.ofString()) {
                this.client.getLogger().info("{} GET {} : {}", response.statusCode(), logUrl, response.body());
            } else {
                this.client.getLogger().info("{} GET {}", response.statusCode(), logUrl);
            }
            this.client.writeCookie();
            return response;
        } catch (IOException | InterruptedException e) {
            this.client.getLogger().error("ERROR GET {} : {}", logUrl, e.getMessage());
            return null;
        }
    }

    public HttpResponse<String> get() {
        return this.get(HttpResponse.BodyHandlers.ofString());
    }

    public <T> HttpResponse<T> post(HttpResponse.BodyHandler<T> bodyHandler, ContentType contentType, String data) {
        this.setContentType(contentType.name);
        this.builder.POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        this.setHeaders(this.fixedHeaders);
        HttpRequest request = this.builder.setHeader("Cookie", this.client.getCookieFile().readAsHeader()).build();
        String logUrl = TextUtil.maskUrl(this.url);
        try {
            HttpResponse<T> response = this.client.getClient().send(request, bodyHandler);
            if (ClientConfig.INSTANCE.options.printRequestResults && bodyHandler == HttpResponse.BodyHandlers.ofString()) {
                this.client.getLogger().info("{} POST {} - {} : {}", response.statusCode(), logUrl, data, response.body());
            } else {
                this.client.getLogger().info("{} POST {}", response.statusCode(), logUrl);
            }
            this.client.writeCookie();
            return response;
        } catch (IOException | InterruptedException e) {
            this.client.getLogger().error("ERROR POST {} : {}", logUrl, e.getMessage());
            return null;
        }
    }

    public <T> HttpResponse<T> post(HttpResponse.BodyHandler<T> bodyHandler, ContentType contentType, Map<?, ?> data) {
        return this.post(bodyHandler, contentType, contentType.parser.apply(data));
    }

    public <T> HttpResponse<T> post(HttpResponse.BodyHandler<T> bodyHandler) {
        return this.post(bodyHandler, ContentType.FORM, "");
    }

    public HttpResponse<String> post() {
        return this.post(HttpResponse.BodyHandlers.ofString());
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
