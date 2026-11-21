package com.hongguo.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Minimal CENC AES-128-CTR MP4 decryptor for hongguo progressive files. */
public final class CencMp4 {
    private static final int MOOV = type("moov");
    private static final int TRAK = type("trak");
    private static final int MDIA = type("mdia");
    private static final int MINF = type("minf");
    private static final int STBL = type("stbl");
    private static final int STSD = type("stsd");
    private static final int STSZ = type("stsz");
    private static final int STCO = type("stco");
    private static final int CO64 = type("co64");
    private static final int STSC = type("stsc");
    private static final int SENC = type("senc");
    private static final int TENC = type("tenc");
    private static final int SINF = type("sinf");
    private static final int FRMA = type("frma");
    private static final int ENCV = type("encv");

    private CencMp4() {
    }

    public static final class Stats {
        public int videoSamples;
        public int videoNalOk;
        public int audioSamples;
    }

    public static Stats decryptFile(File source, File destination, String keyHex) throws Exception {
        byte[] key = hexBytes(keyHex);
        if (key.length != 16) throw new IllegalArgumentException("content key must be 16 bytes");

        long[] range = findMoovRange(source);
        if (range == null) throw new IllegalArgumentException("moov box not found");
        long moovOffset = range[0];
        int moovSize = (int) range[1];
        byte[] moov = readRange(source, moovOffset, moovSize);
        List<Track> tracks = parseTracks(moov);
        if (tracks.isEmpty()) throw new IllegalArgumentException("no audio/video tracks found");

        copyFile(source, destination);
        Stats stats = new Stats();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(destination, "rw")) {
            for (Track track : tracks) {
                if (!track.encrypted || track.offsets.isEmpty()) continue;
                decryptTrackFile(raf, track, key, stats);
            }
        }
        patchCenc(moov);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(destination, "rw")) {
            raf.seek(moovOffset);
            raf.write(moov);
        }
        return stats;
    }

    /** Returns the fourcc of the first video sample entry, or empty if unknown. */
    public static String videoCodec(File source) {
        try {
            long[] range = findMoovRange(source);
            if (range == null) return "";
            byte[] moov = readRange(source, range[0], (int) range[1]);
            Box moovBox = findBox(moov, MOOV, 0, moov.length);
            if (moovBox == null) return "";
            for (Box trak : boxes(moov, moovBox.dataStart, moovBox.end)) {
                if (trak.type != TRAK || trackHandler(moov, trak) != type("vide")) continue;
                Box stsd = findPath(moov, new int[]{MDIA, MINF, STBL, STSD},
                        trak.dataStart, trak.end);
                if (stsd == null) continue;
                List<Box> entries = boxes(moov, stsd.dataStart + 8, stsd.end);
                if (entries.isEmpty()) continue;
                Box entry = entries.get(0);
                if (entry.type == ENCV) {
                    int childStart = entry.dataStart + 78;
                    Box sinf = findBox(moov, SINF, childStart, entry.end);
                    Box frma = sinf == null ? null
                            : findBox(moov, FRMA, sinf.dataStart, sinf.end);
                    if (frma != null && frma.dataStart + 4 <= frma.end) {
                        return fourcc(u32(moov, frma.dataStart));
                    }
                }
                return fourcc(entry.type);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** True when the video sample entry is a codec Android MediaPlayer can decode. */
    public static boolean isPlayable(File source) {
        String codec = videoCodec(source);
        return "hvc1".equals(codec) || "hev1".equals(codec)
                || "avc1".equals(codec) || "avc3".equals(codec);
    }

    private static long[] findMoovRange(File source) throws Exception {
        long fileLength = source.length();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(source, "r")) {
            long position = 0;
            while (position + 8 <= fileLength) {
                raf.seek(position);
                long size = raf.readInt() & 0xffffffffL;
                int boxType = raf.readInt();
                int header = 8;
                if (size == 1) {
                    if (position + 16 > fileLength) break;
                    size = raf.readLong();
                    header = 16;
                } else if (size == 0) {
                    size = fileLength - position;
                }
                if (size < header || position + size > fileLength) break;
                if (boxType == MOOV) return new long[]{position, size};
                position += size;
            }
        }
        return null;
    }

    private static String fourcc(int value) {
        return new String(new char[]{(char) ((value >>> 24) & 0xff), (char) ((value >>> 16) & 0xff),
                (char) ((value >>> 8) & 0xff), (char) (value & 0xff)});
    }

    private static void decryptTrackFile(java.io.RandomAccessFile raf, Track track,
                                         byte[] key, Stats stats) throws Exception {
        if (track.baseIv == null || track.baseIv.length < 8) return;
        long base = 0;
        for (int i = 0; i < 8; i++) base = (base << 8) | (track.baseIv[i] & 0xffL);
        int maxSize = 0;
        for (int size : track.sizes) maxSize = Math.max(maxSize, size);
        byte[] buffer = new byte[Math.max(maxSize, 1)];
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding");
        javax.crypto.spec.SecretKeySpec secret =
                new javax.crypto.spec.SecretKeySpec(key, "AES");
        for (int index = 0; index < track.sizes.size(); index++) {
            long offset = Integer.toUnsignedLong(track.offsets.get(index));
            int size = track.sizes.get(index);
            raf.seek(offset);
            raf.readFully(buffer, 0, size);
            byte[] iv = new byte[16];
            long high = base + index;
            for (int i = 0; i < 8; i++) iv[i] = (byte) (high >>> (56 - i * 8));
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secret,
                    new javax.crypto.spec.IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(buffer, 0, size);
            raf.seek(offset);
            raf.write(plain);
            if (track.handler == type("vide")) {
                stats.videoSamples++;
                if (nalChainOk(plain, 0, size)) stats.videoNalOk++;
            } else if (track.handler == type("soun")) {
                stats.audioSamples++;
            }
        }
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(destination)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
        }
    }

    private static byte[] readRange(File file, long offset, int length) throws Exception {
        byte[] data = new byte[length];
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            raf.seek(offset);
            raf.readFully(data);
        }
        return data;
    }

    public static Stats decrypt(byte[] data, String keyHex) throws Exception {
        byte[] key = hexBytes(keyHex);
        if (key.length != 16) throw new IllegalArgumentException("content key must be 16 bytes");
        Stats stats = new Stats();
        List<Track> tracks = parseTracks(data);
        if (tracks.isEmpty()) throw new IllegalArgumentException("no audio/video tracks found");
        for (Track track : tracks) {
            if (!track.encrypted || track.offsets.isEmpty()) continue;
            decryptTrack(data, track, key);
            if (track.handler == type("vide")) {
                stats.videoSamples += track.sizes.size();
                for (int i = 0; i < track.sizes.size(); i++) {
                    int off = track.offsets.get(i);
                    int size = track.sizes.get(i);
                    if (nalChainOk(data, off, size)) stats.videoNalOk++;
                }
            } else if (track.handler == type("soun")) {
                stats.audioSamples += track.sizes.size();
            }
        }
        patchCenc(data);
        return stats;
    }

    private static void decryptTrack(byte[] data, Track track, byte[] key) throws Exception {
        if (track.baseIv == null || track.baseIv.length < 8) return;
        long base = 0;
        for (int i = 0; i < 8; i++) base = (base << 8) | (track.baseIv[i] & 0xffL);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec secret = new SecretKeySpec(key, "AES");
        for (int index = 0; index < track.sizes.size(); index++) {
            int offset = track.offsets.get(index);
            int size = track.sizes.get(index);
            byte[] iv = new byte[16];
            long high = base + index;
            for (int i = 0; i < 8; i++) iv[i] = (byte) (high >>> (56 - i * 8));
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(data, offset, size);
            System.arraycopy(plain, 0, data, offset, size);
        }
    }

    private static void patchCenc(byte[] data) {
        Box moov = findBox(data, MOOV, 0, data.length);
        if (moov == null) return;
        for (Box trak : boxes(data, moov.dataStart, moov.end)) {
            if (trak.type != TRAK) continue;
            Box stsd = findPath(data, new int[]{MDIA, MINF, STBL, STSD}, trak.dataStart, trak.end);
            if (stsd != null && stsd.dataStart + 8 <= stsd.end) {
                int entryStart = stsd.dataStart + 8;
                List<Box> entries = boxes(data, entryStart, stsd.end);
                for (Box entry : entries) {
                    if (entry.type == ENCV) {
                        Box frma = findDeep(data, FRMA, entry.dataStart, entry.end, 8);
                        if (frma != null && frma.dataStart + 4 <= frma.end) {
                            System.arraycopy(data, frma.dataStart, data, entry.start + 4, 4);
                        }
                    }
                }
            }
            renameDeep(data, SENC, trak.dataStart, trak.end, 8);
            renameDeep(data, SINF, trak.dataStart, trak.end, 8);
            renameDeep(data, type("saiz"), trak.dataStart, trak.end, 8);
            renameDeep(data, type("saio"), trak.dataStart, trak.end, 8);
            renameDeep(data, type("pssh"), trak.dataStart, trak.end, 8);
        }
    }

    private static void renameDeep(byte[] data, int boxType, int start, int end, int depth) {
        if (depth < 0) return;
        for (Box box : boxes(data, start, end)) {
            if (box.type == boxType) {
                writeType(data, box.start + 4, "free");
            }
            if (box.type == MOOV || box.type == TRAK || box.type == MDIA || box.type == MINF
                    || box.type == STBL || box.type == SINF || box.type == type("schi")) {
                renameDeep(data, boxType, box.dataStart, box.end, depth - 1);
            }
        }
    }

    private static List<Track> parseTracks(byte[] data) {
        Box moov = findBox(data, MOOV, 0, data.length);
        if (moov == null) throw new IllegalArgumentException("moov box not found");
        List<Track> tracks = new ArrayList<>();
        for (Box trak : boxes(data, moov.dataStart, moov.end)) {
            if (trak.type != TRAK) continue;
            int handler = trackHandler(data, trak);
            if (handler != type("vide") && handler != type("soun")) continue;
            Track track = new Track();
            track.handler = handler;
            int[] sizes = parseSampleSizes(data, trak);
            int[] offsets = parseSampleOffsets(data, trak, sizes);
            track.sizes = new ArrayList<>();
            track.offsets = new ArrayList<>();
            for (int size : sizes) track.sizes.add(size);
            for (int offset : offsets) track.offsets.add(offset);
            parseProtection(data, trak, track);
            tracks.add(track);
        }
        return tracks;
    }

    private static int trackHandler(byte[] data, Box trak) {
        Box hdlr = findPath(data, new int[]{MDIA, type("hdlr")}, trak.dataStart, trak.end);
        if (hdlr == null || hdlr.dataStart + 12 > hdlr.end) return 0;
        return u32(data, hdlr.dataStart + 8);
    }

    private static int[] parseSampleSizes(byte[] data, Box trak) {
        Box stbl = findPath(data, new int[]{MDIA, MINF, STBL}, trak.dataStart, trak.end);
        if (stbl == null) return new int[0];
        Box stsz = findBox(data, STSZ, stbl.dataStart, stbl.end);
        if (stsz == null) return new int[0];
        int sampleSize = u32(data, stsz.dataStart + 4);
        int count = u32(data, stsz.dataStart + 8);
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) {
            sizes[i] = sampleSize != 0 ? sampleSize : u32(data, stsz.dataStart + 12 + i * 4);
        }
        return sizes;
    }

    private static int[] parseSampleOffsets(byte[] data, Box trak, int[] sizes) {
        Box stbl = findPath(data, new int[]{MDIA, MINF, STBL}, trak.dataStart, trak.end);
        if (stbl == null) return new int[0];
        Box stsc = findBox(data, STSC, stbl.dataStart, stbl.end);
        Box stco = findBox(data, STCO, stbl.dataStart, stbl.end);
        Box co64 = findBox(data, CO64, stbl.dataStart, stbl.end);
        if (stsc == null || (stco == null && co64 == null)) return new int[0];
        int chunkCount = stco != null ? u32(data, stco.dataStart + 4) : u32(data, co64.dataStart + 4);
        long[] chunks = new long[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = stco != null ? u32(data, stco.dataStart + 8 + i * 4)
                    : u64(data, co64.dataStart + 8 + i * 8);
        }
        int entryCount = u32(data, stsc.dataStart + 4);
        int[] firstChunk = new int[entryCount];
        int[] samplesPer = new int[entryCount];
        for (int i = 0; i < entryCount; i++) {
            firstChunk[i] = u32(data, stsc.dataStart + 8 + i * 12);
            samplesPer[i] = u32(data, stsc.dataStart + 12 + i * 12);
        }
        int[] perChunk = new int[chunkCount];
        for (int i = 0; i < entryCount; i++) {
            int last = i + 1 < entryCount ? firstChunk[i + 1] - 1 : chunkCount;
            for (int chunk = firstChunk[i]; chunk <= last; chunk++) {
                if (chunk >= 1 && chunk <= chunkCount) perChunk[chunk - 1] = samplesPer[i];
            }
        }
        int[] offsets = new int[Math.min(sizes.length, 1 << 30)];
        int sample = 0;
        int out = 0;
        for (int chunk = 0; chunk < chunkCount && sample < sizes.length; chunk++) {
            long offset = chunks[chunk];
            for (int i = 0; i < perChunk[chunk] && sample < sizes.length; i++) {
                offsets[out++] = (int) offset;
                offset += sizes[sample++];
            }
        }
        int[] trimmed = new int[out];
        System.arraycopy(offsets, 0, trimmed, 0, out);
        return trimmed;
    }

    private static void parseProtection(byte[] data, Box trak, Track track) {
        Box senc = findDeep(data, SENC, trak.dataStart, trak.end, 8);
        Box tenc = findDeep(data, TENC, trak.dataStart, trak.end, 8);
        int ivSize = 8;
        if (tenc != null && tenc.dataStart + 24 <= tenc.end) {
            ivSize = data[tenc.dataStart + 7] & 0xff;
        }
        if (senc != null && senc.dataStart + 8 + ivSize <= senc.end) {
            byte[] iv = new byte[ivSize];
            System.arraycopy(data, senc.dataStart + 8, iv, 0, ivSize);
            track.baseIv = iv;
            track.encrypted = true;
        }
    }

    static boolean nalChainOk(byte[] data, int offset, int size) {
        int position = 0;
        while (position + 4 <= size) {
            int length = u32(data, offset + position);
            if (length == 0 || position + 4 + length > size) return false;
            position += 4 + length;
        }
        return position == size;
    }

    // ---- boxes ----

    private static final class Box {
        int type;
        int start;
        int dataStart;
        int end;
        int headerSize;
    }

    private static final class Track {
        int handler;
        List<Integer> sizes;
        List<Integer> offsets;
        byte[] baseIv;
        boolean encrypted;
    }

    private static List<Box> boxes(byte[] data, int start, int end) {
        List<Box> out = new ArrayList<>();
        int offset = start;
        while (offset + 8 <= end) {
            long size = u32(data, offset) & 0xffffffffL;
            int boxType = u32(data, offset + 4);
            int header = 8;
            if (size == 1) {
                if (offset + 16 > end) break;
                size = u64(data, offset + 8);
                header = 16;
            } else if (size == 0) {
                size = end - offset;
            }
            if (size < header || offset + size > end) break;
            Box box = new Box();
            box.type = boxType;
            box.start = offset;
            box.dataStart = offset + header;
            box.end = (int) (offset + size);
            box.headerSize = header;
            out.add(box);
            offset += (int) size;
        }
        return out;
    }

    private static Box findBox(byte[] data, int type, int start, int end) {
        for (Box box : boxes(data, start, end)) {
            if (box.type == type) return box;
        }
        return null;
    }

    private static Box findPath(byte[] data, int[] path, int start, int end) {
        Box box = findBox(data, path[0], start, end);
        if (box == null) return null;
        for (int i = 1; i < path.length; i++) {
            box = findBox(data, path[i], box.dataStart, box.end);
            if (box == null) return null;
        }
        return box;
    }

    private static Box findDeep(byte[] data, int type, int start, int end, int depth) {
        if (depth < 0) return null;
        for (Box box : boxes(data, start, end)) {
            if (box.type == type) return box;
            if (box.type == MOOV || box.type == TRAK || box.type == MDIA || box.type == MINF
                    || box.type == STBL || box.type == SINF || box.type == type("schi")
                    || box.type == STSD || box.type == ENCV) {
                Box found = findDeep(data, type, box.dataStart, box.end, depth - 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void writeType(byte[] data, int offset, String type) {
        data[offset] = (byte) type.charAt(0);
        data[offset + 1] = (byte) type.charAt(1);
        data[offset + 2] = (byte) type.charAt(2);
        data[offset + 3] = (byte) type.charAt(3);
    }

    private static int type(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }

    private static int u32(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }

    private static long u64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xffL);
        return value;
    }

    private static byte[] hexBytes(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
