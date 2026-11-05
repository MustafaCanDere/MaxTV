package com.maxtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;

/**
 * GitHub'daki version.json dosyasını kontrol eder; yeni sürüm varsa kullanıcıya sorar,
 * APK'yı indirir ve Android'in yükleyicisini açar.
 */
public class Updater {
    public static final String VERSION_URL =
            "https://github.com/MustafaCanDere/MaxTV/raw/main/version.json";

    private static boolean checkedThisSession = false;

    public static void checkOnStart(final Activity a) {
        if (checkedThisSession) return;
        checkedThisSession = true;
        check(a, false);
    }

    public static void check(final Activity a, final boolean userAsked) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = new String(Net.fetchBytes(VERSION_URL + "?t=" + System.currentTimeMillis(), 64 * 1024), "UTF-8");
                    final JSONObject o = new JSONObject(json);
                    final int latest = o.getInt("versionCode");
                    final int mine = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (a.isFinishing()) return;
                            if (latest > mine) offer(a, o);
                            else if (userAsked) Toast.makeText(a, "En güncel sürümü kullanıyorsunuz.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    if (userAsked) main.post(new Runnable() {
                        public void run() {
                            Toast.makeText(a, "Güncelleme kontrol edilemedi.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static void offer(final Activity a, final JSONObject o) {
        String name = o.optString("versionName", "");
        String notes = o.optString("notes", "");
        new AlertDialog.Builder(a)
                .setTitle("Yeni sürüm var: " + name)
                .setMessage((notes.length() > 0 ? notes + "\n\n" : "") + "Şimdi güncellensin mi? Favorileriniz silinmez.")
                .setPositiveButton("Güncelle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        startDownload(a, o.optString("apk", "https://github.com/MustafaCanDere/MaxTV/raw/main/MaxTV.apk"));
                    }
                })
                .setNegativeButton("Sonra", null)
                .show();
    }

    private static boolean canInstall(Activity a) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            return (Boolean) a.getPackageManager().getClass()
                    .getMethod("canRequestPackageInstalls").invoke(a.getPackageManager());
        } catch (Exception e) {
            return true;
        }
    }

    private static void startDownload(final Activity a, final String url) {
        if (!canInstall(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("Bir kerelik izin gerekli")
                    .setMessage("Açılacak ekranda Max TV için \"Bilinmeyen uygulamaları yükle\" iznini açın, sonra Geri tuşuna basıp tekrar Güncelle'yi seçin.")
                    .setPositiveButton("Ayarları aç", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                a.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                                        Uri.parse("package:" + a.getPackageName())));
                            } catch (Exception e) {
                                a.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
                            }
                        }
                    })
                    .setNegativeButton("Vazgeç", null)
                    .show();
            return;
        }

        final ProgressDialog pd = new ProgressDialog(a);
        pd.setTitle("Güncelleme indiriliyor");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        final Handler main = new Handler(Looper.getMainLooper());
        final File dest = ApkProvider.file(a);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Net.download(url, dest, new Net.Progress() {
                        int last = -1;
                        public void onProgress(long done, long total) {
                            if (total <= 0) return;
                            final int pct = (int) (done * 100 / total);
                            if (pct == last) return;
                            last = pct;
                            main.post(new Runnable() { public void run() { pd.setProgress(pct); } });
                        }
                    });
                    main.post(new Runnable() {
                        public void run() {
                            pd.dismiss();
                            install(a);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        public void run() {
                            pd.dismiss();
                            Toast.makeText(a, "İndirilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static void install(Activity a) {
        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        i.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(v);
        }
    }
}
