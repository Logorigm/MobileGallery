package com.example.offlinemediavault;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Хранилище: файлы лежат в filesDir/media, метаданные (теги, альбомы, корзина) в filesDir/library.json.
 * Запись атомарная (tmp + rename) с резервной копией library.json.bak.
 * Данные переживают обновление приложения, пока applicationId и подпись APK те же.
 */
final class Library {
    static class Item {
        String id = "", name = "", file = "", tags = "", album = "", path = "";
        long added, trashedAt, size;
        int rot;   // поворот для показа: 0, 90, 180, 270
        boolean trashed, fav, enc;
        String sha = "";
    }

    // Один файл может лежать в нескольких альбомах: item.album хранит id через запятую
    static List<String> albumIdsOf(Item it) {
        ArrayList<String> out = new ArrayList<>();
        if (it.album == null) return out;
        for (String s : it.album.split(",")) { s = s.trim(); if (!s.isEmpty() && !out.contains(s)) out.add(s); }
        return out;
    }

    static boolean inAlb(Item it, String id) { return albumIdsOf(it).contains(id); }

    static void addAlb(Item it, String id) {
        List<String> l = albumIdsOf(it);
        if (!l.contains(id)) l.add(id);
        it.album = join(l);
    }

    static void removeAlb(Item it, String id) {
        List<String> l = albumIdsOf(it);
        l.remove(id);
        it.album = join(l);
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(','); sb.append(s); }
        return sb.toString();
    }

    static final String FAV = "__fav__";   // виртуальный альбом «Избранное»

    static class Album {
        String id = "", name = "";
        long created;
        final ArrayList<String> order = new ArrayList<>();   // ручной порядок файлов в альбоме
    }

    static class Meta {
        String name = "", tags = "", album = "";
        long added;
        boolean fav;
    }

    static class Parsed {
        final HashMap<String, Meta> byEntry = new HashMap<>();
        final LinkedHashMap<String, String> albumNames = new LinkedHashMap<>();
    }

    final File mediaDir, dataFile, bakFile;
    final ArrayList<Item> items = new ArrayList<>();
    final ArrayList<Album> albums = new ArrayList<>();
    private long lastStamp = 0;
    private final Random rnd = new Random();

    Library(File filesDir) {
        mediaDir = new File(filesDir, "media");
        mediaDir.mkdirs();
        dataFile = new File(filesDir, "library.json");
        bakFile = new File(filesDir, "library.json.bak");
    }

    // ---------- типы файлов ----------
    static boolean isVideo(String n) {
        return n.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|m4v|3gp|mkv|webm|avi)$");
    }

    static boolean isPdf(String n) {
        return n.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    static boolean isImage(String n) {
        return n.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$");
    }

    static boolean isMedia(String n) {
        return isImage(n) || isVideo(n) || isPdf(n);
    }

    static String baseName(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    // ---------- теги ----------
    static List<String> parseTags(String s) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("[,;\\n]")) {
            String t = part.trim().toLowerCase(Locale.ROOT);
            while (t.startsWith("#")) t = t.substring(1).trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    static List<String> tagsOf(Item it) {
        return parseTags(it.tags);
    }

    static String joinTags(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    static String mergeTags(String a, String b) {
        LinkedHashSet<String> set = new LinkedHashSet<>(parseTags(a));
        set.addAll(parseTags(b));
        return joinTags(new ArrayList<>(set));
    }

    // ---------- служебное ----------
    synchronized long nextStamp() {
        long n = System.currentTimeMillis();
        if (n <= lastStamp) n = lastStamp + 1;
        lastStamp = n;
        return n;
    }

    String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(rnd.nextInt(1 << 20), 36);
    }

    static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }

    static void copyFile(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a); FileOutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private void bind(Item it) {
        File f = new File(mediaDir, it.file);
        it.path = f.getAbsolutePath();
        // самовосстановление: ориентируемся на сам файл, если запись и файл разошлись (сбой посреди шифрования)
        boolean magic = Crypto.hasMagic(f);
        if (magic != it.enc && f.exists()) it.enc = magic;
    }

    // ---------- загрузка / сохранение ----------
    synchronized void load(String legacyJson) {
        items.clear();
        albums.clear();
        boolean ok = readFile(dataFile);
        if (!ok) {
            items.clear();
            albums.clear();
            ok = readFile(bakFile);
        }
        if (!ok && legacyJson != null) {
            items.clear();
            albums.clear();
            readLegacy(legacyJson);
        }
        recoverOrphans();
        save();
    }

    private boolean readFile(File f) {
        if (!f.exists()) return false;
        try {
            JSONObject o = new JSONObject(readText(f));
            JSONArray al = o.optJSONArray("albums");
            if (al != null) {
                for (int i = 0; i < al.length(); i++) {
                    JSONObject j = al.getJSONObject(i);
                    Album a = new Album();
                    a.id = j.optString("id", newId());
                    a.name = j.optString("name", "Album");
                    a.created = j.optLong("created", 0);
                    JSONArray oa = j.optJSONArray("order");
                    if (oa != null) for (int k = 0; k < oa.length(); k++) a.order.add(oa.optString(k));
                    albums.add(a);
                }
            }
            JSONArray ia = o.optJSONArray("items");
            if (ia != null) {
                for (int i = 0; i < ia.length(); i++) {
                    JSONObject j = ia.getJSONObject(i);
                    String file = j.optString("file", "");
                    if (file.isEmpty()) continue;
                    File ff = new File(mediaDir, file);
                    if (!ff.exists()) continue;
                    Item it = new Item();
                    it.id = j.optString("id", newId());
                    it.file = file;
                    it.name = j.optString("name", file);
                    it.tags = j.optString("tags", "");
                    it.album = j.optString("album", "");
                    it.added = j.optLong("added", ff.lastModified());
                    it.trashed = j.optBoolean("trashed", false);
                    it.trashedAt = j.optLong("trashedAt", 0);
                    it.fav = j.optBoolean("fav", false);
                    it.enc = j.optBoolean("enc", false);
                    it.sha = j.optString("sha", "");
                    it.size = j.optLong("size", 0);
                    it.rot = j.optInt("rot", 0);
                    bind(it);
                    items.add(it);
                }
            }
            for (Item it : items) if (it.added > lastStamp) lastStamp = it.added;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Формат старых версий приложения (SharedPreferences "library")
    private void readLegacy(String json) {
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String file = new File(o.optString("path", "")).getName();
                if (file.isEmpty()) continue;
                File ff = new File(mediaDir, file);
                if (!ff.exists()) continue;
                Item it = new Item();
                it.id = newId();
                it.file = file;
                it.name = o.optString("name", file);
                it.tags = o.optString("tags", "");
                it.added = o.optLong("added", ff.lastModified());
                bind(it);
                items.add(it);
                if (it.added > lastStamp) lastStamp = it.added;
            }
        } catch (Exception ignored) {
        }
    }

    // Файлы, которые лежат в папке, но не попали в метаданные, возвращаются в библиотеку
    private void recoverOrphans() {
        File[] fs = mediaDir.listFiles();
        if (fs == null) return;
        HashSet<String> known = new HashSet<>();
        for (Item it : items) known.add(it.file);
        Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        for (File f : fs) {
            if (!f.isFile() || known.contains(f.getName()) || !isMedia(f.getName())) continue;
            Item it = new Item();
            it.id = newId();
            it.file = f.getName();
            it.name = f.getName();
            it.added = f.lastModified();
            bind(it);
            items.add(it);
            if (it.added > lastStamp) lastStamp = it.added;
        }
    }

    synchronized void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 2);
            JSONArray al = new JSONArray();
            for (Album a : albums) {
                JSONObject j = new JSONObject();
                j.put("id", a.id);
                j.put("name", a.name);
                j.put("created", a.created);
                JSONArray oa = new JSONArray();
                for (String x : a.order) oa.put(x);
                j.put("order", oa);
                al.put(j);
            }
            o.put("albums", al);
            JSONArray ia = new JSONArray();
            for (Item it : items) {
                JSONObject j = new JSONObject();
                j.put("id", it.id);
                j.put("file", it.file);
                j.put("name", it.name);
                j.put("tags", it.tags);
                j.put("album", it.album);
                j.put("added", it.added);
                j.put("trashed", it.trashed);
                j.put("trashedAt", it.trashedAt);
                j.put("fav", it.fav);
                if (it.enc) j.put("enc", true);
                if (!it.sha.isEmpty()) j.put("sha", it.sha);
                if (it.size > 0) j.put("size", it.size);
                if (it.rot != 0) j.put("rot", it.rot);
                ia.put(j);
            }
            o.put("items", ia);
            File tmp = new File(dataFile.getParentFile(), "library.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes("UTF-8"));
                out.getFD().sync();
            }
            if (dataFile.exists()) copyFile(dataFile, bakFile);
            if (!tmp.renameTo(dataFile)) {
                dataFile.delete();
                tmp.renameTo(dataFile);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- операции ----------
    synchronized File uniqueFile(String name) {
        String clean = new File(name).getName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (clean.isEmpty()) clean = "file";
        String base = clean, ext = "";
        int dot = clean.lastIndexOf('.');
        if (dot > 0) {
            base = clean.substring(0, dot);
            ext = clean.substring(dot);
        }
        File f = new File(mediaDir, clean);
        int n = 1;
        while (f.exists()) f = new File(mediaDir, base + "_" + (n++) + ext);
        return f;
    }

    synchronized Item addItem(String displayName, File file) {
        Item it = new Item();
        it.id = newId();
        it.name = displayName;
        it.file = file.getName();
        it.added = nextStamp();
        bind(it);
        items.add(it);
        return it;
    }

    synchronized ArrayList<Item> active() {
        ArrayList<Item> out = new ArrayList<>();
        for (Item it : items) if (!it.trashed) out.add(it);
        return out;
    }

    synchronized ArrayList<Item> trashList() {
        ArrayList<Item> out = new ArrayList<>();
        for (Item it : items) if (it.trashed) out.add(it);
        Collections.sort(out, (x, y) -> Long.compare(y.trashedAt, x.trashedAt));
        return out;
    }

    synchronized ArrayList<Item> inAlbum(String albumId) {
        ArrayList<Item> out = new ArrayList<>();
        if (FAV.equals(albumId)) {
            for (Item it : items) if (!it.trashed && it.fav) out.add(it);
            Collections.sort(out, (x, y) -> Long.compare(y.added, x.added));
            return out;
        }
        for (Item it : items) if (!it.trashed && inAlb(it, albumId)) out.add(it);
        final Album al = albumById(albumId);
        final List<String> ord = al == null ? new ArrayList<String>() : al.order;
        Collections.sort(out, (x, y) -> {
            int ix = ord.indexOf(x.id), iy = ord.indexOf(y.id);
            if (ix >= 0 && iy >= 0) return Integer.compare(ix, iy);
            if (ix >= 0) return -1;
            if (iy >= 0) return 1;
            return Long.compare(x.added, y.added);
        });
        return out;
    }

    synchronized Album albumById(String id) {
        if (id == null) return null;
        for (Album a : albums) if (a.id.equals(id)) return a;
        return null;
    }

    synchronized String uniqueAlbumName(String base) {
        String b = base == null || base.trim().isEmpty() ? "Album" : base.trim();
        String name = b;
        int n = 2;
        while (albumNameExists(name)) name = b + " (" + (n++) + ")";
        return name;
    }

    private boolean albumNameExists(String name) {
        for (Album a : albums) if (a.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    synchronized Album createAlbum(String name) {
        Album a = new Album();
        a.id = newId();
        a.name = uniqueAlbumName(name);
        a.created = System.currentTimeMillis();
        albums.add(a);
        return a;
    }

    synchronized void deleteAlbum(String id) {
        Album a = albumById(id);
        if (a != null) albums.remove(a);
        for (Item it : items) removeAlb(it, id);
    }

    synchronized void moveToTrash(Item it) {
        it.trashed = true;
        it.trashedAt = System.currentTimeMillis();
    }

    synchronized void restore(Item it) {
        it.trashed = false;
        it.trashedAt = 0;
        for (String aid : albumIdsOf(it)) if (albumById(aid) == null) removeAlb(it, aid);
    }

    synchronized String deleteForever(Item it) {
        new File(it.path).delete();
        items.remove(it);
        return it.path;
    }

    static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Группы одинаковых файлов (по размеру, затем по SHA-256). Вызывать не в UI-потоке. */
    ArrayList<ArrayList<Item>> findDuplicates() {
        ArrayList<Item> snap;
        synchronized (this) { snap = active(); }
        HashMap<Long, ArrayList<Item>> bySize = new HashMap<>();
        for (Item it : snap) {
            long len = it.enc ? it.size : new File(it.path).length();
            if (len <= 0) continue;
            ArrayList<Item> l = bySize.get(len);
            if (l == null) { l = new ArrayList<>(); bySize.put(len, l); }
            l.add(it);
        }
        ArrayList<ArrayList<Item>> out = new ArrayList<>();
        for (ArrayList<Item> same : bySize.values()) {
            if (same.size() < 2) continue;
            HashMap<String, ArrayList<Item>> byHash = new HashMap<>();
            for (Item it : same) {
                String h = it.enc ? (it.sha.isEmpty() ? null : it.sha) : sha256(new File(it.path));
                if (h == null) continue;
                ArrayList<Item> l = byHash.get(h);
                if (l == null) { l = new ArrayList<>(); byHash.put(h, l); }
                l.add(it);
            }
            for (ArrayList<Item> g : byHash.values()) {
                if (g.size() < 2) continue;
                Collections.sort(g, (x, y) -> Long.compare(x.added, y.added));
                out.add(g);
            }
        }
        return out;
    }

    synchronized int trashCount() {
        int n = 0;
        for (Item it : items) if (it.trashed) n++;
        return n;
    }

    synchronized void wipeAll() {
        File[] fs = mediaDir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        items.clear();
        albums.clear();
        save();
    }

    // ---------- экспорт / импорт метаданных ----------
    synchronized String exportJson(List<Item> list) throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", "omv1");
        HashSet<String> used = new HashSet<>();
        for (Item it : list) used.addAll(albumIdsOf(it));
        JSONArray al = new JSONArray();
        for (Album a : albums) {
            if (!used.contains(a.id)) continue;
            JSONObject j = new JSONObject();
            j.put("id", a.id);
            j.put("name", a.name);
            al.put(j);
        }
        o.put("albums", al);
        JSONArray ia = new JSONArray();
        for (Item it : list) {
            JSONObject j = new JSONObject();
            j.put("file", "media/" + it.file);
            j.put("name", it.name);
            j.put("tags", it.tags);
            j.put("album", it.album);
            j.put("added", it.added);
            j.put("fav", it.fav);
            ia.put(j);
        }
        o.put("items", ia);
        return o.toString();
    }

    static Parsed parseExport(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"omv1".equals(o.optString("format"))) return null;
            Parsed p = new Parsed();
            JSONArray al = o.optJSONArray("albums");
            if (al != null) {
                for (int i = 0; i < al.length(); i++) {
                    JSONObject j = al.getJSONObject(i);
                    p.albumNames.put(j.optString("id"), j.optString("name", "Album"));
                }
            }
            JSONArray ia = o.optJSONArray("items");
            if (ia != null) {
                for (int i = 0; i < ia.length(); i++) {
                    JSONObject j = ia.getJSONObject(i);
                    Meta m = new Meta();
                    m.name = j.optString("name", "");
                    m.tags = j.optString("tags", "");
                    m.album = j.optString("album", "");
                    m.added = j.optLong("added", 0);
                    m.fav = j.optBoolean("fav", false);
                    p.byEntry.put(j.optString("file", ""), m);
                }
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
