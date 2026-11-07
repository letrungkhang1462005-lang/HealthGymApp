package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.MealLog;
import com.example.healthgymapp.utils.GeminiClient;
import com.example.healthgymapp.utils.MealItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Chi tiết một bữa ăn: nguyên liệu có định lượng và cách chế biến do Gemini soạn theo đúng món đã chọn.
 * Kết quả được lưu tạm nên mở lại cùng món sẽ hiện ngay.
 */
public class MealDetailActivity extends AppCompatActivity {

    private static final String PREFS_CACHE = "meal_detail_cache";

    private static final String SYSTEM_PROMPT =
            "Bạn là đầu bếp kiêm chuyên gia dinh dưỡng thể hình tại Việt Nam. "
                    + "Chỉ trả về MỘT đối tượng JSON hợp lệ, không thêm văn bản nào khác, không dùng markdown. "
                    + "Dùng nguyên liệu dễ mua ở Việt Nam, định lượng cụ thể (gram, ml, thìa, bát), cách làm ngắn gọn.";

    private View layoutHeader, layoutDetailLoading, progressDetail;
    private ImageView imgMeal;
    private TextView btnBack, tvMealTitle, tvMealTypeTag, tvMealGoalTag, tvMealNutriSummary;
    private TextView tvCookingSteps, tvTip, tvDetailStatus;
    private LinearLayout layoutIngredients;
    private Button btnSaveToLog;

