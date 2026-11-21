package com.hongguo.core;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Anonymous device_register bootstrap, ported from passport_device.go.
 * No phone number, login code, or account token is used.
 */
public final class DeviceRegister {
    private static final String DEVICE_API = "https://log.snssdk.com";
    private static final String UA = "com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; "
            + "25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 "
            + "QuicVersion:c67e9834 2025-09-08)";
    private static final byte[] SALT = hex(
            "4dd4c2e6b83162090e52b3c7a6733ba41cb2462b829ab58a196b39db57177524"
                    + "f49baf7f08e8d68d26a72e37c1a95a2f1f05a51892aef2949732b62a38aadd58");

    private DeviceRegister() {
    }

    public static DeviceSession register() throws Exception {
        String cdid = UUID.randomUUID().toString();
        String clientudid = UUID.randomUUID().toString();
        byte[] openBytes = new byte[8];
        new SecureRandom().nextBytes(openBytes);
        String openudid = hex(openBytes);
        long created = System.currentTimeMillis();

        JSONObject header = new JSONObject();
        header.put("aid", 8662);
        header.put("access", "wifi");
        header.put("app_version", "7.3.5.32");
        header.put("app_version_minor", "");
        header.put("channel", "update_64");
        header.put("device_brand", "Redmi");
        header.put("device_manufacturer", "Redmi");
        header.put("device_model", "25053RT47C");
        header.put("device_category", "phone");
        header.put("device_platform", "android");
        header.put("os", "Android");
        header.put("os_api", 36);
        header.put("os_version", "16");
        header.put("density_dpi", 520);
        header.put("display_density", "xxxhdpi");
        header.put("resolution", "2772x1280");
        header.put("language", "zh");
        header.put("timezone", 8);
        header.put("tz_name", "Asia/Shanghai");
        header.put("tz_offset", 28800);
        header.put("region", "CN");
        header.put("package", "com.phoenix.read");
        header.put("version_code", 73532);
        header.put("manifest_version_code", 73532);
        header.put("update_version_code", 73532);
        header.put("apk_first_install_time", created);
        header.put("cdid", cdid);
        header.put("clientudid", clientudid);
        header.put("openudid", openudid);
        header.put("cpu_abi", "arm64-v8a");
        header.put("display_name", "开心果短剧");
        header.put("sdk_version", "3.7.2");
        header.put("sdk_target_version", 29);
        header.put("sdk_flavor", "china");
        header.put("guest_mode", 0);
        header.put("is_system_app", 0);
        header.put("not_request_sender", 0);
        header.put("oaid_may_support", true);
        header.put("ipv6_list", new org.json.JSONArray());
        header.put("sim_serial_number", new org.json.JSONArray());
        JSONObject custom = new JSONObject();
        custom.put("is_android_fold", 0);
        custom.put("filter_warn", 0);
        custom.put("web_ua", "");
        custom.put("is_android_pad", 0);
        custom.put("client_ipv4", "");
        header.put("custom", custom);

        JSONObject payload = new JSONObject();
        payload.put("magic_tag", "ss_app_log");
        payload.put("header", header);
        payload.put("_gen_time", created);

        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        byte[] body = ttEncrypt(payload.toString().getBytes(StandardCharsets.UTF_8), seed);

        long stamp = System.currentTimeMillis();
        long now = stamp / 1000;
        TreeMap<String, String> query = baseQuery();
        query.remove("device_id");
        query.remove("iid");
        query.put("cdid", cdid);
        query.put("openudid", openudid);
        query.put("tt_data", "a");
        query.put("ts", Long.toString(now));
        query.put("_rticket", Long.toString(stamp));
        query.put("package", "com.phoenix.read");
        String raw = encodeQuery(query);
        int nonce = new SecureRandom().nextInt(65536);

        URL url = new URL(DEVICE_API + "/service/2/device_register/?" + raw);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Content-Type", "application/octet-stream;tt-data=a");
        connection.setRequestProperty("Accept", "application/json; charset=utf-8,application/x-protobuf");
        connection.setRequestProperty("Content-Encoding", "gzip");
        connection.setRequestProperty("X-Tt-Request-Tag", "t=0;n=1");
        connection.setRequestProperty("Log-Encode-Type", "gzip");
        connection.setRequestProperty("Lc", "102");
        connection.setRequestProperty("Sdk-Version", "2");
        connection.setRequestProperty("Passport-Sdk-Version", "60755");
        connection.setRequestProperty("Passport-Sdk-Settings", "device_transfer_s_0,device_transfer_ab_1");
        connection.setRequestProperty("X-SS-DP", "8662");
        connection.setRequestProperty("X-Vc-Bdturing-Sdk-Version", "4.0.3.cn");
        connection.setRequestProperty("X-Tt-Store-Region", "cn");
        connection.setRequestProperty("X-Tt-Store-Region-Src", "did");
        connection.setRequestProperty("X-Khronos", Long.toString(now));
        connection.setRequestProperty("X-Argus", Base64.getEncoder().encodeToString(le32((int) now)));
        connection.setRequestProperty("X-SS-Req-Ticket", Long.toString(stamp));
        connection.setRequestProperty("X-SS-STUB", hex(md5(body)));
        connection.setRequestProperty("X-Gorgon",
                PassportSigner.gorgon(raw, body, (int) now, nonce));
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        int status = connection.getResponseCode();
        byte[] response = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (status != 200) {
            throw new IllegalStateException("device_register HTTP " + status);
        }
        if (startsWithTc(response)) response = ttDecrypt(response);
        JSONObject json = new JSONObject(new String(response, StandardCharsets.UTF_8));
        String deviceId = firstNonEmpty(json.optString("device_id_str"),
                json.optString("device_id"));
        String installId = firstNonEmpty(json.optString("install_id_str"),
                json.optString("install_id"));
        if (!validId(deviceId) || !validId(installId) || "0".equals(deviceId) || "0".equals(installId)) {
            throw new IllegalStateException("device_register did not return a valid identity");
        }
        return new DeviceSession(deviceId, installId, cdid, openudid);
    }

