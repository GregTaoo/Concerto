package top.gregtao.concerto.util;

import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpURLInputStream extends InputStream {

    private final URL url;
    private HttpURLConnection connection;
    private InputStream in;
    private final int szBytes;
    private int readBytesTotal = 0;

    public HttpURLInputStream(URL url) throws IOException {
        this.url = url;
        this.connection = this.openNewConnection();
        if (this.connection.getResponseCode() == 200) {
            this.szBytes = this.connection.getContentLength();
            this.in = this.connection.getInputStream();
        } else {
            String message = this.connection.getResponseCode() + " - cannot access to url: " + url;
            ConcertoClient.LOGGER.error(message);
            throw new IOException(message);
        }
    }

    private HttpURLConnection openNewConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) this.url.openConnection();
        conn.setConnectTimeout(1000);
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
            ConcertoClient.LOGGER.error(message);
            throw new IOException(message);
        }
    }

    private void reconnect() {
        ConcertoClient.LOGGER.warn("Connection Reset: Trying reconnecting to " + this.url);
        try {
            this.disconnect();
            this.connect();
        } catch (IOException e1) {
            ConcertoClient.LOGGER.error("Failed to reconnect!");
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