    private MealItem meal;
    private String goalText = "";
    private int requestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_detail);

        layoutHeader = findViewById(R.id.layoutHeader);
        layoutDetailLoading = findViewById(R.id.layoutDetailLoading);
        progressDetail = findViewById(R.id.progressDetail);
        imgMeal = findViewById(R.id.imgMeal);
        btnBack = findViewById(R.id.btnBack);
        tvMealTitle = findViewById(R.id.tvMealTitle);
        tvMealTypeTag = findViewById(R.id.tvMealTypeTag);
        tvMealGoalTag = findViewById(R.id.tvMealGoalTag);
        tvMealNutriSummary = findViewById(R.id.tvMealNutriSummary);
        tvCookingSteps = findViewById(R.id.tvCookingSteps);
        tvTip = findViewById(R.id.tvTip);
        tvDetailStatus = findViewById(R.id.tvDetailStatus);
        layoutIngredients = findViewById(R.id.layoutIngredients);
        btnSaveToLog = findViewById(R.id.btnSaveToLog);

        applyHeaderInsets();
        btnBack.setOnClickListener(v -> finish());

        // Nhận món ăn từ màn gợi ý
        String mealJson = getIntent().getStringExtra("MEAL_JSON");
        goalText = getIntent().getStringExtra("USER_GOAL") != null
                ? getIntent().getStringExtra("USER_GOAL") : "";
        try {
            meal = MealItem.fromJson(new JSONObject(mealJson == null ? "{}" : mealJson));
        } catch (JSONException e) {
            meal = null;
        }
        if (meal == null || meal.name.isEmpty()) {
            Toast.makeText(this, "Không đọc được thông tin món ăn", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Thông tin có sẵn từ thực đơn: hiện ngay
        tvMealTitle.setText(meal.name);
        tvMealTypeTag.setText(meal.type);
        tvMealGoalTag.setText(goalShort(goalText));
        tvMealNutriSummary.setText(meal.nutritionLine());
        imgMeal.setImageResource(pickImage(meal.name));

        tvDetailStatus.setOnClickListener(v -> {
            if (progressDetail.getVisibility() != View.VISIBLE) loadDetails();
        });
        btnSaveToLog.setOnClickListener(v -> saveToLog());

        // Nguyên liệu và cách làm: lấy từ AI
        loadDetails();
    }

    private void applyHeaderInsets() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            layoutHeader.setPadding(layoutHeader.getPaddingLeft(), bars.top,
                    layoutHeader.getPaddingRight(), 0);
            return windowInsets;
        });
    }

    // ===================== NGUYÊN LIỆU & CÁCH LÀM =====================

    private void loadDetails() {
        final SharedPreferences cache = getSharedPreferences(PREFS_CACHE, MODE_PRIVATE);
        final String key = meal.name + "|" + meal.calories + "|" + meal.protein + "|" + meal.carbs + "|" + meal.fat;

        String cached = cache.getString(key, null);
        if (cached != null && applyDetails(cached)) {
            layoutDetailLoading.setVisibility(View.GONE);
            return;
        }

        final int myRequest = ++requestId;
        showStatus("AI đang soạn nguyên liệu và cách chế biến...", true, false);

        String prompt = "Món ăn: " + meal.name + " (" + meal.type + ").\n"
                + String.format(Locale.US,
                "Dinh dưỡng mục tiêu của món: %d kcal, %d g protein, %d g carb, %d g chất béo.\n",
                meal.calories, meal.protein, meal.carbs, meal.fat)
                + "Hãy liệt kê 4 đến 8 nguyên liệu kèm định lượng sao cho món đạt đúng dinh dưỡng trên, "
                + "và 3 đến 6 bước chế biến ngắn gọn.\n"
                + "Định dạng JSON bắt buộc: {\"ingredients\":[{\"emoji\":\"🍗\",\"name\":\"Ức gà\",\"amount\":\"150g\"}],"
                + "\"steps\":[\"...\"],\"tip\":\"một mẹo ngắn (có thể để trống)\"}";

        GeminiClient.generateJson(SYSTEM_PROMPT, prompt, 0.6, new GeminiClient.Callback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    if (isFinishing() || myRequest != requestId) return;
                    if (applyDetails(json)) {
                        cache.edit().putString(key, json).apply();
                        layoutDetailLoading.setVisibility(View.GONE);
                    } else {
                        showStatus("Dữ liệu AI trả về chưa đúng định dạng. Bấm vào đây để thử lại.", false, true);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || myRequest != requestId) return;
                    showStatus(message + " Bấm vào đây để thử lại.", false, true);
                });
            }
        });
    }

    /** Hiển thị nguyên liệu và các bước; trả về false nếu dữ liệu thiếu hoặc sai định dạng. */
    private boolean applyDetails(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray ingredients = root.getJSONArray("ingredients");
            JSONArray steps = root.getJSONArray("steps");
            if (ingredients.length() == 0 || steps.length() == 0) return false;

            layoutIngredients.removeAllViews();
            for (int i = 0; i < ingredients.length(); i++) {
                JSONObject item = ingredients.getJSONObject(i);
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) continue;
                layoutIngredients.addView(ingredientRow(
                        item.optString("emoji", "").trim(), name, item.optString("amount", "").trim()));
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < steps.length(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(i + 1).append(". ").append(steps.getString(i).trim());
            }
            tvCookingSteps.setText(sb.toString());

            String tip = root.optString("tip", "").trim();
            if (tip.isEmpty()) {
                tvTip.setVisibility(View.GONE);
            } else {
                tvTip.setText("💡 " + tip);
                tvTip.setVisibility(View.VISIBLE);
            }
            return layoutIngredients.getChildCount() > 0;
        } catch (JSONException e) {
            return false;
        }
    }

    private View ingredientRow(String emoji, String name, String amount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView tvName = new TextView(this);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setText(emoji.isEmpty() ? name : emoji + " " + name);
        tvName.setTextColor(Color.parseColor("#374151"));
        tvName.setTextSize(14);

        TextView tvAmount = new TextView(this);
        tvAmount.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvAmount.setText(amount);
        tvAmount.setTextColor(Color.parseColor("#111827"));
        tvAmount.setTextSize(14);
        tvAmount.setTypeface(tvAmount.getTypeface(), android.graphics.Typeface.BOLD);

        row.addView(tvName);
        row.addView(tvAmount);
        return row;
    }

    private void showStatus(String text, boolean withProgress, boolean isError) {
        layoutDetailLoading.setVisibility(View.VISIBLE);
        progressDetail.setVisibility(withProgress ? View.VISIBLE : View.GONE);
        tvDetailStatus.setText(text);
        tvDetailStatus.setTextColor(Color.parseColor(isError ? "#B45309" : "#059669"));
    }

    // ===================== LƯU NHẬT KÝ =====================

    private void saveToLog() {
        btnSaveToLog.setEnabled(false);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        MealLog log = new MealLog(today, meal.type, meal.name,
                meal.calories, meal.protein, meal.carbs, meal.fat, System.currentTimeMillis());

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.mealLogDao().insert(log);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã lưu bữa ăn vào nhật ký hôm nay!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSaveToLog.setEnabled(true);
                    Toast.makeText(this, "Không lưu được: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===================== TIỆN ÍCH =====================

    private String goalShort(String goal) {
        String g = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (g.contains("tăng cơ") || g.contains("bulking")) return "Tăng cơ";
        if (g.contains("giảm mỡ") || g.contains("cutting")) return "Giảm mỡ";
        return "Duy trì";
    }

    /** Chọn ảnh minh họa gần đúng theo nguyên liệu chính trong tên món. */
    private int pickImage(String name) {
        String n = " " + name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        if (n.contains(" sinh tố ") || n.contains(" yến mạch ") || n.contains(" sữa chua ")) {
            return R.drawable.img_sinhto;
        }
        if (n.contains(" gà ")) return R.drawable.img_chicken;
        if (n.contains(" bò ")) return R.drawable.img_bo;
        if (n.contains(" cá ") || n.contains(" tôm ") || n.contains(" mực ")) return R.drawable.img_cahoi;
        return R.drawable.img_monan;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}