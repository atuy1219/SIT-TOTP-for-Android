package com.atuy.sittotp;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class TotpTileService extends TileService {
    private static final long TILE_PULSE_MILLIS = 350L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resetTileRunnable = this::updateTile;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onStopListening() {
        handler.removeCallbacks(resetTileRunnable);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        copyAndShowNotification();
    }

    private void copyAndShowNotification() {
        SeedStore store = new SeedStore(this);
        if (!store.hasSeed()) {
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

            pulseTile();
            OtpNotificationService.markStarting(this);
            startForegroundService(new Intent(this, OtpNotificationService.class));
        } catch (Exception exception) {
            OtpNotificationService.clearActiveState(this);
            updateTile();
            Toast.makeText(
                    this,
                    "TOTPを表示できません: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void pulseTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        handler.removeCallbacks(resetTileRunnable);
        tile.setState(Tile.STATE_ACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle("コピーしました");
        }
        tile.updateTile();
        handler.postDelayed(resetTileRunnable, TILE_PULSE_MILLIS);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean configured = new SeedStore(this).hasSeed();
        tile.setState(Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(configured ? "タップしてコピー" : "シード未設定");
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
