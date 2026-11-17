package com.example.ticmouseproto2;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private MouseView mouseView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Wear OS の端スワイプ（戻るジェスチャー）でアプリが閉じないようにする。
        // テーマ側の windowSwipeToDismiss=false と合わせて二重に無効化する。
        // 終了したいときは時計の側面ボタンでホームに戻る。
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { });
        }
        mouseView = new MouseView(this);
        setContentView(mouseView);
    }

    @Override
    public void onBackPressed() {
        // スワイプで閉じない（何もしない）。終了は側面ボタンで。
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MouseView.REQ_BT_CONNECT && mouseView != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            mouseView.onBluetoothPermissionResult(granted);
        }
    }

    @Override
    protected void onDestroy() {
        if (mouseView != null) mouseView.shutdown();
        super.onDestroy();
    }
}
