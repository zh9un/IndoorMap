package com.example.navermapapi;

import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.ref.WeakReference;

public class DestinationManager {
    private final WeakReference<AppCompatActivity> activityRef;
    private final double destinationDistance;
    //현재 위치 좌표(x, y)
    private double currentX = 0.0;
    private double currentY = 0.0;
    private double stepLength = 0.75; // 초기 보폭 값 (m)
    //남은 거리 수를 표시할 TextView
    private TextView remainingStepsTextView;
    private int remainingSteps; // 추가: 남은 걸음 수를 저장할 변수

    //액티비티와 목적지까지의 거리를 받아 초기화
    public DestinationManager(AppCompatActivity activity, double destinationDistance) {
        this.activityRef = new WeakReference<>(activity);
        this.destinationDistance = destinationDistance;
        initializeUI(); //UI초기화
    }

    //초기화: 남은 걸은 수를 표시할 TextView를 생성하고 화면에 추가
    private void initializeUI() {
        AppCompatActivity activity = activityRef.get();
        if (activity != null) {
            remainingStepsTextView = activity.findViewById(R.id.remaining_steps_view); // 변경: XML 레이아웃에 정의된 TextView 참조
        }
    }

    //보폭 업데이트: 새로운 보폭 값을 받아 업데이트
    public void updateStepLength(double newStepLength) {
        if (newStepLength > 0) { //보폭이 양수(움직일)일 때만 업데이트
            this.stepLength = newStepLength;
        }
    }

    //현재 위치 업데이트: 새로운 x, y 좌표를 받아 업데이트 하고 남은 걸음 수 계산
    public void updateRemainingSteps(double newX, double newY) {
        currentX = newX;
        currentY = newY;
        calculateAndDisplayRemainingSteps(); //남은 걸음 수 계산 및 화면에 표시
    }

    private void calculateAndDisplayRemainingSteps() {
        //이동한 거리 계산: 피타고라스 정리를 이용한 거리 계산
        double distanceTraveled = Math.sqrt(currentX * currentX + currentY * currentY);
        //남은 거리 계산(최소값 0)
        double remainingDistance = Math.max(0, destinationDistance - distanceTraveled);
        //남은 걸음 수 계산(반올림하여 정수로 계산)
        remainingSteps = (int) Math.ceil(remainingDistance / stepLength);

        //UI 업데이트
        AppCompatActivity activity = activityRef.get();
        if (activity != null && remainingStepsTextView != null) {
            activity.runOnUiThread(() -> {
                remainingStepsTextView.setText(String.format("목적지까지 남은 걸음 수: %d", remainingSteps));
            });
        }
    }

    // 추가: 현재 남은 걸음 수를 반환하는 메서드
    public int getRemainingSteps() {
        return remainingSteps;
    }
}