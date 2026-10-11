package com.example.offlinemediavault;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Картинка с увеличением щипком, перетаскиванием и двойным касанием. */
final class ZoomImageView extends ImageView {
    private final Matrix m = new Matrix();
    private final float[] v = new float[9];
    private final RectF rect = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector tapDetector;
    private float minScale = 1f;
    private static final float MAX_ZOOM = 6f;   // относительно «вписанного» масштаба
    private float lastX, lastY;
    private int lastPointers = 0;
    private ValueAnimator zoomAnim;

    ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float cur = scale();
                float target = Math.max(minScale * 0.9f, Math.min(minScale * MAX_ZOOM, cur * d.getScaleFactor()));
                float f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                fix();
                setImageMatrix(m);
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (scale() < minScale) fit();
            }
        });
        tapDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                float to = isZoomed() ? minScale : Math.min(minScale * 2.6f, minScale * MAX_ZOOM);
                animateTo(to, e.getX(), e.getY());
                return true;
            }
        });
    }

    private float scale() {
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    boolean isZoomed() { return getDrawable() != null && scale() > minScale * 1.03f; }

    @Override public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        fit();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private void fit() {
        Drawable d = getDrawable();
        int vw = getWidth(), vh = getHeight();
        if (d == null || vw <= 0 || vh <= 0) return;
        int dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float s = Math.min((float) vw / dw, (float) vh / dh);
        m.reset();
        m.postScale(s, s);
        m.postTranslate((vw - dw * s) / 2f, (vh - dh * s) / 2f);
        minScale = s;
        setImageMatrix(m);
    }

    private void fix() {
        Drawable d = getDrawable();
        if (d == null) return;
        rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        m.mapRect(rect);
        int vw = getWidth(), vh = getHeight();
        float dx = 0, dy = 0;
        if (rect.width() <= vw) dx = (vw - rect.width()) / 2f - rect.left;
        else if (rect.left > 0) dx = -rect.left;
        else if (rect.right < vw) dx = vw - rect.right;
        if (rect.height() <= vh) dy = (vh - rect.height()) / 2f - rect.top;
        else if (rect.top > 0) dy = -rect.top;
        else if (rect.bottom < vh) dy = vh - rect.bottom;
        m.postTranslate(dx, dy);
    }

    private void animateTo(final float to, final float fx, final float fy) {
        if (zoomAnim != null) zoomAnim.cancel();
        final float from = scale();
        zoomAnim = ValueAnimator.ofFloat(0f, 1f);
        zoomAnim.setDuration(240);
        zoomAnim.addUpdateListener(a -> {
            float s = from + (to - from) * (float) a.getAnimatedValue();
            float f = s / scale();
            m.postScale(f, f, fx, fy);
            fix();
            setImageMatrix(m);
        });
        zoomAnim.start();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (getDrawable() == null) return false;
        tapDetector.onTouchEvent(e);
        scaleDetector.onTouchEvent(e);
        int n = e.getPointerCount();
        float cx = 0, cy = 0;
        for (int i = 0; i < n; i++) { cx += e.getX(i); cy += e.getY(i); }
        cx /= n;
        cy /= n;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || n != lastPointers || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_POINTER_DOWN) {
            lastX = cx;
            lastY = cy;
        } else if (a == MotionEvent.ACTION_MOVE && isZoomed() && !scaleDetector.isInProgress()) {
            m.postTranslate(cx - lastX, cy - lastY);
            fix();
            setImageMatrix(m);
            lastX = cx;
            lastY = cy;
        }
        lastPointers = (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) ? 0 : n;
        if (isZoomed() && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }
}
