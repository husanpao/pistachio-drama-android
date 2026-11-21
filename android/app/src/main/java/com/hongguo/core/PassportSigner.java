package com.hongguo.core;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-Java port of the public hongguo-tv passport signer.
 * No native library, no unidbg, no Frida.
 */
public final class PassportSigner {
    private static final int[] MD5_CONSTANTS = PassportConstants.MD5_CONSTANTS;
    private static final byte[] ORDERS = PassportConstants.ORDERS;
    private static final byte[] SBOX = PassportConstants.SBOX;

    private static final int[] SEEDS = {0x8980f29b, 0xeb549c7f, 0xb08726db, 0xd40cb5e6, 0xe8f559e4};
    private static final int[] START = {0x79e0f2fb, 0xc8b52570, 0xebc2f8cd, 0x7c104d93};
    private static final int[] END = {0x19be4866, 0xe85986b4, 0xe19b326e, 0x71d1d7d4};
    private static final int[][] SHIFTS = {
            {7, 12, 17, 22}, {5, 9, 14, 20}, {4, 11, 16, 23}, {6, 10, 15, 21}
    };

    private PassportSigner() {
    }

    public static final class Result {
        public final String query;
        public final Map<String, String> headers;

        Result(String query, Map<String, String> headers) {
            this.query = query;
            this.headers = headers;
        }
    }

    private static final class Tuned {
        final String raw;
        final int iv;
        final byte[] q;
        final byte[] m;
        final long ticket;

        Tuned(String raw, int iv, byte[] q, byte[] m, long ticket) {
            this.raw = raw;
            this.iv = iv;
            this.q = q;
            this.m = m;
            this.ticket = ticket;
        }
    }

    public static Result sign(Map<String, String> values, byte[] body, long stamp, String deviceId) {
        return sign(values, body, stamp, deviceId, null);
    }

