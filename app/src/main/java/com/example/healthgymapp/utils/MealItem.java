package com.example.healthgymapp.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Một bữa ăn trong thực đơn: tên món và thông tin dinh dưỡng. */
public class MealItem {

    public static final String[] TYPES = {"Bữa sáng", "Bữa trưa", "Bữa phụ", "Bữa tối"};
    public static final String[] TIMES = {"07:00 - 08:00", "11:30 - 12:30", "15:30 - 16:00", "18:30 - 19:30"};

    public String type = "";
    public String name = "";
    public int calories;
    public int protein;
    public int carbs;
    public int fat;

    /** Đọc một bữa từ JSON do AI trả về; loại bữa được gán theo vị trí (sáng, trưa, phụ, tối). */
    public static MealItem fromAi(JSONObject o, int index) throws JSONException {
        MealItem m = fromJson(o);
        if (m.name.isEmpty()) {
            throw new JSONException("Món ăn không có tên");
        }
        m.type = TYPES[index];
        return m;
    }

    public static MealItem fromJson(JSONObject o) {
        MealItem m = new MealItem();
        m.type = o.optString("type", "").trim();
        m.name = o.optString("name", "").trim();
        m.calories = Math.max(0, o.optInt("calories", 0));
        m.protein = Math.max(0, o.optInt("protein", 0));
        m.carbs = Math.max(0, o.optInt("carbs", 0));
        m.fat = Math.max(0, o.optInt("fat", 0));
        return m;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("type", type)
                .put("name", name)
                .put("calories", calories)
                .put("protein", protein)
                .put("carbs", carbs)
                .put("fat", fat);
    }

    public String nutritionLine() {
        return String.format(Locale.getDefault(), "~ %d kcal  |  %dg protein  |  %dg carb  |  %dg béo",
                calories, protein, carbs, fat);
    }
}