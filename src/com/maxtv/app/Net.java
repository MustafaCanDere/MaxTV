package com.maxtv.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Yönlendirmeleri (http↔https dahil) takip eden basit HTTP yardımcıları. */
public class Net {
    public interface Progress {
        void onProgress(long done, long total);
    }

    public static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int redirects = 0; redirects < 8; redirects++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "MaxTV (Android TV)");
            conn.setRequestProperty("Cache-Control", "no-cache");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("Yönlendirme hatası");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != 200) {
                conn.disconnect();
                throw new IOException("Sunucu yanıtı " + code);
            }
            return conn;
        }
        throw new IOException("Çok fazla yönlendirme");
    }

    public static void download(String url, File dest, Progress progress) throws IOException {
        HttpURLConnection conn = open(url);
        long total = conn.getContentLength();
        File tmp = new File(dest.getPath() + ".tmp");
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) progress.onProgress(done, total);
            }
        } finally {
            out.close();
            in.close();
            conn.disconnect();
        }
        if (!tmp.renameTo(dest)) {
            dest.delete();
            if (!tmp.renameTo(dest)) throw new IOException("Dosya yazılamadı");
        }
    }

    public static byte[] fetchBytes(String url, int maxBytes) throws IOException {
        HttpURLConnection conn = open(url);
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > maxBytes) throw new IOException("Dosya çok büyük");
            }
        } finally {
            in.close();
            conn.disconnect();
        }
        return out.toByteArray();
    }
}
