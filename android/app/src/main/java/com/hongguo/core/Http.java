package com.hongguo.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny HttpURLConnection wrapper with timeouts and error-body capture. */
public final class Http {
    public static final String UA = "com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; "
            + "25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 "
            + "QuicVersion:c67e9834 2025-09-08)";

    public static final class Reply {
        public final int status;
        public final byte[] body;

        Reply(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public boolean ok() {
            return status == 200 && body.length > 0;
        }
    }

    private Http() {
    }

    public static Reply get(String url, Map<String, String> headers) throws Exception {
        return get(url, headers, 15000, 30000);
    }

    public static Reply get(String url, Map<String, String> headers,
                            int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        apply(connection, headers);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        try {
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Reply(status, readAll(input));
        } finally {
            connection.disconnect();
        }
    }

    public static Reply post(String url, byte[] body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        apply(connection, headers);
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Reply(status, readAll(input));
        } finally {
            connection.disconnect();
        }
    }

    private static void apply(HttpURLConnection connection, Map<String, String> headers) {
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    public static byte[] readAll(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = input.read(buffer)) >= 0) out.write(buffer, 0, count);
        return out.toByteArray();
    }
}
