package com.example.healthgymapp.data.local.remote.repository;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Một lần ghi nhịp tim: thời điểm (mili giây) và số nhịp mỗi phút. */
@Entity(tableName = "heart_rate_record")
public class HeartRateRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private long timestamp; // thời điểm ghi, tính bằng mili giây
    private int bpm;        // nhịp tim (lần/phút)

    public HeartRateRecord(long timestamp, int bpm) {
        this.timestamp = timestamp;
        this.bpm = bpm;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getTimestamp() { return timestamp; }
    public int getBpm() { return bpm; }
}