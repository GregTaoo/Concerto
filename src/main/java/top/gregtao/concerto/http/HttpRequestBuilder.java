package top.gregtao.concerto.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;

public class HttpRequestBuilder {

    private final HttpClient client;
    private final HttpRequest.Builder builder;
    private boolean redirect302 = false;

    private HttpRequestBuilder(HttpClient client) {
        this.builder = HttpRequest.newBuilder();
        this.client = client;
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Referer,User-Agent");
    }

    public static HttpRequestBuilder open(HttpClient client) {
        return new HttpRequestBuilder(client);
    }

    public HttpRequestBuilder url(String url) throws URISyntaxException {
        this.builder.uri(new URI(url));
        return this;
    }

    public HttpRequestBuilder redirect302() {
        this.redirect302 = true;
        return this;
    }

    public HttpRequestBuilder setHeader(String key, String value) {
        this.builder.setHeader(key, value);
        return this;
    }

    public HttpRequestBuilder setHeaders(Map<String, String> map) {
        map.forEach(this.builder::setHeader);
        return this;
    }


}
