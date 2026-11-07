package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.WorkoutLog;
import com.example.healthgymapp.data.local.remote.repository.WorkoutLogDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Danh sách bài tập gợi ý. Người dùng chạm để chọn / bỏ chọn bài tập của HÔM NAY,
 * bấm "Xác nhận" để lưu vào Room (bảng workout_log) rồi quay về Trang chủ.
 */
public class ExerciseListActivity extends AppCompatActivity {

    /** Một bài tập trong danh mục gợi ý. */
    private static class Exercise {
        final String emoji, name, note;
        final int minutes, kcal;
        Exercise(String emoji, String name, int minutes, String note, int kcal) {
            this.emoji = emoji; this.name = name; this.minutes = minutes;
            this.note = note; this.kcal = kcal;
        }
    }

    // Danh mục bài tập: muốn thêm bài mới chỉ cần thêm một dòng vào đây
    private static final Exercise[] CATALOG = {
            new Exercise("🏃", "Chạy bộ (Cardio)", 30, "Tốc độ trung bình", 300),
            new Exercise("🥊", "Boxing (Đấm bốc)", 45, "Đốt mỡ toàn thân", 450),
            new Exercise("🏊", "Bơi lội", 40, "Vận động toàn cơ bắp", 380),
            new Exercise("🚴", "Đạp xe ngoài trời", 45, "Sức bền đùi & bắp chân", 320),
            new Exercise("⚡", "Nhảy dây tốc độ (HIIT)", 20, "Đốt calo cường độ cao", 280),
            new Exercise("🏋️", "Tập Gym: Ngực & Tay sau", 60, "Bench Press, Dips", 410),
            new Exercise("💪", "Tập Gym: Lưng Xô & Tay trước", 50, "Lat Pulldown, Bicep Curl", 380),
            new Exercise("🦵", "Tập Gym: Chân & Mông", 60, "Squat, Lunges", 480),
            new Exercise("🧘", "Yoga & Giãn cơ", 40, "Dẻo dai, phục hồi", 160),
            new Exercise("🚶", "Đi bộ nhanh", 40, "Nhẹ nhàng, dễ duy trì", 200),
    };

    private final boolean[] selected = new boolean[CATALOG.length];
    private final View[] rows = new View[CATALOG.length];
    private final Set<String> alreadyLogged = new HashSet<>();
    private TextView btnSave;
    private String today;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_list);

        today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        btnSave = findViewById(R.id.btnSaveExercises);
        LinearLayout container = findViewById(R.id.layoutExerciseList);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < CATALOG.length; i++) {
            final int index = i;
            Exercise ex = CATALOG[i];
            View row = inflater.inflate(R.layout.item_exercise_choice, container, false);
            ((TextView) row.findViewById(R.id.tvExName)).setText(ex.emoji + " " + ex.name);
            ((TextView) row.findViewById(R.id.tvExInfo))
                    .setText("Thời gian: " + ex.minutes + " phút | " + ex.note);
            ((TextView) row.findViewById(R.id.tvExKcal)).setText("- " + ex.kcal + " kcal");
            row.setOnClickListener(v -> {
                selected[index] = !selected[index];
                paint(index);
                updateSaveButton();
            });
            container.addView(row);
            rows[i] = row;
        }
        updateSaveButton();
        btnSave.setEnabled(false); // chờ đọc xong dữ liệu hôm nay để tránh lưu trùng

        // Bài tập đã chọn trước đó trong hôm nay -> tick sẵn
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<WorkoutLog> logged = db.workoutLogDao().getByDate(today);
            for (WorkoutLog log : logged) alreadyLogged.add(log.getName());
            runOnUiThread(() -> {
                for (int i = 0; i < CATALOG.length; i++) {
                    selected[i] = alreadyLogged.contains(CATALOG[i].name);
                    paint(i);
                }
                updateSaveButton();
                btnSave.setEnabled(true);
            });
        });

        btnSave.setOnClickListener(v -> saveAndClose());
    }

    private void paint(int i) {
        View row = rows[i];
        row.setBackgroundResource(selected[i]
                ? R.drawable.bg_exercise_card_selected : R.drawable.bg_exercise_card);
        TextView check = row.findViewById(R.id.tvExCheck);
        check.setText(selected[i] ? "✔" : "○");
        check.setTextColor(selected[i] ? 0xFF2BB673 : 0xFF9CB3A8);
    }

    private void updateSaveButton() {
        int count = 0;
        for (boolean b : selected) if (b) count++;
        btnSave.setText(count == 0 ? "Xác nhận" : "Xác nhận (" + count + " bài)");
    }

    /** Ghi thay đổi vào Room: thêm bài mới chọn, xóa bài đã bỏ chọn, giữ nguyên bài cũ. */
    private void saveAndClose() {
        btnSave.setEnabled(false);
        final boolean[] snapshot = selected.clone();
        WorkoutLogDao dao = AppDatabase.getInstance(getApplicationContext()).workoutLogDao();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            for (int i = 0; i < CATALOG.length; i++) {
                Exercise ex = CATALOG[i];
                boolean was = alreadyLogged.contains(ex.name);
                if (snapshot[i] && !was) {
                    dao.insert(new WorkoutLog(today, ex.name, ex.emoji, ex.minutes, ex.kcal, now + i));
                } else if (!snapshot[i] && was) {
                    dao.deleteByDateAndName(today, ex.name);
                }
            }
            runOnUiThread(() -> {
                setResult(RESULT_OK);
                finish();
            });
        });
    }
}
