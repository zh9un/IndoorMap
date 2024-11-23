package com.example.navermapapi.utils;

import android.content.res.Resources;

public class DisplayUtils {
    public static int dpToPx(float dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}