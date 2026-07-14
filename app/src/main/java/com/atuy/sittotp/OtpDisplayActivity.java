package com.atuy.sittotp;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

public final class OtpDisplayActivity extends Activity {
    private boolean finishScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureTransitions();

        boolean returnToPreviousScreen = true;
        try {
            SeedStore store = new SeedStore(this);
            String seed = store.read();
            if (seed == null) {
                returnToPreviousScreen = false;
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
            if (returnToPreviousScreen) {
                scheduleAnimatedFinish();
            } else {
                finish();
            }
        }
    }

    private void configureTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.tile_hold,
                    R.anim.tile_hold
            );
            overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.tile_return_enter,
                    R.anim.tile_return_exit
            );
        }
    }

    private void scheduleAnimatedFinish() {
        if (finishScheduled) {
            return;
        }
        finishScheduled = true;

        // Keep the transparent proxy alive for one frame so Android does not
        // optimize away its closing transition.
        getWindow().getDecorView().postOnAnimation(() -> {
            finish();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overridePendingTransition(
                        R.anim.tile_return_enter,
                        R.anim.tile_return_exit
                );
            }
        });
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
