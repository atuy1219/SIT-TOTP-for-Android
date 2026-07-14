package com.atuy.sittotp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.Collections;

public final class OtpNotificationService extends Service {
    public static final int NOTIFICATION_ID = 1219;

    private static final String CHANNEL_ID = "sit_totp_code";
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
                stopSelf();
                return START_NOT_STICKY;
            }
        } catch (GeneralSecurityException exception) {
            stopSelf();
            return START_NOT_STICKY;
        }

        stopAtElapsedRealtime = SystemClock.elapsedRealtime() + DISPLAY_DURATION_MILLIS;
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

        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, nextSecondDelay());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updateRunnable);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        long epochSeconds = System.currentTimeMillis() / 1000L;
        String code = Totp.generate(normalizedSeed, epochSeconds);
        int remaining = Totp.remainingSeconds(epochSeconds);
        int elapsed = Totp.PERIOD_SECONDS - remaining;

        Intent contentIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_totp)
                .setContentTitle(code)
                .setContentText("SITワンタイムパスワード・残り " + remaining + " 秒")
                .setSubText("SIT TOTP")
                .setContentIntent(pendingIntent)
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
