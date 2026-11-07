package com.example.healthgymapp.data.local.remote.repository;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Nhật ký ăn uống: mỗi lần bấm "Lưu vào nhật ký" ở màn chi tiết bữa ăn sẽ thêm một dòng. */
@Entity(tableName = "meal_log")
public class MealLog {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String date;      // yyyy-MM-dd
    @NonNull
    private String mealType;  // Bữa sáng / Bữa trưa / Bữa phụ / Bữa tối
    @NonNull
    private String name;      // tên món
    private int calories;
    private int protein;
    private int carbs;
    private int fat;
    private long timestamp;   // thời điểm lưu (mili giây)

    public MealLog(@NonNull String date, @NonNull String mealType, @NonNull String name,
                   int calories, int protein, int carbs, int fat, long timestamp) {
        this.date = date;
        this.mealType = mealType;
        this.name = name;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull
    public String getDate() { return date; }
    @NonNull
    public String getMealType() { return mealType; }
    @NonNull
    public String getName() { return name; }
    public int getCalories() { return calories; }
    public int getProtein() { return protein; }
    public int getCarbs() { return carbs; }
    public int getFat() { return fat; }
    public long getTimestamp() { return timestamp; }
}