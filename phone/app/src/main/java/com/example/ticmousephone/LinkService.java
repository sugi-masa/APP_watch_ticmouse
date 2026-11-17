package com.example.ticmousephone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Foreground service that listens for the watch over Bluetooth RFCOMM and
 * forwards packets to CursorController.
 * Protocol: [0x01, dxHi, dxLo, dyHi, dyLo] / [0x02, mask] / [0x03, wheel]
 */
public class LinkService extends Service {
    static final UUID TIC_UUID = UUID.fromString("7a9f3c21-5e4b-4d2a-9c81-0f6e5b8d3a47");
    static volatile boolean running = false;

    private Thread serverThread;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket clientSocket;

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        CursorController.linkStatus = "待機中（時計の接続待ち）";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    "ticmouse_link", "TicMouse接続", NotificationManager.IMPORTANCE_LOW));
        }
        startForegroundWith("待機中：時計からの接続を待っています");
        serverThread = new Thread(this::serverLoop, "ticmouse-link");
        serverThread.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        CursorController.watchConnected = false;
        CursorController.linkStatus = "待ち受け停止中";
        closeQuietly(clientSocket);
        closeQuietly(serverSocket);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "ticmouse_link")
                : new Notification.Builder(this);
        return b.setContentTitle("TicMouse Phone")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build();
    }

    private void startForegroundWith(String text) {
        Notification n = buildNotification(text);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            try { startForeground(1, n); } catch (Exception ignored) {}
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(1, buildNotification(text)); } catch (Exception ignored) {}
        }
    }

    private void serverLoop() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            CursorController.linkStatus = "Bluetoothアダプタなし";
            return;
        }
        while (running) {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("TicMousePhone", TIC_UUID);
                BluetoothSocket sock = serverSocket.accept();
                clientSocket = sock;
                CursorController.watchConnected = true;
                CursorController.linkStatus = "時計と接続中";
                updateNotification("接続中：時計とつながっています");
                readLoop(sock.getInputStream());
            } catch (Exception e) {
                if (running) {
                    CursorController.linkStatus = "待機エラー: " + e.getClass().getSimpleName()
                            + "（Bluetooth権限を確認）";
                }
            } finally {
                CursorController.watchConnected = false;
                closeQuietly(clientSocket);
                closeQuietly(serverSocket);
                clientSocket = null;
                serverSocket = null;
                if (running) {
                    CursorController.linkStatus = "待機中（時計の接続待ち）";
                    updateNotification("待機中：時計からの接続を待っています");
                }
            }
        }
    }

    private void readLoop(InputStream in) throws IOException {
        byte[] buf = new byte[4];
        while (running) {
            int op = in.read();
            if (op < 0) return;
            switch (op) {
                case 0x01: {
                    if (!readFully(in, buf, 4)) return;
                    int dx = (short) ((buf[0] << 8) | (buf[1] & 0xFF));
                    int dy = (short) ((buf[2] << 8) | (buf[3] & 0xFF));
                    CursorController.onMove(dx, dy);
                    break;
                }
                case 0x02: {
                    int m = in.read();
                    if (m < 0) return;
                    CursorController.onButtons(m);
                    break;
                }
                case 0x03: {
                    int w = in.read();
                    if (w < 0) return;
                    CursorController.onWheel((byte) w);
                    break;
                }
                default:
                    break; // unknown byte: skip and resync on next opcode
            }
        }
    }

    private boolean readFully(InputStream in, byte[] buf, int n) throws IOException {
        int got = 0;
        while (got < n) {
            int r = in.read(buf, got, n - got);
            if (r < 0) return false;
            got += r;
        }
        return true;
    }

    private void closeQuietly(BluetoothServerSocket s) {
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }

    private void closeQuietly(BluetoothSocket s) {
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }
}
