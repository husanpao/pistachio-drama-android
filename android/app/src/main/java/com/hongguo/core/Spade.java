package com.hongguo.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Pure-Java port of the spade_a content-key unwrap. */
public final class Spade {
    private Spade() {
    }

    public static String spadeToKey(String spadeA) {
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(spadeA);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("spade_a is not valid base64");
        }
        return unwrap(blob, 0);
    }

    public static String unwrap(byte[] spade, int flag) {
        int length = spade.length;
        if (length < 3) throw new IllegalArgumentException("spade blob is too short");
        int marker = (spade[0] & 0xff) ^ (spade[1] & 0xff) ^ (spade[2] & 0xff);
        int typeLength = marker - 0x30;
        if (typeLength < 1) throw new IllegalArgumentException("invalid spade type length");
        int workLength = (length - marker) + 0x2F;
        if (workLength < 1 || 1 + workLength > length) {
            throw new IllegalArgumentException("invalid spade work length");
        }
        if (typeLength > length - 2) {
            throw new IllegalArgumentException("invalid spade type length");
        }
        byte[] work = new byte[workLength];
        System.arraycopy(spade, 1, work, 0, workLength);

        int tailA = spade[length - typeLength - 2] & 0xff;
        int tailB = spade[length - typeLength - 1] & 0xff;
        byte[] scheme = new byte[typeLength];
        for (int i = 0; i < typeLength; i++) {
            scheme[i] = (byte) ((spade[length - typeLength + i] & 0xff) ^ tailA ^ tailB);
        }
        if (startsWithPrefix(scheme, "app_v2".getBytes(StandardCharsets.US_ASCII), typeLength)
                || startsWithPrefix(scheme, "web_v2".getBytes(StandardCharsets.US_ASCII), typeLength)) {
            throw new IllegalArgumentException("spade ver2 (AES-GCM) is not supported");
        }

        int previousEven = 0x55;
        int previousOdd = 0xFA;
        for (int index = 0; index < workLength; index++) {
            int current = work[index] & 0xff;
            int popcount = Integer.bitCount(index);
            int left;
            int right;
            if ((index & 1) != 0) {
                left = previousOdd;
                right = current;
                previousOdd = previousEven;
            } else {
                left = current;
                right = previousEven;
            }
            int bias = flag != 0 ? (popcount + 0x15) : signedByte(-0x15 - popcount);
            work[index] = (byte) ((bias + (previousOdd ^ current)) & 0xff);
            previousEven = right;
            previousOdd = left;
        }
        int keyLength = keyLengthFromHeader(work[0] & 0xff);
        int end = workLength - keyLength;
        if (end < 2) throw new IllegalArgumentException("spade key is truncated");
        String key = new String(work, 1, end - 1, StandardCharsets.ISO_8859_1);
        if (key.length() != 32 || !key.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("spade key is not 32 hex characters");
        }
        return key.toLowerCase();
    }

    private static boolean startsWithPrefix(byte[] data, byte[] prefix, int length) {
        for (int i = 0; i < length; i++) {
            int left = i < data.length ? data[i] & 0xff : 0;
            int right = i < prefix.length ? prefix[i] & 0xff : 0;
            if (left != right) return false;
            if (left == 0) return true;
        }
        return true;
    }

    private static int signedByte(int value) {
        value &= 0xff;
        return value >= 0x80 ? value - 256 : value;
    }

    private static int keyLengthFromHeader(int header) {
        if (header >= 0x30 && header <= 0x39) return header - 0x30;
        if (header >= 0x61 && header <= 0x7a) return header - 0x57;
        throw new IllegalArgumentException("spade key header is not a hex digit");
    }
}
