package com.cinejoy.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Ø´Ø§Ø´Ø© Ø§Ù„Ø³ÙƒÙˆÙ† (Screensaver) â€” ØªØ¹Ø±Ø¶ Ø§Ù„Ø³Ø§Ø¹Ø© ÙˆÙ…ÙˆØ§Ù‚ÙŠØª Ø§Ù„ØµÙ„Ø§Ø©
 * Ø¹Ù†Ø¯ Ø®Ù…ÙˆÙ„ Ø§Ù„ØªÙ„ÙØ§Ø². Ø§Ù„Ø­Ø³Ø§Ø¨ Ù…Ø­Ù„ÙŠ Ø¨Ø§Ù„ÙƒØ§Ù…Ù„ (Ù„Ø§ ÙŠØ­ØªØ§Ø¬ Ø¥Ù†ØªØ±Ù†Øª)
 * Ø¨Ø¥Ø­Ø¯Ø§Ø«ÙŠØ§Øª Ø§Ù„Ù…Ù‚Ø·Ù… â€” Ø§Ù„Ù‚Ø§Ù‡Ø±Ø©ØŒ ÙˆØ·Ø±ÙŠÙ‚Ø© Ø§Ù„Ù‡ÙŠØ¦Ø© Ø§Ù„Ù…ØµØ±ÙŠØ© Ù„Ù„Ù…Ø³Ø§Ø­Ø©.
 */
public class PrayerDream extends DreamService {

    // Ø¥Ø­Ø¯Ø§Ø«ÙŠØ§Øª Ø§Ù„Ù…Ù‚Ø·Ù… â€” Ø§Ù„Ù‚Ø§Ù‡Ø±Ø©ØŒ Ù…ØµØ±
    private static final double LAT = 29.9773;
    private static final double LNG = 31.3172;
    // Ø·Ø±ÙŠÙ‚Ø© Ø§Ù„Ù‡ÙŠØ¦Ø© Ø§Ù„Ù…ØµØ±ÙŠØ© Ø§Ù„Ø¹Ø§Ù…Ø© Ù„Ù„Ù…Ø³Ø§Ø­Ø©
    private static final double FAJR_ANGLE = 19.5;
    private static final double ISHA_ANGLE = 17.5;

    private static final TimeZone CAIRO = TimeZone.getTimeZone("Africa/Cairo");

    private static final String[] PRAYER_NAMES = {"Ø§Ù„ÙØ¬Ø±", "Ø§Ù„Ø´Ø±ÙˆÙ‚", "Ø§Ù„Ø¸Ù‡Ø±", "Ø§Ù„Ø¹ØµØ±", "Ø§Ù„Ù…ØºØ±Ø¨", "Ø§Ù„Ø¹Ø´Ø§Ø¡"};
    // Ø§Ù„ÙÙ‡Ø§Ø±Ø³ Ø§Ù„ØªÙŠ ØªØ¹ØªØ¨Ø± ØµÙ„ÙˆØ§Øª ÙØ¹Ù„ÙŠØ© (Ø§Ù„Ø´Ø±ÙˆÙ‚ Ù„ÙŠØ³ ØµÙ„Ø§Ø©)
    private static final int[] PRAYER_INDICES = {0, 2, 3, 4, 5};

    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_GREEN = 0xFF00E676;
    private static final int COLOR_TEXT = 0xFFBDBDBD;
    private static final int COLOR_DIM = 0xFF6E6E6E;
    private static final int COLOR_FAINT = 0xFF444444;

    private final android.os.Handler handler = new android.os.Handler();

    private TextView clockView;
    private TextView dateView;
    private TextView nextView;
    private TextView[] nameViews = new TextView[6];
    private TextView[] timeViews = new TextView[6];

