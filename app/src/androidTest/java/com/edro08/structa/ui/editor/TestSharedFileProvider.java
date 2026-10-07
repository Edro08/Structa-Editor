package com.edro08.structa.ui.editor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** A non-DocumentsProvider content URI like one sent by a file-sharing app. */
public class TestSharedFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private String name(Uri uri) { return uri.getLastPathSegment(); }
    private byte[] content(Uri uri) { return ("content for " + name(uri) + "\n").getBytes(StandardCharsets.UTF_8); }

    @Override public String getType(Uri uri) { return name(uri).endsWith(".json") ? "application/json" : "text/plain"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        String[] columns = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (column.equals(OpenableColumns.DISPLAY_NAME)) row.add(name(uri));
            else if (column.equals(OpenableColumns.SIZE)) row.add(content(uri).length);
            else row.add(null);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = new File(getContext().getCacheDir(), "shared-" + name(uri));
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(content(uri)); }
        catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
