package com.example.ticmouseproto;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.Executor;

/**
 * TicMouse UI prototype (Design C - hybrid), layout/design v0.3.
 * Center hub (ring + lever) diameter = 65% of the face, on the top layer.
 * Around it, three 120-degree arc sectors on a lower layer:
 * left-click (upper-left, dark gray-green), right-click (upper-right,
 * dark gray-blue), info zone (bottom). Click sectors are only hit
 * where the hub does not cover them. Buttons are drawn with a drop
 * shadow, a top-light gradient and an edge highlight for depth.
 * Style: graphite / steel gray / muted cyan, mechanical instrument look.
 * Local demo only: the on-screen cursor is a stand-in pointer.
 * No connection to phone/PC is made, except the HID registration
 * test in the test panel.
 */
public class MouseView extends View {
    static final int REQ_BT_CONNECT = 42;

    // Palette v0.3 (graphite instrument)
    private static final int COL_BG_OUT = Color.BLACK;
    private static final int COL_BG_T = Color.rgb(0x2B, 0x2F, 0x34);
    private static final int COL_BG_M = Color.rgb(0x17, 0x19, 0x1C);
    private static final int COL_BG_B = Color.rgb(0x0B, 0x0C, 0x0E);
    private static final int COL_EDGE = Color.rgb(0x3A, 0x3F, 0x44);
    private static final int COL_TICK = Color.rgb(0x4A, 0x50, 0x56);
    private static final int COL_TICK_MAJ = Color.rgb(0x6B, 0x72, 0x78);
    private static final int COL_CYAN = Color.rgb(0x7F, 0xD4, 0xE8);
    private static final int COL_TEXT = Color.rgb(0xD5, 0xDB, 0xE0);
    private static final int COL_MUTED = Color.rgb(0x9A, 0xA3, 0xAB);
    private static final int COL_FAINT = Color.rgb(0x7C, 0x84, 0x8B);
    private static final int COL_HUB_FACE = Color.rgb(0x14, 0x15, 0x18);
    private static final int COL_HUB_LINE = Color.rgb(0x7D, 0x85, 0x8D);
    private static final int COL_PAD_LINE = Color.rgb(0x66, 0x6E, 0x75);
    private static final int COL_L_STROKE = Color.rgb(0x8A, 0xA8, 0x92);
    private static final int COL_R_STROKE = Color.rgb(0x8A, 0xA2, 0xB8);
    private static final int COL_B_STROKE = Color.rgb(0x56, 0x5D, 0x63);
    private static final int COL_L_LABEL = Color.rgb(0xDC, 0xEB, 0xE0);
    private static final int COL_R_LABEL = Color.rgb(0xDF, 0xE9, 0xF2);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;

    // Geometry (computed in onSizeChanged). R = face radius.
    private float cx, cy, R;
    private float hubX, hubY, ringOuter, knobR;
    private float padIn, padOut;
    private float btnInner, btnOuter;
    private Shader gradFace, gradSecL, gradSecR, gradSecB, gradPad;

    // Demo cursor
    private float curX, curY;
    private boolean cursorInit = false;

    // State
    private int countL = 0, countR = 0, countDouble = 0;
    private float scrollTotal = 0f;
    private boolean scrollMode = false;
    private String lastEvent = "タッチで操作感を試してね";
    private long lastLeftTapUpMs = 0;

    private static final int MODE_CLICK_L = 1, MODE_CLICK_R = 2, MODE_LEVER = 3,
            MODE_RING = 4, MODE_BOTTOM = 5;

    private static class Touch {
        int mode;
        int sector; // 0=up 1=right 2=down 3=left (ring)
        long downMs;
        long lastRepeatMs;
        boolean dragging;
        boolean panelOpened;
        boolean bottomScroll; // bottom sector, right half (scroll toggle zone)
    }

    private final SparseArray<Touch> touches = new SparseArray<>();
    private int leverPointerId = -1;
    private float leverDx = 0f, leverDy = 0f; // -1..1 relative to max offset

