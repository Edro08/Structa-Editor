package com.edro08.structa.ui.editor;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Real provider installed only in the test APK, independent of application dependencies. */
public class TestDocumentsProvider extends DocumentsProvider {
    private File root() {
        File root = new File(getContext().getCacheDir(), "saf-fixture");
        root.mkdirs();
        return root;
    }
    @Override public boolean onCreate() { return true; }
    private File file(String id) {
        File root = root();
        File file = id.equals("root") ? root : new File(root, id.substring(5));
        try {
            if (!file.getCanonicalPath().equals(root.getCanonicalPath()) &&
                !file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
                throw new IllegalArgumentException("Invalid document");
        } catch (IOException e) { throw new IllegalArgumentException(e); }
        return file;
    }
    private String id(File file) {
        return file.equals(root()) ? "root" : "root/" + file.getAbsolutePath().substring(root().getAbsolutePath().length() + 1);
    }
    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS};
    private void add(MatrixCursor cursor, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            switch (column) {
                case Document.COLUMN_DOCUMENT_ID: value = id(file); break;
                case Document.COLUMN_DISPLAY_NAME: value = file.getName(); break;
                case Document.COLUMN_SIZE: value = file.length(); break;
                case Document.COLUMN_MIME_TYPE: value = file.isDirectory() ? Document.MIME_TYPE_DIR : "text/plain"; break;
                case Document.COLUMN_FLAGS: value = Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE |
                    Document.FLAG_SUPPORTS_RENAME | (file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0); break;
            }
            row.add(value);
        }
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS});
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            switch (column) {
                case Root.COLUMN_ROOT_ID: case Root.COLUMN_DOCUMENT_ID: value = "root"; break;
                case Root.COLUMN_TITLE: value = "Structa test"; break;
                case Root.COLUMN_FLAGS: value = Root.FLAG_SUPPORTS_CREATE; break;
            }
            row.add(value);
        }
        return cursor;
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        File file = file(documentId);
        if (file.exists()) add(cursor, file);
        return cursor;
    }
    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        File[] children = file(parentDocumentId).listFiles();
        if (children != null) for (File child : children) add(cursor, child);
        return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName) {
        File child = new File(file(parentDocumentId), displayName);
        try {
            if (!(Document.MIME_TYPE_DIR.equals(mimeType) ? child.mkdir() : child.createNewFile()))
                throw new IOException("Create failed");
        } catch (IOException e) { throw new IllegalStateException(e); }
        return id(child);
    }
    @Override public String renameDocument(String documentId, String displayName) {
        File source = file(documentId);
        File target = new File(source.getParentFile(), displayName);
        if (!source.renameTo(target)) throw new IllegalStateException("Rename failed");
        return id(target);
    }
    @Override public void deleteDocument(String documentId) { delete(file(documentId)); }
    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!file.delete()) throw new IllegalStateException("Delete failed");
    }
    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return file(documentId).getAbsolutePath().startsWith(file(parentDocumentId).getAbsolutePath() + File.separator);
    }
}
