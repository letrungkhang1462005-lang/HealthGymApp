package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.UserProfile;
import com.example.healthgymapp.data.local.remote.repository.WeightRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {

    // true: người mới, để form trống. false (mặc định): điền sẵn hồ sơ đã lưu để chỉnh sửa.
    public static final String EXTRA_NEW_PROFILE = "EXTRA_NEW_PROFILE";

    private EditText etFullName, etAge, etHeight, etWeight;
    private LinearLayout btnMale, btnFemale, btnSave;
    private Spinner spinnerActivityLevel, spinnerGoal;

    private boolean isMaleSelected = true; // Mặc định là Nam

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // 1. Ánh xạ các View
        etFullName = findViewById(R.id.etFullName);
        etAge = findViewById(R.id.etAge);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        btnMale = findViewById(R.id.btnMale);
        btnFemale = findViewById(R.id.btnFemale);
        spinnerActivityLevel = findViewById(R.id.spinnerActivityLevel);
        spinnerGoal = findViewById(R.id.spinnerGoal);
        btnSave = findViewById(R.id.btnSave);

        // 2. Thiết lập dữ liệu cho Spinner
        setupSpinners();

        // Điền sẵn dữ liệu cũ (đọc từ Room), trừ khi người dùng chọn "Bạn là người mới"
        if (!getIntent().getBooleanExtra(EXTRA_NEW_PROFILE, false)) {
            prefillFromSavedProfile();
        }

        // 3. Xử lý chọn giới tính
        btnMale.setOnClickListener(v -> {
            if (!isMaleSelected) {
                isMaleSelected = true;
                btnMale.setBackgroundResource(R.drawable.bg_gender_selected);
                btnFemale.setBackgroundResource(R.drawable.bg_gender_unselected);
            }
        });

        btnFemale.setOnClickListener(v -> {
            if (isMaleSelected) {
                isMaleSelected = false;
                btnMale.setBackgroundResource(R.drawable.bg_gender_unselected);
                btnFemale.setBackgroundResource(R.drawable.bg_gender_selected);
            }
        });

        // 4. Xử lý sự kiện khi bấm nút Lưu & Chuyển sang Trang chủ
        btnSave.setOnClickListener(v -> saveAndGoToDashboard());
    }

    private void setupSpinners() {
        String[] activityLevels = {
                "Ít vận động (1.2)",
                "Vận động nhẹ (1.375)",
                "Vận động vừa (1.55)",
                "Vận động nhiều (1.725)",
                "Vận động rất nhiều (1.9)"
        };
        ArrayAdapter<String> adapterActivity = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, activityLevels);
        spinnerActivityLevel.setAdapter(adapterActivity);

        String[] goals = {
                "Tăng cơ (Bulking)",
                "Giảm mỡ (Cutting)",
                "Duy trì cân nặng & Sức khỏe"
        };
        ArrayAdapter<String> adapterGoal = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, goals);
        spinnerGoal.setAdapter(adapterGoal);
    }

    /** Đọc hồ sơ từ Room (luồng nền) rồi điền vào form (luồng giao diện). */
    private void prefillFromSavedProfile() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                UserProfile profile = db.userDao().getProfileOnce();
                if (profile != null) {
                    runOnUiThread(() -> applyProfileToForm(profile));
                }
            } catch (Exception e) {
                Log.e("ProfileActivity", "Không đọc được hồ sơ đã lưu", e);
            }
        });
    }

    private void applyProfileToForm(UserProfile profile) {
        etFullName.setText(profile.getFullName());
        etAge.setText(String.valueOf(profile.getAge()));
        etHeight.setText(UserProfile.formatNumber(profile.getHeight()));
        etWeight.setText(UserProfile.formatNumber(profile.getWeight()));

        if ("Nữ".equals(profile.getGender())) {
            isMaleSelected = false;
            btnMale.setBackgroundResource(R.drawable.bg_gender_unselected);
            btnFemale.setBackgroundResource(R.drawable.bg_gender_selected);
        } else {
            isMaleSelected = true;
            btnMale.setBackgroundResource(R.drawable.bg_gender_selected);
            btnFemale.setBackgroundResource(R.drawable.bg_gender_unselected);
        }

        selectSpinnerItem(spinnerActivityLevel, "(" + UserProfile.formatNumber(profile.getActivityLevel()) + ")", true);
        selectSpinnerItem(spinnerGoal, profile.getFitnessGoal(), false);
    }

    /** Chọn mục trong Spinner: contains = true thì khớp một phần, false thì khớp nguyên chữ. */
    private void selectSpinnerItem(Spinner spinner, String value, boolean contains) {
        if (value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = spinner.getItemAtPosition(i).toString();
            if (contains ? item.contains(value) : item.equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    /** Lấy hệ số vận động trong ngoặc, ví dụ "Vận động nhẹ (1.375)" -> 1.375. */
    private float parseActivityFactor(String text) {
        Matcher matcher = Pattern.compile("\\(([0-9]+(?:\\.[0-9]+)?)\\)").matcher(text);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // dùng giá trị mặc định bên dưới
            }
        }
        return 1.2f;
    }

    /** Báo lỗi ngay trên ô nhập và đưa con trỏ vào ô đó. */
    private void showFieldError(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private void saveAndGoToDashboard() {
        String fullName = etFullName.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        // Chấp nhận cả dấu phẩy (65,5) lẫn dấu chấm (65.5)
        String heightStr = etHeight.getText().toString().trim().replace(',', '.');
        String weightStr = etWeight.getText().toString().trim().replace(',', '.');
        String gender = isMaleSelected ? "Nam" : "Nữ";

        // Kiểm tra dữ liệu trống
        if (fullName.isEmpty() || ageStr.isEmpty() || heightStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra là số hợp lệ và nằm trong khoảng hợp lý
        int age;
        double height, weight;
        try {
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            showFieldError(etAge, "Tuổi phải là số nguyên");
            return;
        }
        try {
            height = Double.parseDouble(heightStr);
        } catch (NumberFormatException e) {
            showFieldError(etHeight, "Chiều cao không hợp lệ");
            return;
        }
        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            showFieldError(etWeight, "Cân nặng không hợp lệ");
            return;
        }

        if (age < 10 || age > 100) {
            showFieldError(etAge, "Tuổi nên từ 10 đến 100");
            return;
        }
        if (height < 100 || height > 250) {
            showFieldError(etHeight, "Chiều cao nên từ 100 đến 250 cm");
            return;
        }
        if (weight < 30 || weight > 300) {
            showFieldError(etWeight, "Cân nặng nên từ 30 đến 300 kg");
            return;
        }

        // An toàn chống lỗi Null từ Spinner
        String activityLevel = spinnerActivityLevel.getSelectedItem() != null
                ? spinnerActivityLevel.getSelectedItem().toString() : "Ít vận động (1.2)";
        String goal = spinnerGoal.getSelectedItem() != null
                ? spinnerGoal.getSelectedItem().toString() : "Duy trì cân nặng & Sức khỏe";

        // Dựng hồ sơ và mốc cân nặng của hôm nay
        UserProfile profile = new UserProfile(fullName, age, gender,
                (float) height, (float) weight, parseActivityFactor(activityLevel), goal);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        WeightRecord record = new WeightRecord(today, (float) weight);

        // Ghi vào Room ở luồng nền, xong mới chuyển màn
        btnSave.setEnabled(false);
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.userDao().insertOrUpdateProfile(profile);
                db.weightDao().insertOrReplace(record);
                runOnUiThread(() ->
                        openDashboard(fullName, ageStr, heightStr, weightStr, gender, goal, activityLevel));
            } catch (Exception e) {
                Log.e("ProfileActivity", "Lỗi khi lưu hồ sơ", e);
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(ProfileActivity.this,
                            "Không lưu được hồ sơ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openDashboard(String fullName, String ageStr, String heightStr, String weightStr,
                               String gender, String goal, String activityLevel) {
        Toast.makeText(this, "Lưu hồ sơ thành công!", Toast.LENGTH_SHORT).show();

        // Gói dữ liệu chuyển sang DashboardActivity (để hiển thị ngay, Dashboard cũng tự đọc Room)
        Intent intent = new Intent(ProfileActivity.this, DashboardActivity.class);
        intent.putExtra("USER_NAME", fullName);
        intent.putExtra("USER_AGE", ageStr);
        intent.putExtra("USER_HEIGHT", heightStr);
        intent.putExtra("USER_WEIGHT", weightStr);
        intent.putExtra("USER_GENDER", gender);
        intent.putExtra("USER_GOAL", goal);
        intent.putExtra("USER_ACTIVITY", activityLevel);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}