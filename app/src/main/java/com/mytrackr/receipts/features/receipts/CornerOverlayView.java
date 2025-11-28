package com.mytrackr.receipts.features.receipts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

public class CornerOverlayView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] pts = null;
    private int activeHandle = -1;
    private final float handleRadius = 20f;
    private final Path path = new Path();
    private OnCornersChangedListener listener;

    public interface OnCornersChangedListener {
        void onCornersChanged(float[] viewCorners);
    }

    public CornerOverlayView(Context context) { this(context, null); }
    public CornerOverlayView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public CornerOverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        linePaint.setColor(Color.argb(180, 255, 193, 7));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(3f);
        handleFill.setColor(Color.argb(200, 255, 193, 7));
        handleFill.setStyle(Paint.Style.FILL);
        setVisibility(GONE);
    }

    public void setOnCornersChangedListener(OnCornersChangedListener l) { this.listener = l; }

    public void setCornersViewCoords(float[] viewPts) {
        if (viewPts == null || viewPts.length < 8) return;
        if (this.pts == null) this.pts = new float[8];
        System.arraycopy(viewPts, 0, this.pts, 0, 8);
        invalidate();
        setVisibility(VISIBLE);
    }

    public float[] getCornersViewCoords() {
        if (pts == null) return null;
        float[] out = new float[8]; System.arraycopy(pts, 0, out, 0, 8); return out;
    }

    public void hide() { setVisibility(GONE); }
    public void show() { if (pts != null) setVisibility(VISIBLE); }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (pts == null) return;
        path.reset();
        path.moveTo(pts[0], pts[1]);
        path.lineTo(pts[2], pts[3]);
        path.lineTo(pts[4], pts[5]);
        path.lineTo(pts[6], pts[7]);
        path.close();
        canvas.drawPath(path, linePaint);
        // handles
        for (int i = 0; i < 4; i++) {
            float x = pts[i*2]; float y = pts[i*2+1];
            canvas.drawCircle(x, y, handleRadius, handleFill);
            canvas.drawCircle(x, y, handleRadius, handlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pts == null || getVisibility() != VISIBLE) return false;
        float x = event.getX(); float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findNearestHandle(x, y);
                return activeHandle != -1;
            case MotionEvent.ACTION_MOVE:
                if (activeHandle != -1) {
                    pts[activeHandle*2] = clamp(x, 0, getWidth());
                    pts[activeHandle*2+1] = clamp(y, 0, getHeight());
                    if (listener != null) listener.onCornersChanged(getCornersViewCoords());
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeHandle != -1) {
                    performClick();
                }
                activeHandle = -1;
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int findNearestHandle(float x, float y) {
        float minDist = Float.MAX_VALUE; int idx = -1;
        for (int i = 0; i < 4; i++) {
            float dx = x - pts[i*2]; float dy = y - pts[i*2+1];
            float d = dx*dx + dy*dy;
            if (d < minDist && d <= (handleRadius*handleRadius*4)) { minDist = d; idx = i; }
        }
        return idx;
    }

    private float clamp(float v, float lo, float hi) { if (v < lo) return lo; if (v > hi) return hi; return v; }
}
