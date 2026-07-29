package top.gregtao.concerto.core.player.source;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Sequential {@link InputStream} view over an {@link AudioByteSource}, starting
 * at an arbitrary byte offset. Closing the stream does NOT close the source; the
 * owning session manages the source lifecycle.
 */
public class ByteSourceInputStream extends InputStream {

    private final AudioByteSource source;
    private long position;

    public ByteSourceInputStream(AudioByteSource source, long startPosition) {
        this.source = source;
        this.position = startPosition;
    }

    public long position() {
        return this.position;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int read = this.read(single, 0, 1);
        return read == -1 ? -1 : (single[0] & 0xFF);
    }

    @Override
    public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int read = this.source.read(this.position, buffer, offset, length);
        if (read > 0) this.position += read;
        return read;
    }

    @Override
    public long skip(long count) {
        if (count <= 0) return 0;
        long total = this.source.length();
        long skipped = total >= 0 ? Math.min(count, Math.max(0, total - this.position)) : count;
        this.position += skipped;
        return skipped;
    }

    @Override
    public int available() {
        long remaining = this.source.availableTo() - this.position;
        return remaining <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
    }
}
