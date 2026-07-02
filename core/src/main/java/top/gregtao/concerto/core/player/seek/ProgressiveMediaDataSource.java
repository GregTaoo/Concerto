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
    private LiveSeekIndexer liveSeekIndexer;
    private final Object prefetchLock = new Object();
    private volatile ProgressiveInputStream activePlaybackStream;
    private HttpRangeInputStream prefetchInputStream;
    private long prefetchPosition = -1L;
    private boolean prefetchRangeIgnored;
    private long prefetchRequestPosition = -1L;
    private long prefetchRequestEnd = -1L;
    private long prefetchRequestGeneration = 0L;
    private volatile IOException prefetchFailure;
    private volatile long prefetchEofPosition = -1L;
    private boolean prefetchWorkerRunning;
    private volatile boolean closed;

    private ProgressiveMediaDataSource(File localFile, URL url, Supplier<String> urlSupplier) {
        this.localFile = localFile;
        this.url = url;
        this.urlSupplier = urlSupplier;
        this.length = localFile == null ? -1L : localFile.length();
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
        }
        scheduleForwardBuffer(stream);
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
        if (this.localFile != null) {
            byte[] bytes = new byte[length];
            int total = 0;
            while (total < length) {
                int read = readLocal(offset + total, bytes, total, length - total);
                if (read == -1) {
                    if (total == 0) {
                        throw new EOFException();
                    }
                    byte[] truncated = new byte[total];
                    System.arraycopy(bytes, 0, truncated, 0, total);
                    return truncated;
                }
                total += read;
            }
            return bytes;
        }
        byte[] bytes = new byte[length];
        int total = 0;
        while (total < length) {
            ensureOpen();
            int cached = readMemory(offset + total, bytes, total, length - total);
            if (cached > 0) {
                total += cached;
                continue;
            }
            long position = offset + total;
            if (this.length >= 0L && position >= this.length) {
                if (total == 0) {
                    throw new EOFException();
                }
                byte[] truncated = new byte[total];
                System.arraycopy(bytes, 0, truncated, 0, total);
                return truncated;
            }
            requestCache(position, position + length - total);
            waitForCache(position);
            if (this.prefetchEofPosition >= 0L && position >= this.prefetchEofPosition) {
                if (total == 0) {
                    throw new EOFException();
                }
                byte[] truncated = new byte[total];
                System.arraycopy(bytes, 0, truncated, 0, total);
                return truncated;
            }
        }
        return bytes;
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        synchronized (this.prefetchLock) {
            this.activePlaybackStream = null;
            closePrefetchLocked();
            this.prefetchLock.notifyAll();
        }
        synchronized (this.memoryLock) {
            this.memoryChunks.clear();
            this.chunkAccessOrder.clear();
            this.memoryLock.notifyAll();
        }
    }

    public void abortReads() {
        this.closed = true;
        synchronized (this.prefetchLock) {
            try {
                closePrefetchLocked();
            } catch (IOException ignored) {
            }
            this.prefetchLock.notifyAll();
        }
        synchronized (this.memoryLock) {
            this.memoryLock.notifyAll();
        }
    }

    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Media source closed");
        }
    }

    private int readLocal(long position, byte[] buffer, int offset, int length) throws IOException {
        try (InputStream inputStream = new FileInputStream(this.localFile)) {
            skipFully(inputStream, position);
            return inputStream.read(buffer, offset, length);
        }
    }

    private HttpRangeInputStream openHttpRange(long offset) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) this.url.openConnection();
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0L) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
            }
            int code = connection.getResponseCode();
            Concerto.getLogger().info("HTTP media GET {} response {} Content-Length={} Content-Range={}",
                    this.url, code, connection.getHeaderField("Content-Length"), connection.getHeaderField("Content-Range"));
            if (code == HttpURLConnection.HTTP_PARTIAL || offset == 0L && code == HttpURLConnection.HTTP_OK) {
                updateLengthFromConnection(connection, offset, code);
                return new HttpRangeInputStream(connection, offset);
            }
            if (offset > 0L && code == HttpURLConnection.HTTP_OK) {
                last = new IOException("Server ignored HTTP Range request for media offset " + offset);
                connection.disconnect();
                if (this.urlSupplier != null) {
                    String refreshed = this.urlSupplier.get();
                    if (refreshed != null && !refreshed.isEmpty() && !refreshed.equals(this.url.toString())) {
                        this.url = URI.create(refreshed).toURL();
                        Concerto.getLogger().warn("Refreshing media URL after ignored range request.");
                        continue;
                    }
                }
                break;
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
            this.memoryLock.notifyAll();
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
        requestCache(stream.position, targetEnd);
    }

    private void runPrefetchWorker() {
        byte[] scratch = new byte[8192];
        while (true) {
            long requestPosition;
            long requestEnd;
            long requestGeneration;
            synchronized (this.prefetchLock) {
                while (!this.closed && this.prefetchRequestPosition < 0L) {
                    try {
                        this.prefetchLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        this.prefetchWorkerRunning = false;
                        return;
                    }
                }
                if (this.closed) {
                    this.prefetchWorkerRunning = false;
                    return;
                }
                requestPosition = this.prefetchRequestPosition;
                requestEnd = this.prefetchRequestEnd;
                requestGeneration = this.prefetchRequestGeneration;
            }

            try {
                long missing = firstMissingPosition(requestPosition, requestEnd);
                if (missing < 0L) {
                    synchronized (this.prefetchLock) {
                        if (this.prefetchRequestGeneration == requestGeneration) {
                            this.prefetchRequestPosition = -1L;
                            this.prefetchRequestEnd = -1L;
                        }
                    }
                    continue;
                }

                HttpRangeInputStream stream;
                long writePosition;
                int max;
                boolean needOpen;
                synchronized (this.prefetchLock) {
                    boolean canKeepSequentialFallback = this.prefetchRangeIgnored
                            && this.prefetchInputStream != null
                            && this.prefetchPosition <= missing;
                    if (this.prefetchInputStream == null || this.prefetchPosition != missing && !canKeepSequentialFallback) {
                        closePrefetchLocked();
                    }
                    needOpen = this.prefetchInputStream == null;
                }
                if (needOpen) {
                    HttpRangeInputStream opened = openHttpRange(missing);
                    synchronized (this.prefetchLock) {
                        if (this.closed) {
                            opened.close();
                            continue;
                        }
                        if (this.prefetchRequestGeneration != requestGeneration) {
                            opened.close();
                            continue;
                        }
                        this.prefetchInputStream = opened;
                        this.prefetchPosition = opened.getStartOffset();
                        this.prefetchRangeIgnored = opened.getStartOffset() != missing;
                    }
                }
                synchronized (this.prefetchLock) {
                    if (this.prefetchRequestGeneration != requestGeneration) {
                        continue;
                    }
                    stream = this.prefetchInputStream;
                    if (stream == null) {
                        continue;
                    }
                    writePosition = this.prefetchPosition;
                    max = (int) Math.min(scratch.length, requestEnd - this.prefetchPosition);
                    if (this.length >= 0L) {
                        max = (int) Math.min(max, this.length - this.prefetchPosition);
                    }
                    if (max <= 0) {
                        this.prefetchRequestPosition = -1L;
                        this.prefetchRequestEnd = -1L;
                        continue;
                    }
                }

                int read = stream.read(scratch, 0, max);
                synchronized (this.prefetchLock) {
                    if (this.prefetchRequestGeneration != requestGeneration
                            || stream != this.prefetchInputStream || writePosition != this.prefetchPosition) {
                        continue;
                    }
                    if (read == -1) {
                        this.prefetchEofPosition = this.prefetchPosition;
                        closePrefetchLocked();
                        synchronized (this.memoryLock) {
                            this.memoryLock.notifyAll();
                        }
                        continue;
                    }
                    this.prefetchPosition += read;
                }

                writeMemory(writePosition, scratch, 0, read);
                notifyLiveIndexer(writePosition, scratch, 0, read);
            } catch (IOException e) {
                synchronized (this.prefetchLock) {
                    if (this.prefetchRequestGeneration != requestGeneration) {
                        continue;
                    }
                    try {
                        closePrefetchLocked();
                    } catch (IOException ignored) {
                    }
                    this.prefetchRequestPosition = -1L;
                    this.prefetchRequestEnd = -1L;
                    this.prefetchFailure = e;
                }
                synchronized (this.memoryLock) {
                    this.memoryLock.notifyAll();
                }
            }
        }
    }

    private void requestCache(long position, long targetEnd) {
        if (this.localFile != null || this.closed) {
            return;
        }
        long safePosition = Math.max(0L, position);
        long safeEnd = Math.max(safePosition + 1L, targetEnd);
        if (this.length >= 0L) {
            safeEnd = Math.min(safeEnd, this.length);
        }
        if (safeEnd <= safePosition || firstMissingPosition(safePosition, safeEnd) < 0L) {
            return;
        }
        synchronized (this.prefetchLock) {
            if (this.closed) {
                return;
            }
            this.prefetchFailure = null;
            if (this.prefetchEofPosition >= 0L && safePosition < this.prefetchEofPosition) {
                this.prefetchEofPosition = -1L;
            }
            long requestEnd = Math.max(safeEnd, safePosition + DEFAULT_FORWARD_READ_CHUNK);
            if (this.length >= 0L) {
                requestEnd = Math.min(requestEnd, this.length);
            }
            long missing = firstMissingPosition(safePosition, requestEnd);
            if (missing < 0L) {
                return;
            }
            this.prefetchRequestPosition = missing;
            this.prefetchRequestEnd = Math.max(safeEnd, missing + DEFAULT_FORWARD_READ_CHUNK);
            if (this.length >= 0L) {
                this.prefetchRequestEnd = Math.min(this.prefetchRequestEnd, this.length);
            }
            if (missing >= 0L && this.prefetchInputStream != null
                    && (missing < this.prefetchPosition || !this.prefetchRangeIgnored && missing != this.prefetchPosition)) {
                try {
                    closePrefetchLocked();
                } catch (IOException ignored) {
                }
            }
            this.prefetchRequestGeneration++;
            if (!this.prefetchWorkerRunning) {
                this.prefetchWorkerRunning = true;
                Thread thread = new Thread(this::runPrefetchWorker, "Concerto media prefetch");
                thread.setDaemon(true);
                thread.start();
            }
            this.prefetchLock.notifyAll();
        }
    }

    private void waitForCache(long position) throws IOException {
        while (true) {
            ensureOpen();
            IOException failure = this.prefetchFailure;
            if (failure != null) {
                throw failure;
            }
            if (this.length >= 0L && position >= this.length) {
                return;
            }
            if (cachedLength(position, 1) > 0 || this.prefetchEofPosition >= 0L && position >= this.prefetchEofPosition) {
                return;
            }
            synchronized (this.memoryLock) {
                if (this.length >= 0L && position >= this.length) {
                    return;
                }
                if (cachedLength(position, 1) > 0 || this.prefetchEofPosition >= 0L && position >= this.prefetchEofPosition) {
                    return;
                }
                try {
                    this.memoryLock.wait(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        }
    }

    private long firstMissingPosition(long position, long end) {
        synchronized (this.memoryLock) {
            long current = position;
            while (current < end) {
                long cached = cachedLengthLocked(current, end - current);
                if (cached <= 0L) {
                    return current;
                }
                current += cached;
            }
            return -1L;
        }
    }

    private long cachedLength(long position, long length) {
        synchronized (this.memoryLock) {
            return cachedLengthLocked(position, length);
        }
    }

    private long cachedLengthLocked(long position, long length) {
        long total = 0L;
        while (total < length) {
            long absolute = position + total;
            long chunkIndex = chunkIndex(absolute);
            byte[] chunk = this.memoryChunks.get(chunkIndex);
            if (chunk == null) {
                break;
            }
            int chunkOffset = (int) (absolute - chunkStart(chunkIndex));
            if (chunkOffset < 0 || chunkOffset >= chunk.length) {
                break;
            }
            int copied = (int) Math.min(length - total, chunk.length - chunkOffset);
            if (copied <= 0) {
                break;
            }
            total += copied;
        }
        return total;
    }

    private void closePrefetchLocked() throws IOException {
        if (this.prefetchInputStream != null) {
            this.prefetchInputStream.close();
            this.prefetchInputStream = null;
            this.prefetchPosition = -1L;
            this.prefetchRangeIgnored = false;
        }
    }

    private boolean isActivePlaybackStream(ProgressiveInputStream stream) {
        return this.activePlaybackStream == stream;
    }

    private final class ProgressiveInputStream extends InputStream {
        private long position;
        private boolean closed;

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
            while (cached <= 0) {
                long targetEnd = ProgressiveMediaDataSource.this.length >= 0L
                        ? Math.min(ProgressiveMediaDataSource.this.length, this.position + Math.max(length, DEFAULT_FORWARD_READ_CHUNK))
                        : this.position + Math.max(length, DEFAULT_FORWARD_READ_CHUNK);
                ProgressiveMediaDataSource.this.requestCache(this.position, targetEnd);
                ProgressiveMediaDataSource.this.waitForCache(this.position);
                if (ProgressiveMediaDataSource.this.prefetchEofPosition >= 0L
                        && this.position >= ProgressiveMediaDataSource.this.prefetchEofPosition) {
                    return -1;
                }
                cached = readMemory(this.position, buffer, offset, length);
            }
            this.position += cached;
            ProgressiveMediaDataSource.this.scheduleForwardBuffer(this);
            return cached;
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
            ProgressiveMediaDataSource.this.scheduleForwardBuffer(this);
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
                if (ProgressiveMediaDataSource.this.activePlaybackStream == this) {
                    ProgressiveMediaDataSource.this.activePlaybackStream = null;
                }
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
        private final long startOffset;

        private HttpRangeInputStream(HttpURLConnection connection, long startOffset) throws IOException {
            this.connection = connection;
            this.delegate = connection.getInputStream();
            this.startOffset = startOffset;
        }

        private long getStartOffset() {
            return this.startOffset;
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
