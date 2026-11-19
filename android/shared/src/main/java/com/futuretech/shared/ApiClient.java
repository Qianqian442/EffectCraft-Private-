package com.futuretech.shared;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** HTTPS-only API client. Never logs credentials, response bodies or bearer tokens. */
public final class ApiClient {
    private static final int CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(12);
    private static final int READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(20);
    private ApiClient() {}

    public static JSONObject request(String baseUrl, String method, String path,
                                     JSONObject body, String bearerToken) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty())
            throw new IllegalStateException("服务器地址尚未配置，请先完成服务器部署。");
        URL url = new URL(baseUrl.trim().replaceAll("/+$", "") + path);
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new SecurityException("账户接口只允许 HTTPS 连接。");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (bearerToken != null && !bearerToken.isEmpty())
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "{}" : readFully(stream);
            JSONObject result;
            try { result = new JSONObject(response); }
            catch (Exception ex) { throw new IllegalStateException("服务器返回格式无效（HTTP " + status + "）。"); }
            if (status < 200 || status >= 300)
                throw new ApiException(status, result.optString("message", "服务器请求失败"));
            return result;
        } finally { connection.disconnect(); }
    }

    private static String readFully(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class ApiException extends Exception {
        public final int statusCode;
        public ApiException(int code, String message) { super(message); statusCode = code; }
    }
}
