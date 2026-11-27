package vn.amlich.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Widget 4x2: đồng hồ có giây (ô đồng hồ của hệ thống tự nhảy mỗi giây), ngày dương + âm,
 * thời tiết hiện tại và dự báo 5 ngày theo vị trí. Không có chỗ nào bấm được.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {

    static final String ACTION_MIDNIGHT = "vn.amlich.widget.WEATHER_MIDNIGHT";

    private static final String[] DOW_FULL = {
        "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };
    private static final String[] DOW_SHORT = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        manager.updateAppWidget(appWidgetIds, build(context));
        scheduleNextMidnight(context);
    }

    @Override
    public void onEnabled(Context context) {
        scheduleNextMidnight(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(midnightIntent(context));
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        boolean refresh = AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || ACTION_MIDNIGHT.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action);

        BroadcastReceiver.PendingResult pending = refresh ? goAsync() : null;
        super.onReceive(context, intent);

        if (refresh) {
            if (!AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
                updateAll(context);
                scheduleNextMidnight(context);
            }
            final BroadcastReceiver.PendingResult pr = pending;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (WeatherStore.refreshIfStale(context)) {
                            updateAll(context);
                        }
                    } catch (Throwable ignored) {
                        // lỗi mạng hoặc vị trí không được làm hỏng widget
                    } finally {
                        pr.finish();
                    }
                }
            }).start();
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        manager.updateAppWidget(ids, build(context));
    }

    private static PendingIntent midnightIntent(Context context) {
        Intent i = new Intent(context, WeatherWidgetProvider.class);
        i.setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(
                context, 20, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleNextMidnight(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 5);
        cal.set(Calendar.MILLISECOND, 0);
        am.set(AlarmManager.RTC, cal.getTimeInMillis(), midnightIntent(context));
    }

    // ------------------------------------------------------------------ vẽ widget

    private static int iconRes(int kind) {
        switch (kind) {
            case 0: return R.drawable.ic_w_sun;
            case 1: return R.drawable.ic_w_partly;
            case 3: return R.drawable.ic_w_rain;
            case 4: return R.drawable.ic_w_moon;
            default: return R.drawable.ic_w_cloud;
        }
    }

    private static int id(Resources res, String pkg, String name) {
        return res.getIdentifier(name, "id", pkg);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    static RemoteViews build(Context ctx) {
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        RemoteViews v = new RemoteViews(pkg, R.layout.widget_weather);

        Calendar now = Calendar.getInstance();
        int ty = now.get(Calendar.YEAR);
        int tm = now.get(Calendar.MONTH) + 1;
        int td = now.get(Calendar.DAY_OF_MONTH);

        // ngày dương + âm
        int[] l = LunarCalendar.solar2lunar(td, tm, ty);
        v.setTextViewText(R.id.tv_date,
                DOW_FULL[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + td + " tháng " + tm);
        v.setTextViewText(R.id.tv_lunar_w,
                "Âm lịch " + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "")
                        + " · " + LunarCalendar.canChi(l[2]));

        // thời tiết
        WeatherData w = WeatherStore.load(ctx);
        String today = String.format(Locale.US, "%04d-%02d-%02d", ty, tm, td);

        if (w == null) {
            boolean perm = WeatherStore.hasPermission(ctx);
            v.setImageViewResource(R.id.iv_now, R.drawable.ic_w_cloud);
            v.setTextViewText(R.id.tv_temp, "--°");
            v.setTextViewText(R.id.tv_cond, perm ? "Đang tải..." : "Chưa có vị trí");
            v.setTextViewText(R.id.tv_hilo, perm ? "" : "Mở app để cho phép");
            v.setTextViewText(R.id.tv_place, "");
            for (int i = 0; i < 5; i++) {
                Calendar d = new GregorianCalendar(ty, tm - 1, td + i + 1);
                v.setTextViewText(id(res, pkg, "wd" + i), DOW_SHORT[d.get(Calendar.DAY_OF_WEEK) - 1]);
                v.setImageViewResource(id(res, pkg, "wi" + i), R.drawable.ic_w_cloud);
                v.setTextViewText(id(res, pkg, "wt" + i), "--°/--°");
            }
            return v;
        }

        int idx = w.indexOf(today);
        v.setImageViewResource(R.id.iv_now, iconRes(WeatherData.iconKind(w.code, w.isDay)));
        v.setTextViewText(R.id.tv_temp, Math.round(w.temp) + "°");
        v.setTextViewText(R.id.tv_cond, WeatherData.conditionText(w.code));
        v.setTextViewText(R.id.tv_hilo,
                "Cao " + Math.round(w.hi[idx]) + "° · Thấp " + Math.round(w.lo[idx]) + "°");
        v.setTextViewText(R.id.tv_place, WeatherStore.place(ctx));

        for (int i = 0; i < 5; i++) {
            Calendar d = new GregorianCalendar(ty, tm - 1, td + i + 1);
            int k = idx + i + 1;
            v.setTextViewText(id(res, pkg, "wd" + i), DOW_SHORT[d.get(Calendar.DAY_OF_WEEK) - 1]);
            if (k < w.dates.length) {
                v.setImageViewResource(id(res, pkg, "wi" + i), iconRes(WeatherData.iconKind(w.codes[k], true)));
                v.setTextViewText(id(res, pkg, "wt" + i),
                        Math.round(w.hi[k]) + "°/" + Math.round(w.lo[k]) + "°");
            } else {
                v.setImageViewResource(id(res, pkg, "wi" + i), R.drawable.ic_w_cloud);
                v.setTextViewText(id(res, pkg, "wt" + i), "--°/--°");
            }
        }
        return v;
    }
}
