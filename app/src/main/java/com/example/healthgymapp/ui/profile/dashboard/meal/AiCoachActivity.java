package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.exifinterface.media.ExifInterface;

import com.example.healthgymapp.BuildConfig;
import com.example.healthgymapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiCoachActivity extends AppCompatActivity {

    private static final String TAG = "AiCoach";

    // Model theo thứ tự ưu tiên. Model đầu bị 404 thì tự chuyển sang model sau.
    private static final String[] MODELS = {
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest"
    };
    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final int MAX_ATTEMPTS_PER_MODEL = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000;

    private static final String SYSTEM_PROMPT =
            "Bạn là huấn luyện viên gym và chuyên gia dinh dưỡng. "
                    + "Trả lời bằng tiếng Việt, ngắn gọn, dễ hiểu, thực tế, dùng văn bản thuần, "
                    + "KHÔNG dùng ký hiệu markdown như ** hoặc # vì ứng dụng không hiển thị được. "
                    + "Tư vấn lịch tập, chế độ ăn theo mục tiêu (tăng cơ, giảm mỡ, duy trì). "
                    + "Nếu câu hỏi liên quan bệnh lý, nhắc người dùng hỏi bác sĩ. "
                    + "Khi người dùng gửi ảnh món ăn: nếu ảnh không phải đồ ăn thì nói rõ. "
                    + "Nếu là đồ ăn thì trả lời theo thứ tự: tên món và khẩu phần ước tính; tổng kcal; "
                    + "protein/carb/béo (gram); đánh giá chất béo "
                    + "(Tốt: không bão hòa như cá béo, bơ, hạt, dầu ô liu; Trung bình: bão hòa vừa phải; "
                    + "Nên hạn chế: chiên rán ngập dầu, đồ chế biến sẵn, nguy cơ chất béo chuyển hóa) kèm lý do ngắn; "
                    + "một lời khuyên cho người tập gym; cuối cùng nhắc đây là số liệu ước tính, có thể lệch 20-30%.";

    private static final String DEFAULT_FOOD_QUESTION =
            "Hãy ước tính calories, protein, carb, chất béo và đánh giá chất béo tốt hay xấu của món ăn này.";

    private static final String STATUS_TYPING = "AI Coach đang trả lời...";
    private static final int MAX_HISTORY_ITEMS = 20;
    private static final int MAX_IMAGE_SIDE = 1024;

    private EditText edtUserMessage;
    private Button btnSend, btnAttach, btnRemoveImage;
    private TextView tvChatResponse;
    private View layoutPreview;
    private ImageView imgPreview;

    private Uri photoUri;
    private Bitmap pendingBitmap;   // ảnh đang đính kèm, chưa gửi

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Nội dung khung chat đã "chốt". Chỉ đụng tới trên UI thread.
    private final StringBuilder chatLog = new StringBuilder();

    // Lịch sử hội thoại (chỉ chữ, không lưu ảnh). Chỉ truy cập trong executor.
    private final JSONArray history = new JSONArray();

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok) loadBitmap(photoUri);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) loadBitmap(uri);
            });

    /** Kết quả một lần gọi API. */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_coach);

        edtUserMessage = findViewById(R.id.edtUserMessage);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);
        tvChatResponse = findViewById(R.id.tvChatResponse);
        layoutPreview = findViewById(R.id.layoutPreview);
        imgPreview = findViewById(R.id.imgPreview);

        View header = findViewById(R.id.headerBar);
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            header.setPadding(header.getPaddingLeft(), bars.top, header.getPaddingRight(), 0);
            int bottom = Math.max(bars.bottom, ime.bottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            if (ime.bottom > 0) scrollToBottom();
            return windowInsets;
        });

        chatLog.append(tvChatResponse.getText());

        btnSend.setOnClickListener(v -> sendMessage());
        btnAttach.setOnClickListener(this::showAttachMenu);
        btnRemoveImage.setOnClickListener(v -> clearAttachment());

        edtUserMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    // ---------- Chọn / chụp ảnh ----------

    private void showAttachMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "📷 Chụp ảnh món ăn");
        menu.getMenu().add(0, 2, 1, "🖼️ Chọn từ thư viện");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                File dir = new File(getCacheDir(), "images");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, "food_" + System.currentTimeMillis() + ".jpg");
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                takePicture.launch(photoUri);
            } else {
                pickImage.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            }
            return true;
        });
        menu.show();
    }

    // Đọc ảnh, thu nhỏ và xoay đúng chiều
    private void loadBitmap(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) throw new IOException("Không đọc được ảnh");

            int degrees = 0;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                int o = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            }

            float scale = Math.min(1f, (float) MAX_IMAGE_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(degrees);
            pendingBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

            imgPreview.setImageBitmap(pendingBitmap);
            layoutPreview.setVisibility(View.VISIBLE);
            edtUserMessage.setHint("Hỏi thêm về món này (hoặc bấm Gửi)...");
        } catch (Exception e) {
            Log.e(TAG, "loadBitmap", e);
            chatLog.append("\n\n⚠️ Không đọc được ảnh, hãy thử ảnh khác.");
            tvChatResponse.setText(chatLog.toString());
        }
    }

    private void clearAttachment() {
        pendingBitmap = null;
        layoutPreview.setVisibility(View.GONE);
        imgPreview.setImageDrawable(null);
        edtUserMessage.setHint("Nhập câu hỏi cho AI Coach...");
    }

    // ---------- Gửi tin ----------

    private void sendMessage() {
        if (!btnSend.isEnabled()) return;

        String text = edtUserMessage.getText().toString().trim();
        Bitmap image = pendingBitmap;
        if (text.isEmpty() && image == null) return;

        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            Toast.makeText(this, "Chưa cấu hình GEMINI_API_KEY trong local.properties",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String prompt = text.isEmpty() ? DEFAULT_FOOD_QUESTION : text;
        chatLog.append("\n\n🧑 Bạn: ")
                .append(image != null ? "📷 [ảnh món ăn] " : "")
                .append(text);
        edtUserMessage.setText("");
        clearAttachment();
        setLoading(true);
        callGemini(prompt, image);
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        btnAttach.setEnabled(!loading);
        if (loading) {
            tvChatResponse.setText(chatLog + "\n\n🤖 " + STATUS_TYPING);
        } else {
            tvChatResponse.setText(chatLog.toString());
        }
        scrollToBottom();
    }

    private void scrollToBottom() {
        View v = tvChatResponse;
        while (v != null && !(v instanceof ScrollView) && !(v instanceof NestedScrollView)
                && v.getParent() instanceof View) {
            v = (View) v.getParent();
        }
        final View scroller = v;
        if (scroller instanceof ScrollView) {
            scroller.post(() -> ((ScrollView) scroller).fullScroll(View.FOCUS_DOWN));
        } else if (scroller instanceof NestedScrollView) {
            scroller.post(() -> ((NestedScrollView) scroller).fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showStatus(String status) {
        runOnUiThread(() -> tvChatResponse.setText(chatLog + "\n\n🤖 " + status));
    }

    private void callGemini(String userPrompt, Bitmap image) {
        executor.execute(() -> {
            boolean userTurnAdded = false;
            try {
                // Lịch sử chỉ lưu chữ (kèm ghi chú nếu có ảnh)
                String historyText = (image != null ? "[Người dùng đã gửi ảnh món ăn] " : "") + userPrompt;
                history.put(buildContent("user", historyText));
                userTurnAdded = true;
                trimHistory();

                // Body dựng một lần (mã hóa ảnh một lần) rồi dùng lại cho mọi lần thử
                JSONObject body = buildRequestBody(userPrompt, image);

                Result result = null;
                outer:
                for (int m = 0; m < MODELS.length; m++) {
                    String model = MODELS[m];

                    for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
                        result = callOnce(model, body);

                        if (result.isSuccess()) break outer;
                        if (result.isModelNotFound()) break;
                        if (!result.isRetryable()) break outer;

                        if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                            long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                            showStatus("AI đang bận, thử lại sau " + (delay / 1000)
                                    + " giây (lần " + (attempt + 1) + "/" + MAX_ATTEMPTS_PER_MODEL + ")...");
                            Thread.sleep(delay);
                            showStatus(STATUS_TYPING);
                        }
                    }

                    if (m < MODELS.length - 1) showStatus("Đang chuyển sang model dự phòng...");
                }

                if (result != null && result.isSuccess()) {
                    String reply = parseReply(result.body);
                    history.put(buildContent("model", reply));
                    runOnUiThread(() -> {
                        chatLog.append("\n\n🤖 AI Coach: ").append(reply);
                        setLoading(false);
                    });
                } else {
                    rollbackUserTurn();
                    showError(friendlyError(result));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (userTurnAdded) rollbackUserTurn();
            } catch (Exception e) {
                Log.e(TAG, "Lỗi xử lý phản hồi", e);
                if (userTurnAdded) rollbackUserTurn();
                showError("Có lỗi khi xử lý câu trả lời. Bạn thử lại nhé.");
            }
        });
    }

    /** contents = lịch sử (chỉ chữ) + lượt hiện tại (có thể kèm ảnh) + system instruction. */
    private JSONObject buildRequestBody(String userPrompt, Bitmap image) throws Exception {
        JSONArray contents = new JSONArray();
        // Lượt cuối của history chính là lượt hiện tại, thay bằng bản có ảnh
        for (int i = 0; i < history.length() - 1; i++) contents.put(history.get(i));

        JSONArray parts = new JSONArray();
        if (image != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            parts.put(new JSONObject().put("inline_data",
                    new JSONObject().put("mime_type", "image/jpeg").put("data", b64)));
        }
        parts.put(new JSONObject().put("text", userPrompt));
        contents.put(new JSONObject().put("role", "user").put("parts", parts));

        JSONObject body = new JSONObject();
        body.put("contents", contents);
        body.put("systemInstruction", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT))));
        return body;
    }

    private Result callOnce(String model, JSONObject body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_BASE + model + ":generateContent").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String text = readStream(code == HttpURLConnection.HTTP_OK
                    ? conn.getInputStream() : conn.getErrorStream());

            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Model " + model + " -> HTTP " + code + ": " + text);
            }
            return new Result(code, text, null);

        } catch (Exception e) {
            Log.e(TAG, "Lỗi mạng khi gọi model " + model, e);
            return new Result(-1, "", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String friendlyError(Result r) {
        if (r == null) return "Không thể kết nối tới AI Coach. Bạn thử lại nhé.";
        if (r.error != null) return "Không kết nối được mạng. Hãy kiểm tra Internet rồi thử lại.";
        switch (r.code) {
            case 400:
                if (r.body.contains("API_KEY_INVALID")) {
                    return "API key không hợp lệ. Hãy kiểm tra GEMINI_API_KEY trong local.properties rồi build lại.";
                }
                return "Yêu cầu không hợp lệ (HTTP 400). Bạn thử diễn đạt lại hoặc dùng ảnh khác.";
            case 401:
            case 403:
                return "API key không có quyền sử dụng dịch vụ này (HTTP " + r.code + ").";
            case 404:
                return "Model AI hiện không còn khả dụng (HTTP 404). Cần cập nhật tên model trong code.";
            case 429:
                return "Bạn đã gửi quá nhiều yêu cầu hoặc hết hạn mức. Hãy chờ một lúc rồi thử lại (HTTP 429).";
            default:
                if (r.code >= 500) {
                    return "AI đang quá tải, bạn vui lòng thử lại sau ít giây (HTTP " + r.code + ").";
                }
                return "Có lỗi không xác định (HTTP " + r.code + ").";
        }
    }

    private JSONObject buildContent(String role, String text) throws Exception {
        return new JSONObject()
                .put("role", role)
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    /** Lấy text từ JSON trả về. Không bao giờ trả chuỗi rỗng (tránh làm hỏng lịch sử). */
    private String parseReply(String raw) throws Exception {
        String fallback = "Xin lỗi, mình chưa tạo được câu trả lời. Bạn thử diễn đạt lại nhé.";
        JSONArray candidates = new JSONObject(raw).optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return fallback;
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) return fallback;
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) return fallback;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            sb.append(parts.getJSONObject(i).optString("text", ""));
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private void trimHistory() {
        while (history.length() > MAX_HISTORY_ITEMS) {
            history.remove(0);
            history.remove(0);
        }
    }

    private void rollbackUserTurn() {
        if (history.length() > 0) history.remove(history.length() - 1);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            chatLog.append("\n\n⚠️ ").append(message);
            setLoading(false);
            Toast.makeText(AiCoachActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}