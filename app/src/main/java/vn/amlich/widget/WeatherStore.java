package vn.amlich.widget;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lấy vị trí máy, tải thời tiết từ Open-Meteo (miễn phí, không cần khóa API) và lưu lại trong máy.
 * Nếu widget chạy ngầm không đọc được vị trí mới (chưa cấp quyền "luôn luôn")
 * thì dùng vị trí đã lưu lần gần nhất.
 */
public final class WeatherStore {

    private static final String PREFS = "weather";
    private static final String KEY_JSON = "json";
    private static final String KEY_FETCHED = "fetched";
    private static final String KEY_TRY = "try";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_PLACE = "place";
    private static final String KEY_PLAT = "plat";
    private static final String KEY_PLON = "plon";

    private static final long STALE_MS = 25L * 60 * 1000;
    private static final long RETRY_MS = 5L * 60 * 1000;

    private static String cachedJson;
    private static WeatherData cachedData;

    private WeatherStore() {}

    // ------------------------------------------------------------------ quyền

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackgroundPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) {
            return hasPermission(ctx);
        }
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ đọc dữ liệu đã lưu

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** @return dữ liệu thời tiết đã lưu, hoặc null nếu chưa có. */
    public static synchronized WeatherData load(Context ctx) {
        String json = prefs(ctx).getString(KEY_JSON, null);
        if (json == null) {
            return null;
        }
        if (!json.equals(cachedJson)) {
            try {
                cachedData = WeatherData.parse(json);
                cachedJson = json;
            } catch (Exception e) {
                cachedData = null;
                cachedJson = null;
            }
        }
        return cachedData;
    }

    public static String place(Context ctx) {
        return prefs(ctx).getString(KEY_PLACE, "");
    }

    public static long lastFetched(Context ctx) {
        return prefs(ctx).getLong(KEY_FETCHED, 0);
    }

    // ------------------------------------------------------------------ làm mới

    /** Tải lại nếu dữ liệu cũ hơn 25 phút. Gọi trên luồng nền. @return true nếu có dữ liệu mới. */
    public static boolean refreshIfStale(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        long now = System.currentTimeMillis();
        if (now - sp.getLong(KEY_FETCHED, 0) < STALE_MS && sp.getString(KEY_JSON, null) != null) {
            return false;
        }
        if (now - sp.getLong(KEY_TRY, 0) < RETRY_MS) {
            return false;
        }
        return refreshNow(ctx) == null;
    }

    /** Lấy vị trí rồi tải thời tiết. Gọi trên luồng nền. @return null nếu thành công, hoặc thông báo lỗi. */
    public static String refreshNow(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        sp.edit().putLong(KEY_TRY, System.currentTimeMillis()).apply();

        if (!hasPermission(ctx)) {
            return "Chưa cấp quyền vị trí.";
        }

        Location loc = lastKnown(ctx);
        if (loc == null) {
            loc = freshLocation(ctx);
        }
        double lat;
        double lon;
        if (loc != null) {
            lat = loc.getLatitude();
            lon = loc.getLongitude();
            sp.edit().putString(KEY_LAT, String.valueOf(lat)).putString(KEY_LON, String.valueOf(lon)).apply();
        } else if (sp.getString(KEY_LAT, null) != null) {
            lat = Double.parseDouble(sp.getString(KEY_LAT, "0"));
            lon = Double.parseDouble(sp.getString(KEY_LON, "0"));
        } else {
            return "Chưa lấy được vị trí. Hãy bật vị trí (GPS) của máy rồi thử lại.";
        }

        String body;
        try {
            body = download(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                            + "&current=temperature_2m,weather_code,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                            + "&timezone=auto&forecast_days=7", lat, lon));
            WeatherData.parse(body); // kiểm tra định dạng trước khi lưu
        } catch (Exception e) {
            return "Không tải được dữ liệu thời tiết. Kiểm tra kết nối mạng.";
        }
        sp.edit().putString(KEY_JSON, body).putLong(KEY_FETCHED, System.currentTimeMillis()).apply();

        updatePlace(ctx, sp, lat, lon);
        return null;
    }

    // ------------------------------------------------------------------ vị trí

    private static Location lastKnown(Context ctx) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return null;
            }
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    /** Hỏi vị trí mới (Android 11 trở lên), chờ tối đa 8 giây. */
    private static Location freshLocation(Context ctx) {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        try {
            final LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null || !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return null;
            }
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Location> out = new AtomicReference<>();
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, new java.util.concurrent.Executor() {
                @Override
                public void execute(Runnable r) {
                    r.run();
                }
            }, new java.util.function.Consumer<Location>() {
                @Override
                public void accept(Location l) {
                    out.set(l);
                    latch.countDown();
                }
            });
            latch.await(8, TimeUnit.SECONDS);
            return out.get();
        } catch (Exception e) {
            return null;
        }
    }

    /** Đổi tọa độ thành tên khu vực (chỉ làm khi đã đi xa chỗ cũ hoặc chưa có tên). */
    private static void updatePlace(Context ctx, SharedPreferences sp, double lat, double lon) {
        String old = sp.getString(KEY_PLACE, "");
        if (!old.isEmpty() && sp.getString(KEY_PLAT, null) != null) {
            float[] d = new float[1];
            Location.distanceBetween(Double.parseDouble(sp.getString(KEY_PLAT, "0")),
                    Double.parseDouble(sp.getString(KEY_PLON, "0")), lat, lon, d);
            if (d[0] < 3000) {
                return;
            }
        }
        String name = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(ctx, new Locale("vi", "VN")).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String p1 = firstNonEmpty(a.getLocality(), a.getSubAdminArea(), a.getSubLocality());
                    String p2 = a.getAdminArea();
                    if (p1 != null && p2 != null && !p1.equals(p2)) {
                        name = p1 + ", " + p2;
                    } else {
                        name = firstNonEmpty(p1, p2);
                    }
                }
            }
        } catch (Exception ignored) {
            // không có dịch vụ đổi tên thì dùng tên mặc định
        }
        if (name == null) {
            name = old.isEmpty() ? "Vị trí hiện tại" : old;
        }
        sp.edit().putString(KEY_PLACE, name)
                .putString(KEY_PLAT, String.valueOf(lat)).putString(KEY_PLON, String.valueOf(lon)).apply();
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ mạng

    private static String download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new Exception("HTTP " + conn.getResponseCode());
            }
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }
}
