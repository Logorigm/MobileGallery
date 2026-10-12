package com.example.offlinemediavault;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Отдаёт файлы другим приложениям (кнопка «Поделиться») по content://-ссылкам.
 * Провайдер не экспортирован: доступ есть только у того, кому выдано разрешение на конкретную ссылку.
 * Корни: f — внутренняя папка приложения (обычные файлы), c — cache/share (расшифрованные копии).
 */
public class ShareProvider extends ContentProvider {
    static String authority(Context c) { return c.getPackageName() + ".share"; }

    static File shareDir(Context c) {
        File d = new File(c.getCacheDir(), "share");
        d.mkdirs();
        return d;
    }

    /** Ссылка на файл внутри filesDir или cache/share; name — имя, которое увидит получатель. */
    static Uri uriFor(Context c, File f, String name) {
        String path = f.getAbsolutePath();
        String files = c.getFilesDir().getAbsolutePath() + File.separator;
        String share = shareDir(c).getAbsolutePath() + File.separator;
        String root, rel;
        if (path.startsWith(share)) { root = "c"; rel = path.substring(share.length()); }
        else if (path.startsWith(files)) { root = "f"; rel = path.substring(files.length()); }
        else throw new IllegalArgumentException("outside of shared roots");
        Uri.Builder b = new Uri.Builder().scheme("content").authority(authority(c)).appendPath(root);
        for (String s : rel.split(java.util.regex.Pattern.quote(File.separator))) if (!s.isEmpty()) b.appendPath(s);
        if (name != null && !name.isEmpty()) b.appendQueryParameter("n", name);
        return b.build();
    }

    static String mimeOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "mp4": case "m4v": return "video/mp4";
            case "mov": return "video/quicktime";
            case "3gp": return "video/3gpp";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            case "avi": return "video/x-msvideo";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
        }
        String m = ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "application/octet-stream" : m;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        Context c = getContext();
        List<String> seg = uri.getPathSegments();
        if (c == null || seg.size() < 2) throw new FileNotFoundException(String.valueOf(uri));
        File base;
        if ("c".equals(seg.get(0))) base = shareDir(c);
        else if ("f".equals(seg.get(0))) base = c.getFilesDir();
        else throw new FileNotFoundException(String.valueOf(uri));
        File f = base;
        for (int i = 1; i < seg.size(); i++) f = new File(f, seg.get(i));
        try {
            String bp = base.getCanonicalPath() + File.separator;
            if (!f.getCanonicalPath().startsWith(bp)) throw new FileNotFoundException("denied");
        } catch (IOException e) {
            throw new FileNotFoundException("denied");
        }
        if (!f.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    private static String nameOf(Uri uri) {
        String n = uri.getQueryParameter("n");
        return n == null || n.isEmpty() ? uri.getLastPathSegment() : n;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new SecurityException("read only");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor mc = new MatrixCursor(cols, 1);
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return mc; }
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = nameOf(uri);
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        mc.addRow(row);
        return mc;
    }

    @Override public String getType(Uri uri) { return mimeOf(nameOf(uri)); }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
