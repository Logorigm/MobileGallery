package com.example.offlinemediavault;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.transition.TransitionManager;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.animation.DecelerateInterpolator;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
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
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.text.DateFormat;
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
    interface IntCb { void on(int i); }

    static class Thumb { Bitmap bmp; long dur; int pages; }
    static class PdfHolder { PdfRenderer r; ParcelFileDescriptor pfd; int pages = 0, page = 0; }

    /** Единый набор векторных иконок вместо текстовых символов и эмодзи. */
    static int glyphRes(CharSequence t) {
        if (t == null) return 0;
        switch (t.toString()) {
            case "✕": case "✖": return R.drawable.ic_close;
            case "←": return R.drawable.ic_back;
            case "✎": return R.drawable.ic_edit;
            case "⋯": return R.drawable.ic_more;
            case "🔍": return R.drawable.ic_search;
            case "▶": return R.drawable.ic_play;
            case "♥": return R.drawable.ic_heart;
            case "♡": return R.drawable.ic_heart_outline;
            case "▣": case "▦": return R.drawable.ic_album;
            case "🗑": return R.drawable.ic_delete;
            case "🔒": return R.drawable.ic_lock;
            case "✓": return R.drawable.ic_check;
            case "☑": return R.drawable.ic_checkbox;
            case "☐": return R.drawable.ic_checkbox_blank;
            case "⌫": return R.drawable.ic_backspace;
            case "‹": case "◀": return R.drawable.ic_chevron_left;
            case "›": return R.drawable.ic_chevron_right;
            case "＋": case "+": return R.drawable.ic_add;
            case "−": return R.drawable.ic_remove;
            case "↑": return R.drawable.ic_arrow_up;
            default: return 0;
        }
    }

    /** Текстовая кнопка, которая рисует векторную иконку, если её текст — известный символ. */
    static class GlyphView extends TextView {
        private Drawable ico;
        private int tint = 0xFFFFFFFF;

        GlyphView(Context c) { super(c); }

        @Override public void setText(CharSequence text, BufferType type) {
            int res = glyphRes(text);
            if (res != 0) {
                Drawable d = getContext().getDrawable(res);
                ico = d == null ? null : d.mutate();
                if (ico != null) ico.setTint(tint);
                super.setText("", type);
                invalidate();
                requestLayout();
            } else {
                ico = null;
                super.setText(text, type);
            }
        }

        @Override public void setTextColor(int color) {
            tint = color;
            if (ico != null) { ico.setTint(color); invalidate(); }
            super.setTextColor(color);
        }

        private int icoSize() { return (int) (getTextSize() * 1.2f); }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            if (ico == null) return;
            int sz = icoSize();
            int mw = Math.max(getMeasuredWidth(), sz + getPaddingLeft() + getPaddingRight());
            int mh = Math.max(getMeasuredHeight(), sz + getPaddingTop() + getPaddingBottom());
            setMeasuredDimension(resolveSize(mw, w), resolveSize(mh, h));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (ico == null) return;
            int aw = getWidth() - getPaddingLeft() - getPaddingRight(), ah = getHeight() - getPaddingTop() - getPaddingBottom();
            int sz = Math.min(icoSize(), Math.min(aw, ah));
            if (sz <= 0) return;
            int cx = getPaddingLeft() + aw / 2, cy = getPaddingTop() + ah / 2;
            ico.setBounds(cx - sz / 2, cy - sz / 2, cx - sz / 2 + sz, cy - sz / 2 + sz);
            ico.draw(c);
        }
    }

    /** Заголовок с мягким градиентом текста. */
    static class GradText extends TextView {
        final int c1, c2;

        GradText(Context c, int a, int b) { super(c); c1 = a; c2 = b; }

        private void shade() {
            float tw = getPaint().measureText(String.valueOf(getText()));
            float w = Math.max(1f, Math.min(getWidth() > 0 ? getWidth() : tw, tw));
            getPaint().setShader(new LinearGradient(0, 0, w, 0, c1, c2, Shader.TileMode.CLAMP));
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); shade(); }

        @Override protected void onTextChanged(CharSequence text, int start, int before, int after) {
            super.onTextChanged(text, start, before, after);
            if (getWidth() > 0) shade();
        }
    }

    /** «Северное сияние»: мягкие цветные пятна акцента на фоне. */
    static class Aurora extends View {
        private final int a1, a2;
        private final boolean dark;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader s1, s2, s3;

        Aurora(Context c, int a1, int a2, boolean dark) { super(c); this.a1 = a1; this.a2 = a2; this.dark = dark; }

        private static int al(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            int a = dark ? 0x40 : 0x2C;
            s1 = new RadialGradient(w * 0.12f, h * 0.06f, w * 0.95f, al(a1, a), al(a1, 0), Shader.TileMode.CLAMP);
            s2 = new RadialGradient(w * 1.0f, h * 0.48f, w * 0.85f, al(a2, a * 3 / 4), al(a2, 0), Shader.TileMode.CLAMP);
            s3 = new RadialGradient(w * 0.25f, h * 1.02f, w * 0.9f, al(a1, a / 2), al(a1, 0), Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas c) {
            if (s1 == null) return;
            for (Shader sh : new Shader[]{s1, s2, s3}) { p.setShader(sh); c.drawRect(0, 0, getWidth(), getHeight(), p); }
        }
    }

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

    /** Контейнер, переносящий чипы на новую строку (с учётом RTL); число строк можно ограничить. */
    static class Flow extends ViewGroup {
        final int hGap, vGap;
        int maxRows = Integer.MAX_VALUE;
        Flow(Context c, int h, int v) { super(c); hGap = h; vGap = v; }
        Flow rows(int n) { maxRows = n; return this; }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec), x = 0, y = 0, rowH = 0, row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    row++;
                    if (row >= maxRows) break;
                    x = 0; y += rowH + vGap; rowH = 0;
                }
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
            int hh = MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hSpec) : y + rowH;
            setMeasuredDimension(maxW, hh);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l, x = 0, y = 0, rowH = 0, row = 0;
            boolean over = false;
            boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                if (over) { c.layout(0, 0, 0, 0); continue; }
                int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    row++;
                    if (row >= maxRows) { over = true; c.layout(0, 0, 0, 0); continue; }
                    x = 0; y += rowH + vGap; rowH = 0;
                }
                if (rtl) c.layout(maxW - x - w, y, maxW - x, y + h); else c.layout(x, y, x + w, y + h);
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
        }
    }

    /** ScrollView с ограничением высоты (для списка тегов в просмотре). */
    static class MaxHScroll extends ScrollView {
        final int maxH;
        MaxHScroll(Context c, int maxH) { super(c); this.maxH = maxH; }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
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

    private static final int PICK_MEDIA = 101, PICK_ZIP = 102, PICK_PDF = 103, EXPORT_ZIP = 104, PICK_BG = 105, PICK_ICON = 106;

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
    private final ImageView[] tabBtns = new ImageView[4];
    private int curTab = 0;
    private int startTab = -1;

    private String albumOpen = null;
    private final LinkedHashSet<String> sel = new LinkedHashSet<>();
    private final LinkedHashSet<String> feedSel = new LinkedHashSet<>();
    private boolean favOnly = false;
    private LinearLayout feedHead, feedSelBar;
    private TextView feedSelCount;
    private ImageView bgView;
    private View bgDim;
    private FrameLayout lockView;
    private final LinkedHashSet<String> incTags = new LinkedHashSet<>(), excTags = new LinkedHashSet<>();
    private boolean matchAll = true;
    private int sortMode = 0, typeFilter = 0;
    private long shuffleSeed = 1;

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
        loadFonts();
        cols = Math.max(1, Math.min(4, prefs.getInt("cols", 2)));
        showNames = prefs.getBoolean("names", true);
        applyTheme();
        lib = openLib();
        applySecure();
        purgeTrash();
        cleanDecSync();
        IconPlan.applyAuto(this);
        IconPlan.schedule(this);
        if (b != null) {
            startTab = b.getInt("tab", -1);
            albumOpen = b.getString("album", null);
        } else {
            Intent in0 = getIntent();
            String act0 = in0 == null ? null : in0.getAction();
            if ("com.example.offlinemediavault.UPLOAD".equals(act0)) { startTab = 2; pendingPick = true; }
            else if ("com.example.offlinemediavault.FAVORITES".equals(act0)) { startTab = 1; albumOpen = Library.FAV; }
        }
        installCrashHook();
        buildAll();
        applyTaskDesc();
        if (hasPin() && !authed) showLock();
        else {
            if (b == null) {
                shell.setAlpha(0f);
                shell.setTranslationY(dp(18));
                shell.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
            if (pendingPick) { pendingPick = false; ui.post(this::pickMedia); }
        }
        ui.postDelayed(this::checkCrash, 700);
    }

    private static boolean crashHook = false;

    // Отчёт о сбое: сохраняется в файл и предлагается при следующем запуске
    private void installCrashHook() {
        if (crashHook) return;
        crashHook = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        final File cf = new File(getFilesDir(), "crash.txt");
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        final String head = "Offline Media Vault " + ver + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL;
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
            try (PrintWriter pw = new PrintWriter(new FileOutputStream(cf))) {
                pw.println(head);
                pw.println(new Date().toString());
                ex.printStackTrace(pw);
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(th, ex);
        });
    }

    private void checkCrash() {
        final File cf = new File(getFilesDir(), "crash.txt");
        if (!cf.exists() || isFinishing()) return;
        String txt;
        try { txt = Library.readText(cf); } catch (Exception e) { txt = ""; }
        cf.delete();
        if (txt.isEmpty()) return;
        final String report = txt;
        showDlg(dlg().setTitle(t("crash_title")).setMessage(t("crash_msg"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("crash_copy"), (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", report));
                    toast(t("crash_copied"));
                }));
    }

    private boolean pendingPick = false;
    private static boolean decoy = false;   // открыт «ложный» (пустой) архив по запасному PIN

    private Library openLib() {
        if (decoy) {
            File dd = new File(getFilesDir(), "decoy");
            dd.mkdirs();
            Library l = new Library(dd);
            l.load(null);
            return l;
        }
        Library l = new Library(getFilesDir());
        l.load(getSharedPreferences("library", 0).getString("items", null));
        return l;
    }

    private void switchVault(boolean fake) {
        if (fake == decoy) return;
        decoy = fake;
        lib = openLib();
        thumbCache.evictAll();
        sel.clear(); feedSel.clear(); albumSelA.clear(); albumSelI.clear();
        incTags.clear(); excTags.clear();
        favOnly = false;
        albumOpen = null;
        refreshAll();
    }

    // Запрет скриншотов и скрытие содержимого в списке недавних
    private void setSecure(boolean on) {
        Window w = getWindow();
        if (w != null) { if (on) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE); else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); }
        for (Dialog d : new ArrayList<>(openDialogs)) {
            try {
                Window dw = d.getWindow();
                if (dw == null) continue;
                if (on) dw.addFlags(WindowManager.LayoutParams.FLAG_SECURE); else dw.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Exception ignored) { }
        }
    }

    private void applySecure() { setSecure(prefs.getBoolean("no_shots", false)); }

    @Override protected void onPause() {
        super.onPause();
        if (prefs != null && prefs.getBoolean("hide_recents", false)) setSecure(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) applySecure();
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
        if (isFinishing()) cleanDecSync();
        for (Dialog d : new ArrayList<>(openDialogs)) { try { if (d.isShowing()) d.dismiss(); } catch (Exception ignored) { } }
    }

    @Override public void onBackPressed() {
        if (lockView != null && lockView.getVisibility() == View.VISIBLE) { moveTaskToBack(true); return; }
        if (curTab == 0 && !feedSel.isEmpty()) { feedSel.clear(); renderFeed(); return; }
        if (!sel.isEmpty()) { sel.clear(); renderUpload(); return; }
        if (curTab == 1 && (!albumSelA.isEmpty() || !albumSelI.isEmpty())) { albumSelCancel(); return; }
        if (curTab == 1 && albumOpen != null) { albumOpen = null; animGrid = true; renderAlbums(); return; }
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
        float[] hsv = new float[3];
        Color.colorToHSV(acc, hsv);
        hsv[0] = (hsv[0] + 32f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * 0.92f);
        hsv[2] = Math.min(1f, hsv[2] * 1.06f);
        ACCENT2 = Color.HSVToColor(hsv);
        // Рисуем под системными панелями: фон и «сияние» видны за статус-баром
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        View decor = getWindow().getDecorView();
        decor.setBackgroundColor(BG);
        int f = decor.getSystemUiVisibility();
        if (!darkTheme) f |= 0x2000 | 0x10; else f &= ~(0x2000 | 0x10);
        f |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decor.setSystemUiVisibility(f);
    }

    // ---------- Мелкие помощники ----------
    private String t(String k) { return Strings.get(lang, k); }
    private String tf(String k, Object... a) { return String.format(Locale.ROOT, Strings.get(lang, k), a); }
    private void toast(String s) { snack(s, null, null); }

    private LinearLayout snackView;
    private int snackToken = 0;

    private boolean dialogShowing() {
        for (Dialog d : openDialogs) { try { if (d.isShowing()) return true; } catch (Exception ignored) { } }
        return false;
    }

    // Всплывающая плашка снизу; с кнопкой действия (например «Отменить»)
    private void snack(String msg, String action, final Runnable onAction) {
        if (appRoot == null || shell == null || dialogShowing() || (lockView != null && lockView.getVisibility() == View.VISIBLE)) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            return;
        }
        if (snackView != null) appRoot.removeView(snackView);
        final LinearLayout sb = new LinearLayout(this);
        sb.setGravity(Gravity.CENTER_VERTICAL);
        sb.setPadding(dp(18), dp(12), dp(10), dp(12));
        sb.setBackground(round(darkTheme ? 0xFF2C2F38 : 0xFF23252C, 18));
        sb.setElevation(dp(12));
        TextView m = tv(msg, 14, 0xFFFFFFFF, false);
        m.setMaxLines(3);
        sb.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null && onAction != null) {
            TextView a = tv(action, 14, ACCENT, true);
            a.setPadding(dp(14), dp(6), dp(10), dp(6));
            a.setOnClickListener(v -> { buzz(v, HapticFeedbackConstants.KEYBOARD_TAP); hideSnack(sb); onAction.run(); });
            sb.addView(a, new LinearLayout.LayoutParams(-2, -2));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(12), navInset + shell.getPaddingBottom() + dp(74));
        appRoot.addView(sb, lp);
        snackView = sb;
        sb.setAlpha(0f);
        sb.setTranslationY(dp(40));
        sb.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
        final int my = ++snackToken;
        ui.postDelayed(() -> { if (my == snackToken) hideSnack(sb); }, action != null ? 4500 : 2600);
    }

    private void hideSnack(final View sb) {
        if (sb == null || sb.getParent() == null) return;
        sb.animate().alpha(0f).translationY(dp(30)).setDuration(180).withEndAction(() -> {
            if (appRoot != null) appRoot.removeView(sb);
            if (snackView == sb) snackView = null;
        }).start();
    }

    private void trashUndo(final ArrayList<Library.Item> targets) {
        snack(tf("moved_trash", targets.size()), t("undo"), () -> {
            for (Library.Item it : targets) lib.restore(it);
            lib.save();
            animGrid = true;
            refreshAll();
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams params(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    // ---------- Дизайн-система ----------
    private static Typeface TF_REG, TF_MED, TF_SEMI, TF_DISP;
    private static String fontLang = null;
    private int ACCENT2;
    private Aurora aurora;
    private FrameLayout barWrapRef;
    private int navInset = 0;

    // Шрифт Inter (латиница и кириллица); для фарси, китайского и японского — системный
    private void loadFonts() {
        if (lang.equals(fontLang)) return;
        fontLang = lang;
        TF_REG = TF_MED = TF_SEMI = TF_DISP = null;
        if ("fa".equals(lang) || "zh".equals(lang) || "ja".equals(lang)) return;
        try {
            TF_REG = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf");
            TF_MED = Typeface.createFromAsset(getAssets(), "fonts/Inter-Medium.ttf");
            TF_SEMI = Typeface.createFromAsset(getAssets(), "fonts/Inter-SemiBold.ttf");
            TF_DISP = Typeface.createFromAsset(getAssets(), "fonts/InterDisplay-Bold.ttf");
        } catch (Throwable e) {
            TF_REG = TF_MED = TF_SEMI = TF_DISP = null;
        }
    }

    private Typeface boldFace() { return TF_SEMI != null ? TF_SEMI : Typeface.DEFAULT_BOLD; }

    private Typeface dispFace() { return TF_DISP != null ? TF_DISP : Typeface.DEFAULT_BOLD; }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView v = glyphRes(text) != 0 ? new GlyphView(this) : new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(boldFace());
        else if (TF_REG != null) v.setTypeface(TF_REG);
        return v;
    }

    // Крупный заголовок экрана
    private TextView headline(String text, float size) {
        GradText g = new GradText(this, FG, blend(FG, ACCENT, 0.75f));
        g.setText(text);
        g.setTextSize(size);
        g.setTextColor(FG);
        g.setTypeface(dispFace());
        g.setLetterSpacing(-0.015f);
        g.setSingleLine(true);
        g.setEllipsize(TextUtils.TruncateAt.END);
        return g;
    }

    private GradientDrawable accentBg(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT2});
        g.setCornerRadius(dp(radius));
        return g;
    }

    // Карточка «стекло»: фон панели + тонкая светлая кромка
    private GradientDrawable panel(int radius) {
        GradientDrawable g = round(PANEL, radius);
        g.setStroke(Math.max(1, dp(1) / 2 + 1), (FG & 0x00FFFFFF) | (darkTheme ? 0x16000000 : 0x12000000));
        return g;
    }

    // Цветная тень (Android 9+)
    private void glow(View v, int color, int elev) {
        v.setElevation(dp(elev));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(color);
            v.setOutlineAmbientShadowColor(color);
        }
    }

    private Drawable icon(int res, int tint) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(tint);
        return d;
    }

    private void startIcon(TextView v, int res, int tint) {
        Drawable d = icon(res, tint);
        d.setBounds(0, 0, dp(18), dp(18));
        v.setCompoundDrawablesRelative(d, null, null, null);
        v.setCompoundDrawablePadding(dp(6));
    }

    private TextView button(String text) {
        TextView b = tv(text, 14, FG, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(BTN, 14));
        b.setPadding(dp(8), 0, dp(8), 0);
        pressFx(b);
        return b;
    }

    private TextView primaryButton(String text) {
        TextView b = button(text);
        b.setTextColor(ONACC);
        b.setBackground(accentBg(14));
        glow(b, ACCENT, 4);
        return b;
    }

    private TextView chip(String text, boolean on) { return chip(text, on, ACCENT); }

    // Переключатель-«тумблер»
    private TextView toggleChip(boolean on) {
        TextView c = tv("", 1, 0x00000000, false);
        GradientDrawable track = on ? accentBg(16) : round(blend(PANEL, FG, 0.2f), 16);
        GradientDrawable knob = new GradientDrawable();
        knob.setShape(GradientDrawable.OVAL);
        knob.setColor(0xFFFFFFFF);
        if (!on) knob.setStroke(1, 0x22000000);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{track, knob});
        ld.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        ld.setLayerSize(1, dp(24), dp(24));
        ld.setLayerGravity(1, (on ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        c.setBackground(ld);
        c.setMinWidth(dp(52));
        c.setMinHeight(dp(30));
        c.setMinimumWidth(dp(52));
        c.setMinimumHeight(dp(30));
        if (on) glow(c, ACCENT, 2);
        pressFx(c);
        return c;
    }

    private TextView chip(String text, boolean on, int onColor) {
        TextView c = tv(text, 13, on ? (onColor == RED ? 0xFFFFFFFF : ONACC) : FG, false);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setBackground(on && onColor == ACCENT ? accentBg(18) : round(on ? onColor : FIELD, 18));
        if (on && TF_MED != null) c.setTypeface(TF_MED);
        pressFx(c);
        return c;
    }

    // Цветные «таблетки»-теги: цвет стабильно выбирается по тексту тега
    private static final int TAG_GREEN = 0xFF2E9E5B;

    private int pillColor(String tag) { return TAG_GREEN; }

    // ---------- Анимации ----------
    private boolean animGrid = false;
    private String popId = null;

    // Лёгкое «нажатие»: кнопка слегка уменьшается и пружинит обратно
    private void buzz(View v, int kind) {
        try { if (v != null) v.performHapticFeedback(kind); } catch (Throwable ignored) { }
    }

    // Ripple-волна поверх кнопки (форма берётся из фона)
    private void addRipple(View v) {
        if (v.getForeground() != null) return;
        Drawable bg = v.getBackground();
        Drawable mask = bg != null && bg.getConstantState() != null ? bg.getConstantState().newDrawable().mutate() : null;
        v.setForeground(new RippleDrawable(ColorStateList.valueOf((FG & 0x00FFFFFF) | 0x2E000000), null, mask));
    }

    private void pressFx(View v) {
        try { addRipple(v); } catch (Throwable ignored) { }
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                x.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
            return false;
        });
    }

    // Плавное появление / скрытие вида
    private void fadeVis(final View v, boolean show) {
        if (v == null) return;
        if (show) {
            if (v.getVisibility() == View.VISIBLE && v.getAlpha() >= 1f) return;
            v.animate().cancel();
            if (v.getVisibility() != View.VISIBLE) { v.setAlpha(0f); v.setTranslationY(-dp(6)); v.setVisibility(View.VISIBLE); }
            v.animate().alpha(1f).translationY(0f).setDuration(220).start();
        } else {
            if (v.getVisibility() != View.VISIBLE) return;
            v.animate().cancel();
            v.animate().alpha(0f).setDuration(150).withEndAction(() -> { v.setVisibility(View.GONE); v.setAlpha(1f); v.setTranslationY(0f); }).start();
        }
    }

    private void animWin(Dialog d, int style) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setWindowAnimations(style);
        if (prefs.getBoolean("no_shots", false)) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31 && (style == R.style.OmvPopAnim || style == R.style.OmvSheetAnim)) {
            try {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(dp(22));
                lp.dimAmount = 0.32f;
                w.setAttributes(lp);
            } catch (Throwable ignored) { }
        }
    }

    // Применяет эффект нажатия ко всем кликабельным видам внутри
    private void fxTree(View v) {
        if (v == null) return;
        if (v.hasOnClickListeners()) pressFx(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) fxTree(g.getChildAt(i));
        }
    }

    /** Пункт нижнего меню. */
    static class Act {
        final String icon, label, desc;
        final int color;   // 0 = акцентный
        final Runnable run;
        Act(String icon, String label, String desc, int color, Runnable run) {
            this.icon = icon; this.label = label; this.desc = desc; this.color = color; this.run = run;
        }
    }

    // Красивое нижнее меню со скруглёнными карточками
    private void sheet(String title, String sub, final Act... acts) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout sh = new LinearLayout(this);
        sh.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(BG);
        float rr = dp(28);
        sbg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        sh.setBackground(sbg);
        sh.setPadding(dp(16), dp(10), dp(16), dp(14));
        sh.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(16), dp(10), dp(16), dp(14) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        View handle = new View(this);
        handle.setBackground(round(blend(BG, FG, 0.25f), 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(12);
        sh.addView(handle, hp);
        TextView ttl = tv(title, 19, FG, true);
        ttl.setMaxLines(2);
        ttl.setEllipsize(TextUtils.TruncateAt.END);
        sh.addView(ttl, new LinearLayout.LayoutParams(-1, -2));
        if (sub != null && !sub.isEmpty()) {
            TextView st = tv(sub, 13, MUTED, false);
            st.setPadding(0, dp(4), 0, 0);
            sh.addView(st, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, 0);
        sv.addView(list);
        for (final Act a : acts) {
            int col = a.color == 0 ? ACCENT : a.color;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(round(a.color == 0 ? PANEL : blend(PANEL, a.color, 0.16f), 18));
            TextView ic = tv(a.icon, 17, 0xFFFFFFFF, true);
            ic.setGravity(Gravity.CENTER);
            GradientDrawable og = new GradientDrawable();
            og.setShape(GradientDrawable.OVAL);
            if (a.color == 0) { og.setColors(new int[]{ACCENT, ACCENT2}); og.setOrientation(GradientDrawable.Orientation.TL_BR); }
            else og.setColor(col);
            ic.setBackground(og);
            row.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(12), 0, dp(4), 0);
            TextView lb = tv(a.label, 15, a.color == RED ? RED : FG, true);
            lb.setMaxLines(2);
            lb.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(lb);
            if (a.desc != null && !a.desc.isEmpty()) tx.addView(tv(a.desc, 12, MUTED, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> { d.dismiss(); if (a.run != null) a.run.run(); });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(8);
            list.addView(row, rp);
        }
        sh.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        TextView cancel = button(t("cancel"));
        cancel.setBackground(round(BTN, 24));
        cancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(12);
        sh.addView(cancel, cp);
        fxTree(sh);
        d.setContentView(sh);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        animWin(d, R.style.OmvSheetAnim);
        d.setCanceledOnTouchOutside(true);
        openDialogs.add(d);
        d.show();
    }

    // Иконка приложения: 15 activity-alias (классика, 4 сезона, 10 праздников), включён ровно один
    private int curIcon() { return IconPlan.cur(this); }

    private void setIcon(int n, boolean quiet) {
        if (IconPlan.set(this, n) && !quiet) toast(t("icon_changed"));
    }

    private static final int[] ICON_PREV = {R.drawable.icon_prev_1, R.drawable.icon_prev_2, R.drawable.icon_prev_3, R.drawable.icon_prev_4, R.drawable.icon_prev_5,
            R.drawable.icon_prev_6, R.drawable.icon_prev_7, R.drawable.icon_prev_8, R.drawable.icon_prev_9, R.drawable.icon_prev_10,
            R.drawable.icon_prev_11, R.drawable.icon_prev_12, R.drawable.icon_prev_13, R.drawable.icon_prev_14, R.drawable.icon_prev_15};

    private TextView tagPill(String tag, boolean small) {
        TextView p = tv(tag, small ? 10.5f : 13, 0xFFFFFFFF, false);
        p.setSingleLine(true);
        p.setEllipsize(TextUtils.TruncateAt.END);
        p.setPadding(dp(small ? 8 : 13), dp(small ? 3 : 7), dp(small ? 8 : 13), dp(small ? 3 : 7));
        p.setBackground(round(pillColor(tag), 20));
        return p;
    }

    private LinearLayout segmented(String[] labels, int selected, final IntCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setBackground(round(FIELD, 24));
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView b = tv(labels[i], 13, i == selected ? ONACC : FG, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(8), dp(10), dp(8), dp(10));
            if (i == selected) b.setBackground(accentBg(20));
            b.setOnClickListener(v -> cb.on(k));
            row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return row;
    }

    private void styleEdit(EditText e) {
        e.setTextColor(FG);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(FIELD, 14));
        if (TF_REG != null) e.setTypeface(TF_REG);
    }

    private View sectionLabel(String emoji, String s) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(20), 0, dp(10));
        TextView ic = tv(emoji, 14, FG, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(blend(BG, ACCENT, darkTheme ? 0.24f : 0.16f), 9));
        row.addView(ic, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView l = tv(s, 12, MUTED, true);
        l.setLetterSpacing(0.08f);
        l.setPadding(dp(10), 0, 0, 0);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        row.setTag("sec");
        return row;
    }

    private TextView sectionLabel(String s) {
        TextView l = tv(s, 12, MUTED, true);
        l.setPadding(dp(6), dp(16), 0, dp(8));
        l.setTag("sec");
        return l;
    }

    private AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, darkTheme ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private void styleDlgBtn(Button bt, boolean primary, boolean danger) {
        if (bt == null) return;
        bt.setAllCaps(false);
        bt.setTextSize(14.5f);
        bt.setTypeface(boldFace());
        bt.setMinHeight(0);
        bt.setMinWidth(dp(72));
        bt.setPadding(dp(18), dp(11), dp(18), dp(11));
        bt.setTextColor(primary || danger ? (danger ? 0xFFFFFFFF : ONACC) : FG);
        bt.setBackground(danger ? round(RED, 14) : primary ? accentBg(14) : round(BTN, 14));
    }

    // Единый красивый вид для всех диалогов
    private void styleDialog(AlertDialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadius(dp(26));
        w.setBackgroundDrawable(new InsetDrawable(g, dp(6)));
        View dv = w.getDecorView();
        int tid = getResources().getIdentifier("alertTitle", "id", "android");
        View ttl = tid != 0 ? dv.findViewById(tid) : null;
        if (ttl instanceof TextView) { TextView x = (TextView) ttl; x.setTextColor(FG); x.setTextSize(19); x.setTypeface(dispFace()); }
        View msg = dv.findViewById(android.R.id.message);
        if (msg instanceof TextView) { TextView x = (TextView) msg; if (TF_REG != null) x.setTypeface(TF_REG); x.setTextColor(blend(FG, PANEL, 0.2f)); x.setTextSize(15); x.setLineSpacing(0, 1.12f); }
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        boolean danger = false;
        if (pos != null) {
            String pt = String.valueOf(pos.getText());
            danger = pt.equals(t("del_forever")) || pt.equals(t("empty_trash")) || pt.equals(t("yes")) || pt.equals(t("reset")) || pt.equals(t("delete"));
        }
        styleDlgBtn(pos, true, danger);
        styleDlgBtn(d.getButton(AlertDialog.BUTTON_NEGATIVE), false, false);
        styleDlgBtn(d.getButton(AlertDialog.BUTTON_NEUTRAL), false, false);
    }

    private AlertDialog showDlg(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        animWin(d, R.style.OmvPopAnim);
        d.show();
        styleDialog(d);
        if (d.getWindow() != null) fxTree(d.getWindow().getDecorView());
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
        shell.setBackgroundColor(0x00000000);
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            int sys = insets.getSystemWindowInsetBottom();
            navInset = Math.min(insets.getStableInsetBottom(), sys);
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), sys - navInset);
            if (barWrapRef != null) {
                barWrapRef.setPadding(0, 0, 0, navInset);
                ViewGroup.LayoutParams lp = barWrapRef.getLayoutParams();
                if (lp != null && lp.height != dp(62) + navInset) { lp.height = dp(62) + navInset; barWrapRef.setLayoutParams(lp); }
            }
            return insets;
        });
        FrameLayout body = new FrameLayout(this);
        for (int i = 0; i < 4; i++) {
            tabViews[i].setBackgroundColor(0x00000000);
            tabViews[i].setVisibility(View.GONE);
            body.addView(tabViews[i], new FrameLayout.LayoutParams(-1, -1));
        }
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        String[] names = {t("tab_feed"), t("tab_albums"), t("tab_upload"), t("tab_settings")};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            FrameLayout cellT = new FrameLayout(this);
            ImageView b = new ImageView(this);
            b.setImageResource(NAV_ICONS[i]);
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            b.setPadding(dp(18), dp(7), dp(18), dp(7));
            b.setContentDescription(names[i]);
            cellT.setOnClickListener(v -> { buzz(v, HapticFeedbackConstants.KEYBOARD_TAP); if (curTab == k) scrollTop(k); else showTab(k); });
            pressFx(b);
            b.setClickable(false);
            tabBtns[i] = b;
            cellT.addView(b, new FrameLayout.LayoutParams(dp(68), dp(40), Gravity.CENTER));
            bar.addView(cellT, new LinearLayout.LayoutParams(0, -1, 1));
        }
        FrameLayout barWrap = new FrameLayout(this);
        GradientDrawable nbg = new GradientDrawable();
        nbg.setColor(hasBg() ? ((PANEL & 0x00FFFFFF) | 0xE0000000) : PANEL);
        float nr = dp(24);
        nbg.setCornerRadii(new float[]{nr, nr, nr, nr, 0, 0, 0, 0});
        barWrap.setBackground(nbg);
        barWrap.setElevation(dp(10));
        navInd = new View(this);
        navInd.setBackground(round(blend(PANEL, ACCENT, 0.24f), 20));
        navInd.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{blend(PANEL, ACCENT, 0.28f), blend(PANEL, ACCENT2, 0.28f)}));
        ((GradientDrawable) navInd.getBackground()).setCornerRadius(dp(20));
        FrameLayout barInner = new FrameLayout(this);
        barInner.addView(navInd, new FrameLayout.LayoutParams(dp(68), dp(40), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        barInner.addView(bar, new FrameLayout.LayoutParams(-1, -1));
        barWrap.addView(barInner, new FrameLayout.LayoutParams(-1, dp(62), Gravity.TOP));
        barWrapRef = barWrap;
        shell.addView(barWrap, new LinearLayout.LayoutParams(-1, dp(62)));
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(BG);
        aurora = new Aurora(this, ACCENT, ACCENT2, darkTheme);
        aurora.setScaleX(1.25f);
        aurora.setScaleY(1.1f);
        aurora.setVisibility(hasBg() ? View.GONE : View.VISIBLE);
        appRoot.addView(aurora, new FrameLayout.LayoutParams(-1, -1));
        bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appRoot.addView(bgView, new FrameLayout.LayoutParams(-1, -1));
        bgDim = new View(this);
        appRoot.addView(bgDim, new FrameLayout.LayoutParams(-1, -1));
        applyBg();
        appRoot.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        buildLock();
        appRoot.addView(lockView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appRoot);
        showTab(startTab >= 0 && startTab < 4 ? startTab : (lib.active().isEmpty() ? 2 : 0));
    }

    private View navInd;

    private void moveNavInd(boolean anim) {
        if (navInd == null) return;
        final View wrap = (View) navInd.getParent();   // barInner
        int w = wrap.getWidth();
        if (w == 0) { wrap.post(() -> moveNavInd(false)); return; }
        int pos = wrap.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 3 - curTab : curTab;
        float x = w / 4f * pos + w / 8f - dp(34);
        navInd.animate().cancel();
        if (anim) navInd.animate().translationX(x).setDuration(360).setInterpolator(new OvershootInterpolator(1.3f)).start();
        else navInd.setTranslationX(x);
    }

    private void scrollTop(int tab) {
        View v = tab == 0 ? feedScroll : tab == 1 ? (albumOpen != null ? albumDetailScroll : albumListScroll) : tabViews[tab];
        if (v instanceof ScrollView) ((ScrollView) v).smoothScrollTo(0, 0);
    }

    private static final int[] NAV_ICONS = {R.drawable.nav_home, R.drawable.nav_gallery, R.drawable.nav_upload, R.drawable.nav_settings};

    private void showTab(int tab) { showTab(tab, 0); }

    private float swX, swY;
    private long swT;
    private boolean swOk = false, tileSwiped = false;

    // Свайп влево/вправо переключает вкладки
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            swX = e.getRawX(); swY = e.getRawY(); swT = e.getEventTime();
            swOk = lockView == null || lockView.getVisibility() != View.VISIBLE;
            tileSwiped = false;
        } else if (a == MotionEvent.ACTION_POINTER_DOWN) swOk = false;
        boolean r = super.dispatchTouchEvent(e);
        if (a == MotionEvent.ACTION_UP && swOk && !tileSwiped) {
            float dx = e.getRawX() - swX, dy = e.getRawY() - swY;
            if (Math.abs(dx) > dp(100) && Math.abs(dx) > Math.abs(dy) * 2.2f && e.getEventTime() - swT < 700) {
                int to = curTab + (dx < 0 ? 1 : -1);
                if (to >= 0 && to < 4) showTab(to, dx < 0 ? 1 : -1);
            }
        }
        return r;
    }

    private void showTab(int tab, int dir) {
        boolean changed = curTab != tab || !visible(tabViews[tab]);
        boolean first = !visible(tabViews[0]) && !visible(tabViews[1]) && !visible(tabViews[2]) && !visible(tabViews[3]);
        curTab = tab;
        animGrid = true;
        for (int i = 0; i < 4; i++) {
            tabViews[i].animate().cancel();
            tabViews[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            tabViews[i].setAlpha(1f);
            tabViews[i].setTranslationY(0f);
            tabViews[i].setTranslationX(0f);
            boolean on = i == tab;
            tabBtns[i].setColorFilter(on ? ACCENT : MUTED);
            if (on && changed && !first) {
                final View ib = tabBtns[i];
                ib.animate().cancel();
                ib.animate().scaleX(1.22f).scaleY(1.22f).setDuration(110).withEndAction(() ->
                        ib.animate().scaleX(1.06f).scaleY(1.06f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start()).start();
            } else tabBtns[i].animate().scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(200).start();
        }
        if (changed && !first) {
            View v = tabViews[tab];
            v.setAlpha(0f);
            if (dir != 0) {
                v.setTranslationX(dir * dp(70));
                v.animate().alpha(1f).translationX(0f).setDuration(260).start();
            } else {
                v.setTranslationY(dp(14));
                v.animate().alpha(1f).translationY(0f).setDuration(260).start();
            }
        }
        moveNavInd(changed && !first);
        if (aurora != null) {
            float ax = (1.5f - tab) * dp(22);
            if (changed && !first) aurora.animate().translationX(ax).setDuration(700).setInterpolator(new DecelerateInterpolator(1.6f)).start();
            else aurora.setTranslationX(ax);
        }
        if (changed && !first) buzz(appRoot, HapticFeedbackConstants.KEYBOARD_TAP);
        if (tab == 0) renderFeed(); else if (tab == 1) renderAlbums(); else if (tab == 2) renderUpload(); else renderSettings();
    }

    private final Bitmap[] bgThumbs = new Bitmap[21];

    private File bgFile() { return new File(getFilesDir(), "bg.jpg"); }

    private boolean hasBg() { return bgFile().exists() || prefs.getInt("bgres", 0) > 0; }

    private Bitmap decodeBgRes(int n, int sample) {
        int id = getResources().getIdentifier("bg_" + n, "drawable", getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeResource(getResources(), id, o);
    }

    private void applyBg() {
        if (bgView == null) return;
        final File f = bgFile();
        final int res = prefs.getInt("bgres", 0);
        if (res <= 0 && !f.exists()) {
            bgView.setImageDrawable(null);
            bgView.setVisibility(View.GONE);
            bgDim.setVisibility(View.GONE);
            return;
        }
        int pct = prefs.getInt("bgdim", 55);
        bgDim.setBackgroundColor((BG & 0x00FFFFFF) | (((int) (pct * 2.55f)) << 24));
        bgView.setVisibility(View.VISIBLE);
        bgDim.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            final Bitmap b = res > 0 ? decodeBgRes(res, 1) : decodeSampled(f.getAbsolutePath(), 1600);
            ui.post(() -> { if (b != null && bgView != null) bgView.setImageBitmap(b); });
        });
    }

    private void pickBg() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_BG);
    }

    private void setBgFrom(final Uri u) {
        io.execute(() -> {
            File raw = new File(getCacheDir(), "bg_raw");
            boolean ok = false;
            try (InputStream in = getContentResolver().openInputStream(u); OutputStream os = new FileOutputStream(raw)) {
                if (in != null) { pump(in, os); ok = true; }
            } catch (Exception ignored) { }
            if (ok) {
                Bitmap b = decodeSampled(raw.getAbsolutePath(), 1600);
                if (b != null) {
                    try (OutputStream os = new FileOutputStream(bgFile())) { b.compress(Bitmap.CompressFormat.JPEG, 88, os); } catch (Exception ignored) { }
                }
            }
            raw.delete();
            ui.post(() -> { prefs.edit().remove("bgres").apply(); toast(t("bg_set")); recreate(); });
        });
    }

    private void refreshAll() { renderFeed(); renderAlbums(); renderUpload(); }

    private boolean visible(View v) { return v != null && v.getVisibility() == View.VISIBLE; }

    // ---------- Миниатюры ----------
    private FrameLayout makeThumb(Library.Item it) {
        boolean vid = Library.isVideo(it.name), pdf = Library.isPdf(it.name);
        FrameLayout fl = new FrameLayout(this);
        fl.setBackground(round(FIELD, 16));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
        });
        iv.setClipToOutline(true);
        fl.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        TextView label = (vid || pdf) ? new TextView(this) : null;
        if (vid || pdf) {   // мягкое затемнение снизу под подписью
            View scrim = new View(this);
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x73000000, 0x00000000});
            sg.setCornerRadii(new float[]{0, 0, 0, 0, dp(16), dp(16), dp(16), dp(16)});
            scrim.setBackground(sg);
            fl.addView(scrim, new FrameLayout.LayoutParams(-1, dp(44), Gravity.BOTTOM));
        }
        if (vid) {
            TextView play = tv("▶", 16, 0xFFFFFFFF, false);
            play.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x66000000);
            g.setStroke(dp(1), 0x66FFFFFF);
            play.setBackground(g);
            fl.addView(play, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        }
        if (label != null) {
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(10);
            label.setTypeface(boldFace());
            label.setPadding(dp(6), dp(2), dp(6), dp(2));
            label.setBackground(round(0x66000000, 8));
            if (pdf) label.setText("PDF"); else label.setVisibility(View.GONE);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, dp(4), dp(4));
            fl.addView(label, dlp);
        }
        if (it.enc) {
            TextView lk = tv("🔒", 10, 0xFFFFFFFF, false);
            lk.setGravity(Gravity.CENTER);
            lk.setBackground(round(0x80000000, 10));
            FrameLayout.LayoutParams lkp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM | Gravity.START);
            lkp.setMargins(dp(4), 0, 0, dp(4));
            fl.addView(lk, lkp);
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
            ui.post(() -> {
                if (!it.path.equals(iv.getTag())) return;
                applyThumb(it, th, iv, label);
                iv.setAlpha(0f);
                iv.setScaleX(1.04f);
                iv.setScaleY(1.04f);
                iv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
            });
        });
    }

    private void applyThumb(Library.Item it, Thumb th, ImageView iv, TextView label) {
        if (th.bmp != null) iv.setImageBitmap(th.bmp);
        if (label == null) return;
        if (Library.isPdf(it.name)) { if (th.pages > 0) label.setText("PDF · " + th.pages); }
        else if (th.dur > 0) { label.setText(fmtDur(th.dur)); label.setVisibility(View.VISIBLE); }
    }

    private File thumbFile(Library.Item it) {
        File d = new File(getFilesDir(), "thumbs");
        d.mkdirs();
        return new File(d, it.id + ".t");
    }

    private void saveEncThumb(Library.Item it, Thumb th) {
        if (th == null || th.bmp == null) return;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            th.bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo);
            try (OutputStream os = new FileOutputStream(thumbFile(it))) {
                Crypto.encrypt(this, new java.io.ByteArrayInputStream(bo.toByteArray()), os);
            }
        } catch (Throwable ignored) { }
    }

    private Thumb makeEncThumb(Library.Item it) {
        Thumb th = new Thumb();
        try {
            File tf = thumbFile(it);
            if (tf.exists()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                Crypto.decrypt(this, tf, bo);
                th.bmp = BitmapFactory.decodeByteArray(bo.toByteArray(), 0, bo.size());
                if (th.bmp != null) return th;
            }
            File pf = plainFile(it);
            if (pf != null) { th = thumbFromFile(it, pf); saveEncThumb(it, th); }
        } catch (Throwable ignored) { }
        return th;
    }

    private Thumb makeThumbData(Library.Item it) {
        return it.enc ? makeEncThumb(it) : thumbFromFile(it, new File(it.path));
    }

    private Thumb thumbFromFile(Library.Item it, File pf) {
        Thumb th = new Thumb();
        try {
            if (Library.isVideo(it.name)) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(pf.getAbsolutePath());
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
                    pfd = ParcelFileDescriptor.open(pf, ParcelFileDescriptor.MODE_READ_ONLY);
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
            } else th.bmp = decodeSampled(pf.getAbsolutePath(), 640);
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
    private int tagBodyH(int lines) { return lines <= 0 ? 0 : lines * dp(22) + (lines - 1) * dp(4); }

    // Нажатие-пружинка + свайп вправо по плитке открывает быстрые теги
    private void cellTouch(final View cell, final Library.Item it, final boolean allowSwipe) {
        final float[] st = new float[2];
        final boolean[] track = {false};
        cell.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                st[0] = e.getRawX(); st[1] = e.getRawY(); track[0] = true;
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
                if (a == MotionEvent.ACTION_UP && track[0] && allowSwipe) {
                    float dx = e.getRawX() - st[0], dy = e.getRawY() - st[1];
                    if (dx > dp(90) && dx > Math.abs(dy) * 2.2f) {
                        tileSwiped = true;
                        track[0] = false;
                        buzz(x, HapticFeedbackConstants.KEYBOARD_TAP);
                        x.cancelLongPress();
                        x.setPressed(false);
                        quickTags(it);
                        return true;
                    }
                }
                track[0] = false;
            }
            return false;
        });
    }

    private void quickTags(final Library.Item it) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout sh = new LinearLayout(this);
        sh.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(BG);
        float rr = dp(28);
        sbg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        sh.setBackground(sbg);
        sh.setPadding(dp(16), dp(10), dp(16), dp(14));
        sh.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(16), dp(10), dp(16), dp(14) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        View handle = new View(this);
        handle.setBackground(round(blend(BG, FG, 0.25f), 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(12);
        sh.addView(handle, hp);
        TextView ttl = tv(t("quick_tags"), 19, FG, true);
        sh.addView(ttl, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = tv(it.name, 13, MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(3), 0, dp(10));
        sh.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        final Flow fl = new Flow(this, dp(8), dp(8));
        MaxHScroll sv = new MaxHScroll(this, (int) (getResources().getDisplayMetrics().heightPixels * 0.4f));
        sv.addView(fl, new ViewGroup.LayoutParams(-1, -2));
        sh.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            fl.removeAllViews();
            HashMap<String, Integer> freq = new HashMap<>();
            for (Library.Item o : lib.active()) for (String tg : Library.tagsOf(o)) { Integer c0 = freq.get(tg); freq.put(tg, c0 == null ? 1 : c0 + 1); }
            final List<String> mine = Library.tagsOf(it);
            ArrayList<String> all = new ArrayList<>(freq.keySet());
            Collections.sort(all, (x, y) -> freq.get(y) - freq.get(x));
            ArrayList<String> show = new ArrayList<>(mine);
            for (String tg : all) { if (show.size() >= 24) break; if (!show.contains(tg)) show.add(tg); }
            for (final String tg : show) {
                final boolean on = mine.contains(tg);
                TextView c = tv(tg, 13.5f, on ? 0xFFFFFFFF : FG, on);
                c.setPadding(dp(14), dp(8), dp(14), dp(8));
                c.setBackground(round(on ? TAG_GREEN : FIELD, 18));
                c.setOnClickListener(v -> {
                    List<String> cur = new ArrayList<>(Library.tagsOf(it));
                    if (!cur.remove(tg)) cur.add(tg);
                    it.tags = Library.joinTags(cur);
                    lib.save();
                    draw[0].run();
                });
                fl.addView(c);
            }
            if (show.isEmpty()) fl.addView(tv(t("tap_add_tags"), 13, MUTED, false));
            fxTree(fl);
        };
        draw[0].run();
        LinearLayout row = new LinearLayout(this);
        TextView custom = button(t("quick_custom")), auto = button(t("quick_auto"));
        custom.setOnClickListener(v -> promptText(t("act_tags"), "", "", txt -> {
            if (txt.isEmpty()) return;
            it.tags = Library.mergeTags(it.tags, txt);
            lib.save();
            draw[0].run();
        }));
        auto.setOnClickListener(v -> {
            toast(t("autotag_busy"));
            io.execute(() -> {
                ArrayList<Library.Item> one = new ArrayList<>();
                one.add(it);
                final int n = autoTagList(one, false);
                lib.save();
                ui.post(() -> { draw[0].run(); if (n == 0) toast(t("autotag_none")); });
            });
        });
        row.addView(custom, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(0, dp(46), 1);
        arp.setMarginStart(dp(8));
        row.addView(auto, arp);
        LinearLayout.LayoutParams rowp = new LinearLayout.LayoutParams(-1, -2);
        rowp.topMargin = dp(14);
        sh.addView(row, rowp);
        TextView done = primaryButton(t("ok"));
        done.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams dp0 = new LinearLayout.LayoutParams(-1, dp(48));
        dp0.topMargin = dp(10);
        sh.addView(done, dp0);
        fxTree(sh);
        d.setContentView(sh);
        d.setOnDismissListener(x -> refreshAll());
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        animWin(d, R.style.OmvSheetAnim);
        d.setCanceledOnTouchOutside(true);
        openDialogs.add(d);
        d.show();
    }

    // ---------- Автотеги (ML Kit) ----------
    private Personal personal;

    private Personal personal() {
        if (personal == null) personal = new Personal(getFilesDir());
        return personal;
    }

    private File decDir() {
        File d = new File(getCacheDir(), "dec");
        d.mkdirs();
        return d;
    }

    private File plainCached(Library.Item it) {
        if (!it.enc) return new File(it.path);
        File d = new File(decDir(), it.id + "_" + it.file);
        return d.exists() && d.length() > 0 ? d : null;
    }

    /** Файл в открытом виде (для зашифрованных — расшифрованная копия в кэше). Блокирующий вызов. */
    private File plainFile(Library.Item it) {
        if (!it.enc) return new File(it.path);
        File d = new File(decDir(), it.id + "_" + it.file);
        if (d.exists() && d.length() > 0) return d;
        File tmp = new File(d.getPath() + ".part");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp), 65536)) {
            Crypto.decrypt(this, new File(it.path), os);
        } catch (Throwable e) {
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(d)) { tmp.delete(); return null; }
        return d;
    }

    private void cleanDecSync() {
        File[] fs = decDir().listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private void cleanDec() { io.execute(this::cleanDecSync); }

    private int autoTagList(List<Library.Item> list, boolean progress) { return autoTagList(list, progress, true); }

    /** Блокирующий вызов: только из фонового потока. assign=false — только запомнить вектор признаков. */
    private int autoTagList(List<Library.Item> list, boolean progress, boolean assign) {
        String tl = prefs.getBoolean("tag_local", false) && "ru".equals(lang) ? "ru" : "en";
        AutoTagger tg = new AutoTagger(tl);
        Personal ps = personal();
        HashMap<String, List<String>> tagMap = new HashMap<>();
        HashSet<String> live = new HashSet<>();
        for (Library.Item o : lib.active()) { live.add(o.id); tagMap.put(o.id, Library.tagsOf(o)); }
        ps.prune(live);
        int n = 0, i = 0;
        try {
            for (Library.Item it : list) {
                i++;
                if (Library.isPdf(it.name)) continue;
                File pf = plainFile(it);
                if (pf == null) continue;
                Bitmap b = Library.isVideo(it.name) ? AutoTagger.videoFrame(pf.getAbsolutePath()) : decodeSampled(pf.getAbsolutePath(), 640);
                AutoTagger.Result r = tg.analyze(b);
                if (!r.vec.isEmpty()) ps.put(it.id, r.vec);
                if (assign) {
                    ArrayList<String> tags = new ArrayList<>(r.tags);
                    for (String x : ps.suggest(it.id, r.vec, tagMap)) if (!tags.contains(x)) tags.add(x);
                    if (!tags.isEmpty()) {
                        it.tags = Library.mergeTags(it.tags, Library.joinTags(tags));
                        tagMap.put(it.id, Library.tagsOf(it));
                        n++;
                    }
                }
                if (progress) setBusy(it.name + "  (" + i + "/" + list.size() + ")", i);
            }
        } finally {
            tg.close();
            ps.save();
        }
        return n;
    }

    private void autoTagUntagged() {
        final ArrayList<Library.Item> todo = new ArrayList<>(), learn = new ArrayList<>();
        for (Library.Item it : lib.active()) {
            if (Library.isPdf(it.name)) continue;
            if (Library.tagsOf(it).isEmpty()) todo.add(it);
            else if (!personal().has(it.id)) learn.add(it);   // размеченные вами файлы: учимся на них
        }
        if (todo.isEmpty() && learn.isEmpty()) { toast(t("autotag_none")); return; }
        showBusy(t("autotag_busy"), 0);
        io.execute(() -> {
            if (!learn.isEmpty()) autoTagList(learn, true, false);
            final int n = todo.isEmpty() ? 0 : autoTagList(todo, true);
            lib.save();
            ui.post(() -> { hideBusy(); refreshAll(); toast(tf("autotag_done", n)); });
        });
    }

    // ---------- Шифрование файлов ----------
    /** Блокирующий вызов. enc=true — зашифровать, false — расшифровать. Возвращает число обработанных файлов. */
    private int cryptItems(List<Library.Item> list, boolean enc, boolean progress) {
        int ok = 0, i = 0;
        for (Library.Item it : list) {
            i++;
            if (it.enc == enc) continue;
            File src = new File(it.path), tmp = new File(it.path + ".tmp");
            try {
                if (enc) {
                    Thumb th = makeThumbData(it);
                    if (it.sha.isEmpty()) { String h = Library.sha256(src); it.sha = h == null ? "" : h; }
                    it.size = src.length();
                    try (InputStream in = new FileInputStream(src); OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp), 65536)) {
                        Crypto.encrypt(this, in, os);
                    }
                    if (!Crypto.verify(this, tmp)) throw new IOException("verify");
                    if (!tmp.renameTo(src)) throw new IOException("rename");
                    it.enc = true;
                    saveEncThumb(it, th);
                } else {
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp), 65536)) {
                        Crypto.decrypt(this, src, os);
                    }
                    if (!tmp.renameTo(src)) throw new IOException("rename");
                    it.enc = false;
                    thumbFile(it).delete();
                }
                ok++;
            } catch (Throwable e) {
                tmp.delete();
            }
            if (progress) setBusy(it.name + "  (" + i + "/" + list.size() + ")", i);
            if (i % 10 == 0) lib.save();
        }
        lib.save();
        return ok;
    }

    private void startCrypt(final boolean enc) {
        final ArrayList<Library.Item> todo = new ArrayList<>();
        synchronized (lib) { for (Library.Item it : lib.items) if (it.enc != enc) todo.add(it); }
        if (todo.isEmpty()) { toast(t("enc_nothing")); return; }
        showDlg(dlg().setMessage(t(enc ? "enc_confirm_all" : "enc_confirm_dec")).setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("ok"), (d, w) -> {
                    showBusy(t("enc_busy"), todo.size());
                    io.execute(() -> {
                        final int n = cryptItems(todo, enc, true);
                        ui.post(() -> { hideBusy(); refreshAll(); toast(tf("enc_done", n)); });
                    });
                }));
    }

    // ---------- Название и иконка приложения ----------
    private String appName() {
        String n = prefs.getString("app_name", "");
        return n == null || n.isEmpty() ? "Offline Media Vault" : n;
    }

    private File iconFile() { return new File(getFilesDir(), "custom_icon.png"); }

    private void applyTaskDesc() {
        try {
            Bitmap b = iconFile().exists() ? BitmapFactory.decodeFile(iconFile().getAbsolutePath()) : null;
            if (b != null) b = Bitmap.createScaledBitmap(b, 192, 192, true);
            setTaskDescription(b == null ? new ActivityManager.TaskDescription(appName()) : new ActivityManager.TaskDescription(appName(), b));
        } catch (Throwable ignored) { }
    }

    private void pickIcon() {
        skipLock = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_ICON);
    }

    private void setIconFrom(final Uri u) {
        io.execute(() -> {
            File raw = new File(getCacheDir(), "icon_raw");
            boolean ok = false;
            try (InputStream in = getContentResolver().openInputStream(u); OutputStream os = new FileOutputStream(raw)) {
                if (in != null) { pump(in, os); ok = true; }
            } catch (Exception ignored) { }
            if (ok) {
                Bitmap b = decodeSampled(raw.getAbsolutePath(), 1024);
                if (b != null) {
                    int side = Math.min(b.getWidth(), b.getHeight());
                    Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
                    sq = Bitmap.createScaledBitmap(sq, 512, 512, true);
                    try (OutputStream os = new FileOutputStream(iconFile())) { sq.compress(Bitmap.CompressFormat.PNG, 100, os); } catch (Exception ignored) { }
                }
            }
            raw.delete();
            ui.post(() -> { toast(t("brand_icon_saved")); applyTaskDesc(); renderSettings(); });
        });
    }

    private Bitmap adaptiveIcon() {
        Bitmap src = iconFile().exists() ? BitmapFactory.decodeFile(iconFile().getAbsolutePath()) : null;
        if (src == null) return null;
        int sz = 432;
        Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0));
        int in = (int) (sz * 0.62f);
        c.drawBitmap(Bitmap.createScaledBitmap(src, in, in, true), (sz - in) / 2f, (sz - in) / 2f, null);
        return out;
    }

    private void pinShortcut() {
        try {
            if (Build.VERSION.SDK_INT < 26) { toast(t("brand_nosupport")); return; }
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) { toast(t("brand_nosupport")); return; }
            Bitmap ab = adaptiveIcon();
            Icon ic = ab != null ? Icon.createWithAdaptiveBitmap(ab) : Icon.createWithResource(this, R.mipmap.ic_launcher_1);
            Intent in = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN);
            ShortcutInfo si = new ShortcutInfo.Builder(this, "custom_" + System.currentTimeMillis())
                    .setShortLabel(appName()).setLongLabel(appName()).setIcon(ic).setIntent(in).build();
            sm.requestPinShortcut(si, null);
            toast(t("brand_done"));
        } catch (Throwable e) {
            toast(t("brand_nosupport"));
        }
    }

    // Корзина очищается сама через N дней (по умолчанию 30)
    private void purgeTrash() {
        int days = prefs.getInt("trash_days", 30);
        if (days <= 0) return;
        long lim = System.currentTimeMillis() - days * 86400000L;
        ArrayList<Library.Item> old = new ArrayList<>();
        for (Library.Item it : lib.trashList()) if (it.trashedAt > 0 && it.trashedAt < lim) old.add(it);
        if (old.isEmpty()) return;
        for (Library.Item it : old) thumbCache.remove(lib.deleteForever(it));
        lib.save();
    }

    private View makeCell(final Library.Item it, int cw, int thumbH, int tagLines, final ArrayList<Library.Item> list, final int idx,
                          final LinkedHashSet<String> selection, final Runnable onSel) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        FrameLayout th = makeThumb(it);
        if (selection != null && selection.contains(it.id)) {
            View tint = new View(this);
            GradientDrawable tg = new GradientDrawable();
            tg.setCornerRadius(dp(16));
            tg.setColor((ACCENT & 0x00FFFFFF) | 0x38000000);
            tg.setStroke(dp(3), ACCENT);
            tint.setBackground(tg);
            th.addView(tint, new FrameLayout.LayoutParams(-1, -1));
            th.setScaleX(0.93f);
            th.setScaleY(0.93f);
            TextView ck = tv("✓", 14, ONACC, true);
            ck.setGravity(Gravity.CENTER);
            GradientDrawable og = new GradientDrawable();
            og.setShape(GradientDrawable.OVAL);
            og.setColor(ACCENT);
            og.setStroke(dp(2), 0xFFFFFFFF);
            ck.setBackground(og);
            FrameLayout.LayoutParams ckp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.START);
            ckp.setMargins(dp(6), dp(6), 0, 0);
            th.addView(ck, ckp);
        }
        if (it.fav) {
            TextView hv = tv("♥", 12, 0xFFFFFFFF, true);
            hv.setGravity(Gravity.CENTER);
            GradientDrawable hg = new GradientDrawable();
            hg.setShape(GradientDrawable.OVAL);
            hg.setColor(0xCCE8527E);
            hv.setBackground(hg);
            FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
            hlp.setMargins(0, dp(6), dp(6), 0);
            th.addView(hv, hlp);
        }
        cell.addView(th, new LinearLayout.LayoutParams(cw, thumbH));
        if (showNames) {
            TextView n = tv(it.name, 11, FG, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setPadding(dp(2), dp(3), dp(2), 0);
            cell.addView(n, new LinearLayout.LayoutParams(-1, -2));
        }
        if (tagLines > 0) {
            Flow tg = new Flow(this, dp(4), dp(4)).rows(tagLines);
            for (String s : Library.tagsOf(it)) tg.addView(tagPill(s, true));
            LinearLayout.LayoutParams tgp = new LinearLayout.LayoutParams(-1, tagBodyH(tagLines));
            tgp.topMargin = dp(4);
            cell.addView(tg, tgp);
        }
        cellTouch(cell, it, selection == null || selection.isEmpty());
        if (selection != null && selection.contains(it.id) && it.id.equals(popId)) {
            popId = null;
            cell.setScaleX(0.9f);
            cell.setScaleY(0.9f);
            cell.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new OvershootInterpolator(2.2f)).start();
        }
        cell.setOnClickListener(v -> {
            if (selection != null && !selection.isEmpty()) {
                if (!selection.remove(it.id)) selection.add(it.id);
                popId = it.id;
                onSel.run();
            } else {
                Rect zr = new Rect();
                if (v.getGlobalVisibleRect(zr)) zoomFrom = zr;
                openViewer(list, idx);
            }
        });
        if (selection != null) {
            cell.setOnLongClickListener(v -> {
                buzz(v, HapticFeedbackConstants.LONG_PRESS);
                if (!selection.isEmpty() && !selection.contains(it.id)) {
                    // диапазон: от последнего выделенного до этого файла
                    String last = null;
                    for (String x : selection) last = x;
                    int a = -1;
                    for (int k = 0; k < list.size(); k++) if (list.get(k).id.equals(last)) a = k;
                    if (a >= 0) {
                        for (int k = Math.min(a, idx); k <= Math.max(a, idx); k++) selection.add(list.get(k).id);
                        popId = it.id;
                        onSel.run();
                        return true;
                    }
                }
                if (!selection.remove(it.id)) selection.add(it.id);
                popId = it.id;
                onSel.run();
                return true;
            });
        }
        return cell;
    }

    private void fillGrid(LinearLayout grid, ScrollView sv, final ArrayList<Library.Item> list, LinkedHashSet<String> selection, Runnable onSel) {
        grid.removeAllViews();
        int sw = sv.getWidth();
        if (sw <= 0) sw = getResources().getDisplayMetrics().widthPixels;
        int vh = sv.getHeight();
        if (vh <= 0) vh = getResources().getDisplayMetrics().heightPixels - dp(54) - dp(170);
        int gap = dp(8);
        int cw = Math.max(dp(40), (sw - dp(24) - gap * (cols - 1)) / cols);
        int tagLines = !prefs.getBoolean("feed_tags", false) ? 0 : cols >= 3 ? 1 : 2;
        int labelH = (showNames ? dp(20) : 0) + tagBodyH(tagLines) + dp(4);
        int cellH = cols <= 2 ? Math.max(dp(150), (vh - dp(8) - gap * 2) / 3) : (int) (cw * 1.3f) + labelH;
        int thumbH = Math.max(dp(70), cellH - labelH);
        final boolean anim = animGrid;
        animGrid = false;
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                if (anim && i / cols < 8) {
                    row.setAlpha(0f);
                    row.setTranslationY(dp(26));
                    row.setScaleX(0.97f);
                    row.setScaleY(0.97f);
                    row.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay((i / cols) * 40L).setDuration(340)
                            .setInterpolator(new DecelerateInterpolator(2f)).start();
                }
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = gap;
                grid.addView(row, rp);
            }
            View cell = makeCell(list.get(i), cw, thumbH, tagLines, list, i, selection, onSel);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cw, -2);
            if (i % cols != 0) cp.setMarginStart(gap);
            row.addView(cell, cp);
        }
    }

    private TextView emptyNote(String text) {
        SpannableString ss = new SpannableString("✨\n" + text);
        ss.setSpan(new RelativeSizeSpan(2.6f), 0, 1, 0);
        TextView e = tv("", 14, MUTED, false);
        e.setText(ss);
        e.setLineSpacing(dp(4), 1f);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(16), dp(32), dp(16), dp(32));
        e.setBackground(panel(14));
        return e;
    }

    // ---------- Лента ----------
    private ScrollView feedScroll;
    private LinearLayout feedGrid;
    private TextView filterBtn, feedInfo;
    private LinearLayout typeRow, contBar;

    private View buildFeed() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(12), dp(12), dp(4));
        head.addView(headline(t("tab_feed"), 30), new LinearLayout.LayoutParams(0, -2, 1));
        filterBtn = tv(t("filter"), 14, ONACC, true);
        filterBtn.setGravity(Gravity.CENTER);
        filterBtn.setPadding(dp(18), 0, dp(18), 0);
        filterBtn.setOnClickListener(v -> openFilter());
        head.addView(filterBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        r.addView(head, new LinearLayout.LayoutParams(-1, -2));
        feedHead = head;
        feedSelBar = new LinearLayout(this);
        feedSelBar.setGravity(Gravity.CENTER_VERTICAL);
        feedSelBar.setPadding(dp(8), dp(12), dp(12), dp(4));
        TextView fsCancel = tv("✕", 20, FG, true);
        fsCancel.setGravity(Gravity.CENTER);
        fsCancel.setOnClickListener(v -> { feedSel.clear(); renderFeed(); });
        feedSelBar.addView(fsCancel, new LinearLayout.LayoutParams(dp(44), dp(44)));
        feedSelCount = tv("", 17, FG, true);
        feedSelCount.setPadding(dp(8), 0, dp(8), 0);
        feedSelBar.addView(feedSelCount, new LinearLayout.LayoutParams(0, -2, 1));
        TextView fsAll = button(t("sel_all"));
        fsAll.setBackground(round(BTN, 20));
        fsAll.setOnClickListener(v -> { for (Library.Item it : filtered()) feedSel.add(it.id); renderFeed(); });
        feedSelBar.addView(fsAll, new LinearLayout.LayoutParams(-2, dp(40)));
        TextView fsFav = tv("♥", 18, 0xFFFFFFFF, true);
        fsFav.setGravity(Gravity.CENTER);
        fsFav.setBackground(round(0xFFE8527E, 20));
        fsFav.setOnClickListener(v -> toggleFav(selectedFeed()));
        TextView fsAlbum = tv("▣", 18, ONACC, true);
        fsAlbum.setGravity(Gravity.CENTER);
        fsAlbum.setBackground(accentBg(20));
        fsAlbum.setOnClickListener(v -> chooseAlbum(selectedFeed()));
        TextView fsDel = tv("🗑", 18, 0xFFFFFFFF, true);
        fsDel.setGravity(Gravity.CENTER);
        fsDel.setBackground(round(RED, 20));
        fsDel.setOnClickListener(v -> feedTrashAsk());
        for (TextView b : new TextView[]{fsFav, fsAlbum, fsDel}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(48), dp(40));
            bp.setMarginStart(dp(8));
            feedSelBar.addView(b, bp);
        }
        feedSelBar.setVisibility(View.GONE);
        r.addView(feedSelBar, new LinearLayout.LayoutParams(-1, -2));
        typeRow = new LinearLayout(this);
        typeRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        r.addView(typeRow, new LinearLayout.LayoutParams(-1, -2));
        contBar = new LinearLayout(this);
        contBar.setGravity(Gravity.CENTER_VERTICAL);
        contBar.setPadding(dp(14), dp(10), dp(14), dp(10));
        contBar.setBackground(panel(18));
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-1, -2);
        cbp.setMargins(dp(12), dp(2), dp(12), dp(6));
        contBar.setVisibility(View.GONE);
        r.addView(contBar, cbp);
        feedInfo = tv("", 12, MUTED, false);
        feedInfo.setPadding(dp(16), 0, dp(16), dp(8));
        feedInfo.setOnClickListener(v -> { if (sortMode == 2) { shuffleSeed = System.nanoTime(); animGrid = true; renderFeed(); } });
        r.addView(feedInfo, new LinearLayout.LayoutParams(-1, -2));
        feedScroll = new ScrollView(this);
        feedGrid = new LinearLayout(this);
        feedGrid.setOrientation(LinearLayout.VERTICAL);
        feedGrid.setPadding(dp(12), 0, dp(12), dp(8));
        feedScroll.addView(feedGrid);
        FrameLayout feedWrap = new FrameLayout(this);
        feedWrap.addView(feedScroll, new FrameLayout.LayoutParams(-1, -1));
        final TextView upFab = tv("↑", 20, ONACC, true);
        upFab.setGravity(Gravity.CENTER);
        GradientDrawable ug = new GradientDrawable();
        ug.setShape(GradientDrawable.OVAL);
        ug.setColor(ACCENT);
        upFab.setBackground(ug);
        upFab.setElevation(dp(8));
        upFab.setVisibility(View.GONE);
        upFab.setOnClickListener(v -> { buzz(v, HapticFeedbackConstants.KEYBOARD_TAP); feedScroll.smoothScrollTo(0, 0); });
        pressFx(upFab);
        FrameLayout.LayoutParams ufp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        ufp.setMargins(0, 0, dp(16), dp(16));
        feedWrap.addView(upFab, ufp);
        feedScroll.setOnScrollChangeListener((sv0, sx, sy, osx, osy) -> {
            boolean show = sy > getResources().getDisplayMetrics().heightPixels;
            boolean shown = upFab.getVisibility() == View.VISIBLE && upFab.getScaleX() > 0.5f;
            if (show == shown) return;
            upFab.animate().cancel();
            if (show) {
                upFab.setVisibility(View.VISIBLE);
                upFab.setScaleX(0f); upFab.setScaleY(0f);
                upFab.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.5f)).start();
            } else {
                upFab.animate().scaleX(0f).scaleY(0f).setDuration(160).withEndAction(() -> upFab.setVisibility(View.GONE)).start();
            }
        });
        r.addView(feedWrap, new LinearLayout.LayoutParams(-1, 0, 1));
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
    private ArrayList<Library.Item> selectedFeed() {
        ArrayList<Library.Item> out = new ArrayList<>();
        for (Library.Item it : lib.active()) if (feedSel.contains(it.id)) out.add(it);
        return out;
    }

    private void toggleFav(ArrayList<Library.Item> targets) {
        if (targets.isEmpty()) return;
        boolean all = true;
        for (Library.Item it : targets) if (!it.fav) { all = false; break; }
        for (Library.Item it : targets) it.fav = !all;
        lib.save();
        feedSel.clear(); albumSelI.clear(); sel.clear();
        refreshAll();
        toast(tf(all ? "unfav_n" : "fav_n", targets.size()));
    }

    private void feedTrashAsk() {
        final ArrayList<Library.Item> targets = selectedFeed();
        if (targets.isEmpty()) return;
        sheet(tf("sel_n", targets.size()), t("trash_note"),
                new Act("🗑", t("del_to_trash"), t("del_to_trash_d"), RED, () -> {
                    for (Library.Item it : targets) lib.moveToTrash(it);
                    lib.save(); feedSel.clear(); refreshAll();
                    trashUndo(targets);
                }));
    }

    // 1 фото, 2 видео, 3 GIF, 4 манга/PDF
    private int typeOf(Library.Item it) {
        String n = it.name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".gif")) return 3;
        if (Library.isVideo(n)) return 2;
        if (Library.isPdf(n)) return 4;
        return 1;
    }

    private void renderTypeRow() {
        int[] cnt = new int[5];
        for (Library.Item it : lib.active()) { cnt[typeOf(it)]++; cnt[0]++; }
        typeRow.removeAllViews();
        String[] names = {t("type_all"), t("type_photo"), t("type_video"), t("type_gif"), t("type_pdf")};
        for (int i = 0; i < 5; i++) {
            if (i > 0 && cnt[i] == 0 && typeFilter != i) continue;
            final int k = i;
            TextView c = chip(names[i] + (i > 0 ? "  " + cnt[i] : ""), typeFilter == i);
            c.setOnClickListener(v -> { typeFilter = k; animGrid = true; renderFeed(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            typeRow.addView(c, lp);
        }
        typeRow.setVisibility(cnt[0] == 0 ? View.GONE : View.VISIBLE);
    }

    // «Продолжить просмотр»: последний открытый файл
    private void renderContinue(final ArrayList<Library.Item> list) {
        contBar.removeAllViews();
        String lid = prefs.getString("last_id", null);
        Library.Item last = null;
        if (lid != null) for (Library.Item it : lib.active()) if (it.id.equals(lid)) { last = it; break; }
        if (last == null || !feedSel.isEmpty()) { contBar.setVisibility(View.GONE); return; }
        final Library.Item li = last;
        contBar.setVisibility(View.VISIBLE);
        contBar.addView(makeThumb(li), new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, dp(8), 0);
        tx.addView(tv("▶ " + t("continue_view"), 14, ACCENT, true));
        String sub = li.name;
        int vp = prefs.getInt("vp_" + li.id, 0), pg = prefs.getInt("pg_" + li.id, 0);
        if (Library.isVideo(li.name) && vp > 0) sub += " · " + fmtDur(vp);
        else if (Library.isPdf(li.name) && pg > 0) sub += " · " + tf("page_n", pg + 1, 0).replace(" / 0", "");
        TextView st = tv(sub, 12, MUTED, false);
        st.setSingleLine(true);
        st.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(st);
        contBar.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        contBar.setOnClickListener(v -> {
            ArrayList<Library.Item> src = filtered();
            int ix = -1;
            for (int i = 0; i < src.size(); i++) if (src.get(i).id.equals(li.id)) { ix = i; break; }
            if (ix < 0) { src = new ArrayList<>(); src.add(li); ix = 0; }
            openViewer(src, ix);
        });
        pressFx(contBar);
    }

    private ArrayList<Library.Item> filtered() {
        ArrayList<Library.Item> out = new ArrayList<>();
        for (Library.Item it : lib.active()) {
            List<String> ts = Library.tagsOf(it);
            boolean banned = false;
            for (String x : excTags) if (ts.contains(x)) { banned = true; break; }
            if (banned) continue;
            if (favOnly && !it.fav) continue;
            if (typeFilter != 0 && typeOf(it) != typeFilter) continue;
            boolean ok;
            if (incTags.isEmpty()) ok = true;
            else if (matchAll) ok = ts.containsAll(incTags);
            else { ok = false; for (String x : incTags) if (ts.contains(x)) { ok = true; break; } }
            if (ok) out.add(it);
        }
        if (sortMode == 2) Collections.shuffle(out, new java.util.Random(shuffleSeed));
        else Collections.sort(out, (m1, m2) -> sortMode == 0 ? Long.compare(m2.added, m1.added) : Long.compare(m1.added, m2.added));
        return out;
    }

    private void renderFeed() {
        if (feedGrid == null || !visible(tabViews[0])) return;
        TreeMap<String, Integer> counts = allTagCounts();
        incTags.retainAll(counts.keySet());
        excTags.retainAll(counts.keySet());
        HashSet<String> liveIds = new HashSet<>();
        for (Library.Item it : lib.active()) liveIds.add(it.id);
        feedSel.retainAll(liveIds);
        boolean fmode = !feedSel.isEmpty();
        feedHead.setVisibility(fmode ? View.GONE : View.VISIBLE);
        fadeVis(feedSelBar, fmode);
        feedSelCount.setText(tf("sel_n", feedSel.size()));
        int active = incTags.size() + excTags.size() + (sortMode == 0 ? 0 : 1) + (favOnly ? 1 : 0);
        filterBtn.setText(active > 0 ? t("filter") + " · " + active : t("filter"));
        filterBtn.setTextColor(active > 0 ? ONACC : FG);
        filterBtn.setBackground(active > 0 ? accentBg(20) : round(BTN, 20));
        startIcon(filterBtn, R.drawable.ic_tune, active > 0 ? ONACC : FG);
        final ArrayList<Library.Item> list = filtered();
        renderTypeRow();
        renderContinue(list);
        StringBuilder sb = new StringBuilder();
        sb.append(tf("count_of", list.size(), lib.active().size()));
        if (!incTags.isEmpty()) sb.append(" · ").append(tf("with_tags", TextUtils.join(", ", incTags)));
        if (!excTags.isEmpty()) sb.append(" · ").append(tf("hidden_tags", TextUtils.join(", ", excTags)));
        if (sortMode == 1) sb.append(" · ").append(t("oldest_first_note"));
        else if (sortMode == 2) sb.append(" · ").append(t("random_note"));
        feedInfo.setText(sb.toString());
        feedGrid.removeAllViews();
        if (list.isEmpty()) {
            View en = emptyNote(lib.active().isEmpty() ? t("empty_all") : t("empty_filter"));
            en.setAlpha(0f);
            en.setTranslationY(dp(20));
            en.animate().alpha(1f).translationY(0f).setDuration(360).setInterpolator(new DecelerateInterpolator(2f)).start();
            feedGrid.addView(en, params(-1, -2));
            if (lib.active().isEmpty()) {
                TextView add = primaryButton("＋  " + t("tab_upload"));
                add.setBackground(accentBg(24));
                add.setOnClickListener(v -> showTab(2));
                LinearLayout.LayoutParams ap0 = params(-1, 50);
                ap0.topMargin = dp(12);
                feedGrid.addView(add, ap0);
            }
            return;
        }
        fillGrid(feedGrid, feedScroll, list, feedSel, this::renderFeed);
        fxTree(feedGrid);
    }

    // ---------- Фильтр ленты (нижняя шторка) ----------
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        c.setBackground(panel(20));
        return c;
    }

    private void addCard(LinearLayout parent, LinearLayout c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(c, lp);
    }

    private TextView cardTitle(String s) {
        TextView l = tv(s, 12, MUTED, true);
        l.setPadding(dp(2), 0, 0, dp(10));
        return l;
    }

    private TextView filterChip(String tag, int count, boolean on, boolean black) {
        int col = black ? RED : pillColor(tag);
        String label = (on ? (black ? "✕ " : "✓ ") : "") + tag + (count > 0 ? "  " + count : "");
        TextView c = tv(label, 13, on ? 0xFFFFFFFF : FG, on);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setBackground(round(on ? col : blend(PANEL, col, 0.30f), 20));
        return c;
    }

    private void openFilter() {
        final LinkedHashSet<String> inc = new LinkedHashSet<>(incTags), exc = new LinkedHashSet<>(excTags);
        final boolean[] st = {matchAll, true, favOnly};
        final int[] sortSel = {sortMode};
        final TreeMap<String, Integer> counts = allTagCounts();
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);

        final LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(BG);
        float rr = dp(28);
        sbg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        sheet.setBackground(sbg);
        sheet.setPadding(dp(16), dp(10), dp(16), dp(14));
        sheet.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(16), dp(10), dp(16), dp(14) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        View handle = new View(this);
        handle.setBackground(round(blend(BG, FG, 0.25f), 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(10);
        sheet.addView(handle, hp);

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.addView(tv(t("filter_title"), 20, FG, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView closeX = tv("✕", 16, FG, true);
        closeX.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL);
        cg.setColor(FIELD);
        closeX.setBackground(cg);
        closeX.setOnClickListener(v -> d.dismiss());
        hd.addView(closeX, new LinearLayout.LayoutParams(dp(38), dp(38)));
        sheet.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        final EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint(t("tag_search"));
        styleEdit(q);
        q.setBackground(round(FIELD, 24));
        q.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams qp = params(-1, 46);
        qp.topMargin = dp(12);
        qp.bottomMargin = dp(10);
        sheet.addView(q, qp);

        ScrollView sv = new ScrollView(this);
        final LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        sv.addView(holder);
        sheet.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout foot = new LinearLayout(this);
        foot.setPadding(0, dp(10), 0, 0);
        final TextView clear = button(t("clear_sel"));
        clear.setBackground(round(BTN, 24));
        TextView applyB = primaryButton(t("apply"));
        applyB.setBackground(accentBg(24));
        foot.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(48), 1);
        ap.setMarginStart(dp(10));
        foot.addView(applyB, ap);
        sheet.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            holder.removeAllViews();
            int picked = inc.size() + exc.size();
            clear.setText(t("clear_sel") + (picked > 0 ? " (" + picked + ")" : ""));
            String f = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            while (f.startsWith("#")) f = f.substring(1);

            LinearLayout c1 = card();
            c1.addView(cardTitle(t("sec_tags")));
            LinearLayout c2 = card();
            c2.addView(cardTitle(t("sec_black")));
            if (counts.isEmpty()) {
                c1.addView(tv(t("no_tags_yet"), 13, MUTED, false));
                addCard(holder, c1);
            } else {
                Flow f1 = new Flow(this, dp(8), dp(8));
                Flow f2 = new Flow(this, dp(8), dp(8));
                int shown = 0;
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    final String tag = e.getKey();
                    if (!f.isEmpty() && !tag.contains(f)) continue;
                    shown++;
                    TextView a1 = filterChip(tag, e.getValue(), inc.contains(tag), false);
                    a1.setOnClickListener(v -> { if (!inc.remove(tag)) { inc.add(tag); exc.remove(tag); } draw[0].run(); });
                    f1.addView(a1);
                    TextView a2 = filterChip(tag, 0, exc.contains(tag), true);
                    a2.setOnClickListener(v -> { if (!exc.remove(tag)) { exc.add(tag); inc.remove(tag); } draw[0].run(); });
                    f2.addView(a2);
                }
                if (shown == 0) {
                    c1.addView(tv(t("no_tag_found"), 13, MUTED, false));
                    addCard(holder, c1);
                } else {
                    c1.addView(f1, new LinearLayout.LayoutParams(-1, -2));
                    c2.addView(f2, new LinearLayout.LayoutParams(-1, -2));
                    addCard(holder, c1);
                    addCard(holder, c2);
                }
            }
            LinearLayout c5 = card();
            c5.addView(cardTitle(t("sec_show")));
            c5.addView(segmented(new String[]{t("show_all"), "♥ " + t("favorites")}, st[2] ? 1 : 0, i -> { st[2] = i == 1; draw[0].run(); }));
            addCard(holder, c5);
            LinearLayout c3 = card();
            c3.addView(cardTitle(t("sec_match")));
            c3.addView(segmented(new String[]{t("match_all"), t("match_any")}, st[0] ? 0 : 1, i -> { st[0] = i == 0; draw[0].run(); }));
            addCard(holder, c3);
            LinearLayout c4 = card();
            c4.addView(cardTitle(t("sec_sort")));
            c4.addView(segmented(new String[]{t("sort_new"), t("sort_old"), "🎲 " + t("sort_random")}, sortSel[0], i -> { sortSel[0] = i; draw[0].run(); }));
            addCard(holder, c4);
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { draw[0].run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        clear.setOnClickListener(v -> { inc.clear(); exc.clear(); draw[0].run(); });
        applyB.setOnClickListener(v -> {
            incTags.clear(); incTags.addAll(inc);
            excTags.clear(); excTags.addAll(exc);
            matchAll = st[0]; favOnly = st[2];
            sortMode = sortSel[0];
            if (sortMode == 2) shuffleSeed = System.nanoTime();
            animGrid = true;
            renderFeed();
            d.dismiss();
        });
        draw[0].run();
        fxTree(sheet);
        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().heightPixels * 0.88f));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        animWin(d, R.style.OmvSheetAnim);
        d.setCanceledOnTouchOutside(true);
        openDialogs.add(d);
        d.show();
    }

    // ---------- Альбомы ----------
    private LinearLayout albumListBox, albumGrid, albumHead, albumSelBar;
    private ScrollView albumListScroll, albumDetailScroll;
    private TextView albumTitle, albumBackBtn, albumRenameBtn, albumDelBtn, albumNewBtn, albumSelCount, albumSelRename;
    private final LinkedHashSet<String> albumSelA = new LinkedHashSet<>(), albumSelI = new LinkedHashSet<>();

    private View buildAlbums() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);
        albumHead = new LinearLayout(this);
        albumHead.setGravity(Gravity.CENTER_VERTICAL);
        albumHead.setPadding(dp(8), dp(12), dp(12), dp(8));
        albumBackBtn = tv("←", 22, FG, true);
        albumBackBtn.setGravity(Gravity.CENTER);
        albumBackBtn.setOnClickListener(v -> { albumOpen = null; animGrid = true; renderAlbums(); });
        albumHead.addView(albumBackBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumTitle = headline("", 26);
        albumTitle.setSingleLine(true);
        albumTitle.setEllipsize(TextUtils.TruncateAt.END);
        albumTitle.setPadding(dp(8), 0, dp(8), 0);
        albumHead.addView(albumTitle, new LinearLayout.LayoutParams(0, -2, 1));
        albumRenameBtn = tv("✎", 20, FG, true);
        albumRenameBtn.setGravity(Gravity.CENTER);
        albumRenameBtn.setOnClickListener(v -> renameAlbum());
        albumHead.addView(albumRenameBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumDelBtn = tv("⋯", 26, FG, true);
        albumDelBtn.setGravity(Gravity.CENTER);
        albumDelBtn.setOnClickListener(v -> albumMenu());
        albumHead.addView(albumDelBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumNewBtn = tv("+ " + t("new_album"), 14, ONACC, true);
        albumNewBtn.setGravity(Gravity.CENTER);
        albumNewBtn.setPadding(dp(16), 0, dp(16), 0);
        albumNewBtn.setBackground(accentBg(20));
        albumNewBtn.setOnClickListener(v -> promptText(t("new_album"), "", t("album_name"), name -> {
            if (!name.isEmpty()) { lib.createAlbum(name); lib.save(); renderAlbums(); }
        }));
        albumHead.addView(albumNewBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        r.addView(albumHead, new LinearLayout.LayoutParams(-1, -2));

        albumSelBar = new LinearLayout(this);
        albumSelBar.setGravity(Gravity.CENTER_VERTICAL);
        albumSelBar.setPadding(dp(8), dp(12), dp(12), dp(8));
        TextView cancelSel = tv("✕", 20, FG, true);
        cancelSel.setGravity(Gravity.CENTER);
        cancelSel.setOnClickListener(v -> albumSelCancel());
        albumSelBar.addView(cancelSel, new LinearLayout.LayoutParams(dp(44), dp(44)));
        albumSelCount = tv("", 17, FG, true);
        albumSelCount.setPadding(dp(8), 0, dp(8), 0);
        albumSelBar.addView(albumSelCount, new LinearLayout.LayoutParams(0, -2, 1));
        albumSelRename = tv("✎", 20, FG, true);
        albumSelRename.setGravity(Gravity.CENTER);
        albumSelRename.setOnClickListener(v -> { if (albumSelA.size() == 1) renameAlbumById(albumSelA.iterator().next()); });
        albumSelBar.addView(albumSelRename, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView selAll = button(t("sel_all"));
        selAll.setBackground(round(BTN, 20));
        selAll.setOnClickListener(v -> albumSelAll());
        albumSelBar.addView(selAll, new LinearLayout.LayoutParams(-2, dp(40)));
        TextView selDel = tv("🗑", 18, 0xFFFFFFFF, true);
        selDel.setGravity(Gravity.CENTER);
        selDel.setBackground(round(RED, 20));
        selDel.setOnClickListener(v -> albumSelDelete());
        LinearLayout.LayoutParams sdp = new LinearLayout.LayoutParams(dp(54), dp(40));
        sdp.setMarginStart(dp(8));
        albumSelBar.addView(selDel, sdp);
        albumSelBar.setVisibility(View.GONE);
        r.addView(albumSelBar, new LinearLayout.LayoutParams(-1, -2));

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

    private void albumSelCancel() { albumSelA.clear(); albumSelI.clear(); renderAlbums(); }

    private void albumSelAll() {
        if (albumOpen == null) { for (Library.Album a : lib.albums) albumSelA.add(a.id); }
        else { for (Library.Item it : lib.inAlbum(albumOpen)) albumSelI.add(it.id); }
        renderAlbums();
    }

    private void toggleAlbumSel(String id) {
        if (!albumSelA.remove(id)) albumSelA.add(id);
        renderAlbums();
    }

    // В списке альбомов: удалить выбранные альбомы (с файлами или без). Внутри альбома: действия с выбранными файлами.
    private void albumSelDelete() {
        if (albumOpen != null) {
            final ArrayList<Library.Item> targets = new ArrayList<>();
            for (Library.Item it : lib.inAlbum(albumOpen)) if (albumSelI.contains(it.id)) targets.add(it);
            if (targets.isEmpty()) return;
            final String aid = albumOpen;
            final boolean isFav = Library.FAV.equals(aid);
            boolean allFav = true;
            for (Library.Item it : targets) if (!it.fav) { allFav = false; break; }
            ArrayList<Act> acts = new ArrayList<>();
            acts.add(new Act(isFav ? "♡" : "↗", isFav ? t("rm_from_fav") : t("rm_from_album"), isFav ? t("rm_from_fav_d") : t("rm_from_album_d"), 0, () -> {
                for (Library.Item it : targets) { if (isFav) it.fav = false; else Library.removeAlb(it, aid); }
                lib.save(); albumSelI.clear(); refreshAll();
                toast(tf("removed_from_album", targets.size()));
            }));
            if (!isFav) {
                final boolean af = allFav;
                acts.add(new Act(af ? "♡" : "♥", af ? t("rm_from_fav") : t("add_to_fav"), null, 0, () -> toggleFav(targets)));
            }
            acts.add(new Act("＋", t("pick_album"), t("pick_album_d"), 0, () -> chooseAlbum(targets)));
            acts.add(new Act("🗑", t("del_from_app"), t("del_from_app_d"), RED, () -> {
                for (Library.Item it : targets) lib.moveToTrash(it);
                lib.save(); albumSelI.clear(); refreshAll();
                trashUndo(targets);
            }));
            sheet(tf("sel_n", targets.size()), null, acts.toArray(new Act[0]));
            return;
        }
        final ArrayList<String> ids = new ArrayList<>(albumSelA);
        if (ids.isEmpty()) return;
        sheet(tf("album_del_q", ids.size()), t("albums_files_note"),
                new Act("▣", t("albums_only"), t("albums_only_d"), 0, () -> deleteAlbums(ids, false)),
                new Act("🗑", t("albums_files"), t("albums_files_d"), RED, () -> deleteAlbums(ids, true)));
    }

    private void deleteAlbums(ArrayList<String> ids, boolean withFiles) {
        int files = 0;
        if (albumOpen != null && ids.contains(albumOpen)) albumOpen = null;
        for (String id : ids) {
            if (withFiles) for (Library.Item it : lib.inAlbum(id)) { lib.moveToTrash(it); files++; }
            lib.deleteAlbum(id);
        }
        lib.save();
        albumSelA.clear();
        refreshAll();
        toast(tf("albums_deleted", ids.size()) + (files > 0 ? " · " + tf("moved_trash", files) : ""));
    }

    private void renderAlbums() {
        if (albumListBox == null || !visible(tabViews[1])) return;
        if (albumOpen != null && !Library.FAV.equals(albumOpen) && lib.albumById(albumOpen) == null) albumOpen = null;
        HashSet<String> aids = new HashSet<>();
        for (Library.Album a : lib.albums) aids.add(a.id);
        albumSelA.retainAll(aids);
        final boolean detail = albumOpen != null;
        if (detail) {
            albumSelA.clear();
            HashSet<String> iids = new HashSet<>();
            for (Library.Item it : lib.inAlbum(albumOpen)) iids.add(it.id);
            albumSelI.retainAll(iids);
        } else albumSelI.clear();
        final boolean selMode = detail ? !albumSelI.isEmpty() : !albumSelA.isEmpty();
        albumHead.setVisibility(selMode ? View.GONE : View.VISIBLE);
        fadeVis(albumSelBar, selMode);
        albumSelCount.setText(tf("sel_n", detail ? albumSelI.size() : albumSelA.size()));
        albumSelRename.setVisibility(!detail && albumSelA.size() == 1 ? View.VISIBLE : View.GONE);
        int dv = detail ? View.VISIBLE : View.GONE, lv = detail ? View.GONE : View.VISIBLE;
        albumBackBtn.setVisibility(dv);
        albumRenameBtn.setVisibility(dv);
        albumDelBtn.setVisibility(dv);
        albumNewBtn.setVisibility(lv);
        albumDetailScroll.setVisibility(dv);
        albumListScroll.setVisibility(lv);
        if (detail) {
            boolean isFav = Library.FAV.equals(albumOpen);
            Library.Album a = isFav ? null : lib.albumById(albumOpen);
            albumTitle.setText(isFav ? "♥ " + t("favorites") : a.name);
            albumTitle.setTextSize(24);
            albumRenameBtn.setVisibility(isFav ? View.GONE : View.VISIBLE);
            albumDelBtn.setVisibility(isFav ? View.GONE : View.VISIBLE);
            ArrayList<Library.Item> list = lib.inAlbum(albumOpen);
            albumGrid.removeAllViews();
            if (list.isEmpty()) albumGrid.addView(emptyNote(t(isFav ? "fav_empty" : "album_empty")), params(-1, -2));
            else fillGrid(albumGrid, albumDetailScroll, list, albumSelI, this::renderAlbums);
            fxTree(albumGrid);
            return;
        }
        albumTitle.setText(t("tab_albums"));
        albumTitle.setTextSize(30);
        albumListBox.removeAllViews();
        addFavCard();
        if (lib.albums.isEmpty()) { albumListBox.addView(emptyNote(t("no_albums")), params(-1, -2)); fxTree(albumListBox); return; }
        for (final Library.Album a : new ArrayList<>(lib.albums)) {
            ArrayList<Library.Item> its = lib.inAlbum(a.id);
            final boolean picked = albumSelA.contains(a.id);
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(9), dp(12), dp(9));
            card.setBackground(picked ? round(blend(PANEL, ACCENT, 0.3f), 20) : panel(20));
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
            card.addView(stack(cover, ACCENT), new LinearLayout.LayoutParams(dp(86), dp(84)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            TextView nm = tv(a.name, 15, FG, true);
            nm.setMaxLines(2);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(nm);
            info.addView(tv(tf("items_n", its.size()), 12, MUTED, false));
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            if (selMode) {
                TextView ck = tv(picked ? "☑" : "☐", 22, picked ? ACCENT : FG, false);
                ck.setGravity(Gravity.CENTER);
                card.addView(ck, new LinearLayout.LayoutParams(dp(36), dp(48)));
            }
            card.setOnClickListener(v -> {
                if (!albumSelA.isEmpty()) toggleAlbumSel(a.id);
                else { albumSelI.clear(); albumOpen = a.id; animGrid = true; renderAlbums(); }
            });
            card.setOnLongClickListener(v -> { toggleAlbumSel(a.id); return true; });
        }
        fxTree(albumListBox);
    }

    // Обложка «стопкой»: за фото выглядывают ещё две карточки
    private View stack(View cover, int tint) {
        FrameLayout f = new FrameLayout(this);
        View b1 = new View(this);
        b1.setBackground(round(blend(FIELD, tint, 0.35f), 14));
        b1.setRotation(-8f);
        f.addView(b1, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        View b2 = new View(this);
        b2.setBackground(round(blend(FIELD, tint, 0.18f), 14));
        b2.setRotation(6f);
        f.addView(b2, new FrameLayout.LayoutParams(dp(66), dp(66), Gravity.CENTER));
        cover.setElevation(dp(3));
        f.addView(cover, new FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER));
        return f;
    }

    private void addFavCard() {
        ArrayList<Library.Item> favs = lib.inAlbum(Library.FAV);
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(12), dp(9));
        GradientDrawable fbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{blend(PANEL, 0xFFE8527E, 0.3f), blend(PANEL, 0xFFE8527E, 0.08f)});
        fbg.setCornerRadius(dp(20));
        card.setBackground(fbg);
        LinearLayout.LayoutParams cp = params(-1, -2);
        cp.bottomMargin = dp(8);
        albumListBox.addView(card, cp);
        View cover;
        if (favs.isEmpty()) {
            TextView ph = tv("♥", 28, 0xFFE8527E, false);
            ph.setGravity(Gravity.CENTER);
            ph.setBackground(round(FIELD, 10));
            cover = ph;
        } else cover = makeThumb(favs.get(0));
        card.addView(stack(cover, 0xFFE8527E), new LinearLayout.LayoutParams(dp(86), dp(84)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(4), 0);
        info.addView(tv("♥ " + t("favorites"), 15, FG, true));
        info.addView(tv(tf("items_n", favs.size()), 12, MUTED, false));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        card.setOnClickListener(v -> {
            if (!albumSelA.isEmpty()) return;
            albumSelI.clear();
            albumOpen = Library.FAV;
            animGrid = true;
            renderAlbums();
        });
    }

    private void albumMenu() {
        final Library.Album a = lib.albumById(albumOpen);
        if (a == null) return;
        final String aid = a.id;
        ArrayList<Act> acts = new ArrayList<>();
        acts.add(new Act("✎", t("rename"), t("rename_album_d"), 0, () -> renameAlbumById(aid)));
        if (lib.inAlbum(aid).size() > 1) acts.add(new Act("⇅", t("reorder"), t("reorder_d"), 0, () -> openReorder(aid)));
        acts.add(new Act("▣", t("del_album"), t("albums_only_d"), RED, () -> deleteAlbums(new ArrayList<>(Collections.singletonList(aid)), false)));
        acts.add(new Act("🗑", t("albums_files"), t("albums_files_d"), RED, () -> deleteAlbums(new ArrayList<>(Collections.singletonList(aid)), true)));
        sheet(a.name, tf("items_n", lib.inAlbum(aid).size()), acts.toArray(new Act[0]));
    }

    private void renameAlbum() { renameAlbumById(albumOpen); }

    private void renameAlbumById(String id) {
        final Library.Album a = lib.albumById(id);
        if (a == null) return;
        promptText(t("rename"), a.name, t("album_name"), name -> {
            if (!name.isEmpty()) { a.name = name; lib.save(); renderAlbums(); }
        });
    }

    private void deleteAlbumAsk() { albumMenu(); }

    // Перетаскивание файлов в альбоме (ручной порядок)
    private void openReorder(final String albumId) {
        final Library.Album al = lib.albumById(albumId);
        if (al == null) return;
        final ArrayList<Library.Item> its = lib.inAlbum(albumId);
        final ArrayList<String> ids = new ArrayList<>();
        for (Library.Item it : its) ids.add(it.id);
        final HashMap<String, Library.Item> byId = new HashMap<>();
        for (Library.Item it : its) byId.put(it.id, it);
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
        TextView ttl = tv(t("reorder"), 19, FG, true);
        ttl.setPadding(dp(8), 0, dp(8), 0);
        head.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = primaryButton(t("save"));
        save.setBackground(accentBg(20));
        save.setPadding(dp(18), 0, dp(18), 0);
        head.addView(save, new LinearLayout.LayoutParams(-2, dp(40)));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = tv(t("reorder_hint"), 12, MUTED, false);
        hint.setPadding(dp(16), dp(2), dp(16), dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        final ScrollView sv = new ScrollView(this);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), dp(16));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        final int H = dp(72), M = dp(8), STEP = H + M;
        final Runnable[] build = new Runnable[1];
        build[0] = () -> {
            box.removeAllViews();
            for (int i = 0; i < ids.size(); i++) {
                final Library.Item it = byId.get(ids.get(i));
                final LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(8), dp(4), dp(8));
                row.setBackground(panel(14));
                row.addView(makeThumb(it), new LinearLayout.LayoutParams(dp(56), dp(56)));
                TextView nm = tv((i + 1) + ".  " + it.name, 14, FG, true);
                nm.setSingleLine(true);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                nm.setPadding(dp(10), 0, dp(6), 0);
                row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1));
                final TextView handle = tv("≡", 26, MUTED, true);
                handle.setGravity(Gravity.CENTER);
                row.addView(handle, new LinearLayout.LayoutParams(dp(56), -1));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, H);
                rp.bottomMargin = M;
                box.addView(row, rp);
                final int startIdx = i;
                final float[] st = new float[3];   // стартовый rawY, стартовый scrollY, последний rawY
                final boolean[] drag = {false};
                final Runnable[] auto = new Runnable[1];
                final Runnable apply = () -> {
                    float dy = (st[2] - st[0]) + (sv.getScrollY() - st[1]);
                    dy = Math.max(-startIdx * STEP, Math.min((ids.size() - 1 - startIdx) * STEP, dy));
                    row.setTranslationY(dy);
                    int target = Math.max(0, Math.min(ids.size() - 1, startIdx + Math.round(dy / STEP)));
                    for (int j = 0; j < box.getChildCount(); j++) {
                        if (j == startIdx) continue;
                        float shift = 0;
                        if (startIdx < target && j > startIdx && j <= target) shift = -STEP;
                        else if (startIdx > target && j >= target && j < startIdx) shift = STEP;
                        box.getChildAt(j).animate().translationY(shift).setDuration(120).start();
                    }
                };
                auto[0] = () -> {
                    if (!drag[0]) return;
                    int[] loc = new int[2];
                    sv.getLocationOnScreen(loc);
                    float top = loc[1], bot = loc[1] + sv.getHeight();
                    if (st[2] < top + dp(90)) sv.scrollBy(0, -dp(10));
                    else if (st[2] > bot - dp(90)) sv.scrollBy(0, dp(10));
                    else { ui.postDelayed(auto[0], 16); return; }
                    apply.run();
                    ui.postDelayed(auto[0], 16);
                };
                handle.setOnTouchListener((hv, e) -> {
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        drag[0] = true;
                        st[0] = e.getRawY(); st[1] = sv.getScrollY(); st[2] = e.getRawY();
                        row.setElevation(dp(8));
                        row.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
                        sv.requestDisallowInterceptTouchEvent(true);
                        ui.post(auto[0]);
                        return true;
                    } else if (a == MotionEvent.ACTION_MOVE && drag[0]) {
                        st[2] = e.getRawY();
                        apply.run();
                        return true;
                    } else if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && drag[0]) {
                        drag[0] = false;
                        sv.requestDisallowInterceptTouchEvent(false);
                        float dy = row.getTranslationY();
                        final int target = Math.max(0, Math.min(ids.size() - 1, startIdx + Math.round(dy / STEP)));
                        row.animate().translationY((target - startIdx) * STEP).scaleX(1f).scaleY(1f).setDuration(150).withEndAction(() -> {
                            String moved = ids.remove(startIdx);
                            ids.add(target, moved);
                            build[0].run();
                        }).start();
                        return true;
                    }
                    return false;
                });
            }
        };
        build[0].run();
        save.setOnClickListener(v -> {
            al.order.clear();
            al.order.addAll(ids);
            lib.save();
            d.dismiss();
            refreshAll();
        });
        d.setContentView(root);
        animWin(d, R.style.OmvSheetAnim);
        openDialogs.add(d);
        d.show();
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
        upHeader.addView(headline(t("tab_upload"), 30), new LinearLayout.LayoutParams(0, -2, 1));
        trashBtn = button(t("trash"));
        trashBtn.setPadding(dp(14), 0, dp(14), 0);
        startIcon(trashBtn, R.drawable.ic_delete, FG);
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
        upButtons.setOrientation(LinearLayout.VERTICAL);
        upButtons.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.setBackground(accentBg(24));
        glow(hero, ACCENT, 8);
        ImageView hic = new ImageView(this);
        hic.setImageResource(R.drawable.nav_upload);
        hic.setColorFilter(ONACC);
        hic.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable hcg = new GradientDrawable();
        hcg.setShape(GradientDrawable.OVAL);
        hcg.setColor((ONACC & 0x00FFFFFF) | 0x2E000000);
        hic.setBackground(hcg);
        hero.addView(hic, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout htx = new LinearLayout(this);
        htx.setOrientation(LinearLayout.VERTICAL);
        htx.setPadding(dp(14), 0, 0, 0);
        TextView ht = tv(t("hero_add"), 17, ONACC, true);
        ht.setTypeface(dispFace());
        htx.addView(ht);
        TextView hs = tv(t("hero_add_sub"), 12.5f, (ONACC & 0x00FFFFFF) | 0xCC000000, false);
        hs.setPadding(0, dp(2), 0, 0);
        htx.addView(hs);
        hero.addView(htx, new LinearLayout.LayoutParams(0, -2, 1));
        hero.setOnClickListener(v -> pickMedia());
        pressFx(hero);
        upButtons.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row2 = new LinearLayout(this);
        int[] icRes = {R.drawable.ic_archive, R.drawable.ic_book};
        String[] lbls = {t("add_zip"), t("add_manga")};
        for (int k = 0; k < 2; k++) {
            final int kk = k;
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(14), 0, dp(10), 0);
            tile.setBackground(panel(18));
            ImageView ti = new ImageView(this);
            ti.setImageResource(icRes[k]);
            ti.setColorFilter(ACCENT);
            tile.addView(ti, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView tl = tv(lbls[k], 14, FG, true);
            tl.setSingleLine(true);
            tl.setEllipsize(TextUtils.TruncateAt.END);
            tl.setPadding(dp(10), 0, 0, 0);
            tile.addView(tl, new LinearLayout.LayoutParams(0, -2, 1));
            tile.setOnClickListener(v -> { if (kk == 0) pickZip(); else pickPdf(); });
            pressFx(tile);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(54), 1);
            if (k == 1) tp.setMarginStart(dp(10));
            row2.addView(tile, tp);
        }
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = dp(10);
        upButtons.addView(row2, r2p);
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
        fadeVis(selBar, mode);
        fadeVis(selActions, mode);
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
            card.setBackground(picked ? round(blend(PANEL, ACCENT, 0.3f), 20) : panel(20));
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
            name.setOnClickListener(v -> {
                if (!sel.isEmpty()) { if (!sel.remove(it.id)) sel.add(it.id); renderUpload(); }
                else renameItem(it, null);
            });
            details.addView(name);
            String kind = Library.isPdf(it.name) ? t("kind_manga") : Library.isVideo(it.name) ? t("kind_video") : t("kind_photo");
            ArrayList<String> anames = new ArrayList<>();
            for (String aid : Library.albumIdsOf(it)) { Library.Album al = lib.albumById(aid); if (al != null) anames.add(al.name); }
            TextView meta = tv(kind + " · " + fmtSize(new File(it.path).length()) + (anames.isEmpty() ? "" : " · " + TextUtils.join(", ", anames)), 11, ACCENT, false);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(meta);
            List<String> ts = Library.tagsOf(it);
            LinearLayout.LayoutParams tp = params(-1, -2);
            tp.topMargin = dp(4);
            if (ts.isEmpty()) details.addView(tv(t("tap_add_tags"), 12, MUTED, false), tp);
            else {
                Flow tagFlow = new Flow(this, dp(4), dp(4)).rows(2);
                for (String s : ts) tagFlow.addView(tagPill(s, true));
                details.addView(tagFlow, tp);
            }
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
        fxTree(listBox);
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

    // Поле тегов с подсказками: уже существующие теги предлагаются по набранному тексту
    private LinearLayout tagBox(final EditText input) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        final TreeMap<String, Integer> counts = allTagCounts();
        if (counts.isEmpty()) return box;
        TextView lbl = tv(t("tag_suggest"), 11.5f, MUTED, true);
        lbl.setPadding(0, dp(10), 0, dp(6));
        box.addView(lbl);
        final Flow sugg = new Flow(this, dp(6), dp(6)).rows(3);
        box.addView(sugg, new LinearLayout.LayoutParams(-1, -2));
        final Runnable[] upd = new Runnable[1];
        upd[0] = () -> {
            sugg.removeAllViews();
            String txt = input.getText().toString();
            int cut = Math.max(Math.max(txt.lastIndexOf(','), txt.lastIndexOf(';')), txt.lastIndexOf('\n'));
            final String head = txt.substring(0, cut + 1);
            String token = txt.substring(cut + 1).trim().toLowerCase(Locale.ROOT);
            while (token.startsWith("#")) token = token.substring(1).trim();
            List<String> have = Library.parseTags(head);
            ArrayList<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
            Collections.sort(es, (x, y) -> y.getValue() - x.getValue());
            int shown = 0;
            for (Map.Entry<String, Integer> en : es) {
                final String tag = en.getKey();
                if (have.contains(tag) || tag.equals(token) || (!token.isEmpty() && !tag.contains(token))) continue;
                TextView c = tagPill(tag, false);
                c.setOnClickListener(v -> {
                    String base = head.replaceAll("\\s+$", "");
                    String nt = (base.isEmpty() ? "" : base + " ") + tag + ", ";
                    input.setText(nt);
                    input.setSelection(nt.length());
                });
                pressFx(c);
                sugg.addView(c);
                if (++shown >= 14) break;
            }
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { upd[0].run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        upd[0].run();
        return box;
    }

    private void editTags(final Library.Item it) { editTags(it, null); }

    private void editTags(final Library.Item it, final Runnable after) {
        final EditText input = tagInput(it.tags);
        LinearLayout box = tagBox(input);
        showDlg(dlg().setTitle(t("act_tags")).setMessage(it.name).setView(box)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("save"), (d, w) -> {
                    it.tags = Library.joinTags(Library.parseTags(input.getText().toString()));
                    lib.save();
                    refreshAll();
                    if (after != null) after.run();
                }));
    }

    private void bulkTags() {
        final ArrayList<Library.Item> targets = selectedItems();
        if (targets.isEmpty()) return;
        final EditText input = tagInput("");
        LinearLayout box = tagBox(input);
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
        trashUndo(targets);
    }

    private void finishAlbumMove(int n) {
        lib.save();
        sel.clear(); albumSelI.clear(); feedSel.clear();
        refreshAll();
        toast(tf("moved_album", n));
    }

    private void chooseAlbum(final ArrayList<Library.Item> targets) {
        if (targets.isEmpty()) return;
        final int n = targets.size();
        ArrayList<Act> acts = new ArrayList<>();
        acts.add(new Act("＋", t("new_album"), null, 0, () -> promptText(t("new_album"), "", t("album_name"), name -> {
            if (name.isEmpty()) return;
            Library.Album a = lib.createAlbum(name);
            for (Library.Item it : targets) Library.addAlb(it, a.id);
            finishAlbumMove(n);
        })));
        for (final Library.Album al : new ArrayList<>(lib.albums)) {
            acts.add(new Act("▣", al.name, tf("items_n", lib.inAlbum(al.id).size()), 0, () -> {
                for (Library.Item it : targets) Library.addAlb(it, al.id);
                finishAlbumMove(n);
            }));
        }
        acts.add(new Act("✕", t("no_album"), null, RED, () -> {
            for (Library.Item it : targets) it.album = "";
            finishAlbumMove(n);
        }));
        sheet(t("pick_album"), tf("sel_n", n), acts.toArray(new Act[0]));
    }

    // Переименование файла: расширение сохраняется (по нему определяется тип)
    private void renameItem(final Library.Item it, final Runnable after) {
        int dot = it.name.lastIndexOf('.');
        final String ext = dot > 0 ? it.name.substring(dot) : "";
        String base = dot > 0 ? it.name.substring(0, dot) : it.name;
        promptText(t("rename"), base, t("file_name"), name -> {
            if (name.isEmpty()) return;
            it.name = name + ext;
            lib.save();
            refreshAll();
            if (after != null) after.run();
        });
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
        TextView title = headline(t("trash"), 24);
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
                card.setBackground(panel(14));
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
                int tdays = prefs.getInt("trash_days", 30);
                if (tdays > 0 && it.trashedAt > 0) {
                    int left = Math.max(0, tdays - (int) ((System.currentTimeMillis() - it.trashedAt) / 86400000L));
                    info.addView(tv(tf("trash_left", left), 11.5f, left <= 3 ? RED : MUTED, false));
                }
                LinearLayout btns = new LinearLayout(this);
                TextView re = button(t("restore")), de = button(t("del_forever"));
                re.setTextSize(13);
                de.setTextSize(13);
                re.setSingleLine(true);
                de.setSingleLine(true);
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
            fxTree(box);
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
        animWin(d, R.style.OmvSheetAnim);
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

    private TextView topBtn(String glyph, float size) {
        TextView b = tv(glyph, size, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(TAG_GREEN);
        g.setStroke(dp(1), 0x55FFFFFF);
        b.setBackground(g);
        b.setElevation(dp(4));
        pressFx(b);
        return b;
    }

    private TextView circleBtn(String glyph, float size) {
        TextView b = tv(glyph, size, 0xFFFFFFFF, false);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x8C000000);
        b.setBackground(g);
        pressFx(b);
        return b;
    }

    private boolean hits(View v, MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        Rect r = new Rect();
        if (!v.getGlobalVisibleRect(r)) return false;
        return r.contains((int) e.getRawX(), (int) e.getRawY());
    }

    private Rect zoomFrom = null;

    private void openViewer(final ArrayList<Library.Item> list, int start) {
        if (list.isEmpty()) return;
        final int[] idx = {start};
        final int[] token = {0};
        final Runnable[] show = new Runnable[1];
        final Runnable[] pageShow = new Runnable[1];
        final IntCb[] stepPage = new IntCb[1];
        final MediaController[] mcRef = new MediaController[1];
        final ZoomImageView[] zoomRef = new ZoomImageView[1];
        final VideoView[] vvRef = new VideoView[1];
        final ScrollView[] vsRef = new ScrollView[1];
        final int[][] topsRef = new int[1][];
        final String[] curId = {null};
        final boolean[] pdfMode = {false};
        final boolean[] videoMode = {false};
        final int[] mmode = {Math.max(0, Math.min(2, prefs.getInt("mmode", 0)))};   // 0 слева направо, 1 справа налево, 2 вертикальная лента
        final View[] chrome = new View[4];   // верх, низ, стрелка назад, стрелка вперёд
        final boolean[] tagsOpen = {false};
        final Runnable[] tagsUpd = new Runnable[1];
        final Runnable[] saveVp = new Runnable[1];
        final int[] slide = {0};
        final View[] tagsRef = new View[1];
        final TextView[] flashRef = new TextView[1];
        final PdfHolder pdf = new PdfHolder();
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dy) > dp(60) && Math.abs(dy) > Math.abs(dx) * 1.5f && hits(chrome[1], e1) && !hits(tagsRef[0], e1)) {
                    tagsOpen[0] = dy < 0;   // свайп вверх по нижней панели поднимает теги, вниз — опускает
                    tagsUpd[0].run();
                    return true;
                }
                if (zoomRef[0] != null && zoomRef[0].isZoomed()) return false;
                if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    slide[0] = dx < 0 ? 1 : -1;
                    if (pdfMode[0] && mmode[0] != 2) {
                        boolean rtl = mmode[0] == 1;
                        stepPage[0].on(((dx < 0) != rtl) ? 1 : -1);
                        return true;
                    }
                    if (list.size() < 2) return false;
                    idx[0] = (idx[0] + (dx < 0 ? 1 : list.size() - 1)) % list.size();
                    show[0].run();
                    return true;
                }
                return false;
            }
            // Двойное касание по видео: слева −10 с, справа +10 с
            @Override public boolean onDoubleTap(MotionEvent e) {
                VideoView vv = vvRef[0];
                if (!videoMode[0] || vv == null) return false;
                for (View c : chrome) if (hits(c, e)) return false;
                int[] loc = new int[2];
                vv.getLocationOnScreen(loc);
                if (e.getRawY() > loc[1] + vv.getHeight() * 0.8f) return false;
                boolean right = e.getRawX() > loc[0] + vv.getWidth() / 2f;
                try {
                    int dur = vv.getDuration();
                    int np = vv.getCurrentPosition() + (right ? 10000 : -10000);
                    np = Math.max(0, dur > 0 ? Math.min(dur - 300, np) : np);
                    vv.seekTo(np);
                } catch (Exception ignored) { }
                TextView fl = flashRef[0];
                if (fl != null) {
                    fl.setText(right ? "⏩  +10" : "−10  ⏪");
                    fl.animate().cancel();
                    fl.setAlpha(1f);
                    fl.animate().alpha(0f).setStartDelay(500).setDuration(350).start();
                }
                return true;
            }

            // Одиночное касание по картинке прячет и показывает панели
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (videoMode[0]) return false;
                for (View c : chrome) if (hits(c, e)) return false;
                boolean showIt = chrome[0].getVisibility() != View.VISIBLE;
                fadeVis(chrome[0], showIt);
                fadeVis(chrome[1], showIt);
                if (list.size() > 1) { fadeVis(chrome[2], showIt); fadeVis(chrome[3], showIt); }
                return true;
            }
        });
        final View[] dragRefs = new View[2];   // media, root
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            float x0, y0;
            boolean cand, drag;

            @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                gd.onTouchEvent(ev);
                if (dragTouch(ev)) return true;
                return super.dispatchTouchEvent(ev);
            }

            // Свайп вниз по фото закрывает просмотр
            private boolean dragTouch(MotionEvent ev) {
                View m = dragRefs[0], root = dragRefs[1];
                if (m == null || root == null) return false;
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    x0 = ev.getRawX(); y0 = ev.getRawY(); drag = false;
                    cand = !(zoomRef[0] != null && zoomRef[0].isZoomed()) && vsRef[0] == null && !hits(chrome[0], ev) && !hits(chrome[1], ev);
                    return false;
                }
                if (a == MotionEvent.ACTION_POINTER_DOWN) { cand = false; if (!drag) return false; }
                float dy = ev.getRawY() - y0, dx = ev.getRawX() - x0;
                float h = Math.max(1, root.getHeight());
                if (a == MotionEvent.ACTION_MOVE && cand && !drag && dy > dp(26) && dy > Math.abs(dx) * 1.7f) {
                    drag = true;
                    MotionEvent c = MotionEvent.obtain(ev);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c);
                    c.recycle();
                }
                if (!drag) return false;
                if (a == MotionEvent.ACTION_MOVE) {
                    float d = Math.max(0, dy);
                    float p = Math.min(1f, d / (h * 0.55f));
                    m.setTranslationY(d);
                    m.setTranslationX(dx * 0.4f);
                    m.setScaleX(1f - p * 0.22f);
                    m.setScaleY(1f - p * 0.22f);
                    if (root.getBackground() != null) root.getBackground().setAlpha((int) (255 * (1f - p * 0.85f)));
                    for (View c : chrome) if (c != null) c.setAlpha(Math.max(0f, 1f - p * 3f));
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    drag = false;
                    if (dy > dp(130)) {
                        m.animate().translationY(h).alpha(0f).setDuration(200).withEndAction(this::dismiss).start();
                    } else {
                        m.animate().translationY(0f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new OvershootInterpolator(1.6f)).start();
                        if (root.getBackground() != null) root.getBackground().setAlpha(255);
                        for (View c : chrome) if (c != null) c.animate().alpha(1f).setDuration(200).start();
                    }
                }
                return true;
            }
        };
        final FrameLayout media = new FrameLayout(this);
        final FrameLayout rootF = new FrameLayout(this);
        dragRefs[0] = media;
        dragRefs[1] = rootF;
        rootF.setBackgroundColor(0xFF000000);
        rootF.addView(media, new FrameLayout.LayoutParams(-1, -1));
        final TextView flash = tv("", 22, 0xFFFFFFFF, true);
        flash.setPadding(dp(22), dp(12), dp(22), dp(12));
        flash.setBackground(round(0xB0000000, 28));
        flash.setAlpha(0f);
        flashRef[0] = flash;
        rootF.addView(flash, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // Верхняя панель: закрыть, счётчик, избранное, удалить
        final LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14), dp(12), dp(26));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        TextView close = topBtn("✕", 20);
        TextView del = topBtn("🗑", 20);
        final TextView favB = topBtn("♡", 24);
        final TextView counter = tv("", 13, 0xFFFFFFFF, true);
        counter.setGravity(Gravity.CENTER);
        counter.setPadding(dp(14), dp(7), dp(14), dp(7));
        counter.setBackground(round(TAG_GREEN, 22));
        top.addView(close, new LinearLayout.LayoutParams(dp(50), dp(50)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(counter, new LinearLayout.LayoutParams(-2, -2));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(dp(50), dp(50));
        fbp.setMarginEnd(dp(10));
        top.addView(favB, fbp);
        top.addView(del, new LinearLayout.LayoutParams(dp(50), dp(50)));
        rootF.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Стрелки по бокам
        TextView prev = circleBtn("‹", 26), next = circleBtn("›", 26);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.setMarginStart(dp(10));
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER_VERTICAL | Gravity.END);
        nlp.setMarginEnd(dp(10));
        rootF.addView(prev, plp);
        rootF.addView(next, nlp);

        // Нижняя панель: страницы PDF, имя, детали, окошко тегов
        final LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(18), dp(16), dp(18), dp(20));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(0xE6101114);
        float br = dp(24);
        bbg.setCornerRadii(new float[]{br, br, br, br, 0, 0, 0, 0});
        bottom.setBackground(bbg);

        final LinearLayout pdfBar = new LinearLayout(this);
        pdfBar.setGravity(Gravity.CENTER);
        TextView pPrev = viewerBtn("◀"), pNext = viewerBtn("▶");
        final TextView modeBtn = viewerBtn("→");
        final TextView pageTxt = tv("", 14, 0xFFF0F0F4, true);
        pageTxt.setGravity(Gravity.CENTER);
        pdfBar.addView(pPrev, new LinearLayout.LayoutParams(dp(56), dp(40)));
        pdfBar.addView(pageTxt, new LinearLayout.LayoutParams(dp(130), -2));
        pdfBar.addView(pNext, new LinearLayout.LayoutParams(dp(56), dp(40)));
        pdfBar.addView(modeBtn, new LinearLayout.LayoutParams(dp(56), dp(40)));
        pdfBar.setVisibility(View.GONE);
        bottom.addView(pdfBar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView nameTv = tv("", 15, 0xFFFFFFFF, true);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(nameTv, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView speedB = tv("1×", 13, 0xFFFFFFFF, true);
        speedB.setGravity(Gravity.CENTER);
        speedB.setPadding(dp(12), dp(6), dp(12), dp(6));
        speedB.setBackground(round(TAG_GREEN, 16));
        speedB.setVisibility(View.GONE);
        pressFx(speedB);
        final float[] speeds = {1f, 1.5f, 2f, 0.5f};
        final int[] speedI = {0};
        final MediaPlayer[] mpRef = new MediaPlayer[1];
        final Runnable applySpeed = () -> {
            try {
                VideoView vv0 = vvRef[0];
                if (mpRef[0] != null && vv0 != null && vv0.isPlaying()) {
                    PlaybackParams pp = mpRef[0].getPlaybackParams();
                    pp.setSpeed(speeds[speedI[0]]);
                    mpRef[0].setPlaybackParams(pp);
                }
            } catch (Exception ignored) { }
        };
        speedB.setOnClickListener(v -> {
            speedI[0] = (speedI[0] + 1) % speeds.length;
            float sp = speeds[speedI[0]];
            speedB.setText((sp == (int) sp ? String.valueOf((int) sp) : String.valueOf(sp)) + "×");
            applySpeed.run();
        });
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-2, -2);
        spp.setMarginStart(dp(8));
        nameRow.addView(speedB, spp);
        TextView renameB = circleBtn("✎", 13);
        LinearLayout.LayoutParams rbp = new LinearLayout.LayoutParams(dp(34), dp(34));
        rbp.setMarginStart(dp(8));
        nameRow.addView(renameB, rbp);
        bottom.addView(nameRow, new LinearLayout.LayoutParams(-1, -2));
        final TextView metaTv = tv("", 12, 0xFFB0B3BD, false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = dp(2);
        bottom.addView(metaTv, mlp);

        // Окошко тегов: ярлык поднимает/опускает список, карандаш открывает редактор
        final LinearLayout tagsHead = new LinearLayout(this);
        tagsHead.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tagsLabel = tv("", 13, 0xFFFFFFFF, true);
        tagsLabel.setPadding(dp(14), dp(8), dp(14), dp(8));
        tagsLabel.setBackground(round(0x33FFFFFF, 18));
        pressFx(tagsLabel);
        TextView pencil = circleBtn("✎", 15);
        tagsHead.addView(tagsLabel, new LinearLayout.LayoutParams(-2, -2));
        tagsHead.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        tagsHead.addView(pencil, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(-1, -2);
        thp.topMargin = dp(12);
        bottom.addView(tagsHead, thp);
        // Теги в прокручиваемом окошке: помещаются все, сколько бы их ни было
        final MaxHScroll tagsScroll = new MaxHScroll(this, (int) (getResources().getDisplayMetrics().heightPixels * 0.42f));
        tagsRef[0] = tagsScroll;
        final Flow tagsFlow = new Flow(this, dp(6), dp(6));
        tagsScroll.addView(tagsFlow, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tflp = new LinearLayout.LayoutParams(-1, -2);
        tflp.topMargin = dp(12);
        tagsScroll.setVisibility(View.GONE);
        bottom.addView(tagsScroll, tflp);
        final TextView tagsEmpty = tv(t("tap_add_tags"), 12, 0xFFB0B3BD, false);
        tagsEmpty.setVisibility(View.GONE);
        LinearLayout.LayoutParams tep = new LinearLayout.LayoutParams(-1, -2);
        tep.topMargin = dp(10);
        bottom.addView(tagsEmpty, tep);
        tagsUpd[0] = () -> {
            Library.Item cur = list.get(idx[0]);
            List<String> ts = Library.tagsOf(cur);
            tagsLabel.setText((tagsOpen[0] ? "▾ " : "▴ ") + t("act_tags") + (ts.isEmpty() ? "" : " · " + ts.size()));
            boolean showFlow = tagsOpen[0] && !ts.isEmpty();
            boolean showEmpty = tagsOpen[0] && ts.isEmpty();
            if ((tagsScroll.getVisibility() == View.VISIBLE) != showFlow || (tagsEmpty.getVisibility() == View.VISIBLE) != showEmpty) {
                if (bottom.isAttachedToWindow()) TransitionManager.beginDelayedTransition(bottom);
            }
            tagsFlow.removeAllViews();
            for (String s : ts) tagsFlow.addView(tagPill(s, false));
            tagsScroll.setVisibility(showFlow ? View.VISIBLE : View.GONE);
            tagsEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
            tagsScroll.scrollTo(0, 0);
        };
        tagsLabel.setOnClickListener(v -> { tagsOpen[0] = !tagsOpen[0]; tagsUpd[0].run(); });
        pencil.setOnClickListener(v -> editTags(list.get(idx[0]), () -> { tagsOpen[0] = true; tagsUpd[0].run(); }));
        renameB.setOnClickListener(v -> {
            final Library.Item cur = list.get(idx[0]);
            renameItem(cur, () -> nameTv.setText(cur.name));
        });
        nameTv.setOnClickListener(v -> {
            final Library.Item cur = list.get(idx[0]);
            renameItem(cur, () -> nameTv.setText(cur.name));
        });
        rootF.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        chrome[0] = top; chrome[1] = bottom; chrome[2] = prev; chrome[3] = next;
        bottom.addOnLayoutChangeListener((vw0, c1, c2, c3, c4, c5, c6, c7, c8) -> {
            if (videoMode[0] && bottom.getVisibility() == View.VISIBLE) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) media.getLayoutParams();
                if (lp != null && lp.bottomMargin != bottom.getHeight()) { lp.bottomMargin = bottom.getHeight(); media.setLayoutParams(lp); }
            }
        });

        final Runnable updFav = () -> {
            Library.Item cur = list.get(idx[0]);
            favB.setText(cur.fav ? "♥" : "♡");
            favB.setTextColor(cur.fav ? 0xFFFFD0E0 : 0xFFFFFFFF);
        };
        favB.setOnClickListener(v -> {
            buzz(v, HapticFeedbackConstants.KEYBOARD_TAP);
            Library.Item cur = list.get(idx[0]);
            cur.fav = !cur.fav;
            lib.save();
            updFav.run();
            favB.animate().cancel();
            favB.animate().scaleX(1.4f).scaleY(1.4f).setDuration(120).withEndAction(() -> favB.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator(3f)).start()).start();
            refreshAll();
        });

        close.setOnClickListener(v -> dialog.dismiss());
        prev.setOnClickListener(v -> { slide[0] = -1; idx[0] = (idx[0] + list.size() - 1) % list.size(); show[0].run(); });
        next.setOnClickListener(v -> { slide[0] = 1; idx[0] = (idx[0] + 1) % list.size(); show[0].run(); });
        stepPage[0] = dlt -> {
            int np = pdf.page + dlt;
            if (np < 0 || np >= pdf.pages) return;
            if (mmode[0] == 2) {
                if (vsRef[0] != null && topsRef[0] != null && np < topsRef[0].length) vsRef[0].smoothScrollTo(0, topsRef[0][np]);
            } else { pdf.page = np; pageShow[0].run(); }
        };
        pPrev.setOnClickListener(v -> stepPage[0].on(mmode[0] == 1 ? 1 : -1));
        pNext.setOnClickListener(v -> stepPage[0].on(mmode[0] == 1 ? -1 : 1));
        final String[] MG = {"→", "←", "↕"};
        modeBtn.setOnClickListener(v -> {
            mmode[0] = (mmode[0] + 1) % 3;
            prefs.edit().putInt("mmode", mmode[0]).apply();
            toast(t("mmode_" + mmode[0]));
            slide[0] = 0;
            show[0].run();
        });
        // Удаление прямо из просмотра: файл уходит в корзину, показывается следующий
        del.setOnClickListener(v -> {
            final int i = idx[0];
            Library.Item gone = list.get(i);
            lib.moveToTrash(gone);
            lib.save();
            list.remove(i);
            refreshAll();
            toast(tf("moved_trash", 1));
            if (list.isEmpty()) { dialog.dismiss(); return; }
            if (idx[0] >= list.size()) idx[0] = list.size() - 1;
            media.animate().cancel();
            media.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(140).withEndAction(() -> {
                slide[0] = 0;
                show[0].run();
            }).start();
        });

        // позиция видео запоминается и восстанавливается
        saveVp[0] = () -> {
            VideoView vv = vvRef[0];
            if (vv == null || curId[0] == null || !videoMode[0]) return;
            try {
                int pos = vv.getCurrentPosition(), dur = vv.getDuration();
                SharedPreferences.Editor ed = prefs.edit();
                if (dur > 0 && pos > 3000 && pos < dur - 3000) ed.putInt("vp_" + curId[0], pos); else ed.remove("vp_" + curId[0]);
                ed.apply();
            } catch (Exception ignored) { }
        };

        pageShow[0] = () -> {
            final int pg = pdf.page;
            final int my = token[0];
            final String pid = curId[0];
            final ImageView target = zoomRef[0];
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
                        if (pid != null) prefs.edit().putInt("pg_" + pid, pg).apply();
                    }
                });
            });
        };

        show[0] = () -> {
            saveVp[0].run();
            token[0]++;
            final int my = token[0];
            media.animate().cancel();
            media.setScaleX(1f);
            media.setScaleY(1f);
            media.setAlpha(0f);
            media.setTranslationX(slide[0] * dp(48));
            media.animate().alpha(1f).translationX(0f).setDuration(260).start();
            slide[0] = 0;
            if (mcRef[0] != null) mcRef[0].hide();
            stopMedia(media);
            media.removeAllViews();
            closePdf(pdf);
            zoomRef[0] = null;
            vvRef[0] = null;
            vsRef[0] = null;
            topsRef[0] = null;
            pdfMode[0] = false;
            videoMode[0] = false;
            pdfBar.setVisibility(View.GONE);
            speedB.setVisibility(View.GONE);
            speedI[0] = 0;
            speedB.setText("1×");
            mpRef[0] = null;
            final Library.Item it = list.get(idx[0]);
            curId[0] = it.id;
            prefs.edit().putString("last_id", it.id).apply();
            counter.setText((idx[0] + 1) + " / " + list.size());
            nameTv.setText(it.name);
            updFav.run();
            File f0 = new File(it.path);
            if (it.enc) {
                File pc = plainCached(it);
                if (pc == null) {
                    metaTv.setText("🔒 …");
                    pool.execute(() -> {
                        final File pf = plainFile(it);
                        ui.post(() -> {
                            if (my != token[0]) return;
                            if (pf != null) show[0].run(); else toast(t("integrity_bad"));
                        });
                    });
                    return;
                }
                f0 = pc;
            }
            final File f = f0;
            String kind = Library.isPdf(it.name) ? t("kind_manga") : Library.isVideo(it.name) ? t("kind_video") : t("kind_photo");
            metaTv.setText((it.enc ? "🔒 " : "") + kind + " · " + fmtSize(f.length()) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale(lang)).format(new Date(it.added)));
            tagsUpd[0].run();
            int arrows = list.size() > 1 && top.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE;
            prev.setVisibility(arrows);
            next.setVisibility(arrows);
            if (!f.exists()) { toast(t("missing")); return; }
            final FrameLayout.LayoutParams mlp2 = new FrameLayout.LayoutParams(-1, -1);
            if (Library.isVideo(it.name)) {
                videoMode[0] = true;
                top.setVisibility(View.VISIBLE);
                bottom.setVisibility(View.VISIBLE);
                final VideoView vv = new VideoView(this);
                MediaController mc = new MediaController(this);
                mc.setAnchorView(vv);
                vv.setMediaController(mc);
                mcRef[0] = mc;
                vvRef[0] = vv;
                final int resume = prefs.getInt("vp_" + it.id, 0);
                vv.setOnPreparedListener(mp -> { mpRef[0] = mp; if (my == token[0] && resume > 0) vv.seekTo(resume); });
                vv.setOnCompletionListener(mp -> prefs.edit().remove("vp_" + it.id).apply());
                vv.setVideoPath(f.getAbsolutePath());
                media.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                // видео и его кнопки управления остаются над нижней панелью
                bottom.post(() -> {
                    if (my != token[0]) return;
                    mlp2.bottomMargin = bottom.getHeight();
                    media.setLayoutParams(mlp2);
                });
                vv.start();
                speedB.setVisibility(View.VISIBLE);
            } else if (Library.isPdf(it.name)) {
                media.setLayoutParams(mlp2);
                pdfMode[0] = true;
                pdf.page = Math.max(0, prefs.getInt("pg_" + it.id, 0));
                pdfBar.setVisibility(View.VISIBLE);
                pageTxt.setText("…");
                modeBtn.setText(MG[mmode[0]]);
                final boolean vertical = mmode[0] == 2;
                final int startPage0 = pdf.page;
                pdfExec.execute(() -> {
                    float[] ratio = null;
                    try {
                        pdf.pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                        pdf.r = new PdfRenderer(pdf.pfd);
                        pdf.pages = pdf.r.getPageCount();
                        if (pdf.page >= pdf.pages) pdf.page = Math.max(0, pdf.pages - 1);
                        if (vertical) {
                            ratio = new float[pdf.pages];
                            for (int pi = 0; pi < pdf.pages; pi++) {
                                PdfRenderer.Page p = pdf.r.openPage(pi);
                                ratio[pi] = p.getHeight() / (float) Math.max(1, p.getWidth());
                                p.close();
                            }
                        }
                    } catch (Exception e) {
                        ui.post(() -> toast(t("pdf_failed")));
                        return;
                    }
                    final float[] fr = ratio;
                    ui.post(() -> {
                        if (my != token[0]) return;
                        if (!vertical) {
                            final ZoomImageView iv = new ZoomImageView(this);
                            media.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                            zoomRef[0] = iv;
                            pageShow[0].run();
                            return;
                        }
                        buildVerticalPdf(media, pdf, fr, my, token, it.id, pageTxt, vsRef, topsRef, startPage0);
                    });
                });
            } else {
                media.setLayoutParams(mlp2);
                final ZoomImageView iv = new ZoomImageView(this);
                media.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                zoomRef[0] = iv;
                pool.execute(() -> {
                    final Bitmap b = decodeSampled(f.getAbsolutePath(), 2048);
                    ui.post(() -> {
                        if (my == token[0]) { if (b != null) iv.setImageBitmap(b); else toast(t("unsupported")); }
                    });
                });
            }
        };
        dialog.setContentView(rootF);
        animWin(dialog, R.style.OmvFadeAnim);
        fxTree(rootF);
        dialog.setOnDismissListener(d -> {
            saveVp[0].run();
            if (mcRef[0] != null) mcRef[0].hide();
            stopMedia(media);
            closePdf(pdf);
            cleanDec();
            refreshAll();
        });
        openDialogs.add(dialog);
        dialog.show();
        show[0].run();
        if (zoomFrom != null) {   // фото «вырастает» из плитки
            final Rect zr = zoomFrom;
            zoomFrom = null;
            float sw = getResources().getDisplayMetrics().widthPixels;
            float sc = Math.max(0.15f, Math.min(1f, zr.width() / sw));
            media.setPivotX(zr.exactCenterX());
            media.setPivotY(zr.exactCenterY());
            media.setScaleX(sc);
            media.setScaleY(sc);
            media.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(340).setInterpolator(new DecelerateInterpolator(2.2f))
                    .withEndAction(() -> { media.setPivotX(media.getWidth() / 2f); media.setPivotY(media.getHeight() / 2f); }).start();
        }
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!dialog.isShowing()) return;
            saveVp[0].run();
            ui.postDelayed(tick[0], 3000);
        };
        ui.postDelayed(tick[0], 3000);
    }

    // Режим «вертикальная лента» для манги: страницы рисуются по мере прокрутки
    private void buildVerticalPdf(FrameLayout media, final PdfHolder pdf, final float[] ratio, final int my, final int[] token,
                                  final String itemId, final TextView pageTxt, ScrollView[] vsRef, int[][] topsRef, int startPage) {
        final int n = ratio.length;
        if (n == 0) return;
        final ScrollView vs = new ScrollView(this);
        vs.setVerticalScrollBarEnabled(false);
        final LinearLayout vbox = new LinearLayout(this);
        vbox.setOrientation(LinearLayout.VERTICAL);
        final int w0 = getResources().getDisplayMetrics().widthPixels;
        final ImageView[] views = new ImageView[n];
        final boolean[] loaded = new boolean[n], pending = new boolean[n];
        final int[] tops = new int[n + 1];
        final int gap = dp(4);
        int y = 0;
        for (int i = 0; i < n; i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setBackgroundColor(0xFF1B1B1B);
            int h = Math.max(dp(40), (int) (w0 * ratio[i]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w0, h);
            lp.bottomMargin = gap;
            vbox.addView(iv, lp);
            views[i] = iv;
            tops[i] = y;
            y += h + gap;
        }
        tops[n] = y;
        vs.addView(vbox, new ViewGroup.LayoutParams(-1, -2));
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            if (my != token[0]) return;
            int sy = vs.getScrollY(), vh = Math.max(vs.getHeight(), dp(200));
            int cur = n - 1;
            for (int i = 0; i < n; i++) if (tops[i + 1] > sy + vh / 3) { cur = i; break; }
            for (int i = 0; i < n; i++) {
                boolean near = tops[i + 1] > sy - vh && tops[i] < sy + vh * 2;
                boolean far = tops[i + 1] < sy - vh * 3 || tops[i] > sy + vh * 4;
                if (near && !loaded[i] && !pending[i]) {
                    pending[i] = true;
                    final int pi = i;
                    pdfExec.execute(() -> {
                        Bitmap bmp = null;
                        if (my == token[0] && pdf.r != null && pi < pdf.pages) {
                            try {
                                PdfRenderer.Page p = pdf.r.openPage(pi);
                                int w = Math.min(w0, 1100);
                                int h = Math.max(1, (int) (w * ratio[pi]));
                                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                bmp.eraseColor(0xFFFFFFFF);
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                p.close();
                            } catch (Throwable ignored) { bmp = null; }
                        }
                        final Bitmap fb = bmp;
                        ui.post(() -> {
                            pending[pi] = false;
                            if (my == token[0] && fb != null) { views[pi].setImageBitmap(fb); loaded[pi] = true; }
                        });
                    });
                } else if (far && loaded[i]) {
                    views[i].setImageDrawable(null);
                    loaded[i] = false;
                }
            }
            if (cur != pdf.page) {
                pdf.page = cur;
                pageTxt.setText(tf("page_n", cur + 1, n));
                prefs.edit().putInt("pg_" + itemId, cur).apply();
            }
        };
        vs.setOnScrollChangeListener((v1, a1, a2, a3, a4) -> render[0].run());
        media.addView(vs, new FrameLayout.LayoutParams(-1, -1));
        vsRef[0] = vs;
        topsRef[0] = tops;
        final int sp = Math.max(0, Math.min(n - 1, startPage));
        pdf.page = sp;
        pageTxt.setText(tf("page_n", sp + 1, n));
        vs.post(() -> { vs.scrollTo(0, tops[sp]); render[0].run(); });
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

    private boolean hasDecoy() { return !prefs.getString("dpinhash", "").isEmpty(); }

    private boolean checkDecoy(String pin) {
        return hashPin(pin, prefs.getString("dpinsalt", "")).equals(prefs.getString("dpinhash", ""));
    }

    private void saveDecoy(String pin) {
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder salt = new StringBuilder();
        for (byte b : raw) salt.append(String.format("%02x", b));
        prefs.edit().putString("dpinsalt", salt.toString()).putString("dpinhash", hashPin(pin, salt.toString())).apply();
    }

    private void setDecoyFlow() {
        promptPin(t("pin_current"), null, cur -> {
            if (!checkPin(cur)) { toast(t("pin_wrong")); return; }
            promptPin(t("decoy_new"), t("decoy_note"), p1 -> {
                if (!p1.matches("\\d{4,8}")) { toast(t("pin_short")); return; }
                if (checkPin(p1)) { toast(t("decoy_same")); return; }
                promptPin(t("pin_repeat"), null, p2 -> {
                    if (!p1.equals(p2)) { toast(t("pin_mismatch")); return; }
                    saveDecoy(p1);
                    toast(t("decoy_saved"));
                    renderSettings();
                });
            });
        });
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
        lockView.addView(new Aurora(this, ACCENT, ACCENT2, darkTheme), new FrameLayout.LayoutParams(-1, -1));
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
        TextView lockIc = tv("🔒", 30, ONACC, false);
        lockIc.setGravity(Gravity.CENTER);
        GradientDrawable lig = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT2});
        lig.setShape(GradientDrawable.OVAL);
        lockIc.setBackground(lig);
        glow(lockIc, ACCENT, 12);
        LinearLayout.LayoutParams lip = new LinearLayout.LayoutParams(dp(84), dp(84));
        lip.gravity = Gravity.CENTER_HORIZONTAL;
        lip.bottomMargin = dp(8);
        col.addView(lockIc, lip);
        TextView title = tv(t("pin_enter"), 22, FG, true);
        title.setTypeface(dispFace());
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        pinDots = tv("", 28, ACCENT, false);
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
                TextView key = tv(k, k.equals("⌫") ? 22 : 26, k.equals("✓") ? ONACC : FG, true);
                key.setGravity(Gravity.CENTER);
                key.setBackground(k.equals("✓") ? accentBg(36) : k.equals("⌫") ? round(BTN, 36) : panel(36));
                if (TF_MED != null && !k.equals("✓") && !k.equals("⌫")) key.setTypeface(TF_MED);
                key.setOnClickListener(v -> pinKey(k));
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, dp(72), 1);
                kp.setMargins(dp(8), dp(7), dp(8), dp(7));
                row.addView(key, kp);
            }
            col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        lockView.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    }

    private void updateDots() {
        StringBuilder sb = new StringBuilder();
        int shown = Math.max(4, pinBuf.length());
        for (int i = 0; i < shown; i++) sb.append(i < pinBuf.length() ? "●  " : "○  ");
        pinDots.setText(sb.toString().trim());
        if (pinBuf.length() > 0) {
            pinDots.animate().cancel();
            pinDots.setScaleX(1.12f);
            pinDots.setScaleY(1.12f);
            pinDots.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new OvershootInterpolator(3f)).start();
        }
    }

    private void pinKey(String k) {
        buzz(lockView, HapticFeedbackConstants.KEYBOARD_TAP);
        if (k.equals("⌫")) {
            if (pinBuf.length() > 0) pinBuf.setLength(pinBuf.length() - 1);
            updateDots();
        } else if (k.equals("✓")) {
            long now = System.currentTimeMillis();
            if (now < pinBlockedUntil) { pinMsg.setText(tf("pin_blocked", (int) ((pinBlockedUntil - now) / 1000) + 1)); return; }
            String p = pinBuf.toString();
            pinBuf.setLength(0);
            updateDots();
            boolean realOk = checkPin(p);
            boolean fakeOk = !realOk && hasDecoy() && checkDecoy(p);
            if (realOk || fakeOk) {
                switchVault(fakeOk);
                authed = true;
                pinFails = 0;
                pinMsg.setText("");
                shell.setVisibility(View.VISIBLE);
                shell.setAlpha(0f);
                shell.animate().alpha(1f).setDuration(300).start();
                lockView.animate().alpha(0f).setDuration(260).withEndAction(() -> { lockView.setVisibility(View.GONE); lockView.setAlpha(1f); }).start();
            } else {
                pinFails++;
                if (pinFails >= 5) { pinFails = 0; pinBlockedUntil = now + 30000; pinMsg.setText(tf("pin_blocked", 30)); }
                else pinMsg.setText(t("pin_wrong"));
                buzz(lockView, HapticFeedbackConstants.LONG_PRESS);
                pinDots.animate().cancel();
                pinDots.setTranslationX(0f);
                pinDots.animate().translationX(dp(14)).setDuration(60).withEndAction(() ->
                        pinDots.animate().translationX(-dp(12)).setDuration(90).withEndAction(() ->
                                pinDots.animate().translationX(0f).setDuration(70).start()).start()).start();
            }
        } else if (pinBuf.length() < 8) {
            pinBuf.append(k);
            updateDots();
        }
    }

    private void showLock() {
        authed = false;
        cleanDec();
        for (Dialog d : new ArrayList<>(openDialogs)) { try { if (d.isShowing()) d.dismiss(); } catch (Exception ignored) { } }
        openDialogs.clear();
        pinBuf.setLength(0);
        updateDots();
        pinMsg.setText("");
        lockView.animate().cancel();
        lockView.setAlpha(1f);
        lockView.setVisibility(View.VISIBLE);
        lockView.bringToFront();
        shell.animate().cancel();
        shell.setVisibility(View.INVISIBLE);
    }

    private void promptPin(String title, String msg, final StrCb cb) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setSingleLine(true);
        styleEdit(in);
        in.setGravity(Gravity.CENTER);
        in.setTextSize(24);
        in.setLetterSpacing(0.35f);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in, params(-1, 56));
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
                prefs.edit().remove("pinhash").remove("pinsalt").remove("dpinhash").remove("dpinsalt").apply();
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

    // Раскладывает настройки по скруглённым карточкам: каждая секция — своя карточка
    private void groupCards() {
        ArrayList<View> kids = new ArrayList<>();
        for (int i = 0; i < setBox.getChildCount(); i++) kids.add(setBox.getChildAt(i));
        setBox.removeAllViews();
        LinearLayout card = null;
        for (View v : kids) {
            if ("sec".equals(v.getTag())) {
                setBox.addView(v, new LinearLayout.LayoutParams(-1, -2));
                card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(14), dp(10), dp(14), dp(14));
                card.setBackground(hasBg() ? round((PANEL & 0x00FFFFFF) | 0xE6000000, 22) : panel(22));
                setBox.addView(card, new LinearLayout.LayoutParams(-1, -2));
            } else if ("ver".equals(v.getTag()) || card == null) {
                setBox.addView(v);
            } else {
                card.addView(v);
            }
        }
    }

    private void renderSettings() {
        if (setBox == null || !visible(tabViews[3])) return;
        setBox.removeAllViews();
        setBox.addView(headline(t("tab_settings"), 30), new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("🌐", t("set_language")));
        Flow lf = new Flow(this, dp(8), dp(8));
        for (int i = 0; i < Strings.LANGS.length; i++) {
            final String code = Strings.LANGS[i];
            TextView c = chip(Strings.NAMES[i], code.equals(lang));
            c.setOnClickListener(v -> { prefs.edit().putString("lang", code).apply(); recreate(); });
            lf.addView(c);
        }
        setBox.addView(lf, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("🎨", t("set_appearance")));
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

        TextView iconLbl = tv(t("app_icon"), 15, FG, true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = dp(16);
        setBox.addView(iconLbl, ilp);
        final TextView autoIc = toggleChip(IconPlan.auto(this));
        autoIc.setOnClickListener(v -> {
            boolean on = !IconPlan.auto(this);
            prefs.edit().putBoolean("auto_icon", on).apply();
            if (on) IconPlan.applyAuto(this);
            IconPlan.schedule(this);
            renderSettings();
        });
        setBox.addView(settingRow(t("auto_icon_sw"), autoIc), new LinearLayout.LayoutParams(-1, -2));
        TextView autoNote = tv(t("auto_icon_note"), 11.5f, MUTED, false);
        autoNote.setPadding(0, 0, 0, dp(6));
        setBox.addView(autoNote, new LinearLayout.LayoutParams(-1, -2));
        Flow iconFlow = new Flow(this, dp(12), dp(10));
        int curI = curIcon();
        for (int i = 0; i < IconPlan.COUNT; i++) {
            final int ii = i;
            LinearLayout ic = new LinearLayout(this);
            ic.setOrientation(LinearLayout.VERTICAL);
            ic.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout frame = new FrameLayout(this);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(18));
            ring.setColor(0x00000000);
            if (i == curI) ring.setStroke(dp(3), ACCENT);
            frame.setBackground(ring);
            ImageView iv = new ImageView(this);
            iv.setImageResource(ICON_PREV[i]);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            frame.addView(iv, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
            ic.addView(frame, new LinearLayout.LayoutParams(dp(66), dp(66)));
            TextView cap = tv(t("icon_" + (i + 1)), 10.5f, i == curI ? ACCENT : MUTED, i == curI);
            cap.setGravity(Gravity.CENTER);
            cap.setSingleLine(true);
            ic.addView(cap, new LinearLayout.LayoutParams(dp(66), -2));
            pressFx(ic);
            ic.setOnClickListener(v -> { prefs.edit().putBoolean("auto_icon", false).apply(); IconPlan.schedule(this); setIcon(ii, false); renderSettings(); });
            iconFlow.addView(ic);
        }
        LinearLayout.LayoutParams ifp = new LinearLayout.LayoutParams(-1, -2);
        ifp.topMargin = dp(8);
        setBox.addView(iconFlow, ifp);

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

        final TextView ftChip = toggleChip(prefs.getBoolean("feed_tags", false));
        ftChip.setOnClickListener(v -> { prefs.edit().putBoolean("feed_tags", !prefs.getBoolean("feed_tags", false)).apply(); renderSettings(); });
        setBox.addView(settingRow(t("feed_tags_sw"), ftChip), new LinearLayout.LayoutParams(-1, -2));

        TextView namesChip = toggleChip(showNames);
        namesChip.setOnClickListener(v -> { showNames = !showNames; prefs.edit().putBoolean("names", showNames).apply(); renderSettings(); });
        setBox.addView(settingRow(t("set_names"), namesChip), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams bgl = new LinearLayout.LayoutParams(-1, -2);
        bgl.topMargin = dp(14);
        setBox.addView(tv(t("set_bg"), 15, FG, true), bgl);
        LinearLayout bgRow = new LinearLayout(this);
        TextView bgPick = button(t("bg_choose"));
        bgPick.setOnClickListener(v -> pickBg());
        bgRow.addView(bgPick, new LinearLayout.LayoutParams(0, dp(46), 1));
        final boolean hasBg = hasBg();
        if (hasBg) {
            TextView bgRm = button(t("bg_remove"));
            bgRm.setTextColor(0xFFFFFFFF);
            bgRm.setBackground(round(RED, 12));
            bgRm.setOnClickListener(v -> { bgFile().delete(); prefs.edit().remove("bgres").apply(); recreate(); });
            LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(0, dp(46), 1);
            brp.setMarginStart(dp(8));
            bgRow.addView(bgRm, brp);
        }
        LinearLayout.LayoutParams bgrp = new LinearLayout.LayoutParams(-1, -2);
        bgrp.topMargin = dp(8);
        setBox.addView(bgRow, bgrp);
        TextView presetLbl = tv(t("bg_presets"), 13, MUTED, true);
        presetLbl.setPadding(0, dp(12), 0, dp(6));
        setBox.addView(presetLbl, new LinearLayout.LayoutParams(-1, -2));
        Flow presets = new Flow(this, dp(8), dp(8));
        final int curRes = prefs.getInt("bgres", 0);
        for (int bi = 1; bi <= 20; bi++) {
            final int bn = bi;
            FrameLayout pf = new FrameLayout(this);
            final ImageView piv = new ImageView(this);
            piv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            piv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
            });
            piv.setClipToOutline(true);
            pf.addView(piv, new FrameLayout.LayoutParams(-1, -1));
            Bitmap cached = bgThumbs[bn];
            if (cached != null) piv.setImageBitmap(cached);
            else pool.execute(() -> {
                final Bitmap tb = decodeBgRes(bn, 4);
                ui.post(() -> { if (tb != null) { bgThumbs[bn] = tb; piv.setImageBitmap(tb); } });
            });
            if (bn == curRes) {
                View ring = new View(this);
                GradientDrawable rg = new GradientDrawable();
                rg.setCornerRadius(dp(12));
                rg.setStroke(dp(3), ACCENT);
                rg.setColor(0x00000000);
                ring.setBackground(rg);
                pf.addView(ring, new FrameLayout.LayoutParams(-1, -1));
                TextView ck = tv("✓", 12, ONACC, true);
                ck.setGravity(Gravity.CENTER);
                GradientDrawable og = new GradientDrawable();
                og.setShape(GradientDrawable.OVAL);
                og.setColor(ACCENT);
                ck.setBackground(og);
                FrameLayout.LayoutParams ckp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
                ckp.setMargins(0, dp(5), dp(5), 0);
                pf.addView(ck, ckp);
            }
            pf.setOnClickListener(v -> {
                bgFile().delete();
                prefs.edit().putInt("bgres", bn).apply();
                recreate();
            });
            presets.addView(pf, new ViewGroup.LayoutParams(dp(72), dp(104)));
        }
        setBox.addView(presets, new LinearLayout.LayoutParams(-1, -2));
        if (hasBg) {
            LinearLayout dimSt = new LinearLayout(this);
            dimSt.setGravity(Gravity.CENTER_VERTICAL);
            TextView dm = button("−"), dp2 = button("+");
            final TextView dv2 = tv(prefs.getInt("bgdim", 55) + "%", 15, FG, true);
            dv2.setGravity(Gravity.CENTER);
            dm.setOnClickListener(v -> { prefs.edit().putInt("bgdim", Math.max(0, prefs.getInt("bgdim", 55) - 10)).apply(); applyBg(); renderSettings(); });
            dp2.setOnClickListener(v -> { prefs.edit().putInt("bgdim", Math.min(90, prefs.getInt("bgdim", 55) + 10)).apply(); applyBg(); renderSettings(); });
            dimSt.addView(dm, new LinearLayout.LayoutParams(dp(44), dp(40)));
            dimSt.addView(dv2, new LinearLayout.LayoutParams(dp(56), -2));
            dimSt.addView(dp2, new LinearLayout.LayoutParams(dp(44), dp(40)));
            setBox.addView(settingRow(t("bg_dim"), dimSt), new LinearLayout.LayoutParams(-1, -2));
        }

        setBox.addView(sectionLabel("✨", t("set_brand")));
        TextView nameBtn = button("✎  " + appName());
        nameBtn.setSingleLine(true);
        nameBtn.setEllipsize(TextUtils.TruncateAt.END);
        nameBtn.setMaxWidth(dp(190));
        nameBtn.setPadding(dp(14), 0, dp(14), 0);
        nameBtn.setOnClickListener(v -> promptText(t("brand_name"), prefs.getString("app_name", ""), "Offline Media Vault", nm -> {
            prefs.edit().putString("app_name", nm).apply();
            applyTaskDesc();
            renderSettings();
        }));
        setBox.addView(settingRow(t("brand_name"), nameBtn), new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout icRow = new LinearLayout(this);
        icRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icPrev = new ImageView(this);
        icPrev.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icPrev.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
        });
        icPrev.setClipToOutline(true);
        if (iconFile().exists()) icPrev.setImageBitmap(BitmapFactory.decodeFile(iconFile().getAbsolutePath()));
        else icPrev.setImageResource(ICON_PREV[Math.min(curIcon(), ICON_PREV.length - 1)]);
        icRow.addView(icPrev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView pickIc = button(t("brand_icon_pick"));
        pickIc.setOnClickListener(v -> pickIcon());
        LinearLayout.LayoutParams pip = new LinearLayout.LayoutParams(0, dp(48), 1);
        pip.setMarginStart(dp(10));
        icRow.addView(pickIc, pip);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(-1, -2);
        icp.topMargin = dp(8);
        setBox.addView(icRow, icp);
        TextView shortB = primaryButton(t("brand_shortcut"));
        shortB.setOnClickListener(v -> pinShortcut());
        LinearLayout.LayoutParams sbp = params(-1, 46);
        sbp.topMargin = dp(8);
        setBox.addView(shortB, sbp);
        TextView brReset = button(t("brand_reset"));
        brReset.setOnClickListener(v -> {
            prefs.edit().remove("app_name").apply();
            iconFile().delete();
            applyTaskDesc();
            renderSettings();
        });
        LinearLayout.LayoutParams brp = params(-1, 44);
        brp.topMargin = dp(8);
        setBox.addView(brReset, brp);
        TextView brNote = tv(t("brand_note"), 11.5f, MUTED, false);
        brNote.setPadding(0, dp(8), 0, 0);
        setBox.addView(brNote, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("🔐", t("set_security")));
        if (!decoy) {
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
            warn.setPadding(0, dp(6), 0, dp(6));
            setBox.addView(warn, new LinearLayout.LayoutParams(-1, -2));
            if (hasPin()) {
                setBox.addView(settingRow(t("decoy_pin"), tv(hasDecoy() ? t("pin_on") : t("pin_off"), 14, hasDecoy() ? ACCENT : MUTED, true)), new LinearLayout.LayoutParams(-1, -2));
                LinearLayout dRow = new LinearLayout(this);
                TextView dSet = hasDecoy() ? button(t("pin_change")) : primaryButton(t("decoy_set"));
                dSet.setOnClickListener(v -> setDecoyFlow());
                dRow.addView(dSet, new LinearLayout.LayoutParams(0, dp(46), 1));
                if (hasDecoy()) {
                    TextView dRm = button(t("pin_remove"));
                    dRm.setTextColor(0xFFFFFFFF);
                    dRm.setBackground(round(RED, 12));
                    dRm.setOnClickListener(v -> { prefs.edit().remove("dpinhash").remove("dpinsalt").apply(); renderSettings(); });
                    LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(0, dp(46), 1);
                    drp.setMarginStart(dp(8));
                    dRow.addView(dRm, drp);
                }
                setBox.addView(dRow, new LinearLayout.LayoutParams(-1, -2));
                TextView dn = tv(t("decoy_note"), 11.5f, MUTED, false);
                dn.setPadding(0, dp(6), 0, dp(6));
                setBox.addView(dn, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        final TextView recentsChip = toggleChip(prefs.getBoolean("hide_recents", false));
        recentsChip.setOnClickListener(v -> { prefs.edit().putBoolean("hide_recents", !prefs.getBoolean("hide_recents", false)).apply(); renderSettings(); });
        setBox.addView(settingRow(t("hide_recents"), recentsChip), new LinearLayout.LayoutParams(-1, -2));
        final TextView shotsChip = toggleChip(prefs.getBoolean("no_shots", false));
        shotsChip.setOnClickListener(v -> { prefs.edit().putBoolean("no_shots", !prefs.getBoolean("no_shots", false)).apply(); applySecure(); renderSettings(); });
        setBox.addView(settingRow(t("no_shots"), shotsChip), new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("🛡️", t("set_protect")));
        final TextView encChip = toggleChip(prefs.getBoolean("enc_new", false));
        encChip.setOnClickListener(v -> { prefs.edit().putBoolean("enc_new", !prefs.getBoolean("enc_new", false)).apply(); renderSettings(); });
        setBox.addView(settingRow(t("enc_new"), encChip), new LinearLayout.LayoutParams(-1, -2));
        TextView encAll = primaryButton(t("enc_all"));
        encAll.setOnClickListener(v -> startCrypt(true));
        LinearLayout.LayoutParams eap = params(-1, 46);
        eap.topMargin = dp(8);
        setBox.addView(encAll, eap);
        TextView decAll = button(t("dec_all"));
        decAll.setOnClickListener(v -> startCrypt(false));
        LinearLayout.LayoutParams dap = params(-1, 46);
        dap.topMargin = dp(8);
        setBox.addView(decAll, dap);
        TextView encNote = tv(t("enc_note"), 11.5f, MUTED, false);
        encNote.setPadding(0, dp(8), 0, 0);
        setBox.addView(encNote, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("🧠", t("set_smart")));
        final TextView atChip = toggleChip(prefs.getBoolean("autotag_new", true));
        atChip.setOnClickListener(v -> { prefs.edit().putBoolean("autotag_new", !prefs.getBoolean("autotag_new", true)).apply(); renderSettings(); });
        setBox.addView(settingRow(t("autotag_new"), atChip), new LinearLayout.LayoutParams(-1, -2));
        TextView atNote = tv(t("autotag_note"), 11.5f, MUTED, false);
        atNote.setPadding(0, 0, 0, dp(8));
        setBox.addView(atNote, new LinearLayout.LayoutParams(-1, -2));
        TextView atRun = primaryButton(t("autotag_run"));
        atRun.setOnClickListener(v -> autoTagUntagged());
        setBox.addView(atRun, params(-1, 46));
        final TextView tlChip = toggleChip(prefs.getBoolean("tag_local", false));
        tlChip.setOnClickListener(v -> { prefs.edit().putBoolean("tag_local", !prefs.getBoolean("tag_local", false)).apply(); renderSettings(); });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(10);
        setBox.addView(settingRow(t("tag_local_sw"), tlChip), tlp);
        TextView tlNote = tv(t("tag_local_note"), 11.5f, MUTED, false);
        setBox.addView(tlNote, new LinearLayout.LayoutParams(-1, -2));
        TextView trLbl = tv(t("trash_auto"), 15, FG, false);
        trLbl.setPadding(0, dp(14), 0, dp(6));
        setBox.addView(trLbl, new LinearLayout.LayoutParams(-1, -2));
        Flow trFlow = new Flow(this, dp(8), dp(8));
        int[] trOpts = {0, 7, 14, 30, 60, 90};
        int curTr = prefs.getInt("trash_days", 30);
        for (final int dn : trOpts) {
            TextView c = chip(dn == 0 ? t("off") : tf("trash_days_n", dn), dn == curTr);
            c.setOnClickListener(v -> { prefs.edit().putInt("trash_days", dn).apply(); renderSettings(); });
            trFlow.addView(c);
        }
        setBox.addView(trFlow, new LinearLayout.LayoutParams(-1, -2));

        setBox.addView(sectionLabel("📚", t("set_library")));
        TextView statsB = button(t("stats_btn"));
        statsB.setOnClickListener(v -> openStats());
        setBox.addView(statsB, params(-1, 48));
        TextView dupB = button(t("dup_btn"));
        dupB.setOnClickListener(v -> openDuplicates());
        LinearLayout.LayoutParams dbp = params(-1, 48);
        dbp.topMargin = dp(8);
        setBox.addView(dupB, dbp);
        TextView intB = button(t("int_btn"));
        intB.setOnClickListener(v -> openIntegrity());
        LinearLayout.LayoutParams ibp = params(-1, 48);
        ibp.topMargin = dp(8);
        setBox.addView(intB, ibp);

        setBox.addView(sectionLabel("💾", t("set_data")));
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
                    prefs.edit().remove("lang").remove("theme").remove("cols").remove("names").remove("bgdim").remove("bgres").remove("auto_icon").remove("autotag_new").remove("trash_days").remove("app_name").remove("feed_tags").remove("tag_local").remove("enc_new")
                            .remove("no_shots").remove("hide_recents").remove("mmode").apply();
                    bgFile().delete();
                    iconFile().delete();
                    setIcon(0, true);
                    toast(t("reset_done"));
                    recreate();
                })));
        LinearLayout.LayoutParams rsp = params(-1, 48);
        rsp.topMargin = dp(10);
        setBox.addView(rs, rsp);
        TextView ver = tv("Offline Media Vault · v2.1", 11, MUTED, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(18), 0, 0);
        ver.setTag("ver");
        setBox.addView(ver, new LinearLayout.LayoutParams(-1, -2));
        groupCards();
        fxTree(setBox);
    }

    // ---------- Проверка целостности ----------
    private boolean decodeOk(Library.Item it, File f) {
        try {
            if (Library.isVideo(it.name)) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(f.getAbsolutePath());
                    String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    return d != null && Long.parseLong(d) > 0;
                } finally { try { r.release(); } catch (Exception ignored) { } }
            } else if (Library.isPdf(it.name)) {
                try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
                    PdfRenderer r = new PdfRenderer(pfd);
                    int n = r.getPageCount();
                    r.close();
                    return n > 0;
                }
            } else {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                return o.outWidth > 0 && o.outHeight > 0;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    private void openIntegrity() {
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
        TextView ttl = tv(t("int_title"), 19, FG, true);
        ttl.setPadding(dp(8), 0, 0, 0);
        head.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(20));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        final TextView scanning = tv(tf("int_scanning", 0, 0), 14, MUTED, false);
        scanning.setGravity(Gravity.CENTER);
        scanning.setPadding(0, dp(40), 0, dp(12));
        body.addView(scanning, new LinearLayout.LayoutParams(-1, -2));
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        body.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(root);
        animWin(d, R.style.OmvSheetAnim);
        openDialogs.add(d);
        d.show();
        final ArrayList<Library.Item> all = new ArrayList<>();
        synchronized (lib) { all.addAll(lib.items); }
        bar.setMax(Math.max(1, all.size()));
        io.execute(() -> {
            final ArrayList<Library.Item> bad = new ArrayList<>();
            final ArrayList<String> why = new ArrayList<>();
            int base = 0, i = 0;
            for (Library.Item it : all) {
                i++;
                File f = new File(it.path);
                String problem = null;
                if (!f.exists()) problem = "int_r_missing";
                else if (f.length() == 0) problem = "int_r_empty";
                else if (it.enc) { if (!Crypto.verify(this, f)) problem = "int_r_mac"; }
                else {
                    if (!it.sha.isEmpty()) {
                        String h = Library.sha256(f);
                        if (h == null || !h.equals(it.sha)) problem = "int_r_hash";
                    }
                    if (problem == null && !decodeOk(it, f)) problem = "int_r_decode";
                    if (problem == null && it.sha.isEmpty()) {
                        String h = Library.sha256(f);
                        if (h != null) { it.sha = h; it.size = f.length(); base++; }
                    }
                }
                if (problem != null) { bad.add(it); why.add(problem); }
                final int cur = i, tot = all.size();
                ui.post(() -> { scanning.setText(tf("int_scanning", cur, tot)); bar.setProgress(cur); });
            }
            if (base > 0) lib.save();
            final int fb = base, total = all.size();
            ui.post(() -> {
                body.removeAllViews();
                TextView sum = tv(tf("int_summary", total, bad.size(), fb), 13, MUTED, false);
                sum.setPadding(dp(2), dp(8), 0, dp(12));
                body.addView(sum, new LinearLayout.LayoutParams(-1, -2));
                if (bad.isEmpty()) {
                    TextView okT = tv(t("int_ok"), 18, TAG_GREEN, true);
                    okT.setGravity(Gravity.CENTER);
                    okT.setPadding(0, dp(40), 0, 0);
                    body.addView(okT, new LinearLayout.LayoutParams(-1, -2));
                    return;
                }
                TextView all2 = button(t("int_trash_all"));
                all2.setTextColor(0xFFFFFFFF);
                all2.setBackground(round(RED, 12));
                all2.setOnClickListener(v -> {
                    for (Library.Item it : bad) if (!it.trashed) lib.moveToTrash(it);
                    lib.save();
                    refreshAll();
                    toast(tf("moved_trash", bad.size()));
                    d.dismiss();
                });
                body.addView(all2, params(-1, 46));
                for (int k = 0; k < bad.size(); k++) {
                    final Library.Item it = bad.get(k);
                    LinearLayout card = card();
                    card.setOrientation(LinearLayout.VERTICAL);
                    TextView nm = tv(it.name, 14, FG, true);
                    nm.setMaxLines(2);
                    nm.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(nm);
                    card.addView(tv(t(why.get(k)), 12.5f, RED, false));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                    cp.topMargin = dp(10);
                    body.addView(card, cp);
                }
                fxTree(body);
            });
        });
    }

    // ---------- Дубликаты ----------
    private void openDuplicates() {
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
        TextView ttl = tv(t("dup_title"), 19, FG, true);
        ttl.setPadding(dp(8), 0, 0, 0);
        head.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(20));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView scanning = tv(t("dup_scanning"), 14, MUTED, false);
        scanning.setGravity(Gravity.CENTER);
        scanning.setPadding(0, dp(40), 0, dp(12));
        body.addView(scanning, new LinearLayout.LayoutParams(-1, -2));
        body.addView(new ProgressBar(this), new LinearLayout.LayoutParams(-1, dp(40)));
        d.setContentView(root);
        animWin(d, R.style.OmvSheetAnim);
        openDialogs.add(d);
        d.show();
        pool.execute(() -> {
            final ArrayList<ArrayList<Library.Item>> groups = lib.findDuplicates();
            ui.post(() -> { if (d.isShowing()) renderDup(body, groups); });
        });
    }

    private void renderDup(final LinearLayout body, final ArrayList<ArrayList<Library.Item>> groups) {
        body.removeAllViews();
        if (groups.isEmpty()) {
            body.addView(emptyNote(t("dup_none")), params(-1, -2));
            return;
        }
        long extra = 0;
        int extraN = 0;
        for (ArrayList<Library.Item> g : groups) {
            for (int i = 1; i < g.size(); i++) { extra += new File(g.get(i).path).length(); extraN++; }
        }
        TextView sum = tv(tf("dup_summary", groups.size(), extraN, fmtSize(extra)), 14, FG, true);
        sum.setPadding(0, dp(4), 0, dp(10));
        body.addView(sum, new LinearLayout.LayoutParams(-1, -2));
        TextView all = primaryButton(t("dup_trash_all"));
        all.setBackground(round(RED, 24));
        all.setTextColor(0xFFFFFFFF);
        all.setOnClickListener(v -> {
            int n = 0;
            for (ArrayList<Library.Item> g : groups) for (int i = 1; i < g.size(); i++) { lib.moveToTrash(g.get(i)); n++; }
            lib.save();
            groups.clear();
            refreshAll();
            toast(tf("moved_trash", n));
            renderDup(body, groups);
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(48));
        alp.bottomMargin = dp(12);
        body.addView(all, alp);
        int k = 0;
        for (final ArrayList<Library.Item> g : new ArrayList<>(groups)) {
            k++;
            LinearLayout card = card();
            card.addView(cardTitle(tf("dup_group", k, g.size(), fmtSize(new File(g.get(0).path).length()))));
            for (int i = 0; i < g.size(); i++) {
                Library.Item it = g.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(makeThumb(it), new LinearLayout.LayoutParams(dp(56), dp(56)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(10), 0, 0, 0);
                TextView nm = tv(it.name, 13, FG, true);
                nm.setSingleLine(true);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                tx.addView(nm);
                tx.addView(tv((i == 0 ? t("dup_keep") + " · " : "") + DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale(lang)).format(new Date(it.added)), 11.5f, i == 0 ? ACCENT : MUTED, false));
                row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(8);
                card.addView(row, rp);
            }
            TextView one = button(t("dup_trash_group"));
            one.setBackground(round(BTN, 20));
            one.setOnClickListener(v -> {
                for (int i = 1; i < g.size(); i++) lib.moveToTrash(g.get(i));
                lib.save();
                groups.remove(g);
                refreshAll();
                toast(tf("moved_trash", g.size() - 1));
                renderDup(body, groups);
            });
            card.addView(one, new LinearLayout.LayoutParams(-1, dp(42)));
            addCard(body, card);
        }
        fxTree(body);
    }

    // ---------- Статистика ----------
    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) n += dirSize(x);
        return n;
    }

    private LinearLayout statRow(String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        r.addView(tv(label, 13.5f, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = tv(value, 13.5f, FG, true);
        v.setGravity(Gravity.END);
        v.setPadding(dp(10), 0, 0, 0);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        return r;
    }

    private LinearLayout barRow(String label, double frac, String value, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        TextView lb = tv(label, 12.5f, FG, false);
        lb.setSingleLine(true);
        lb.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(lb, new LinearLayout.LayoutParams(dp(92), -2));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(FIELD, 6));
        float f = (float) Math.max(0.02, Math.min(1.0, frac));
        View fill = new View(this);
        fill.setBackground(round(color, 6));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(10), f));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(10), 1f - f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(10), 1);
        tp.setMarginStart(dp(6));
        r.addView(track, tp);
        TextView vt = tv(value, 12, MUTED, true);
        vt.setGravity(Gravity.END);
        vt.setPadding(dp(8), 0, 0, 0);
        r.addView(vt, new LinearLayout.LayoutParams(-2, -2));
        return r;
    }

    private void openStats() {
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
        TextView ttl = tv(t("stats_title"), 19, FG, true);
        ttl.setPadding(dp(8), 0, 0, 0);
        head.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(new ProgressBar(this), new LinearLayout.LayoutParams(-1, dp(60)));
        d.setContentView(root);
        animWin(d, R.style.OmvSheetAnim);
        openDialogs.add(d);
        d.show();
        pool.execute(() -> {
            final ArrayList<Library.Item> all;
            synchronized (lib) { all = new ArrayList<>(lib.items); }
            final ArrayList<Library.Album> albs = new ArrayList<>(lib.albums);
            final long[] sz = new long[all.size()];
            for (int i = 0; i < all.size(); i++) sz[i] = new File(all.get(i).path).length();
            final long appData = dirSize(getFilesDir()) + dirSize(getCacheDir());
            final long libJson = lib.dataFile.length();
            long fr = 0, tot = 0;
            try { StatFs sf = new StatFs(getFilesDir().getPath()); fr = sf.getAvailableBytes(); tot = sf.getTotalBytes(); } catch (Exception ignored) { }
            final long free = fr, total = tot;
            ui.post(() -> { if (d.isShowing()) renderStats(body, all, sz, albs, appData, libJson, free, total); });
        });
    }

    private void renderStats(LinearLayout body, ArrayList<Library.Item> all, long[] sz, ArrayList<Library.Album> albs,
                             long appData, long libJson, long free, long total) {
        body.removeAllViews();
        int photos = 0, videos = 0, manga = 0, trashN = 0, favN = 0, noAlbum = 0, multiAlb = 0, untagged = 0, tagAssign = 0, activeN = 0;
        long photoB = 0, videoB = 0, mangaB = 0, trashB = 0, activeB = 0;
        long largest = -1, smallest = Long.MAX_VALUE, first = Long.MAX_VALUE, last = 0;
        String largestName = "", smallestName = "", firstName = "", lastName = "";
        int[] weekday = new int[7];
        TreeMap<String, Integer> monthMap = new TreeMap<>();
        TreeMap<String, Integer> tagCount = new TreeMap<>();
        HashMap<String, Integer> extN = new HashMap<>();
        HashMap<String, Long> extB = new HashMap<>();
        HashMap<String, Integer> albCount = new HashMap<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        SimpleDateFormat mf = new SimpleDateFormat("yyyy-MM", Locale.ROOT);
        long oldestTrash = Long.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            Library.Item it = all.get(i);
            long b = sz[i];
            if (it.trashed) { trashN++; trashB += b; if (it.trashedAt > 0) oldestTrash = Math.min(oldestTrash, it.trashedAt); continue; }
            activeN++;
            activeB += b;
            if (Library.isPdf(it.name)) { manga++; mangaB += b; } else if (Library.isVideo(it.name)) { videos++; videoB += b; } else { photos++; photoB += b; }
            if (it.fav) favN++;
            if (b > largest) { largest = b; largestName = it.name; }
            if (b < smallest) { smallest = b; smallestName = it.name; }
            if (it.added < first) { first = it.added; firstName = it.name; }
            if (it.added > last) { last = it.added; lastName = it.name; }
            cal.setTimeInMillis(it.added);
            weekday[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]++;
            String mk = mf.format(new Date(it.added));
            Integer mv = monthMap.get(mk);
            monthMap.put(mk, mv == null ? 1 : mv + 1);
            List<String> ts = Library.tagsOf(it);
            if (ts.isEmpty()) untagged++;
            tagAssign += ts.size();
            for (String tg : ts) { Integer c = tagCount.get(tg); tagCount.put(tg, c == null ? 1 : c + 1); }
            List<String> ai = Library.albumIdsOf(it);
            int valid = 0;
            for (String aid : ai) { if (lib.albumById(aid) != null) { valid++; Integer c = albCount.get(aid); albCount.put(aid, c == null ? 1 : c + 1); } }
            if (valid == 0) noAlbum++;
            if (valid > 1) multiAlb++;
            int dot = it.file.lastIndexOf('.');
            String ext = dot >= 0 ? it.file.substring(dot + 1).toLowerCase(Locale.ROOT) : "?";
            Integer en = extN.get(ext); extN.put(ext, en == null ? 1 : en + 1);
            Long eb = extB.get(ext); extB.put(ext, (eb == null ? 0 : eb) + b);
        }
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale(lang));
        int BAR2 = 0xFFE8527E;

        LinearLayout c1 = card();
        c1.addView(cardTitle(t("st_overview")));
        c1.addView(statRow(t("st_files"), String.valueOf(activeN)));
        c1.addView(statRow(t("st_size"), fmtSize(activeB)));
        c1.addView(statRow(t("st_avg"), activeN > 0 ? fmtSize(activeB / activeN) : "—"));
        long mx = Math.max(1, Math.max(photoB, Math.max(videoB, mangaB)));
        c1.addView(barRow(t("kind_photo"), photoB / (double) mx, photos + " · " + fmtSize(photoB), ACCENT));
        c1.addView(barRow(t("kind_video"), videoB / (double) mx, videos + " · " + fmtSize(videoB), BAR2));
        c1.addView(barRow(t("kind_manga"), mangaB / (double) mx, manga + " · " + fmtSize(mangaB), TAG_GREEN));
        addCard(body, c1);

        LinearLayout c2 = card();
        c2.addView(cardTitle(t("st_storage")));
        c2.addView(statRow(t("st_media"), fmtSize(activeB)));
        c2.addView(statRow(t("st_trash"), trashN + " · " + fmtSize(trashB)));
        if (oldestTrash != Long.MAX_VALUE) c2.addView(statRow(t("st_trash_old"), df.format(new Date(oldestTrash))));
        c2.addView(statRow(t("st_appdata"), fmtSize(appData)));
        c2.addView(statRow(t("st_libfile"), fmtSize(libJson)));
        if (total > 0) {
            c2.addView(statRow(t("st_dev_free"), fmtSize(free) + " / " + fmtSize(total)));
            c2.addView(barRow(t("st_dev_used"), (total - free) / (double) total, Math.round((total - free) * 100.0 / total) + "%", ACCENT));
            c2.addView(barRow(t("st_app_share"), appData / (double) total, String.format(Locale.ROOT, "%.2f%%", appData * 100.0 / total), BAR2));
        }
        addCard(body, c2);

        if (activeN > 0) {
            LinearLayout c3 = card();
            c3.addView(cardTitle(t("st_records")));
            c3.addView(statRow(t("st_largest"), largestName + " · " + fmtSize(largest)));
            c3.addView(statRow(t("st_smallest"), smallestName + " · " + fmtSize(smallest)));
            c3.addView(statRow(t("st_first"), df.format(new Date(first)) + " · " + firstName));
            c3.addView(statRow(t("st_last"), df.format(new Date(last)) + " · " + lastName));
            long span = Math.max(1, (last - first) / 86400000L + 1);
            c3.addView(statRow(t("st_span"), tf("st_days", span)));
            c3.addView(statRow(t("st_per_day"), String.format(Locale.ROOT, "%.2f", activeN / (double) span)));
            addCard(body, c3);

            LinearLayout c4 = card();
            c4.addView(cardTitle(t("st_by_month")));
            ArrayList<String> keys = new ArrayList<>(monthMap.keySet());
            int from = Math.max(0, keys.size() - 12);
            int mmax = 1;
            for (int i = from; i < keys.size(); i++) mmax = Math.max(mmax, monthMap.get(keys.get(i)));
            for (int i = from; i < keys.size(); i++) c4.addView(barRow(keys.get(i), monthMap.get(keys.get(i)) / (double) mmax, String.valueOf(monthMap.get(keys.get(i))), ACCENT));
            addCard(body, c4);

            LinearLayout c5 = card();
            c5.addView(cardTitle(t("st_by_weekday")));
            String[] wd = new java.text.DateFormatSymbols(new Locale(lang)).getShortWeekdays();
            int wmax = 1;
            for (int n : weekday) wmax = Math.max(wmax, n);
            for (int i = 0; i < 7; i++) c5.addView(barRow(wd[i + 1], weekday[i] / (double) wmax, String.valueOf(weekday[i]), BAR2));
            addCard(body, c5);
        }

        LinearLayout c6 = card();
        c6.addView(cardTitle(t("st_tags")));
        c6.addView(statRow(t("st_tags_unique"), String.valueOf(tagCount.size())));
        c6.addView(statRow(t("st_tags_assign"), String.valueOf(tagAssign)));
        c6.addView(statRow(t("st_tags_avg"), activeN > 0 ? String.format(Locale.ROOT, "%.2f", tagAssign / (double) activeN) : "—"));
        c6.addView(statRow(t("st_untagged"), untagged + (activeN > 0 ? " (" + Math.round(untagged * 100.0 / activeN) + "%)" : "")));
        ArrayList<Map.Entry<String, Integer>> te = new ArrayList<>(tagCount.entrySet());
        Collections.sort(te, (x, y) -> y.getValue() - x.getValue());
        if (!te.isEmpty()) {
            TextView top = tv(t("st_tags_top"), 12, MUTED, true);
            top.setPadding(0, dp(8), 0, dp(4));
            c6.addView(top);
            int tmax = te.get(0).getValue();
            for (int i = 0; i < Math.min(10, te.size()); i++) c6.addView(barRow(te.get(i).getKey(), te.get(i).getValue() / (double) tmax, String.valueOf(te.get(i).getValue()), TAG_GREEN));
        }
        addCard(body, c6);

        LinearLayout c7 = card();
        c7.addView(cardTitle(t("st_albums")));
        c7.addView(statRow(t("st_albums_n"), String.valueOf(albs.size())));
        c7.addView(statRow(t("favorites"), String.valueOf(favN)));
        c7.addView(statRow(t("st_no_album"), String.valueOf(noAlbum)));
        c7.addView(statRow(t("st_multi_album"), String.valueOf(multiAlb)));
        ArrayList<Library.Album> sortedAlb = new ArrayList<>(albs);
        final HashMap<String, Integer> acf = albCount;
        Collections.sort(sortedAlb, (x, y) -> {
            Integer ix = acf.get(x.id), iy = acf.get(y.id);
            return (iy == null ? 0 : iy) - (ix == null ? 0 : ix);
        });
        if (!sortedAlb.isEmpty()) {
            Integer f0 = acf.get(sortedAlb.get(0).id);
            int amax = Math.max(1, f0 == null ? 1 : f0);
            for (int i = 0; i < Math.min(6, sortedAlb.size()); i++) {
                Integer cc = acf.get(sortedAlb.get(i).id);
                int cn = cc == null ? 0 : cc;
                c7.addView(barRow(sortedAlb.get(i).name, cn / (double) amax, String.valueOf(cn), ACCENT));
            }
        }
        addCard(body, c7);

        LinearLayout c8 = card();
        c8.addView(cardTitle(t("st_types")));
        ArrayList<Map.Entry<String, Integer>> ee = new ArrayList<>(extN.entrySet());
        Collections.sort(ee, (x, y) -> y.getValue() - x.getValue());
        for (int i = 0; i < Math.min(8, ee.size()); i++) {
            Map.Entry<String, Integer> e = ee.get(i);
            c8.addView(statRow("." + e.getKey(), e.getValue() + " · " + fmtSize(extB.get(e.getKey()))));
        }
        addCard(body, c8);

        // подробности, которые считаются дольше (длительность видео, разрешение фото, страницы манги)
        LinearLayout c9 = card();
        c9.addView(cardTitle(t("st_details")));
        final TextView vDur = tv("…", 13.5f, FG, true), vAvg = tv("…", 13.5f, FG, true), vLong = tv("…", 13.5f, FG, true);
        final TextView pRes = tv("…", 13.5f, FG, true), pMp = tv("…", 13.5f, FG, true), mPages = tv("…", 13.5f, FG, true);
        for (TextView v : new TextView[]{vDur, vAvg, vLong, pRes, pMp, mPages}) v.setGravity(Gravity.END);
        String[] dl = {t("st_vid_total"), t("st_vid_avg"), t("st_vid_long"), t("st_ph_maxres"), t("st_ph_mp"), t("st_manga_pages")};
        TextView[] dv = {vDur, vAvg, vLong, pRes, pMp, mPages};
        for (int i = 0; i < dl.length; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(4), 0, dp(4));
            r.addView(tv(dl[i], 13.5f, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(dv[i], new LinearLayout.LayoutParams(-2, -2));
            c9.addView(r);
        }
        addCard(body, c9);

        LinearLayout c10 = card();
        c10.addView(cardTitle(t("st_app")));
        c10.addView(statRow(t("st_version"), "v1.7"));
        c10.addView(statRow(t("st_pin"), hasPin() ? t("pin_on") : t("pin_off")));
        c10.addView(statRow(t("st_theme"), prefs.getString("theme", "system")));
        c10.addView(statRow(t("set_columns"), String.valueOf(cols)));
        c10.addView(statRow(t("st_thumbs"), String.valueOf(thumbCache.size())));
        addCard(body, c10);
        fxTree(body);

        final ArrayList<Library.Item> act = new ArrayList<>();
        for (Library.Item it : all) if (!it.trashed) act.add(it);
        pool.execute(() -> {
            long durTotal = 0, durMax = 0;
            int vn = 0;
            long maxPix = 0;
            String maxRes = "—";
            double mp = 0;
            int pages = 0;
            for (Library.Item it : act) {
                File f = new File(it.path);
                if (Library.isVideo(it.name)) {
                    MediaMetadataRetriever mr = new MediaMetadataRetriever();
                    try {
                        mr.setDataSource(f.getAbsolutePath());
                        String ds = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        long dur = ds == null ? 0 : Long.parseLong(ds);
                        durTotal += dur; durMax = Math.max(durMax, dur); vn++;
                    } catch (Exception ignored) { } finally { try { mr.release(); } catch (Exception ignored) { } }
                } else if (Library.isPdf(it.name)) {
                    try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
                        PdfRenderer r = new PdfRenderer(pfd);
                        pages += r.getPageCount();
                        r.close();
                    } catch (Throwable ignored) { }
                } else {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                    if (o.outWidth > 0 && o.outHeight > 0) {
                        long px = (long) o.outWidth * o.outHeight;
                        mp += px / 1_000_000.0;
                        if (px > maxPix) { maxPix = px; maxRes = o.outWidth + "×" + o.outHeight; }
                    }
                }
            }
            final String sDur = vn > 0 ? fmtDur(durTotal) : "—", sAvg = vn > 0 ? fmtDur(durTotal / vn) : "—", sLong = vn > 0 ? fmtDur(durMax) : "—";
            final String sRes = maxRes, sMp = String.format(Locale.ROOT, "%.1f MP", mp), sPg = String.valueOf(pages);
            ui.post(() -> {
                vDur.setText(sDur); vAvg.setText(sAvg); vLong.setText(sLong);
                pRes.setText(sRes); pMp.setText(sMp); mPages.setText(sPg);
            });
        });
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
        styleDialog(busy);
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
        } else if (requestCode == PICK_BG && data.getData() != null) {
            setBgFrom(data.getData());
        } else if (requestCode == PICK_ICON && data.getData() != null) {
            setIconFrom(data.getData());
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
            final ArrayList<Library.Item> newItems = new ArrayList<>();
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
                        newItems.add(it);
                        if (Library.isPdf(name)) {   // манга: автоматически создаём альбом
                            Library.Album a = lib.createAlbum(Library.baseName(name));
                            it.album = a.id;
                        }
                    }
                }
                setBusy(name + "  (" + (i + 1) + "/" + uris.size() + ")", i + 1);
            }
            if (prefs.getBoolean("autotag_new", true) && !newItems.isEmpty()) {
                autoTagList(newItems, true);
            }
            if (prefs.getBoolean("enc_new", false) && !newItems.isEmpty()) cryptItems(newItems, true, false);
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
                                it.fav = mm.fav;
                                if (mm.added > 0) it.added = mm.added;
                                for (String oa : mm.album.split(",")) {
                                    String na = amap.get(oa.trim());
                                    if (na != null) Library.addAlb(it, na);
                                }
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
                        if (it.enc) Crypto.decrypt(this, f, zos);
                        else try (InputStream in = new FileInputStream(f)) { pump(in, zos); }
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
