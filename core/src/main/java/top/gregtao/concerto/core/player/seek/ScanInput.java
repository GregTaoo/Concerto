package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;
import top.gregtao.concerto.core.player.source.ByteSourceInputStream;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Sequential scan reader used by the index builders: buffered reads over an
 * {@link AudioByteSource} with absolute-position tracking and big-/little-endian
 * integer helpers.
 */
class ScanInput {

    private final BufferedInputStream in;
    private long position;

    ScanInput(AudioByteSource source, long startPosition) {
        this.in = new BufferedInputStream(new ByteSourceInputStream(source, startPosition), 64 * 1024);
        this.position = startPosition;
    }

    long position() {
        return this.position;
    }

    /** @return -1 at end of media */
    int read() throws IOException {
        int value = this.in.read();
        if (value != -1) this.position++;
        return value;
    }

    /** Reads exactly {@code length} bytes or throws {@link EOFException}. */
    void readFully(byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = this.in.read(buffer, offset + total, length - total);
            if (read == -1) throw new EOFException();
            total += read;
        }
        this.position += length;
    }

    /** Tries to fill {@code buffer}; @return bytes actually read (may be < length at EOF). */
    int readUpTo(byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = this.in.read(buffer, offset + total, length - total);
            if (read == -1) break;
            total += read;
        }
        this.position += total;
        return total;
    }

    void skipFully(long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = this.in.skip(remaining);
            if (skipped <= 0) {
                if (this.in.read() == -1) throw new EOFException();
                skipped = 1;
            }
            remaining -= skipped;
        }
        this.position += count;
    }

    int readU8() throws IOException {
        int value = this.read();
        if (value == -1) throw new EOFException();
        return value;
    }

    int readU16BE() throws IOException {
        return (this.readU8() << 8) | this.readU8();
    }

    long readU32BE() throws IOException {
        return ((long) this.readU16BE() << 16) | this.readU16BE();
    }

    long readU64BE() throws IOException {
        return (this.readU32BE() << 32) | this.readU32BE();
    }

    int readU16LE() throws IOException {
        int a = this.readU8(), b = this.readU8();
        return (b << 8) | a;
    }

    long readU32LE() throws IOException {
        long a = this.readU16LE(), b = this.readU16LE();
        return (b << 16) | a;
    }

    long readU64LE() throws IOException {
        long a = this.readU32LE(), b = this.readU32LE();
        return (b << 32) | a;
    }
}
