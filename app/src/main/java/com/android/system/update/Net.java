package com.android.system.update;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Net {
    public static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestMethod("GET");
        try (InputStream in = c.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    public static String post(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        try (InputStream in = c.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }
}
