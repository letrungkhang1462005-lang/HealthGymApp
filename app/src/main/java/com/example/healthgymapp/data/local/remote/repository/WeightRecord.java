package com.example.healthgymapp.data.local.remote.repository;


import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Một mốc cân nặng trong ngày. Khóa chính là ngày (yyyy-MM-dd) nên mỗi ngày chỉ có một dòng:
 * lưu lại trong cùng ngày sẽ ghi đè mốc cũ của ngày đó.
 */
@Entity(tableName = "weight_record")
public class WeightRecord {

    @PrimaryKey
    @NonNull
    private String date; // định dạng yyyy-MM-dd

    private float weight; // kg

    public WeightRecord(@NonNull String date, float weight) {
        this.date = date;
        this.weight = weight;
    }

    @NonNull
    public String getDate() { return date; }
    public float getWeight() { return weight; }
}
