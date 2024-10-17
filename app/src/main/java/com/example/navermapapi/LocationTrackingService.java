package com.example.navermapapi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "LocationTrackingChannel";

    private GPSManager gpsManager;
    private LocationDataManager locationDataManager;

    @Override
    public void onCreate() {
        super.onCreate();
        gpsManager = new GPSManager(this);
        locationDataManager = new LocationDataManager(this);

        gpsManager.setOnLocationUpdateListener(location -> {
            updateLocation(location);
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LocationTrackingService started");
        startForeground(NOTIFICATION_ID, createNotification());
        gpsManager.startLocationUpdates();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gpsManager.stopLocationUpdates();
    }

    private void updateLocation(Location location) {
        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            locationDataManager.saveLocationData(latitude, longitude, 0); // 총 이동 거리는 별도로 계산 필요
            Log.d(TAG, "Location updated: " + latitude + ", " + longitude);

            // 위치 업데이트를 브로드캐스트
            Intent intent = new Intent("com.example.navermapapi.LOCATION_UPDATE");
            intent.putExtra("latitude", latitude);
            intent.putExtra("longitude", longitude);
            sendBroadcast(intent);
        }
    }

    private Notification createNotification() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, ProjectBActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("위치 추적 중")
                .setContentText("백그라운드에서 위치를 추적하고 있습니다.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}