package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthgymapp.R;

public class BmiCalculatorActivity extends AppCompatActivity {

    private EditText etHeightBmi, etWeightBmi;
    private Button btnCalculateBmi;
    private TextView tvBmiResult, tvBmiStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bmi_calculator);

        etHeightBmi = findViewById(R.id.etHeightBmi);
        etWeightBmi = findViewById(R.id.etWeightBmi);
        btnCalculateBmi = findViewById(R.id.btnCalculateBmi);
        tvBmiResult = findViewById(R.id.tvBmiResult);
        tvBmiStatus = findViewById(R.id.tvBmiStatus);

        // Nhận dữ liệu truyền sang từ DashboardActivity
        Intent intent = getIntent();
        String heightStr = intent.getStringExtra("USER_HEIGHT");
        String weightStr = intent.getStringExtra("USER_WEIGHT");

        // Tự động điền và tính toán luôn nếu có dữ liệu sẵn
        if (heightStr != null && !heightStr.isEmpty() && weightStr != null && !weightStr.isEmpty()) {
            etHeightBmi.setText(heightStr);
            etWeightBmi.setText(weightStr);
            calculateAndDisplayBmi(heightStr, weightStr);
        }

        // Nút tính lại khi thay đổi thông số
        btnCalculateBmi.setOnClickListener(v -> {
            String h = etHeightBmi.getText().toString().trim();
            String w = etWeightBmi.getText().toString().trim();
            if (!h.isEmpty() && !w.isEmpty()) {
                calculateAndDisplayBmi(h, w);
            }
        });
    }

    private void calculateAndDisplayBmi(String heightStr, String weightStr) {
        try {
            float heightCm = Float.parseFloat(heightStr);
            float weightKg = Float.parseFloat(weightStr);

            if (heightCm > 0) {
                float heightM = heightCm / 100f;
                float bmi = weightKg / (heightM * heightM);

                tvBmiResult.setText(String.format("%.1f", bmi));

                if (bmi < 18.5) {
                    tvBmiStatus.setText("Gầy (Thiếu cân)");
                } else if (bmi >= 18.5 && bmi <= 24.9) {
                    tvBmiStatus.setText("Bình thường");
                } else if (bmi >= 25.0 && bmi <= 29.9) {
                    tvBmiStatus.setText("Thừa cân");
                } else {
                    tvBmiStatus.setText("Béo phì");
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }
}