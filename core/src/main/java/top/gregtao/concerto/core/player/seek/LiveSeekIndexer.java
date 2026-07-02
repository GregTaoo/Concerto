package top.gregtao.concerto.core.player.seek;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LiveSeekIndexer implements LiveSeekIndex.Resolver {
    private final LiveSeekIndex index;
    private final String formatName;
    private final ProgressiveMediaDataSource source;
    private final LinearState linear = new LinearState();
    private final Mp4State mp4 = new Mp4State();
    private final Mp3SeekMap mp3 = new Mp3SeekMap();

    public LiveSeekIndexer(String formatName, LiveSeekIndex index, ProgressiveMediaDataSource source) {
        this.formatName = formatName == null ? "" : formatName;
        this.index = index;
        this.source = source;
        this.index.setResolver(this);
    }

    public synchronized void onBytes(long offset, byte[] data, int dataOffset, int length) {
        if (length <= 0 || this.index == null) {
            return;
        }
        byte[] bytes = merged(data, dataOffset, length);
        long mergedOffset = offset - this.linear.tailLength;
        switch (this.formatName) {
            case "mp3" -> {
                // MP3 frame indexing is driven by resolveMp3 so playback and prefetch reads only fill cache.
            }
            case "adts-aac" -> scanAdts(mergedOffset, offset, bytes);
            case "wav" -> scanWav(mergedOffset, bytes);
            case "flac" -> scanFlac(mergedOffset, offset, bytes);
            case "ogg" -> scanOgg(mergedOffset, offset, bytes);
            case "mp4-aac" -> scanMp4(mergedOffset, bytes);
            default -> {
            }
        }
        saveTail(data, dataOffset, length);
    }

    @Override
    public synchronized SeekPoint resolve(ProgressiveMediaDataSource source, long timeMilliseconds) throws IOException {
        if ("mp3".equals(this.formatName)) {
            return resolveMp3(source, timeMilliseconds);
        }
        if ("mp4-aac".equals(this.formatName)) {
            ensureMp4Parsed(source);
            return this.index.timeToSeekPoint(timeMilliseconds);
        }
        SeekPoint known = this.index.timeToSeekPoint(timeMilliseconds);
        if (known.getTimeMilliseconds() >= timeMilliseconds || !source.hasLength()) {
            return known;
        }
        long position = Math.max(known.getByteOffset(), this.linear.scanPosition);
        long limit = source.length();
        byte[] buffer = new byte[64 * 1024];
        while (position < limit) {
            int read = Math.min(buffer.length, (int) Math.min(Integer.MAX_VALUE, limit - position));
            byte[] bytes = source.readAt(position, read);
            if (bytes.length == 0) {
                break;
            }
            onBytes(position, bytes, 0, bytes.length);
            known = this.index.timeToSeekPoint(timeMilliseconds);
            if (known.getTimeMilliseconds() >= timeMilliseconds || bytes.length < read) {
                break;
            }
            position += bytes.length;
        }
        return known;
    }

    private SeekPoint resolveMp3(ProgressiveMediaDataSource source, long timeMilliseconds) throws IOException {
        long target = Math.max(0L, timeMilliseconds);
        if (mp3ScannedPast(target) || !source.hasLength()) {
            return this.mp3.timeToSeekPoint(target).withRequestedTime(target);
        }
        initializeMp3ScanPosition(source);
        long lastScanPosition = -1L;
        byte[] buffer = new byte[64 * 1024];
        while (!mp3ScannedPast(target) && this.linear.scanPosition < source.length()) {
            long position = Math.max(0L, this.linear.scanPosition);
            if (position == lastScanPosition) {
                break;
            }
            lastScanPosition = position;
            int read = Math.min(buffer.length, (int) Math.min(Integer.MAX_VALUE, source.length() - position));
            if (read <= 0) {
                break;
            }
            byte[] bytes = source.readAt(position, read);
            if (bytes.length == 0) {
                break;
            }
            long before = this.linear.scanPosition;
            scanMp3(position, position, bytes);
            if (this.linear.scanPosition <= before) {
                break;
            }
        }
        return this.mp3.timeToSeekPoint(target).withRequestedTime(target);
    }

    private boolean mp3ScannedPast(long timeMilliseconds) {
        return this.linear.sampleRate > 0
                && this.linear.samples * 1000L / this.linear.sampleRate > Math.max(0L, timeMilliseconds);
    }

    private void initializeMp3ScanPosition(ProgressiveMediaDataSource source) throws IOException {
        if (this.linear.sampleRate > 0 || this.linear.scanPosition > 0L || !source.hasLength() || source.length() < 10L) {
            return;
        }
        byte[] header = source.readAt(0L, 10);
        if (SeekParsing.asciiEquals(header, 0, "ID3")) {
            this.linear.scanPosition = Math.min(source.length(), 10L + SeekParsing.syncSafeInt(header, 6));
        }
    }

    private void scanMp3(long mergedOffset, long freshOffset, byte[] bytes) {
        int start = skipId3(bytes, mergedOffset);
        for (int i = Math.max(0, start); i + 4 <= bytes.length; i++) {
            Mp3Frame frame = parseMp3(bytes, i);
            if (frame == null) {
                continue;
            }
            if (i + frame.frameSize > bytes.length) {
                break;
            }
            long frameOffset = mergedOffset + i;
            if (!isExpectedMp3Frame(frameOffset, mergedOffset)) {
                continue;
            }
            if (frameOffset < freshOffset && i + frame.frameSize <= this.linear.tailLength) {
                continue;
            }
            if (!this.linear.mp3SeekHeaderChecked) {
                installMp3SeekMap(bytes, i, frame);
                this.linear.mp3SeekHeaderChecked = true;
            }
            if (this.linear.sampleRate <= 0) {
                this.linear.sampleRate = frame.sampleRate;
                this.linear.samples = 0L;
            }
            long timeMs = this.linear.samples * 1000L / this.linear.sampleRate;
            this.mp3.addPoint(this.linear.samples, frameOffset, frame.sampleRate);
            this.index.addPoint(timeMs, frameOffset);
            this.linear.samples += frame.samplesPerFrame;
            this.linear.scanPosition = frameOffset + frame.frameSize;
            if (this.source.hasLength() && this.linear.scanPosition >= this.source.length()) {
                setMp3DurationMilliseconds(this.linear.samples * 1000L / this.linear.sampleRate);
            }
            i += Math.max(0, frame.frameSize - 1);
        }
    }

    private boolean isExpectedMp3Frame(long frameOffset, long mergedOffset) {
        if (this.linear.sampleRate <= 0 && this.linear.samples == 0L) {
            return frameOffset == this.linear.scanPosition || mergedOffset == 0L;
        }
        return frameOffset == this.linear.scanPosition;
    }

    private void installMp3SeekMap(byte[] bytes, int frameOffsetInBuffer, Mp3Frame frame) {
        if (!this.source.hasLength() || frame.sampleRate <= 0 || frame.samplesPerFrame <= 0 || frame.bitrate <= 0) {
            return;
        }
        Mp3HeaderSeekMap seekMap = parseXingSeekMap(bytes, frameOffsetInBuffer, frame);
        if (seekMap == null) {
            seekMap = parseVbriSeekMap(bytes, frameOffsetInBuffer, frame);
        }
        if (seekMap != null) {
            setMp3DurationMilliseconds(seekMap.getDurationMilliseconds());
        }
    }

    private void setMp3DurationMilliseconds(long durationMilliseconds) {
        this.mp3.setDurationMilliseconds(durationMilliseconds);
        this.index.setDurationMilliseconds(durationMilliseconds);
    }

    private static Mp3HeaderSeekMap parseXingSeekMap(byte[] bytes, int frameOffset, Mp3Frame frame) {
        int sideInfo = frame.versionBits == 3
                ? (frame.channelMode == 3 ? 17 : 32)
                : (frame.channelMode == 3 ? 9 : 17);
        int xing = frameOffset + 4 + sideInfo;
        if (xing + 16 > bytes.length) {
            return null;
        }
        boolean xingHeader = SeekParsing.asciiEquals(bytes, xing, "Xing");
        boolean infoHeader = SeekParsing.asciiEquals(bytes, xing, "Info");
        if (!xingHeader && !infoHeader) {
            return null;
        }
        int flags = (int) SeekParsing.u32be(bytes, xing + 4);
        int cursor = xing + 8;
        long frames = -1L;
        int[] toc = null;
        if ((flags & 0x01) != 0 && cursor + 4 <= bytes.length) {
            frames = SeekParsing.u32be(bytes, cursor);
            cursor += 4;
        }
        if ((flags & 0x02) != 0 && cursor + 4 <= bytes.length) {
            cursor += 4;
        }
        if ((flags & 0x04) != 0 && cursor + 100 <= bytes.length) {
            toc = new int[100];
            for (int i = 0; i < toc.length; i++) {
                toc[i] = u8(bytes, cursor + i);
            }
        }
        long durationMs = frames > 0L ? frames * frame.samplesPerFrame * 1000L / frame.sampleRate : -1L;
        if (durationMs <= 0L) {
            return null;
        }
        if (toc != null) {
            return Mp3HeaderSeekMap.vbr(durationMs);
        }
        return infoHeader ? Mp3HeaderSeekMap.info(durationMs) : null;
    }

    private static Mp3HeaderSeekMap parseVbriSeekMap(byte[] bytes, int frameOffset, Mp3Frame frame) {
        int vbri = frameOffset + 4 + 32;
        if (vbri + 26 > bytes.length || !SeekParsing.asciiEquals(bytes, vbri, "VBRI")) {
            return null;
        }
        long frames = SeekParsing.u32be(bytes, vbri + 14);
        int entryCount = SeekParsing.u16be(bytes, vbri + 18);
        int scale = SeekParsing.u16be(bytes, vbri + 20);
        int bytesPerEntry = SeekParsing.u16be(bytes, vbri + 22);
        int framesPerEntry = SeekParsing.u16be(bytes, vbri + 24);
        if (frames <= 0L || entryCount <= 0 || scale <= 0 || bytesPerEntry <= 0 || bytesPerEntry > 4 || framesPerEntry <= 0) {
            return null;
        }
        int tableStart = vbri + 26;
        if (tableStart + entryCount * bytesPerEntry > bytes.length) {
            return null;
        }
        long durationMs = frames * frame.samplesPerFrame * 1000L / frame.sampleRate;
        return Mp3HeaderSeekMap.vbri(durationMs);
    }

    private int skipId3(byte[] bytes, long mergedOffset) {
        if (mergedOffset != 0L || bytes.length < 10 || !SeekParsing.asciiEquals(bytes, 0, "ID3")) {
            return 0;
        }
        return (int) Math.min(bytes.length, 10L + SeekParsing.syncSafeInt(bytes, 6));
    }

    private void scanAdts(long mergedOffset, long freshOffset, byte[] bytes) {
        for (int i = 0; i + 7 <= bytes.length; i++) {
            AdtsFrame frame = parseAdts(bytes, i);
            if (frame == null || mergedOffset + i < this.linear.scanPosition) {
                continue;
            }
            long frameOffset = mergedOffset + i;
            if (frameOffset < freshOffset && i + frame.frameSize <= this.linear.tailLength) {
                continue;
            }
            if (this.linear.sampleRate <= 0) {
                this.linear.sampleRate = frame.sampleRate;
                this.linear.samples = 0L;
            }
            long timeMs = this.linear.samples * 1000L / this.linear.sampleRate;
            this.index.addPoint(timeMs, frameOffset);
            this.linear.samples += 1024L * frame.rawBlocks;
            this.index.setDurationMilliseconds(this.linear.samples * 1000L / this.linear.sampleRate);
            this.linear.scanPosition = frameOffset + frame.frameSize;
            i += Math.max(0, frame.frameSize - 1);
        }
    }

    private void scanWav(long mergedOffset, byte[] bytes) {
        if (this.linear.wavConfigured || mergedOffset != 0L || bytes.length < 44
                || !SeekParsing.asciiEquals(bytes, 0, "RIFF") || !SeekParsing.asciiEquals(bytes, 8, "WAVE")) {
            return;
        }
        long position = 12L;
        int audioFormat = -1;
        int channels = 0;
        long sampleRate = 0L;
        long byteRate = 0L;
        int blockAlign = 0;
        int bitsPerSample = 0;
        long dataStart = -1L;
        long dataSize = -1L;
        while (position + 8L <= bytes.length) {
            int pos = (int) position;
            long chunkSize = SeekParsing.u32le(bytes, pos + 4);
            long chunkDataStart = position + 8L;
            String chunkId = new String(bytes, pos, 4, StandardCharsets.US_ASCII);
            if ("fmt ".equals(chunkId) && chunkDataStart + 16L <= bytes.length) {
                int fmt = (int) chunkDataStart;
                audioFormat = SeekParsing.u16le(bytes, fmt);
                channels = SeekParsing.u16le(bytes, fmt + 2);
                sampleRate = SeekParsing.u32le(bytes, fmt + 4);
                byteRate = SeekParsing.u32le(bytes, fmt + 8);
                blockAlign = SeekParsing.u16le(bytes, fmt + 12);
                bitsPerSample = SeekParsing.u16le(bytes, fmt + 14);
            } else if ("data".equals(chunkId)) {
                dataStart = chunkDataStart;
                dataSize = chunkSize;
                break;
            }
            position = chunkDataStart + chunkSize + (chunkSize & 1L);
        }
        if (audioFormat == 1 && channels > 0 && sampleRate > 0L && byteRate > 0L
                && blockAlign > 0 && bitsPerSample > 0 && dataStart >= 0L && dataSize > 0L) {
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, bitsPerSample,
                    channels, blockAlign, sampleRate, false);
            this.index.configureWav(dataStart, dataSize, byteRate, blockAlign, format);
            this.linear.wavConfigured = true;
        }
    }

    private void scanFlac(long mergedOffset, long freshOffset, byte[] bytes) {
        if (!this.linear.flacHeaderComplete) {
            parseFlacMetadata(mergedOffset, bytes);
        }
        if (!this.linear.flacHeaderComplete || this.linear.sampleRate <= 0) {
            return;
        }
        for (int i = Math.max(0, (int) (this.linear.flacFrameStart - mergedOffset)); i + 6 <= bytes.length; i++) {
            FlacFrame frame = parseFlacFrame(bytes, i);
            if (frame == null || mergedOffset + i < this.linear.scanPosition) {
                continue;
            }
            long frameOffset = mergedOffset + i;
            if (frameOffset < freshOffset && i + 16 <= this.linear.tailLength) {
                continue;
            }
            long sampleNumber = frame.variableBlock ? frame.number : frame.number * frame.blockSize;
            long timeMs = sampleNumber * 1000L / this.linear.sampleRate;
            this.index.addPoint(timeMs, frameOffset);
            this.linear.scanPosition = frameOffset + 2L;
            i++;
        }
    }

    private void parseFlacMetadata(long mergedOffset, byte[] bytes) {
        if (mergedOffset != 0L || bytes.length < 8 || !SeekParsing.asciiEquals(bytes, 0, "fLaC")) {
            return;
        }
        long position = 4L;
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.writeBytes(new byte[]{'f', 'L', 'a', 'C'});
        boolean last = false;
        while (!last && position + 4L <= bytes.length) {
            int pos = (int) position;
            last = (bytes[pos] & 0x80) != 0;
            int type = bytes[pos] & 0x7F;
            int length = (SeekParsing.u8(bytes, pos + 1) << 16) | (SeekParsing.u8(bytes, pos + 2) << 8) | SeekParsing.u8(bytes, pos + 3);
            if (position + 4L + length > bytes.length) {
                return;
            }
            header.write(bytes, pos, 4 + length);
            int data = pos + 4;
            if (type == 0 && length >= 34) {
                long packed = ((long) SeekParsing.u8(bytes, data + 10) << 56)
                        | ((long) SeekParsing.u8(bytes, data + 11) << 48)
                        | ((long) SeekParsing.u8(bytes, data + 12) << 40)
                        | ((long) SeekParsing.u8(bytes, data + 13) << 32)
                        | ((long) SeekParsing.u8(bytes, data + 14) << 24)
                        | ((long) SeekParsing.u8(bytes, data + 15) << 16)
                        | ((long) SeekParsing.u8(bytes, data + 16) << 8)
                        | SeekParsing.u8(bytes, data + 17);
                this.linear.sampleRate = (int) ((packed >>> 44) & 0xFFFFF);
                long totalSamples = packed & 0x0FFFFFFFFFL;
                if (this.linear.sampleRate > 0 && totalSamples > 0L) {
                    this.index.setDurationMilliseconds(totalSamples * 1000L / this.linear.sampleRate);
                }
            } else if (type == 3 && this.linear.sampleRate > 0) {
                for (int i = 0; i + 18 <= length; i += 18) {
                    long sampleNumber = SeekParsing.u64be(bytes, data + i);
                    long streamOffset = SeekParsing.u64be(bytes, data + i + 8);
                    if (sampleNumber != 0xFFFFFFFFFFFFFFFFL) {
                        this.linear.flacSeekSamples.add(sampleNumber);
                        this.linear.flacSeekOffsets.add(streamOffset);
                    }
                }
            }
            position += 4L + length;
        }
        if (last) {
            this.linear.flacHeaderComplete = true;
            this.linear.flacFrameStart = position;
            this.linear.scanPosition = position;
            this.index.setPrefixBytes(header.toByteArray());
            if (this.linear.sampleRate > 0) {
                for (int i = 0; i < this.linear.flacSeekSamples.size(); i++) {
                    long sampleNumber = this.linear.flacSeekSamples.get(i);
                    long streamOffset = this.linear.flacSeekOffsets.get(i);
                    this.index.addPoint(sampleNumber * 1000L / this.linear.sampleRate, this.linear.flacFrameStart + streamOffset);
                }
            }
        }
    }

    private void scanOgg(long mergedOffset, long freshOffset, byte[] bytes) {
        for (int i = 0; i + 27 <= bytes.length; i++) {
            if (!SeekParsing.asciiEquals(bytes, i, "OggS")) {
                continue;
            }
            int segments = SeekParsing.u8(bytes, i + 26);
            if (i + 27 + segments > bytes.length) {
                break;
            }
            int payload = 0;
            for (int j = 0; j < segments; j++) {
                payload += SeekParsing.u8(bytes, i + 27 + j);
            }
            int pageSize = 27 + segments + payload;
            if (i + pageSize > bytes.length) {
                break;
            }
            long pageOffset = mergedOffset + i;
            if (pageOffset < freshOffset && i + pageSize <= this.linear.tailLength) {
                continue;
            }
            int serial = (int) SeekParsing.u32le(bytes, i + 14);
            long granule = SeekParsing.u64le(bytes, i + 6);
            int payloadOffset = i + 27 + segments;
            if (this.linear.oggSerial == 0) {
                this.linear.oggSerial = serial;
            }
            if (serial == this.linear.oggSerial) {
                detectOggCodec(bytes, payloadOffset);
                if (!this.linear.oggHeadersComplete && granule <= 0L) {
                    this.linear.oggInit.write(bytes, i, pageSize);
                } else if (!this.linear.oggHeadersComplete) {
                    this.linear.oggHeadersComplete = true;
                    this.index.setPrefixBytes(this.linear.oggInit.toByteArray());
                }
                if (this.linear.sampleRate > 0 && granule >= 0L) {
                    long startGranule = Math.max(0L, this.linear.oggLastGranule);
                    this.index.addPoint(startGranule * 1000L / this.linear.sampleRate, pageOffset);
                    this.linear.oggLastGranule = granule;
                    this.index.setDurationMilliseconds(granule * 1000L / this.linear.sampleRate);
                }
            }
            i += Math.max(0, pageSize - 1);
        }
    }

    private void detectOggCodec(byte[] bytes, int payloadOffset) {
        if (this.linear.sampleRate > 0 || payloadOffset + 16 > bytes.length) {
            return;
        }
        if (SeekParsing.asciiEquals(bytes, payloadOffset, "OpusHead")) {
            this.linear.sampleRate = 48000;
        } else if (bytes[payloadOffset] == 0x01 && SeekParsing.asciiEquals(bytes, payloadOffset + 1, "vorbis")
                && payloadOffset + 16 <= bytes.length) {
            this.linear.sampleRate = (int) SeekParsing.u32le(bytes, payloadOffset + 12);
        }
    }

    private void scanMp4(long mergedOffset, byte[] bytes) {
        if (this.mp4.parsed) {
            return;
        }
        parseTopLevelMp4Boxes(mergedOffset, bytes);
        if (this.mp4.ftyp != null && this.mp4.moov != null && this.mp4.mdat != null) {
            try {
                byte[] ftypBytes = this.source.readAt(this.mp4.ftyp.start, (int) this.mp4.ftyp.size);
                byte[] moovBytes = this.source.readAt(this.mp4.moov.start, (int) this.mp4.moov.size);
                installMp4SeekMap(ftypBytes, moovBytes, this.mp4.moov.start, this.mp4.mdat == null ? -1L : this.mp4.mdat.dataStart());
            } catch (IOException ignored) {
            }
        }
    }

    private void ensureMp4Parsed(ProgressiveMediaDataSource source) throws IOException {
        if (this.mp4.parsed) {
            return;
        }
        if (source.hasLength()) {
            long position = 0L;
            while (position + 8L <= source.length()) {
                byte[] header = source.readAt(position, 16);
                Box box = readBoxHeader(header, position, source.length());
                if (box == null) {
                    break;
                }
                rememberMp4Box(box);
                if ("ftyp".equals(box.type)) {
                    this.mp4.ftypBytes = source.readAt(box.start, (int) box.size);
                } else if ("moov".equals(box.type)) {
                    this.mp4.moovBytes = source.readAt(box.start, (int) box.size);
                }
                if (this.mp4.ftypBytes != null && this.mp4.moovBytes != null && this.mp4.mdat != null) {
                    installMp4SeekMap(this.mp4.ftypBytes, this.mp4.moovBytes, this.mp4.moov.start, this.mp4.mdat.dataStart());
                    break;
                }
                position += box.size;
            }
        }
    }

    private void parseTopLevelMp4Boxes(long mergedOffset, byte[] bytes) {
        long position = Math.max(0L, this.mp4.scanPosition - mergedOffset);
        while (position + 8L <= bytes.length) {
            Box box = readBoxHeader(bytes, (int) position, mergedOffset + position, Long.MAX_VALUE);
            if (box == null || box.size <= 0L) {
                break;
            }
            rememberMp4Box(box);
            this.mp4.scanPosition = box.start + box.size;
            position += box.size;
        }
    }

    private void rememberMp4Box(Box box) {
        if ("ftyp".equals(box.type)) {
            this.mp4.ftyp = box;
        } else if ("moov".equals(box.type)) {
            this.mp4.moov = box;
        } else if ("mdat".equals(box.type)) {
            this.mp4.mdat = box;
        }
    }

    private void installMp4SeekMap(byte[] ftypBytes, byte[] moovBytes, long moovStart, long mdatDataStart) throws IOException {
        if (this.mp4.parsed || ftypBytes == null || moovBytes == null || mdatDataStart < 0L) {
            return;
        }
        Mp4SeekMap seekMap = Mp4SeekMap.parse(ftypBytes, moovBytes, moovStart, this.source.length());
        if (seekMap != null) {
            this.index.setDelegate(seekMap);
            this.mp4.parsed = true;
        }
    }

    private byte[] merged(byte[] data, int dataOffset, int length) {
        byte[] merged = new byte[this.linear.tailLength + length];
        System.arraycopy(this.linear.tail, 0, merged, 0, this.linear.tailLength);
        System.arraycopy(data, dataOffset, merged, this.linear.tailLength, length);
        return merged;
    }

    private void saveTail(byte[] data, int dataOffset, int length) {
        int keep = Math.min(65536, length);
        if (this.linear.tail.length < keep) {
            this.linear.tail = new byte[keep];
        }
        this.linear.tailLength = keep;
        System.arraycopy(data, dataOffset + length - keep, this.linear.tail, 0, keep);
    }

    private static Mp3Frame parseMp3(byte[] bytes, int offset) {
        int b0 = u8(bytes, offset);
        int b1 = u8(bytes, offset + 1);
        int b2 = u8(bytes, offset + 2);
        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) return null;
        int versionBits = (b1 >>> 3) & 0x03;
        int layerBits = (b1 >>> 1) & 0x03;
        int bitrateIndex = (b2 >>> 4) & 0x0F;
        int sampleRateIndex = (b2 >>> 2) & 0x03;
        int padding = (b2 >>> 1) & 0x01;
        if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null;
        int bitrate = mp3Bitrate(versionBits, layerBits, bitrateIndex);
        int sampleRate = mp3SampleRate(versionBits, sampleRateIndex);
        if (bitrate <= 0 || sampleRate <= 0) return null;
        int frameSize;
        int samplesPerFrame;
        if (layerBits == 3) {
            frameSize = ((12 * bitrate / sampleRate) + padding) * 4;
            samplesPerFrame = 384;
        } else if (layerBits == 2) {
            frameSize = (144 * bitrate / sampleRate) + padding;
            samplesPerFrame = 1152;
        } else {
            int coefficient = versionBits == 3 ? 144 : 72;
            frameSize = (coefficient * bitrate / sampleRate) + padding;
            samplesPerFrame = versionBits == 3 ? 1152 : 576;
        }
        int channelMode = (u8(bytes, offset + 3) >>> 6) & 0x03;
        return frameSize >= 4 ? new Mp3Frame(frameSize, sampleRate, samplesPerFrame, bitrate, versionBits, channelMode) : null;
    }

    private static int mp3Bitrate(int versionBits, int layerBits, int bitrateIndex) {
        int[][] mpeg1Kbps = {
                {},
                {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
                {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
                {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448}
        };
        int[][] mpeg2Kbps = {
                {},
                {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
                {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
                {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256}
        };
        int[][] table = versionBits == 3 ? mpeg1Kbps : mpeg2Kbps;
        return table[layerBits][bitrateIndex] * 1000;
    }

    private static int mp3SampleRate(int versionBits, int sampleRateIndex) {
        int[][] sampleRates = {
                {11025, 12000, 8000},
                {},
                {22050, 24000, 16000},
                {44100, 48000, 32000}
        };
        return sampleRates[versionBits][sampleRateIndex];
    }

    private static AdtsFrame parseAdts(byte[] bytes, int offset) {
        int b0 = u8(bytes, offset);
        int b1 = u8(bytes, offset + 1);
        if (b0 != 0xFF || (b1 & 0xF0) != 0xF0) return null;
        int sampleRateIndex = (u8(bytes, offset + 2) >>> 2) & 0x0F;
        int[] sampleRates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        if (sampleRateIndex >= sampleRates.length) return null;
        int frameLength = ((u8(bytes, offset + 3) & 0x03) << 11) | (u8(bytes, offset + 4) << 3) | ((u8(bytes, offset + 5) >>> 5) & 0x07);
        int rawBlocks = (u8(bytes, offset + 6) & 0x03) + 1;
        return frameLength >= 7 ? new AdtsFrame(frameLength, sampleRates[sampleRateIndex], rawBlocks) : null;
    }

    private static FlacFrame parseFlacFrame(byte[] bytes, int offset) {
        if (u8(bytes, offset) != 0xFF || (u8(bytes, offset + 1) & 0xFC) != 0xF8) {
            return null;
        }
        boolean variable = (u8(bytes, offset + 1) & 0x01) != 0;
        int blockSizeCode = (u8(bytes, offset + 2) >>> 4) & 0x0F;
        Utf8Number number = readUtf8Number(bytes, offset + 4);
        if (number == null) {
            return null;
        }
        int cursor = offset + 4 + number.length;
        int blockSize = switch (blockSizeCode) {
            case 1 -> 192;
            case 2, 3, 4, 5 -> 576 << (blockSizeCode - 2);
            case 6 -> cursor < bytes.length ? u8(bytes, cursor) + 1 : -1;
            case 7 -> cursor + 1 < bytes.length ? SeekParsing.u16be(bytes, cursor) + 1 : -1;
            case 8, 9, 10, 11, 12, 13, 14, 15 -> 256 << (blockSizeCode - 8);
            default -> -1;
        };
        return blockSize > 0 ? new FlacFrame(variable, number.value, blockSize) : null;
    }

    private static Utf8Number readUtf8Number(byte[] bytes, int offset) {
        if (offset >= bytes.length) return null;
        int first = u8(bytes, offset);
        int length;
        long value;
        if ((first & 0x80) == 0) {
            length = 1;
            value = first;
        } else if ((first & 0xE0) == 0xC0) {
            length = 2;
            value = first & 0x1F;
        } else if ((first & 0xF0) == 0xE0) {
            length = 3;
            value = first & 0x0F;
        } else if ((first & 0xF8) == 0xF0) {
            length = 4;
            value = first & 0x07;
        } else if ((first & 0xFC) == 0xF8) {
            length = 5;
            value = first & 0x03;
        } else if ((first & 0xFE) == 0xFC) {
            length = 6;
            value = first & 0x01;
        } else {
            return null;
        }
        if (offset + length > bytes.length) return null;
        for (int i = 1; i < length; i++) {
            int next = u8(bytes, offset + i);
            if ((next & 0xC0) != 0x80) return null;
            value = (value << 6) | (next & 0x3F);
        }
        return new Utf8Number(value, length);
    }

    private static Box readBoxHeader(byte[] bytes, int offset, long absoluteOffset, long parentEnd) {
        if (offset + 8 > bytes.length) {
            return null;
        }
        long size = SeekParsing.u32be(bytes, offset);
        String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
        int headerSize = 8;
        if (size == 1L) {
            if (offset + 16 > bytes.length) {
                return null;
            }
            size = SeekParsing.u64be(bytes, offset + 8);
            headerSize = 16;
        } else if (size == 0L && parentEnd != Long.MAX_VALUE) {
            size = parentEnd - absoluteOffset;
        }
        if (size < headerSize || absoluteOffset + size > parentEnd) {
            return null;
        }
        return new Box(absoluteOffset, size, headerSize, type);
    }

    private static Box readBoxHeader(byte[] header, long absoluteOffset, long parentEnd) {
        return readBoxHeader(header, 0, absoluteOffset, parentEnd);
    }

    private static int u8(byte[] bytes, int offset) {
        return bytes[offset] & 0xFF;
    }

    private static final class LinearState {
        private byte[] tail = new byte[0];
        private int tailLength;
        private long scanPosition;
        private long samples;
        private int sampleRate = -1;
        private boolean wavConfigured;
        private boolean flacHeaderComplete;
        private long flacFrameStart = -1L;
        private final List<Long> flacSeekSamples = new ArrayList<>();
        private final List<Long> flacSeekOffsets = new ArrayList<>();
        private int oggSerial;
        private long oggLastGranule = -1L;
        private boolean oggHeadersComplete;
        private final ByteArrayOutputStream oggInit = new ByteArrayOutputStream();
        private boolean mp3SeekHeaderChecked;
    }

    private static final class Mp4State {
        private long scanPosition;
        private Box ftyp;
        private Box moov;
        private Box mdat;
        private byte[] ftypBytes;
        private byte[] moovBytes;
        private boolean parsed;
    }

    private record Mp3Frame(int frameSize, int sampleRate, int samplesPerFrame, int bitrate, int versionBits, int channelMode) {
    }

    private record AdtsFrame(int frameSize, int sampleRate, int rawBlocks) {
    }

    private record FlacFrame(boolean variableBlock, long number, int blockSize) {
    }

    private record Utf8Number(long value, int length) {
    }

    private record Box(long start, long size, int headerSize, String type) {
        private long dataStart() {
            return this.start + this.headerSize;
        }
    }

    private static final class Mp3SeekMap implements SeekMap {
        private final List<Mp3Point> points = new ArrayList<>();
        private long durationMilliseconds = -1L;
        private int sampleRate = -1;

        private Mp3SeekMap() {
            this.points.add(new Mp3Point(0L, 0L));
        }

        private synchronized void addPoint(long samples, long byteOffset, int sampleRate) {
            if (samples < 0L || byteOffset < 0L || sampleRate <= 0) {
                return;
            }
            if (this.sampleRate <= 0) {
                this.sampleRate = sampleRate;
            }
            for (Mp3Point point : this.points) {
                if (point.samples == samples || point.byteOffset == byteOffset) {
                    return;
                }
            }
            this.points.add(new Mp3Point(samples, byteOffset));
            this.points.sort(Comparator.comparingLong(Mp3Point::samples));
        }

        private synchronized void setDurationMilliseconds(long durationMilliseconds) {
            this.durationMilliseconds = durationMilliseconds;
        }

        @Override
        public synchronized boolean isSeekable() {
            return this.points.size() > 1;
        }

        @Override
        public synchronized long getDurationMilliseconds() {
            return this.durationMilliseconds;
        }

        @Override
        public synchronized SeekPoint timeToSeekPoint(long timeMilliseconds) {
            long target = Math.max(0L, timeMilliseconds);
            if (this.sampleRate <= 0) {
                return SeekPoint.at(0L, 0L).withRequestedTime(target);
            }
            long targetSamples = target * this.sampleRate / 1000L;
            int low = 0;
            int high = this.points.size() - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (this.points.get(mid).samples <= targetSamples) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            Mp3Point point = this.points.get(Math.max(0, high));
            long actualMs = point.samples * 1000L / this.sampleRate;
            return SeekPoint.at(actualMs, point.byteOffset).withRequestedTime(target);
        }

        @Override
        public String getFormatName() {
            return "mp3";
        }

        @Override
        public InputStream openSeekInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
            return SeekMap.super.openSeekInputStream(source, seekPoint);
        }

        private record Mp3Point(long samples, long byteOffset) {
        }
    }

    private record Mp3HeaderSeekMap(long durationMilliseconds) {
        private long getDurationMilliseconds() {
            return this.durationMilliseconds;
        }

        private static Mp3HeaderSeekMap vbr(long durationMs) {
            return new Mp3HeaderSeekMap(durationMs);
        }

        private static Mp3HeaderSeekMap info(long durationMs) {
            return new Mp3HeaderSeekMap(durationMs);
        }

        private static Mp3HeaderSeekMap vbri(long durationMs) {
            return new Mp3HeaderSeekMap(durationMs);
        }
    }

    private static final class Mp4SeekMap implements SeekMap {
        private final byte[] ftypBytes;
        private final byte[] moovBytes;
        private final BoxNode moovTree;
        private final AudioTrack track;
        private final long sourceLength;
        private final long durationMs;

        private Mp4SeekMap(byte[] ftypBytes, byte[] moovBytes, BoxNode moovTree, AudioTrack track, long sourceLength, long durationMs) {
            this.ftypBytes = ftypBytes;
            this.moovBytes = moovBytes;
            this.moovTree = moovTree;
            this.track = track;
            this.sourceLength = sourceLength;
            this.durationMs = durationMs;
        }

        private static Mp4SeekMap parse(byte[] ftypBytes, byte[] moovBytes, long moovStart, long sourceLength) {
            BoxNode moov = parseBoxTree(moovBytes, moovStart, 0, moovBytes.length, null);
            AudioTrack track = findAudioTrack(moovBytes, moov);
            if (track == null || track.timescale <= 0L || track.sampleSizes.isEmpty()
                    || track.chunkOffsets.isEmpty() || track.stts.isEmpty() || track.stsc.isEmpty()) {
                return null;
            }
            track.buildChunks();
            long duration = track.durationUnits > 0L ? track.durationUnits * 1000L / track.timescale : -1L;
            return new Mp4SeekMap(ftypBytes, moovBytes, moov, track, sourceLength, duration);
        }

        @Override
        public boolean isSeekable() {
            return true;
        }

        @Override
        public long getDurationMilliseconds() {
            return this.durationMs;
        }

        @Override
        public SeekPoint timeToSeekPoint(long timeMilliseconds) {
            long targetUnits = Math.max(0L, timeMilliseconds) * this.track.timescale / 1000L;
            int sampleIndex = this.track.sampleIndexForTime(targetUnits);
            long actualUnits = this.track.timeForSample(sampleIndex);
            long actualMs = actualUnits * 1000L / this.track.timescale;
            long byteOffset = this.track.byteOffsetForSample(sampleIndex);
            return new Mp4SeekPoint(actualMs, byteOffset, Math.max(0L, timeMilliseconds - actualMs), sampleIndex);
        }

        @Override
        public String getFormatName() {
            return "mp4-aac";
        }

        @Override
        public java.io.InputStream openSeekInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
            int sampleIndex = seekPoint instanceof Mp4SeekPoint mp4SeekPoint ? mp4SeekPoint.sampleIndex : 0;
            long dataOffset = this.track.byteOffsetForSample(sampleIndex);
            byte[] moov = buildTrimmedMoov(sampleIndex, 0L);
            long mediaBytes = this.sourceLength > 0L ? Math.max(0L, this.sourceLength - dataOffset) : 0L;
            byte[] mdatHeader = buildMdatHeader(mediaBytes);
            long mediaDataStart = this.ftypBytes.length + moov.length + mdatHeader.length;
            moov = buildTrimmedMoov(sampleIndex, mediaDataStart);
            mdatHeader = buildMdatHeader(mediaBytes);
            ByteArrayOutputStream prefix = new ByteArrayOutputStream();
            prefix.writeBytes(this.ftypBytes);
            prefix.writeBytes(moov);
            prefix.writeBytes(mdatHeader);
            return new PrefixInputStream(prefix.toByteArray(), source.openStream(dataOffset));
        }

        private byte[] buildTrimmedMoov(int sampleIndex, long mediaDataStart) {
            return rebuildBox(this.moovTree, this.track.buildReplacements(sampleIndex, mediaDataStart));
        }

        private byte[] rebuildBox(BoxNode box, Map<String, byte[]> replacements) {
            byte[] replacement = replacements.get(box.path());
            if (replacement != null) {
                return replacement;
            }
            if (box.isContainer()) {
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                for (BoxNode child : box.children) {
                    payload.writeBytes(rebuildBox(child, replacements));
                }
                return buildBox(box.type, payload.toByteArray());
            }
            byte[] original = new byte[(int) box.size];
            System.arraycopy(this.moovBytes, (int) (box.start - this.moovTree.start), original, 0, original.length);
            return original;
        }
    }

    private static final class Mp4SeekPoint extends SeekPoint {
        private final int sampleIndex;

        private Mp4SeekPoint(long timeMilliseconds, long byteOffset, long pcmSkipMilliseconds, int sampleIndex) {
            super(timeMilliseconds, byteOffset, pcmSkipMilliseconds);
            this.sampleIndex = sampleIndex;
        }
    }

    private static BoxNode parseBoxTree(byte[] bytes, long absoluteStart, int start, int end, BoxNode parent) {
        Box header = readBoxHeader(bytes, start, absoluteStart + start, absoluteStart + end);
        BoxNode node = null;
        if (header != null) {
            node = new BoxNode(header.start, header.size, header.headerSize, header.type, parent);
            if (node.isContainer()) {
                int pos = start + header.headerSize;
                int boxEnd = start + (int) header.size;
                while (pos + 8 <= boxEnd) {
                    Box childHeader = readBoxHeader(bytes, pos, absoluteStart + pos, absoluteStart + boxEnd);
                    if (childHeader == null) {
                        break;
                    }
                    node.children.add(parseBoxTree(bytes, absoluteStart, pos, pos + (int) childHeader.size, node));
                    pos += (int) childHeader.size;
                }
            }
        }
        return node;
    }

    private static AudioTrack findAudioTrack(byte[] moovBytes, BoxNode moov) {
        for (BoxNode trak : moov.childrenOf("trak")) {
            BoxNode mdia = trak.child("mdia");
            BoxNode hdlr = mdia == null ? null : mdia.child("hdlr");
            if (hdlr == null || hdlr.dataOffset(moov) + 12 > moovBytes.length
                    || !SeekParsing.asciiEquals(moovBytes, hdlr.dataOffset(moov) + 8, "soun")) {
                continue;
            }
            AudioTrack track = new AudioTrack(trak);
            BoxNode mdhd = mdia.child("mdhd");
            if (mdhd != null) {
                parseMdhd(moovBytes, moov, mdhd, track);
            }
            BoxNode minf = mdia.child("minf");
            BoxNode stbl = minf == null ? null : minf.child("stbl");
            if (stbl != null) {
                parseStbl(moovBytes, moov, stbl, track);
                return track;
            }
        }
        return null;
    }

    private static void parseMdhd(byte[] bytes, BoxNode root, BoxNode mdhd, AudioTrack track) {
        int data = mdhd.dataOffset(root);
        if (data + 20 > bytes.length) {
            return;
        }
        int version = SeekParsing.u8(bytes, data);
        if (version == 1 && data + 32 <= bytes.length) {
            track.timescale = SeekParsing.u32be(bytes, data + 20);
            track.durationUnits = SeekParsing.u64be(bytes, data + 24);
        } else {
            track.timescale = SeekParsing.u32be(bytes, data + 12);
            track.durationUnits = SeekParsing.u32be(bytes, data + 16);
        }
    }

    private static void parseStbl(byte[] bytes, BoxNode root, BoxNode stbl, AudioTrack track) {
        BoxNode stts = stbl.child("stts");
        if (stts != null) {
            int data = stts.dataOffset(root);
            int count = data + 8 <= bytes.length ? (int) SeekParsing.u32be(bytes, data + 4) : 0;
            for (int i = 0; i < count && data + 8 + i * 8 + 8 <= bytes.length; i++) {
                track.stts.add(new SttsEntry((int) SeekParsing.u32be(bytes, data + 8 + i * 8),
                        SeekParsing.u32be(bytes, data + 12 + i * 8)));
            }
        }
        BoxNode stsc = stbl.child("stsc");
        if (stsc != null) {
            int data = stsc.dataOffset(root);
            int count = data + 8 <= bytes.length ? (int) SeekParsing.u32be(bytes, data + 4) : 0;
            for (int i = 0; i < count && data + 8 + i * 12 + 12 <= bytes.length; i++) {
                track.stsc.add(new StscEntry((int) SeekParsing.u32be(bytes, data + 8 + i * 12),
                        (int) SeekParsing.u32be(bytes, data + 12 + i * 12),
                        (int) SeekParsing.u32be(bytes, data + 16 + i * 12)));
            }
        }
        BoxNode stsz = stbl.child("stsz");
        if (stsz != null) {
            int data = stsz.dataOffset(root);
            if (data + 12 <= bytes.length) {
                long constantSize = SeekParsing.u32be(bytes, data + 4);
                int sampleCount = (int) SeekParsing.u32be(bytes, data + 8);
                for (int i = 0; i < sampleCount; i++) {
                    if (constantSize > 0L) {
                        track.sampleSizes.add((int) constantSize);
                    } else if (data + 12 + i * 4 + 4 <= bytes.length) {
                        track.sampleSizes.add((int) SeekParsing.u32be(bytes, data + 12 + i * 4));
                    }
                }
            }
        }
        BoxNode co64 = stbl.child("co64");
        BoxNode stco = stbl.child("stco");
        if (co64 != null) {
            int data = co64.dataOffset(root);
            int count = data + 8 <= bytes.length ? (int) SeekParsing.u32be(bytes, data + 4) : 0;
            for (int i = 0; i < count && data + 8 + i * 8 + 8 <= bytes.length; i++) {
                track.chunkOffsets.add(SeekParsing.u64be(bytes, data + 8 + i * 8));
            }
        } else if (stco != null) {
            int data = stco.dataOffset(root);
            int count = data + 8 <= bytes.length ? (int) SeekParsing.u32be(bytes, data + 4) : 0;
            for (int i = 0; i < count && data + 8 + i * 4 + 4 <= bytes.length; i++) {
                track.chunkOffsets.add(SeekParsing.u32be(bytes, data + 8 + i * 4));
            }
        }
    }

    private static byte[] buildBox(String type, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU32(out, payload.length + 8L);
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] buildFullBox(String type, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.writeBytes(payload);
        return buildBox(type, out.toByteArray());
    }

    private static byte[] buildMdatHeader(long mediaBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (mediaBytes > 0L && mediaBytes + 8L <= 0xFFFFFFFFL) {
            writeU32(out, mediaBytes + 8L);
            out.writeBytes("mdat".getBytes(StandardCharsets.US_ASCII));
        } else if (mediaBytes > 0L) {
            writeU32(out, 1L);
            out.writeBytes("mdat".getBytes(StandardCharsets.US_ASCII));
            writeU64(out, mediaBytes + 16L);
        } else {
            writeU32(out, 0L);
            out.writeBytes("mdat".getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    private static void writeU64(ByteArrayOutputStream out, long value) {
        writeU32(out, value >>> 32);
        writeU32(out, value);
    }

    private static final class BoxNode {
        private final long start;
        private final long size;
        private final int headerSize;
        private final String type;
        private final BoxNode parent;
        private final List<BoxNode> children = new ArrayList<>();

        private BoxNode(long start, long size, int headerSize, String type, BoxNode parent) {
            this.start = start;
            this.size = size;
            this.headerSize = headerSize;
            this.type = type;
            this.parent = parent;
        }

        private boolean isContainer() {
            return "moov".equals(this.type) || "trak".equals(this.type) || "mdia".equals(this.type)
                    || "minf".equals(this.type) || "stbl".equals(this.type) || "edts".equals(this.type)
                    || "dinf".equals(this.type) || "udta".equals(this.type);
        }

        private int dataOffset(BoxNode root) {
            return (int) (this.start - root.start + this.headerSize);
        }

        private BoxNode child(String type) {
            for (BoxNode child : this.children) {
                if (type.equals(child.type)) {
                    return child;
                }
            }
            return null;
        }

        private List<BoxNode> childrenOf(String type) {
            List<BoxNode> result = new ArrayList<>();
            for (BoxNode child : this.children) {
                if (type.equals(child.type)) {
                    result.add(child);
                }
            }
            return result;
        }

        private String path() {
            return this.parent == null ? this.type + "@" + this.start : this.parent.path() + "/" + this.type + "@" + this.start;
        }
    }

    private static final class AudioTrack {
        private final BoxNode trak;
        private long timescale = -1L;
        private long durationUnits = -1L;
        private final List<SttsEntry> stts = new ArrayList<>();
        private final List<StscEntry> stsc = new ArrayList<>();
        private final List<Integer> sampleSizes = new ArrayList<>();
        private final List<Long> chunkOffsets = new ArrayList<>();
        private final List<ChunkInfo> chunks = new ArrayList<>();

        private AudioTrack(BoxNode trak) {
            this.trak = trak;
        }

        private int totalSamples() {
            return this.sampleSizes.size();
        }

        private void buildChunks() {
            int sampleIndex = 0;
            this.chunks.clear();
            for (int chunk = 1; chunk <= this.chunkOffsets.size(); chunk++) {
                StscEntry entry = stscForChunk(chunk);
                int samples = Math.min(entry.samplesPerChunk, this.sampleSizes.size() - sampleIndex);
                if (samples <= 0) {
                    break;
                }
                this.chunks.add(new ChunkInfo(sampleIndex, samples, entry.sampleDescriptionIndex, this.chunkOffsets.get(chunk - 1)));
                sampleIndex += samples;
            }
        }

        private StscEntry stscForChunk(int chunk) {
            StscEntry current = this.stsc.get(0);
            for (StscEntry entry : this.stsc) {
                if (entry.firstChunk <= chunk) {
                    current = entry;
                } else {
                    break;
                }
            }
            return current;
        }

        private int sampleIndexForTime(long targetUnits) {
            long time = 0L;
            int sample = 0;
            for (SttsEntry entry : this.stts) {
                long duration = entry.sampleCount * entry.sampleDelta;
                if (time + duration > targetUnits) {
                    int inside = (int) Math.max(0L, (targetUnits - time) / entry.sampleDelta);
                    return Math.min(this.totalSamples() - 1, sample + inside);
                }
                time += duration;
                sample += entry.sampleCount;
            }
            return Math.max(0, this.totalSamples() - 1);
        }

        private long timeForSample(int sampleIndex) {
            long time = 0L;
            int sample = 0;
            for (SttsEntry entry : this.stts) {
                if (sample + entry.sampleCount > sampleIndex) {
                    return time + (long) (sampleIndex - sample) * entry.sampleDelta;
                }
                time += entry.sampleCount * entry.sampleDelta;
                sample += entry.sampleCount;
            }
            return time;
        }

        private long byteOffsetForSample(int sampleIndex) {
            ChunkInfo chunk = chunkForSample(sampleIndex);
            long offset = chunk.chunkOffset;
            for (int i = chunk.firstSample; i < sampleIndex; i++) {
                offset += this.sampleSizes.get(i);
            }
            return offset;
        }

        private ChunkInfo chunkForSample(int sampleIndex) {
            for (ChunkInfo chunk : this.chunks) {
                if (sampleIndex >= chunk.firstSample && sampleIndex < chunk.firstSample + chunk.sampleCount) {
                    return chunk;
                }
            }
            return this.chunks.get(this.chunks.size() - 1);
        }

        private Map<String, byte[]> buildReplacements(int sampleIndex, long mediaDataStart) {
            Map<String, byte[]> replacements = new HashMap<>();
            replacements.put(pathFor("stts"), buildStts(sampleIndex));
            replacements.put(pathFor("stsz"), buildStsz(sampleIndex));
            replacements.put(pathFor("stsc"), buildStsc(sampleIndex));
            replacements.put(pathFor("stco"), buildStco(sampleIndex, mediaDataStart));
            replacements.put(pathFor("co64"), buildCo64(sampleIndex, mediaDataStart));
            replacements.values().removeIf(Objects::isNull);
            return replacements;
        }

        private String pathFor(String type) {
            BoxNode mdia = this.trak.child("mdia");
            BoxNode minf = mdia == null ? null : mdia.child("minf");
            BoxNode stbl = minf == null ? null : minf.child("stbl");
            BoxNode box = stbl == null ? null : stbl.child(type);
            return box == null ? "" : box.path();
        }

        private byte[] buildStts(int sampleIndex) {
            List<SttsEntry> entries = new ArrayList<>();
            int sample = 0;
            for (SttsEntry entry : this.stts) {
                int start = Math.max(sample, sampleIndex);
                int end = Math.min(sample + entry.sampleCount, this.totalSamples());
                if (end > start) {
                    entries.add(new SttsEntry(end - start, entry.sampleDelta));
                }
                sample += entry.sampleCount;
            }
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeU32(payload, entries.size());
            for (SttsEntry entry : entries) {
                writeU32(payload, entry.sampleCount);
                writeU32(payload, entry.sampleDelta);
            }
            return buildFullBox("stts", payload.toByteArray());
        }

        private byte[] buildStsz(int sampleIndex) {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeU32(payload, 0L);
            writeU32(payload, this.totalSamples() - sampleIndex);
            for (int i = sampleIndex; i < this.totalSamples(); i++) {
                writeU32(payload, this.sampleSizes.get(i));
            }
            return buildFullBox("stsz", payload.toByteArray());
        }

        private byte[] buildStsc(int sampleIndex) {
            List<ChunkInfo> remaining = remainingChunks(sampleIndex);
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeU32(payload, remaining.size());
            for (int i = 0; i < remaining.size(); i++) {
                ChunkInfo chunk = remaining.get(i);
                int samples = chunk.sampleCount;
                if (i == 0) {
                    samples -= sampleIndex - chunk.firstSample;
                }
                writeU32(payload, i + 1L);
                writeU32(payload, samples);
                writeU32(payload, chunk.sampleDescriptionIndex);
            }
            return buildFullBox("stsc", payload.toByteArray());
        }

        private byte[] buildStco(int sampleIndex, long mediaDataStart) {
            if (pathFor("stco").isEmpty()) return null;
            List<Long> offsets = virtualChunkOffsets(sampleIndex, mediaDataStart);
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeU32(payload, offsets.size());
            for (long offset : offsets) {
                writeU32(payload, offset);
            }
            return buildFullBox("stco", payload.toByteArray());
        }

        private byte[] buildCo64(int sampleIndex, long mediaDataStart) {
            if (pathFor("co64").isEmpty()) return null;
            List<Long> offsets = virtualChunkOffsets(sampleIndex, mediaDataStart);
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeU32(payload, offsets.size());
            for (long offset : offsets) {
                writeU64(payload, offset);
            }
            return buildFullBox("co64", payload.toByteArray());
        }

        private List<ChunkInfo> remainingChunks(int sampleIndex) {
            List<ChunkInfo> remaining = new ArrayList<>();
            for (ChunkInfo chunk : this.chunks) {
                if (chunk.firstSample + chunk.sampleCount > sampleIndex) {
                    remaining.add(chunk);
                }
            }
            return remaining;
        }

        private List<Long> virtualChunkOffsets(int sampleIndex, long mediaDataStart) {
            long firstByte = byteOffsetForSample(sampleIndex);
            List<Long> offsets = new ArrayList<>();
            for (ChunkInfo chunk : remainingChunks(sampleIndex)) {
                offsets.add(offsets.isEmpty() ? mediaDataStart : mediaDataStart + (chunk.chunkOffset - firstByte));
            }
            return offsets;
        }
    }

    private record SttsEntry(int sampleCount, long sampleDelta) {
    }

    private record StscEntry(int firstChunk, int samplesPerChunk, int sampleDescriptionIndex) {
    }

    private record ChunkInfo(int firstSample, int sampleCount, int sampleDescriptionIndex, long chunkOffset) {
    }
}
