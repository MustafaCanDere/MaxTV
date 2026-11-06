package com.maxtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Adım adım seçim: 1) Bölge  2) Kategori  3) Kanal listesi.
 * Her şey kumandanın yön tuşları + OK ile büyük kutular üzerinden yapılır. Geri tuşu bir adım geri gider.
 */
public class MainActivity extends Activity {

    private static final String BASE = "https://iptv-org.github.io/iptv/";
    private static final Locale TR = new Locale("tr", "TR");

    // ---------- Bölgeler ----------
    // {simge, ad, liste adresi veya özel kod}
    private static final String[][] REGIONS = {
            {"🇹🇷", "Türkiye", BASE + "countries/tr.m3u"},
            {"🗣️", "Türkçe yayınlar", BASE + "languages/tur.m3u"},
            {"🇦🇿", "Azerbaycan", BASE + "countries/az.m3u"},
            {"🇩🇪", "Almanya", BASE + "countries/de.m3u"},
            {"🇬🇧", "İngiltere", BASE + "countries/uk.m3u"},
            {"🇺🇸", "Amerika", BASE + "countries/us.m3u"},
            {"🇫🇷", "Fransa", BASE + "countries/fr.m3u"},
            {"🌍", "Tüm dünya", "world:"},
            {"📺", "YouTube canlı", "youtube:"},
            {"⭐", "Favorilerim", "fav:"},
            {"🔗", "Kendi listem", "custom:"},
            {"🔄", "Güncelleme", "update:"},
    };

    // ---------- Kategoriler ----------
    // {simge, Türkçe ad, iptv-org kategori kimliği, eşleşen grup adları (| ile)}
    private static final String[][] CATEGORIES = {
            {"📺", "Hepsi", "", ""},
            {"📰", "Haber", "news", "news"},
            {"⚽", "Spor", "sports", "sports|sport"},
            {"🧸", "Çocuk", "kids", "kids|animation"},
            {"🎬", "Film", "movies", "movies|movie"},
            {"🎭", "Dizi", "series", "series"},
            {"🎉", "Eğlence", "entertainment", "entertainment|comedy"},
            {"🎵", "Müzik", "music", "music"},
            {"🦁", "Belgesel", "documentary", "documentary|science|outdoor"},
            {"📡", "Genel", "general", "general"},
            {"🕌", "Dini", "religious", "religious"},
            {"🍳", "Yemek", "cooking", "cooking"},
            {"🎓", "Eğitim", "education", "education"},
            {"🛍️", "Yaşam", "lifestyle", "lifestyle|travel|family|culture"},
    };

    private static final int STEP_REGION = 0, STEP_CATEGORY = 1, STEP_CHANNELS = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView breadcrumb, question, status, hint;
    private ProgressBar progress;
    private GridView grid;
    private ListView list;
    private EditText search;

    private int step = STEP_REGION;
    private String[] region;        // seçili bölge
    private String[] category;      // seçili kategori (null = hepsi)
    private boolean skippedCategory; // favori / kendi listem: kategori adımı yok
    private List<Channel> regionChannels = new ArrayList<Channel>();
    private int loadToken = 0;

    private final List<String[]> tiles = new ArrayList<String[]>(); // {simge, ad, alt yazı}
    private final List<String[]> tileData = new ArrayList<String[]>();
    private final TileAdapter tileAdapter = new TileAdapter();

    private List<Channel> all = new ArrayList<Channel>();
    private final List<Channel> shown = new ArrayList<Channel>();
    private final List<Integer> shownNumbers = new ArrayList<Integer>();
    private final ChannelAdapter channelAdapter = new ChannelAdapter();
    private LinkedHashMap<String, Channel> favorites;
    private List<Channel> lastLaunchedList = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        breadcrumb = (TextView) findViewById(R.id.breadcrumb);
        question = (TextView) findViewById(R.id.question);
        status = (TextView) findViewById(R.id.status);
        hint = (TextView) findViewById(R.id.hint);
        progress = (ProgressBar) findViewById(R.id.progress);
        grid = (GridView) findViewById(R.id.grid);
        list = (ListView) findViewById(R.id.list);
        search = (EditText) findViewById(R.id.search);
        favorites = ChannelStore.loadFavorites(this);

