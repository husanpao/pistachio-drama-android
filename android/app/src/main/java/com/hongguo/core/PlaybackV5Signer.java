package com.hongguo.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Playback-only signer for the 7.4.x v5 envelope.
 *
 * <p>Ported from the public hongguo-tv playback-v5 implementation. This is
 * deliberately separate from the passport/account signer: the license, F13
 * branch, Medusa envelope and query profile are different.</p>
 */
final class PlaybackV5Signer {
    private static final byte[] MAGIC = hex(PlaybackV5Constants.MAGIC_HEX);
    private static final byte[] KEY = hex(PlaybackV5Constants.KEY_HEX);
    private static final byte[] SBOX = hex(PlaybackV5Constants.SBOX_HEX);
    private static final int[] HASH_TABLE = PlaybackV5Constants.HASH_TABLE;
    private static final String LICENSE = "646637794";
    private static final long PROCESS_STARTED = System.currentTimeMillis();
    private static final String PROCESS_NONCE = base64Url(randomBytes(18));

    private static final String[] QUERY_KEYS = {
            "aid", "app_name", "version_code", "version_name", "channel",
            "device_platform", "device_type", "os_version", "update_version_code",
            "iid", "cdid", "device_id", "_rticket"
    };

    private PlaybackV5Signer() {
    }

    static final class Result {
        final String query;
        final Map<String, String> headers;

        Result(String query, Map<String, String> headers) {
            this.query = query;
            this.headers = headers;
        }
    }

    static final String[] VIDEO_TAB_KEYS = {
            "aid", "app_name", "version_code", "version_name", "channel",
            "device_platform", "device_type", "os_version", "update_version_code",
            "iid", "cdid", "device_id", "video_id", "video_id_type", "device_level",
            "need_all_video_definition", "need_mp4_align", "use_os_player",
            "video_platform", "source", "from_video_id", "_rticket"
    };

    static Result sign(Map<String, String> values, byte[] body, long stamp) {
        return signKeys(values, body, stamp, QUERY_KEYS);
    }

