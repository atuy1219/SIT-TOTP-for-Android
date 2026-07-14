package com.atuy.sittotp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class OtpDisplayActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        try {
            SeedStore store = new SeedStore(this);
            String seed = store.read();
            if (seed == null) {
                startActivity(new Intent(this, MainActivity.class));
            } else if (OtpNotificationService.isActive(this)) {
                OtpNotificationService.stopAndRemove(this);
            } else {
                OtpNotificationService.markStarting(this);
                Intent serviceIntent = new Intent(this, OtpNotificationService.class);
                startForegroundService(serviceIntent);
            }
        } catch (Exception exception) {
            OtpNotificationService.clearActiveState(this);
            Toast.makeText(
                    this,
                    "TOTPを切り替えられません: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
