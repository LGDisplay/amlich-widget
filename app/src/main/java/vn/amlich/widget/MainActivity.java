package vn.amlich.widget;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Màn hình hướng dẫn, cấp quyền vị trí cho thời tiết và kiểm tra việc tải dữ liệu. */
public class MainActivity extends Activity {

    private static final int REQ_FG = 1;
    private static final int REQ_BG = 2;

    private TextView holStatus;
    private TextView wxStatus;

    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        int pad = dp(24);

        Calendar now = Calendar.getInstance();
        int[] l = LunarCalendar.solar2lunar(
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView info = text(16, "Hôm nay: " + LunarWidgetProvider.solarText(now) + "\n"
                + "Âm lịch: " + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "")
                + " năm " + LunarCalendar.canChi(l[2]) + "\n\n"
                + "Thêm widget: nhấn giữ chỗ trống trên màn hình chính, chọn Widget, rồi kéo "
                + "\"Âm lịch Việt Nam\" (lịch tháng) hoặc \"Đồng hồ & thời tiết\" ra màn hình.");
        root.addView(info);

        // ----- thời tiết
        root.addView(title("Thời tiết theo vị trí"));
        wxStatus = text(14, "");
        root.addView(wxStatus);

        Button grant = button("1. Cấp quyền vị trí");
        grant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_FG);
            }
        });
        root.addView(grant);

        if (Build.VERSION.SDK_INT >= 29) {
            Button bg = button("2. Cho phép \"luôn luôn\" (để widget tự cập nhật khi đi nơi khác)");
            bg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!WeatherStore.hasPermission(MainActivity.this)) {
                        wxStatus.setText("Hãy bấm bước 1 trước.");
                        return;
                    }
                    requestPermissions(new String[] {Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG);
                }
            });
            root.addView(bg);
        }

        Button refreshWx = button("Làm mới thời tiết ngay");
        refreshWx.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshWeather();
            }
        });
        root.addView(refreshWx);

        // ----- ngày nghỉ
        root.addView(title("Ngày nghỉ lễ"));
        holStatus = text(14, "");
        root.addView(holStatus);

        Button refreshHol = button("Cập nhật ngày nghỉ ngay");
        refreshHol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holStatus.setText("Đang tải...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = HolidayStore.refreshNow(getApplicationContext());
                        if (ok) {
                            LunarWidgetProvider.updateAll(getApplicationContext());
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showHolidayStatus();
                                if (!ok) {
                                    holStatus.setText(holStatus.getText()
                                            + "\n(Lần tải vừa rồi không thành công. Kiểm tra mạng"
                                            + " và chắc repo GitHub để chế độ Public.)");
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        root.addView(refreshHol);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        showWeatherStatus(null);
        showHolidayStatus();
        if (WeatherStore.hasPermission(this) && WeatherStore.load(this) == null) {
            refreshWeather();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_FG && granted) {
            refreshWeather();
        } else {
            showWeatherStatus(null);
        }
    }

    private void refreshWeather() {
        wxStatus.setText("Đang lấy vị trí và tải thời tiết...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String err = WeatherStore.refreshNow(getApplicationContext());
                if (err == null) {
                    WeatherWidgetProvider.updateAll(getApplicationContext());
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showWeatherStatus(err);
                    }
                });
            }
        }).start();
    }

    private void showWeatherStatus(String error) {
        StringBuilder sb = new StringBuilder();
        boolean fg = WeatherStore.hasPermission(this);
        sb.append("Quyền vị trí: ").append(fg ? "đã cấp" : "chưa cấp");
        if (Build.VERSION.SDK_INT >= 29) {
            sb.append("\nChạy nền (luôn luôn): ")
                    .append(WeatherStore.hasBackgroundPermission(this) ? "đã cấp" : "chưa cấp");
        }
        long t = WeatherStore.lastFetched(this);
        WeatherData w = WeatherStore.load(this);
        if (t != 0 && w != null) {
            sb.append("\nCập nhật lúc ")
                    .append(new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(t)))
                    .append(": ").append(Math.round(w.temp)).append("°, ")
                    .append(WeatherData.conditionText(w.code))
                    .append(" · ").append(WeatherStore.place(this));
        } else {
            sb.append("\nChưa có dữ liệu thời tiết.");
        }
        if (error != null) {
            sb.append("\n").append(error);
        }
        wxStatus.setText(sb.toString());
    }

    private void showHolidayStatus() {
        long t = HolidayStore.lastFetched(getApplicationContext());
        if (t == 0) {
            holStatus.setText("Đang dùng dữ liệu đi kèm trong app, chưa tải từ mạng.");
        } else {
            String s = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(t));
            holStatus.setText("Đã cập nhật từ mạng lúc " + s);
        }
    }

    // ------------------------------------------------------------------ dựng giao diện nhỏ

    private int dp(int v) {
        return (int) (v * density);
    }

    private TextView text(int sp, String s) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setText(s);
        return tv;
    }

    private TextView title(String s) {
        TextView tv = text(18, s);
        tv.setPadding(0, dp(24), 0, dp(6));
        return tv;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }
}
