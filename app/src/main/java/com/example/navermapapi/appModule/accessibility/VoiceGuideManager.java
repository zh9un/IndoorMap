package com.example.navermapapi.appModule.accessibility;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;

@Singleton
public class VoiceGuideManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "VoiceGuideManager";
    private static final long DEFAULT_ANNOUNCEMENT_INTERVAL = 5000L;  // 5초
    private static final long NAVIGATION_ANNOUNCEMENT_INTERVAL = 3000L; // 3초
    private static final int CLOCK_POSITIONS = 12;
    private static final String[] CLOCK_DIRECTIONS = {
            "12시", "1시", "2시", "3시", "4시", "5시",
            "6시", "7시", "8시", "9시", "10시", "11시"
    };

    private final Context context;
    private final TextToSpeech tts;
    private final AudioManager audioManager;
    private final Handler handler;
    private final Vibrator vibrator;

    private long lastAnnouncementTime = 0;
    private String lastMessage = "";
    private long currentAnnouncementInterval = DEFAULT_ANNOUNCEMENT_INTERVAL;
    private boolean isNavigating = false;

    @Inject
    public VoiceGuideManager(Context context) {
        this.context = context.getApplicationContext();
        this.tts = new TextToSpeech(context, this);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());

        initializeTTS();
    }

    private void initializeTTS() {
        tts.setLanguage(Locale.KOREAN);
        tts.setSpeechRate(1.0f);
        tts.setPitch(1.0f);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            announce("음성 안내를 시작합니다");
        } else {
            Log.e(TAG, "TTS 초기화 실패");
        }
    }

    public void setNavigationMode(boolean enabled) {
        isNavigating = enabled;
        currentAnnouncementInterval = enabled ?
                NAVIGATION_ANNOUNCEMENT_INTERVAL : DEFAULT_ANNOUNCEMENT_INTERVAL;
        Log.d(TAG, "Navigation mode set to: " + enabled);
    }

    public void announceLocation(LocationData location, EnvironmentType environment) {
        if (!shouldAnnounce()) return;

        StringBuilder message = new StringBuilder();

        switch (environment) {
            case INDOOR:
                buildIndoorMessage(message, location);
                provideDirectionalHapticFeedback(location.getBearing());
                break;
            case OUTDOOR:
                buildOutdoorMessage(message, location);
                break;
            case TRANSITION:
                message.append("실내외 전환 구역입니다. 잠시만 기다려주세요.");
                provideTransitionHapticFeedback();
                break;
        }

        announce(message.toString());
    }

    private void buildIndoorMessage(StringBuilder message, LocationData location) {
        int clockPosition = getClockPosition(location.getBearing());
        message.append(CLOCK_DIRECTIONS[clockPosition])
                .append(" 방향으로 이동 중입니다. ");

        if (location.getSpeed() > 0) {
            message.append("현재 속도는 분당 ")
                    .append(String.format("%.0f", location.getSpeed() * 60))
                    .append("미터입니다. ");
        }

        // 이동 거리 정보 추가
        if (location.getOffsetX() != 0 || location.getOffsetY() != 0) {
            double distance = Math.sqrt(
                    Math.pow(location.getOffsetX(), 2) +
                            Math.pow(location.getOffsetY(), 2)
            );
            if (distance > 1.0) {
                message.append("시작 지점에서 ")
                        .append(String.format("%.0f", distance))
                        .append("미터 이동했습니다.");
            }
        }
    }

    private void buildOutdoorMessage(StringBuilder message, LocationData location) {
        double distanceToExhibition = calculateDistanceToExhibition(location);

        if (distanceToExhibition <= 100) {
            message.append("전시장이 ")
                    .append(String.format("%.0f", distanceToExhibition))
                    .append("미터 앞에 있습니다.");
        }

        // GPS 정확도 정보 추가
        if (location.getAccuracy() > 0) {
            message.append(" GPS 정확도는 ")
                    .append(String.format("%.0f", location.getAccuracy()))
                    .append("미터입니다.");
        }
    }

    private double calculateDistanceToExhibition(LocationData location) {
        // 실제 구현에서는 전시장 좌표와의 거리 계산
        return 50.0; // 예시 값
    }

    private int getClockPosition(float bearing) {
        int position = Math.round(bearing / (360.0f / CLOCK_POSITIONS)) % CLOCK_POSITIONS;
        if (position < 0) position += CLOCK_POSITIONS;
        return position;
    }

    private void provideDirectionalHapticFeedback(float bearing) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int intensity = (int)(Math.abs(bearing % 90) / 90f * 255);
            VibrationEffect effect = VibrationEffect.createOneShot(50, intensity);
            vibrator.vibrate(effect);
        } else {
            vibrator.vibrate(50);
        }
    }

    private void provideTransitionHapticFeedback() {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createWaveform(
                    new long[]{0, 100, 100, 100},
                    new int[]{0, 255, 0, 255},
                    -1
            );
            vibrator.vibrate(effect);
        } else {
            vibrator.vibrate(new long[]{0, 100, 100, 100}, -1);
        }
    }

    public void announceEnvironmentChange(EnvironmentType newEnvironment) {
        String message;
        switch (newEnvironment) {
            case INDOOR:
                message = "실내에 진입했습니다. 실내 내비게이션을 시작합니다.";
                break;
            case OUTDOOR:
                message = "실외로 나왔습니다. GPS 내비게이션을 시작합니다.";
                break;
            default:
                message = "위치 확인 중입니다. 잠시만 기다려주세요.";
        }
        announce(message, true);
    }

    private boolean shouldAnnounce() {
        long currentTime = System.currentTimeMillis();
        return currentTime - lastAnnouncementTime >= currentAnnouncementInterval;
    }

    public void announce(String message) {
        announce(message, false);
    }

    private void announce(String message, boolean priority) {
        if (message == null || message.isEmpty()) return;
        if (!priority && !shouldAnnounce()) return;
        if (message.equals(lastMessage)) return;

        lastMessage = message;
        lastAnnouncementTime = System.currentTimeMillis();

        tts.stop();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        }

        Log.d(TAG, "Announced: " + message);
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}