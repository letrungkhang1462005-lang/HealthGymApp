package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.UserProfile;
import com.example.healthgymapp.utils.GeminiClient;
import com.example.healthgymapp.utils.HealthCalculator;
import com.example.healthgymapp.utils.MealItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gợi ý thực đơn: Gemini lập thực đơn theo hồ sơ (tuổi, giới tính, chiều cao, cân nặng, mục tiêu)
 * và lượng calo mục tiêu. Thực đơn được lưu tạm để mở lại trong ngày không phải gọi AI nữa.
 */
public class MealSuggestionActivity extends AppCompatActivity {

    private static final String PREFS_CACHE = "meal_menu_cache";

    private static final String SYSTEM_PROMPT =
            "Bạn là chuyên gia dinh dưỡng thể hình tại Việt Nam. "
                    + "Chỉ trả về MỘT đối tượng JSON hợp lệ, không thêm văn bản nào khác, không dùng markdown. "
                    + "Ưu tiên món ăn Việt Nam quen thuộc, dễ nấu, nguyên liệu dễ mua ở chợ và siêu thị. "
                    + "Số liệu dinh dưỡng phải nhất quán: calories xấp xỉ 4 x protein + 4 x carbs + 9 x fat.";

    // Giao diện
    private View layoutHeader, layoutMeals, layoutLoading, progressMenu, scrollDays;
    private View cardBreakfast, cardLunch, cardSnack, cardDinner;
    private TextView btnBack, tvGoalTitle, tvGoalCalories, tvMenuHeaderTitle, tvDaySummary, tvMenuStatus;
    private TextView btnTabToday, btnTab7Days, btnTabCustom;
    private TextView tvBreakfastMeal, tvBreakfastNutri, tvLunchMeal, tvLunchNutri;
    private TextView tvSnackMeal, tvSnackNutri, tvDinnerMeal, tvDinnerNutri;
    private LinearLayout layoutDayChips;
    private Button btnGenerateOtherMenu;

