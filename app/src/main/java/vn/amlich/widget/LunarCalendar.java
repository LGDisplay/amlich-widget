package vn.amlich.widget;

import java.util.HashMap;
import java.util.Map;

/**
 * Âm lịch Việt Nam (múi giờ UTC+7), tính offline theo thuật toán thiên văn
 * của Hồ Ngọc Đức. Không cần mạng.
 */
public final class LunarCalendar {

    private static final double TZ = 7.0;
    private static final double PI = Math.PI;

    private LunarCalendar() {}

    private static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        }
        return jd;
    }

    private static double newMoon(int k) {
        double T = k / 1236.85;
        double T2 = T * T;
        double T3 = T2 * T;
        double dr = PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double c1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        c1 = c1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * Mpr);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        c1 = c1 - 0.0074 * Math.sin(dr * (M - Mpr)) - 0.0004 * Math.sin(dr * (2 * F + M));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * F - M)) + 0.0006 * Math.sin(dr * (2 * F + Mpr));
        c1 = c1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        double deltat;
        if (T < -11) {
            deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3;
        } else {
            deltat = -0.000278 + 0.000265 * T + 0.000262 * T2;
        }
        return jd1 + c1 - deltat;
    }

    private static int newMoonDay(int k) {
        return (int) (newMoon(k) + 0.5 + TZ / 24);
    }

    private static double sunLongitude(double jdn) {
        double T = (jdn - 2451545.0) / 36525;
        double T2 = T * T;
        double dr = PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L -= PI * 2 * (int) (L / (PI * 2));
        return L;
    }

    private static int sunLongIdx(int dayNumber) {
        return (int) (sunLongitude(dayNumber - 0.5 - TZ / 24) / PI * 6);
    }

    private static int lunarMonth11(int yy) {
        int off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) (off / 29.530588853);
        int nm = newMoonDay(k);
        if (sunLongIdx(nm) >= 9) {
            nm = newMoonDay(k - 1);
        }
        return nm;
    }

    private static int leapMonthOffset(int a11) {
        int k = (int) ((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int i = 1;
        int arc = sunLongIdx(newMoonDay(k + i));
        int last;
        do {
            last = arc;
            i++;
            arc = sunLongIdx(newMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }

    /** @return {ngày âm, tháng âm, năm âm, 1 nếu tháng nhuận (0 nếu không)} */
    public static int[] solar2lunar(int dd, int mm, int yy) {
        int dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) ((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = newMoonDay(k + 1);
        if (monthStart > dayNumber) {
            monthStart = newMoonDay(k);
        }
        int a11 = lunarMonth11(yy);
        int b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = lunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (int) ((monthStart - a11) / 29);
        int lunarLeap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = leapMonthOffset(a11);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) {
                    lunarLeap = 1;
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1;
        }
        return new int[] {lunarDay, lunarMonth, lunarYear, lunarLeap};
    }

    private static final String[] CAN = {
        "Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"
    };
    private static final String[] CHI = {
        "Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"
    };

    public static String canChi(int year) {
        return CAN[(year + 6) % 10] + " " + CHI[(year + 8) % 12];
    }

    /** Can chi của ngày (dương lịch dd/mm/yy). */
    public static String canChiDay(int dd, int mm, int yy) {
        int jd = jdFromDate(dd, mm, yy);
        return CAN[(jd + 9) % 10] + " " + CHI[(jd + 1) % 12];
    }

    private static final Map<Integer, String> HOLIDAYS = new HashMap<>();

    static {
        // khóa = tháng * 100 + ngày (âm lịch)
        HOLIDAYS.put(101, "Tết Nguyên Đán");
        HOLIDAYS.put(102, "Mùng 2 Tết");
        HOLIDAYS.put(103, "Mùng 3 Tết");
        HOLIDAYS.put(115, "Rằm tháng Giêng");
        HOLIDAYS.put(303, "Tết Hàn Thực");
        HOLIDAYS.put(310, "Giỗ Tổ Hùng Vương");
        HOLIDAYS.put(415, "Phật Đản");
        HOLIDAYS.put(505, "Tết Đoan Ngọ");
        HOLIDAYS.put(715, "Vu Lan");
        HOLIDAYS.put(815, "Tết Trung Thu");
        HOLIDAYS.put(909, "Tết Trùng Cửu");
        HOLIDAYS.put(1223, "Ông Táo chầu trời");
    }

    /**
     * Nhãn ngắn cho ngày đặc biệt: lễ, Giao thừa, Mùng 1, Rằm. Rỗng nếu là ngày thường.
     *
     * @param today ngày âm hôm nay (kết quả solar2lunar)
     * @param next ngày âm của ngày mai
     */
    public static String label(int[] today, int[] next) {
        int ld = today[0];
        int lm = today[1];
        boolean leap = today[3] == 1;
        if (!leap) {
            String h = HOLIDAYS.get(lm * 100 + ld);
            if (h != null) {
                return h;
            }
        }
        if (next[0] == 1 && next[1] == 1 && next[3] == 0 && lm != 1) {
            return "Giao thừa";
        }
        if (ld == 1) {
            return "Mùng 1";
        }
        if (ld == 15) {
            return "Rằm";
        }
        return "";
    }
}
