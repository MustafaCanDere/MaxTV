package com.maxtv.app;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

/** Tam ekran oynatıcı. ▲/▼ ve CH+/CH- kanal değiştirir, OK bilgi gösterir, rakamlar kanala atlar. */
public class PlayerActivity extends Activity {

    private static final long OVERLAY_MS = 4000;
    private static final long NUMBER_MS = 1800;
    private static final long TIMEOUT_MS = 25000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView video;
    private View loading;
    private TextView numberBadge, nextLabel;
    private View overlay;
    private TextView title, info, numberInput;
    private String typedNumber = "";
    private boolean prepared = false;
    private boolean isTv = true;
    private ImageView logo;
    private GestureDetector gestures;

    private final Runnable hideOverlay = new Runnable() {
        public void run() { overlay.setVisibility(View.GONE); }
    };
    private final Runnable commitNumber = new Runnable() {
        public void run() { jumpToNumber(); }
    };
    private final Runnable timeout = new Runnable() {
        public void run() {
            if (!prepared) showError("Yayın yanıt vermiyor. Başka bir kanal deneyin (▲/▼).");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        video = (VideoView) findViewById(R.id.video);
        loading = findViewById(R.id.loading);
        numberBadge = (TextView) findViewById(R.id.number);
        nextLabel = (TextView) findViewById(R.id.next);
        overlay = findViewById(R.id.overlay);
        title = (TextView) findViewById(R.id.title);
        info = (TextView) findViewById(R.id.info);
        numberInput = (TextView) findViewById(R.id.numberInput);
        logo = (ImageView) findViewById(R.id.logo);
        isTv = getPackageManager().hasSystemFeature("android.software.leanback");
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleOverlay();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 80) {
                    step(dy < 0 ? +1 : -1); // yukarı kaydır: sonraki kanal
                    return true;
                }
                if (Math.abs(dx) > 80) {
                    step(dx < 0 ? +1 : -1); // sola kaydır: sonraki kanal
                    return true;
                }
                return false;
            }
        });

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                Popular.countWatch(PlayerActivity.this, current());
                handler.removeCallbacks(timeout);
                loading.setVisibility(View.GONE);
                video.start();
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                        || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) loading.setVisibility(View.GONE);
                return false;
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showError("Bu kanal şu an açılamıyor (yayın kapalı veya bölgeye kapalı olabilir). " + (isTv ? "▲/▼ ile" : "Kaydırarak") + " başka kanala geçin.");
                return true;
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // Canlı yayın koptuysa yeniden bağlan
                playCurrent();
            }
        });

        if (ChannelStore.playlist.isEmpty()) {
            finish();
            return;
        }
        playCurrent();
    }

    private Channel current() {
        return ChannelStore.playlist.get(ChannelStore.index);
    }

    private void playCurrent() {
        prepared = false;
        handler.removeCallbacks(timeout);
        Channel c = current();
        ChannelStore.prefs(this).edit().putString("lastChannel", c.serialize()).apply();
        video.stopPlayback();
        loading.setVisibility(View.VISIBLE);
        showOverlay(null);
        try {
            video.setVideoURI(Uri.parse(c.url), c.headers());
        } catch (Exception e) {
            showError("Geçersiz yayın adresi.");
            return;
        }
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    private void showOverlay(String message) {
        Channel c = current();
        int number = ChannelStore.numbers.size() > ChannelStore.index
                ? ChannelStore.numbers.get(ChannelStore.index) : ChannelStore.index + 1;
        title.setText(c.name);
        numberBadge.setText("KANAL " + number);
        int size = ChannelStore.playlist.size();
        if (size > 1) {
            Channel n = ChannelStore.playlist.get((ChannelStore.index + 1) % size);
            nextLabel.setText("Sonraki ▸ " + n.name);
            nextLabel.setVisibility(View.VISIBLE);
        } else {
            nextLabel.setVisibility(View.GONE);
        }
        ImageLoader.load(logo, c.logo, R.mipmap.ic_launcher, 192);
        String text = c.group.length() > 0 ? c.group : "";
        if (message != null) text = message;
        else text = text.replace(";", " • ") + (text.length() > 0 ? "    ·    " : "")
                + (isTv ? "▲▼ kanal   ·   OK bilgi   ·   Geri liste"
                        : "Kaydır: kanal   ·   Dokun: bilgi");
        info.setText(text);
        overlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        if (message == null) handler.postDelayed(hideOverlay, OVERLAY_MS);
    }

    private void showError(String message) {
        handler.removeCallbacks(timeout);
        loading.setVisibility(View.GONE);
        showOverlay(message);
    }

    private void step(int delta) {
        int size = ChannelStore.playlist.size();
        if (size == 0) return;
        for (int tries = 0; tries < size; tries++) {
            ChannelStore.index = ((ChannelStore.index + delta) % size + size) % size;
            if (!current().url.contains("youtube.com/")) break; // YouTube kayıtları burada oynatılamaz
        }
        playCurrent();
    }

    private void jumpToNumber() {
        numberInput.setVisibility(View.GONE);
        if (typedNumber.length() == 0) return;
        int wanted;
        try {
            wanted = Integer.parseInt(typedNumber);
        } catch (NumberFormatException e) {
            typedNumber = "";
            return;
        }
        typedNumber = "";
        for (int i = 0; i < ChannelStore.numbers.size(); i++) {
            if (ChannelStore.numbers.get(i) == wanted) {
                ChannelStore.index = i;
                playCurrent();
                return;
            }
        }
        showOverlay("Kanal " + wanted + " bu listede yok.");
        handler.postDelayed(hideOverlay, OVERLAY_MS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private void toggleOverlay() {
        if (overlay.getVisibility() == View.VISIBLE && prepared) {
            handler.removeCallbacks(hideOverlay);
            overlay.setVisibility(View.GONE);
        } else {
            showOverlay(null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                step(+1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_INFO:
                toggleOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (video.isPlaying()) video.pause();
                else video.start();
                return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && typedNumber.length() < 5) {
            typedNumber += (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
            numberInput.setText(typedNumber);
            numberInput.setVisibility(View.VISIBLE);
            handler.removeCallbacks(commitNumber);
            handler.postDelayed(commitNumber, NUMBER_MS);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacksAndMessages(null);
        video.stopPlayback();
        finish(); // arka planda ses çalmaya devam etmesin
    }
}
