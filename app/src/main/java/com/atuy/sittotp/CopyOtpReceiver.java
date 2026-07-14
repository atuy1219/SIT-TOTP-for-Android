package com.atuy.sittotp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

public final class CopyOtpReceiver extends BroadcastReceiver {
    public static final String ACTION_COPY_CURRENT_CODE =
            "com.atuy.sittotp.action.COPY_CURRENT_CODE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COPY_CURRENT_CODE.equals(intent.getAction())) {
            return;
        }

        try {
            String seed = new SeedStore(context).read();
            if (seed == null) {
                return;
            }

            String code = Totp.generate(seed, System.currentTimeMillis() / 1_000L);
            boolean copied = ClipboardUtils.copySensitiveText(context, code);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(
                        context,
                        copied ? R.string.code_copied : R.string.copy_failed,
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (Exception exception) {
            Toast.makeText(context, R.string.copy_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
