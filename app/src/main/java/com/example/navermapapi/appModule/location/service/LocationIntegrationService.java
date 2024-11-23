package com.example.navermapapi.appModule.location.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import com.example.navermapapi.R;
import com.example.navermapapi.appModule.main.MainActivity;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;

@AndroidEntryPoint
public class LocationIntegrationService extends Service {
    private static final String TAG = "LocationIntegrationService";
    private static final String CHANNEL_ID = "LocationTrackingChannel";
    private static final int NOTIFICATION_ID = 1;

    @Inject
    LocationIntegrationManager locationManager;

    private final IBinder binder = new LocalBinder();
    private boolean isTracking = false;

    public class LocalBinder extends Binder {
        public LocationIntegrationService getService() {
            return LocationIntegrationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        startTracking();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used for tracking location in background");

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("실내외 내비게이션")
                .setContentText("위치 추적 중...")
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .build();
    }

    public void startTracking() {
        if (!isTracking) {
            isTracking = true;
            locationManager.startTracking();

            // 위치 업데이트 시 알림 업데이트
            locationManager.getCurrentLocation().observeForever(this::updateNotification);
            locationManager.getCurrentEnvironment().observeForever(this::updateEnvironmentStatus);
        }
    }

    public void stopTracking() {
        if (isTracking) {
            isTracking = false;
            locationManager.stopTracking();
            stopForeground(true);
            stopSelf();
        }
    }

    private void updateNotification(LocationData location) {
        if (location == null) return;

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("실내외 내비게이션")
                .setContentText(String.format(
                        "현재 위치: %.6f, %.6f",
                        location.getLatitude(),
                        location.getLongitude()
                ))
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void updateEnvironmentStatus(EnvironmentType environment) {
        if (environment == null) return;

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String environmentText = environment == EnvironmentType.INDOOR ?
                "실내 모드" : "실외 모드";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("실내외 내비게이션")
                .setContentText(environmentText)
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTracking();
    }

    public boolean isTracking() {
        return isTracking;
    }
}