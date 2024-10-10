package com.example.navermapapi;

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
            activity.addContentView(remainingStepsTextView, new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
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
        int remainingSteps = (int) Math.ceil(remainingDistance / stepLength);

        AppCompatActivity activity = activityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                remainingStepsTextView.setText(String.format("목적지까지 남은 걸음 수: %d", remainingSteps));
            });
        }
    }
}