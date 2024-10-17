package com.example.navermapapi;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.naver.maps.geometry.LatLng;

import java.util.ArrayList;
import java.util.List;



public class MapView extends View {
    private static final String TAG = "MapView";
    private static final float ARROW_SIZE = 60f;
    private float scaleFactor = 200f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private float lastTouchY;
    private static final int INVALID_POINTER_ID = -1;
    private int activePointerId = INVALID_POINTER_ID;

    private Paint paint;
    private List<Double> trailX = new ArrayList<>();
    private List<Double> trailY = new ArrayList<>();

    private static final float ALPHA = 0.05f;
    private float[] filteredOrientation = new float[3];

    private double positionX = 0.0;
    private double positionY = 0.0;

    // 건물 테두리 관련 변수 추가
    private List<LatLng> buildingCorners;
    private boolean showBuildingOutline = false;

    public MapView(Context context) {
        super(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5f);
        buildingCorners = new ArrayList<>();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scaleFactor, scaleFactor, centerX, centerY);

        canvas.drawColor(Color.WHITE);

        // 비콘 위치 그리기
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.FILL);
        for (BeaconLocationManager.Beacon beacon : BeaconLocationManager.getBeacons()) {
            canvas.drawCircle(centerX + (float) beacon.getX(), centerY - (float) beacon.getY(), 30f / scaleFactor, paint);
        }

        // 이동 경로 그리기
        paint.setColor(Color.argb(76, 0, 255, 0));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(20f / scaleFactor);
        if (!trailX.isEmpty()) {
            Path path = new Path();
            path.moveTo(centerX + (float) trailX.get(0).doubleValue(), centerY - (float) trailY.get(0).doubleValue());
            for (int i = 1; i < trailX.size(); i++) {
                path.lineTo(centerX + (float) trailX.get(i).doubleValue(), centerY - (float) trailY.get(i).doubleValue());
            }
            canvas.drawPath(path, paint);
        }

        // 현재 위치 그리기
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX + (float) positionX, centerY - (float) positionY, 20f / scaleFactor, paint);

        // 방향 화살표 그리기
        paint.setColor(Color.BLUE);
        float arrowX = centerX + (float) positionX;
        float arrowY = centerY - (float) positionY;
        float angle = filteredOrientation[0];

        Path arrowHead = new Path();
        arrowHead.moveTo(arrowX + ARROW_SIZE / scaleFactor * (float) Math.sin(angle),
                arrowY - ARROW_SIZE / scaleFactor * (float) Math.cos(angle));
        arrowHead.lineTo(arrowX + ARROW_SIZE * 0.7f / scaleFactor * (float) Math.sin(angle + Math.PI * 0.875),
                arrowY - ARROW_SIZE * 0.7f / scaleFactor * (float) Math.cos(angle + Math.PI * 0.875));
        arrowHead.lineTo(arrowX + ARROW_SIZE * 0.7f / scaleFactor * (float) Math.sin(angle - Math.PI * 0.875),
                arrowY - ARROW_SIZE * 0.7f / scaleFactor * (float) Math.cos(angle - Math.PI * 0.875));
        arrowHead.close();
        canvas.drawPath(arrowHead, paint);

        // 건물 테두리 그리기
        if (showBuildingOutline && buildingCorners != null && !buildingCorners.isEmpty()) {
            Log.d(TAG, "Drawing building outline. Number of corners: " + buildingCorners.size());
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            Path buildingPath = new Path();

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

            for (LatLng corner : buildingCorners) {
                float x = (float) (corner.longitude * 1000);
                float y = (float) (corner.latitude * 1000);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }

            float scaleX = width / (maxX - minX);
            float scaleY = height / (maxY - minY);
            float scale = Math.min(scaleX, scaleY) * 0.8f;

            LatLng firstCorner = buildingCorners.get(0);
            float startX = (float) ((firstCorner.longitude * 1000 - minX) * scale);
            float startY = height - (float) ((firstCorner.latitude * 1000 - minY) * scale);
            buildingPath.moveTo(startX, startY);
            Log.d(TAG, "First corner: (" + startX + ", " + startY + ")");

            for (int i = 1; i < buildingCorners.size(); i++) {
                LatLng corner = buildingCorners.get(i);
                float x = (float) ((corner.longitude * 1000 - minX) * scale);
                float y = height - (float) ((corner.latitude * 1000 - minY) * scale);
                Log.d(TAG, "Corner " + i + ": (" + x + ", " + y + ")");
                buildingPath.lineTo(x, y);
            }
            buildingPath.close();
            canvas.drawPath(buildingPath, paint);
        }

        canvas.restore();

        drawCompass(canvas, width - 140, 140, 120);
    }
    private void drawCompass(Canvas canvas, float centerX, float centerY, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(24f);
        paint.setTextAlign(Paint.Align.CENTER);

        String[] directions = {"N", "E", "S", "W"};
        float[] angles = {0, 90, 180, 270};

        for (int i = 0; i < 4; i++) {
            float angle = (float) Math.toRadians(angles[i] - Math.toDegrees(filteredOrientation[0]));
            float x = centerX + (radius - 25) * (float) Math.sin(angle);
            float y = centerY - (radius - 25) * (float) Math.cos(angle);
            canvas.drawText(directions[i], x, y + 9, paint);
        }

        paint.setColor(Color.RED);
        canvas.drawLine(centerX, centerY,
                centerX + radius * (float) Math.sin(-filteredOrientation[0]),
                centerY - radius * (float) Math.cos(-filteredOrientation[0]), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);

        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                lastTouchX = x;
                lastTouchY = y;
                activePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                if (!scaleDetector.isInProgress()) {
                    final float dx = x - lastTouchX;
                    final float dy = y - lastTouchY;
                    offsetX += dx;
                    offsetY += dy;
                    invalidate();
                }
                lastTouchX = x;
                lastTouchY = y;
                break;
            }
            case MotionEvent.ACTION_UP: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    lastTouchX = ev.getX(newPointerIndex);
                    lastTouchY = ev.getY(newPointerIndex);
                    activePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
            invalidate();
            return true;
        }
    }

    public void updatePosition(double x, double y) {
        this.positionX = x;
        this.positionY = y;
        trailX.add(x);
        trailY.add(y);
        invalidate();
        Log.d(TAG, "Position updated: (" + x + ", " + y + ")");
    }

    public void updateOrientation(float[] orientation) {
        for (int i = 0; i < 3; i++) {
            filteredOrientation[i] = filteredOrientation[i] + ALPHA * (orientation[i] - filteredOrientation[i]);
        }
        invalidate();
    }

    // 건물 테두리 설정 메서드 추가
    public void setBuildingCorners(List<LatLng> corners) {
        this.buildingCorners = corners;
        invalidate();
    }

    // 건물 테두리 표시 여부 설정 메서드 추가
    public void setShowBuildingOutline(boolean show) {
        this.showBuildingOutline = show;
        invalidate();
    }
}