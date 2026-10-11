package com.example.offlinemediavault;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    interface StrCb { void on(String s); }

    static class Thumb { Bitmap bmp; long dur; int pages; }
    static class PdfHolder { PdfRenderer r; ParcelFileDescriptor pfd; int pages = 0, page = 0; }

    /** Маленький квадратик темы: две половины (фон и акцент). */
    static class Swatch extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final int c1, c2, ring;
        final boolean sel;
        Swatch(Context c, int a, int b, int ringColor, boolean selected, final int cornerPx) {
            super(c);
            c1 = a; c2 = b; ring = ringColor; sel = selected;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerPx); }
            });
            setClipToOutline(true);
        }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(c1);
            cv.drawRect(0, 0, w, h, p);
            p.setColor(c2);
            path.reset();
            path.moveTo(w, 0); path.lineTo(w, h); path.lineTo(0, h); path.close();
            cv.drawPath(path, p);
            if (sel) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(4, w / 9f));
                p.setColor(ring);
                cv.drawRect(0, 0, w, h, p);
            }
        }
    }

    /** Контейнер, переносящий чипы на новую строку (с учётом RTL). */
    static class Flow extends ViewGroup {
        final int hGap, vGap;
        Flow(Context c, int h, int v) { super(c); hGap = h; vGap = v; }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec), x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) { x = 0; y += rowH + vGap; rowH = 0; }
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
            setMeasuredDimension(maxW, y + rowH);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l, x = 0, y = 0, rowH = 0;
            boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) { x = 0; y += rowH + vGap; rowH = 0; }
                if (rtl) c.layout(maxW - x - w, y, maxW - x, y + h); else c.layout(x, y, x + w, y + h);
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
        }
    }

    // ---------- Темы ----------
    private static final String[] TH_KEY = {"system", "dark", "light", "teal", "orange", "rose", "green", "navy", "black", "lrose", "lteal", "lorange", "lblue"};
    private static final int[] TH_BG = {0, 0xFF18191D, 0xFFF4F5F8, 0xFF0F1B1E, 0xFF1C1713, 0xFF1D1318, 0xFF121B14, 0xFF0E1A33, 0xFF0A0A0A, 0xFFFBF1F4, 0xFFEFF8F6, 0xFFFAF4EC, 0xFFEFF4FC};
    private static final int[] TH_ACC = {0, 0xFF777FE8, 0xFF4F5BD5, 0xFF22B8A6, 0xFFF0883A, 0xFFE8527E, 0xFF4CC26A, 0xFFF2C14E, 0xFFE0383E, 0xFFD6436E, 0xFF139C8C, 0xFFE07B1F, 0xFF2F6FD6};

    private int BG, PANEL, FIELD, FG, MUTED, ACCENT, BTN, ONACC;
    private boolean darkTheme;
    private static final int RED = 0xFFD64550;

    private String lang = "en";
    private SharedPreferences prefs;
    private Library lib;
    private int cols = 2;
    private boolean showNames = true;

    private static boolean authed = false;
    private static long leftAt = 0;
    private boolean skipLock = false;

    private static final int PICK_MEDIA = 101, PICK_ZIP = 102, PICK_PDF = 103, EXPORT_ZIP = 104;

    private final ArrayList<Dialog> openDialogs = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService pdfExec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Thumb> thumbCache = new LruCache<String, Thumb>(32 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Thumb t) { return t.bmp == null ? 1 : t.bmp.getByteCount(); }
    };

    private FrameLayout appRoot;
    private LinearLayout shell;
    private final View[] tabViews = new View[4];
    private final TextView[] tabBtns = new TextView[4];
    private int curTab = 0;
    private int startTab = -1;

    private String albumOpen = null;
    private final LinkedHashSet<String> sel = new LinkedHashSet<>();
    private FrameLayout lockView;
    private final LinkedHashSet<String> incTags = new LinkedHashSet<>(), excTags = new LinkedHashSet<>();
    private boolean matchAll = true, newestFirst = true;

    // ---------- Жизненный цикл ----------
    @Override protected void attachBaseContext(Context base) {
        SharedPreferences p = base.getSharedPreferences("omv_settings", 0);
        String l = p.getString("lang", "auto");
        if ("auto".equals(l)) l = Locale.getDefault().getLanguage();
        lang = Strings.norm(l);
        Locale loc = new Locale(lang);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(loc);
        c.setLayoutDirection(loc);
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("omv_settings", 0);
        cols = Math.max(1, Math.min(4, prefs.getInt("cols", 2)));
        showNames = prefs.getBoolean("names", true);
        applyTheme();
        lib = new Library(getFilesDir());
        lib.load(getSharedPreferences("library", 0).getString("items", null));
        if (b != null) {
            startTab = b.getInt("tab", -1);
            albumOpen = b.getString("album", null);
        }
        buildAll();
        if (hasPin() && !authed) showLock();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", curTab);
        if (albumOpen != null) out.putString("album", albumOpen);
    }

    @Override protected void onStop() {
        super.onStop();
        leftAt = System.currentTimeMillis();
    }

    @Override protected void onStart() {
        super.onStart();
        if (skipLock) { skipLock = false; return; }
        if (hasPin() && authed && lockView != null && System.currentTimeMillis() - leftAt > 30000) showLock();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        for (Dialog d : new ArrayList<>(openDialogs)) { try { if (d.isShowing()) d.dismiss(); } catch (Exception ignored) { } }
    }

    @Override public void onBackPressed() {
        if (lockView != null && lockView.getVisibility() == View.VISIBLE) { moveTaskToBack(true); return; }
        if (!sel.isEmpty()) { sel.clear(); renderUpload(); return; }
        if (curTab == 1 && albumOpen != null) { albumOpen = null; renderAlbums(); return; }
        super.onBackPressed();
    }

    // ---------- Тема ----------
    private int blend(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private double luma(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
    }

    private boolean systemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int themeIndex(String key) {
        for (int i = 0; i < TH_KEY.length; i++) if (TH_KEY[i].equals(key)) return i;
        return 0;
    }

    private void applyTheme() {
        String key = prefs.getString("theme", "system");
        int idx = themeIndex(key);
        if (idx == 0) idx = systemNight() ? 1 : 2;
        int bg = TH_BG[idx], acc = TH_ACC[idx];
        darkTheme = luma(bg) < 0.5;
        BG = bg;
        FG = darkTheme ? 0xFFF0F0F4 : 0xFF1B1D22;
        PANEL = blend(bg, FG, 0.07f);
        FIELD = blend(bg, FG, 0.13f);
        BTN = blend(bg, FG, 0.2f);
        MUTED = blend(bg, FG, 0.62f);
        ACCENT = acc;
        ONACC = luma(acc) > 0.62 ? 0xFF1B1D22 : 0xFFFFFFFF;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        View decor = getWindow().getDecorView();
        decor.setBackgroundColor(BG);
        int f = decor.getSystemUiVisibility();
        if (!darkTheme) f |= 0x2000 | 0x10; else f &= ~(0x2000 | 0x10);
        decor.setSystemUiVisibility(f);
    }

    // ---------- Мелкие помощники ----------
    private String t(String k) { return Strings.get(lang, k); }
    private String tf(String k, Object... a) { return String.format(Locale.ROOT, Strings.get(lang, k), a); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams params(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private TextView button(String text) {
        TextView b = tv(text, 14, FG, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(BTN, 12));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private TextView primaryButton(String text) {
        TextView b = button(text);
        b.setTextColor(ONACC);
        b.setBackground(round(ACCENT, 12));
        return b;
    }

    private TextView chip(String text, boolean on) { return chip(text, on, ACCENT); }

    private TextView chip(String text, boolean on, int onColor) {
        TextView c = tv(text, 13, on ? (onColor == RED ? 0xFFFFFFFF : ONACC) : FG, false);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setBackground(round(on ? onColor : FIELD, 18));
        return c;
    }

    private void styleEdit(EditText e) {
        e.setTextColor(FG);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(FIELD, 12));
    }

    private TextView sectionLabel(String s) {
        TextView l = tv(s, 12, MUTED, true);
        l.setPadding(0, dp(12), 0, dp(8));
        return l;
    }

    private AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, darkTheme ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private AlertDialog showDlg(AlertDialog.Builder b) {
        AlertDialog d = b.show();
        openDialogs.add(d);
        return d;
    }

    private void promptText(String title, String initial, String hint, final StrCb cb) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setHint(hint);
        styleEdit(input);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, params(-1, 48));
        showDlg(dlg().setTitle(title).setView(box).setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("save"), (d, w) -> cb.on(input.getText().toString().trim())));
    }

    private String getName(Uri uri) {
        String result = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) result = c.getString(ix);
            }
        } catch (Exception ignored) { }
        if (result == null) result = uri.getLastPathSegment();
        if (result == null) result = "media_" + System.currentTimeMillis();
        return new File(result).getName();
    }

    private String fmtDur(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    private String fmtSize(long b) {
        if (b < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
        return String.format(Locale.ROOT, "%.2f GB", b / 1073741824.0);
    }

    // ---------- Каркас и вкладки ----------
    private void buildAll() {
        tabViews[0] = buildFeed();
        tabViews[1] = buildAlbums();
        tabViews[2] = buildUpload();
        tabViews[3] = buildSettings();
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout body = new FrameLayout(this);
        for (int i = 0; i < 4; i++) { tabViews[i].setVisibility(View.GONE); body.addView(tabViews[i], new FrameLayout.LayoutParams(-1, -1)); }
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(PANEL);
        String[] names = {t("tab_feed"), t("tab_albums"), t("tab_upload"), t("tab_settings")};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            TextView b = tv(names[i], 13, MUTED, true);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setOnClickListener(v -> showTab(k));
            tabBtns[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        }
        shell.addView(bar, new LinearLayout.LayoutParams(-1, dp(54)));
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(BG);
        appRoot.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        buildLock();
        appRoot.addView(lockView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appRoot);
        showTab(startTab >= 0 && startTab < 4 ? startTab : (lib.active().isEmpty() ? 2 : 0));
    }

    private void showTab(int tab) {
        curTab = tab;
        for (int i = 0; i < 4; i++) {
            tabViews[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            tabBtns[i].setTextColor(i == tab ? ACCENT : MUTED);
        }
        if (tab == 0) renderFeed(); else if (tab == 1) renderAlbums(); else if (tab == 2) renderUpload(); else renderSettings();
    }

    private void refreshAll() { renderFeed(); renderAlbums(); renderUpload(); }

    private boolean visible(View v) { return v != null && v.getVisibility() == View.VISIBLE; }

    // ---------- Миниатюры ----------
    private FrameLayout makeThumb(Library.Item it) {
        boolean vid = Library.isVideo(it.name), pdf = Library.isPdf(it.name);
        FrameLayout fl = new FrameLayout(this);
        fl.setBackground(round(FIELD, 10));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10)); }
        });
        iv.setClipToOutline(true);
        fl.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        TextView label = (vid || pdf) ? new TextView(this) : null;
        if (vid) {
            TextView play = tv("▶", 15, 0xFFFFFFFF, false);
            play.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x99000000);
            play.setBackground(g);
            fl.addView(play, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        }
        if (label != null) {
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(10);
            label.setPadding(dp(4), dp(1), dp(4), dp(1));
            label.setBackground(round(0x99000000, 4));
            if (pdf) label.setText("PDF"); else label.setVisibility(View.GONE);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, dp(4), dp(4));
            fl.addView(label, dlp);
        }
        loadThumb(it, iv, label);
        return fl;
    }

    private void loadThumb(final Library.Item it, final ImageView iv, final TextView label) {
        iv.setTag(it.path);
        Thumb c = thumbCache.get(it.path);
        if (c != null) { applyThumb(it, c, iv, label); return; }
        pool.execute(() -> {
            final Thumb th = makeThumbData(it);
            thumbCache.put(it.path, th);
            ui.post(() -> { if (it.path.equals(iv.getTag())) applyThumb(it, th, iv, label); });
        });
    }

    private void applyThumb(Library.Item it, Thumb th, ImageView iv, TextView label) {
        if (th.bmp != null) iv.setImageBitmap(th.bmp);
        if (label == null) return;
        if (Library.isPdf(it.name)) { if (th.pages > 0) label.setText("PDF · " + th.pages); }
        else if (th.dur > 0) { label.setText(fmtDur(th.dur)); label.setVisibility(View.VISIBLE); }
    }

    private Thumb makeThumbData(Library.Item it) {
        Thumb th = new Thumb();
        try {
            if (Library.isVideo(it.name)) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(it.path);
                    String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (d != null) th.dur = Long.parseLong(d);
                    Bitmap f = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (f == null) f = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    th.bmp = scaleDown(f, 640);
                } finally { try { r.release(); } catch (Exception ignored) { } }
            } else if (Library.isPdf(it.name)) {
                ParcelFileDescriptor pfd = null;
                PdfRenderer r = null;
                try {
                    pfd = ParcelFileDescriptor.open(new File(it.path), ParcelFileDescriptor.MODE_READ_ONLY);
                    r = new PdfRenderer(pfd);
                    th.pages = r.getPageCount();
                    PdfRenderer.Page p = r.openPage(0);
                    int w = 640;
                    int h = Math.max(1, (int) ((long) w * p.getHeight() / Math.max(1, p.getWidth())));
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(0xFFFFFFFF);
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    p.close();
                    th.bmp = bmp;
                } finally {
                    try { if (r != null) r.close(); } catch (Exception ignored) { }
                    try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
                }
            } else th.bmp = decodeSampled(it.path, 640);
        } catch (Throwable ignored) { }
        return th;
    }

    private Bitmap scaleDown(Bitmap b, int max) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight(), m = Math.max(w, h);
        if (m <= max) return b;
        float k = (float) max / m;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true);
    }

    private Bitmap decodeSampled(String path, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= max && o.outHeight / (sample * 2) >= max) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(path, o2);
        if (b == null) return null;
        try {
            int ori = new android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            if (deg != 0) { Matrix m = new Matrix(); m.postRotate(deg); b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true); }
        } catch (Exception ignored) { }
        return b;
    }

    // ---------- Сетка (лента и альбомы) ----------
    private View makeCell(final Library.Item it, int cw, int thumbH, int tagLines, final ArrayList<Library.Item> list, final int idx) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(makeThumb(it), new LinearLayout.LayoutParams(cw, thumbH));
        if (showNames) {
            TextView n = tv(it.name, 11, FG, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setPadding(dp(2), dp(3), dp(2), 0);
            cell.addView(n, new LinearLayout.LayoutParams(-1, -2));
        }
        List<String> ts = Library.tagsOf(it);
        TextView tg = tv(ts.isEmpty() ? "" : "#" + TextUtils.join(" #", ts), 10.5f, MUTED, false);
        tg.setLines(tagLines);
        tg.setEllipsize(TextUtils.TruncateAt.END);
        tg.setPadding(dp(2), dp(1), dp(2), 0);
        cell.addView(tg, new LinearLayout.LayoutParams(-1, -2));
        cell.setOnClickListener(v -> openViewer(list, idx));
        return cell;
    }

    private void fillGrid(LinearLayout grid, ScrollView sv, final ArrayList<Library.Item> list) {
        grid.removeAllViews();
        int sw = sv.getWidth();
        if (sw <= 0) sw = getResources().getDisplayMetrics().widthPixels;
        int vh = sv.getHeight();
        if (vh <= 0) vh = getResources().getDisplayMetrics().heightPixels - dp(54) - dp(170);
        int gap = dp(8);
        int cw = Math.max(dp(40), (sw - dp(24) - gap * (cols - 1)) / cols);
        int tagLines = cols >= 3 ? 1 : 2;
        int labelH = dp(4) + (showNames ? dp(20) : 0) + dp(14) * tagLines;
        int cellH = cols <= 2 ? Math.max(dp(150), (vh - dp(8) - gap * 2) / 3) : (int) (cw * 1.3f) + labelH;
        int thumbH = Math.max(dp(70), cellH - labelH);
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = gap;
                grid.addView(row, rp);
            }
            View cell = makeCell(list.get(i), cw, thumbH, tagLines, list, i);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cw, -2);
            if (i % cols != 0) cp.setMarginStart(gap);
            row.addView(cell, cp);
        }
    }

    private TextView emptyNote(String text) {
        TextView e = tv(text, 14, MUTED, false);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(16), dp(32), dp(16), dp(32));
        e.setBackground(round(PANEL, 14));
        return e;
    }

    // ---------- Лента ----------
    private ScrollView feedScroll;
    private LinearLayout feedGrid;
    private TextView filterBtn, feedInfo;

    private View buildFeed() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(12), dp(12), dp(4));
        head.addView(tv(t("tab_feed").toUpperCase(Locale.ROOT), 21, FG, true), new LinearLayout.LayoutParams(0, -2, 1));
        filterBtn = tv(t("filter"), 14, ONACC, true);
        filterBtn.setGravity(Gravity.CENTER);
        filterBtn.setPadding(dp(18), 0, dp(18), 0);
        filterBtn.setOnClickListener(v -> openFilter());
        head.addView(filterBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        r.addView(head, new LinearLayout.LayoutParams(-1, -2));
        feedInfo = tv("", 12, MUTED, false);
        feedInfo.setPadding(dp(16), 0, dp(16), dp(8));
        r.addView(feedInfo, new LinearLayout.LayoutParams(-1, -2));
        feedScroll = new ScrollView(this);
        feedGrid = new LinearLayout(this);
        feedGrid.setOrientation(LinearLayout.VERTICAL);
        feedGrid.setPadding(dp(12), 0, dp(12), dp(8));
        feedScroll.addView(feedGrid);
        r.addView(feedScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        feedScroll.addOnLayoutChangeListener((vw, a1, a2, a3, a4, a5, a6, a7, a8) -> {
            if ((a4 - a2) != (a8 - a6) || (a3 - a1) != (a7 - a5)) feedScroll.post(this::renderFeed);
        });
        return r;
    }

    private TreeMap<String, Integer> allTagCounts() {
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (Library.Item it : lib.active()) for (String tg : Library.tagsOf(it)) counts.put(tg, counts.containsKey(tg) ? counts.get(tg) + 1 : 1);
        return counts;
    }

    // Лента: теги (показать только с ними) минус чёрные теги; сортировка по времени загрузки
    private ArrayList<Library.Item> filtered() {
        ArrayList<Library.Item> out = new ArrayList<>();
        for (Library.Item it : lib.active()) {
            List<String> ts = Library.tagsOf(it);
            boolean banned = false;
            for (String x : excTags) if (ts.contains(x)) { banned = true; break; }
            if (banned) continue;
            boolean ok;
            if (incTags.isEmpty()) ok = true;
            else if (matchAll) ok = ts.containsAll(incTags);
            else { ok = false; for (String x : incTags) if (ts.contains(x)) { ok = true; break; } }
            if (ok) out.add(it);
        }
        Collections.sort(out, (m1, m2) -> newestFirst ? Long.compare(m2.added, m1.added) : Long.compare(m1.added, m2.added));
        return out;
    }

    private void renderFeed() {
        if (feedGrid == null || !visible(tabViews[0])) return;
        TreeMap<String, Integer> counts = allTagCounts();
        incTags.retainAll(counts.keySet());
        excTags.retainAll(counts.keySet());
        int active = incTags.size() + excTags.size() + (newestFirst ? 0 : 1);
        filterBtn.setText(active > 0 ? t("filter") + " · " + active : t("filter"));
        filterBtn.setTextColor(active > 0 ? ONACC : FG);
        filterBtn.setBackground(round(active > 0 ? ACCENT : BTN, 20));
        final ArrayList<Library.Item> list = filtered();
        StringBuilder sb = new StringBuilder();
        sb.append(tf("count_of", list.size(), lib.active().size()));
        if (!incTags.isEmpty()) sb.append(" · ").append(tf("with_tags", TextUtils.join(", ", incTags)));
        if (!excTags.isEmpty()) sb.append(" · ").append(tf("hidden_tags", TextUtils.join(", ", excTags)));
        if (!newestFirst) sb.append(" · ").append(t("oldest_first_note"));
        feedInfo.setText(sb.toString());
        feedGrid.removeAllViews();
        if (list.isEmpty()) {
            feedGrid.addView(emptyNote(lib.active().isEmpty() ? t("empty_all") : t("empty_filter")), params(-1, -2));
            return;
        }
        fillGrid(feedGrid, feedScroll, list);
    }

    // ---------- Фильтр ленты ----------
    private void openFilter() {
        final LinkedHashSet<String> inc = new LinkedHashSet<>(incTags), exc = new LinkedHashSet<>(excTags);
        final boolean[] st = {matchAll, newestFirst};
        final TreeMap<String, Integer> counts = allTagCounts();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(10));
        final EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint(t("tag_search"));
        styleEdit(q);
        box.addView(q, params(-1, 44));
        TextView clear = button(t("clear_sel"));
        LinearLayout.LayoutParams clp = params(-1, 40);
        clp.topMargin = dp(8);
        box.addView(clear, clp);
        final LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        box.addView(holder, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            holder.removeAllViews();
            String f = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            while (f.startsWith("#")) f = f.substring(1);
            holder.addView(sectionLabel(t("sec_tags")));
            if (counts.isEmpty()) {
                holder.addView(tv(t("no_tags_yet"), 13, MUTED, false));
            } else {
                Flow f1 = new Flow(this, dp(8), dp(8));
                Flow f2 = new Flow(this, dp(8), dp(8));
                int shown = 0;
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    final String tag = e.getKey();
                    if (!f.isEmpty() && !tag.contains(f)) continue;
                    shown++;
                    TextView c1 = chip("#" + tag + " (" + e.getValue() + ")", inc.contains(tag));
                    c1.setOnClickListener(v -> { if (!inc.remove(tag)) { inc.add(tag); exc.remove(tag); } draw[0].run(); });
                    f1.addView(c1);
                    TextView c2 = chip("#" + tag, exc.contains(tag), RED);
                    c2.setOnClickListener(v -> { if (!exc.remove(tag)) { exc.add(tag); inc.remove(tag); } draw[0].run(); });
                    f2.addView(c2);
                }
                if (shown == 0) {
                    holder.addView(tv(t("no_tag_found"), 13, MUTED, false));
                } else {
                    holder.addView(f1, new LinearLayout.LayoutParams(-1, -2));
                    holder.addView(sectionLabel(t("sec_black")));
                    holder.addView(f2, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            holder.addView(sectionLabel(t("sec_match")));
            Flow f3 = new Flow(this, dp(8), dp(8));
            TextView m1 = chip(t("match_all"), st[0]);
            TextView m2 = chip(t("match_any"), !st[0]);
            m1.setOnClickListener(v -> { st[0] = true; draw[0].run(); });
            m2.setOnClickListener(v -> { st[0] = false; draw[0].run(); });
            f3.addView(m1);
            f3.addView(m2);
            holder.addView(f3, new LinearLayout.LayoutParams(-1, -2));
            holder.addView(sectionLabel(t("sec_sort")));
            Flow f4 = new Flow(this, dp(8), dp(8));
            TextView n1 = chip(t("sort_new"), st[1]);
            TextView n2 = chip(t("sort_old"), !st[1]);
            n1.setOnClickListener(v -> { st[1] = true; draw[0].run(); });
            n2.setOnClickListener(v -> { st[1] = false; draw[0].run(); });
            f4.addView(n1);
            f4.addView(n2);
            holder.addView(f4, new LinearLayout.LayoutParams(-1, -2));
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { draw[0].run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        clear.setOnClickListener(v -> { inc.clear(); exc.clear(); draw[0].run(); });
        draw[0].run();
        showDlg(dlg().setTitle(t("filter_title")).setView(sv)
                .setNegativeButton(t("cancel"), null)
                .setNeutralButton(t("reset"), (d, w) -> { incTags.clear(); excTags.clear(); matchAll = true; newestFirst = true; renderFeed(); })
                .setPositiveButton(t("apply"), (d, w) -> {
                    incTags.clear(); incTags.addAll(inc);
                    excTags.clear(); excTags.addAll(exc);
                    matchAll = st[0]; newestFirst = st[1];
                    renderFeed();
                }));
    }

    // ---------- Альбомы ----------
    private LinearLayout albumListBox, albumGrid;
    private ScrollView albumListScroll, albumDetailScroll;
    private TextView albumTitle, albumBackBtn, albumRenameBtn, albumDelBtn, albumNewBtn;

    private View buildAlbums() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(12), dp(12), dp(8));
        albumBackBtn = tv("←", 22, FG, true);
        albumBackBtn.setGravity(Gravity.CENTER);
        albumBackBtn.setOnClickListener(v -> { albumOpen = null; renderAlbums(); });
        head.addView(albumBackBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumTitle = tv("", 21, FG, true);
        albumTitle.setSingleLine(true);
        albumTitle.setEllipsize(TextUtils.TruncateAt.END);
        albumTitle.setPadding(dp(8), 0, dp(8), 0);
        head.addView(albumTitle, new LinearLayout.LayoutParams(0, -2, 1));
        albumRenameBtn = tv("✎", 20, FG, true);
        albumRenameBtn.setGravity(Gravity.CENTER);
        albumRenameBtn.setOnClickListener(v -> renameAlbum());
        head.addView(albumRenameBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumDelBtn = tv("🗑", 20, FG, true);
        albumDelBtn.setGravity(Gravity.CENTER);
        albumDelBtn.setOnClickListener(v -> deleteAlbumAsk());
        head.addView(albumDelBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumNewBtn = tv("+ " + t("new_album"), 14, ONACC, true);
        albumNewBtn.setGravity(Gravity.CENTER);
        albumNewBtn.setPadding(dp(16), 0, dp(16), 0);
        albumNewBtn.setBackground(round(ACCENT, 20));
        albumNewBtn.setOnClickListener(v -> promptText(t("new_album"), "", t("album_name"), name -> {
            if (!name.isEmpty()) { lib.createAlbum(name); lib.save(); renderAlbums(); }
        }));
        head.addView(albumNewBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        r.addView(head, new LinearLayout.LayoutParams(-1, -2));
        albumListScroll = new ScrollView(this);
        albumListBox = new LinearLayout(this);
        albumListBox.setOrientation(LinearLayout.VERTICAL);
        albumListBox.setPadding(dp(16), 0, dp(16), dp(12));
        albumListScroll.addView(albumListBox);
        r.addView(albumListScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        albumDetailScroll = new ScrollView(this);
        albumGrid = new LinearLayout(this);
        albumGrid.setOrientation(LinearLayout.VERTICAL);
        albumGrid.setPadding(dp(12), 0, dp(12), dp(8));
        albumDetailScroll.addView(albumGrid);
        r.addView(albumDetailScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        albumDetailScroll.addOnLayoutChangeListener((vw, a1, a2, a3, a4, a5, a6, a7, a8) -> {
            if ((a4 - a2) != (a8 - a6) || (a3 - a1) != (a7 - a5)) albumDetailScroll.post(this::renderAlbums);
        });
        return r;
    }

    private void renderAlbums() {
        if (albumListBox == null || !visible(tabViews[1])) return;
        if (albumOpen != null && lib.albumById(albumOpen) == null) albumOpen = null;
        boolean detail = albumOpen != null;
        int dv = detail ? View.VISIBLE : View.GONE, lv = detail ? View.GONE : View.VISIBLE;
        albumBackBtn.setVisibility(dv);
        albumRenameBtn.setVisibility(dv);
        albumDelBtn.setVisibility(dv);
        albumNewBtn.setVisibility(lv);
        albumDetailScroll.setVisibility(dv);
        albumListScroll.setVisibility(lv);
        if (detail) {
            Library.Album a = lib.albumById(albumOpen);
            albumTitle.setText(a.name);
            ArrayList<Library.Item> list = lib.inAlbum(a.id);
            albumGrid.removeAllViews();
            if (list.isEmpty()) albumGrid.addView(emptyNote(t("album_empty")), params(-1, -2));
            else fillGrid(albumGrid, albumDetailScroll, list);
            return;
        }
        albumTitle.setText(t("tab_albums").toUpperCase(Locale.ROOT));
        albumListBox.removeAllViews();
        if (lib.albums.isEmpty()) { albumListBox.addView(emptyNote(t("no_albums")), params(-1, -2)); return; }
        for (final Library.Album a : new ArrayList<>(lib.albums)) {
            ArrayList<Library.Item> its = lib.inAlbum(a.id);
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(9), dp(12), dp(9));
            card.setBackground(round(PANEL, 14));
            LinearLayout.LayoutParams cp = params(-1, -2);
            cp.bottomMargin = dp(8);
            albumListBox.addView(card, cp);
            View cover;
            if (its.isEmpty()) {
                TextView ph = tv("▦", 24, MUTED, false);
                ph.setGravity(Gravity.CENTER);
                ph.setBackground(round(FIELD, 10));
                cover = ph;
            } else cover = makeThumb(its.get(0));
            card.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(76)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            TextView nm = tv(a.name, 15, FG, true);
            nm.setMaxLines(2);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(nm);
            info.addView(tv(tf("items_n", its.size()), 12, MUTED, false));
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            card.setOnClickListener(v -> { albumOpen = a.id; renderAlbums(); });
        }
    }

    private void renameAlbum() {
        final Library.Album a = lib.albumById(albumOpen);
        if (a == null) return;
        promptText(t("rename"), a.name, t("album_name"), name -> {
            if (!name.isEmpty()) { a.name = name; lib.save(); renderAlbums(); }
        });
    }

    private void deleteAlbumAsk() {
        final Library.Album a = lib.albumById(albumOpen);
        if (a == null) return;
        showDlg(dlg().setTitle(t("del_album")).setMessage(a.name + "\n" + t("del_album_msg"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("del_album"), (d, w) -> { lib.deleteAlbum(a.id); lib.save(); albumOpen = null; refreshAll(); }));
    }

    // ---------- Загрузка ----------
    private LinearLayout listBox, upHeader, selBar, selActions, upButtons;
    private EditText search;
    private TextView trashBtn, selCount;

    private View buildUpload() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);

        upHeader = new LinearLayout(this);
        upHeader.setGravity(Gravity.CENTER_VERTICAL);
        upHeader.setPadding(dp(16), dp(12), dp(12), dp(4));
        upHeader.addView(tv(t("tab_upload").toUpperCase(Locale.ROOT), 21, FG, true), new LinearLayout.LayoutParams(0, -2, 1));
        trashBtn = button(t("trash"));
        trashBtn.setPadding(dp(14), 0, dp(14), 0);
        trashBtn.setOnClickListener(v -> openTrash());
        upHeader.addView(trashBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        r.addView(upHeader, new LinearLayout.LayoutParams(-1, -2));

        selBar = new LinearLayout(this);
        selBar.setGravity(Gravity.CENTER_VERTICAL);
        selBar.setPadding(dp(8), dp(10), dp(12), dp(4));
        TextView cancelSel = tv("✕", 20, FG, true);
        cancelSel.setGravity(Gravity.CENTER);
        cancelSel.setOnClickListener(v -> { sel.clear(); renderUpload(); });
        selBar.addView(cancelSel, new LinearLayout.LayoutParams(dp(44), dp(44)));
        selCount = tv("", 17, FG, true);
        selCount.setPadding(dp(8), 0, dp(8), 0);
        selBar.addView(selCount, new LinearLayout.LayoutParams(0, -2, 1));
        TextView selAll = button(t("sel_all"));
        selAll.setOnClickListener(v -> { for (Library.Item it : uploadList()) sel.add(it.id); renderUpload(); });
        selBar.addView(selAll, new LinearLayout.LayoutParams(-2, dp(40)));
        selBar.setVisibility(View.GONE);
        r.addView(selBar, new LinearLayout.LayoutParams(-1, -2));

        selActions = new LinearLayout(this);
        selActions.setPadding(dp(16), dp(4), dp(16), dp(8));
        TextView aTags = button(t("act_tags")), aAlbum = button(t("act_album")), aTrash = primaryButton(t("act_trash"));
        aTags.setOnClickListener(v -> bulkTags());
        aAlbum.setOnClickListener(v -> chooseAlbum(selectedItems()));
        aTrash.setOnClickListener(v -> bulkTrash());
        selActions.addView(aTags, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(46), 1);
        l2.setMarginStart(dp(8));
        selActions.addView(aAlbum, l2);
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(0, dp(46), 1);
        l3.setMarginStart(dp(8));
        selActions.addView(aTrash, l3);
        selActions.setVisibility(View.GONE);
        r.addView(selActions, new LinearLayout.LayoutParams(-1, -2));

        upButtons = new LinearLayout(this);
        upButtons.setPadding(dp(16), dp(6), dp(16), dp(6));
        TextView add = primaryButton(t("add_files")), zip = button(t("add_zip")), manga = button(t("add_manga"));
        add.setOnClickListener(v -> pickMedia());
        zip.setOnClickListener(v -> pickZip());
        manga.setOnClickListener(v -> pickPdf());
        upButtons.addView(add, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams z2 = new LinearLayout.LayoutParams(0, dp(46), 1);
        z2.setMarginStart(dp(8));
        upButtons.addView(zip, z2);
        LinearLayout.LayoutParams z3 = new LinearLayout.LayoutParams(0, dp(46), 1);
        z3.setMarginStart(dp(8));
        upButtons.addView(manga, z3);
        r.addView(upButtons, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setPadding(dp(16), dp(4), dp(8), 0);
        labelRow.addView(tv(t("uploaded"), 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView searchToggle = tv("🔍", 16, FG, false);
        searchToggle.setGravity(Gravity.CENTER);
        labelRow.addView(searchToggle, new LinearLayout.LayoutParams(dp(44), dp(36)));
        r.addView(labelRow, new LinearLayout.LayoutParams(-1, -2));

        // Компактный поиск: скрыт, пока не нажата лупа
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(t("search_hint"));
        styleEdit(search);
        search.setTextSize(13);
        search.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = params(-1, 38);
        sp.setMargins(dp(16), dp(2), dp(16), dp(6));
        r.addView(search, sp);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderUpload(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        searchToggle.setOnClickListener(v -> {
            if (search.getVisibility() == View.VISIBLE) { search.setText(""); search.setVisibility(View.GONE); }
            else { search.setVisibility(View.VISIBLE); search.requestFocus(); }
        });

        ScrollView upScroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(16), dp(4), dp(16), dp(12));
        upScroll.addView(listBox);
        r.addView(upScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return r;
    }

    private ArrayList<Library.Item> uploadList() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<Library.Item> out = new ArrayList<>();
        for (Library.Item it : lib.active()) {
            if (!q.isEmpty() && !it.name.toLowerCase(Locale.ROOT).contains(q) && !it.tags.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(it);
        }
        Collections.sort(out, (x, y) -> Long.compare(y.added, x.added));
        return out;
    }

    private ArrayList<Library.Item> selectedItems() {
        ArrayList<Library.Item> out = new ArrayList<>();
        for (Library.Item it : lib.active()) if (sel.contains(it.id)) out.add(it);
        return out;
    }

    private void renderUpload() {
        if (listBox == null || !visible(tabViews[2])) return;
        HashSet<String> ids = new HashSet<>();
        for (Library.Item it : lib.active()) ids.add(it.id);
        sel.retainAll(ids);
        boolean mode = !sel.isEmpty();
        upHeader.setVisibility(mode ? View.GONE : View.VISIBLE);
        upButtons.setVisibility(mode ? View.GONE : View.VISIBLE);
        selBar.setVisibility(mode ? View.VISIBLE : View.GONE);
        selActions.setVisibility(mode ? View.VISIBLE : View.GONE);
        selCount.setText(tf("sel_n", sel.size()));
        int tc = lib.trashCount();
        trashBtn.setText(t("trash") + (tc > 0 ? " (" + tc + ")" : ""));

        listBox.removeAllViews();
        final ArrayList<Library.Item> list = uploadList();
        for (int i = 0; i < list.size(); i++) {
            final Library.Item it = list.get(i);
            final int idx = i;
            final boolean picked = sel.contains(it.id);
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(9), dp(8), dp(9));
            card.setBackground(round(picked ? blend(PANEL, ACCENT, 0.3f) : PANEL, 14));
            LinearLayout.LayoutParams cp = params(-1, -2);
            cp.bottomMargin = dp(8);
            listBox.addView(card, cp);
            card.addView(makeThumb(it), new LinearLayout.LayoutParams(dp(88), dp(88)));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(10), 0, dp(4), 0);
            card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            TextView name = tv(it.name, 14, FG, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(name);
            String kind = Library.isPdf(it.name) ? t("kind_manga") : Library.isVideo(it.name) ? t("kind_video") : t("kind_photo");
            Library.Album al = lib.albumById(it.album);
            TextView meta = tv(kind + " · " + fmtSize(new File(it.path).length()) + (al != null ? " · " + al.name : ""), 11, ACCENT, false);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(meta);
            List<String> ts = Library.tagsOf(it);
            TextView tag = tv(ts.isEmpty() ? t("tap_add_tags") : "# " + TextUtils.join(", ", ts), 12, MUTED, false);
            tag.setMaxLines(2);
            tag.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = params(-1, -2);
            tp.topMargin = dp(4);
            details.addView(tag, tp);
            TextView side = tv(mode ? (picked ? "☑" : "☐") : "✎", 22, mode && picked ? ACCENT : FG, false);
            side.setGravity(Gravity.CENTER);
            card.addView(side, new LinearLayout.LayoutParams(dp(40), dp(48)));
            card.setOnClickListener(v -> {
                if (!sel.isEmpty()) { if (!sel.remove(it.id)) sel.add(it.id); renderUpload(); }
                else openViewer(list, idx);
            });
            side.setOnClickListener(v -> {
                if (!sel.isEmpty()) { if (!sel.remove(it.id)) sel.add(it.id); renderUpload(); }
                else editTags(it);
            });
            card.setOnLongClickListener(v -> { if (!sel.remove(it.id)) sel.add(it.id); renderUpload(); return true; });
        }
        if (list.isEmpty()) {
            listBox.addView(emptyNote(lib.active().isEmpty() ? t("upload_empty") : t("upload_nomatch")), params(-1, -2));
        }
        TextView foot = tv(t("stored_local"), 12, MUTED, false);
        LinearLayout.LayoutParams fp = params(-1, -2);
        fp.topMargin = dp(12);
        listBox.addView(foot, fp);
    }

    // ---------- Теги, альбомы, корзина ----------
    private EditText tagInput(String initial) {
        EditText input = new EditText(this);
        input.setText(initial);
        input.setHint(t("tags_hint"));
        input.setSingleLine(false);
        input.setTextColor(FG);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(FIELD, 10));
        return input;
    }

    private void editTags(final Library.Item it) {
        final EditText input = tagInput(it.tags);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, params(-1, 56));
        showDlg(dlg().setTitle(t("act_tags")).setMessage(it.name).setView(box)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("save"), (d, w) -> {
                    it.tags = Library.joinTags(Library.parseTags(input.getText().toString()));
                    lib.save();
                    refreshAll();
                }));
    }

    private void bulkTags() {
        final ArrayList<Library.Item> targets = selectedItems();
        if (targets.isEmpty()) return;
        final EditText input = tagInput("");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, params(-1, 56));
        showDlg(dlg().setTitle(t("act_tags") + " · " + tf("sel_n", targets.size())).setView(box)
                .setNegativeButton(t("cancel"), null)
                .setNeutralButton(t("tags_replace"), (d, w) -> {
                    String s = Library.joinTags(Library.parseTags(input.getText().toString()));
                    for (Library.Item it : targets) it.tags = s;
                    lib.save(); sel.clear(); refreshAll();
                })
                .setPositiveButton(t("tags_add"), (d, w) -> {
                    String s = input.getText().toString();
                    for (Library.Item it : targets) it.tags = Library.mergeTags(it.tags, s);
                    lib.save(); sel.clear(); refreshAll();
                }));
    }

    private void bulkTrash() {
        ArrayList<Library.Item> targets = selectedItems();
        for (Library.Item it : targets) lib.moveToTrash(it);
        lib.save();
        sel.clear();
        refreshAll();
        toast(tf("moved_trash", targets.size()));
    }

    private void chooseAlbum(final ArrayList<Library.Item> targets) {
        if (targets.isEmpty()) return;
        final ArrayList<Library.Album> al = new ArrayList<>(lib.albums);
        final String[] names = new String[al.size() + 2];
        names[0] = "+ " + t("new_album");
        for (int i = 0; i < al.size(); i++) names[i + 1] = al.get(i).name;
        names[names.length - 1] = t("no_album");
        showDlg(dlg().setTitle(t("pick_album")).setItems(names, (d, which) -> {
            if (which == 0) {
                promptText(t("new_album"), "", t("album_name"), name -> {
                    if (name.isEmpty()) return;
                    Library.Album a = lib.createAlbum(name);
                    for (Library.Item it : targets) it.album = a.id;
                    lib.save(); sel.clear(); refreshAll(); toast(tf("moved_album", targets.size()));
                });
            } else {
                String id = which == names.length - 1 ? "" : al.get(which - 1).id;
                for (Library.Item it : targets) it.album = id;
                lib.save(); sel.clear(); refreshAll(); toast(tf("moved_album", targets.size()));
            }
        }).setNegativeButton(t("cancel"), null));
    }

    private void openTrash() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(10), dp(12), dp(4));
        TextView close = tv("✕", 20, FG, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = tv(t("trash").toUpperCase(Locale.ROOT), 21, FG, true);
        title.setPadding(dp(8), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = tv(t("trash_hint"), 12, MUTED, false);
        hint.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), 0, dp(16), dp(8));
        TextView restoreAll = button(t("restore_all")), emptyBtn = button(t("empty_trash"));
        emptyBtn.setTextColor(0xFFFFFFFF);
        emptyBtn.setBackground(round(RED, 12));
        actions.addView(restoreAll, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(44), 1);
        ep.setMarginStart(dp(8));
        actions.addView(emptyBtn, ep);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(12));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            box.removeAllViews();
            ArrayList<Library.Item> tl = lib.trashList();
            if (tl.isEmpty()) { box.addView(emptyNote(t("trash_empty")), params(-1, -2)); return; }
            for (final Library.Item it : tl) {
                LinearLayout card = new LinearLayout(this);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(10), dp(9), dp(10), dp(9));
                card.setBackground(round(PANEL, 14));
                LinearLayout.LayoutParams cp = params(-1, -2);
                cp.bottomMargin = dp(8);
                box.addView(card, cp);
                card.addView(makeThumb(it), new LinearLayout.LayoutParams(dp(72), dp(72)));
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(dp(10), 0, 0, 0);
                card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                TextView nm = tv(it.name, 13, FG, true);
                nm.setMaxLines(2);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(nm);
                LinearLayout btns = new LinearLayout(this);
                TextView re = button(t("restore")), de = button(t("del_forever"));
                re.setTextSize(12);
                de.setTextSize(12);
                de.setTextColor(0xFFFFFFFF);
                de.setBackground(round(RED, 12));
                re.setOnClickListener(v -> { lib.restore(it); lib.save(); draw[0].run(); toast(tf("restored", 1)); });
                de.setOnClickListener(v -> showDlg(dlg().setMessage(tf("confirm_forever", 1)).setNegativeButton(t("cancel"), null)
                        .setPositiveButton(t("del_forever"), (dd, w) -> {
                            thumbCache.remove(lib.deleteForever(it));
                            lib.save(); draw[0].run(); toast(tf("deleted", 1));
                        })));
                btns.addView(re, new LinearLayout.LayoutParams(0, dp(38), 1));
                LinearLayout.LayoutParams dpm = new LinearLayout.LayoutParams(0, dp(38), 1);
                dpm.setMarginStart(dp(6));
                btns.addView(de, dpm);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
                bp.topMargin = dp(6);
                info.addView(btns, bp);
            }
        };
        restoreAll.setOnClickListener(v -> {
            ArrayList<Library.Item> tl = lib.trashList();
            for (Library.Item it : tl) lib.restore(it);
            lib.save(); draw[0].run(); toast(tf("restored", tl.size()));
        });
        emptyBtn.setOnClickListener(v -> {
            final int n = lib.trashCount();
            if (n == 0) return;
            showDlg(dlg().setMessage(tf("confirm_forever", n)).setNegativeButton(t("cancel"), null)
                    .setPositiveButton(t("empty_trash"), (dd, w) -> {
                        for (Library.Item it : lib.trashList()) thumbCache.remove(lib.deleteForever(it));
                        lib.save(); draw[0].run(); toast(tf("deleted", n));
                    }));
        });
        draw[0].run();
        d.setContentView(root);
        d.setOnDismissListener(x -> refreshAll());
        openDialogs.add(d);
        d.show();
    }

    // ---------- Полноэкранный просмотр (фото, видео, манга PDF) ----------
    private void stopMedia(FrameLayout media) {
        for (int i = 0; i < media.getChildCount(); i++) {
            View v = media.getChildAt(i);
            if (v instanceof VideoView) ((VideoView) v).stopPlayback();
        }
    }

    private TextView viewerBtn(String s) {
        TextView b = tv(s, 20, 0xFFF0F0F4, false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void closePdf(final PdfHolder h) {
        pdfExec.execute(() -> {
            try { if (h.r != null) h.r.close(); } catch (Exception ignored) { }
            try { if (h.pfd != null) h.pfd.close(); } catch (Exception ignored) { }
            h.r = null;
            h.pfd = null;
            h.pages = 0;
        });
    }

    private void openViewer(final ArrayList<Library.Item> list, int start) {
        if (list.isEmpty()) return;
        final int[] idx = {start};
        final int[] token = {0};
        final Runnable[] show = new Runnable[1];
        final Runnable[] pageShow = new Runnable[1];
        final MediaController[] mcRef = new MediaController[1];
        final ImageView[] pdfView = new ImageView[1];
        final boolean[] pdfMode = {false};
        final PdfHolder pdf = new PdfHolder();
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    if (pdfMode[0]) {
                        int np = pdf.page + (dx < 0 ? 1 : -1);
                        if (np >= 0 && np < pdf.pages) { pdf.page = np; pageShow[0].run(); }
                        return true;
                    }
                    if (list.size() < 2) return false;
                    idx[0] = (idx[0] + (dx < 0 ? 1 : list.size() - 1)) % list.size();
                    show[0].run();
                    return true;
                }
                return false;
            }
        });
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            @Override public boolean dispatchTouchEvent(MotionEvent ev) { gd.onTouchEvent(ev); return super.dispatchTouchEvent(ev); }
        };
        final FrameLayout media = new FrameLayout(this);
        FrameLayout rootF = new FrameLayout(this);
        rootF.setBackgroundColor(0xFF000000);
        rootF.addView(media, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(0xB3000000);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = viewerBtn("✕"), prev = viewerBtn("◀"), next = viewerBtn("▶");
        final TextView title = tv("", 13, 0xFFF0F0F4, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), 0);
        final TextView tagsTv = tv("", 12, 0xFFB0B3BD, false);
        tagsTv.setPadding(dp(52), dp(2), dp(8), 0);
        bar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (list.size() > 1) {
            bar.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(44)));
            bar.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        top.addView(bar);
        top.addView(tagsTv);
        rootF.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        final LinearLayout pdfBar = new LinearLayout(this);
        pdfBar.setGravity(Gravity.CENTER);
        pdfBar.setBackgroundColor(0xB3000000);
        pdfBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView pPrev = viewerBtn("◀"), pNext = viewerBtn("▶");
        final TextView pageTxt = tv("", 14, 0xFFF0F0F4, true);
        pageTxt.setGravity(Gravity.CENTER);
        pdfBar.addView(pPrev, new LinearLayout.LayoutParams(dp(56), dp(44)));
        pdfBar.addView(pageTxt, new LinearLayout.LayoutParams(dp(150), -2));
        pdfBar.addView(pNext, new LinearLayout.LayoutParams(dp(56), dp(44)));
        pdfBar.setVisibility(View.GONE);
        rootF.addView(pdfBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        close.setOnClickListener(v -> dialog.dismiss());
        prev.setOnClickListener(v -> { idx[0] = (idx[0] + list.size() - 1) % list.size(); show[0].run(); });
        next.setOnClickListener(v -> { idx[0] = (idx[0] + 1) % list.size(); show[0].run(); });
        pPrev.setOnClickListener(v -> { if (pdf.page > 0) { pdf.page--; pageShow[0].run(); } });
        pNext.setOnClickListener(v -> { if (pdf.page + 1 < pdf.pages) { pdf.page++; pageShow[0].run(); } });

        pageShow[0] = () -> {
            final int pg = pdf.page;
            final int my = token[0];
            final ImageView target = pdfView[0];
            pdfExec.execute(() -> {
                if (pdf.r == null || pg < 0 || pg >= pdf.pages) return;
                Bitmap bmp = null;
                try {
                    PdfRenderer.Page p = pdf.r.openPage(pg);
                    int w = Math.min(getResources().getDisplayMetrics().widthPixels, 1400);
                    int h = Math.max(1, (int) ((long) w * p.getHeight() / Math.max(1, p.getWidth())));
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(0xFFFFFFFF);
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    p.close();
                } catch (Throwable ignored) { bmp = null; }
                final Bitmap fb = bmp;
                final int total = pdf.pages;
                ui.post(() -> {
                    if (my == token[0] && fb != null && target != null) {
                        target.setImageBitmap(fb);
                        pageTxt.setText(tf("page_n", pg + 1, total));
                    }
                });
            });
        };

        show[0] = () -> {
            token[0]++;
            final int my = token[0];
            if (mcRef[0] != null) mcRef[0].hide();
            stopMedia(media);
            media.removeAllViews();
            closePdf(pdf);
            pdfMode[0] = false;
            pdfBar.setVisibility(View.GONE);
            Library.Item it = list.get(idx[0]);
            title.setText((idx[0] + 1) + " / " + list.size() + "  ·  " + it.name);
            List<String> ts = Library.tagsOf(it);
            tagsTv.setText(ts.isEmpty() ? t("no_tags") : "# " + TextUtils.join(", ", ts));
            final File f = new File(it.path);
            if (!f.exists()) { toast(t("missing")); return; }
            if (Library.isVideo(it.name)) {
                VideoView vv = new VideoView(this);
                MediaController mc = new MediaController(this);
                mc.setAnchorView(vv);
                vv.setMediaController(mc);
                mcRef[0] = mc;
                vv.setVideoPath(f.getAbsolutePath());
                media.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                vv.start();
            } else if (Library.isPdf(it.name)) {
                pdfMode[0] = true;
                pdf.page = 0;
                pdfBar.setVisibility(View.VISIBLE);
                pageTxt.setText("…");
                final ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                media.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                pdfView[0] = iv;
                pdfExec.execute(() -> {
                    try {
                        pdf.pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                        pdf.r = new PdfRenderer(pdf.pfd);
                        pdf.pages = pdf.r.getPageCount();
                    } catch (Exception e) {
                        ui.post(() -> toast(t("pdf_failed")));
                        return;
                    }
                    ui.post(() -> { if (my == token[0]) pageShow[0].run(); });
                });
            } else {
                final ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                media.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                pool.execute(() -> {
                    final Bitmap b = decodeSampled(f.getAbsolutePath(), 2048);
                    ui.post(() -> {
                        if (my == token[0]) { if (b != null) iv.setImageBitmap(b); else toast(t("unsupported")); }
                    });
                });
            }
        };
        dialog.setContentView(rootF);
        dialog.setOnDismissListener(d -> {
            if (mcRef[0] != null) mcRef[0].hide();
            stopMedia(media);
            closePdf(pdf);
        });
        openDialogs.add(dialog);
        dialog.show();
        show[0].run();
    }

    // ---------- ПИН-код ----------
    private TextView pinDots, pinMsg;
    private final StringBuilder pinBuf = new StringBuilder();
    private int pinFails = 0;
    private long pinBlockedUntil = 0;

    private boolean hasPin() { return !prefs.getString("pinhash", "").isEmpty(); }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private boolean checkPin(String pin) {
        return hashPin(pin, prefs.getString("pinsalt", "")).equals(prefs.getString("pinhash", ""));
    }

    private void savePin(String pin) {
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder salt = new StringBuilder();
        for (byte b : raw) salt.append(String.format("%02x", b));
        prefs.edit().putString("pinsalt", salt.toString()).putString("pinhash", hashPin(pin, salt.toString())).apply();
    }

    private void buildLock() {
        lockView = new FrameLayout(this);
        lockView.setBackgroundColor(BG);
        lockView.setClickable(true);
        lockView.setVisibility(View.GONE);
        lockView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(32), dp(24), dp(32), dp(24));
        TextView title = tv(t("pin_enter"), 20, FG, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        pinDots = tv("", 28, FG, false);
        pinDots.setGravity(Gravity.CENTER);
        pinDots.setMinHeight(dp(56));
        col.addView(pinDots, new LinearLayout.LayoutParams(-1, -2));
        pinMsg = tv("", 13, RED, false);
        pinMsg.setGravity(Gravity.CENTER);
        pinMsg.setMinHeight(dp(24));
        col.addView(pinMsg, new LinearLayout.LayoutParams(-1, -2));
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"⌫", "0", "✓"}};
        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            for (final String k : rowKeys) {
                TextView key = tv(k, 24, k.equals("✓") ? ONACC : FG, true);
                key.setGravity(Gravity.CENTER);
                key.setBackground(round(k.equals("✓") ? ACCENT : FIELD, 18));
                key.setOnClickListener(v -> pinKey(k));
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, dp(64), 1);
                kp.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(key, kp);
            }
            col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        lockView.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    }

    private void updateDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pinBuf.length(); i++) sb.append("● ");
        pinDots.setText(sb.toString().trim());
    }

    private void pinKey(String k) {
        if (k.equals("⌫")) {
            if (pinBuf.length() > 0) pinBuf.setLength(pinBuf.length() - 1);
            updateDots();
        } else if (k.equals("✓")) {
            long now = System.currentTimeMillis();
            if (now < pinBlockedUntil) { pinMsg.setText(tf("pin_blocked", (int) ((pinBlockedUntil - now) / 1000) + 1)); return; }
            String p = pinBuf.toString();
            pinBuf.setLength(0);
            updateDots();
            if (checkPin(p)) {
                authed = true;
                pinFails = 0;
                pinMsg.setText("");
                lockView.setVisibility(View.GONE);
                shell.setVisibility(View.VISIBLE);
            } else {
                pinFails++;
                if (pinFails >= 5) { pinFails = 0; pinBlockedUntil = now + 30000; pinMsg.setText(tf("pin_blocked", 30)); }
                else pinMsg.setText(t("pin_wrong"));
            }
        } else if (pinBuf.length() < 8) {
            pinBuf.append(k);
            updateDots();
        }
    }

    private void showLock() {
        authed = false;
        for (Dialog d : new ArrayList<>(openDialogs)) { try { if (d.isShowing()) d.dismiss(); } catch (Exception ignored) { } }
        openDialogs.clear();
        pinBuf.setLength(0);
        updateDots();
        pinMsg.setText("");
        lockView.setVisibility(View.VISIBLE);
        lockView.bringToFront();
        shell.setVisibility(View.INVISIBLE);
    }

    private void promptPin(String title, String msg, final StrCb cb) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setSingleLine(true);
        styleEdit(in);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in, params(-1, 48));
        AlertDialog.Builder b = dlg().setTitle(title).setView(box).setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("ok"), (d, w) -> cb.on(in.getText().toString()));
        if (msg != null) b.setMessage(msg);
        showDlg(b);
    }

    private void setPinFlow() {
        final Runnable askNew = () -> promptPin(t("pin_new"), t("pin_warn"), p1 -> {
            if (!p1.matches("\\d{4,8}")) { toast(t("pin_short")); return; }
            promptPin(t("pin_repeat"), null, p2 -> {
                if (!p1.equals(p2)) { toast(t("pin_mismatch")); return; }
                savePin(p1);
                authed = true;
                toast(t("pin_saved"));
                renderSettings();
            });
        });
        if (hasPin()) promptPin(t("pin_current"), null, cur -> { if (checkPin(cur)) askNew.run(); else toast(t("pin_wrong")); });
        else askNew.run();
    }

    private void removePinFlow() {
        promptPin(t("pin_current"), null, cur -> {
            if (checkPin(cur)) {
                prefs.edit().remove("pinhash").remove("pinsalt").apply();
                toast(t("pin_removed"));
                renderSettings();
            } else toast(t("pin_wrong"));
        });
    }

    // ---------- Настройки ----------
    private LinearLayout setBox;

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        setBox = new LinearLayout(this);
        setBox.setOrientation(LinearLayout.VERTICAL);
        setBox.setPadding(dp(16), dp(12), dp(16), dp(24));
        sv.addView(setBox);
        return sv;
    }

    private LinearLayout settingRow(String label, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(tv(label, 15, FG, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void renderSettings() {
        if (setBox == null || !visible(tabViews[3])) return;
        setBox.removeAllViews();
        setBox.addView(tv(t("tab_settings").toUpperCase(Locale.ROOT), 21, FG, true), new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel(t("set_language")));
        Flow lf = new Flow(this, dp(8), dp(8));
        for (int i = 0; i < Strings.LANGS.length; i++) {
            final String code = Strings.LANGS[i];
            TextView c = chip(Strings.NAMES[i], code.equals(lang));
            c.setOnClickListener(v -> { prefs.edit().putString("lang", code).apply(); recreate(); });
            lf.addView(c);
        }
        setBox.addView(lf, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel(t("set_appearance")));
        setBox.addView(tv(t("set_theme"), 15, FG, true));
        Flow themeFlow = new Flow(this, dp(14), dp(10));
        String cur = prefs.getString("theme", "system");
        String[] caps = {t("theme_system"), t("theme_dark"), t("theme_light")};
        for (int i = 0; i < TH_KEY.length; i++) {
            final int ti = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int c1 = i == 0 ? 0xFF18191D : TH_BG[i];
            int c2 = i == 0 ? 0xFFF4F5F8 : TH_ACC[i];
            Swatch sw = new Swatch(this, c1, c2, ACCENT, TH_KEY[i].equals(cur), dp(10));
            cell.addView(sw, new LinearLayout.LayoutParams(dp(46), dp(46)));
            if (i < 3) {
                TextView cap = tv(caps[i], 10.5f, MUTED, false);
                cap.setGravity(Gravity.CENTER);
                cell.addView(cap, new LinearLayout.LayoutParams(dp(64), -2));
            }
            cell.setOnClickListener(v -> { prefs.edit().putString("theme", TH_KEY[ti]).apply(); recreate(); });
            themeFlow.addView(cell);
        }
        LinearLayout.LayoutParams tfp = new LinearLayout.LayoutParams(-1, -2);
        tfp.topMargin = dp(8);
        setBox.addView(themeFlow, tfp);

        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = button("−"), plus = button("+");
        TextView val = tv(String.valueOf(cols), 16, FG, true);
        val.setGravity(Gravity.CENTER);
        minus.setOnClickListener(v -> { if (cols > 1) { cols--; prefs.edit().putInt("cols", cols).apply(); renderSettings(); } });
        plus.setOnClickListener(v -> { if (cols < 4) { cols++; prefs.edit().putInt("cols", cols).apply(); renderSettings(); } });
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        stepper.addView(val, new LinearLayout.LayoutParams(dp(40), -2));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(-1, -2);
        crp.topMargin = dp(10);
        setBox.addView(settingRow(t("set_columns"), stepper), crp);

        TextView namesChip = chip(showNames ? t("on") : t("off"), showNames);
        namesChip.setOnClickListener(v -> { showNames = !showNames; prefs.edit().putBoolean("names", showNames).apply(); renderSettings(); });
        setBox.addView(settingRow(t("set_names"), namesChip), new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel(t("set_security")));
        setBox.addView(settingRow(t("pin_code"), tv(hasPin() ? t("pin_on") : t("pin_off"), 14, hasPin() ? ACCENT : MUTED, true)), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout pinRow = new LinearLayout(this);
        if (!hasPin()) {
            TextView set = primaryButton(t("pin_set"));
            set.setOnClickListener(v -> setPinFlow());
            pinRow.addView(set, new LinearLayout.LayoutParams(0, dp(46), 1));
        } else {
            TextView change = button(t("pin_change")), remove = button(t("pin_remove"));
            remove.setTextColor(0xFFFFFFFF);
            remove.setBackground(round(RED, 12));
            change.setOnClickListener(v -> setPinFlow());
            remove.setOnClickListener(v -> removePinFlow());
            pinRow.addView(change, new LinearLayout.LayoutParams(0, dp(46), 1));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(46), 1);
            rp.setMarginStart(dp(8));
            pinRow.addView(remove, rp);
        }
        setBox.addView(pinRow, new LinearLayout.LayoutParams(-1, -2));
        TextView warn = tv(t("pin_warn"), 11.5f, MUTED, false);
        warn.setPadding(0, dp(6), 0, 0);
        setBox.addView(warn, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel(t("set_data")));
        TextView exp = button(t("export_all"));
        exp.setOnClickListener(v -> startExport());
        setBox.addView(exp, params(-1, 48));
        TextView eh = tv(t("export_hint"), 11.5f, MUTED, false);
        eh.setPadding(0, dp(6), 0, dp(10));
        setBox.addView(eh, new LinearLayout.LayoutParams(-1, -2));
        TextView del = button(t("delete_all"));
        del.setTextColor(0xFFFFFFFF);
        del.setBackground(round(RED, 12));
        del.setOnClickListener(v -> deleteAllFlow());
        setBox.addView(del, params(-1, 48));
        TextView rs = button(t("reset_settings"));
        rs.setOnClickListener(v -> showDlg(dlg().setTitle(t("reset_settings")).setMessage(t("reset_msg"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("reset"), (d, w) -> {
                    prefs.edit().remove("lang").remove("theme").remove("cols").remove("names").apply();
                    toast(t("reset_done"));
                    recreate();
                })));
        LinearLayout.LayoutParams rsp = params(-1, 48);
        rsp.topMargin = dp(10);
        setBox.addView(rs, rsp);
        TextView ver = tv("Offline Media Vault · v1.4", 11, MUTED, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(18), 0, 0);
        setBox.addView(ver, new LinearLayout.LayoutParams(-1, -2));
    }

    // Двойное подтверждение: «Уверен?» -> «Точно уверен?»
    private void deleteAllFlow() {
        showDlg(dlg().setTitle(t("delete_all_q1")).setMessage(t("delete_all_msg"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("yes"), (d, w) -> showDlg(dlg().setTitle(t("delete_all_q2"))
                        .setNegativeButton(t("cancel"), null)
                        .setPositiveButton(t("yes_delete_all"), (d2, w2) -> wipeAllData()))));
    }

    private void wipeAllData() {
        showBusy("…", 0);
        io.execute(() -> {
            lib.wipeAll();
            ui.post(() -> {
                thumbCache.evictAll();
                hideBusy();
                sel.clear();
                incTags.clear();
                excTags.clear();
                albumOpen = null;
                refreshAll();
                toast(t("all_deleted"));
            });
        });
    }

    // ---------- Загрузка файлов, ZIP, PDF ----------
    private AlertDialog busy;
    private TextView busyText;
    private ProgressBar busyBar;

    private void showBusy(String msg, int max) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(16));
        busyText = tv(msg, 14, FG, false);
        busyText.setSingleLine(true);
        busyText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(busyText, new LinearLayout.LayoutParams(-1, -2));
        busyBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        busyBar.setIndeterminate(max <= 0);
        if (max > 0) busyBar.setMax(max);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(12);
        box.addView(busyBar, bp);
        busy = dlg().setView(box).setCancelable(false).create();
        busy.show();
        openDialogs.add(busy);
    }

    private void setBusy(final String msg, final int prog) {
        ui.post(() -> {
            if (busyText != null && msg != null) busyText.setText(msg);
            if (busyBar != null && !busyBar.isIndeterminate()) busyBar.setProgress(prog);
        });
    }

    private void hideBusy() {
        if (busy != null) { try { busy.dismiss(); } catch (Exception ignored) { } busy = null; }
    }

    private void pickMedia() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_MEDIA);
    }

    private void pickZip() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_ZIP);
    }

    private void pickPdf() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_PDF);
    }

    private void startExport() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "OfflineMediaVault_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(new Date()) + ".zip");
        startActivityForResult(i, EXPORT_ZIP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_MEDIA || requestCode == PICK_PDF) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int n = 0; n < data.getClipData().getItemCount(); n++) uris.add(data.getClipData().getItemAt(n).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) importUris(uris);
        } else if (requestCode == PICK_ZIP && data.getData() != null) {
            importZip(data.getData(), getName(data.getData()));
        } else if (requestCode == EXPORT_ZIP && data.getData() != null) {
            exportAll(data.getData());
        }
    }

    private void pump(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    private Library.Item copyIn(Uri u, String name) {
        File out = lib.uniqueFile(name);
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("no stream");
            pump(in, os);
            return lib.addItem(name, out);
        } catch (Exception e) {
            out.delete();
            return null;
        }
    }

    private void importUris(final ArrayList<Uri> uris) {
        showBusy(t("importing"), uris.size());
        io.execute(() -> {
            int added = 0;
            for (int i = 0; i < uris.size(); i++) {
                Uri u = uris.get(i);
                String name = getName(u);
                if (!Library.isMedia(name)) {
                    final String m = tf("skipped", name);
                    ui.post(() -> toast(m));
                } else {
                    Library.Item it = copyIn(u, name);
                    if (it == null) {
                        final String m = tf("import_failed", name);
                        ui.post(() -> toast(m));
                    } else {
                        added++;
                        if (Library.isPdf(name)) {   // манга: автоматически создаём альбом
                            Library.Album a = lib.createAlbum(Library.baseName(name));
                            it.album = a.id;
                        }
                    }
                }
                setBusy(name + "  (" + (i + 1) + "/" + uris.size() + ")", i + 1);
            }
            lib.save();
            final int n = added;
            ui.post(() -> { hideBusy(); refreshAll(); toast(tf("imported_n", n)); });
        });
    }

    private void importZip(final Uri uri, final String zipName) {
        showBusy(t("importing"), 0);
        io.execute(() -> {
            int count = 0;
            String albumName = null;
            try (InputStream raw = getContentResolver().openInputStream(uri)) {
                if (raw == null) throw new IOException("no stream");
                ZipInputStream zin = new ZipInputStream(raw);
                Library.Parsed meta = null;
                HashMap<String, String> amap = new HashMap<>();
                String zipAlbum = null;
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (!e.isDirectory()) {
                        String ename = e.getName();
                        String base = new File(ename).getName();
                        if (meta == null && ename.equals("library.json")) {
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            pump(zin, bo);
                            meta = Library.parseExport(bo.toString("UTF-8"));
                            if (meta != null) {
                                for (Map.Entry<String, String> en : meta.albumNames.entrySet()) {
                                    Library.Album a = lib.createAlbum(en.getValue());
                                    amap.put(en.getKey(), a.id);
                                }
                            }
                        } else if (Library.isMedia(base)) {
                            File out = lib.uniqueFile(base);
                            try (OutputStream os = new FileOutputStream(out)) { pump(zin, os); }
                            Library.Meta mm = meta == null ? null : meta.byEntry.get(ename);
                            Library.Item it = lib.addItem(mm != null && !mm.name.isEmpty() ? mm.name : base, out);
                            if (mm != null) {
                                it.tags = mm.tags;
                                if (mm.added > 0) it.added = mm.added;
                                String na = amap.get(mm.album);
                                if (na != null) it.album = na;
                            } else if (meta == null) {   // обычный ZIP: автоматически создаём альбом
                                if (zipAlbum == null) {
                                    Library.Album a = lib.createAlbum(Library.baseName(zipName));
                                    zipAlbum = a.id;
                                    albumName = a.name;
                                }
                                it.album = zipAlbum;
                            }
                            count++;
                        }
                    }
                    zin.closeEntry();
                }
            } catch (Exception ex) {
                final String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                lib.save();
                ui.post(() -> { hideBusy(); refreshAll(); toast(tf("zip_failed", msg)); });
                return;
            }
            lib.save();
            final int n = count;
            final String an = albumName;
            ui.post(() -> {
                hideBusy();
                refreshAll();
                toast(tf("imported_n", n));
                if (an != null) toast(tf("album_created", an));
            });
        });
    }

    private void exportAll(final Uri uri) {
        final ArrayList<Library.Item> list = lib.active();
        showBusy(t("exporting"), Math.max(1, list.size()));
        io.execute(() -> {
            int n = 0;
            String err = null;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("no stream");
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os, 65536));
                zos.setLevel(1);
                zos.putNextEntry(new ZipEntry("library.json"));
                zos.write(lib.exportJson(list).getBytes("UTF-8"));
                zos.closeEntry();
                for (int i = 0; i < list.size(); i++) {
                    Library.Item it = list.get(i);
                    File f = new File(it.path);
                    if (f.exists()) {
                        zos.putNextEntry(new ZipEntry("media/" + it.file));
                        try (InputStream in = new FileInputStream(f)) { pump(in, zos); }
                        zos.closeEntry();
                        n++;
                    }
                    setBusy(it.name + "  (" + (i + 1) + "/" + list.size() + ")", i + 1);
                }
                zos.finish();
                zos.flush();
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final int fn = n;
            final String fe = err;
            ui.post(() -> { hideBusy(); toast(fe == null ? tf("export_done", fn) : tf("export_failed", fe)); });
        });
    }
}
