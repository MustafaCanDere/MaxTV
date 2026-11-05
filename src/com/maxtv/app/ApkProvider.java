package com.maxtv.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** İndirilen güncelleme APK'sını yalnızca sistem yükleyicisine (geçici izinle) sunar. */
public class ApkProvider extends ContentProvider {
    public static final String AUTHORITY = "com.maxtv.app.files";
    public static final String FILE_NAME = "update.apk";

    public static Uri uri() {
        return Uri.parse("content://" + AUTHORITY + "/" + FILE_NAME);
    }

    public static File file(android.content.Context c) {
        return new File(c.getCacheDir(), FILE_NAME);
    }

    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (!FILE_NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        File f = file(getContext());
        if (!f.exists()) throw new FileNotFoundException(uri.toString());
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        File f;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"MaxTV.apk", f.length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
