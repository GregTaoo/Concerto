package top.gregtao.concerto.core.player.seek;

import java.io.IOException;
import java.io.InputStream;

class LimitedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;

    LimitedInputStream(InputStream delegate, long remaining) {
        this.delegate = delegate;
        this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
        if (this.remaining == 0L) {
            return -1;
        }
        int value = this.delegate.read();
        if (value != -1 && this.remaining > 0L) {
            this.remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (this.remaining == 0L) {
            return -1;
        }
        int max = this.remaining < 0L ? length : (int) Math.min(length, this.remaining);
        int read = this.delegate.read(buffer, offset, max);
        if (read > 0 && this.remaining > 0L) {
            this.remaining -= read;
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }
}
