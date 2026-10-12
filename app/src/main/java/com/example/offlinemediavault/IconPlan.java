package com.example.offlinemediavault;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Calendar;

/** Выбор иконки по дате: праздники и времена года. Индексы: 0 классика, 1 зима, 2 осень, 3 весна, 4 лето, 5.. праздники. */
final class IconPlan {
    static final int COUNT = 15;
    static final int NEW_YEAR = 5, VALENTINE = 6, EASTER = 7, HALLOWEEN = 8, MARCH8 = 9, FEB23 = 10,
            EASTER_OR = 11, MOTHER = 12, PATRICK = 13, MAY1 = 14;

    private IconPlan() {}

    static int doy(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d);
        return c.get(Calendar.DAY_OF_YEAR);
    }

    /** Пасха (григорианская), возвращает {месяц 0-11, день}. */
    static int[] easterWestern(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[]{month - 1, day};
    }

    /** Православная пасха в григорианском календаре (1900–2099: юлианская + 13 дней). */
    static int[] easterOrthodox(int y) {
        int a = y % 4, b = y % 7, c = y % 19, d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = ((d + e + 114) % 31) + 1;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, month - 1, day);
        cal.add(Calendar.DAY_OF_YEAR, 13);
        return new int[]{cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)};
    }

    static int pick(Calendar now) {
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH), d = now.get(Calendar.DAY_OF_MONTH);
        int n = now.get(Calendar.DAY_OF_YEAR);
        if (m == Calendar.DECEMBER && d >= 20 || m == Calendar.JANUARY && d <= 8) return NEW_YEAR;
        if (m == Calendar.FEBRUARY && d >= 12 && d <= 15) return VALENTINE;
        if (m == Calendar.FEBRUARY && d >= 21 && d <= 24) return FEB23;
        if (m == Calendar.MARCH && d >= 6 && d <= 9) return MARCH8;
        if (m == Calendar.MARCH && d >= 16 && d <= 18) return PATRICK;
        int[] ew = easterWestern(y), eo = easterOrthodox(y);
        int dw = doy(y, ew[0], ew[1]), dor = doy(y, eo[0], eo[1]);
        if (n >= dor - 3 && n <= dor + 1) return EASTER_OR;
        if (n >= dw - 3 && n <= dw + 1) return EASTER;
        if (m == Calendar.APRIL && d >= 29 || m == Calendar.MAY && d <= 2) return MAY1;
        if (m == Calendar.OCTOBER && d >= 28 || m == Calendar.NOVEMBER && d == 1) return HALLOWEEN;
        if (m == Calendar.NOVEMBER) {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(y, Calendar.NOVEMBER, 30);
            while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) c.add(Calendar.DAY_OF_MONTH, -1);
            int ls = c.get(Calendar.DAY_OF_YEAR);
            if (n >= ls - 1 && n <= ls) return MOTHER;
        }
        if (m == Calendar.DECEMBER || m <= Calendar.FEBRUARY) return 1;
        if (m <= Calendar.MAY) return 3;
        if (m <= Calendar.AUGUST) return 4;
        return 2;
    }

    static int cur(Context c) {
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < COUNT; i++) {
            int st = pm.getComponentEnabledSetting(new ComponentName(c, "com.example.offlinemediavault.Icon" + (i + 1)));
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0)) return i;
        }
        return 0;
    }

    static boolean set(Context c, int n) {
        if (cur(c) == n) return false;
        PackageManager pm = c.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(c, "com.example.offlinemediavault.Icon" + (n + 1)),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (int i = 0; i < COUNT; i++) {
            if (i == n) continue;
            pm.setComponentEnabledSetting(new ComponentName(c, "com.example.offlinemediavault.Icon" + (i + 1)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        return true;
    }

    static boolean auto(Context c) {
        SharedPreferences p = c.getSharedPreferences("omv_settings", Context.MODE_PRIVATE);
        return p.getBoolean("auto_icon", true);
    }

    static void applyAuto(Context c) {
        if (auto(c)) set(c, pick(Calendar.getInstance()));
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            if (!auto(c)) { js.cancel(7701); return; }
            for (JobInfo ji : js.getAllPendingJobs()) if (ji.getId() == 7701) return;
            js.schedule(new JobInfo.Builder(7701, new ComponentName(c, IconJobService.class))
                    .setPeriodic(12L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build());
        } catch (Throwable ignore) {
        }
    }
}
