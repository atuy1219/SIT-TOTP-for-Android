package com.atuy.sittotp;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class TotpTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Runnable action = this::launchAndCollapse;
        if (isLocked()) {
            unlockAndRun(action);
        } else {
            action.run();
        }
    }

    private void launchAndCollapse() {
        boolean configured = new SeedStore(this).hasSeed();
        Intent intent;
        int requestCode;

        if (configured) {
            intent = new Intent(this, OtpDisplayActivity.class)
                    .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    );
            requestCode = 1;
        } else {
            intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            requestCode = 2;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean configured = new SeedStore(this).hasSeed();
        boolean displaying = configured && OtpNotificationService.isActive(this);

        tile.setState(displaying ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!configured) {
                tile.setSubtitle("シード未設定");
            } else if (displaying) {
                tile.setSubtitle("タップして閉じる");
            } else {
                tile.setSubtitle("タップして表示");
            }
        }
        tile.updateTile();
    }
}
