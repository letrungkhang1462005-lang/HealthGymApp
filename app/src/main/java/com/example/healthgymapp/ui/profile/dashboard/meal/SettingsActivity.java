package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;

public class SettingsActivity extends AppCompatActivity {

    private ImageView btnBackSettings;
    private ImageView imgAvatar;
    private TextView btnEditProfileHeader;
    private LinearLayout itemProfile, itemGoal, itemBodyIndex, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Ánh xạ View
        btnBackSettings = findViewById(R.id.btnBackSettings);
        imgAvatar = findViewById(R.id.imgAvatar);
        btnEditProfileHeader = findViewById(R.id.btnEditProfileHeader);
        itemProfile = findViewById(R.id.itemProfile);
        itemGoal = findViewById(R.id.itemGoal);
        itemBodyIndex = findViewById(R.id.itemBodyIndex);
        btnLogout = findViewById(R.id.btnLogout);

        // Nút quay lại
        if (btnBackSettings != null) {
            btnBackSettings.setOnClickListener(v -> finish());
        }

        // Chỉnh sửa hồ sơ cá nhân
        android.view.View.OnClickListener openProfileListener = v -> {
            Intent intent = new Intent(SettingsActivity.this, ProfileActivity.class);
            startActivity(intent);
        };

        if (btnEditProfileHeader != null) btnEditProfileHeader.setOnClickListener(openProfileListener);
        if (itemProfile != null) itemProfile.setOnClickListener(openProfileListener);
        if (itemGoal != null) itemGoal.setOnClickListener(openProfileListener);

        // Bấm vào Chỉ số cơ thể -> Mở BmiCalculatorActivity
        if (itemBodyIndex != null) {
            itemBodyIndex.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, BmiCalculatorActivity.class);
                startActivity(intent);
            });
        }

        // Nút Đăng xuất
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                Toast.makeText(SettingsActivity.this, "Đã đăng xuất khỏi tài khoản!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        // Quan sát LiveData tự động cập nhật avatar theo giới tính
        loadUserAvatar();
    }

    private void loadUserAvatar() {
        if (imgAvatar == null) return;

        // Sử dụng LiveData.observe để lắng nghe sự thay đổi tự động từ Room Database
        AppDatabase.getInstance(this).userDao().getUserProfile().observe(this, user -> {
            if (user != null && user.getGender() != null) {
                String gender = user.getGender().trim().toLowerCase();

                // Kiểm tra giới tính để đổi avatar tương ứng
                if (gender.equals("nam") || gender.equals("male")) {
                    imgAvatar.setImageResource(R.drawable.img_changtrai);
                } else if (gender.equals("nữ") || gender.equals("nu") || gender.equals("female")) {
                    imgAvatar.setImageResource(R.drawable.img_girl);
                } else {
                    imgAvatar.setImageResource(R.drawable.img_changtrai); // Mặc định
                }
            }
        });
    }
}