    private static final String[] AR_MONTHS = {"ÙŠÙ†Ø§ÙŠØ±", "ÙØ¨Ø±Ø§ÙŠØ±", "Ù…Ø§Ø±Ø³", "Ø£Ø¨Ø±ÙŠÙ„", "Ù…Ø§ÙŠÙˆ", "ÙŠÙˆÙ†ÙŠÙˆ",
            "ÙŠÙˆÙ„ÙŠÙˆ", "Ø£ØºØ³Ø·Ø³", "Ø³Ø¨ØªÙ…Ø¨Ø±", "Ø£ÙƒØªÙˆØ¨Ø±", "Ù†ÙˆÙÙ…Ø¨Ø±", "Ø¯ÙŠØ³Ù…Ø¨Ø±"};
    private static final String[] AR_DAYS = {"Ø§Ù„Ø£Ø­Ø¯", "Ø§Ù„Ø§Ø«Ù†ÙŠÙ†", "Ø§Ù„Ø«Ù„Ø§Ø«Ø§Ø¡", "Ø§Ù„Ø£Ø±Ø¨Ø¹Ø§Ø¡", "Ø§Ù„Ø®Ù…ÙŠØ³", "Ø§Ù„Ø¬Ù…Ø¹Ø©", "Ø§Ù„Ø³Ø¨Øª"};

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(buildUi());
        update();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(tick);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setGravity(Gravity.CENTER);
        root.setPadding(80, 60, 80, 60);

        // Ù…ÙˆØ¶Ø¹ Ø§Ù„Ø­Ø³Ø§Ø¨
        TextView location = new TextView(this);
        location.setText("Ø§Ù„Ù‚Ø§Ù‡Ø±Ø© â€” Ø§Ù„Ù…Ù‚Ø·Ù…");
        location.setTextSize(26);
        location.setTextColor(COLOR_DIM);
        location.setGravity(Gravity.CENTER);
        root.addView(location, vp(28));

        // Ø§Ù„Ø³Ø§Ø¹Ø©
        clockView = new TextView(this);
        clockView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockView.setTextSize(150);
        clockView.setTextColor(COLOR_GREEN);
        clockView.setGravity(Gravity.CENTER);
        root.addView(clockView, vp(250));

        // Ø§Ù„ØªØ§Ø±ÙŠØ®
        dateView = new TextView(this);
        dateView.setTextSize(30);
        dateView.setTextColor(COLOR_TEXT);
        dateView.setGravity(Gravity.CENTER);
        root.addView(dateView, vp(50));

        // Ø§Ù„ØµÙ„Ø§Ø© Ø§Ù„Ù‚Ø§Ø¯Ù…Ø©
        nextView = new TextView(this);
        nextView.setTextSize(38);
        nextView.setTextColor(COLOR_GREEN);
        nextView.setGravity(Gravity.CENTER);
        root.addView(nextView, vp(80));

