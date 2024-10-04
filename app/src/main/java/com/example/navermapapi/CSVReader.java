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
    public static int[][] readCSV(Context context, String fileName) {
        List<int[]> result = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                // BOM 제거
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                // 끝의 쉼표 제거
                line = line.replaceAll(",+$", "");
                String[] row = line.split(",");
                int[] intRow = new int[row.length];
                for (int i = 0; i < row.length; i++) {
                    try {
                        intRow[i] = Integer.parseInt(row[i].trim());
                    } catch (NumberFormatException e) {
                        Log.e("CSVReader", "Invalid number format: " + row[i] + " in row " + result.size());
                        intRow[i] = 0; // 오류 발생 시 기본값 0으로 설정
                    }
                }
                result.add(intRow);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("CSVReader", "Error reading CSV file: " + e.getMessage());
        }
        return result.toArray(new int[0][]);
    }
}