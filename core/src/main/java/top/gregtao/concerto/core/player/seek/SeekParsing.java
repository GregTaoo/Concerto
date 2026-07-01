package top.gregtao.concerto.core.player.seek;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class SeekParsing {
    private SeekParsing() {
    }

    static int u8(byte[] bytes, int offset) {
        return bytes[offset] & 0xFF;
    }

    static int u16be(byte[] bytes, int offset) {
        return (u8(bytes, offset) << 8) | u8(bytes, offset + 1);
    }

    static int u16le(byte[] bytes, int offset) {
        return u8(bytes, offset) | (u8(bytes, offset + 1) << 8);
    }

    static int i32be(byte[] bytes, int offset) {
        return (u8(bytes, offset) << 24) | (u8(bytes, offset + 1) << 16)
                | (u8(bytes, offset + 2) << 8) | u8(bytes, offset + 3);
    }

    static long u32be(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(i32be(bytes, offset));
    }

    static long u32le(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(u8(bytes, offset) | (u8(bytes, offset + 1) << 8)
                | (u8(bytes, offset + 2) << 16) | (u8(bytes, offset + 3) << 24));
    }

    static long u64be(byte[] bytes, int offset) {
        return (u32be(bytes, offset) << 32) | u32be(bytes, offset + 4);
    }

    static long u64le(byte[] bytes, int offset) {
        return u32le(bytes, offset) | (u32le(bytes, offset + 4) << 32);
    }

    static boolean asciiEquals(byte[] bytes, int offset, String text) {
        if (offset < 0 || offset + text.length() > bytes.length) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if ((byte) text.charAt(i) != bytes[offset + i]) {
                return false;
            }
        }
        return true;
    }

    static long syncSafeInt(byte[] bytes, int offset) {
        return ((long) (u8(bytes, offset) & 0x7F) << 21)
                | ((long) (u8(bytes, offset + 1) & 0x7F) << 14)
                | ((long) (u8(bytes, offset + 2) & 0x7F) << 7)
                | (u8(bytes, offset + 3) & 0x7F);
    }

    static void readFully(InputStream inputStream, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = inputStream.read(buffer, offset + total, length - total);
            if (read == -1) {
                throw new EOFException();
            }
            total += read;
        }
    }

    static long skipFully(InputStream inputStream, long bytes) throws IOException {
        long total = 0L;
        byte[] scratch = null;
        while (total < bytes) {
            long skipped = inputStream.skip(bytes - total);
            if (skipped <= 0L) {
                if (scratch == null) {
                    scratch = new byte[4096];
                }
                int read = inputStream.read(scratch, 0, (int) Math.min(scratch.length, bytes - total));
                if (read == -1) {
                    break;
                }
                skipped = read;
            }
            total += skipped;
        }
        return total;
    }
}
