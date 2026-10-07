package com.edro08.structa.ui.editor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.DocumentsContract;

/** Test APK process does not include the application's Kotlin runtime. */
public class GrantTestDocumentsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.grantUriPermission("com.edro08.structa",
            DocumentsContract.buildTreeDocumentUri("com.edro08.structa.test.documents", "root"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    }
}
