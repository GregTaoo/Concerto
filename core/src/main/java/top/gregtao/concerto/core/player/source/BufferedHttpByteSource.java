package top.gregtao.concerto.core.player.source;

import top.gregtao.concerto.core.Concerto;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Progressively downloads an HTTP media URL into a temporary file and exposes it
 * as a random-access {@link AudioByteSource}.
 *
 * A single background thread downloads sequentially from byte 0; readers wait on
 * a condition until the region they need has been written. Expired CDN URLs are
 * re-resolved through {@code urlRefresher} and the download resumes with an HTTP
 * Range request.
 */
public class BufferedHttpByteSource implements AudioByteSource {

    private static final int DOWNLOAD_CHUNK = 64 * 1024;
    private static final int MAX_RETRIES = 10;
    private static final Path TEMP_DIR = Path.of("Concerto", "temp");

    private final Supplier<String> urlRefresher;
    private final Path tempFile;
    private final FileChannel channel;
    private final Thread downloadThread;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition progress = lock.newCondition();

    private URL url;
    private long downloadedTo = 0;
    private long totalLength = -1;
    private boolean complete = false;
    private boolean closed = false;
    private IOException failure = null;

    public BufferedHttpByteSource(String url, Supplier<String> urlRefresher) throws IOException {
        this.url = URI.create(url).toURL();
        this.urlRefresher = urlRefresher;
        Files.createDirectories(TEMP_DIR);
        this.tempFile = Files.createTempFile(TEMP_DIR, "stream-", ".tmp");
        this.channel = FileChannel.open(this.tempFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        this.downloadThread = new Thread(this::runDownload, "Concerto-Download");
        this.downloadThread.setDaemon(true);
        this.downloadThread.start();
    }

    private void runDownload() {
        int retries = 0;
        try {
            while (true) {
                long resumeFrom;
                this.lock.lock();
                try {
                    if (this.closed || this.complete) return;
                    resumeFrom = this.downloadedTo;
                } finally {
                    this.lock.unlock();
                }
                HttpURLConnection connection = null;
                try {
                    connection = this.openConnection(resumeFrom);
                    int code = connection.getResponseCode();
                    if (code == HttpURLConnection.HTTP_FORBIDDEN && this.urlRefresher != null) {
                        String fresh = this.urlRefresher.get();
                        if (fresh != null) {
                            this.url = URI.create(fresh).toURL();
                            Concerto.getLogger().warn("Media URL expired, re-resolved a new one");
                        }
                        throw new IOException("HTTP 403 for " + this.url);
                    }
                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                        throw new IOException("HTTP " + code + " for " + this.url);
                    }
                    this.updateTotalLength(connection, code, resumeFrom);
                    // A 200 answer to a ranged request means the server ignored Range:
                    // discard everything before the resume point.
                    long discard = (code == HttpURLConnection.HTTP_OK) ? resumeFrom : 0;
                    try (var in = connection.getInputStream()) {
                        skipFully(in, discard);
                        byte[] buffer = new byte[DOWNLOAD_CHUNK];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            long writeAt;
                            this.lock.lock();
                            try {
                                if (this.closed) return;
                                writeAt = this.downloadedTo;
                            } finally {
                                this.lock.unlock();
                            }
                            this.channel.write(ByteBuffer.wrap(buffer, 0, read), writeAt);
                            retries = 0;
                            this.lock.lock();
                            try {
                                this.downloadedTo = writeAt + read;
                                this.progress.signalAll();
                            } finally {
                                this.lock.unlock();
                            }
                        }
                    }
                    this.lock.lock();
                    try {
                        if (this.totalLength < 0 || this.downloadedTo >= this.totalLength) {
                            // Servers that never told us a length define it by EOF.
                            this.totalLength = this.downloadedTo;
                            this.complete = true;
                            this.progress.signalAll();
                            return;
                        }
                    } finally {
                        this.lock.unlock();
                    }
                    // Short body: reconnect with a Range resume.
                    if (++retries > MAX_RETRIES) {
                        this.fail(new IOException("Download kept ending early for " + this.url));
                        return;
                    }
                } catch (IOException e) {
                    if (this.isClosed()) return;
                    if (++retries > MAX_RETRIES) {
                        this.fail(e);
                        return;
                    }
                    Concerto.getLogger().warn("Download error ({}), retrying: {}", retries, e.getMessage());
                    try {
                        Thread.sleep(Math.min(2000L, 200L * retries));
                    } catch (InterruptedException interrupted) {
                        return;
                    }
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        } finally {
            this.lock.lock();
            try {
                this.progress.signalAll();
            } finally {
                this.lock.unlock();
            }
        }
    }

    private HttpURLConnection openConnection(long from) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) this.url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (from > 0) {
            connection.setRequestProperty("Range", "bytes=" + from + "-");
        }
        return connection;
    }

    private void updateTotalLength(HttpURLConnection connection, int code, long resumeFrom) {
        long total = -1;
        if (code == HttpURLConnection.HTTP_PARTIAL) {
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                int slash = contentRange.lastIndexOf('/');
                if (slash >= 0 && !contentRange.endsWith("*")) {
                    try {
                        total = Long.parseLong(contentRange.substring(slash + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (total < 0) {
                long remaining = connection.getContentLengthLong();
                if (remaining >= 0) total = resumeFrom + remaining;
            }
        } else {
            total = connection.getContentLengthLong();
        }
        if (total >= 0) {
            this.lock.lock();
            try {
                this.totalLength = total;
                this.progress.signalAll();
            } finally {
                this.lock.unlock();
            }
        }
    }

    private static void skipFully(java.io.InputStream in, long count) throws IOException {
        byte[] scratch = count > 0 ? new byte[DOWNLOAD_CHUNK] : null;
        while (count > 0) {
            int read = in.read(scratch, 0, (int) Math.min(scratch.length, count));
            if (read == -1) throw new IOException("Stream ended before the resume point");
            count -= read;
        }
    }

    private void fail(IOException e) {
        Concerto.getLogger().error("Media download failed: {}", e.getMessage());
        this.lock.lock();
        try {
            this.failure = e;
            this.progress.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    private boolean isClosed() {
        this.lock.lock();
        try {
            return this.closed;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int length) throws IOException {
        int count;
        this.lock.lock();
        try {
            while (true) {
                if (this.closed) throw new IOException("Source closed");
                if (this.totalLength >= 0 && position >= this.totalLength) return -1;
                if (position < this.downloadedTo) break;
                if (this.failure != null) throw new IOException("Media download failed", this.failure);
                try {
                    this.progress.await(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for media data", e);
                }
            }
            count = (int) Math.min(length, this.downloadedTo - position);
        } finally {
            this.lock.unlock();
        }
        return this.channel.read(ByteBuffer.wrap(buffer, offset, count), position);
    }

    @Override
    public long length() {
        this.lock.lock();
        try {
            return this.totalLength;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public long availableTo() {
        this.lock.lock();
        try {
            return this.downloadedTo;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean isComplete() {
        this.lock.lock();
        try {
            return this.complete;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean awaitAvailable(long position, int count, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.lock.lock();
        try {
            while (true) {
                if (this.closed || this.failure != null) return true;
                if (this.totalLength >= 0 && position + count >= this.totalLength) return true;
                if (position + count <= this.downloadedTo) return true;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                this.progress.awaitNanos(remaining);
            }
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Best-effort sweep of the spool directory (startup and track changes).
     * Files still open by a live source are locked on Windows and survive.
     */
    public static void cleanTempDirectory() {
        try (var files = Files.list(TEMP_DIR)) {
            files.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
            // directory does not exist yet
        }
    }

    @Override
    public void close() throws IOException {
        this.lock.lock();
        try {
            if (this.closed) return;
            this.closed = true;
            this.progress.signalAll();
        } finally {
            this.lock.unlock();
        }
        this.downloadThread.interrupt();
        this.channel.close();
        try {
            this.downloadThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            Files.deleteIfExists(this.tempFile);
        } catch (IOException e) {
            Concerto.getLogger().warn("Could not delete temp media file {}", this.tempFile);
        }
    }
}
