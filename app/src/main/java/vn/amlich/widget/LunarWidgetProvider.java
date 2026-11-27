package vn.amlich.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Widget 4x2: hàng đầu có nút ‹ (trái), "Tháng, năm" (giữa), nút › (phải); ở giữa là lưới tháng;
 * dưới đáy là thanh thông tin ngày hôm nay (dương, âm, can chi, lễ).
 * Chỉ nút ‹ › và "Hôm nay" bấm được. Chạm vào chỗ khác không mở app.
 */
public class LunarWidgetProvider extends AppWidgetProvider {

    static final String ACTION_PREV = "vn.amlich.widget.PREV";
    static final String ACTION_NEXT = "vn.amlich.widget.NEXT";
    static final String ACTION_TODAY = "vn.amlich.widget.TODAY";
    static final String ACTION_MIDNIGHT = "vn.amlich.widget.MIDNIGHT";

    private static final String PREFS = "widget_state";
    private static final String KEY_OFFSET = "offset";
    private static final String KEY_DAY = "day";

    private static final int C_TEXT = 0xFFF4F6FA;
    private static final int C_DIM = 0xFF838CA0;
    private static final int C_SUN = 0xFFFF9C8A;
    private static final int C_HOL = 0xFFFF5A4D;
    private static final int C_HOL_DIM = 0xFFC9736C;
    private static final int C_LUNAR = 0xFFC9A85A;
    private static final int C_LUNAR_DIM = 0xFF7C859A;
    private static final int C_SPECIAL = 0xFF4ADE9A;
    private static final int C_INK = 0xFF1A1405;

    private static final String[] DOW = {
        "CHỦ NHẬT", "THỨ HAI", "THỨ BA", "THỨ TƯ", "THỨ NĂM", "THỨ SÁU", "THỨ BẢY"
    };