    // Dữ liệu
    private UserProfile profile;
    private int currentTargetCalo = 0;
    private String currentUserGoal = "Duy trì cân nặng & Sức khỏe";
    private int currentTab = 1;    // 1: Hôm nay, 2: 7 ngày, 3: Tùy chỉnh
    private int previousTab = 1;
    private String customNote = "";
    private List<List<MealItem>> days = new ArrayList<>();
    private int selectedDay = 0;
    private int requestId = 0;     // bỏ qua kết quả của yêu cầu đã cũ
    private boolean firstMenuRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_suggestion);

        // 1. Ánh xạ View
        layoutHeader = findViewById(R.id.layoutHeader);
        btnBack = findViewById(R.id.btnBack);
        tvGoalTitle = findViewById(R.id.tvGoalTitle);
        tvGoalCalories = findViewById(R.id.tvGoalCalories);
        tvMenuHeaderTitle = findViewById(R.id.tvMenuHeaderTitle);
        tvDaySummary = findViewById(R.id.tvDaySummary);
        tvMenuStatus = findViewById(R.id.tvMenuStatus);
        layoutLoading = findViewById(R.id.layoutLoading);
        progressMenu = findViewById(R.id.progressMenu);
        layoutMeals = findViewById(R.id.layoutMeals);
        scrollDays = findViewById(R.id.scrollDays);
        layoutDayChips = findViewById(R.id.layoutDayChips);

        btnTabToday = findViewById(R.id.btnTabToday);
        btnTab7Days = findViewById(R.id.btnTab7Days);
        btnTabCustom = findViewById(R.id.btnTabCustom);

        tvBreakfastMeal = findViewById(R.id.tvBreakfastMeal);
        tvBreakfastNutri = findViewById(R.id.tvBreakfastNutri);
        tvLunchMeal = findViewById(R.id.tvLunchMeal);
        tvLunchNutri = findViewById(R.id.tvLunchNutri);
        tvSnackMeal = findViewById(R.id.tvSnackMeal);
        tvSnackNutri = findViewById(R.id.tvSnackNutri);
        tvDinnerMeal = findViewById(R.id.tvDinnerMeal);
        tvDinnerNutri = findViewById(R.id.tvDinnerNutri);

        cardBreakfast = findViewById(R.id.cardBreakfast);
        cardLunch = findViewById(R.id.cardLunch);
        cardSnack = findViewById(R.id.cardSnack);
        cardDinner = findViewById(R.id.cardDinner);
        btnGenerateOtherMenu = findViewById(R.id.btnGenerateOtherMenu);

        applyHeaderInsets();

        // 2. Sự kiện
        btnBack.setOnClickListener(v -> finish());
        btnTabToday.setOnClickListener(v -> switchTab(1));
        btnTab7Days.setOnClickListener(v -> switchTab(2));
        btnTabCustom.setOnClickListener(v -> switchTab(3));
        btnGenerateOtherMenu.setOnClickListener(v -> generateMenu(true));

        cardBreakfast.setOnClickListener(v -> openMealDetail(0));
        cardLunch.setOnClickListener(v -> openMealDetail(1));
        cardSnack.setOnClickListener(v -> openMealDetail(2));
        cardDinner.setOnClickListener(v -> openMealDetail(3));

        // Chưa có hồ sơ thì bấm vào dòng calo để đi nhập hồ sơ
        tvGoalCalories.setOnClickListener(v -> {
            if (profile == null) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_NEW_PROFILE, true);
                startActivity(intent);
            }
        });

        // 3. Mục tiêu: ưu tiên Intent, sau đó là hồ sơ trong Room
        final String intentGoal = getIntent().getStringExtra("USER_GOAL");
        if (intentGoal != null && !intentGoal.isEmpty()) {
            currentUserGoal = intentGoal;
        }
        tvGoalTitle.setText(currentUserGoal);

        AppDatabase.getInstance(getApplicationContext()).userDao().getUserProfile().observe(this, p -> {
            profile = p;
            if (p == null) {
                tvGoalCalories.setText("Chưa có hồ sơ. Bấm vào đây để nhập hồ sơ.");
                showStatus("Cần có hồ sơ sức khỏe để AI lập thực đơn phù hợp với bạn.", false, true);
                return;
            }
            if ((intentGoal == null || intentGoal.isEmpty())
                    && p.getFitnessGoal() != null && !p.getFitnessGoal().isEmpty()) {
                currentUserGoal = p.getFitnessGoal();
            }
            tvGoalTitle.setText(currentUserGoal);

            float bmr = HealthCalculator.calculateBMR(p.getWeight(), p.getHeight(), p.getAge(), p.getGender());
            float tdee = HealthCalculator.calculateTDEE(bmr, p.getActivityLevel());
            currentTargetCalo = HealthCalculator.calculateTargetCalories(tdee, currentUserGoal);
            tvGoalCalories.setText("Calo mục tiêu: " + currentTargetCalo + " kcal/ngày");

            // Lập thực đơn lần đầu khi đã có hồ sơ
            if (!firstMenuRequested) {
                firstMenuRequested = true;
                generateMenu(false);
            }
        });
    }

    /** Đẩy tiêu đề xuống dưới thanh trạng thái (màn hình edge-to-edge). */
    private void applyHeaderInsets() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            layoutHeader.setPadding(layoutHeader.getPaddingLeft(), bars.top,
                    layoutHeader.getPaddingRight(), 0);
            return windowInsets;
        });
    }

    // ===================== TAB =====================

    private void switchTab(int tab) {
        if (tab == 3 && currentTab != 3) {
            previousTab = currentTab;
        }
        currentTab = tab;

        styleTab(btnTabToday, tab == 1);
        styleTab(btnTab7Days, tab == 2);
        styleTab(btnTabCustom, tab == 3);

        tvMenuHeaderTitle.setText(tab == 1 ? "Thực đơn gợi ý cho Hôm nay"
                : tab == 2 ? "Thực đơn gợi ý trong 7 ngày"
                  : "Thực đơn theo yêu cầu của bạn");
        scrollDays.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        if (tab == 3) {
            showCustomDialog();
        } else {
            generateMenu(false);
        }
    }

    private void styleTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_green_banner);
            tab.setTextColor(Color.WHITE);
        } else {
            tab.setBackgroundResource(0);
            tab.setTextColor(Color.parseColor("#6B7280"));
        }
    }

    /** Tab Tùy chỉnh: người dùng nhập yêu cầu riêng (ăn chay, không ăn hải sản...). */
    private void showCustomDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setHint("Ví dụ: không ăn hải sản, ăn chay, ngân sách thấp, nhiều rau...");
        input.setText(customNote);

        FrameLayout container = new FrameLayout(this);
        container.setPadding(dp(20), dp(8), dp(20), 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Yêu cầu riêng cho thực đơn")
                .setView(container)
                .setPositiveButton("Tạo thực đơn", (d, w) -> {
                    customNote = input.getText().toString().trim();
                    generateMenu(true);
                })
                .setNegativeButton("Hủy", (d, w) -> switchTab(previousTab))
                .setOnCancelListener(d -> switchTab(previousTab))
                .show();
    }

    // ===================== TẠO THỰC ĐƠN BẰNG AI =====================

    private void generateMenu(boolean forceNew) {
        if (profile == null) {
            showStatus("Cần có hồ sơ sức khỏe để AI lập thực đơn phù hợp với bạn.", false, true);
            return;
        }
        final int daysCount = currentTab == 2 ? 7 : 1;
        final String cacheKey = currentTab + "|" + todayString() + "|" + currentUserGoal + "|" + currentTargetCalo;
        final SharedPreferences cache = getSharedPreferences(PREFS_CACHE, MODE_PRIVATE);

        // Dùng lại thực đơn đã tạo trong ngày (trừ tab Tùy chỉnh) để đỡ phải chờ AI
        if (!forceNew && currentTab != 3) {
            String cached = cache.getString(cacheKey, null);
            if (cached != null && applyMenuJson(cached)) {
                setLoading(false);
                hideStatus();
                return;
            }
        }

        final int myRequest = ++requestId;
        setLoading(true);
        String prompt = buildPrompt(daysCount, forceNew);

        GeminiClient.generateJson(SYSTEM_PROMPT, prompt, 0.9, new GeminiClient.Callback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    if (isFinishing() || myRequest != requestId) return;
                    setLoading(false);
                    if (applyMenuJson(json)) {
                        if (currentTab != 3) {
                            cache.edit().putString(cacheKey, json).apply();
                        }
                        hideStatus();
                    } else {
                        showStatus("Thực đơn AI trả về chưa đúng định dạng. Bấm \"Tạo thực đơn khác\" để thử lại.", false, true);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || myRequest != requestId) return;
                    setLoading(false);
                    showStatus(message + " Bấm \"Tạo thực đơn khác\" để thử lại.", false, true);
                });
            }
        });
    }

    private String buildPrompt(int daysCount, boolean avoidPrevious) {
        float weight = profile.getWeight();
        int proteinMin = Math.round(weight * 1.6f);
        int proteinMax = Math.round(weight * 2.2f);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "Hồ sơ người dùng: giới tính %s, %d tuổi, cao %.0f cm, nặng %.1f kg, hệ số vận động %.2f, mục tiêu: %s.\n",
                profile.getGender(), profile.getAge(), profile.getHeight(), weight,
                profile.getActivityLevel(), currentUserGoal));
        sb.append(String.format(Locale.US,
                "Mục tiêu năng lượng: khoảng %d kcal mỗi ngày (sai số tối đa 5%%). Protein khoảng %d đến %d g mỗi ngày.\n",
                currentTargetCalo, proteinMin, proteinMax));
        sb.append(String.format(Locale.US,
                "Hãy lập thực đơn cho %d ngày. Mỗi ngày đúng 4 bữa theo thứ tự: Bữa sáng, Bữa trưa, Bữa phụ, Bữa tối.\n",
                daysCount));
        sb.append("Tên món ngắn gọn (dưới 90 ký tự), ghi rõ khẩu phần chính (ví dụ: 2 bát cơm, 150g ức gà).\n");
        if (daysCount > 1) {
            sb.append("Các ngày phải khác nhau, không lặp lại cùng một món chính.\n");
        }
        if (currentTab == 3 && !customNote.isEmpty()) {
            sb.append("Yêu cầu riêng của người dùng (bắt buộc tuân thủ): ").append(customNote).append(".\n");
        }
        if (avoidPrevious && !days.isEmpty()) {
            sb.append("Không dùng lại các món sau: ");
            int count = 0;
            for (MealItem meal : days.get(0)) {
                if (count++ > 0) sb.append("; ");
                sb.append(meal.name);
            }
            sb.append(".\n");
        }
        sb.append("Định dạng JSON bắt buộc: {\"days\":[{\"meals\":[")
                .append("{\"name\":\"...\",\"calories\":650,\"protein\":35,\"carbs\":80,\"fat\":18},")
                .append("... đủ 4 bữa]}]} với đúng ").append(daysCount).append(" phần tử trong \"days\". ")
                .append("calories, protein, carbs, fat là số nguyên (gram, riêng calories là kcal).");
        return sb.toString();
    }

    /** Đọc JSON thực đơn và hiển thị. Trả về false nếu dữ liệu không dùng được. */
    private boolean applyMenuJson(String json) {
        try {
            List<List<MealItem>> parsed = parseDays(json);
            if (parsed.isEmpty()) return false;
            days = parsed;
            selectedDay = 0;
            buildDayChips();
            showDay(0);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private List<List<MealItem>> parseDays(String json) throws JSONException {
        JSONArray daysArray = new JSONObject(json).getJSONArray("days");
        List<List<MealItem>> result = new ArrayList<>();
        for (int i = 0; i < daysArray.length(); i++) {
            JSONArray meals = daysArray.getJSONObject(i).getJSONArray("meals");
            if (meals.length() < 4) continue;
            List<MealItem> day = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                day.add(MealItem.fromAi(meals.getJSONObject(j), j));
            }
            result.add(day);
        }
        return result;
    }

    // ===================== HIỂN THỊ =====================

    private void showDay(int index) {
        selectedDay = index;
        List<MealItem> meals = days.get(index);

        tvBreakfastMeal.setText(meals.get(0).name);
        tvBreakfastNutri.setText(meals.get(0).nutritionLine());
        tvLunchMeal.setText(meals.get(1).name);
        tvLunchNutri.setText(meals.get(1).nutritionLine());
        tvSnackMeal.setText(meals.get(2).name);
        tvSnackNutri.setText(meals.get(2).nutritionLine());
        tvDinnerMeal.setText(meals.get(3).name);
        tvDinnerNutri.setText(meals.get(3).nutritionLine());

        int totalKcal = 0;
        int totalProtein = 0;
        for (MealItem meal : meals) {
            totalKcal += meal.calories;
            totalProtein += meal.protein;
        }
        StringBuilder summary = new StringBuilder("Tổng cả ngày: " + totalKcal + " kcal, " + totalProtein + "g protein");
        boolean offTarget = false;
        if (currentTargetCalo > 0) {
            int diff = totalKcal - currentTargetCalo;
            summary.append(" (mục tiêu ").append(currentTargetCalo).append(" kcal");
            if (Math.abs(diff) > currentTargetCalo * 0.1) {
                offTarget = true;
                summary.append(", lệch ").append(diff > 0 ? "+" : "").append(diff).append(" kcal");
            }
            summary.append(")");
        }
        tvDaySummary.setText(summary.toString());
        tvDaySummary.setTextColor(Color.parseColor(offTarget ? "#B45309" : "#059669"));

        updateChipStyles();
    }

    /** Nút chọn ngày cho tab 7 ngày (Hôm nay, T3 20/10, ...). */
    private void buildDayChips() {
        layoutDayChips.removeAllViews();
        if (days.size() <= 1) return;

        String[] weekdays = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days.size(); i++) {
            final int index = i;
            String label = i == 0 ? "Hôm nay"
                    : weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1] + " "
                      + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.getTime());

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(12);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> showDay(index));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            layoutDayChips.addView(chip, lp);

            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void updateChipStyles() {
        for (int i = 0; i < layoutDayChips.getChildCount(); i++) {
            TextView chip = (TextView) layoutDayChips.getChildAt(i);
            if (i == selectedDay) {
                chip.setBackgroundResource(R.drawable.bg_green_banner);
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackgroundResource(R.drawable.bg_stat_item);
                chip.setTextColor(Color.parseColor("#374151"));
            }
        }
    }

    private void setLoading(boolean loading) {
        if (loading) {
            showStatus("AI đang lập thực đơn theo hồ sơ của bạn...", true, false);
            layoutMeals.setAlpha(0.4f);
            btnGenerateOtherMenu.setEnabled(false);
        } else {
            layoutMeals.setAlpha(1f);
            btnGenerateOtherMenu.setEnabled(true);
        }
    }

    private void showStatus(String text, boolean withProgress, boolean isError) {
        layoutLoading.setVisibility(View.VISIBLE);
        progressMenu.setVisibility(withProgress ? View.VISIBLE : View.GONE);
        tvMenuStatus.setText(text);
        tvMenuStatus.setTextColor(Color.parseColor(isError ? "#B45309" : "#059669"));
    }

    private void hideStatus() {
        layoutLoading.setVisibility(View.GONE);
    }

    private void openMealDetail(int mealIndex) {
        if (days.isEmpty() || selectedDay >= days.size()) return;
        MealItem meal = days.get(selectedDay).get(mealIndex);
        try {
            Intent intent = new Intent(this, MealDetailActivity.class);
            intent.putExtra("MEAL_JSON", meal.toJson().toString());
            intent.putExtra("USER_GOAL", currentUserGoal);
            startActivity(intent);
        } catch (JSONException e) {
            showStatus("Không mở được chi tiết món ăn.", false, true);
        }
    }

    private String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}