package com.example.navermapapi;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CSVReader {
    // CSV 파일을 읽어 2차원 배열로 변환하는 메서드입니다.
    // 실내 지도의 구조를 나타내는 데이터를 로드하여 경로 탐색 및 위치 표시 등에 활용합니다.
    public static int[][] readCSV(Context context, String fileName) {
        List<int[]> result = new ArrayList<>();
        try {
            // assets 폴더에 있는 CSV 파일을 열기 위한 InputStream 생성
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                // BOM(Byte Order Mark) 제거 - 파일의 첫 부분에 포함될 수 있는 BOM을 제거합니다.
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                // 끝의 쉼표 제거 - 데이터 끝에 불필요한 쉼표가 있을 경우 제거합니다.
                line = line.replaceAll(",+$", "");
                // 쉼표로 구분된 데이터를 배열로 변환
                String[] row = line.split(",");
                int[] intRow = new int[row.length];
                for (int i = 0; i < row.length; i++) {
                    try {
                        // 각 셀의 값을 정수형으로 변환하여 int 배열에 저장
                        intRow[i] = Integer.parseInt(row[i].trim());
                    } catch (NumberFormatException e) {
                        // 숫자 형식이 잘못되었을 경우 기본값 0으로 설정하고 로그에 오류 출력
                        Log.e("CSVReader", "Invalid number format: " + row[i] + " in row " + result.size());
                        intRow[i] = 0; // 오류 발생 시 기본값 0으로 설정
                    }
                }
                // 변환된 행 데이터를 결과 리스트에 추가
                result.add(intRow);
            }
            reader.close(); // 파일 읽기 완료 후 BufferedReader 닫기
        } catch (IOException e) {
            // 파일을 읽는 도중 오류가 발생했을 때 로그 출력
            e.printStackTrace();
            Log.e("CSVReader", "Error reading CSV file: " + e.getMessage());
        }
        // 리스트를 2차원 배열로 변환하여 반환
        return result.toArray(new int[0][]);
    }
}