    static Result signKeys(Map<String, String> values, byte[] body, long stamp, String[] keys) {
        Tuned tuned = tuneKeys(values, body, stamp, keys);
        int[] n = new int[4];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < n.length; i++) {
            byte[] b = new byte[4];
            random.nextBytes(b);
            n[i] = le32(b, 0);
        }
        int ts = (int) (stamp / 1000L);
        String medusa = medusa(values, ts, tuned.q, tuned.m, n, PROCESS_NONCE, PROCESS_STARTED);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Khronos", Integer.toUnsignedString(ts));
        headers.put("X-Helios", helios(ts, n[3]));
        headers.put("X-Medusa", medusa);
        headers.put("X-SS-STUB", hexUpper(tuned.m));
        return new Result(tuned.query, headers);
    }

    /** Deterministic hook used by the local protocol-vector check. */
    static Result signForTest(Map<String, String> values, byte[] body, long stamp,
                              String nonce, long started, int[] n) {
        Tuned tuned = tune(values, body, stamp);
        int ts = (int) (stamp / 1000L);
        String medusa = medusa(values, ts, tuned.q, tuned.m, n, nonce, started);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Khronos", Integer.toUnsignedString(ts));
        headers.put("X-Helios", helios(ts, n[3]));
        headers.put("X-Medusa", medusa);
        headers.put("X-SS-STUB", hexUpper(tuned.m));
        return new Result(tuned.query, headers);
    }

    private static final class Tuned {
        final String query;
        final byte[] q;
        final byte[] m;

        Tuned(String query, byte[] q, byte[] m) {
            this.query = query;
            this.q = q;
            this.m = m;
        }
    }

    private static Tuned tune(Map<String, String> values, byte[] body, long stamp) {
        return tuneKeys(values, body, stamp, QUERY_KEYS);
    }

    private static Tuned tuneKeys(Map<String, String> values, byte[] body, long stamp, String[] keys) {
        for (int offset = 0; offset < 1024; offset++) {
            StringBuilder query = new StringBuilder();
            for (String key : keys) {
                if (query.length() > 0) query.append('&');
                String value = "_rticket".equals(key)
                        ? Long.toString(stamp + offset) : values.get(key);
                query.append(key).append('=').append(percentEncode(value == null ? "" : value));
            }
            String raw = query.toString();
            byte[] q = sm3(raw.getBytes(StandardCharsets.UTF_8));
            byte[] m = body != null && body.length > 0 ? md5(body) : new byte[16];
            int iv = passportIV(le32((int) (stamp / 1000L)),
                    passportIV(m, passportIV(q, 0x20230928)));
            if ((iv & 15) == 12) return new Tuned(raw, q, m);
        }
        throw new IllegalStateException("unable to select v5 signature branch");
    }

    private static String medusa(Map<String, String> values, int ts, byte[] q, byte[] m, int[] n,
                                 String processNonce, long processStarted) {
        byte[] hash = f13(q, m, ts);
        byte[] message = proto(values, ts, n[0], q, hash, processNonce, processStarted);
        byte[] key = sm3(concat(KEY, le32(n[1]), KEY));
        int high = (n[1] >>> 16) & 255;
        int seedValue = ~(((high << 11) | (n[1] >>> 24)) ^ (high >>> 5) ^ high);
        byte[] seed = le32(seedValue);
        byte[] p = reverse(concat(new byte[8], xmxor(message, key)));
        for (int i = 0; i < p.length; i++) p[i] ^= seed[(~i) & 3];
        int check = 0x18000001 | ((q[0] & 63) << 14) | ((hash[0] & 63) << 8);
        byte[] n1 = le32(n[1]);
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(0xca);
        write(packed, le32(n[2]));
        write(packed, le32(check));
        write(packed, p);
        packed.write(n1[2]);
        packed.write(n1[3]);
        byte[] encrypted = carrier(packed.toByteArray(), ts,
                md5(java.util.Arrays.copyOfRange(KEY, 0, 16)),
                md5(java.util.Arrays.copyOfRange(KEY, 16, 32)));
        byte[] timestamp = le32(ts);
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        write(prefix, le32(5));
        write(prefix, MAGIC);
        byte[] header = prefix.toByteArray();
        for (int i = 0; i < header.length; i++) header[i] ^= timestamp[i & 3];
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        write(result, header);
        result.write(n1[0]);
        result.write(n1[1]);
        result.write(0);
        result.write(1);
        write(result, encrypted);
        return java.util.Base64.getEncoder().encodeToString(result.toByteArray());
    }

    private static byte[] f13(byte[] q, byte[] m, int ts) {
        int seed = 0x9d4a97c2;
        int count = seed + ts;
        byte[] pad = {(byte) 0xfa, 0x45, 0x61, (byte) 0xd7};
        for (int i = 0; i < pad.length; i++) {
            pad[i] = (byte) (((pad[i] & 255) * 257) >>> ((count + 2) & 7));
        }
        ByteArrayOutputStream dataOut = new ByteArrayOutputStream();
        write(dataOut, q);
        write(dataOut, m);
        write(dataOut, le32(ts));
        write(dataOut, pad);
        write(dataOut, new byte[]{0, 0, 0, 0, 0, 0, 1, (byte) 0xa0});
        byte[] data = dataOut.toByteArray();
        int[] words = new int[128];
        for (int i = 0; i < 16; i++) words[i] = be32(data, i * 4);
        for (int i = 0; i < 112; i++) {
            int x = words[i + 1];
            int y = words[i + 14];
            words[i + 16] = words[i] + words[i + 9]
                    + (rol(x, 14) ^ rol(x, 25) ^ (x >>> 3))
                    + (rol(y, 13) ^ rol(y, 15) ^ (y >>> 10));
        }
        int[] initial = {
                0x7aba4fc8, 0x67166507, 0x6403fa00, 0x340f512f,
                0x3aab4d10, 0xb31d643a, 0xab4fabed, 0x804b3850
        };
        for (int i = 0; i < initial.length; i++) {
            initial[i] = rol(initial[i], -(count & 31));
        }
        int[] d = initial.clone();
        for (int i = 0; i < 96; i++) {
            int base = seed + i;
            int n1 = ((d[4] ^ d[0]) & d[1]) ^ d[4];
            int n2 = rol(d[1], 26) ^ rol(d[1], 21) ^ rol(d[1], 7);
            int n4 = words[base & 127] + n1 + n2
                    + rol(HASH_TABLE[base & 63], -((count + 1) & 31)) + d[6];
            int n5 = rol(d[3], 30) ^ rol(d[3], 19) ^ rol(d[3], 10);
            int n6 = (d[3] & d[5]) | ((d[3] | d[5]) & d[2]);
            int old = d[7];
            for (int j = 7; j > 0; j--) d[j] = d[j - 1];
            d[7] = n5 + n6 + n4;
            d[0] = old + n4;
        }
        byte[] ret = new byte[32];
        for (int i = 0; i < 8; i++) putBe32(ret, i * 4, d[i] + initial[i]);
        byte[] out = new byte[20];
        for (int i = 0; i < 16; i++) out[i] = (byte) (ret[i] ^ ret[i + 16]);
        int check = 0x20220420;
        for (int i = 0; i < 12; i++) {
            int x = out[i] & 255;
            if ((i & 1) == 0) {
                check = (check >>> 3) ^ check ^ x ^ (check << 7);
            } else {
                check = (check >>> 5) ^ check ^ ~(x | (check << 11));
            }
        }
        putLe32(out, 16, ((check | 4) ^ 0x1000000) | 0x20);
        return out;
    }

    private static byte[] proto(Map<String, String> values, int ts, int nonce,
                                byte[] q, byte[] hash, String processNonce, long processStarted) {
        Proto device = new Proto();
        device.sint(1, 1);
        device.sint(2, 2);
        device.text(3, "8662");
        device.text(4, value(values, "device_id"));
        device.text(5, processNonce);
        device.text(6, "!noperm!");
        device.text(12, "Asia/Shanghai,8");
        device.text(13, "zh_CN");
        device.text(22, value(values, "os_version"));
        device.sint(25, processStarted);
        device.text(30, value(values, "device_type"));
        device.text(31, "HONOR");
        device.text(32, value(values, "device_type"));
        device.text(34, "HONOR");

        Proto env = new Proto();
        env.text(6, "v04.10.03.04-bugfix");
        env.text(8, value(values, "channel"));
        env.blob(12, device.bytes());
        env.text(14, value(values, "version_name"));

        Proto counters = new Proto();
        for (int field : new int[]{1, 2, 3, 5}) counters.sint(field, 1);

        Proto p = new Proto();
        p.blob(1, MAGIC);
        p.sint(2, 5);
        p.sint(3, Integer.toUnsignedLong(nonce));
        p.text(4, "8662");
        p.text(5, value(values, "device_id"));
        p.text(6, LICENSE);
        p.text(7, value(values, "version_name"));
        p.text(8, "v04.10.03-ml-android");
        p.sint(9, 0x040a0300);
        p.blob(10, new byte[8]);
        p.sint(12, ts);
        p.blob(13, hash);
        p.blob(14, java.util.Arrays.copyOfRange(q, 0, 6));
        p.blob(15, counters.bytes());
        p.sint(17, ts);
        p.text(20, "none");
        p.sint(21, 377);
        p.blob(23, env.bytes());
        p.text(24, "{\"cmr\":16777216,\"cmr2\":16777216,\"un_h\":11,\"vpn\":0,"
                + "\"kd\":0,\"dyn\":\"\",\"do\":1,\"tk\":true}");
        return p.bytes();
    }

    private static final class Proto {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void sint(int field, long value) {
            varint(field * 8);
            varint(value < 0 ? -value * 2 - 1 : value * 2);
        }

        void blob(int field, byte[] value) {
            if (value == null || value.length == 0) return;
            varint(field * 8 + 2);
            varint(value.length);
            write(out, value);
        }

        void text(int field, String value) {
            blob(field, (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }

        private void varint(long value) {
            while (value > 127) {
                out.write((int) (value % 128) | 128);
                value /= 128;
            }
            out.write((int) value);
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }

    private static byte[] xmxor(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            int k = (i * 4) & 28;
            int a = key[k] & 255;
            int b = key[k + 1] & 255;
            int v = (~(rol8(data[i] & 255, 4) + a) ^ b) & 255;
            v = (rol8(v, 3) + b) & 255;
            out[out.length - 1 - i] = (byte) ~(v ^ a);
        }
        int flag = (out[out.length - 1] ^ out[out.length - 2]) & 255;
        int old = out[0] & 255;
        out[0] = (byte) (out[0] + ~flag);
        out[1] = (byte) (out[1] + ((out[0] & 255) ^ (out[out.length - 1] & 255) ^ 254));
        out[2] = (byte) (out[2] + ((flag - old) ^ rol8(out[1] & 255, 3) ^ 2));
        for (int i = 0; i < out.length - 4; i++) {
            out[i + 3] = (byte) (out[i + 3]
                    + ~(rol8(out[i + 2] & 255, 3) ^ (out[i + 1] & 255) ^ (i + 3)));
        }
        out[out.length - 1] ^= out[out.length - 2];
        int sum = 0;
        for (int i = 1; i < out.length; i++) sum = (sum + (out[i] & 255)) & 255;
        out[0] = (byte) (((out[0] & 255) ^ (out[1] & 255)) + sum);
        return out;
    }

    private static byte[] carrier(byte[] data, int ts, byte[] key, byte[] iv) {
        if (data.length < 248 || key.length != 16 || iv.length != 16) {
            throw new IllegalStateException("signature payload is too short");
        }
        int branch = ts & 3;
        int[][] cons = {{2, 0, 3, 1}, {1, 3, 2, 0}, {3, 2, 1, 0}, {2, 3, 1, 0}};
        int[][] orders = {
                {0, 9, 14, 15, 4, 13, 2, 7, 8, 1, 6, 3, 12, 5, 10, 11},
                {0, 9, 14, 11, 4, 13, 2, 3, 8, 1, 6, 15, 12, 5, 10, 7},
                {0, 9, 14, 3, 4, 13, 2, 15, 8, 1, 6, 11, 12, 5, 10, 7},
                {0, 9, 14, 15, 4, 13, 2, 3, 8, 1, 6, 11, 12, 5, 10, 7}
        };
        int[] init = {0xbbb5de72, 0xd4638d5b, 0x92a5f73b, 0x89f8a1c9};
        int[] con = cons[branch];
        int[] order = orders[branch];
        int initValue = init[branch];
        byte[] box = java.util.Arrays.copyOfRange(SBOX, branch * 256, branch * 256 + 256);
        byte[] mk = new byte[48];
        for (int j = 0; j < 16; j++) {
            mk[j] = (byte) (key[j] ^ ((initValue >>> ((j % 4) * 8)) & 255));
        }
        int r = 8;
        for (int j = 4; j < 12; j++) {
            byte[] prev = java.util.Arrays.copyOfRange(mk, j * 4 - 4, j * 4);
            if (j % 4 == 0) {
                prev = new byte[]{
                        (byte) (((initValue >>> (r & 24)) & 255) ^ box[prev[1] & 255]),
                        box[prev[2] & 255], box[prev[3] & 255], box[prev[0] & 255]
                };
            }
            r += 2;
            for (int k = 0; k < 4; k++) {
                mk[j * 4 + k] = (byte) (prev[k] ^ mk[j * 4 + k - 16]);
            }
        }
        byte[] encrypted = new byte[32];
        byte[] previous = iv.clone();
        byte[] plain = new byte[32];
        for (int j = 0; j < 31; j++) {
            for (int k = 0; k < 8; k++) {
                plain[j] |= (byte) (((data[j * 8 + k] >>> wire(k)) & 1) << compact(k));
            }
        }
        plain[31] = 1;
        for (int j = 0; j < 32; j += 16) {
            for (int k = 0; k < 16; k++) plain[j + k] ^= previous[k];
            previous = carrierBlock(java.util.Arrays.copyOfRange(plain, j, j + 16), mk, box, con, order);
            System.arraycopy(previous, 0, encrypted, j, 16);
        }
        byte[] out = new byte[data.length + 1];
        out[0] = encrypted[31];
        System.arraycopy(data, 0, out, 1, data.length);
        for (int j = 0; j < 31; j++) {
            for (int k = 0; k < 8; k++) {
                int at = 1 + j * 8 + k;
                int mask = 1 << wire(k);
                out[at] = (byte) ((out[at] & ~mask)
                        | (((encrypted[j] >>> compact(k)) & 1) << wire(k)));
            }
        }
        return out;
    }

    private static byte[] carrierBlock(byte[] src, byte[] mk, byte[] box, int[] con, int[] order) {
        byte[] b = src.clone();
        add(b, mk, con, 0);
        for (int round = 1; round <= 2; round++) {
            byte[] prev = b.clone();
            for (int z = 0; z < b.length; z++) {
                int index = order[z];
                b[z] = box[prev[con[index / 4] * 4 + (index % 4)] & 255];
            }
            if (round == 1) {
                prev = b.clone();
                for (int z = 0; z < b.length; z++) {
                    b[z] = prev[(z / 4) * 4 + con[z % 4]];
                }
                for (int col = 0; col < 4; col++) {
                    int aa = b[col] & 255;
                    int cc = b[4 + col] & 255;
                    int dd = b[8 + col] & 255;
                    int ee = b[12 + col] & 255;
                    int t = aa ^ cc ^ dd ^ ee;
                    b[col] ^= (byte) (t ^ xt(aa ^ cc));
                    b[4 + col] ^= (byte) (t ^ xt(cc ^ dd));
                    b[8 + col] ^= (byte) (t ^ xt(dd ^ ee));
                    b[12 + col] ^= (byte) (t ^ xt(ee ^ aa));
                }
            }
            add(b, mk, con, round * 16);
        }
        for (int z = 0; z < b.length; z++) b[z] ^= mk[16 + z];
        return b;
    }

    private static void add(byte[] b, byte[] mk, int[] con, int base) {
        for (int z = 0; z < b.length; z++) {
            b[z] ^= mk[base + (z / 4) * 4 + con[z % 4]];
        }
    }

    private static int xt(int x) {
        return (x & 128) != 0 ? ((x << 1) ^ 0x1b) & 255 : (x << 1) & 255;
    }

    private static int wire(int k) {
        return new int[]{6, 5, 3, 4, 2, 7, 1, 0}[k];
    }

    private static int compact(int k) {
        return new int[]{1, 7, 3, 6, 2, 0, 4, 5}[k];
    }

    private static String helios(int ts, int nonce) {
        byte[] key = md5(concat(le32(nonce), "8662".getBytes(StandardCharsets.US_ASCII)));
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
        byte[] text = (ts + "-" + LICENSE + "-8662").getBytes(StandardCharsets.US_ASCII);
        int pad = 16 - text.length % 16;
        byte[] plain = new byte[text.length + pad];
        System.arraycopy(text, 0, plain, 0, text.length);
        java.util.Arrays.fill(plain, text.length, plain.length, (byte) pad);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, le32(nonce));
        for (int off = 0; off < plain.length; off += 16) {
            long a = le64(plain, off);
            long b = le64(plain, off + 8);
            for (long round : rounds) {
                b = round ^ (a + Long.rotateRight(b, 8));
                a = b ^ Long.rotateLeft(a, 3);
            }
            write(out, le64(a));
            write(out, le64(b));
        }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static int passportIV(byte[] data, int iv) {
        for (int i = 0; i < data.length; i++) {
            int x = data[i] & 255;
            if ((i & 1) == 0) {
                iv = (iv >>> 4) ^ iv ^ (iv << 6) ^ x;
            } else {
                iv = ~((iv >>> 7) ^ iv ^ (x | (iv << 12)));
            }
        }
        return iv;
    }

    private static byte[] sm3(byte[] data) {
        return PassportSigner.sm3ForPlayback(data);
    }

    private static byte[] sm3Legacy(byte[] data) {
        long bitLength = (long) data.length * 8;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        write(buf, data);
        buf.write(0x80);
        while (buf.size() % 64 != 56) buf.write(0);
        write(buf, be64(bitLength));
        byte[] all = buf.toByteArray();
        int[] v = {0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
                0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e};
        for (int off = 0; off < all.length; off += 64) {
            int[] w = new int[68];
            for (int i = 0; i < 16; i++) w[i] = be32(all, off + i * 4);
            for (int i = 16; i < 68; i++) {
                int x = w[i - 16] ^ w[i - 9] ^ rol(w[i - 3], 15);
                w[i] = (x ^ rol(x, 9) ^ rol(x, 17)) ^ rol(w[i - 13], 7) ^ w[i - 6];
            }
            int a = v[0], b = v[1], c = v[2], d = v[3];
            int e = v[4], f = v[5], g = v[6], h = v[7];
            for (int j = 0; j < 64; j++) {
                int t = j < 16 ? 0x79cc4519 : 0x7a879d8a;
                int ff = j < 16 ? (a ^ b ^ c) : ((a & b) | (a & c) | (b & c));
                int gg = j < 16 ? (e ^ f ^ g) : ((e & f) | (~e & g));
                int s1 = rol(rol(a, 12) + e + rol(t, j), 7);
                int s2 = s1 ^ rol(a, 12);
                int u1 = ff + d + s2 + (w[j] ^ w[j + 4]);
                int u2 = gg + h + s1 + w[j];
                int oldE = e;
                d = c;
                c = rol(b, 9);
                b = a;
                a = u1;
                h = g;
                g = rol(f, 19);
                f = oldE;
                e = u2 ^ rol(u2, 9) ^ rol(u2, 17);
            }
            v[0] ^= a;
            v[1] ^= b;
            v[2] ^= c;
            v[3] ^= d;
            v[4] ^= e;
            v[5] ^= f;
            v[6] ^= g;
            v[7] ^= h;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) putBe32(out, i * 4, v[i]);
        return out;
    }

    private static int rol(int x, int n) {
        return Integer.rotateLeft(x, n & 31);
    }

    private static int rol8(int x, int n) {
        n &= 7;
        return ((x << n) | (x >>> (8 - n))) & 255;
    }

    private static String percentEncode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            int b = value & 255;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                    || (b >= '0' && b <= '9') || b == '-' || b == '_'
                    || b == '.' || b == '~') {
                out.append((char) b);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(b >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 15, 16)));
            }
        }
        return out.toString();
    }

    private static String value(Map<String, String> values, String key) {
        String v = values.get(key);
        return v == null ? "" : v;
    }

    private static byte[] reverse(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = data[data.length - 1 - i];
        return out;
    }

    private static byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private static String base64Url(byte[] data) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] a : arrays) length += a.length;
        byte[] out = new byte[length];
        int at = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, at, a.length);
            at += a.length;
        }
        return out;
    }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
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

    private static String hexUpper(byte[] data) {
        return hex(data).toUpperCase(java.util.Locale.US);
    }

    private static byte[] le32(int n) {
        return new byte[]{(byte) n, (byte) (n >>> 8), (byte) (n >>> 16), (byte) (n >>> 24)};
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 255) | ((b[off + 1] & 255) << 8)
                | ((b[off + 2] & 255) << 16) | ((b[off + 3] & 255) << 24);
    }

    private static void putLe32(byte[] b, int off, int n) {
        b[off] = (byte) n;
        b[off + 1] = (byte) (n >>> 8);
        b[off + 2] = (byte) (n >>> 16);
        b[off + 3] = (byte) (n >>> 24);
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 255) << 24) | ((b[off + 1] & 255) << 16)
                | ((b[off + 2] & 255) << 8) | (b[off + 3] & 255);
    }

    private static void putBe32(byte[] b, int off, int n) {
        b[off] = (byte) (n >>> 24);
        b[off + 1] = (byte) (n >>> 16);
        b[off + 2] = (byte) (n >>> 8);
        b[off + 3] = (byte) n;
    }

    private static byte[] be64(long n) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) b[i] = (byte) (n >>> (56 - i * 8));
        return b;
    }

    private static long le64(byte[] b, int off) {
        long out = 0;
        for (int i = 0; i < 8; i++) out |= (long) (b[off + i] & 255) << (i * 8);
        return out;
    }

    private static byte[] le64(long n) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) b[i] = (byte) (n >>> (i * 8));
        return b;
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }
}
