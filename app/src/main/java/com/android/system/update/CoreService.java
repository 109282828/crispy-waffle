package com.android.system.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class CoreService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel("sys_core", "System Service", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "sys_core")
                .setContentTitle("System Update")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
            startForeground(1, n);
        }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
