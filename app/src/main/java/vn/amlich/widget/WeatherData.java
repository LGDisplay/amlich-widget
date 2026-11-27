package vn.amlich.widget;

import org.json.JSONArray;
import org.json.JSONObject;

/** Dữ liệu thời tiết đã phân tích từ Open-Meteo. */
final class WeatherData {

    double temp;
    int code;
    boolean isDay;
    String[] dates;   // yyyy-MM-dd
    int[] codes;
    double[] hi;
    double[] lo;

    static WeatherData parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        JSONArray t = daily.getJSONArray("time");
        JSONArray c = daily.getJSONArray("weather_code");
        JSONArray mx = daily.getJSONArray("temperature_2m_max");
        JSONArray mn = daily.getJSONArray("temperature_2m_min");

        WeatherData w = new WeatherData();
        w.temp = cur.getDouble("temperature_2m");
        w.code = cur.getInt("weather_code");
        w.isDay = cur.optInt("is_day", 1) == 1;
        int n = t.length();
        w.dates = new String[n];
        w.codes = new int[n];
        w.hi = new double[n];
        w.lo = new double[n];
        for (int i = 0; i < n; i++) {
            w.dates[i] = t.getString(i);
            w.codes[i] = c.getInt(i);
            w.hi[i] = mx.getDouble(i);
            w.lo[i] = mn.getDouble(i);
        }
        return w;
    }

    /** Vị trí của ngày hôm nay trong mảng dự báo (0 nếu dữ liệu đã cũ hơn). */
    int indexOf(String yyyyMMdd) {
        for (int i = 0; i < dates.length; i++) {
            if (dates[i].equals(yyyyMMdd)) {
                return i;
            }
        }
        return 0;
    }

    static String conditionText(int code) {
        if (code == 0) return "Trời quang";
        if (code == 1) return "Ít mây";
        if (code == 2) return "Có mây";
        if (code == 3) return "Nhiều mây";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 57) return "Mưa phùn";
        if (code >= 61 && code <= 67) return "Mưa";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Tuyết";
        if (code >= 80 && code <= 82) return "Mưa rào";
        if (code >= 95 && code <= 99) return "Dông";
        return "Không rõ";
    }

    /** Biểu tượng: 0 trời quang (ngày/đêm), 1 ít mây, 2 nhiều mây, 3 mưa. */
    static int iconKind(int code, boolean isDay) {
        if (code == 0) return isDay ? 0 : 4;
        if (code == 1 || code == 2) return 1;
        if (code == 3 || code == 45 || code == 48 || (code >= 71 && code <= 77) || code == 85 || code == 86) {
            return 2;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) return 3;
        return 2;
    }
}
