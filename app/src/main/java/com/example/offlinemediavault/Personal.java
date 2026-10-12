package com.example.offlinemediavault;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** «Личная» память: вектор признаков каждого фото (уверенности меток ML Kit) и подсказка тегов по похожим фото с вашими тегами. */
final class Personal {
    private final File file;
    private final HashMap<String, HashMap<Integer, Float>> vecs = new HashMap<>();
    private boolean dirty = false;

    Personal(File dir) {
        file = new File(dir, "vec.txt");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(" ");
                if (p.length < 2) continue;
                HashMap<Integer, Float> v = new HashMap<>();
                for (int i = 1; i < p.length; i++) {
                    int c = p[i].indexOf(':');
                    if (c > 0) v.put(Integer.parseInt(p[i].substring(0, c)), Float.parseFloat(p[i].substring(c + 1)));
                }
                vecs.put(p[0], v);
            }
        } catch (Exception ignored) {
        }
    }

    synchronized boolean has(String id) { return vecs.containsKey(id); }

    synchronized void put(String id, Map<Integer, Float> v) {
        vecs.put(id, new HashMap<>(v));
        dirty = true;
    }

    synchronized void prune(Set<String> live) {
        if (vecs.keySet().retainAll(live)) dirty = true;
    }

    synchronized void save() {
        if (!dirty) return;
        try {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, HashMap<Integer, Float>> e : vecs.entrySet()) {
                    sb.setLength(0);
                    sb.append(e.getKey());
                    for (Map.Entry<Integer, Float> x : e.getValue().entrySet()) sb.append(' ').append(x.getKey()).append(':').append(String.format(java.util.Locale.ROOT, "%.3f", x.getValue()));
                    sb.append('\n');
                    o.write(sb.toString().getBytes("UTF-8"));
                }
            }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file); }
            dirty = false;
        } catch (Exception ignored) {
        }
    }

    private static double norm(Map<Integer, Float> v) {
        double s = 0;
        for (float x : v.values()) s += x * x;
        return Math.sqrt(s);
    }

    private static double cos(Map<Integer, Float> a, double na, Map<Integer, Float> b, double nb) {
        if (na == 0 || nb == 0) return 0;
        double d = 0;
        Map<Integer, Float> s = a.size() < b.size() ? a : b, l = s == a ? b : a;
        for (Map.Entry<Integer, Float> e : s.entrySet()) {
            Float y = l.get(e.getKey());
            if (y != null) d += e.getValue() * y;
        }
        return d / (na * nb);
    }

    /** Теги, которые стоят на похожих фото (по вектору). tagsById — теги всех уже размеченных файлов. */
    synchronized List<String> suggest(String selfId, Map<Integer, Float> v, Map<String, List<String>> tagsById) {
        ArrayList<String> out = new ArrayList<>();
        double nv = norm(v);
        if (nv == 0) return out;
        ArrayList<Object[]> nb = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : tagsById.entrySet()) {
            if (e.getKey().equals(selfId) || e.getValue().isEmpty()) continue;
            HashMap<Integer, Float> o = vecs.get(e.getKey());
            if (o == null) continue;
            double s = cos(v, nv, o, norm(o));
            if (s >= 0.80) nb.add(new Object[]{s, e.getKey()});
        }
        if (nb.isEmpty()) return out;
        Collections.sort(nb, (x, y) -> Double.compare((Double) y[0], (Double) x[0]));
        int k = Math.min(8, nb.size());
        HashMap<String, Double> score = new HashMap<>();
        HashMap<String, Integer> cnt = new HashMap<>();
        double tot = 0;
        for (int i = 0; i < k; i++) {
            double w = Math.pow((Double) nb.get(i)[0], 4);
            tot += w;
            for (String tg : tagsById.get((String) nb.get(i)[1])) {
                Double c = score.get(tg);
                score.put(tg, (c == null ? 0 : c) + w);
                Integer n = cnt.get(tg);
                cnt.put(tg, n == null ? 1 : n + 1);
            }
        }
        for (Map.Entry<String, Double> e : score.entrySet()) {
            int n = cnt.get(e.getKey());
            boolean strong = k == 1 && (Double) nb.get(0)[0] > 0.93;
            if (e.getValue() / tot >= 0.5 && (n >= 2 || strong)) out.add(e.getKey());
        }
        return out;
    }
}
