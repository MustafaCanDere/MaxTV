package com.maxtv.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Basit, hataya dayanıklı M3U / M3U8 oynatma listesi ayrıştırıcısı. */
public class M3uParser {
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");

    public static List<Channel> parse(Reader in) throws IOException {
        BufferedReader r = new BufferedReader(in);
        List<Channel> out = new ArrayList<Channel>();
        Channel pending = null;
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            if (line.charAt(0) == '﻿') line = line.substring(1);
            if (line.startsWith("#EXTINF")) {
                pending = new Channel();
                Matcher m = ATTR.matcher(line);
                while (m.find()) {
                    String k = m.group(1).toLowerCase();
                    String v = m.group(2);
                    if (k.equals("tvg-logo")) pending.logo = v;
                    else if (k.equals("group-title")) pending.group = v;
                    else if (k.equals("http-user-agent") || k.equals("user-agent")) pending.userAgent = v;
                    else if (k.equals("http-referrer") || k.equals("referrer")) pending.referrer = v;
                    else if (k.equals("tvg-name") && pending.name.length() == 0) pending.name = v;
                }
                int comma = firstCommaOutsideQuotes(line);
                if (comma >= 0 && comma + 1 < line.length()) {
                    String title = line.substring(comma + 1).trim();
                    if (title.length() > 0) pending.name = title;
                }
            } else if (line.startsWith("#EXTVLCOPT:")) {
                if (pending == null) pending = new Channel();
                String opt = line.substring(11).trim();
                int eq = opt.indexOf('=');
                if (eq > 0) {
                    String k = opt.substring(0, eq).trim().toLowerCase();
                    String v = opt.substring(eq + 1).trim();
                    if (k.equals("http-user-agent")) pending.userAgent = v;
                    else if (k.equals("http-referrer")) pending.referrer = v;
                }
            } else if (line.startsWith("#EXTGRP:")) {
                if (pending == null) pending = new Channel();
                pending.group = line.substring(8).trim();
            } else if (line.startsWith("#")) {
                // diğer etiketler (#EXTM3U vb.) yok sayılır
            } else {
                Channel c = pending != null ? pending : new Channel();
                c.url = line;
                if (c.name.length() == 0) c.name = line;
                out.add(c);
                pending = null;
            }
        }
        return out;
    }

    /** Başlıkta virgül olabileceği için tırnak dışındaki İLK virgülü bulur. */
    private static int firstCommaOutsideQuotes(String s) {
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inQuote = !inQuote;
            else if (ch == ',' && !inQuote) return i;
        }
        return -1;
    }
}
