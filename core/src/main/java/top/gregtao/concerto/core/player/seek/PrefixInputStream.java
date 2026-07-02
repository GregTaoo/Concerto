package top.gregtao.concerto.core.player.seek;

import java.io.IOException;
import java.io.InputStream;

class PrefixInputStream extends InputStream {
    private final byte[] prefix;
    private final InputStream delegate;
    private int prefixPosition;

    PrefixInputStream(byte[] prefix, InputStream delegate) {
        this.prefix = prefix == null ? new byte[0] : prefix;
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
        if (this.prefixPosition < this.prefix.length) {
            return this.prefix[this.prefixPosition++] & 0xFF;
        }
        return this.delegate.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (this.prefixPosition < this.prefix.length) {
            int copied = Math.min(length, this.prefix.length - this.prefixPosition);
            System.arraycopy(this.prefix, this.prefixPosition, buffer, offset, copied);
            this.prefixPosition += copied;
            return copied;
        }
        return this.delegate.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }
}
