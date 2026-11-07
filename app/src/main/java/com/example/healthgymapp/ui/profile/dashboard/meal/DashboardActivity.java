package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.UserProfile;
import com.example.healthgymapp.data.local.remote.repository.WorkoutLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcomeName, tvMoreExercises, tvDashboardDate;
    private TextView tvDashboardWeight, tvDashboardBmi, tvDashboardCalories;
    private ImageView btnHeaderSettings, imgUserAvatar;
    private FrameLayout layoutMealSuggestion;
    private LinearLayout cardWater, cardBmi, cardHeartRate;

    private LinearLayout menuHome, menuHistory, menuAiCoach, menuSettings;
    private String currentWeight = "68";
    private String currentHeight = "168";
    private String userGoal = "Duy trì cân nặng & Sức khỏe";

    // Hoạt động hôm nay (nạp từ Room) và ngày đang theo dõi
    private LinearLayout layoutTodayActivities;
    private TextView tvNoActivityToday;
    private LiveData<List<WorkoutLog>> todayLogs;
    private String observedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // 1. Ánh xạ View
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        tvDashboardDate = findViewById(R.id.tvDashboardDate);
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        layoutMealSuggestion = findViewById(R.id.layoutMealSuggestion);
        tvMoreExercises = findViewById(R.id.tvMoreExercises);
        layoutTodayActivities = findViewById(R.id.layoutTodayActivities);
        tvNoActivityToday = findViewById(R.id.tvNoActivityToday);

        cardHeartRate = findViewById(R.id.cardHeartRate);
        cardWater = findViewById(R.id.cardWater);
        cardBmi = findViewById(R.id.cardBmi);
        tvDashboardWeight = findViewById(R.id.tvDashboardWeight);
        tvDashboardBmi = findViewById(R.id.tvDashboardBmi);
        tvDashboardCalories = findViewById(R.id.tvDashboardCalories);

        menuHome = findViewById(R.id.menuHome);
        menuHistory = findViewById(R.id.menuHistory);
        menuAiCoach = findViewById(R.id.menuAiCoach);
        menuSettings = findViewById(R.id.menuSettings);

        // Hiển thị ngày hôm nay (lấy theo đồng hồ máy, không gõ cứng)
        updateTodayDate();

        // 2. Hiển thị ngay dữ liệu Profile từ Intent (nếu có) để không bị nháy giá trị mặc định
        applyProfile(
                getIntent().getStringExtra("USER_NAME"),
                getIntent().getStringExtra("USER_WEIGHT"),
                getIntent().getStringExtra("USER_HEIGHT"),
                getIntent().getStringExtra("USER_GOAL"),
                getIntent().getStringExtra("USER_GENDER"));

        // Đồng thời theo dõi hồ sơ trong Room: lưu/sửa hồ sơ ở đâu thì Trang chủ cũng tự cập nhật
        AppDatabase.getInstance(getApplicationContext()).userDao().getUserProfile()
                .observe(this, profile -> {
                    if (profile != null) {
                        applyProfile(
                                profile.getFullName(),
                                UserProfile.formatNumber(profile.getWeight()),
                                UserProfile.formatNumber(profile.getHeight()),
                                profile.getFitnessGoal(),
                                profile.getGender());
                    }
                });

        // 4. Hoạt động hôm nay + Calories đã đốt: lấy từ Room, tự cập nhật khi có thay đổi
        observeTodayWorkouts();

        // 5. Sự kiện bấm Thẻ Nhịp tim -> Mở HeartRateDetailActivity
        if (cardHeartRate != null) {
            cardHeartRate.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, HeartRateDetailActivity.class);
                startActivity(intent);
            });
        }

        // 6. Sự kiện bấm Thẻ Nước -> Mở WaterReminderActivity
        if (cardWater != null) {
            cardWater.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, WaterReminderActivity.class);
                intent.putExtra("USER_WEIGHT", currentWeight);
                startActivity(intent);
            });
        }

        // 7. Sự kiện bấm vào ô BMI -> Mở BmiCalculatorActivity
        if (cardBmi != null) {
            cardBmi.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, BmiCalculatorActivity.class);
                intent.putExtra("USER_HEIGHT", currentHeight);
                intent.putExtra("USER_WEIGHT", currentWeight);
                startActivity(intent);
            });
        }

        // 8. Sự kiện bấm Gợi ý chế độ ăn uống -> Mở MealSuggestionActivity
        if (layoutMealSuggestion != null) {
            layoutMealSuggestion.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, MealSuggestionActivity.class);
                intent.putExtra("USER_GOAL", userGoal);
                startActivity(intent);
            });
        }

        // 9. Bấm "Chọn thêm bài tập gợi ý..." -> mở danh sách để chọn bài tập hôm nay
        if (tvMoreExercises != null) {
            tvMoreExercises.setOnClickListener(v ->
                    startActivity(new Intent(DashboardActivity.this, ExerciseListActivity.class)));
        }

        // 10. Sự kiện bấm nút AI Coach ở thanh menu dưới đáy -> Mở AiCoachActivity (tích hợp Gemini AI)
        if (menuAiCoach != null) {
            menuAiCoach.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AiCoachActivity.class);
                startActivity(intent);
            });
        }

        // 10b. Thẻ "Bản đồ & đếm bước chân" -> Mở TrackingActivity
        android.view.View layoutTracking = findViewById(R.id.layoutTracking);
        if (layoutTracking != null) {
            layoutTracking.setOnClickListener(v ->
                    startActivity(new Intent(DashboardActivity.this, TrackingActivity.class)));
        }

        // 11. Các sự kiện điều hướng menu dưới đáy khác & cài đặt
        if (menuHistory != null) {
            menuHistory.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, HistoryActivity.class)));
        }

        if (menuSettings != null || btnHeaderSettings != null) {
            android.view.View.OnClickListener settingsListener = v -> startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
            if (menuSettings != null) menuSettings.setOnClickListener(settingsListener);
            if (btnHeaderSettings != null) btnHeaderSettings.setOnClickListener(settingsListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cập nhật lại ngày khi quay lại app (phòng trường hợp app mở qua nửa đêm)
        updateTodayDate();
        observeTodayWorkouts(); // đổi sang ngày mới nếu app mở qua nửa đêm

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String genderStr = prefs.getString("GENDER", "Nam");
        updateAvatarByGender(genderStr);
    }

    /** Theo dõi các bài tập của hôm nay trong Room; chỉ đăng ký lại khi sang ngày mới. */
    private void observeTodayWorkouts() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(observedDate)) return;
        if (todayLogs != null) todayLogs.removeObservers(this);
        observedDate = today;
        todayLogs = AppDatabase.getInstance(getApplicationContext())
                .workoutLogDao().observeByDate(today);
        todayLogs.observe(this, this::renderTodayWorkouts);
    }

    /** Vẽ danh sách hoạt động hôm nay và cập nhật tổng calo đã đốt. */
    private void renderTodayWorkouts(List<WorkoutLog> logs) {
        if (layoutTodayActivities == null) return;
        layoutTodayActivities.removeAllViews();
        int totalKcal = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        if (logs != null) {
            for (WorkoutLog log : logs) {
                totalKcal += log.getCalories();
                View row = inflater.inflate(R.layout.item_activity_row, layoutTodayActivities, false);
                ((TextView) row.findViewById(R.id.tvRowEmoji)).setText(log.getEmoji());
                ((TextView) row.findViewById(R.id.tvRowName)).setText(log.getName());
                ((TextView) row.findViewById(R.id.tvRowSub)).setText(log.getDurationMin() + " phút");
                ((TextView) row.findViewById(R.id.tvRowKcal)).setText("- " + log.getCalories() + " kcal");
                // Nhấn giữ để bỏ bài tập khỏi hôm nay
                row.setOnLongClickListener(v -> {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setMessage("Bỏ \"" + log.getName() + "\" khỏi hoạt động hôm nay?")
                            .setPositiveButton("Bỏ", (dlg, w) ->
                                    AppDatabase.databaseWriteExecutor.execute(() ->
                                            AppDatabase.getInstance(getApplicationContext())
                                                    .workoutLogDao().deleteById(log.getId())))
                            .setNegativeButton("Giữ lại", null)
                            .show();
                    return true;
                });
                layoutTodayActivities.addView(row);
            }
        }
        if (tvNoActivityToday != null) {
            tvNoActivityToday.setVisibility(layoutTodayActivities.getChildCount() == 0
                    ? View.VISIBLE : View.GONE);
        }
        if (tvDashboardCalories != null) {
            tvDashboardCalories.setText("🔥 " + totalKcal);
        }
    }

    /** Áp dụng thông tin hồ sơ lên giao diện (tên, cân nặng, chiều cao, mục tiêu, giới tính, BMI). */
    private void applyProfile(String fullName, String weightStr, String heightStr,
                              String goalStr, String genderStr) {
        // Giới tính: nếu không có thì dùng giá trị đã nhớ trước đó
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (genderStr == null || genderStr.isEmpty()) {
            genderStr = prefs.getString("GENDER", "Nam");
        } else {
            prefs.edit().putString("GENDER", genderStr).apply();
        }
        updateAvatarByGender(genderStr);

        if (fullName != null && !fullName.isEmpty() && tvWelcomeName != null) {
            tvWelcomeName.setText("Xin chào, " + fullName + " 👋");
        }

        if (goalStr != null && !goalStr.isEmpty()) {
            userGoal = goalStr;
        }

        if (weightStr != null && !weightStr.isEmpty()) {
            currentWeight = weightStr;
            if (tvDashboardWeight != null) {
                tvDashboardWeight.setText(weightStr + " kg");
            }
        }

        if (heightStr != null && !heightStr.isEmpty()) {
            currentHeight = heightStr;
        }

        updateBmi();
    }

    /** Tính lại BMI từ cân nặng và chiều cao hiện tại (đã xử lý an toàn chống văng app). */
    private void updateBmi() {
        try {
            if (currentWeight != null && currentHeight != null && !currentWeight.isEmpty() && !currentHeight.isEmpty()) {
                String cleanWeight = currentWeight.trim().replace(",", ".");
                String cleanHeight = currentHeight.trim().replace(",", ".");

                double weight = Double.parseDouble(cleanWeight);
                double heightM = Double.parseDouble(cleanHeight) / 100.0;

                if (heightM > 0 && tvDashboardBmi != null) {
                    double bmi = weight / (heightM * heightM);
                    tvDashboardBmi.setText(String.format("%.1f", bmi));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (tvDashboardBmi != null) {
                tvDashboardBmi.setText("23.7");
            }
        }
    }

    /** Hiển thị ngày hiện tại theo đồng hồ của máy. */
    private void updateTodayDate() {
        if (tvDashboardDate != null) {
            String today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
            tvDashboardDate.setText("Hôm nay, " + today);
        }
    }

    private void updateAvatarByGender(String gender) {
        if (imgUserAvatar != null) {
            if ("Nam".equalsIgnoreCase(gender)) {
                imgUserAvatar.setImageResource(R.drawable.img_changtrai);
            } else {
                imgUserAvatar.setImageResource(R.drawable.img_girl);
            }
        }
    }
}
