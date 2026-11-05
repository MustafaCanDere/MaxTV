package com.maxtv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uygulama içi ortak durum: oynatılan liste ve favoriler. */
public class ChannelStore {
    /** Oynatıcının ▲▼ ile gezdiği liste (ana ekranda o an görünen liste). */
    public static List<Channel> playlist = new ArrayList<Channel>();
    /** Listede her kanalın ana ekrandaki numarası. */
    public static List<Integer> numbers = new ArrayList<Integer>();
    public static int index = 0;

    private static final String PREFS = "maxtv";
    private static final String KEY_FAV = "favorites";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static LinkedHashMap<String, Channel> loadFavorites(Context c) {
        LinkedHashMap<String, Channel> map = new LinkedHashMap<String, Channel>();
        String raw = prefs(c).getString(KEY_FAV, "");
        for (String line : raw.split("\n")) {
            if (line.length() == 0) continue;
            Channel ch = Channel.deserialize(line);
            if (ch != null) map.put(ch.url, ch);
        }
        return map;
    }

    public static void saveFavorites(Context c, Map<String, Channel> favs) {
        StringBuilder sb = new StringBuilder();
        for (Channel ch : favs.values()) sb.append(ch.serialize()).append('\n');
        prefs(c).edit().putString(KEY_FAV, sb.toString()).apply();
    }
}
