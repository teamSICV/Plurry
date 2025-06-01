package com.SICV.plurry.unity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.SICV.plurry.MainActivity;

public class SharedClass {

    // 메인 액티비티로 돌아가기
    public static void showMainActivity(String dataFromUnity) {
        // UnityPlayerGameActivity.currentActivity 대신에 다른 접근 방법 사용
        Activity currentActivity = null;
        try {
            // UnityPlayer 클래스에서 현재 액티비티 가져오기 시도
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            java.lang.reflect.Field field = unityPlayerClass.getDeclaredField("currentActivity");
            field.setAccessible(true);
            currentActivity = (Activity) field.get(null);
        } catch (Exception e) {
            Log.e("SharedClass", "Error getting current activity", e);
        }

        if (currentActivity != null) {
            showMainActivity(currentActivity, dataFromUnity);
        } else {
            Log.e("SharedClass", "Current activity is null, cannot show main activity");
        }
    }

    public static void showMainActivity(Activity activity, String dataFromUnity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (dataFromUnity != null && !dataFromUnity.isEmpty()) {
            intent.putExtra("dataFromUnity", dataFromUnity);
        }
        activity.startActivity(intent);
    }

    // Unity 액티비티 시작
    public static void startUnityActivity(Activity activity) {
        try {
            Intent intent = new Intent(activity, com.SICV.plurry.unity.MainUnityGameActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            /*
            if (dataForUnity != null && !dataForUnity.isEmpty()) {
                intent.putExtra("dataForUnity", dataForUnity);
            }
            */

            activity.startActivity(intent);
            //Log.d("SharedClass", "Started Unity activity with data: " + dataForUnity);
        } catch (Exception e) {
            Log.e("SharedClass", "Error starting Unity activity", e);
        }
    }
}