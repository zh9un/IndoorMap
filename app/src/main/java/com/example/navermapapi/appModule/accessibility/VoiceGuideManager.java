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
    private static final long MIN_ANNOUNCEMENT_INTERVAL = 5000L;
    private static final int CLOCK_POSITIONS = 12;
    private static final String[] CLOCK_DIRECTIONS = {
            "12시", "1시", "2시", "3시", "4시", "5시",
            "6시", "7시", "8시", "9시", "10시", "11시"
    };

    private final Context context;
    private final TextToSpeech tts;
    private final AudioManager audioManager;
    private final Handler handler;
    private long lastAnnouncementTime = 0;
    private String lastMessage = "";

    @Inject
    public VoiceGuideManager(Context context) {
        this.context = context.getApplicationContext();
        this.tts = new TextToSpeech(context, this);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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

    public void announceLocation(LocationData location, EnvironmentType environment) {
        if (!shouldAnnounce()) return;

        StringBuilder message = new StringBuilder();

        switch (environment) {
            case INDOOR:
                buildIndoorMessage(message, location);
                break;
            case OUTDOOR:
                buildOutdoorMessage(message, location);
                break;
            case TRANSITION:
                message.append("실내외 전환 구역입니다. 잠시만 기다려주세요.");
                break;
        }

        announce(message.toString());
    }

    private void buildIndoorMessage(StringBuilder message, LocationData location) {
        int clockPosition = getClockPosition(location.getBearing());
        message.append(CLOCK_DIRECTIONS[clockPosition])
                .append(" 방향으로 이동 중입니다. ");

        // distanceFromStart 대신 현재 위치의 다른 정보를 사용
        if (location.getAccuracy() <= 10.0f) {  // 정확도가 높을 때만 위치 정보 제공
            message.append("현재 속도는 ")
                    .append(String.format("%.1f", location.getSpeed()))
                    .append("m/s 입니다.");

            if (location.getAltitude() != 0.0f) {
                message.append(" 고도는 ")
                        .append(String.format("%.0f", location.getAltitude()))
                        .append("m 입니다.");
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
    }

    private double calculateDistanceToExhibition(LocationData location) {
        return 50.0; // 예시 값 - 실제 구현에서는 계산 필요
    }

    private int getClockPosition(float bearing) {
        int position = Math.round(bearing / (360.0f / CLOCK_POSITIONS)) % CLOCK_POSITIONS;
        if (position < 0) position += CLOCK_POSITIONS;
        return position;
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
        return currentTime - lastAnnouncementTime >= MIN_ANNOUNCEMENT_INTERVAL;
    }

    public void announce(String message) {
        if (message == null || message.isEmpty()) return;

        announce(message, false);
    }

    private void announce(String message, boolean priority) {
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
    }

    public void provideHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            VibrationEffect effect = VibrationEffect.createOneShot(
                    100,
                    VibrationEffect.DEFAULT_AMPLITUDE
            );
            vibrator.vibrate(effect);
        }
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}