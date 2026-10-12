package com.example.offlinemediavault;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Автотеги: ML Kit (метки + лица), всё на устройстве. Вызывать только из фонового потока. */
final class AutoTagger {
    private final ImageLabeler labeler;
    private final FaceDetector faces;
    private final String lang;

    AutoTagger(String lang) {
        this.lang = lang;
        labeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder().setConfidenceThreshold(0.03f).build());
        faces = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setMinFaceSize(0.08f).build());
    }

    void close() {
        try { labeler.close(); } catch (Throwable ignore) { }
        try { faces.close(); } catch (Throwable ignore) { }
    }

    static Bitmap videoFrame(String path) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            Bitmap b = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = r.getFrameAtTime();
            return b;
        } catch (Throwable e) {
            return null;
        } finally {
            try { r.release(); } catch (Throwable ignore) { }
        }
    }

    static final class Result {
        final List<String> tags = new ArrayList<>();
        final java.util.HashMap<Integer, Float> vec = new java.util.HashMap<>();
    }

    private void labelPass(Bitmap bmp, java.util.HashMap<Integer, Float> conf, java.util.HashMap<Integer, String> names) throws Exception {
        List<ImageLabel> ls = Tasks.await(labeler.process(InputImage.fromBitmap(bmp, 0)));
        for (ImageLabel l : ls) {
            Float old = conf.get(l.getIndex());
            if (old == null || old < l.getConfidence()) conf.put(l.getIndex(), l.getConfidence());
            names.put(l.getIndex(), l.getText());
        }
    }

    /** Полный кадр + центральный фрагмент (видит более мелкие объекты), метки объединяются по максимуму. */
    Result analyze(Bitmap bmp) {
        Result res = new Result();
        if (bmp == null) return res;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            int nFaces = 0;
            try {
                List<Face> fl = Tasks.await(faces.process(InputImage.fromBitmap(bmp, 0)));
                nFaces = fl.size();
            } catch (Throwable ignore) { }
            if (nFaces == 1) out.add("ru".equals(lang) ? "портрет" : "portrait");
            else if (nFaces == 2) out.add("ru".equals(lang) ? "пара" : "couple");
            else if (nFaces >= 3) out.add("ru".equals(lang) ? "группа" : "group");
            if (nFaces > 0) out.add("ru".equals(lang) ? "люди" : "people");
            java.util.HashMap<Integer, Float> conf = new java.util.HashMap<>();
            java.util.HashMap<Integer, String> names = new java.util.HashMap<>();
            labelPass(bmp, conf, names);
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w >= 240 && h >= 240) {
                try {
                    Bitmap crop = Bitmap.createBitmap(bmp, (int) (w * 0.2f), (int) (h * 0.2f), (int) (w * 0.6f), (int) (h * 0.6f));
                    labelPass(crop, conf, names);
                } catch (Throwable ignore) { }
            }
            ArrayList<java.util.Map.Entry<Integer, Float>> sorted = new ArrayList<>(conf.entrySet());
            java.util.Collections.sort(sorted, (x, y) -> Float.compare(y.getValue(), x.getValue()));
            int n = 0;
            for (java.util.Map.Entry<Integer, Float> e : sorted) {
                if (e.getValue() >= 0.03f) res.vec.put(e.getKey(), e.getValue());
                String en = names.get(e.getKey());
                if (en == null || e.getValue() < 0.55f || n >= 7) continue;
                if (Labels.SKIP.contains(en.toLowerCase(java.util.Locale.ROOT))) continue;
                out.add(Labels.tr(en, lang));
                String c = Labels.category(en, lang);
                if (c != null) out.add(c);
                n++;
            }
        } catch (Throwable ignore) {
        }
        res.tags.addAll(out);
        return res;
    }
}
