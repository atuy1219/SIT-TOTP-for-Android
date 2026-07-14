package com.atuy.sittotp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class OtpDisplayActivity extends Activity {
    public static final String EXTRA_FROM_TILE = "from_tile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        try {
            String seed = new SeedStore(this).read();
            if (seed == null) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                Intent serviceIntent = new Intent(this, OtpNotificationService.class);
                startForegroundService(serviceIntent);
            }
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "TOTPを表示できません: " + safeMessage(exception),
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
