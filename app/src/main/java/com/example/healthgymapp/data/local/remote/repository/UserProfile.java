package com.example.healthgymapp.data.local.remote.repository;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Hồ sơ người dùng. App chỉ có MỘT hồ sơ nên id luôn là PROFILE_ID (=1):
 * mỗi lần lưu sẽ ghi đè đúng dòng này, không sinh thêm dòng mới.
 */
@Entity(tableName = "user_profile")
public class UserProfile {

    public static final int PROFILE_ID = 1;

    @PrimaryKey
    private int id;

    private String fullName;
    private int age;
    private String gender; // "Nam" hoặc "Nữ"
    private float height;  // cm
    private float weight;  // kg
    private float activityLevel; // Hệ số vận động (1.2 -> 1.9)
    private String fitnessGoal;  // Lưu nguyên chữ hiển thị, ví dụ "Tăng cơ (Bulking)"

    public UserProfile(String fullName, int age, String gender, float height, float weight, float activityLevel, String fitnessGoal) {
        this.id = PROFILE_ID;
        this.fullName = fullName;
        this.age = age;
        this.gender = gender;
        this.height = height;
        this.weight = weight;
        this.activityLevel = activityLevel;
        this.fitnessGoal = fitnessGoal;
    }

    // Getters và Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public float getHeight() { return height; }
    public float getWeight() { return weight; }
    public float getActivityLevel() { return activityLevel; }
    public String getFitnessGoal() { return fitnessGoal; }

    /** Đổi số thực thành chữ gọn: 67.0 -> "67", 67.5 -> "67.5". */
    public static String formatNumber(float value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}