    private static final String[] DOW_FULL = {
        "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    // ------------------------------------------------------------------ vòng đời

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
        if (ACTION_PREV.equals(action)) {
            shiftMonth(context, -1);
            updateAll(context);
            return;
        }
        if (ACTION_NEXT.equals(action)) {
            shiftMonth(context, 1);
            updateAll(context);
            return;
        }
        if (ACTION_TODAY.equals(action)) {
            state(context).edit().putInt(KEY_OFFSET, 0).apply();
            updateAll(context);
            return;
        }

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
                        if (HolidayStore.refreshIfStale(context)) {
                            updateAll(context);
                        }
                    } catch (Throwable ignored) {
                        // không để lỗi mạng làm hỏng widget
                    } finally {
                        pr.finish();
                    }
                }
            }).start();
        }
    }

    // ------------------------------------------------------------------ trạng thái

    private static SharedPreferences state(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void shiftMonth(Context ctx, int delta) {
        SharedPreferences sp = state(ctx);
        int off = sp.getInt(KEY_OFFSET, 0) + delta;
        Calendar target = new GregorianCalendar();
        target.set(Calendar.DAY_OF_MONTH, 1);
        target.add(Calendar.MONTH, off);
        int y = target.get(Calendar.YEAR);
        if (y < 1901 || y > 2099) {
            return; // thuật toán âm lịch chỉ chính xác trong khoảng này
        }
        sp.edit().putInt(KEY_OFFSET, off).apply();
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, LunarWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        manager.updateAppWidget(ids, build(context));
    }

    private static PendingIntent pending(Context context, String action, int code) {
        Intent i = new Intent(context, LunarWidgetProvider.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(
                context, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent midnightIntent(Context context) {
        return pending(context, ACTION_MIDNIGHT, 10);
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

    static String solarText(Calendar now) {
        return DOW[now.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + now.get(Calendar.DAY_OF_MONTH) + "/"
                + (now.get(Calendar.MONTH) + 1) + "/"
                + now.get(Calendar.YEAR);
    }

    private static int id(Resources res, String pkg, String name) {
        return res.getIdentifier(name, "id", pkg);
    }

    static RemoteViews build(Context ctx) {
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        Calendar now = Calendar.getInstance();
        int ty = now.get(Calendar.YEAR);
        int tm = now.get(Calendar.MONTH) + 1;
        int td = now.get(Calendar.DAY_OF_MONTH);

        // sang ngày mới thì tự quay về tháng hiện tại
        SharedPreferences sp = state(ctx);
        int todayKey = ty * 10000 + tm * 100 + td;
        if (sp.getInt(KEY_DAY, -1) != todayKey) {
            sp.edit().putInt(KEY_DAY, todayKey).putInt(KEY_OFFSET, 0).apply();
        }
        int offset = sp.getInt(KEY_OFFSET, 0);

        RemoteViews v = new RemoteViews(pkg, R.layout.widget_lunar);

        // ---- cột trái: hôm nay
        int[] lt = LunarCalendar.solar2lunar(td, tm, ty);
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        int[] ln = LunarCalendar.solar2lunar(
                tomorrow.get(Calendar.DAY_OF_MONTH), tomorrow.get(Calendar.MONTH) + 1,
                tomorrow.get(Calendar.YEAR));

        v.setTextViewText(R.id.tv_solar_line,
                DOW_FULL[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + td + " tháng " + tm + ", " + ty);
        v.setTextViewText(R.id.tv_lunar_line,
                "Âm lịch " + lt[0] + "/" + lt[1] + (lt[3] == 1 ? " nhuận" : "")
                        + " · năm " + LunarCalendar.canChi(lt[2])
                        + " · ngày " + LunarCalendar.canChiDay(td, tm, ty));

        String label = LunarCalendar.label(lt, ln);
        if (label.isEmpty() && HolidayStore.isOff(ctx, ty, tm, td)) {
            label = "Nghỉ lễ";
        }
        if (label.isEmpty()) {
            v.setViewVisibility(R.id.tv_event, View.GONE);
        } else {
            v.setTextViewText(R.id.tv_event, label);
            v.setViewVisibility(R.id.tv_event, View.VISIBLE);
        }

        // ---- cột phải: lịch tháng
        Calendar view = new GregorianCalendar(ty, now.get(Calendar.MONTH), 1);
        view.add(Calendar.MONTH, offset);
        int vy = view.get(Calendar.YEAR);
        int vm = view.get(Calendar.MONTH);
        int first = (view.get(Calendar.DAY_OF_WEEK) + 5) % 7; // thứ Hai = 0
        int rows = (first + view.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7;

        v.setTextViewText(R.id.tv_title, "Tháng " + (vm + 1) + ", " + vy);
        v.setViewVisibility(R.id.btn_today, offset != 0 ? View.VISIBLE : View.GONE);
        v.setOnClickPendingIntent(R.id.btn_prev, pending(ctx, ACTION_PREV, 1));
        v.setOnClickPendingIntent(R.id.btn_next, pending(ctx, ACTION_NEXT, 2));
        v.setOnClickPendingIntent(R.id.btn_today, pending(ctx, ACTION_TODAY, 3));

        for (int r = 0; r < 6; r++) {
            v.setViewVisibility(id(res, pkg, "row" + r), r < rows ? View.VISIBLE : View.GONE);
        }

        Calendar cur = new GregorianCalendar(vy, vm, 1 - first);
        for (int i = 0; i < rows * 7; i++) {
            int cy = cur.get(Calendar.YEAR);
            int cm = cur.get(Calendar.MONTH) + 1;
            int cd = cur.get(Calendar.DAY_OF_MONTH);
            int[] l = LunarCalendar.solar2lunar(cd, cm, cy);

            boolean inMonth = (cm - 1) == vm;
            boolean isToday = cy == ty && cm == tm && cd == td;
            boolean isSun = cur.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
            boolean isOff = HolidayStore.isOff(ctx, cy, cm, cd);
            boolean special = l[0] == 1 || l[0] == 15;

            int sc = inMonth ? (isOff ? C_HOL : (isSun ? C_SUN : C_TEXT)) : (isOff ? C_HOL_DIM : C_DIM);
            int lc = inMonth ? (special ? C_SPECIAL : C_LUNAR) : C_LUNAR_DIM;
            int bg = (isOff && inMonth) ? R.drawable.bg_off : 0;
            if (isToday) {
                sc = C_INK;
                lc = C_INK;
                bg = R.drawable.bg_today;
            }

            int sid = id(res, pkg, "s" + i);
            int lid = id(res, pkg, "l" + i);
            int cid = id(res, pkg, "c" + i);
            v.setTextViewText(sid, String.valueOf(cd));
            v.setTextColor(sid, sc);
            v.setTextViewText(lid, l[0] == 1 ? (l[0] + "/" + l[1]) : String.valueOf(l[0]));
            v.setTextColor(lid, lc);
            v.setInt(cid, "setBackgroundResource", bg);

            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        return v;
    }
}