        // Ø®Ø· ÙØ§ØµÙ„
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_FAINT);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));

        // ØµÙÙˆÙ Ø§Ù„Ù…ÙˆØ§Ù‚ÙŠØª
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setTextSize(i == 1 ? 30 : 36);
            name.setGravity(Gravity.END);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nameViews[i] = name;

            TextView time = new TextView(this);
            time.setTextSize(i == 1 ? 30 : 36);
            time.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            time.setGravity(Gravity.START);
            row.addView(time, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            timeViews[i] = time;

            rows.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 70 - (i == 1 ? 14 : 0)));
        }
        root.addView(rows);

        // ØªØ°ÙŠÙŠÙ„
        TextView footer = new TextView(this);
        footer.setText("Ø­Ø³Ø§Ø¨ Ù…Ø­Ù„ÙŠ Ø¨Ø²ÙˆØ§ÙŠØ§ Ø§Ù„Ù‡ÙŠØ¦Ø© Ø§Ù„Ù…ØµØ±ÙŠØ© Ù„Ù„Ù…Ø³Ø§Ø­Ø©");
        footer.setTextSize(20);
        footer.setTextColor(COLOR_FAINT);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, vp(40));

        return root;
    }

    private LinearLayout.LayoutParams vp(int h) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
    }

    private void update() {
        Calendar now = Calendar.getInstance();
        Calendar cairoNow = Calendar.getInstance(CAIRO);
        cairoNow.setTimeInMillis(now.getTimeInMillis());

        // Ø§Ù„Ø³Ø§Ø¹Ø© ÙˆØ§Ù„ØªØ§Ø±ÙŠØ®
        int h12 = cairoNow.get(Calendar.HOUR_OF_DAY);
        int h = h12 % 12;
        if (h == 0) h = 12;
        String ampm = h12 < 12 ? "Øµ" : "Ù…";
        clockView.setText(String.format("%d:%02d", h, cairoNow.get(Calendar.MINUTE)) + " " + ampm);

        dateView.setText(AR_DAYS[cairoNow.get(Calendar.DAY_OF_WEEK) - 1] + " "
                + cairoNow.get(Calendar.DAY_OF_MONTH) + " "
                + AR_MONTHS[cairoNow.get(Calendar.MONTH)] + " "
                + cairoNow.get(Calendar.YEAR));

        // Ø§Ù„Ù…ÙˆØ§Ù‚ÙŠØª
        double[] times = computeTimes(cairoNow);
        double nowH = cairoNow.get(Calendar.HOUR_OF_DAY)
                + cairoNow.get(Calendar.MINUTE) / 60.0
                + cairoNow.get(Calendar.SECOND) / 3600.0;

        // Ø§Ù„ØµÙ„Ø§Ø© Ø§Ù„Ù‚Ø§Ø¯Ù…Ø©
        int nextIdx = -1;
        double countdownH = 0;
        outer:
        for (int i = 0; i < 6; i++) {
            boolean isPrayer = false;
            for (int p : PRAYER_INDICES) if (p == i) { isPrayer = true; break; }
            if (!isPrayer) continue;
            if (times[i] > nowH) { nextIdx = i; countdownH = times[i] - nowH; break outer; }
        }
        if (nextIdx == -1) {
            // Ø¨Ø¹Ø¯ Ø§Ù„Ø¹Ø´Ø§Ø¡ â€” Ø§Ù„Ù‚Ø§Ø¯Ù…Ø© ÙØ¬Ø± Ø§Ù„ØºØ¯
            Calendar tomorrow = (Calendar) cairoNow.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            double[] tm = computeTimes(tomorrow);
            nextIdx = 0;
            countdownH = (24 - nowH) + tm[0];
        }

        long cdSec = Math.round(countdownH * 3600);
        long cdH = cdSec / 3600, cdM = (cdSec % 3600) / 60, cdS = cdSec % 60;
        nextView.setText("Ø§Ù„ØµÙ„Ø§Ø© Ø§Ù„Ù‚Ø§Ø¯Ù…Ø©: " + PRAYER_NAMES[nextIdx]
                + " â€” Ø¨Ø¹Ø¯ " + String.format("%02d:%02d:%02d", cdH, cdM, cdS));

        // ØªÙ„ÙˆÙŠÙ† Ø§Ù„ØµÙÙˆÙ
        for (int i = 0; i < 6; i++) {
            if (i == nextIdx) {
                nameViews[i].setTextColor(COLOR_GREEN);
                timeViews[i].setTextColor(COLOR_GREEN);
                nameViews[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else if (i == 1) {
                nameViews[i].setTextColor(COLOR_DIM);
                timeViews[i].setTextColor(COLOR_DIM);
                nameViews[i].setTypeface(Typeface.DEFAULT);
            } else {
                nameViews[i].setTextColor(COLOR_TEXT);
                timeViews[i].setTextColor(COLOR_TEXT);
                nameViews[i].setTypeface(Typeface.DEFAULT);
            }
            nameViews[i].setText(PRAYER_NAMES[i]);
            timeViews[i].setText(formatTime(fixHour(times[i])));
        }
    }

    private static String formatTime(double hours) {
        int total = (int) Math.round(hours * 60);
        int h = (total / 60) % 24;
        int m = total % 60;
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%d:%02d %s", h12, m, h < 12 ? "Øµ" : "Ù…");
    }

    // ================= Ø­Ø³Ø§Ø¨ Ø§Ù„Ù…ÙˆØ§Ù‚ÙŠØª (Ø®ÙˆØ§Ø±Ø²Ù…ÙŠØ© ÙÙ„ÙƒÙŠØ© Ù…Ø¹ÙŠØ§Ø±ÙŠØ©) =================

    private double[] computeTimes(Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        double jDate = julian(y, m, d) - LNG / (15 * 24);
        double tz = CAIRO.getOffset(c.getTimeInMillis()) / 3600000.0;

        double[] t = {5 / 24.0, 6 / 24.0, 12 / 24.0, 13 / 24.0, 18 / 24.0, 18 / 24.0};
        double[] times = new double[6];
        for (int iter = 0; iter < 2; iter++) {
            times[0] = sunAngleTime(jDate, FAJR_ANGLE, t[0], -1);
            times[1] = sunAngleTime(jDate, 0.833, t[1], -1);
            times[2] = midDay(jDate, t[2]);
            times[3] = asrTime(jDate, t[3]);
            times[4] = sunAngleTime(jDate, 0.833, t[4], 1);
            times[5] = sunAngleTime(jDate, ISHA_ANGLE, t[5], 1);
            System.arraycopy(times, 0, t, 0, 6);
        }
        for (int i = 0; i < 6; i++) {
            times[i] += tz - LNG / 15;
        }
        // Ø¥Ø²Ø§Ø­Ø§Øª Ø§Ø­ØªÙŠØ§Ø·ÙŠØ© Ø¨Ø³ÙŠØ·Ø© (Ø¯Ù‚ÙŠÙ‚ØªØ§Ù† Ù„Ù„Ø¸Ù‡Ø± ÙˆØ§Ù„Ù…ØºØ±Ø¨ ÙƒÙ…Ø§ ÙÙŠ Ø§Ù„Ø¬Ø¯Ø§ÙˆÙ„ Ø§Ù„Ù…ØµØ±ÙŠØ©)
        times[2] += 2.0 / 60;
        times[4] += 2.0 / 60;
        return times;
    }

    private static double midDay(double jDate, double t) {
        double eqt = sunPosition(jDate + t)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jDate, double angle, double t, int dir) {
        double decl = sunPosition(jDate + t)[0];
        double noon = midDay(jDate, t);
        double cosVal = (-dsin(angle) - dsin(LAT) * dsin(decl)) / (dcos(LAT) * dcos(decl));
        if (cosVal > 1) cosVal = 1;
        if (cosVal < -1) cosVal = -1;
        double delta = Math.toDegrees(Math.acos(cosVal)) / 15.0;
        return noon + dir * delta;
    }

    private static double asrTime(double jDate, double t) {
        double decl = sunPosition(jDate + t)[0];
        double angle = -Math.toDegrees(
                Math.atan(1.0 / (1.0 + Math.tan(Math.toRadians(Math.abs(LAT - decl))))));
        return sunAngleTime(jDate, angle, t, 1);
    }

    private static double[] sunPosition(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double RA = Math.toDegrees(Math.atan2(dsin(L) * dcos(e), dcos(L))) / 15.0;
        RA = fixHour(RA);
        double eqt = q / 15.0 - RA;
        double decl = Math.toDegrees(Math.asin(dsin(e) * dsin(L)));
        return new double[]{decl, eqt};
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0);
        double B = 2 - A + Math.floor(A / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    private static double dsin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double dcos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double fixAngle(double a) { a -= 360.0 * Math.floor(a / 360.0); return a < 0 ? a + 360.0 : a; }
    private static double fixHour(double a) { a -= 24.0 * Math.floor(a / 24.0); return a < 0 ? a + 24.0 : a; }
}
