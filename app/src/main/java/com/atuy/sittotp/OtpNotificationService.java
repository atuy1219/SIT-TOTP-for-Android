package com.atuy.sittotp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.service.quicksettings.TileService;

import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.Collections;

public final class OtpNotificationService extends Service {
    public static final int NOTIFICATION_ID = 1219;

    private static final int COPY_REQUEST_CODE = 1220;
    private static final String CHANNEL_ID = "sit_totp_code";
    private static final String STATE_PREFERENCES = "otp_notification_state";
    private static final String KEY_ACTIVE_UNTIL = "active_until";
    private static final long DISPLAY_DURATION_MILLIS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = this::updateNotification;

    private NotificationManager notificationManager;
    private String normalizedSeed;
    private long stopAtElapsedRealtime;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            normalizedSeed = new SeedStore(this).read();
            if (normalizedSeed == null) {
                clearActiveState(this);
                requestTileRefresh(this);
                stopSelf();
                return START_NOT_STICKY;
            }
        } catch (GeneralSecurityException exception) {
            clearActiveState(this);
            requestTileRefresh(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopAtElapsedRealtime = SystemClock.elapsedRealtime() + DISPLAY_DURATION_MILLIS;
        setActiveUntil(this, System.currentTimeMillis() + DISPLAY_DURATION_MILLIS);

        Notification initial = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    initial,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            );
        } else {
            startForeground(NOTIFICATION_ID, initial);
        }

        requestTileRefresh(this);
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, nextSecondDelay());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updateRunnable);
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        clearActiveState(this);
        requestTileRefresh(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isActive(Context context) {
        long activeUntil = statePreferences(context).getLong(KEY_ACTIVE_UNTIL, 0L);
        if (activeUntil <= System.currentTimeMillis()) {
            clearActiveState(context);
            return false;
        }
        return true;
    }

    static void markStarting(Context context) {
        setActiveUntil(context, System.currentTimeMillis() + DISPLAY_DURATION_MILLIS);
        requestTileRefresh(context);
    }

    static void stopAndRemove(Context context) {
        context.stopService(new Intent(context, OtpNotificationService.class));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
        clearActiveState(context);
        requestTileRefresh(context);
    }

    static void clearActiveState(Context context) {
        statePreferences(context).edit().remove(KEY_ACTIVE_UNTIL).apply();
    }

    private static void setActiveUntil(Context context, long activeUntil) {
        statePreferences(context).edit().putLong(KEY_ACTIVE_UNTIL, activeUntil).apply();
    }

    private static SharedPreferences statePreferences(Context context) {
        return context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE);
    }

    private static void requestTileRefresh(Context context) {
        TileService.requestListeningState(
                context,
                new ComponentName(context, TotpTileService.class)
        );
    }

    private void updateNotification() {
        if (SystemClock.elapsedRealtime() >= stopAtElapsedRealtime) {
            stopSelf();
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
        handler.postDelayed(updateRunnable, nextSecondDelay());
    }

    private Notification buildNotification() {
        long epochSeconds = System.currentTimeMillis() / 1_000L;
        String code = Totp.generate(normalizedSeed, epochSeconds);
        int remaining = Totp.remainingSeconds(epochSeconds);
        int elapsed = Totp.PERIOD_SECONDS - remaining;

        Intent contentIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent copyIntent = new Intent(this, CopyOtpReceiver.class)
                .setAction(CopyOtpReceiver.ACTION_COPY_CURRENT_CODE);
        PendingIntent copyPendingIntent = PendingIntent.getBroadcast(
                this,
                COPY_REQUEST_CODE,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Action copyAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_copy),
                getString(R.string.copy_code),
                copyPendingIntent
        ).build();

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_totp)
                .setContentTitle(code)
                .setContentText("SITワンタイムパスワード・残り " + remaining + " 秒")
                .setSubText("SIT TOTP")
                .setContentIntent(contentPendingIntent)
                .addAction(copyAction)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTimeoutAfter(Math.max(
                        1_000L,
                        stopAtElapsedRealtime - SystemClock.elapsedRealtime()
                ));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        if (Build.VERSION.SDK_INT >= 36) {
            Notification.ProgressStyle progressStyle = new Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgress(elapsed)
                    .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.ic_totp))
                    .setProgressSegments(Collections.singletonList(
                            new Notification.ProgressStyle.Segment(Totp.PERIOD_SECONDS)
                    ));
            builder.setStyle(progressStyle)
                    .setShortCriticalText(code)
                    .setWhen(System.currentTimeMillis() + (remaining * 1_000L))
                    .setShowWhen(true);
            requestPromotedOngoingIfAvailable(builder);
        } else {
            builder.setProgress(Totp.PERIOD_SECONDS, elapsed, false);
        }

        return builder.build();
    }

    /** Android 16.1 added this method after the base API 36 SDK, so call it defensively. */
    private static void requestPromotedOngoingIfAvailable(Notification.Builder builder) {
        try {
            Method method = Notification.Builder.class.getMethod(
                    "setRequestPromotedOngoing",
                    boolean.class
            );
            method.invoke(builder, true);
        } catch (ReflectiveOperationException ignored) {
            // Android 16.0 and OEM builds without the 36.1 API use ProgressStyle normally.
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
    }

    private static long nextSecondDelay() {
        long now = System.currentTimeMillis();
        return Math.max(50L, 1_000L - (now % 1_000L));
    }
}
