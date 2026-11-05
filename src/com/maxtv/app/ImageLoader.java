package com.maxtv.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kanal logolarını arka planda indirip küçülterek bellekte saklayan minik yükleyici. */
public class ImageLoader {
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Set<String> failed = new HashSet<String>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void load(final ImageView view, final String url, final int fallbackRes, final int maxPx) {
        view.setTag(url);
        if (url == null || url.length() == 0 || failed.contains(url)) {
            view.setImageResource(fallbackRes);
            return;
        }
        Bitmap b = cache.get(url);
        if (b != null) {
            view.setImageBitmap(b);
            return;
        }
        view.setImageResource(fallbackRes);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    byte[] data = Net.fetchBytes(url, 2 * 1024 * 1024);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx / 2) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                } catch (Throwable ignored) {
                }
                final Bitmap fBmp = bmp;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fBmp == null) {
                            failed.add(url);
                            return;
                        }
                        cache.put(url, fBmp);
                        if (url.equals(view.getTag())) view.setImageBitmap(fBmp);
                    }
                });
            }
        });
    }
}