    private static TreeMap<String, String> baseQuery() {
        TreeMap<String, String> q = new TreeMap<>();
        q.put("ac", "wifi");
        q.put("aid", "8662");
        q.put("app_name", "novelread");
        q.put("channel", "update_64");
        q.put("device_brand", "Redmi");
        q.put("device_platform", "android");
        q.put("device_type", "25053RT47C");
        q.put("dpi", "520");
        q.put("language", "zh");
        q.put("manifest_version_code", "73532");
        q.put("os", "android");
        q.put("os_api", "36");
        q.put("os_version", "16");
        q.put("resolution", "1280*2772");
        q.put("ssmix", "a");
        q.put("update_version_code", "73532");
        q.put("version_code", "73532");
        q.put("version_name", "7.3.5.32");
        return q;
    }

    private static byte[] ttEncrypt(byte[] plain, byte[] seed) throws Exception {
        byte[] z = gzip(plain);
        byte[] header = {(byte) 0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, 0};
        System.arraycopy(header, 0, z, 0, header.length);
        byte[] hash = sha512(z);
        byte[] p = new byte[hash.length + z.length];
        System.arraycopy(hash, 0, p, 0, hash.length);
        System.arraycopy(z, 0, p, hash.length, z.length);
        int pad = 16 - p.length % 16;
        byte[] padded = new byte[p.length + pad];
        System.arraycopy(p, 0, padded, 0, p.length);
        for (int i = p.length; i < padded.length; i++) padded[i] = (byte) pad;
        byte[] key = deviceKey(seed);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, 0, 16, "AES"),
                new IvParameterSpec(key, 16, 16));
        byte[] enc = cipher.doFinal(padded);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x74, 0x63, 5, 0x10, 0, 0});
        out.write(seed);
        out.write(enc);
        return out.toByteArray();
    }

    private static byte[] ttDecrypt(byte[] blob) throws Exception {
        if (blob.length < 38 + 80 || !startsWithTc(blob) || (blob.length - 38) % 16 != 0) {
            throw new IllegalArgumentException("bad tt response");
        }
        byte[] seed = new byte[32];
        System.arraycopy(blob, 6, seed, 0, 32);
        byte[] key = deviceKey(seed);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, 0, 16, "AES"),
                new IvParameterSpec(key, 16, 16));
        byte[] p = cipher.doFinal(blob, 38, blob.length - 38);
        int pad = p[p.length - 1] & 0xff;
        if (pad < 1 || pad > 16) throw new IllegalArgumentException("bad padding");
        int len = p.length - pad;
        if (len < 64) throw new IllegalArgumentException("bad response");
        byte[] hash = sha512(java.util.Arrays.copyOfRange(p, 64, len));
        for (int i = 0; i < 64; i++) {
            if (hash[i] != p[i]) throw new IllegalArgumentException("bad response hash");
        }
        return gunzip(java.util.Arrays.copyOfRange(p, 64, len));
    }

    private static byte[] deviceKey(byte[] seed) throws Exception {
        byte[] h = sha512(seed);
        byte[] input = new byte[h.length + SALT.length];
        System.arraycopy(h, 0, input, 0, h.length);
        System.arraycopy(SALT, 0, input, h.length, SALT.length);
        return sha512(input);
    }

    private static byte[] gzip(byte[] plain) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(plain);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            return readAll(gzip);
        }
    }

    private static byte[] sha512(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-512").digest(data);
    }

    private static byte[] md5(byte[] data) throws Exception {
        return MessageDigest.getInstance("MD5").digest(data);
    }

    private static boolean startsWithTc(byte[] data) {
        return data.length >= 6 && data[0] == 0x74 && data[1] == 0x63
                && data[2] == 5 && data[3] == 0x10 && data[4] == 0 && data[5] == 0;
    }

    private static String encodeQuery(Map<String, String> values) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private static byte[] le32(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[0-9A-Za-z_-]{1,64}");
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
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
}
