package top.gregtao.concerto.core.http;

import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.Concerto;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

public class HttpClientInputStream extends InputStream {

    private final String url;
    private final HttpApiClient connection;
    private InputStream in;
    private int szBytes = 0;
    private int readBytesTotal;

    public HttpClientInputStream(String url, HttpApiClient client, int startBytePos) throws IOException {
        this.readBytesTotal = startBytePos;
        this.url = url;
        this.connection = client;
        HttpResponse<InputStream> response = this.connection.open().url(this.url).openStream();
        if (response.statusCode() == 206 || response.statusCode() == 200) {
            response.headers().firstValueAsLong("Content-Length").ifPresent(num -> this.szBytes = (int) num);
            this.in = response.body();
        } else {
            throw new IOException(String.valueOf(response.statusCode()));
        }
    }

    public HttpClientInputStream(String url, HttpApiClient client) throws IOException {
        this(url, client, 0);
    }

    private void disconnect() throws IOException {
        this.in.close();
    }

    private void connect() throws IOException {
        HttpResponse<InputStream> response = this.connection.open().url(this.url)
                .setHeader("Range", "bytes=" + this.readBytesTotal + "-" + this.szBytes).openStream();
        if (response.statusCode() == 206 || response.statusCode() == 200) {
            this.in = response.body();
        } else {
            String message = response.statusCode() + " - cannot access to url: " + url;
            Concerto.getLogger().error(message);
            throw new IOException(message);
        }
    }

    private void reconnect() {
        Concerto.getLogger().warn("Connection Reset: Trying reconnecting to {}", this.url);
        try {
            this.disconnect();
            this.connect();
        } catch (IOException e) {
            Concerto.getLogger().error("Failed to reconnect!");
        }
    }

    public int read() throws IOException {
        if (this.readBytesTotal >= this.szBytes) {
            this.close();
            return -1;
        }
        int k = -1, counter = 0;
        while (k == -1 && ++counter <= 3) {
            try {
                if ((k = this.in.read()) == -1) this.reconnect();
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
        if (this.readBytesTotal >= this.szBytes) {
            this.close();
            return -1;
        }
        for (int i = off; i < b.length && i < len + off; ++i) {
            int b1 = this.read();
            if (b1 == -1) return i - off + 1;
            b[i] = (byte) (b1 & 0xFF);
        }
        return len;
    }

    public long skip(long n) throws IOException {
        int k = 0;
        while (--n >= 0 && this.read() != -1) ++k;
        return k;
    }

    public int available() {
        return this.szBytes - this.readBytesTotal;
    }

    public void close() throws IOException {
        this.disconnect();
    }

    public boolean markSupported() {
        return false;
    }
}
