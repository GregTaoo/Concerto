package top.gregtao.concerto.core.http;

import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.util.HttpUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.function.Supplier;

public class HttpURLInputStream extends InputStream {

    private URL url;
    private HttpURLConnection connection;
    private InputStream in;
    private final int szBytes;
    private int readBytesTotal;
    private int retryCount = 0;
    private boolean closed = false;

    private final Supplier<String> urlSupplier;
    private final Map<String, String> requestHeaders;


    public HttpURLInputStream(URL url, int startBytePos, Supplier<String> urlSupplier) throws IOException {
        this(url, startBytePos, urlSupplier, Map.of());
    }

    public HttpURLInputStream(URL url, Map<String, String> requestHeaders) throws IOException {
        this(url, 0, null, requestHeaders);
    }

    public HttpURLInputStream(URL url, int startBytePos, Supplier<String> urlSupplier,
                              Map<String, String> requestHeaders) throws IOException {
        this.readBytesTotal = startBytePos;
        this.url = url;
        this.urlSupplier = urlSupplier;
        this.requestHeaders = Map.copyOf(requestHeaders);
        this.connection = this.openNewConnection();
        if (this.connection.getResponseCode() == 200) {
            this.szBytes = this.connection.getContentLength();
            this.in = this.connection.getInputStream();
        } else {
            String message = this.connection.getResponseCode() + " - couldn't access to: " + url;
            Concerto.getLogger().error(message);
            throw new IOException(message);
        }
    }

    public HttpURLInputStream(URL url, Supplier<String> urlSupplier) throws IOException {
        this(url, 0, urlSupplier);
    }

    public HttpURLInputStream(URL url) throws IOException {
        this(url, 0, null);
    }

    public static int getTotalBytes(URL url) {
        try (HttpURLInputStream stream = new HttpURLInputStream(url)) {
            return stream.szBytes;
        } catch (IOException e) {
            Concerto.getLogger().error(e.getMessage());
            return 0;
        }
    }

    public static int getTotalBytes(String url) {
        try {
            return getTotalBytes(URI.create(url).toURL());
        } catch (MalformedURLException e) {
            Concerto.getLogger().error(e.getMessage());
            return 0;
        }
    }

    private HttpURLConnection openNewConnection() throws IOException {
        Concerto.getLogger().info("Opening HTTP stream connection: {}", HttpUtil.maskUrl(this.url.toString()));
        HttpURLConnection conn = (HttpURLConnection) this.url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        this.requestHeaders.forEach(conn::setRequestProperty);
        conn.setRequestMethod("GET");
        return conn;
    }

    private void disconnect() throws IOException {
        this.in.close();
        this.connection.disconnect();
    }

    private void connect() throws IOException {
        this.connection = this.openNewConnection();
        this.connection.setRequestProperty("Range", "bytes=" + this.readBytesTotal + "-" + this.szBytes);
        if (this.connection.getResponseCode() == 206 || this.connection.getResponseCode() == 200) {
            this.in = this.connection.getInputStream();
        } else {
            String message = this.connection.getResponseCode() + " - cannot access to url: " + url;
            Concerto.getLogger().error(message);
            if (this.connection.getResponseCode() == 403 && this.urlSupplier != null) {
                this.url = URI.create(this.urlSupplier.get()).toURL();
                Concerto.getLogger().warn("Trying to request for a new url.");
            }
            throw new IOException(message);
        }
    }

    private void reconnect() throws IOException {
        Concerto.getLogger().warn("Connection Reset: Trying reconnecting to {}", this.url);
        try {
            this.disconnect();
            this.connect();
        } catch (IOException e) {
            Concerto.getLogger().error("Failed to reconnect!");
            if (++this.retryCount > 10) {
                Concerto.getLogger().error("Failed to reconnect for 10 times! Closing...");
                this.close();
            }
        }
    }

    public int read() throws IOException {
        if (this.closed) return -1;
        if (this.readBytesTotal >= this.szBytes) {
            this.close();
            return -1;
        }
        int k = -1, counter = 0;
        while (k == -1 && ++counter <= 3) {
            try {
                if ((k = this.in.read()) == -1) this.reconnect();
                this.retryCount = 0;
            } catch (IOException e) {
                this.reconnect();
            }
        }
        if (k != -1) this.readBytesTotal++;
        return k;
    }

    public int read(byte @NotNull [] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    public int read(byte @NotNull [] b, int off, int len) throws IOException {
        if (this.closed) return -1;
        if (this.readBytesTotal >= this.szBytes) {
            this.close();
            return -1;
        }
        for (int i = off; i < len + off; ++i) {
            int b1 = this.read();
            if (b1 == -1) return i - off;
            b[i] = (byte) (b1 & 0xFF);
        }
        return len;
    }

    public long skip(long n) throws IOException {
        if (this.closed) return -1;
        int k = 0;
        while (--n >= 0 && this.read() != -1) ++k;
        return k;
    }

    public int available() {
        return this.closed ? 0 : this.szBytes - this.readBytesTotal;
    }

    public void close() throws IOException {
        this.closed = true;
        this.disconnect();
    }

}
