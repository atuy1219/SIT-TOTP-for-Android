package com.atuy.sittotp;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class TotpTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        toggleNotification();
    }

    private void toggleNotification() {
        SeedStore store = new SeedStore(this);
        if (!store.hasSeed()) {
            updateTile();
            return;
        }

        if (OtpNotificationService.isActive(this)) {
            OtpNotificationService.stopAndRemove(this);
            updateTile();
            return;
        }

        try {
            String seed = store.read();
            if (seed == null) {
                updateTile();
                return;
            }

            String code = Totp.generate(seed, System.currentTimeMillis() / 1_000L);
            ClipboardUtils.copySensitiveText(this, code);

            OtpNotificationService.markStarting(this);
            startForegroundService(new Intent(this, OtpNotificationService.class));
        } catch (Exception exception) {
            OtpNotificationService.clearActiveState(this);
            Toast.makeText(
                    this,
                    "TOTPを表示できません: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }

        updateTile();
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
                tile.setSubtitle("タップして表示・コピー");
            }
        }
        tile.updateTile();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
