package com.example.healthgymapp.data.local.remote.repository;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Nhật ký tập luyện: mỗi bài tập người dùng chọn trong ngày là một dòng. */
@Entity(tableName = "workout_log")
public class WorkoutLog {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String date;      // yyyy-MM-dd
    @NonNull
    private String name;      // tên bài tập
    @NonNull
    private String emoji;
    private int durationMin;  // thời lượng (phút)
    private int calories;     // calo tiêu hao (kcal)
    private long timestamp;   // thời điểm chọn (mili giây)

    public WorkoutLog(@NonNull String date, @NonNull String name, @NonNull String emoji,
                      int durationMin, int calories, long timestamp) {
        this.date = date;
        this.name = name;
        this.emoji = emoji;
        this.durationMin = durationMin;
        this.calories = calories;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull public String getDate() { return date; }
    @NonNull public String getName() { return name; }
    @NonNull public String getEmoji() { return emoji; }
    public int getDurationMin() { return durationMin; }
    public int getCalories() { return calories; }
    public long getTimestamp() { return timestamp; }
}