    public static Result sign(Map<String, String> values, byte[] body, long stamp, String deviceId,
                              byte[] random16) {
        Tuned tuned = tuneQuery(values, body, stamp);
        byte[] rnd = random16;
        if (rnd == null) {
            rnd = new byte[16];
            new SecureRandom().nextBytes(rnd);
        }
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) n[i] = le32(rnd, i * 4);
        int ts = (int) (stamp / 1000);
        String medusa = passportMedusa(tuned.raw, ts, n[0], n[1], n[2], deviceId,
                tuned.iv, tuned.q, tuned.m);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Khronos", Integer.toUnsignedString(ts));
        headers.put("X-SS-Req-Ticket", Long.toString(tuned.ticket));
        headers.put("X-Helios", passportHelios(ts, n[3]));
        headers.put("X-Medusa", medusa);
        headers.put("X-Gorgon", passportGorgon(tuned.raw, body, ts, n[0] & 0xffff));
        if (body != null && body.length > 0) {
            headers.put("X-SS-STUB", hex(tuned.m).toUpperCase());
        }
        return new Result(tuned.raw, headers);
    }

    private static Tuned tuneQuery(Map<String, String> values, byte[] body, long stamp) {
        TreeMap<String, String> map = new TreeMap<>(values);
        for (int i = 0; i < 256; i++) {
            long ticket = stamp + i;
            map.put("_rticket", Long.toString(ticket));
            String raw = encodeQuery(map);
            int ts = (int) (stamp / 1000);
            Branch branch = passportBranch(raw, body, ts);
            if ((branch.iv & 15) % 3 == 2) {
                return new Tuned(raw, branch.iv, branch.q, branch.m, ticket);
            }
        }
        throw new IllegalStateException("unable to select signature branch");
    }

    private static String encodeQuery(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            try {
                parts.add(URLEncoder.encode(e.getKey(), "UTF-8") + "="
                        + URLEncoder.encode(e.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException impossible) {
                throw new AssertionError(impossible);
            }
        }
        return String.join("&", parts);
    }

    private static final class Branch {
        final int iv;
        final byte[] q;
        final byte[] m;

        Branch(int iv, byte[] q, byte[] m) {
            this.iv = iv;
            this.q = q;
            this.m = m;
        }
    }

    private static Branch passportBranch(String query, byte[] body, int ts) {
        byte[] q = sm3(query.getBytes(StandardCharsets.UTF_8));
        byte[] m = (body != null && body.length > 0) ? md5(body) : new byte[16];
        int iv = passportIV(le32(ts), passportIV(m, passportIV(q, 0x20230928)));
        return new Branch(iv, q, m);
    }

    private static int passportIV(byte[] data, int iv) {
        for (int i = 0; i < data.length; i++) {
            int x = data[i] & 0xff;
            if ((i & 1) == 0) {
                iv = (iv >>> 4) ^ iv ^ (iv << 6) ^ x;
            } else {
                iv = ~((iv >>> 7) ^ iv ^ (x | (iv << 12)));
            }
        }
        return iv;
    }

    private static byte[] passportF13(int iv, int ts, byte[] q, byte[] m) {
        int group = ((iv & 15) - 2) / 3;
        int count = SEEDS[group] + ts;
        int shift = (count + 5) & 7;
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        msg.write(q, 0, q.length);
        msg.write(m, 0, m.length);
        write(msg, le32(ts));
        byte[] key = hexBytes("8496779dd4150bf8");
        for (byte b : key) {
            msg.write(((b & 0xff) * 257) >> shift);
        }
        msg.write(0xa0);
        msg.write(1);
        msg.write(0);
        msg.write(0);
        byte[] message = msg.toByteArray();
        int[] start = START.clone();
        int[] end = END.clone();
        for (int i = 0; i < 4; i++) {
            start[i] = Integer.rotateLeft(start[i], -(count & 31));
            end[i] = Integer.rotateLeft(end[i], -((count + 6) & 31));
        }
        int a = start[0], b = start[1], c = start[2], d = start[3];
        for (int i = 0; i < 64; i++) {
            int f;
            switch (i / 16) {
                case 0: f = (b & c) | (~b & d); break;
                case 1: f = (b & d) | (c & ~d); break;
                case 2: f = b ^ c ^ d; break;
                default: f = c ^ (b | ~d); break;
            }
            int word = le32(message, (ORDERS[group * 64 + i] & 0xff) * 4);
            int x = b + Integer.rotateLeft(a + f + word
                    + Integer.rotateLeft(MD5_CONSTANTS[i], -((count + 1) & 31)),
                    SHIFTS[i / 16][i % 4]);
            a = d;
            d = c;
            c = b;
            b = x;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] vals = {a, b, c, d};
        for (int i = 0; i < 4; i++) write(out, le32((start[i] + vals[i]) ^ end[i]));
        byte[] twelve = out.toByteArray();
        int check = 0x20220420;
        for (int i = 0; i < 12; i++) {
            int x = twelve[i] & 0xff;
            if ((i & 1) == 0) {
                check = (check >>> 3) ^ check ^ x ^ (check << 7);
            } else {
                check = (check >>> 5) ^ check ^ ~(x | (check << 11));
            }
        }
        write(out, le32((check | 4) ^ 0x1000000));
        return out.toByteArray();
    }

    private static String passportHelios(int ts, int nonce) {
        byte[] key = md5(concat(le32(nonce), "8662".getBytes(StandardCharsets.UTF_8)));
        byte[] raw = hex(key).getBytes(StandardCharsets.US_ASCII);
        long k = le64(raw, 0);
        long[] pending = {le64(raw, 8), le64(raw, 16), le64(raw, 24)};
        long[] rounds = new long[34];
        rounds[0] = k;
        for (int i = 0; i < 33; i++) {
            long x = (Long.rotateRight(pending[0], 8) + k) ^ (long) i;
            pending[0] = pending[1];
            pending[1] = pending[2];
            pending[2] = x;
            k = x ^ Long.rotateLeft(k, 3);
            rounds[i + 1] = k;
        }
        byte[] text = (ts + "-1588093228-8662").getBytes(StandardCharsets.US_ASCII);
        int pad = 16 - text.length % 16;
        byte[] plain = new byte[text.length + pad];
        System.arraycopy(text, 0, plain, 0, text.length);
        Arrays.fill(plain, text.length, plain.length, (byte) pad);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, le32(nonce));
        for (int off = 0; off < plain.length; off += 16) {
            long a = le64(plain, off);
            long b = le64(plain, off + 8);
            for (long r : rounds) {
                b = r ^ (a + Long.rotateRight(b, 8));
                a = b ^ Long.rotateLeft(a, 3);
            }
            write(out, le64(a));
            write(out, le64(b));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static String gorgon(String query, byte[] body, int ts, int nonce) {
        return passportGorgon(query, body, ts, nonce);
    }

    private static String passportGorgon(String query, byte[] body, int ts, int nonce) {
        byte[] q = md5(query.getBytes(StandardCharsets.UTF_8));
        byte[] m = (body != null && body.length > 0) ? md5(body) : new byte[16];
        byte[] data = new byte[20];
        System.arraycopy(q, 0, data, 0, 4);
        System.arraycopy(m, 0, data, 4, 4);
        byte[] le = le32(67503104);
        System.arraycopy(le, 0, data, 12, 4);
        byte[] be = be32(ts);
        System.arraycopy(be, 0, data, 16, 4);
        int[] key = {0x4a, 0x40, 0x16, (nonce >>> 8) & 255, 0x47, 0x6c, 1, nonce & 255};
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + key[i % 8]) & 255;
            s[i] = s[j];
        }
        j = 0;
        for (int i = 0; i < 20; i++) {
            j += s[i + 1];
            int y = s[j & 255];
            s[i + 1] = y;
            data[i] ^= (byte) s[(y * 2) & 255];
        }
        for (int i = 0; i < 20; i++) {
            data[i] = (byte) rotl8(data[i] & 0xff, 4);
            int next = i < 19 ? (data[i + 1] & 0xff) : (data[0] & 0xff);
            data[i] = (byte) ~(reverse8((data[i] & 0xff) ^ next) ^ 20);
        }
        byte[] head = {(byte) 0x84, 4, (byte) nonce, (byte) (nonce >>> 8), 0x40, 1};
        return hex(concat(head, data));
    }

    private static byte[] passportProto(String query, int ts, int nonce, String deviceId,
                                        byte[] q, byte[] m, byte[] f13) {
        Proto device = new Proto();
        device.sint(1, 1);
        device.sint(2, 2);
        device.text(3, "8662");
        device.text(4, deviceId);
        device.text(5, "Ai6svO3PyrwDOUSmO6ZcResxu");
        device.text(6, "!noperm!");
        for (int i : new int[]{7, 8}) device.sint(i, -888888);
        device.sint(9, 3);
        device.sint(10, -888888);
        device.text(11, "!notset!");
        device.text(12, "Asia/Shanghai,8");
        device.text(13, "zh_CN");
        device.sint(14, 4);
        float[] floats = {255.24993896484375f, 35.58599090576172f, 3.467449188232422f,
                3.467449188232422f, 255.1754913330078f, 42.17544174194336f};
        for (int i = 0; i < floats.length; i++) device.float32(i + 16, floats[i]);
        device.text(22, "16");
        device.sint(23, 41);
        device.sint(24, 36);
        for (int i = 25; i <= 28; i++) {
            long n = 1728388016635L + (i == 28 ? 2 : 0);
            device.sint(i, n);
        }
        device.sint(29, -1);
        String[] models = {"25053RT47C", "Redmi", "25053RT47C", "25053RT47C", "Redmi", "Redmi", "Redmi"};
        for (int i = 0; i < models.length; i++) device.text(i + 30, models[i]);
        device.sint(38, 31);

        Proto report = new Proto();
        report.sint(1, ts);
        report.sint(2, -2);
        report.sint(4, 200);

        Proto env = new Proto();
        env.sint(1, 110);
        env.sint(2, 146331399);
        env.sint(3, 146331396);
        env.sint(5, 7);
        env.text(6, "v04.06.04.03-bugfix");
        env.sint(7, 11001);
        env.blob(12, device.data());
        env.blob(13, report.data());
        env.text(14, "7.3.5.32");

        Proto count = new Proto();
        count.sint(1, 111);
        count.sint(2, 10);
        count.sint(3, 694367);
        count.sint(5, 586952199);

        Proto p = new Proto();
        p.blob(1, hexBytes("f7e85ffad7d7dc3bd62ac87057cf6118"));
        p.sint(2, 3);
        p.sint(3, nonce);
        p.text(4, "8662");
        p.text(5, deviceId);
        p.text(6, "1588093228");
        p.text(7, "7.3.5.32");
        p.text(8, "v04.06.04-ml-android");
        p.sint(9, 67503104);
        p.blob(10, hexBytes("4001000000000000"));
        p.sint(12, ts);
        p.blob(13, f13);
        p.blob(14, Arrays.copyOf(q, 6));
        p.blob(15, count.data());
        p.text(16, "AXYQOS6n2m60x1fVZHIrH3iol");
        p.sint(17, ts);
        byte[] digest = sm3(concat(query.getBytes(StandardCharsets.UTF_8), m, "none".getBytes(StandardCharsets.UTF_8)));
        p.blob(19, digest);
        p.text(20, "none");
        p.sint(21, 312);
        p.blob(23, env.data());
        p.text(24, "{\"cmr\":16777216,\"cmr2\":16777216,\"un_h\":1879194040,\"vpn\":0,\"kd\":0,"
                + "\"fkd\":3672518972,\"pd\":-1872573247,\"dyn\":\"\",\"do\":0,\"tk\":true}");
        return p.data();
    }

    private static byte[] passportXmxor(byte[] data, byte[] key) {
        int n = data.length;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int k = (i * 4) & 28;
            int a = key[k] & 0xff;
            int b = key[k + 1] & 0xff;
            int v = (~(rotl8(data[i] & 0xff, 4) + a) ^ b) & 255;
            v = (rotl8(v, 3) + b) & 255;
            out[n - 1 - i] = (byte) (~(v ^ a) & 255);
        }
        int flag = (out[n - 1] & 0xff) ^ (out[n - 2] & 0xff);
        int old = out[0] & 0xff;
        out[0] = (byte) ((out[0] & 0xff) + (~flag) & 255);
        out[1] = (byte) (((out[1] & 0xff) + ((out[0] & 0xff) ^ (out[n - 1] & 0xff) ^ 254)) & 255);
        out[2] = (byte) (((out[2] & 0xff) + (((flag - old) & 255) ^ rotl8(out[1] & 0xff, 3) ^ 2)) & 255);
        for (int i = 0; i < n - 4; i++) {
            out[i + 3] = (byte) (((out[i + 3] & 0xff)
                    + (~(rotl8(out[i + 2] & 0xff, 3) ^ (out[i + 1] & 0xff) ^ (i + 3)) & 255)) & 255);
        }
        out[n - 1] ^= out[n - 2];
        int sum = 0;
        for (int i = 1; i < n; i++) sum = (sum + (out[i] & 0xff)) & 255;
        out[0] = (byte) (((out[0] & 0xff) ^ (out[1] & 0xff)) + sum & 255);
        return out;
    }

    private static byte[] passportAesV3(byte[] data, int ts) {
        if (data.length < 248) throw new IllegalArgumentException("signature payload is too short");
        int branch = ts & 3;
        int boxOff = branch * 256;
        int[][] cons = {
                {1, 0, 2, 3}, {2, 0, 3, 1}, {0, 1, 3, 2}, {1, 0, 2, 3}
        };
        int[] con = cons[branch];
        int[][] orders = {
                {0, 9, 14, 11, 4, 13, 2, 7, 8, 1, 6, 15, 12, 5, 10, 3},
                {0, 9, 14, 15, 4, 13, 2, 7, 8, 1, 6, 3, 12, 5, 10, 11},
                {0, 9, 14, 7, 4, 13, 2, 11, 8, 1, 6, 3, 12, 5, 10, 15},
                {0, 9, 14, 11, 4, 13, 2, 7, 8, 1, 6, 15, 12, 5, 10, 3}
        };
        int[] order = orders[branch];
        int[] inits = {0xca025ddc, 0x823dc546, 0xc9420583, 0xc298225f};
        int init = inits[branch];
        byte[] key = hexBytes("f1593376766ea98d34f31b057a9d5be4");
        byte[] mk = new byte[48];
        for (int j = 0; j < 16; j++) {
            mk[j] = (byte) (key[j] ^ (init >>> ((j % 4) * 8)));
        }
        int r = 8;
        for (int j = 4; j < 12; j++) {
            byte[] prev = Arrays.copyOfRange(mk, j * 4 - 4, j * 4);
            if (j % 4 == 0) {
                prev = new byte[]{
                        (byte) ((init >>> (r & 24)) ^ (SBOX[boxOff + (prev[1] & 0xff)] & 0xff)),
                        SBOX[boxOff + (prev[2] & 0xff)],
                        SBOX[boxOff + (prev[3] & 0xff)],
                        SBOX[boxOff + (prev[0] & 0xff)]
                };
            }
            r += 2;
            for (int k = 0; k < 4; k++) {
                mk[j * 4 + k] = (byte) (prev[k] ^ mk[j * 4 + k - 16]);
            }
        }
        byte[][] encrypted = new byte[1][];
        final byte[] mkFinal = mk;
        final int[] conFinal = con;
        final int[] orderFinal = order;
        final int boxOffFinal = boxOff;

        // Java lambda cannot easily return byte[]; use a helper method below.
        byte[] plain = new byte[32];
        for (int j = 0; j < 31; j++) {
            int off = j * 8;
            plain[j] = (byte) ((((data[off] & 0xff) >>> 4) & 2)
                    | ((data[off + 1] & 0xff) & 64)
                    | (((data[off + 2] & 0xff) >>> 2) & 1)
                    | (((data[off + 3] & 0xff) << 3) & 128)
                    | (((data[off + 4] & 0xff) >>> 1) & 4)
                    | (((data[off + 5] & 0xff) << 3) & 16)
                    | (((data[off + 6] & 0xff) << 5) & 32)
                    | (((data[off + 7] & 0xff) >>> 4) & 8));
        }
        plain[31] = 1;
        byte[] previous = hexBytes("1fe109a4125283f418de9e051a969e12");
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        for (int j = 0; j < 32; j += 16) {
            for (int k = 0; k < 16; k++) plain[j + k] ^= previous[k];
            previous = aesBlock(Arrays.copyOfRange(plain, j, j + 16), mkFinal, conFinal,
                    orderFinal, boxOffFinal);
            write(encOut, previous);
        }
        byte[] encryptedBytes = encOut.toByteArray();
        byte[] out = new byte[1 + data.length];
        out[0] = encryptedBytes[31];
        System.arraycopy(data, 0, out, 1, data.length);
        for (int j = 0; j < 31; j++) {
            int off = 1 + j * 8;
            int k = encryptedBytes[j] & 0xff;
            out[off] = (byte) ((out[off] & ~32) | ((k << 4) & 32));
            out[off + 1] = (byte) ((out[off + 1] & ~64) | (k & 64));
            out[off + 2] = (byte) ((out[off + 2] & ~4) | ((k << 2) & 4));
            out[off + 3] = (byte) ((out[off + 3] & ~16) | ((k >>> 3) & 16));
            out[off + 4] = (byte) ((out[off + 4] & ~8) | ((k << 1) & 8));
            out[off + 5] = (byte) ((out[off + 5] & ~2) | ((k >>> 3) & 2));
            out[off + 6] = (byte) ((out[off + 6] & ~1) | ((k >>> 5) & 1));
            out[off + 7] = (byte) ((out[off + 7] & 127) | ((k << 4) & 128));
        }
        return out;
    }

    private static byte[] aesBlock(byte[] src, byte[] mk, int[] con, int[] order, int boxOff) {
        byte[] b = src.clone();

        for (int j = 0; j < b.length; j++) {
            b[j] ^= mk[(j / 4) * 4 + con[j % 4]];
        }
        for (int round = 1; round <= 2; round++) {
            byte[] prev = b.clone();
            for (int j = 0; j < b.length; j++) {
                int index = order[j];
                b[j] = SBOX[boxOff + (prev[(con[index / 4] * 4 + index % 4)] & 0xff)];
            }
            if (round == 1) {
                prev = b.clone();
                for (int j = 0; j < b.length; j++) {
                    b[j] = prev[(j / 4) * 4 + con[j % 4]];
                }
                for (int col = 0; col < 4; col++) {
                    int a = b[col] & 0xff, c = b[4 + col] & 0xff;
                    int d = b[8 + col] & 0xff, e = b[12 + col] & 0xff;
                    int t = a ^ c ^ d ^ e;
                    b[col] ^= (byte) (t ^ xt(a ^ c));
                    b[4 + col] ^= (byte) (t ^ xt(c ^ d));
                    b[8 + col] ^= (byte) (t ^ xt(d ^ e));
                    b[12 + col] ^= (byte) (t ^ xt(e ^ a));
                }
            }
            for (int j = 0; j < b.length; j++) {
                b[j] ^= mk[round * 16 + (j / 4) * 4 + con[j % 4]];
            }
        }
        for (int j = 0; j < b.length; j++) b[j] ^= mk[16 + j];
        return b;
    }

    private static int xt(int x) {
        return (x & 128) != 0 ? ((x << 1) ^ 0x1b) & 255 : (x << 1) & 255;
    }

    private static String passportMedusa(String query, int ts, int nonce, int hashRand, int xmRand,
                                         String deviceId, int iv, byte[] q, byte[] m) {
        byte[] f13 = passportF13(iv, ts, q, m);
        byte[] p = passportProto(query, ts, nonce, deviceId, q, m, f13);
        byte[] signKey = hexBytes("8ebdfa3806ecc5cee79423e6029ed82540bc2218bb7eaef71cb691f7aa8aa2f5");
        byte[] key = sm3(concat(signKey, le32(hashRand), signKey));
        int d1 = (hashRand >>> 16) & 255;
        int seedInt = ~(((d1 << 11) | (hashRand >>> 24)) ^ (d1 >>> 5) ^ d1);
        byte[] seed = le32(seedInt);
        p = concat(hexBytes("4001000000000000"), passportXmxor(p, key));
        for (int a = 0, b = p.length - 1; a < b; a++, b--) {
            byte t = p[a];
            p[a] = p[b];
            p[b] = t;
        }
        for (int i = 0; i < p.length; i++) p[i] ^= seed[(~i) & 3];
        int check = 0x18000001 | ((q[0] & 63) << 14) | ((f13[0] & 63) << 8);
        byte[] body = concat(new byte[]{0x35}, le32(xmRand), le32(check), p,
                Arrays.copyOfRange(le32(hashRand), 2, 4));
        byte[] enc = passportAesV3(body, ts);
        byte[] header = hexBytes("03000000f7e85ffad7d7dc3bd62ac87057cf6118");
        for (int j = 0; j < 20; j += 4) {
            int v = le32(header, j) ^ ts;
            byte[] le = le32(v);
            System.arraycopy(le, 0, header, j, 4);
        }
        byte[] out = concat(header, Arrays.copyOfRange(le32(hashRand), 0, 2), new byte[]{0, 1}, enc);
        return Base64.getEncoder().encodeToString(out);
    }

    // ---- primitives ----

    /** Package-private bridge for the playback-v5 profile (same SM3). */
    static byte[] sm3ForPlayback(byte[] data) {
        return sm3(data);
    }

    private static byte[] sm3(byte[] data) {
        long bitLen = (long) data.length * 8;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(data, 0, data.length);
        buf.write(0x80);
        while (buf.size() % 64 != 56) buf.write(0);
        byte[] len = new byte[8];
        for (int i = 0; i < 8; i++) len[7 - i] = (byte) (bitLen >>> (i * 8));
        buf.write(len, 0, 8);
        byte[] all = buf.toByteArray();
        int[] v = {0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
                0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e};
        int[] w = new int[68];
        for (int off = 0; off < all.length; off += 64) {
            for (int i = 0; i < 16; i++) w[i] = be32(all, off + i * 4);
            for (int i = 16; i < 68; i++) {
                w[i] = p1(w[i - 16] ^ w[i - 9] ^ Integer.rotateLeft(w[i - 3], 15))
                        ^ Integer.rotateLeft(w[i - 13], 7) ^ w[i - 6];
            }
            int a = v[0], c = v[1], d = v[2], e = v[3], f = v[4], g = v[5], h = v[6], k = v[7];
            for (int j = 0; j < 64; j++) {
                int t, ff, gg;
                if (j < 16) {
                    t = 0x79cc4519;
                    ff = a ^ c ^ d;
                    gg = f ^ g ^ h;
                } else {
                    t = 0x7a879d8a;
                    ff = (a & c) | (a & d) | (c & d);
                    gg = (f & g) | (~f & h);
                }
                int s1 = Integer.rotateLeft(Integer.rotateLeft(a, 12) + f + Integer.rotateLeft(t, j), 7);
                int s2 = s1 ^ Integer.rotateLeft(a, 12);
                int u1 = ff + e + s2 + (w[j] ^ w[j + 4]);
                int u2 = gg + k + s1 + w[j];
                e = d;
                d = Integer.rotateLeft(c, 9);
                c = a;
                a = u1;
                k = h;
                h = Integer.rotateLeft(g, 19);
                g = f;
                f = p0(u2);
            }
            v[0] ^= a; v[1] ^= c; v[2] ^= d; v[3] ^= e;
            v[4] ^= f; v[5] ^= g; v[6] ^= h; v[7] ^= k;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[i * 4] = (byte) (v[i] >>> 24);
            out[i * 4 + 1] = (byte) (v[i] >>> 16);
            out[i * 4 + 2] = (byte) (v[i] >>> 8);
            out[i * 4 + 3] = (byte) v[i];
        }
        return out;
    }

    private static int p0(int x) { return x ^ Integer.rotateLeft(x, 9) ^ Integer.rotateLeft(x, 17); }
    private static int p1(int x) { return x ^ Integer.rotateLeft(x, 15) ^ Integer.rotateLeft(x, 23); }

    private static int rotl8(int x, int n) {
        n &= 7;
        x &= 255;
        return n == 0 ? x : ((x << n) | (x >>> (8 - n))) & 255;
    }

    private static int reverse8(int x) {
        x &= 255;
        int y = 0;
        for (int i = 0; i < 8; i++) y |= ((x >>> i) & 1) << (7 - i);
        return y;
    }

    private static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int n = 0;
        for (byte[] a : arrays) n += a.length;
        byte[] out = new byte[n];
        int off = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        return out;
    }

    private static byte[] le32(int n) {
        return new byte[]{(byte) n, (byte) (n >>> 8), (byte) (n >>> 16), (byte) (n >>> 24)};
    }

    private static byte[] be32(int n) {
        return new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    }

    private static byte[] le64(long n) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) out[i] = (byte) (n >>> (i * 8));
        return out;
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    private static long le64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= (long) (b[off + i] & 0xff) << (i * 8);
        return v;
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    private static String hex(byte[] data) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = digits[(data[i] >>> 4) & 15];
            out[i * 2 + 1] = digits[data[i] & 15];
        }
        return new String(out);
    }

    private static byte[] hexBytes(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static final class Proto {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void sint(int field, long n) {
            varint(field << 3);
            varint((n << 1) ^ (n >> 63));
        }

        void blob(int field, byte[] b) {
            if (b == null || b.length == 0) return;
            varint((field << 3) | 2);
            varint(b.length);
            out.write(b, 0, b.length);
        }

        void text(int field, String s) {
            blob(field, s.getBytes(StandardCharsets.UTF_8));
        }

        void float32(int field, float n) {
            varint((field << 3) | 5);
            int bits = Float.floatToIntBits(n);
            out.write(bits & 255);
            out.write((bits >>> 8) & 255);
            out.write((bits >>> 16) & 255);
            out.write((bits >>> 24) & 255);
        }

        byte[] data() {
            return out.toByteArray();
        }

        private void varint(long value) {
            while (value > 127) {
                out.write((int) (value & 127) | 128);
                value >>>= 7;
            }
            out.write((int) value & 127);
        }
    }
}
