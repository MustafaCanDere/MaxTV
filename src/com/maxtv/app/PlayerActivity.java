package com.maxtv.app;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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
    private ProgressBar loading;
    private View overlay;
    private TextView title, info, numberInput;
    private String typedNumber = "";
    private boolean prepared = false;

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
        loading = (ProgressBar) findViewById(R.id.loading);
        overlay = findViewById(R.id.overlay);
        title = (TextView) findViewById(R.id.title);
        info = (TextView) findViewById(R.id.info);
        numberInput = (TextView) findViewById(R.id.numberInput);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
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
                showError("Bu kanal şu an açılamıyor (yayın kapalı veya bölgeye kapalı olabilir). ▲/▼ ile başka kanala geçin.");
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
        title.setText(number + "  " + c.name);
        String text = c.group.length() > 0 ? c.group : "";
        if (message != null) text = message;
        else text = (text.length() > 0 ? text + "   •   " : "") + "▲▼ kanal değiştir   •   OK bilgi   •   Geri: liste";
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
        ChannelStore.index = ((ChannelStore.index + delta) % size + size) % size;
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
                if (overlay.getVisibility() == View.VISIBLE && prepared) {
                    handler.removeCallbacks(hideOverlay);
                    overlay.setVisibility(View.GONE);
                } else {
                    showOverlay(null);
                }
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
