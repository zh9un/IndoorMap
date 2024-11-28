package com.example.navermapapi.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

public class BitmapUtils {

    /**
     * Bitmap을 주어진 각도로 회전시킵니다.
     * @param source 원본 Bitmap
     * @param angle 회전 각도 (도 단위)
     * @return 회전된 Bitmap
     */
    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        // 원본 이미지의 너비와 높이
        int width = source.getWidth();
        int height = source.getHeight();

        // 회전 후의 이미지 크기를 계산하기 위한 RectF 생성
        RectF rect = new RectF(0, 0, width, height);
        matrix.mapRect(rect);

        // 회전 후의 이미지 크기
        int newWidth = Math.round(rect.width());
        int newHeight = Math.round(rect.height());

        // 회전 후 이미지가 중심에 오도록 Matrix 조정
        matrix.postTranslate(-rect.left, -rect.top);

        // 새로운 Bitmap 생성
        Bitmap rotatedBitmap = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
        Canvas canvas = new Canvas(rotatedBitmap);
        canvas.drawBitmap(source, matrix, new Paint(Paint.ANTI_ALIAS_FLAG));

        return rotatedBitmap;
    }
}
