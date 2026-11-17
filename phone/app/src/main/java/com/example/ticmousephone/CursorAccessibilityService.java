package com.example.ticmousephone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Shows a floating cursor (accessibility overlay) and turns watch packets
 * into taps / drags / swipes / BACK via dispatchGesture.
 * Left button: quick press = tap, two quick = double tap, hold+move = drag.
 * Right button: BACK. Wheel: vertical swipe at the cursor.
 */
public class CursorAccessibilityService extends AccessibilityService {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View cursorView;
    private WindowManager.LayoutParams lp;
    private int screenW, screenH, cursorSize;
    private float curX, curY;

    private boolean leftDown = false, rightDown = false;
    private long leftDownMs = 0;
    private float downX, downY, lastX, lastY, dragDist;
    private GestureDescription.StrokeDescription dragStroke;
    private long lastTapUpMs = 0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        cursorSize = Math.round(46 * dm.density);
        curX = screenW / 2f;
        curY = screenH / 2f;
        cursorView = new CursorView(this);
        lp = new WindowManager.LayoutParams(
                cursorSize, cursorSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        positionCursor();
        try { wm.addView(cursorView, lp); } catch (Exception ignored) {}
        CursorController.register(this);
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        CursorController.unregister(this);
        if (wm != null && cursorView != null) {
            try { wm.removeView(cursorView); } catch (Exception ignored) {}
        }
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private void positionCursor() {
        if (lp == null) return;
        lp.x = Math.round(curX - cursorSize / 2f);
        lp.y = Math.round(curY - cursorSize / 2f);
        if (wm != null && cursorView != null) {
            try { wm.updateViewLayout(cursorView, lp); } catch (Exception ignored) {}
        }
    }

    // ---- packet handlers (called from the Bluetooth reader thread) ----
    void handleMove(int dx, int dy) {
        ui.post(() -> {
            curX = Math.max(0, Math.min(screenW - 1, curX + dx));
            curY = Math.max(0, Math.min(screenH - 1, curY + dy));
            positionCursor();
            if (leftDown) {
                dragDist += Math.abs(dx) + Math.abs(dy);
                continueDrag();
            }
        });
    }

    void handleButtons(int mask) {
        ui.post(() -> {
            boolean l = (mask & 1) != 0;
            boolean r = (mask & 2) != 0;
            if (l && !leftDown) startLeft();
            else if (!l && leftDown) endLeft();
            if (r && !rightDown) {
                rightDown = true;
                performGlobalAction(GLOBAL_ACTION_BACK);
            } else if (!r) {
                rightDown = false;
            }
        });
    }

    void handleWheel(int n) {
        ui.post(() -> doWheel(n));
    }

    // ---- gesture engine ----
    private void dispatch(GestureDescription.StrokeDescription stroke) {
        try {
            dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Exception ignored) {}
    }

    private void doTap() {
        Path p = new Path();
        p.moveTo(curX, curY);
        dispatch(new GestureDescription.StrokeDescription(p, 0, 60, false));
    }

    private void startLeft() {
        leftDown = true;
        leftDownMs = System.currentTimeMillis();
        downX = lastX = curX;
        downY = lastY = curY;
        dragDist = 0;
        dragStroke = null;
    }

    private void continueDrag() {
        try {
            if (dragStroke == null) {
                if (dragDist < 14) return; // still a possible tap
                Path p = new Path();
                p.moveTo(downX, downY);
                p.lineTo(curX, curY);
                dragStroke = new GestureDescription.StrokeDescription(p, 0, 30, true);
                dispatch(dragStroke);
            } else {
                Path seg = new Path();
                seg.moveTo(lastX, lastY);
                seg.lineTo(curX, curY);
                dragStroke = dragStroke.continueStroke(seg, 0, 30, true);
                dispatch(dragStroke);
            }
            lastX = curX;
            lastY = curY;
        } catch (Exception e) {
            dragStroke = null;
        }
    }

    private void endLeft() {
        leftDown = false;
        if (dragStroke != null) {
            try {
                Path seg = new Path();
                seg.moveTo(lastX, lastY);
                seg.lineTo(curX, curY);
                GestureDescription.StrokeDescription end =
                        dragStroke.continueStroke(seg, 0, 30, false);
                dispatch(end);
            } catch (Exception ignored) {}
            dragStroke = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTapUpMs < 350) {
            lastTapUpMs = 0;
            doTap();
            ui.postDelayed(this::doTap, 150);
        } else {
            lastTapUpMs = now;
            doTap();
        }
    }

    private void doWheel(int n) {
        float len = 360f * Math.min(Math.abs(n), 3);
        float endY = n > 0 ? curY - len : curY + len; // wheel down = swipe up
        endY = Math.max(0, Math.min(screenH - 1, endY));
        Path p = new Path();
        p.moveTo(curX, curY);
        p.lineTo(curX, endY);
        dispatch(new GestureDescription.StrokeDescription(p, 0, 200, false));
    }

    /** Ring + dot pointer, drawn on the accessibility overlay. */
    private static class CursorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(Context ctx) { super(ctx); }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 4f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(Color.argb(90, 0, 0, 0));
            c.drawCircle(cx, cy + 2f, r, paint);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(0x4F, 0xC3, 0xF7));
            c.drawCircle(cx, cy, 5f, paint);
        }
    }
}