    // Test panel / HID
    private boolean panelOpen = false;
    private final RectF panelRect = new RectF(), panelHidBtn = new RectF(), panelCloseBtn = new RectF();
    private String hidStatus = "未実行";
    private BluetoothHidDevice hidDevice;
    private boolean hidRegistered = false;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            tick();
            invalidate();
            handler.postDelayed(this, 33);
        }
    };

    public MouseView(Context ctx) {
        super(ctx);
        vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(loop);
        super.onDetachedFromWindow();
    }

    void shutdown() {
        handler.removeCallbacks(loop);
        unregisterHid();
    }

    private void buzz(long ms) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}
    }

    // ---------- geometry ----------
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f; cy = h / 2f;
        R = Math.min(w, h) / 2f * 0.985f;
        hubX = cx; hubY = cy;
        ringOuter = R * 0.65f;          // hub diameter = 65% of face diameter
        knobR = ringOuter * 0.43f;
        padIn = R * 0.365f; padOut = R * 0.58f;
        btnInner = R * 0.685f;          // visual inner edge of the arc sectors
        btnOuter = R * 0.90f;           // visual outer edge of the arc sectors
        gradFace = new RadialGradient(cx, cy - R * 0.1f, R * 1.05f,
                new int[]{COL_BG_T, COL_BG_M, COL_BG_B}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        gradSecL = new LinearGradient(0, cy - btnOuter, 0, cy + btnOuter,
                Color.rgb(0x2F, 0x3D, 0x34), Color.rgb(0x23, 0x2D, 0x27), Shader.TileMode.CLAMP);
        gradSecR = new LinearGradient(0, cy - btnOuter, 0, cy + btnOuter,
                Color.rgb(0x2F, 0x3D, 0x4A), Color.rgb(0x23, 0x2C, 0x36), Shader.TileMode.CLAMP);
        gradSecB = new LinearGradient(0, cy - btnOuter, 0, cy + btnOuter,
                Color.rgb(0x26, 0x29, 0x2D), Color.rgb(0x1B, 0x1E, 0x21), Shader.TileMode.CLAMP);
        gradPad = new LinearGradient(0, cy - padOut, 0, cy + padOut,
                Color.rgb(0x2B, 0x2E, 0x33), Color.rgb(0x1F, 0x21, 0x24), Shader.TileMode.CLAMP);
        if (!cursorInit) { curX = cx; curY = cy - R * 0.79f; cursorInit = true; }
        panelRect.set(cx - R * 0.80f, cy - R * 0.64f, cx + R * 0.80f, cy + R * 0.64f);
        panelHidBtn.set(panelRect.left + R * 0.07f, panelRect.bottom - R * 0.30f, cx - R * 0.03f, panelRect.bottom - R * 0.07f);
        panelCloseBtn.set(cx + R * 0.03f, panelRect.bottom - R * 0.30f, panelRect.right - R * 0.07f, panelRect.bottom - R * 0.07f);
    }

    /** Clock angle: 0 = 12 o'clock, clockwise, 0..360. */
    private float clockDeg(float x, float y) {
        double a = Math.toDegrees(Math.atan2(x - cx, -(y - cy)));
        return (float) ((a + 360) % 360);
    }

    private float stepPx() { return Math.max(6f, R * 0.06f); } // ~12px on a 400px face

    // ---------- simulation ----------
    private void tick() {
        long now = System.currentTimeMillis();
        // Lever continuous movement
        if (leverPointerId != -1) {
            float mag = (float) Math.hypot(leverDx, leverDy);
            if (mag > 0.08f) {
                float mm = Math.min(1f, mag);
                float speed = (R * 2.2f) * mm * mm; // px per second, squared curve
                float ux = leverDx / (mag == 0 ? 1 : mag), uy = leverDy / (mag == 0 ? 1 : mag);
                float dt = 0.033f;
                if (scrollMode) {
                    scrollTotal += -uy * speed * dt / stepPx();
                    moveCursor(ux * speed * dt, 0);
                } else {
                    moveCursor(ux * speed * dt, uy * speed * dt);
                }
            }
        }
        // Per-pointer timed behaviour
        for (int i = 0; i < touches.size(); i++) {
            Touch t = touches.valueAt(i);
            long held = now - t.downMs;
            if ((t.mode == MODE_CLICK_L || t.mode == MODE_CLICK_R) && held >= 300 && !t.dragging) {
                t.dragging = true;
                lastEvent = (t.mode == MODE_CLICK_L ? "左" : "右") + "ドラッグ中（長押し）";
                buzz(25);
            }
            if (t.mode == MODE_RING && held >= 350) {
                long interval = held > 1500 ? 50 : (held > 700 ? 85 : 130);
                if (now - t.lastRepeatMs >= interval) {
                    t.lastRepeatMs = now;
                    ringStep(t.sector);
                }
            }
            if (t.mode == MODE_BOTTOM && held >= 600 && !t.panelOpened) {
                t.panelOpened = true;
                panelOpen = true;
                buzz(30);
            }
        }
    }

    private void moveCursor(float dx, float dy) {
        curX += dx; curY += dy;
        float d = (float) Math.hypot(curX - cx, curY - cy);
        float max = R * 0.93f;
        if (d > max) { curX = cx + (curX - cx) / d * max; curY = cy + (curY - cy) / d * max; }
    }

    private void ringStep(int sector) {
        if (scrollMode) {
            if (sector == 0) scrollTotal += 1;
            else if (sector == 2) scrollTotal -= 1;
            else moveCursor(sector == 1 ? stepPx() : -stepPx(), 0);
            lastEvent = "スクロール " + (int) scrollTotal;
            return;
        }
        switch (sector) {
            case 0: moveCursor(0, -stepPx()); break;
            case 1: moveCursor(stepPx(), 0); break;
            case 2: moveCursor(0, stepPx()); break;
            default: moveCursor(-stepPx(), 0); break;
        }
        lastEvent = "十字ステップ " + "▲▶▼◀".charAt(sector);
    }

    // ---------- drawing ----------
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(COL_BG_OUT);
        // Face background
        p.setShader(gradFace); p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        // Instrument hairlines: concentric circles + crosshair
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f);
        p.setColor(Color.argb(13, 255, 255, 255));
        c.drawCircle(cx, cy, R * 0.81f, p);
        c.drawCircle(cx, cy, R * 0.42f, p);
        p.setColor(Color.argb(9, 255, 255, 255));
        c.drawLine(cx, cy - R, cx, cy + R, p);
        c.drawLine(cx - R, cy, cx + R, cy, p);
        // Tick ring
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            boolean major = i % 5 == 0;
            float r1 = R * 0.972f, r0 = R * (major ? 0.918f : 0.93f);
            p.setColor(major ? COL_TICK_MAJ : COL_TICK); p.setStrokeWidth(major ? 3f : 2f);
            c.drawLine(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, p);
        }
        // Face edge
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.02f); p.setColor(COL_EDGE);
        c.drawCircle(cx, cy, R * 0.99f, p);
        // 12 o'clock notch (muted cyan)
        p.setStyle(Paint.Style.FILL); p.setColor(COL_CYAN);
        c.drawRect(cx - R * 0.008f, cy - R * 0.99f, cx + R * 0.008f, cy - R * 0.925f, p);

        drawSectors(c);
        drawHub(c);
        drawBottomInfo(c);
        drawCursor(c);
        if (panelOpen) drawPanel(c);
    }

    private boolean isPressed(int mode) {
        for (int i = 0; i < touches.size(); i++) if (touches.valueAt(i).mode == mode) return true;
        return false;
    }

    private boolean isDragging(int mode) {
        for (int i = 0; i < touches.size(); i++) {
            Touch t = touches.valueAt(i);
            if (t.mode == mode && t.dragging) return true;
        }
        return false;
    }

    /** Annular sector path around (ox, oy). Angles: canvas degrees (0 = 3 o'clock, clockwise). */
    private Path sectorPathAt(float ox, float oy, float rIn, float rOut, float startDeg, float sweepDeg) {
        Path path = new Path();
        RectF outer = new RectF(ox - rOut, oy - rOut, ox + rOut, oy + rOut);
        RectF inner = new RectF(ox - rIn, oy - rIn, ox + rIn, oy + rIn);
        path.arcTo(outer, startDeg, sweepDeg);
        path.arcTo(inner, startDeg + sweepDeg, -sweepDeg);
        path.close();
        return path;
    }

    private void drawSectors(Canvas c) {
        float gap = 2.5f, sweep = 120f - gap * 2;
        // Bottom info sector: canvas 30..150 deg
        drawSectorButton(c, 30 + gap, sweep, gradSecB, COL_B_STROKE, false, false);
        // Right click: canvas -90..30 deg (clock 0..120)
        drawSectorButton(c, -90 + gap, sweep, gradSecR, COL_R_STROKE,
                isPressed(MODE_CLICK_R), isDragging(MODE_CLICK_R));
        // Left click: canvas 150..270 deg (clock 240..360)
        drawSectorButton(c, 150 + gap, sweep, gradSecL, COL_L_STROKE,
                isPressed(MODE_CLICK_L), isDragging(MODE_CLICK_L));
        // Labels along the arc (tangential text)
        drawArcLabel(c, 300, "左クリック", "BTN-L", countL, COL_L_LABEL);
        drawArcLabel(c, 60, "右クリック", "BTN-R", countR, COL_R_LABEL);
    }

    /** Sector with drop shadow, top-light gradient fill, edge highlight (depth). */
    private void drawSectorButton(Canvas c, float startDeg, float sweepDeg, Shader fill,
                                  int strokeColor, boolean pressed, boolean dragging) {
        // Drop shadow
        Path shadow = sectorPathAt(cx, cy + R * 0.016f, btnInner, btnOuter, startDeg, sweepDeg);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(95, 0, 0, 0));
        c.drawPath(shadow, p);
        // Fill
        Path path = sectorPathAt(cx, cy, btnInner, btnOuter, startDeg, sweepDeg);
        p.setShader(fill);
        c.drawPath(path, p);
        p.setShader(null);
        if (pressed) {
            p.setColor(Color.argb(dragging ? 52 : 26, 255, 255, 255));
            c.drawPath(path, p);
        }
        // Edge highlight on the outer arc + shadow line on the inner arc
        RectF outerRect = new RectF(cx - (btnOuter - R * 0.008f), cy - (btnOuter - R * 0.008f),
                cx + (btnOuter - R * 0.008f), cy + (btnOuter - R * 0.008f));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.008f);
        p.setColor(Color.argb(46, 255, 255, 255));
        c.drawArc(outerRect, startDeg + 4, sweepDeg - 8, false, p);
        RectF innerRect = new RectF(cx - (btnInner + R * 0.008f), cy - (btnInner + R * 0.008f),
                cx + (btnInner + R * 0.008f), cy + (btnInner + R * 0.008f));
        p.setColor(Color.argb(70, 0, 0, 0));
        c.drawArc(innerRect, startDeg + 4, sweepDeg - 8, false, p);
        // Outline
        p.setStrokeWidth(R * 0.012f);
        p.setColor(dragging ? COL_CYAN : strokeColor);
        p.setStyle(Paint.Style.STROKE);
        c.drawPath(path, p);
    }

    /** Text that follows the arc at the sector's middle clock angle. */
    private void drawArcLabel(Canvas c, float clockMid, String main, String micro, int count, int color) {
        double rad = Math.toRadians(clockMid);
        float rMid = (btnInner + btnOuter) / 2f;
        float x = cx + (float) Math.sin(rad) * rMid;
        float y = cy - (float) Math.cos(rad) * rMid;
        c.save();
        c.translate(x, y);
        c.rotate(clockMid);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color); p.setTextSize(R * 0.072f); p.setFakeBoldText(true);
        c.drawText(main, 0, -R * 0.008f, p);
        p.setFakeBoldText(false); p.setTextSize(R * 0.045f); p.setColor(COL_FAINT);
        p.setLetterSpacing(0.25f);
        c.drawText(count > 0 ? micro + " ×" + count : micro, 0, R * 0.062f, p);
        p.setLetterSpacing(0f);
        c.restore();
    }

    private void drawHub(Canvas c) {
        // Hub drop shadow over the sectors
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(110, 0, 0, 0));
        c.drawCircle(hubX + R * 0.008f, hubY + R * 0.018f, ringOuter, p);
        // Hub face
        p.setColor(COL_HUB_FACE);
        c.drawCircle(hubX, hubY, ringOuter, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.008f);
        p.setColor(Color.argb(14, 255, 255, 255));
        c.drawCircle(hubX, hubY, R * 0.60f, p);
        p.setStrokeWidth(R * 0.013f);
        p.setColor(scrollMode ? COL_CYAN : COL_HUB_LINE);
        c.drawCircle(hubX, hubY, ringOuter, p);
        // Ring pads (cross-key sectors) with shadow + gradient
        for (int s = 0; s < 4; s++) {
            float start = s * 90 - 90 - 35, sweep = 70;
            Path pShadow = sectorPathAt(hubX, hubY + R * 0.012f, padIn, padOut, start, sweep);
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(85, 0, 0, 0));
            c.drawPath(pShadow, p);
            Path pad = sectorPathAt(hubX, hubY, padIn, padOut, start, sweep);
            p.setShader(gradPad);
            c.drawPath(pad, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.008f); p.setColor(COL_PAD_LINE);
            c.drawPath(pad, p);
        }
        // Active pad highlight
        for (int i = 0; i < touches.size(); i++) {
            Touch t = touches.valueAt(i);
            if (t.mode == MODE_RING) {
                float start = t.sector * 90 - 90 - 35;
                Path pad = sectorPathAt(hubX, hubY, padIn, padOut, start, 70);
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(44, 0x7F, 0xD4, 0xE8));
                c.drawPath(pad, p);
            }
        }
        // Chevron arrows
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.011f); p.setColor(COL_TEXT);
        for (int s = 0; s < 4; s++) {
            c.save();
            c.rotate(s * 90, hubX, hubY);
            Path ch = new Path();
            ch.moveTo(hubX, hubY - R * 0.52f);
            ch.lineTo(hubX - R * 0.047f, hubY - R * 0.52f + R * 0.068f);
            ch.lineTo(hubX + R * 0.047f, hubY - R * 0.52f + R * 0.068f);
            ch.close();
            c.drawPath(ch, p);
            c.restore();
        }
        // Knob (lever): shadow, knurled edge, metal body
        float maxOff = ringOuter - knobR;
        float kx = hubX + leverDx * maxOff, ky = hubY + leverDy * maxOff;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(120, 0, 0, 0));
        c.drawCircle(kx + R * 0.012f, ky + R * 0.02f, knobR + R * 0.014f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.032f); p.setColor(COL_TICK);
        p.setPathEffect(new DashPathEffect(new float[]{R * 0.02f, R * 0.034f}, 0));
        c.drawCircle(kx, ky, knobR + R * 0.016f, p);
        p.setPathEffect(null);
        RadialGradient kg = new RadialGradient(kx - knobR * 0.3f, ky - knobR * 0.3f, knobR * 1.25f,
                new int[]{Color.rgb(0x43, 0x48, 0x4E), Color.rgb(0x2A, 0x2D, 0x31), Color.rgb(0x1D, 0x1F, 0x22)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        p.setShader(kg); p.setStyle(Paint.Style.FILL);
        c.drawCircle(kx, ky, knobR, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(R * 0.008f); p.setColor(Color.rgb(0x87, 0x8F, 0x96));
        c.drawCircle(kx, ky, knobR, p);
        // Center marker
        p.setStrokeWidth(R * 0.009f); p.setColor(COL_CYAN);
        c.drawCircle(kx, ky, R * 0.047f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(kx, ky, R * 0.015f, p);
        p.setColor(COL_MUTED); p.setTextSize(R * 0.046f); p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.3f);
        c.drawText("LEVER", kx, ky + R * 0.175f, p);
        p.setLetterSpacing(0f);
    }

    private void drawBottomInfo(Canvas c) {
        // Event text (②) centered in the bottom sector
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
        p.setColor(COL_TEXT); p.setTextSize(R * 0.056f);
        String info = lastEvent + (countDouble > 0 ? "（W×" + countDouble + "）" : "");
        if (scrollMode || Math.abs(scrollTotal) >= 1) info += "  SC:" + (int) scrollTotal;
        c.drawText(info, cx, cy + R * 0.815f, p);
        // LOCAL indicator at clock 207 deg, scroll toggle label at clock 153 deg
        drawBottomSide(c, 207, true);
        drawBottomSide(c, 153, false);
        // Micro system label
        p.setColor(Color.rgb(0x56, 0x5D, 0x63)); p.setTextSize(R * 0.038f);
        p.setLetterSpacing(0.3f);
        c.drawText("SYS.UI-03", cx, cy + R * 0.955f, p);
        p.setLetterSpacing(0f);
    }

    private void drawBottomSide(Canvas c, float clockDeg, boolean isLocal) {
        double rad = Math.toRadians(clockDeg);
        float rMid = (btnInner + btnOuter) / 2f;
        float x = cx + (float) Math.sin(rad) * rMid;
        float y = cy - (float) Math.cos(rad) * rMid;
        p.setStyle(Paint.Style.FILL); p.setTextSize(R * 0.05f);
        p.setTextAlign(Paint.Align.CENTER);
        if (isLocal) {
            float tw = p.measureText("LOCAL");
            p.setColor(COL_CYAN);
            c.drawCircle(x - tw / 2f - R * 0.036f, y - R * 0.017f, R * 0.019f, p);
            p.setColor(COL_MUTED);
            p.setLetterSpacing(0.2f);
            c.drawText("LOCAL", x, y, p);
            p.setLetterSpacing(0f);
        } else {
            p.setColor(scrollMode ? COL_CYAN : COL_MUTED);
            p.setFakeBoldText(scrollMode);
            p.setLetterSpacing(0.15f);
            c.drawText(scrollMode ? "SCROLL ON" : "SCROLL", x, y, p);
            p.setLetterSpacing(0f);
            p.setFakeBoldText(false);
        }
    }

    private void drawCursor(Canvas c) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(COL_TEXT);
        c.drawCircle(curX, curY, R * 0.045f, p);
        float l = R * 0.075f;
        c.drawLine(curX - l, curY, curX - R * 0.02f, curY, p);
        c.drawLine(curX + R * 0.02f, curY, curX + l, curY, p);
        c.drawLine(curX, curY - l, curX, curY - R * 0.02f, p);
        c.drawLine(curX, curY + R * 0.02f, curX, curY + l, p);
        p.setStyle(Paint.Style.FILL); p.setColor(COL_CYAN);
        c.drawCircle(curX, curY, R * 0.014f, p);
    }

    private void drawPanel(Canvas c) {
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(245, 14, 15, 17));
        c.drawRoundRect(panelRect, R * 0.08f, R * 0.08f, p);
        p.setStyle(Paint.Style.STROKE); p.setColor(COL_HUB_LINE); p.setStrokeWidth(2f);
        c.drawRoundRect(panelRect, R * 0.08f, R * 0.08f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(R * 0.075f); p.setFakeBoldText(true);
        c.drawText("テストパネル", cx, panelRect.top + R * 0.13f, p);
        p.setFakeBoldText(false); p.setTextSize(R * 0.058f); p.setColor(COL_MUTED);
        c.drawText("端末API: " + Build.VERSION.SDK_INT + " / Proto 0.3", cx, panelRect.top + R * 0.24f, p);
        c.drawText("HID登録テスト（PC向け）", cx, panelRect.top + R * 0.36f, p);
        p.setColor(COL_CYAN); p.setTextSize(R * 0.062f);
        String s = hidStatus;
        if (s.length() > 18) {
            c.drawText(s.substring(0, 18), cx, panelRect.top + R * 0.48f, p);
            c.drawText(s.substring(18), cx, panelRect.top + R * 0.59f, p);
        } else {
            c.drawText(s, cx, panelRect.top + R * 0.48f, p);
        }
        // Buttons
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(0x24, 0x27, 0x2B));
        c.drawRoundRect(panelHidBtn, R * 0.05f, R * 0.05f, p);
        c.drawRoundRect(panelCloseBtn, R * 0.05f, R * 0.05f, p);
        p.setStyle(Paint.Style.STROKE); p.setColor(COL_PAD_LINE); p.setStrokeWidth(1.5f);
        c.drawRoundRect(panelHidBtn, R * 0.05f, R * 0.05f, p);
        c.drawRoundRect(panelCloseBtn, R * 0.05f, R * 0.05f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(R * 0.06f);
        c.drawText(hidRegistered ? "HID解除" : "HIDテスト", panelHidBtn.centerX(), panelHidBtn.centerY() + R * 0.02f, p);
        c.drawText("閉じる", panelCloseBtn.centerX(), panelCloseBtn.centerY() + R * 0.02f, p);
    }

    // ---------- touch ----------
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int pid = e.getPointerId(idx);
        long now = System.currentTimeMillis();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                handleDown(pid, x, y, now);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    Touch t = touches.get(id);
                    if (t != null && t.mode == MODE_LEVER) {
                        updateLever(e.getX(i), e.getY(i));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                float x = e.getX(idx), y = e.getY(idx);
                handleUp(pid, x, y, now);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                touches.clear();
                leverPointerId = -1; leverDx = 0; leverDy = 0;
                break;
            }
        }
        invalidate();
        return true;
    }

    private void handleDown(int pid, float x, float y, long now) {
        if (panelOpen) {
            if (panelHidBtn.contains(x, y)) { onHidButton(); return; }
            if (panelCloseBtn.contains(x, y)) { panelOpen = false; return; }
            return; // consume everything else while panel is open
        }
        Touch t = new Touch(); t.downMs = now; t.lastRepeatMs = now;
        float dh = (float) Math.hypot(x - hubX, y - hubY);
        // Top layer first: the hub (③) wins wherever it covers.
        if (dh <= knobR * 1.15f && leverPointerId == -1) {
            t.mode = MODE_LEVER; touches.put(pid, t);
            leverPointerId = pid; updateLever(x, y);
            return;
        }
        if (dh <= ringOuter * 1.02f) {
            float th = clockDeg(x, y);
            int sector;
            if (th >= 315 || th < 45) sector = 0;
            else if (th < 135) sector = 1;
            else if (th < 225) sector = 2;
            else sector = 3;
            t.mode = MODE_RING; t.sector = sector; touches.put(pid, t);
            ringStep(sector);
            return;
        }
        // Lower layer: the arc sectors (①), only where the hub does not cover.
        float th = clockDeg(x, y);
        if (th >= 120 && th < 240) {
            t.mode = MODE_BOTTOM; t.bottomScroll = th < 180; touches.put(pid, t);
        } else if (th >= 240) {
            t.mode = MODE_CLICK_L; touches.put(pid, t);
        } else {
            t.mode = MODE_CLICK_R; touches.put(pid, t);
        }
    }

    private void updateLever(float x, float y) {
        float maxOff = ringOuter - knobR;
        float dx = x - hubX, dy = y - hubY;
        float d = (float) Math.hypot(dx, dy);
        if (d > maxOff) { dx = dx / d * maxOff; dy = dy / d * maxOff; }
        leverDx = dx / maxOff; leverDy = dy / maxOff;
    }

    private void handleUp(int pid, float x, float y, long now) {
        Touch t = touches.get(pid);
        if (t == null) return;
        touches.remove(pid);
        long held = now - t.downMs;
        if (t.mode == MODE_CLICK_L || t.mode == MODE_CLICK_R) {
            boolean left = t.mode == MODE_CLICK_L;
            if (t.dragging) {
                lastEvent = (left ? "左" : "右") + "ドラッグ終了";
            } else if (held < 300) {
                if (left) {
                    if (now - lastLeftTapUpMs < 350) { countDouble++; lastEvent = "左ダブルクリック"; lastLeftTapUpMs = 0; }
                    else { countL++; lastEvent = "左クリック"; lastLeftTapUpMs = now; }
                } else { countR++; lastEvent = "右クリック"; }
                buzz(10);
            }
        }
        if (t.mode == MODE_BOTTOM && t.bottomScroll && !t.panelOpened && held < 600) {
            scrollMode = !scrollMode;
            lastEvent = scrollMode ? "スクロールモード ON" : "スクロールモード OFF";
            buzz(15);
        }
        if (t.mode == MODE_LEVER && pid == leverPointerId) {
            leverPointerId = -1; leverDx = 0; leverDy = 0;
        }
    }

    // ---------- HID registration test ----------
    private void onHidButton() {
        if (hidRegistered) { unregisterHid(); hidStatus = "解除した"; return; }
        if (Build.VERSION.SDK_INT >= 31) {
            Context ctx = getContext();
            if (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                hidStatus = "Bluetooth権限を許可してね";
                ((Activity) ctx).requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
                return;
            }
        }
        startHidRegister();
    }

    void onBluetoothPermissionResult(boolean granted) {
        if (granted) { hidStatus = "権限OK、登録中…"; startHidRegister(); }
        else hidStatus = "権限がなくて未実行";
        invalidate();
    }

    private void startHidRegister() {
        try {
            BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm == null) { hidStatus = "Bluetooth管理なし"; return; }
            BluetoothAdapter adapter = bm.getAdapter();
            if (adapter == null) { hidStatus = "Bluetoothアダプタなし"; return; }
            if (!adapter.isEnabled()) { hidStatus = "BluetoothがOFF"; return; }
            hidStatus = "HIDサービス接続中…";
            boolean ok = adapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile != BluetoothProfile.HID_DEVICE) return;
                    hidDevice = (BluetoothHidDevice) proxy;
                    doRegisterApp();
                }
                @Override public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HID_DEVICE) { hidDevice = null; hidRegistered = false; }
                }
            }, BluetoothProfile.HID_DEVICE);
            if (!ok) hidStatus = "HIDサービス取得に失敗";
        } catch (Exception ex) {
            hidStatus = "例外: " + ex.getClass().getSimpleName();
        }
        invalidate();
    }

    private void doRegisterApp() {
        try {
            byte[] descriptor = new byte[]{
                    (byte) 0x05, (byte) 0x01, (byte) 0x09, (byte) 0x02, (byte) 0xA1, (byte) 0x01,
                    (byte) 0x09, (byte) 0x01, (byte) 0xA1, (byte) 0x00,
                    (byte) 0x05, (byte) 0x09, (byte) 0x19, (byte) 0x01, (byte) 0x29, (byte) 0x03,
                    (byte) 0x15, (byte) 0x00, (byte) 0x25, (byte) 0x01, (byte) 0x95, (byte) 0x03,
                    (byte) 0x75, (byte) 0x01, (byte) 0x81, (byte) 0x02,
                    (byte) 0x95, (byte) 0x01, (byte) 0x75, (byte) 0x05, (byte) 0x81, (byte) 0x03,
                    (byte) 0x05, (byte) 0x01, (byte) 0x09, (byte) 0x30, (byte) 0x09, (byte) 0x31,
                    (byte) 0x09, (byte) 0x38, (byte) 0x15, (byte) 0x81, (byte) 0x25, (byte) 0x7F,
                    (byte) 0x75, (byte) 0x08, (byte) 0x95, (byte) 0x03, (byte) 0x81, (byte) 0x06,
                    (byte) 0xC0, (byte) 0xC0
            };
            BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                    "TicMouseProto", "UI prototype HID test", "Sei",
                    BluetoothHidDevice.SUBCLASS1_MOUSE, descriptor);
            Executor executor = getContext().getMainExecutor();
            BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
                @Override public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
                    hidRegistered = registered;
                    hidStatus = registered ? "登録成功！ OS4でもHID可" : "登録失敗／解除された";
                    invalidate();
                }
                @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    invalidate();
                }
            };
            boolean accepted = hidDevice.registerApp(sdp, null, null, executor, callback);
            hidStatus = accepted ? "登録要求OK、結果待ち…" : "登録要求が拒否された";
        } catch (Exception ex) {
            hidStatus = "登録例外: " + ex.getClass().getSimpleName();
        }
        invalidate();
    }

    private void unregisterHid() {
        try {
            if (hidDevice != null && hidRegistered) hidDevice.unregisterApp();
        } catch (Exception ignored) {}
        hidRegistered = false;
    }
}
