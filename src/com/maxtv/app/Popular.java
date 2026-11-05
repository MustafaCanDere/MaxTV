package com.maxtv.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "En popüler" (ülkede genel olarak en çok izlenen kanallar, GitHub'daki popular.json'dan)
 * ve "En çok izlediklerim" (bu cihazda kaç kez açıldığı) listeleri.
 */
public class Popular {
    private static final String URL = "https://github.com/MustafaCanDere/MaxTV/raw/main/popular.json";
    private static final Locale TR = new Locale("tr", "TR");
    private static volatile JSONObject data;

    /** Uygulama açılışında arka planda güncel listeyi indirir. */
    public static void refresh(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File f = new File(ctx.getCacheDir(), "popular.json");
                try {
                    Net.download(URL + "?t=" + System.currentTimeMillis(), f, null);
                    data = new JSONObject(read(new FileInputStream(f)));
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static JSONObject get(Context ctx) {
        if (data != null) return data;
        try {
            File f = new File(ctx.getCacheDir(), "popular.json");
            if (f.exists()) return data = new JSONObject(read(new FileInputStream(f)));
        } catch (Exception ignored) {
        }
        try {
            return data = new JSONObject(read(ctx.getResources().openRawResource(R.raw.popular)));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Bölge adresinden anahtar: countries/tr.m3u → "tr", languages/tur.m3u → "tr". */
    public static String keyFor(String regionUrl) {
        if (regionUrl.contains("languages/tur")) return "tr";
        int i = regionUrl.indexOf("countries/");
        if (i < 0) return null;
        String rest = regionUrl.substring(i + 10);
        int dot = rest.indexOf('.');
        return dot > 0 ? rest.substring(0, dot) : rest;
    }

    static String norm(String s) {
        s = s.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").toLowerCase(TR);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    public static List<Channel> popular(Context ctx, String regionUrl, List<Channel> channels) {
        List<Channel> out = new ArrayList<Channel>();
        String key = keyFor(regionUrl);
        if (key == null) return out;
        JSONArray names = get(ctx).optJSONArray(key);
        if (names == null) return out;

        List<String> normNames = new ArrayList<String>();
        for (Channel c : channels) normNames.add(norm(c.name));
        Set<Integer> used = new HashSet<Integer>();
        Channel[] picked = new Channel[names.length()];

        // 1. tur: birebir eşleşme, 2. tur: "TRT 1 HD" gibi önek eşleşmesi
        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < names.length(); n++) {
                if (picked[n] != null) continue;
                String want = norm(names.optString(n));
                if (want.length() == 0) continue;
                for (int i = 0; i < channels.size(); i++) {
                    if (used.contains(i)) continue;
                    String have = normNames.get(i);
                    if (pass == 0 ? have.equals(want) : have.startsWith(want)) {
                        picked[n] = channels.get(i);
                        used.add(i);
                        break;
                    }
                }
            }
        }
        for (Channel c : picked) {
            if (c != null && out.size() < 50) out.add(c);
        }
        return out;
    }

    // ---------- Bu cihazdaki izlenme sayıları ----------

    private static SharedPreferences watch(Context ctx) {
        return ctx.getSharedPreferences("watch_counts", Context.MODE_PRIVATE);
    }

    public static void countWatch(Context ctx, Channel c) {
        SharedPreferences p = watch(ctx);
        p.edit().putInt(c.url, p.getInt(c.url, 0) + 1).apply();
    }

    public static List<Channel> mostWatched(Context ctx, List<Channel> channels) {
        final SharedPreferences p = watch(ctx);
        List<Channel> out = new ArrayList<Channel>();
        for (Channel c : channels) if (p.getInt(c.url, 0) > 0) out.add(c);
        Collections.sort(out, new Comparator<Channel>() {
            @Override
            public int compare(Channel a, Channel b) {
                return p.getInt(b.url, 0) - p.getInt(a.url, 0);
            }
        });
        return out.size() > 50 ? new ArrayList<Channel>(out.subList(0, 50)) : out;
    }

    private static String read(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
