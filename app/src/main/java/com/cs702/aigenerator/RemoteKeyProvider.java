package com.cs702.aigenerator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteKeyProvider {
    private static final String PREFS = "remote_key_guard";
    private static final String KEY_FRAGMENT = "fragment_v1";
    private static final String URL = "http://4.155.227.179/api/aig/fragment?v=1";
    private static final String APP_ID = "com.cs702.aigenerator";

    public interface Callback {
        void onSuccess(String fragment);
        void onFailure(String message);
    }

    public static String getCachedFragment(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fragment = prefs.getString(KEY_FRAGMENT, "");
        return isValid(fragment) ? fragment : "";
    }

    private static void cacheFragment(Context context, String fragment) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FRAGMENT, fragment)
            .apply();
    }

    private static boolean isValid(String fragment) {
        return fragment != null && fragment.startsWith("rfrag_v1_") && fragment.length() >= 18;
    }

    public static void ensureFragment(Context context, Callback callback) {
        String cached = getCachedFragment(context);
        if (isValid(cached)) {
            callback.onSuccess(cached);
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        new Thread(() -> {
            Request request = new Request.Builder()
                .url(URL)
                .get()
                .header("X-App-Id", APP_ID)
                .header("X-Key-Version", "1")
                .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("HTTP " + response.code());
                }
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                String fragment = json.optString("fragment", "");
                if (!isValid(fragment)) {
                    throw new IOException("invalid fragment");
                }
                cacheFragment(context, fragment);
                main.post(() -> callback.onSuccess(fragment));
            } catch (Exception e) {
                main.post(() -> callback.onFailure("remote-fragment-unavailable"));
            }
        }).start();
    }
}
