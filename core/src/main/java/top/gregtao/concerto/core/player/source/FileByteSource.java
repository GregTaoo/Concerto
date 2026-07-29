package top.gregtao.concerto.core.player.source;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class FileByteSource implements AudioByteSource {

    private final FileChannel channel;
    private final long length;

    public FileByteSource(File file) throws IOException {
        this.channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        this.length = this.channel.size();
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int length) throws IOException {
        if (position >= this.length) return -1;
        return this.channel.read(ByteBuffer.wrap(buffer, offset, length), position);
    }

    @Override
    public long length() {
        return this.length;
    }

    @Override
    public long availableTo() {
        return this.length;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public boolean awaitAvailable(long position, int count, long timeoutMillis) {
        return true;
    }

    @Override
    public void close() throws IOException {
        this.channel.close();
    }
}
