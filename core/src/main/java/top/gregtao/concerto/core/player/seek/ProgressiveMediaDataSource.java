package top.gregtao.concerto.core.player.seek;

import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.Concerto;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ProgressiveMediaDataSource implements Closeable {
    private static final int HTTP_CONNECT_TIMEOUT_MS = 5000;
    private static final int HTTP_READ_TIMEOUT_MS = 10000;
    private static final int DEFAULT_FORWARD_READ_CHUNK = 256 * 1024;
    private static final int MEMORY_CHUNK_SIZE = 64 * 1024;
    private static final int MAX_MEMORY_BUFFER_BYTES = 16 * 1024 * 1024;
    private static final int FORWARD_BUFFER_BYTES = 4 * 1024 * 1024;

    private final File localFile;
    private final Supplier<String> urlSupplier;
    private URL url;
    private long length;

    private final Object memoryLock = new Object();
    private final Map<Long, byte[]> memoryChunks = new HashMap<>();
    private final ArrayDeque<Long> chunkAccessOrder = new ArrayDeque<>();
    private final Object controlLock = new Object();
    private InputStream controlInputStream;
    private long controlPosition = -1L;
    private LiveSeekIndexer liveSeekIndexer;
    private final Object prefetchLock = new Object();
    private ProgressiveInputStream pendingPrefetchStream;
    private volatile ProgressiveInputStream activePlaybackStream;
    private boolean prefetchWorkerRunning;
    private volatile boolean closed;

    private ProgressiveMediaDataSource(File localFile, URL url, Supplier<String> urlSupplier) {
        this.localFile = localFile;
        this.url = url;
        this.urlSupplier = urlSupplier;
        this.length = localFile == null ? probeLength(url) : localFile.length();
    }

    public static ProgressiveMediaDataSource forFile(File file) throws IOException {
        return new ProgressiveMediaDataSource(file, null, null);
    }

    public static ProgressiveMediaDataSource forUrl(String rawUrl, Supplier<String> urlSupplier) throws IOException {
        URL resolved = URI.create(rawUrl).toURL();
        return new ProgressiveMediaDataSource(null, resolved, urlSupplier);
    }

    public InputStream openStream(long offset) throws IOException {
        ensureOpen();
        long safeOffset = Math.max(0L, offset);
        if (this.localFile != null) {
            InputStream inputStream = new FileInputStream(this.localFile);
            skipFully(inputStream, safeOffset);
            return new NotifyingInputStream(inputStream, safeOffset);
        }
        ProgressiveInputStream stream = new ProgressiveInputStream(safeOffset);
        synchronized (this.prefetchLock) {
            this.activePlaybackStream = stream;
            this.pendingPrefetchStream = null;
        }
        return stream;
    }

    public long length() {
        return this.length;
    }

    public boolean hasLength() {
        return this.length >= 0L;
    }

    public void setLiveSeekIndex(LiveSeekIndex index) {
        this.liveSeekIndexer = new LiveSeekIndexer(index.getFormatName(), index, this);
    }

    public boolean isFullyCached(long offset, long length) {
        if (this.localFile != null) {
            return offset >= 0L && length >= 0L && offset + length <= this.length;
        }
        if (length < 0L) {
            return false;
        }
        synchronized (this.memoryLock) {
            long position = offset;
            long end = offset + length;
            while (position < end) {
                long chunkIndex = chunkIndex(position);
                byte[] chunk = this.memoryChunks.get(chunkIndex);
                if (chunk == null) {
                    return false;
                }
                int chunkOffset = (int) (position - chunkStart(chunkIndex));
                if (chunkOffset < 0 || chunkOffset >= chunk.length) {
                    return false;
                }
                int copied = Math.min((int) Math.min(Integer.MAX_VALUE, end - position), chunk.length - chunkOffset);
                if (copied <= 0) {
                    return false;
                }
                position += copied;
            }
            return true;
        }
    }

    public String getSuggestedSuffix() {
        String path = this.localFile != null ? this.localFile.getName() : this.url.getPath();
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "";
        }
        return path.substring(idx + 1).toLowerCase();
    }

    public byte[] readAt(long offset, int length) throws IOException {
        ensureOpen();
        byte[] bytes = new byte[length];
        int total = 0;
        while (total < length) {
            ensureOpen();
            int cached = readMemory(offset + total, bytes, total, length - total);
            if (cached > 0) {
                total += cached;
                continue;
            }
            int read = readControl(offset + total, bytes, total, length - total);
            if (read == -1) {
                if (total == 0) {
                    throw new EOFException();
                }
                byte[] truncated = new byte[total];
                System.arraycopy(bytes, 0, truncated, 0, total);
                return truncated;
            }
            writeMemory(offset + total, bytes, total, read);
            notifyLiveIndexer(offset + total, bytes, total, read);
            total += read;
        }
        return bytes;
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        synchronized (this.prefetchLock) {
            this.pendingPrefetchStream = null;
            this.activePlaybackStream = null;
        }
        synchronized (this.memoryLock) {
            this.memoryChunks.clear();
            this.chunkAccessOrder.clear();
        }
        closeControl();
    }

    public void abortReads() {
        this.closed = true;
    }

    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Media source closed");
        }
    }

    private InputStream openHttpRange(long offset) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) this.url.openConnection();
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            if (offset > 0L) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
            }
            int code = connection.getResponseCode();
            Concerto.getLogger().info("HTTP media GET {} response {} Content-Length={} Content-Range={}",
                    this.url, code, connection.getHeaderField("Content-Length"), connection.getHeaderField("Content-Range"));
            if (code == HttpURLConnection.HTTP_PARTIAL || code == HttpURLConnection.HTTP_OK) {
                updateLengthFromConnection(connection, offset, code);
                HttpRangeInputStream inputStream = new HttpRangeInputStream(connection);
                if (offset > 0L && code == HttpURLConnection.HTTP_OK) {
                    skipFully(inputStream, offset);
                }
                return inputStream;
            }
            last = new IOException(code + " - cannot access media url: " + this.url);
            if (code == HttpURLConnection.HTTP_FORBIDDEN && this.urlSupplier != null) {
                String refreshed = this.urlSupplier.get();
                if (refreshed != null && !refreshed.isEmpty()) {
                    this.url = URI.create(refreshed).toURL();
                    Concerto.getLogger().warn("Refreshing expired media URL for precise seek.");
                    continue;
                }
            }
            connection.disconnect();
            break;
        }
        throw last;
    }

    private long probeLength(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            Concerto.getLogger().info("HTTP media connect GET {} Range=bytes=0-0", url);
            int code = connection.getResponseCode();
            Concerto.getLogger().info("HTTP media response {} Content-Length={} Content-Range={}",
                    code, connection.getHeaderField("Content-Length"), connection.getHeaderField("Content-Range"));
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                long parsed = parseContentRangeLength(connection.getHeaderField("Content-Range"));
                if (parsed >= 0L) {
                    return parsed;
                }
            }
            long headerLength = connection.getHeaderFieldLong("Content-Length", -1L);
            if (code == HttpURLConnection.HTTP_OK) {
                return headerLength;
            }
        } catch (IOException ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return -1L;
    }

    private void updateLengthFromConnection(HttpURLConnection connection, long requestedOffset, int responseCode) {
        long parsed = parseContentRangeLength(connection.getHeaderField("Content-Range"));
        if (parsed >= 0L) {
            this.length = parsed;
            return;
        }
        long contentLength = connection.getHeaderFieldLong("Content-Length", -1L);
        if (contentLength >= 0L) {
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                this.length = Math.max(this.length, requestedOffset + contentLength);
            } else if (requestedOffset == 0L) {
                this.length = contentLength;
            }
        }
    }

    private static long parseContentRangeLength(String contentRange) {
        if (contentRange == null) {
            return -1L;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash == contentRange.length() - 1) {
            return -1L;
        }
        String total = contentRange.substring(slash + 1).trim();
        if ("*".equals(total)) {
            return -1L;
        }
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private int readMemory(long position, byte[] buffer, int offset, int length) {
        if (this.localFile != null) {
            return -1;
        }
        synchronized (this.memoryLock) {
            int total = 0;
            while (total < length) {
                long absolute = position + total;
                long chunkIndex = chunkIndex(absolute);
                byte[] chunk = this.memoryChunks.get(chunkIndex);
                if (chunk == null) {
                    break;
                }
                touchChunk(chunkIndex);
                int chunkOffset = (int) (absolute - chunkStart(chunkIndex));
                if (chunkOffset >= chunk.length) {
                    break;
                }
                int copied = Math.min(length - total, chunk.length - chunkOffset);
                System.arraycopy(chunk, chunkOffset, buffer, offset + total, copied);
                total += copied;
            }
            return total == 0 ? -1 : total;
        }
    }

    private int readControl(long position, byte[] buffer, int offset, int length) throws IOException {
        if (this.localFile != null) {
            try (InputStream inputStream = new FileInputStream(this.localFile)) {
                skipFully(inputStream, position);
                return inputStream.read(buffer, offset, length);
            }
        }
        synchronized (this.controlLock) {
            if (this.controlInputStream == null || this.controlPosition != position) {
                closeControlLocked();
                this.controlInputStream = openHttpRange(position);
                this.controlPosition = position;
            }
            int max = this.length >= 0L ? (int) Math.min(length, this.length - position) : length;
            if (max <= 0) {
                return -1;
            }
            int read = this.controlInputStream.read(buffer, offset, max);
            if (read == -1) {
                closeControlLocked();
                return -1;
            }
            this.controlPosition += read;
            return read;
        }
    }

    private void closeControl() throws IOException {
        synchronized (this.controlLock) {
            closeControlLocked();
        }
    }

    private void closeControlLocked() throws IOException {
        if (this.controlInputStream != null) {
            this.controlInputStream.close();
            this.controlInputStream = null;
            this.controlPosition = -1L;
        }
    }

    private void writeMemory(long position, byte[] buffer, int offset, int length) {
        synchronized (this.memoryLock) {
            int written = 0;
            while (written < length) {
                long absolute = position + written;
                long chunkIndex = chunkIndex(absolute);
                int chunkOffset = (int) (absolute - chunkStart(chunkIndex));
                int copied = Math.min(length - written, MEMORY_CHUNK_SIZE - chunkOffset);
                byte[] old = this.memoryChunks.get(chunkIndex);
                int newLength = Math.max(old == null ? 0 : old.length, chunkOffset + copied);
                byte[] chunk = old == null || old.length < newLength ? new byte[newLength] : old;
                if (old != null && old != chunk) {
                    System.arraycopy(old, 0, chunk, 0, old.length);
                }
                System.arraycopy(buffer, offset + written, chunk, chunkOffset, copied);
                this.memoryChunks.put(chunkIndex, chunk);
                touchChunk(chunkIndex);
                written += copied;
            }
            evictOldChunks();
        }
    }

    private void touchChunk(long chunkIndex) {
        this.chunkAccessOrder.remove(chunkIndex);
        this.chunkAccessOrder.addLast(chunkIndex);
    }

    private void evictOldChunks() {
        int maxChunks = Math.max(1, MAX_MEMORY_BUFFER_BYTES / MEMORY_CHUNK_SIZE);
        while (this.memoryChunks.size() > maxChunks) {
            Long oldest = this.chunkAccessOrder.pollFirst();
            if (oldest == null) {
                break;
            }
            this.memoryChunks.remove(oldest);
        }
    }

    private static long chunkIndex(long position) {
        return Math.max(0L, position / MEMORY_CHUNK_SIZE);
    }

    private static long chunkStart(long chunkIndex) {
        return chunkIndex * MEMORY_CHUNK_SIZE;
    }

    private static void skipFully(InputStream inputStream, long bytes) throws IOException {
        long skippedTotal = 0L;
        byte[] scratch = null;
        while (skippedTotal < bytes) {
            long skipped = inputStream.skip(bytes - skippedTotal);
            if (skipped == 0L) {
                if (scratch == null) {
                    scratch = new byte[8192];
                }
                int read = inputStream.read(scratch, 0, (int) Math.min(scratch.length, bytes - skippedTotal));
                if (read == -1) {
                    throw new EOFException();
                }
                skipped = read;
            }
            skippedTotal += skipped;
        }
    }

    private void scheduleForwardBuffer(ProgressiveInputStream stream) {
        if (!isActivePlaybackStream(stream) || stream.closed || this.length >= 0L && stream.position >= this.length) {
            return;
        }
        long targetEnd = this.length >= 0L
                ? Math.min(this.length, stream.position + FORWARD_BUFFER_BYTES)
                : stream.position + FORWARD_BUFFER_BYTES;
        if (isFullyCached(stream.position, Math.max(0L, targetEnd - stream.position))) {
            return;
        }
        synchronized (this.prefetchLock) {
            if (!isActivePlaybackStream(stream)) {
                return;
            }
            this.pendingPrefetchStream = stream;
            if (this.prefetchWorkerRunning) {
                return;
            }
            this.prefetchWorkerRunning = true;
        }
        Thread thread = new Thread(this::runPrefetchWorker, "Concerto media prefetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void runPrefetchWorker() {
        while (true) {
            ProgressiveInputStream stream;
            synchronized (this.prefetchLock) {
                stream = this.pendingPrefetchStream;
                this.pendingPrefetchStream = null;
                if (stream == null) {
                    this.prefetchWorkerRunning = false;
                    return;
                }
            }
            try {
                if (isActivePlaybackStream(stream)) {
                    stream.prefetchForwardBuffer();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private boolean isActivePlaybackStream(ProgressiveInputStream stream) {
        return this.activePlaybackStream == stream;
    }

    private final class ProgressiveInputStream extends InputStream {
        private long position;
        private InputStream networkInputStream;
        private long networkPosition = -1L;
        private boolean closed;
        private final Object networkLock = new Object();

        private ProgressiveInputStream(long position) {
            this.position = position;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = this.read(one, 0, 1);
            return read == -1 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            if (this.closed || ProgressiveMediaDataSource.this.closed) {
                return -1;
            }
            if (length == 0) {
                return 0;
            }
            if (ProgressiveMediaDataSource.this.length >= 0L && this.position >= ProgressiveMediaDataSource.this.length) {
                return -1;
            }
            int cached = readMemory(this.position, buffer, offset, length);
            if (cached > 0) {
                this.position += cached;
                ProgressiveMediaDataSource.this.scheduleForwardBuffer(this);
                return cached;
            }
            int read;
            synchronized (this.networkLock) {
                if (this.networkInputStream == null || this.networkPosition != this.position) {
                    closeNetworkLocked();
                    this.networkInputStream = openHttpRange(this.position);
                    this.networkPosition = this.position;
                }
                int max = ProgressiveMediaDataSource.this.length >= 0L
                        ? (int) Math.min(length, ProgressiveMediaDataSource.this.length - this.position)
                        : length;
                read = this.networkInputStream.read(buffer, offset, max);
                if (read == -1) {
                    return -1;
                }
                writeMemory(this.position, buffer, offset, read);
                notifyLiveIndexer(this.position, buffer, offset, read);
                this.position += read;
                this.networkPosition += read;
            }
            ProgressiveMediaDataSource.this.scheduleForwardBuffer(this);
            return read;
        }

        private void prefetchForwardBuffer() throws IOException {
            if (!ProgressiveMediaDataSource.this.isActivePlaybackStream(this)) {
                closeNetwork();
                return;
            }
            long snapshotPosition = this.position;
            long targetEnd = ProgressiveMediaDataSource.this.length >= 0L
                    ? Math.min(ProgressiveMediaDataSource.this.length, snapshotPosition + FORWARD_BUFFER_BYTES)
                    : snapshotPosition + FORWARD_BUFFER_BYTES;
            byte[] scratch = new byte[8192];
            while (ProgressiveMediaDataSource.this.isActivePlaybackStream(this)
                    && !this.closed && !ProgressiveMediaDataSource.this.closed) {
                synchronized (this.networkLock) {
                    if (!ProgressiveMediaDataSource.this.isActivePlaybackStream(this)
                            || this.closed || ProgressiveMediaDataSource.this.closed) {
                        closeNetworkLocked();
                        return;
                    }
                    if (this.networkInputStream == null || this.networkPosition >= targetEnd
                            || !isFullyCached(snapshotPosition, Math.max(0L, this.networkPosition - snapshotPosition))) {
                        return;
                    }
                    int max = (int) Math.min(scratch.length, targetEnd - this.networkPosition);
                    if (max <= 0) {
                        return;
                    }
                    long writePosition = this.networkPosition;
                    int read = this.networkInputStream.read(scratch, 0, max);
                    if (read == -1) {
                        closeNetworkLocked();
                        return;
                    }
                    writeMemory(writePosition, scratch, 0, read);
                    notifyLiveIndexer(writePosition, scratch, 0, read);
                    this.networkPosition += read;
                }
            }
            synchronized (this.networkLock) {
                closeNetworkLocked();
            }
        }

        @Override
        public long skip(long bytes) throws IOException {
            if (bytes <= 0L) {
                return 0L;
            }
            long skipped = ProgressiveMediaDataSource.this.length >= 0L
                    ? Math.min(bytes, ProgressiveMediaDataSource.this.length - this.position)
                    : bytes;
            this.position += skipped;
            closeNetwork();
            return skipped;
        }

        @Override
        public int available() {
            if (ProgressiveMediaDataSource.this.length < 0L) {
                return DEFAULT_FORWARD_READ_CHUNK;
            }
            return (int) Math.min(Integer.MAX_VALUE, ProgressiveMediaDataSource.this.length - this.position);
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            synchronized (ProgressiveMediaDataSource.this.prefetchLock) {
                if (ProgressiveMediaDataSource.this.pendingPrefetchStream == this) {
                    ProgressiveMediaDataSource.this.pendingPrefetchStream = null;
                }
                if (ProgressiveMediaDataSource.this.activePlaybackStream == this) {
                    ProgressiveMediaDataSource.this.activePlaybackStream = null;
                }
            }
            closeNetwork();
        }

        private void closeNetwork() throws IOException {
            synchronized (this.networkLock) {
                closeNetworkLocked();
            }
        }

        private void closeNetworkLocked() throws IOException {
            if (this.networkInputStream != null) {
                this.networkInputStream.close();
                this.networkInputStream = null;
                this.networkPosition = -1L;
            }
        }
    }

    private void notifyLiveIndexer(long position, byte[] buffer, int offset, int length) {
        LiveSeekIndexer indexer = this.liveSeekIndexer;
        if (indexer != null) {
            indexer.onBytes(position, buffer, offset, length);
        }
    }

    private final class NotifyingInputStream extends InputStream {
        private final InputStream delegate;
        private long position;

        private NotifyingInputStream(InputStream delegate, long position) {
            this.delegate = delegate;
            this.position = position;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = this.read(one, 0, 1);
            return read == -1 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            int read = this.delegate.read(buffer, offset, length);
            if (read > 0) {
                notifyLiveIndexer(this.position, buffer, offset, read);
                this.position += read;
            }
            return read;
        }

        @Override
        public long skip(long bytes) throws IOException {
            long skipped = this.delegate.skip(bytes);
            this.position += skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return this.delegate.available();
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }
    }

    private static final class HttpRangeInputStream extends InputStream {
        private final HttpURLConnection connection;
        private final InputStream delegate;

        private HttpRangeInputStream(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            this.delegate = connection.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return this.delegate.read();
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            return this.delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            try {
                this.delegate.close();
            } finally {
                this.connection.disconnect();
            }
        }
    }
}
