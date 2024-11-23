package com.example.navermapapi.appModule.accessibility;

import android.content.Context;
import android.view.accessibility.AccessibilityManager;
import android.view.View;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

public class AccessibilityUtils {
    private AccessibilityUtils() {
        // Utility class
    }

    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled();
    }

    public static void setCustomAccessibilityDelegate(View view, String description) {
        ViewCompat.setAccessibilityDelegate(view,
                new AccessibilityDelegateCompat() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                            View host,
                            AccessibilityNodeInfoCompat info
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setContentDescription(description);
                    }
                }
        );
    }

    public static void announceForAccessibility(View view, String announcement) {
        if (isAccessibilityEnabled(view.getContext())) {
            view.announceForAccessibility(announcement);
        }
    }

    public static String getDirectionDescription(float bearing) {
        if (bearing >= 337.5 || bearing < 22.5) return "북쪽";
        if (bearing >= 22.5 && bearing < 67.5) return "북동쪽";
        if (bearing >= 67.5 && bearing < 112.5) return "동쪽";
        if (bearing >= 112.5 && bearing < 157.5) return "남동쪽";
        if (bearing >= 157.5 && bearing < 202.5) return "남쪽";
        if (bearing >= 202.5 && bearing < 247.5) return "남서쪽";
        if (bearing >= 247.5 && bearing < 292.5) return "서쪽";
        return "북서쪽";
    }
}