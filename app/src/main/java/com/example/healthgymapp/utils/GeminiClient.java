package com.example.healthgymapp.utils;

import com.example.healthgymapp.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gọi Gemini để nhận kết quả dạng JSON (thực đơn, công thức...).
 * Tự thử lại khi máy chủ quá tải và tự chuyển sang model dự phòng.
 * Callback được gọi trên LUỒNG NỀN: nơi gọi phải dùng runOnUiThread khi cập nhật giao diện.
 */
public final class GeminiClient {

    public interface Callback {
        void onSuccess(String jsonText);

        void onError(String message);
    }

    private static final String[] MODELS = {"gemini-3.6-flash", "gemini-3.5-flash-lite"};
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_ATTEMPTS_PER_MODEL = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private GeminiClient() { }

    private static class Result {
        final int code;
        final String body;
        final Exception error;

        Result(int code, String body, Exception error) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.error = error;
        }

        boolean isSuccess() {
            return error == null && code == HttpURLConnection.HTTP_OK;
        }

        boolean isModelNotFound() {
            return error == null && code == 404;
        }

        boolean isRetryable() {
            if (error != null) return true;
            return code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
        }
    }

    /** Gửi yêu cầu và nhận về MỘT đối tượng JSON (đã được cắt sạch phần thừa). */
    public static void generateJson(String systemPrompt, String userPrompt, double temperature, Callback callback) {
        EXECUTOR.execute(() -> {
            String key = BuildConfig.GEMINI_API_KEY;
            if (key == null || key.isEmpty()) {
                callback.onError("Chưa cấu hình GEMINI_API_KEY trong local.properties.");
                return;
            }
            try {
                JSONObject body = buildBody(systemPrompt, userPrompt, temperature);

                Result result = null;
                outer:
                for (int m = 0; m < MODELS.length; m++) {
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
                        result = callOnce(MODELS[m], body, key);
                        if (result.isSuccess()) break outer;
                        if (result.isModelNotFound()) break;       // sang model dự phòng
                        if (!result.isRetryable()) break outer;    // lỗi key/yêu cầu: thử lại vô ích
                        if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                            Thread.sleep(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)));
                        }
                    }
                }

                if (result != null && result.isSuccess()) {
                    String json = extractJsonObject(extractText(result.body));
                    if (json == null) {
                        callback.onError("AI trả về dữ liệu không đúng định dạng. Hãy thử lại.");
                    } else {
                        callback.onSuccess(json);
                    }
                } else {
                    callback.onError(friendlyError(result));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                callback.onError("Có lỗi khi xử lý phản hồi của AI: " + e.getMessage());
            }
        });
    }

    private static JSONObject buildBody(String systemPrompt, String userPrompt, double temperature) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))));
        body.put("contents", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", userPrompt)))));
        body.put("generationConfig", new JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", temperature));
        return body;
    }

    private static Result callOnce(String model, JSONObject body, String apiKey) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_BASE + model + ":generateContent").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String text = readStream(code == HttpURLConnection.HTTP_OK
                    ? conn.getInputStream() : conn.getErrorStream());
            return new Result(code, text, null);
        } catch (Exception e) {
            return new Result(-1, "", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** Ghép các phần text trong phản hồi của Gemini. */
    private static String extractText(String raw) throws Exception {
        JSONArray candidates = new JSONObject(raw).optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return "";
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) return "";
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            sb.append(parts.getJSONObject(i).optString("text", ""));
        }
        return sb.toString();
    }

    /** Cắt lấy đoạn JSON từ dấu { đầu tiên đến dấu } cuối cùng và kiểm tra hợp lệ. */
    private static String extractJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String candidate = text.substring(start, end + 1);
        try {
            new JSONObject(candidate);
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private static String friendlyError(Result r) {
        if (r == null) return "Không thể kết nối tới AI. Bạn thử lại nhé.";
        if (r.error != null) return "Không kết nối được mạng. Hãy kiểm tra Internet rồi thử lại.";
        switch (r.code) {
            case 400:
                if (r.body.contains("API_KEY_INVALID")) {
                    return "API key không hợp lệ. Hãy kiểm tra GEMINI_API_KEY trong local.properties.";
                }
                return "Yêu cầu không hợp lệ (HTTP 400).";
            case 401:
            case 403:
                return "API key không có quyền sử dụng dịch vụ này (HTTP " + r.code + ").";
            case 404:
                return "Model AI không còn khả dụng (HTTP 404). Cần cập nhật tên model.";
            case 429:
                return "Đã gửi quá nhiều yêu cầu hoặc hết hạn mức. Hãy chờ một lúc rồi thử lại (HTTP 429).";
            default:
                if (r.code >= 500) return "AI đang quá tải, hãy thử lại sau ít giây (HTTP " + r.code + ").";
                return "Có lỗi không xác định (HTTP " + r.code + ").";
        }
    }
}