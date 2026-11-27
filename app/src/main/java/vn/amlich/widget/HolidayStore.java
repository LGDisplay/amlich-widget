package vn.amlich.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ngày nghỉ lễ Việt Nam.
 *
 * Thứ tự ưu tiên:
 *  1. File holidays.json tải từ mạng (lưu lại trong máy, làm mới mỗi ngày).
 *  2. Nếu chưa tải được: holidays.json đi kèm trong app.
 *  3. Năm nào không có trong file (hoặc file ghi "complete": false) thì bổ sung bằng quy tắc:
 *     1/1, 30/4, 1/5, 2/9, Giỗ Tổ 10/3 âm, Tết (30/29 chạp và mùng 1-3), rơi vào T7/CN thì nghỉ bù thứ Hai.
 *
 * Lịch nghỉ Tết và Quốc khánh do Chính phủ công bố từng năm nên cần file cập nhật.
 */
public final class HolidayStore {

    private static final String PREFS = "holidays";
    private static final String KEY_JSON = "json";
    private static final String KEY_FETCHED = "fetched";
    private static final String KEY_TRY = "try";
    private static final long STALE_MS = 24L * 3600 * 1000;
    private static final long RETRY_MS = 3600L * 1000;

    private static JSONObject years;
    private static final Map<Integer, Set<Integer>> CACHE = new HashMap<>();

    private HolidayStore() {}

    /** @return true nếu (năm, tháng, ngày) dương lịch là ngày nghỉ lễ. */
    public static synchronized boolean isOff(Context ctx, int y, int m, int d) {
        return offSet(ctx, y).contains(m * 100 + d);
    }

    /** Thời điểm tải thành công gần nhất (ms), 0 nếu chưa từng. */
    public static long lastFetched(Context ctx) {
        return prefs(ctx).getLong(KEY_FETCHED, 0);
    }

    /** Tải lại nếu dữ liệu cũ hơn 24 giờ. Gọi trên luồng nền. @return true nếu có dữ liệu mới. */
    public static boolean refreshIfStale(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        long now = System.currentTimeMillis();
        boolean have = sp.getString(KEY_JSON, null) != null;
        if (have && now - sp.getLong(KEY_FETCHED, 0) < STALE_MS) {
            return false;
        }
        if (now - sp.getLong(KEY_TRY, 0) < RETRY_MS) {
            return false;
        }
        return fetch(ctx);
    }

    /** Tải ngay, bỏ qua giới hạn thời gian. Gọi trên luồng nền. */
    public static boolean refreshNow(Context ctx) {
        return fetch(ctx);
    }

    // ------------------------------------------------------------------

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean fetch(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        sp.edit().putLong(KEY_TRY, System.currentTimeMillis()).apply();
        String url = BuildConfig.HOLIDAY_URL;
        if (url.contains("OWNER/REPO")) {
            return false; // build ở máy riêng, chưa biết repo nào
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            String body = readAll(conn.getInputStream());
            new JSONObject(body).getJSONObject("years"); // kiểm tra định dạng trước khi lưu
            sp.edit().putString(KEY_JSON, body).putLong(KEY_FETCHED, System.currentTimeMillis()).apply();
            synchronized (HolidayStore.class) {
                years = null;
                CACHE.clear();
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject loadYears(Context ctx) {
        if (years != null) {
            return years;
        }
        try {
            String json = prefs(ctx).getString(KEY_JSON, null);
            if (json == null) {
                json = readAll(ctx.getAssets().open("holidays.json"));
            }
            years = new JSONObject(json).getJSONObject("years");
        } catch (Exception e) {
            years = new JSONObject();
        }
        return years;
    }

    private static Set<Integer> offSet(Context ctx, int y) {
        Set<Integer> cached = CACHE.get(y);
        if (cached != null) {
            return cached;
        }
        Set<Integer> set = new HashSet<>();
        boolean complete = false;
        JSONObject yd = loadYears(ctx).optJSONObject(String.valueOf(y));
        if (yd != null) {
            complete = yd.optBoolean("complete", false);
            JSONArray off = yd.optJSONArray("off");
            String prefix = y + "-";
            if (off != null) {
                for (int i = 0; i < off.length(); i++) {
                    String s = off.optString(i, "");
                    // yyyy-MM-dd
                    if (s.length() == 10 && s.startsWith(prefix)) {
                        try {
                            int m = Integer.parseInt(s.substring(5, 7));
                            int d = Integer.parseInt(s.substring(8, 10));
                            set.add(m * 100 + d);
                        } catch (NumberFormatException ignored) {
                            // bỏ qua dòng hỏng
                        }
                    }
                }
            }
        }
        if (!complete) {
            addRules(y, set);
        }
        CACHE.put(y, set);
        return set;
    }

    /** Thêm ngày lễ; nếu rơi vào T7 hoặc CN thì nghỉ bù vào thứ Hai kế tiếp. */
    private static void addWithSubstitute(Set<Integer> set, int y, int m, int d) {
        set.add(m * 100 + d);
        Calendar c = new GregorianCalendar(y, m - 1, d);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_MONTH, 2);
        } else if (dow == Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        } else {
            return;
        }
        set.add((c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH));
    }

    private static void addRules(int y, Set<Integer> set) {
        addWithSubstitute(set, y, 1, 1);
        addWithSubstitute(set, y, 4, 30);
        addWithSubstitute(set, y, 5, 1);
        addWithSubstitute(set, y, 9, 2);

        Calendar c = new GregorianCalendar(y, Calendar.JANUARY, 1);
        while (c.get(Calendar.YEAR) == y) {
            int d = c.get(Calendar.DAY_OF_MONTH);
            int m = c.get(Calendar.MONTH) + 1;
            int[] l = LunarCalendar.solar2lunar(d, m, y);
            if (l[3] == 0) {
                if (l[1] == 3 && l[0] == 10) {
                    addWithSubstitute(set, y, m, d); // Giỗ Tổ Hùng Vương
                }
                if (l[1] == 1 && l[0] <= 3) {
                    set.add(m * 100 + d); // mùng 1, 2, 3 Tết
                }
                if (l[1] == 1 && l[0] == 1) {
                    Calendar prev = (Calendar) c.clone();
                    prev.add(Calendar.DAY_OF_MONTH, -1); // ngày cuối năm âm lịch
                    set.add((prev.get(Calendar.MONTH) + 1) * 100 + prev.get(Calendar.DAY_OF_MONTH));
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
    }
}
