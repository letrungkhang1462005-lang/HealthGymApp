package com.example.healthgymapp.utils;

public class HealthCalculator {

    // Tính chỉ số BMI = Cân nặng (kg) / (Chiều cao (m) ^ 2)
    public static float calculateBMI(float weightKg, float heightCm) {
        float heightM = heightCm / 100f;
        return weightKg / (heightM * heightM);
    }

    // Tính BMR theo công thức Mifflin-St Jeor chuẩn khoa học
    public static float calculateBMR(float weightKg, float heightCm, int age, String gender) {
        float bmr = (10f * weightKg) + (6.25f * heightCm) - (5f * age);
        if ("Nam".equalsIgnoreCase(gender)) {
            bmr += 5f;
        } else {
            bmr -= 161f;
        }
        return bmr;
    }

    // Tính TDEE = BMR * Hệ số vận động
    public static float calculateTDEE(float bmr, float activityLevel) {
        return bmr * activityLevel;
    }

    // Tính Calo mục tiêu dựa trên mục tiêu Gym
    public static int calculateTargetCalories(float tdee, String goal) {
        if ("Bulking".equalsIgnoreCase(goal)) {
            return Math.round(tdee + 400); // Tăng cơ: Nạp dư 400 kcal
        } else if ("Cutting".equalsIgnoreCase(goal)) {
            return Math.round(tdee - 400); // Giảm mỡ: Thâm hụt 400 kcal
        }
        return Math.round(tdee); // Giữ dáng (Maintenance)
    }
}
