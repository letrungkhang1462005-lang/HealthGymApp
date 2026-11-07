package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;

public class WaterReminderActivity extends AppCompatActivity {

    private EditText etWeightWater;
    private Button btnCalculateTarget, btnAdd200, btnAdd500;
    private TextView tvTargetWater, tvPercentWater, tvResetDay;
    private ProgressBar progressBarWater;

    private int currentWater = 0;
    private int targetWaterMl = 2275; // Mặc định tính theo 65kg

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_reminder);

        // 1. Ánh xạ các View
        etWeightWater = findViewById(R.id.etWeightWater);
        btnCalculateTarget = findViewById(R.id.btnCalculateTarget);
        tvTargetWater = findViewById(R.id.tvTargetWater);
        tvPercentWater = findViewById(R.id.tvPercentWater);
        btnAdd200 = findViewById(R.id.btnAdd200);
        btnAdd500 = findViewById(R.id.btnAdd500);
        tvResetDay = findViewById(R.id.tvResetDay);
        progressBarWater = findViewById(R.id.progressBarWater);

        // 2. Nhận cân nặng truyền từ DashboardActivity sang (nếu có)
        String weightStr = getIntent().getStringExtra("USER_WEIGHT");
        if (weightStr != null && !weightStr.isEmpty()) {
            etWeightWater.setText(weightStr);
            calculateTarget(weightStr);
        } else {
            calculateTarget("65");
        }

        // 3. Sự kiện bấm nút "Tính mục tiêu"
        btnCalculateTarget.setOnClickListener(v -> {
            String weight = etWeightWater.getText().toString().trim();
            if (!weight.isEmpty()) {
                calculateTarget(weight);
                Toast.makeText(this, "Đã cập nhật mục tiêu nước mới!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Vui lòng nhập cân nặng!", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Sự kiện bấm nút +200ml
        btnAdd200.setOnClickListener(v -> addWater(200));

        // 5. Sự kiện bấm nút +500ml
        btnAdd500.setOnClickListener(v -> addWater(500));

        // 6. Sự kiện Đặt lại ngày mới
        tvResetDay.setOnClickListener(v -> {
            currentWater = 0;
            updateWaterUI();
            Toast.makeText(this, "Đã đặt lại về 0ml!", Toast.LENGTH_SHORT).show();
        });
    }

    // Hàm tính mục tiêu nước dựa trên cân nặng (Cân nặng * 35ml)
    private void calculateTarget(String weightStr) {
        try {
            double weight = Double.parseDouble(weightStr);
            targetWaterMl = (int) (weight * 35);
            updateWaterUI();
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    // Hàm cộng thêm nước đã uống
    private void addWater(int amount) {
        currentWater += amount;
        updateWaterUI();
    }

    // Hàm cập nhật chữ hiển thị, phần trăm và vòng tròn ProgressBar
    private void updateWaterUI() {
        if (tvTargetWater != null) {
            tvTargetWater.setText(currentWater + " / " + targetWaterMl + " ml");
        }

        int percent = 0;
        if (targetWaterMl > 0) {
            percent = (int) (((double) currentWater / targetWaterMl) * 100);
            if (percent > 100) percent = 100; // Giới hạn tối đa 100%
        }

        if (tvPercentWater != null) {
            tvPercentWater.setText(percent + "%");
        }

        if (progressBarWater != null) {
            progressBarWater.setProgress(percent);
        }
    }
}