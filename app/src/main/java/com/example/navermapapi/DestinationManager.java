package com.example.navermapapi;

import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.ref.WeakReference;

public class DestinationManager {
    private final WeakReference<AppCompatActivity> activityRef;
    private final double destinationDistance;
    private double currentX = 0.0;
    private double currentY = 0.0;
    private double stepLength = 0.75; // 초기 보폭 값 (m)
    private TextView remainingStepsTextView;
    private int remainingSteps;
    private FrameLayout remainingStepsContainer;

    public DestinationManager(AppCompatActivity activity, double destinationDistance) {
        this.activityRef = new WeakReference<>(activity);
        this.destinationDistance = destinationDistance;
        initializeUI();
    }

    private void initializeUI() {
        AppCompatActivity activity = activityRef.get();
        if (activity != null) {
            remainingStepsTextView = new TextView(activity);
            remainingStepsTextView.setTextSize(18);
            // 여기서는 화면에 직접 추가하지 않음
        }
    }

    public void setRemainingStepsContainer(FrameLayout container) {
        this.remainingStepsContainer = container;
        if (remainingStepsTextView != null) {
            remainingStepsContainer.addView(remainingStepsTextView);
        }
    }

    public void updateStepLength(double newStepLength) {
        if (newStepLength > 0) {
            this.stepLength = newStepLength;
        }
    }

    public void updateRemainingSteps(double newX, double newY) {
        currentX = newX;
        currentY = newY;
        calculateAndDisplayRemainingSteps();
    }

    private void calculateAndDisplayRemainingSteps() {
        double distanceTraveled = Math.sqrt(currentX * currentX + currentY * currentY);
        double remainingDistance = Math.max(0, destinationDistance - distanceTraveled);
        remainingSteps = (int) Math.ceil(remainingDistance / stepLength);

        AppCompatActivity activity = activityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                remainingStepsTextView.setText(String.format("목적지까지 남은 걸음 수: %d", remainingSteps));
            });
        }
    }

    public int getRemainingSteps() {
        return remainingSteps;
    }
}