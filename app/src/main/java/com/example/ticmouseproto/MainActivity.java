package com.example.ticmouseproto;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private MouseView mouseView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mouseView = new MouseView(this);
        setContentView(mouseView);
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
