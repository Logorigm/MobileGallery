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
        labeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build());
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

    List<String> analyze(Bitmap bmp) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (bmp == null) return new ArrayList<>();
        try {
            InputImage img = InputImage.fromBitmap(bmp, 0);
            int nFaces = 0;
            try {
                List<Face> fl = Tasks.await(faces.process(img));
                nFaces = fl.size();
            } catch (Throwable ignore) { }
            if (nFaces == 1) out.add("ru".equals(lang) ? "портрет" : "portrait");
            else if (nFaces == 2) out.add("ru".equals(lang) ? "пара" : "couple");
            else if (nFaces >= 3) out.add("ru".equals(lang) ? "группа" : "group");
            if (nFaces > 0) out.add("ru".equals(lang) ? "люди" : "people");
            List<ImageLabel> ls = Tasks.await(labeler.process(img));
            int n = 0;
            for (ImageLabel l : ls) {
                String en = l.getText();
                if (Labels.SKIP.contains(en.toLowerCase(java.util.Locale.ROOT))) continue;
                if (n >= 6) break;
                out.add(Labels.tr(en, lang));
                String c = Labels.category(en, lang);
                if (c != null) out.add(c);
                n++;
            }
        } catch (Throwable ignore) {
        }
        return new ArrayList<>(out);
    }
}
