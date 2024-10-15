package com.example.navermapapi;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class AudioManager {
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public AudioManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 언어가 지원되지 않을 경우 영어로 설정
                    tts.setLanguage(Locale.US);
                }
                isInitialized = true;
            }
        });
    }

    public void speak(String text) {
        if (isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}