        grid.setAdapter(tileAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                onTileClicked(position);
            }
        });

        list.setAdapter(channelAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                play(position);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int position, long id) {
                toggleFavorite(position);
                return true;
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(); }
        });

        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            View lastSelected;
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (lastSelected != null && lastSelected != v) zoom(lastSelected, 1f);
                if (v != null) zoom(v, 1.08f);
                lastSelected = v;
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {
                if (lastSelected != null) zoom(lastSelected, 1f);
                lastSelected = null;
            }
        });

        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            View lastRow;
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (lastRow != null && lastRow != v) zoom(lastRow, 1f);
                if (v != null) zoom(v, 1.03f);
                lastRow = v;
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {
                if (lastRow != null) zoom(lastRow, 1f);
                lastRow = null;
            }
        });

        isTv = getPackageManager().hasSystemFeature("android.software.leanback");
        showRegions();
        Updater.checkOnStart(this);
        Popular.refresh(this);
    }

    private boolean isTv = true;

    private static void zoom(View v, float s) {
        v.animate().scaleX(s).scaleY(s).setDuration(150).start();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == STEP_CHANNELS && ChannelStore.playlist == lastLaunchedList && lastLaunchedList != null) {
            int pos = ChannelStore.index;
            if (pos >= 0 && pos < shown.size()) {
                list.setSelection(pos);
                list.requestFocus();
            }
        }
    }

    // ================= 1) BÖLGE =================

    private void showRegions() {
        step = STEP_REGION;
        loadToken++; // süren indirmeyi yok say
        progress.setVisibility(View.GONE);
        breadcrumb.setText("");
        question.setText("Nereden kanal izlemek istersiniz?");
        status.setText(isTv ? "Yön tuşlarıyla seçin, OK'a basın." : "Bir kutuya dokunun.");
        hint.setText(isTv ? "Geri: çıkış" : "");
        tiles.clear();
        tileData.clear();

        String last = ChannelStore.prefs(this).getString("lastChannel", null);
        if (last != null) {
            Channel c = Channel.deserialize(last);
            if (c != null) {
                tiles.add(new String[]{"▶️", "Devam et", c.name});
                tileData.add(new String[]{"resume:"});
            }
        }
        for (String[] r : REGIONS) {
            String sub = "";
            if (r[2].equals("fav:")) sub = favorites.size() + " kanal";
            if (r[2].equals("update:")) sub = "Sürüm " + versionName();
            tiles.add(new String[]{r[0], r[1], sub});
            tileData.add(r);
        }
        showGrid();
    }

    // ================= 2) KATEGORİ =================

    private void chooseRegion(String[] r) {
        region = r;
        category = null;
        String src = r[2];
        if (src.equals("fav:")) {
            skippedCategory = true;
            ChannelStore.prefs(this).edit().putString("source", "fav:").apply();
            showChannels(new ArrayList<Channel>(favorites.values()));
            if (favorites.isEmpty()) {
                status.setText("Henüz favori yok. Bir kanalın üzerinde OK'a uzun basarak ekleyebilirsiniz.");
            }
            return;
        }
        if (src.equals("youtube:")) {
            skippedCategory = true;
            prepareChannelScreen();
            loadYoutube();
            return;
        }
        if (src.equals("custom:")) {
            skippedCategory = true;
            askCustomUrl();
            return;
        }
        skippedCategory = false;
        if (src.equals("world:")) {
            // Tüm dünya: tek seferde 10.000+ kanal indirmek yerine kategori listesini kullan
            showCategories(null);
            return;
        }
        // Ülke/dil listesi: önce indir, sonra her kategoride kaç kanal var göster
        step = STEP_CATEGORY;
        breadcrumb.setText(r[1]);
        question.setText("Ne tür bir şey izlemek istersiniz?");
        tiles.clear();
        tileData.clear();
        tileAdapter.notifyDataSetChanged();
        loadList(src, new Callback() {
            @Override
            public void done(List<Channel> channels) {
                regionChannels = channels;
                showCategories(channels);
            }
        });
    }

    private void showCategories(List<Channel> channels) {
        step = STEP_CATEGORY;
        breadcrumb.setText(region[1]);
        question.setText("Ne tür bir şey izlemek istersiniz?");
        hint.setText("Geri: bölge seçimine dön");
        tiles.clear();
        tileData.clear();
        if (channels != null) {
            int pop = Popular.popular(this, region[2], channels).size();
            if (pop > 0) {
                tiles.add(new String[]{"🔥", "En popüler", "İlk " + pop + " kanal"});
                tileData.add(new String[]{"🔥", "En popüler", "popular:", ""});
            }
            int mine = Popular.mostWatched(this, channels).size();
            if (mine > 0) {
                tiles.add(new String[]{"🕘", "En çok izlediklerim", mine + " kanal"});
                tileData.add(new String[]{"🕘", "En çok izlediklerim", "mine:", ""});
            }
        }
        for (String[] c : CATEGORIES) {
            if (channels == null) {
                tiles.add(new String[]{c[0], c[1], c[2].length() == 0 ? "çok büyük liste" : ""});
                tileData.add(c);
            } else {
                int n = c[3].length() == 0 ? channels.size() : filterByCategory(channels, c).size();
                if (n == 0) continue; // boş kategoriyi gösterme
                tiles.add(new String[]{c[0], c[1], n + " kanal"});
                tileData.add(c);
            }
        }
        status.setText(channels == null ? "Bir kategori seçin." : channels.size() + " kanal bulundu. Bir kategori seçin.");
        showGrid();
    }

    private void chooseCategory(String[] c) {
        category = c;
        ChannelStore.prefs(this).edit()
                .putString("source", region[2]).putString("category", c[2]).apply();
        if (region[2].equals("world:")) {
            String url = c[2].length() == 0 ? BASE + "index.m3u" : BASE + "categories/" + c[2] + ".m3u";
            prepareChannelScreen();
            loadList(url, new Callback() {
                @Override
                public void done(List<Channel> channels) {
                    showChannels(channels);
                }
            });
        } else if (c[2].equals("popular:")) {
            showChannels(Popular.popular(this, region[2], regionChannels));
        } else if (c[2].equals("mine:")) {
            showChannels(Popular.mostWatched(this, regionChannels));
        } else {
            showChannels(c[3].length() == 0 ? regionChannels : filterByCategory(regionChannels, c));
        }
    }

    private static List<Channel> filterByCategory(List<Channel> channels, String[] c) {
        String[] keys = c[3].split("\\|");
        List<Channel> out = new ArrayList<Channel>();
        for (Channel ch : channels) {
            String g = ch.group.toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (g.contains(k)) {
                    out.add(ch);
                    break;
                }
            }
        }
        return out;
    }

    // ================= 3) KANALLAR =================

    private void prepareChannelScreen() {
        step = STEP_CHANNELS;
        String crumb = region[1] + (category != null ? "  ›  " + category[1] : "");
        breadcrumb.setText(crumb);
        question.setText("Kanal seçin");
        hint.setText(isTv
                ? "OK: izle   •   OK'a uzun bas: favori ★   •   Geri: " + (skippedCategory ? "bölge seçimi" : "kategori seçimi")
                : "Dokun: izle   •   Uzun bas: favori ★");
        grid.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        search.setVisibility(View.VISIBLE);
        search.setText("");
        all = new ArrayList<Channel>();
        applyFilter();
    }

    private void showChannels(List<Channel> channels) {
        prepareChannelScreen();
        all = channels;
        applyFilter();
        status.setText(all.size() + " kanal • Açılmayan kanal olursa sonrakine geçin.");
        if (!shown.isEmpty()) {
            list.setSelection(0);
            list.requestFocus();
        }
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(TR);
        shown.clear();
        shownNumbers.clear();
        for (int i = 0; i < all.size(); i++) {
            Channel c = all.get(i);
            if (q.length() == 0 || c.name.toLowerCase(TR).contains(q) || c.group.toLowerCase(TR).contains(q)) {
                shown.add(c);
                shownNumbers.add(i + 1);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    // ================= Ortak =================

    private void showGrid() {
        list.setVisibility(View.GONE);
        search.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        tileAdapter.notifyDataSetChanged();
        grid.setSelection(0);
        grid.requestFocus();
    }

    private void onTileClicked(int position) {
        if (position < 0 || position >= tileData.size()) return;
        String[] d = tileData.get(position);
        if (step == STEP_REGION) {
            if (d[0].equals("resume:")) resumeLast();
            else if (d[2].equals("update:")) Updater.check(this, true);
            else chooseRegion(d);
        } else if (step == STEP_CATEGORY) {
            chooseCategory(d);
        }
    }

    private void resumeLast() {
        Channel c = Channel.deserialize(ChannelStore.prefs(this).getString("lastChannel", ""));
        if (c == null) return;
        List<Channel> one = new ArrayList<Channel>();
        one.add(c);
        ChannelStore.playlist = one;
        ChannelStore.numbers = new ArrayList<Integer>();
        ChannelStore.numbers.add(1);
        ChannelStore.index = 0;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    @Override
    public void onBackPressed() {
        if (step == STEP_CHANNELS) {
            if (skippedCategory || region == null) showRegions();
            else if (region[2].equals("world:")) showCategories(null);
            else showCategories(regionChannels);
        } else if (step == STEP_CATEGORY) {
            showRegions();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && step == STEP_CHANNELS && list.hasFocus()) {
            toggleFavorite(list.getSelectedItemPosition());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void askCustomUrl() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://… .m3u");
        input.setText(ChannelStore.prefs(this).getString("customUrl", ""));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("M3U liste adresi")
                .setMessage("Yasal olarak erişim hakkınız olan bir M3U/M3U8 oynatma listesinin adresini girin.")
                .setView(input)
                .setPositiveButton("Yükle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String u = input.getText().toString().trim();
                        if (u.length() == 0) return;
                        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
                        ChannelStore.prefs(MainActivity.this).edit().putString("customUrl", u).apply();
                        prepareChannelScreen();
                        loadList(u, new Callback() {
                            @Override
                            public void done(List<Channel> channels) {
                                showChannels(channels);
                            }
                        });
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void toggleFavorite(int position) {
        if (position < 0 || position >= shown.size()) return;
        Channel c = shown.get(position);
        if (favorites.containsKey(c.url)) {
            favorites.remove(c.url);
            Toast.makeText(this, c.name + " favorilerden çıkarıldı", Toast.LENGTH_SHORT).show();
        } else {
            favorites.put(c.url, c);
            Toast.makeText(this, c.name + " favorilere eklendi ★", Toast.LENGTH_SHORT).show();
        }
        ChannelStore.saveFavorites(this, favorites);
        if (region != null && "fav:".equals(region[2])) {
            showChannels(new ArrayList<Channel>(favorites.values()));
            if (position < shown.size()) list.setSelection(position);
        } else {
            channelAdapter.notifyDataSetChanged();
        }
    }

    /** YouTube canlı yayın listesi GitHub'dan gelir, böylece uygulamayı güncellemeden liste değiştirilebilir. */
    private static final String YOUTUBE_URL = "https://github.com/MustafaCanDere/MaxTV/raw/main/youtube.json";

    private void loadYoutube() {
        final int token = ++loadToken;
        progress.setVisibility(View.VISIBLE);
        status.setText("YouTube listesi yükleniyor…");
        final File cache = new File(getCacheDir(), "youtube.json");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String json = null;
                try {
                    Net.download(YOUTUBE_URL + "?t=" + System.currentTimeMillis(), cache, null);
                } catch (Exception ignored) {
                }
                try {
                    if (cache.exists()) json = readAll(new FileInputStream(cache));
                } catch (Exception ignored) {
                }
                if (json == null) {
                    try {
                        json = readAll(getResources().openRawResource(R.raw.youtube));
                    } catch (Exception ignored) {
                    }
                }
                final List<Channel> out = new ArrayList<Channel>();
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        Channel c = new Channel();
                        c.name = o.optString("name");
                        c.url = o.optString("url");
                        c.group = "YouTube • " + o.optString("group");
                        c.logo = o.optString("logo", "");
                        if (c.url.length() > 0) out.add(c);
                    }
                } catch (Exception ignored) {
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken) return;
                        progress.setVisibility(View.GONE);
                        showChannels(out);
                        status.setText(out.size() + " canlı yayın • Seçince YouTube uygulamasında açılır");
                    }
                });
            }
        }).start();
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static boolean isYoutube(Channel c) {
        return c.url.contains("youtube.com/") || c.url.contains("youtu.be/");
    }

    private void openYoutube(Channel c) {
        Uri uri = Uri.parse(c.url);
        String[] pkgs = isTv
                ? new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", null}
                : new String[]{"com.google.android.youtube", null};
        for (String pkg : pkgs) {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            if (pkg != null) i.setPackage(pkg);
            try {
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, "YouTube uygulaması açılamadı.", Toast.LENGTH_LONG).show();
    }

    private void play(int position) {
        if (position < 0 || position >= shown.size()) return;
        if (isYoutube(shown.get(position))) {
            openYoutube(shown.get(position));
            return;
        }
        ChannelStore.playlist = new ArrayList<Channel>(shown);
        ChannelStore.numbers = new ArrayList<Integer>(shownNumbers);
        ChannelStore.index = position;
        lastLaunchedList = ChannelStore.playlist;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    // ================= İndirme =================

    private interface Callback {
        void done(List<Channel> channels);
    }

    private void loadList(final String url, final Callback cb) {
        final int token = ++loadToken;
        progress.setVisibility(View.VISIBLE);
        status.setText("Kanallar yükleniyor, lütfen bekleyin…");
        final File cache = cacheFile(url);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Channel> result = null;
                String error = null;
                boolean fromCache = false;
                try {
                    Net.download(url, cache, null);
                    result = parseFile(cache);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                    if (cache.exists()) {
                        try {
                            result = parseFile(cache);
                            fromCache = true;
                        } catch (IOException ignored) {
                        }
                    }
                }
                final List<Channel> fResult = result;
                final String fError = error;
                final boolean fFromCache = fromCache;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken) return;
                        progress.setVisibility(View.GONE);
                        if (fResult != null) {
                            cb.done(fResult);
                            if (fFromCache) Toast.makeText(MainActivity.this,
                                    "İnternet yok, kayıtlı liste gösteriliyor.", Toast.LENGTH_LONG).show();
                        } else {
                            status.setText("Liste indirilemedi: " + fError + "  (Geri tuşuyla dönüp tekrar deneyin)");
                            Toast.makeText(MainActivity.this, "İnternet bağlantınızı kontrol edin.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private File cacheFile(String url) {
        return new File(getCacheDir(), "list_" + Integer.toHexString(url.hashCode()) + ".m3u");
    }

    private static List<Channel> parseFile(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            return M3uParser.parse(new InputStreamReader(in, "UTF-8"));
        } finally {
            in.close();
        }
    }

    // ================= Adaptörler =================

    private class TileAdapter extends BaseAdapter {
        @Override public int getCount() { return tiles.size(); }
        @Override public Object getItem(int p) { return tiles.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_tile, parent, false);
            String[] t = tiles.get(position);
            ((TextView) v.findViewById(R.id.icon)).setText(t[0]);
            ((TextView) v.findViewById(R.id.label)).setText(t[1]);
            TextView sub = (TextView) v.findViewById(R.id.sub);
            sub.setText(t[2]);
            sub.setVisibility(t[2].length() == 0 ? View.GONE : View.VISIBLE);
            return v;
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_channel, parent, false);
            Channel c = shown.get(position);
            ((TextView) v.findViewById(R.id.number)).setText(String.valueOf(shownNumbers.get(position)));
            String name = favorites.containsKey(c.url) ? "★ " + c.name : c.name;
            ((TextView) v.findViewById(R.id.name)).setText(name);
            TextView group = (TextView) v.findViewById(R.id.group);
            group.setText(c.group.replace(";", " • "));
            group.setVisibility(c.group.length() == 0 ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.badge)).setText(isYoutube(c) ? "▶ YOUTUBE" : "● CANLI");
            v.setScaleX(1f);
            v.setScaleY(1f);
            ImageLoader.load((ImageView) v.findViewById(R.id.logo), c.logo, R.mipmap.ic_launcher, 144);
            return v;
        }
    }
}
