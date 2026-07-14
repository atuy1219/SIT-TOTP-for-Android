package com.atuy.sittotp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

final class ClipboardUtils {
    private static final String LEGACY_SENSITIVE_KEY =
            "android.content.extra.IS_SENSITIVE";

    private ClipboardUtils() {
    }

    static boolean copySensitiveText(Context context, String text) {
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            return false;
        }

        ClipData clip = ClipData.newPlainText("SIT TOTP", text);
        PersistableBundle extras = new PersistableBundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        } else {
            extras.putBoolean(LEGACY_SENSITIVE_KEY, true);
        }
        clip.getDescription().setExtras(extras);
        clipboard.setPrimaryClip(clip);
        return true;
    }
}
