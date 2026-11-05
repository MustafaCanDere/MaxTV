package com.maxtv.app;

import java.util.HashMap;
import java.util.Map;

/** Bir M3U kaydı: kanal adı, yayın adresi ve isteğe bağlı HTTP başlıkları. */
public class Channel {
    public String name = "";
    public String url = "";
    public String group = "";
    public String logo = "";
    public String userAgent = null;
    public String referrer = null;

    public Map<String, String> headers() {
        Map<String, String> h = new HashMap<String, String>();
        if (userAgent != null && userAgent.length() > 0) h.put("User-Agent", userAgent);
        if (referrer != null && referrer.length() > 0) h.put("Referer", referrer);
        return h;
    }

    /** Favoriler için tek satırlık kayıt (sekme ile ayrılmış). */
    public String serialize() {
        return clean(name) + "\t" + clean(url) + "\t" + clean(group) + "\t"
                + clean(userAgent) + "\t" + clean(referrer);
    }

    public static Channel deserialize(String line) {
        String[] p = line.split("\t", -1);
        if (p.length < 2 || p[1].length() == 0) return null;
        Channel c = new Channel();
        c.name = p[0];
        c.url = p[1];
        if (p.length > 2) c.group = p[2];
        if (p.length > 3 && p[3].length() > 0) c.userAgent = p[3];
        if (p.length > 4 && p[4].length() > 0) c.referrer = p[4];
        return c;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }
}
