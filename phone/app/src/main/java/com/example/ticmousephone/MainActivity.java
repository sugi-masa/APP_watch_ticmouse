package com.example.ticmousephone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Setup / status screen for the TicMouse phone companion. */
public class MainActivity extends Activity {

    private TextView statusView;
    private Button toggleBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh();
            ui.postDelayed(this, 600);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(d, 24), dp(d, 28), dp(d, 24), dp(d, 24));
        root.setBackgroundColor(Color.rgb(0x14, 0x16, 0x19));

        TextView title = new TextView(this);
        title.setText("TicMouse Phone");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTextColor(Color.rgb(0xC8, 0xD2, 0xDC));
        statusView.setPadding(0, dp(d, 20), 0, dp(d, 20));
        root.addView(statusView);

        toggleBtn = new Button(this);
        toggleBtn.setTextSize(16);
        toggleBtn.setOnClickListener(v -> toggleLink());
        root.addView(toggleBtn);

        Button accBtn = new Button(this);
        accBtn.setText("アクセシビリティ設定を開く");
        accBtn.setTextSize(16);
        accBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(d, 12);
        accBtn.setLayoutParams(lp);
        root.addView(accBtn);

        TextView help = new TextView(this);
        help.setTextSize(13);
        help.setTextColor(Color.rgb(0x8A, 0x94, 0x9E));
        help.setPadding(0, dp(d, 20), 0, 0);
        help.setText("使い方\n"
                + "1. 「待ち受け開始」を押す\n"
                + "2. アクセシビリティ設定で「TicMouse Phone」をONにする\n"
                + "3. 時計の接続パネル（下バー長押し）で「スマホ接続」を押す\n\n"
                + "操作：レバー/十字でカーソル移動、左=タップ（長押し+移動でドラッグ、"
                + "2回押しでダブルタップ）、右=戻る、スクロールモードでホイール");
        root.addView(help);

        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(poll);
        ui.post(poll);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(poll);
        super.onPause();
    }

    private int dp(float density, int v) { return Math.round(v * density); }

    private void refresh() {
        if (statusView == null) return;
        String btPerm = Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                ? "OK" : "未許可（待ち受け開始時に聞きます）";
        statusView.setText(
                "待ち受け: " + (LinkService.running ? "実行中" : "停止中") + "\n"
                + "接続状態: " + CursorController.linkStatus + "\n"
                + "アクセシビリティ: " + (CursorController.accessibilityOn() ? "ON（カーソル表示中）" : "OFF") + "\n"
                + "Bluetooth権限: " + btPerm);
        toggleBtn.setText(LinkService.running ? "待ち受け停止" : "待ち受け開始");
    }

    private void toggleLink() {
        if (LinkService.running) {
            stopService(new Intent(this, LinkService.class));
            ui.postDelayed(this::refresh, 200);
            return;
        }
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;
        }
        startLink();
    }

    private void startLink() {
        Intent i = new Intent(this, LinkService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        ui.postDelayed(this::refresh, 200);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLink();
        }
        refresh();
    }
}
