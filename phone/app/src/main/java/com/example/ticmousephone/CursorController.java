package com.example.ticmousephone;

/** Bridge between LinkService (Bluetooth) and CursorAccessibilityService (gestures). */
public class CursorController {
    static volatile CursorAccessibilityService service;
    static volatile boolean watchConnected = false;
    static volatile String linkStatus = "待ち受け停止中";

    static void register(CursorAccessibilityService s) { service = s; }
    static void unregister(CursorAccessibilityService s) { if (service == s) service = null; }
    static boolean accessibilityOn() { return service != null; }

    static void onMove(int dx, int dy) {
        CursorAccessibilityService s = service;
        if (s != null) s.handleMove(dx, dy);
    }

    static void onButtons(int mask) {
        CursorAccessibilityService s = service;
        if (s != null) s.handleButtons(mask);
    }

    static void onWheel(int n) {
        CursorAccessibilityService s = service;
        if (s != null) s.handleWheel(n);
    }
}
