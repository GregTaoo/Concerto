package top.gregtao.concerto.core.http.kugou;

import java.util.HashMap;
import java.util.Map;

public class KuGouRequestConfig {

    private final boolean needKey;

    private final boolean noSign;

    private final String baseUrl;

    private final RequestType requestType;

    private final Map<String, String> headers;

    private final Object data;

    private final EncryptType encryptType;

    private final boolean clearDefaultParams;

    public KuGouRequestConfig(boolean needKey, boolean noSign, String baseUrl, RequestType requestType, Map<String, String> headers, Object data, EncryptType encryptType, boolean clearDefaultParams) {
        this.needKey = needKey;
        this.noSign = noSign;
        this.baseUrl = baseUrl;
        this.requestType = requestType;
        this.headers = headers;
        this.data = data;
        this.encryptType = encryptType;
        this.clearDefaultParams = clearDefaultParams;
    }

    public boolean isNeedKey() {
        return needKey;
    }

    public boolean isNoSign() {
        return noSign;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Object getData() {
        return data;
    }

    public EncryptType getEncryptType() {
        return encryptType;
    }

    public boolean isClearDefaultParams() {
        return clearDefaultParams;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean needKey = false;
        private boolean noSign = false;
        private String baseUrl = null;
        private RequestType requestType = RequestType.GET;
        private Map<String, String> headers = new HashMap<>();
        private Object data = null;
        private EncryptType encryptType = EncryptType.ANDROID;
        private boolean clearDefaultParams = false;

        public Builder needKey(boolean needKey) {
            this.needKey = needKey;
            return this;
        }

        public Builder noSign(boolean noSign) {
            this.noSign = noSign;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder requestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder addHeaders(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder encryptType(EncryptType encryptType) {
            this.encryptType = encryptType;
            return this;
        }

        public Builder clearDefaultParams(boolean clearDefaultParams) {
            this.clearDefaultParams = clearDefaultParams;
            return this;
        }

        public KuGouRequestConfig build() {
            return new KuGouRequestConfig(needKey, noSign, baseUrl, requestType, headers, data, encryptType, clearDefaultParams);
        }
    }

    public enum RequestType {
        GET,
        POST
    }

    public enum EncryptType {
        WEB,
        ANDROID,
        REGISTER
    }
}