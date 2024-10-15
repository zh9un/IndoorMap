package com.example.navermapapi;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

public class AudioManager {
    private static final String TAG = "AudioManager";
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public AudioManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "한국어가 지원되지 않습니다. 영어로 설정합니다.");
                    tts.setLanguage(Locale.US);
                }
                isInitialized = true;
                Log.d(TAG, "TextToSpeech 초기화 성공");
            } else {
                Log.e(TAG, "TextToSpeech 초기화 실패");
            }
        });
    }

    public void speak(String text) {
        if (isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "음성 출력: " + text);
        } else {
            Log.e(TAG, "TextToSpeech가 초기화되지 않았습니다.");
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TextToSpeech 종료");
        }
    }
}