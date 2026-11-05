package com.maxtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    /** Hazır kaynaklar: iptv-org topluluğunun derlediği, herkese açık ücretsiz yayınlar. */
    private static final String BASE = "https://iptv-org.github.io/iptv/";
    private static final String[][] SOURCES = {
            {"Türkiye", BASE + "countries/tr.m3u"},
            {"Türkçe (tüm ülkeler)", BASE + "languages/tur.m3u"},
            {"Haber", BASE + "categories/news.m3u"},
            {"Spor", BASE + "categories/sports.m3u"},
            {"Çocuk", BASE + "categories/kids.m3u"},
            {"Müzik", BASE + "categories/music.m3u"},
            {"Film", BASE + "categories/movies.m3u"},
            {"Belgesel", BASE + "categories/documentary.m3u"},
            {"Dünya (tümü)", BASE + "index.m3u"},
            {"★ Favoriler", "fav:"},
            {"Kendi listem (URL)", "custom:"},
    };

    private static final Locale TR = new Locale("tr", "TR");

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private ProgressBar progress;
    private EditText search;
    private ListView list;
    private final List<Button> sourceButtons = new ArrayList<Button>();

    private List<Channel> all = new ArrayList<Channel>();
    private final List<Channel> shown = new ArrayList<Channel>();
    private final List<Integer> shownNumbers = new ArrayList<Integer>();
    private LinkedHashMap<String, Channel> favorites;
    private String currentSource = null;
    private int loadToken = 0;
    private final ChannelAdapter adapter = new ChannelAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = (TextView) findViewById(R.id.status);
        progress = (ProgressBar) findViewById(R.id.progress);
        search = (EditText) findViewById(R.id.search);
        list = (ListView) findViewById(R.id.list);
        favorites = ChannelStore.loadFavorites(this);

        LinearLayout row = (LinearLayout) findViewById(R.id.sources);
        for (int i = 0; i < SOURCES.length; i++) {
            final String[] src = SOURCES[i];
            Button b = (Button) LayoutInflater.from(this).inflate(R.layout.source_button, row, false);
            b.setText(src[0]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (src[1].equals("custom:")) askCustomUrl();
                    else selectSource(src[1]);
                }
            });
            row.addView(b);
            sourceButtons.add(b);
        }

        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                play(position);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                toggleFavorite(position);
                return true;
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(); }
        });

        String last = ChannelStore.prefs(this).getString("source", SOURCES[0][1]);
        if (last.equals("custom:")) last = SOURCES[0][1];
        selectSource(last);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Oynatıcıdan dönünce son izlenen kanalı seçili göster
        if (!ChannelStore.playlist.isEmpty() && ChannelStore.playlist == lastLaunchedList) {
            int pos = ChannelStore.index;
            if (pos >= 0 && pos < shown.size()) {
                list.setSelection(pos);
                list.requestFocus();
            }
        }
    }

    private List<Channel> lastLaunchedList = null;

    // ---------- Kaynak seçimi ve yükleme ----------

    private void selectSource(final String source) {
        currentSource = source;
        ChannelStore.prefs(this).edit().putString("source", source).apply();
        highlightButton(source);
        search.setText("");

        if (source.equals("fav:")) {
            setChannels(new ArrayList<Channel>(favorites.values()), "Favoriler");
            if (favorites.isEmpty()) {
                status.setText("Henüz favori yok. Bir kanalın üzerinde OK tuşuna uzun basarak ekleyebilirsiniz.");
            }
            return;
        }

        final String url = source.startsWith("custom:") ? source.substring(7) : source;
        final int token = ++loadToken;
        progress.setVisibility(View.VISIBLE);
        status.setText("Kanal listesi yükleniyor…");

        // Önbellekte varsa hemen göster, arka planda tazele
        final File cache = cacheFile(url);
        if (cache.exists()) {
            try {
                List<Channel> cached = parseFile(cache);
                setChannels(cached, "Önbellek");
            } catch (IOException ignored) {
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Channel> result = null;
                String error = null;
                try {
                    download(url, cache);
                    result = parseFile(cache);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final List<Channel> fResult = result;
                final String fError = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken) return; // bu arada başka kaynak seçildi
                        progress.setVisibility(View.GONE);
                        if (fResult != null) {
                            setChannels(fResult, null);
                        } else if (!all.isEmpty()) {
                            status.setText(all.size() + " kanal (çevrimdışı önbellek) • güncellenemedi: " + fError);
                        } else {
                            status.setText("Liste indirilemedi: " + fError);
                            Toast.makeText(MainActivity.this,
                                    "İnternet bağlantınızı kontrol edin.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void setChannels(List<Channel> channels, String note) {
        all = channels;
        applyFilter();
        String label = all.size() + " kanal";
        if (note != null) label += " • " + note;
        status.setText(label);
        if (!shown.isEmpty() && !search.hasFocus()) list.requestFocus();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(TR);
        shown.clear();
        shownNumbers.clear();
        for (int i = 0; i < all.size(); i++) {
            Channel c = all.get(i);
            if (q.length() == 0
                    || c.name.toLowerCase(TR).contains(q)
                    || c.group.toLowerCase(TR).contains(q)) {
                shown.add(c);
                shownNumbers.add(i + 1);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void highlightButton(String source) {
        for (int i = 0; i < sourceButtons.size(); i++) {
            String s = SOURCES[i][1];
            boolean active = s.equals(source) || (s.equals("custom:") && source.startsWith("custom:"));
            sourceButtons.get(i).setActivated(active);
        }
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
                        selectSource("custom:" + u);
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ---------- Favoriler ----------

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
        if ("fav:".equals(currentSource)) {
            setChannels(new ArrayList<Channel>(favorites.values()), "Favoriler");
            if (position < shown.size()) list.setSelection(position);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Kumandadaki MENU tuşu da favori ekler/çıkarır
        if (keyCode == KeyEvent.KEYCODE_MENU && list.hasFocus()) {
            toggleFavorite(list.getSelectedItemPosition());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- Oynatma ----------

    private void play(int position) {
        if (position < 0 || position >= shown.size()) return;
        ChannelStore.playlist = new ArrayList<Channel>(shown);
        ChannelStore.numbers = new ArrayList<Integer>(shownNumbers);
        ChannelStore.index = position;
        lastLaunchedList = ChannelStore.playlist;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    // ---------- Ağ / dosya ----------

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

    private static void download(String url, File dest) throws IOException {
        HttpURLConnection conn = null;
        String current = url;
        for (int redirects = 0; redirects < 6; redirects++) {
            conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "MaxTV/1.0 (Android TV)");
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
            break;
        }
        File tmp = new File(dest.getPath() + ".tmp");
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
            conn.disconnect();
        }
        if (!tmp.renameTo(dest)) {
            dest.delete();
            if (!tmp.renameTo(dest)) throw new IOException("Önbelleğe yazılamadı");
        }
    }

    // ---------- Liste görünümü ----------

    private class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_channel, parent, false);
            Channel c = shown.get(position);
            ((TextView) v.findViewById(R.id.number)).setText(String.valueOf(shownNumbers.get(position)));
            String name = favorites.containsKey(c.url) ? "★ " + c.name : c.name;
            ((TextView) v.findViewById(R.id.name)).setText(name);
            ((TextView) v.findViewById(R.id.group)).setText(c.group);
            return v;
        }
    }
}
