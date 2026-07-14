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
        SeedStore store = new SeedStore(this);
        Intent intent = new Intent(
                this,
                store.hasSeed() ? OtpDisplayActivity.class : MainActivity.class
        )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OtpDisplayActivity.EXTRA_FROM_TILE, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
        tile.setState(configured ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(configured ? "コードを表示" : "シード未設定");
        }
        tile.updateTile();
    }
}
