package com.cs702.aigenerator;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.cs702.aigenerator.databinding.ActivityMainBinding;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ApiService apiService;
    private Call<?> currentCall;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Bitmap lastGeneratedBitmap;
    private String lastGeneratedImageUrl;
    private String currentSignature;

    private static final String BASE_URL = "https://ai.elliottwen.info/";
    // Replace with your actual Authorization header from the course
    private static final String AUTH_KEY = "c0957e34a11786192e8819a7d4faef725c3a0becf05716823b30e37111196e92ba1953a695dddd761cce8abbffefce40da8059d06aa651a02f9cc3322a7d1e0b";

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    saveImageToGallery();
                } else {
                    Toast.makeText(this, R.string.error_save, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupApiService();
        setupClickListeners();
    }

    private void setupApiService() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void setupClickListeners() {
        binding.btnGenerate.setOnClickListener(v -> generateImage());
        binding.btnSave.setOnClickListener(v -> checkPermissionAndSave());
        binding.btnCancel.setOnClickListener(v -> cancelRequest());
    }

    private void generateImage() {
        String prompt = binding.etPrompt.getText().toString().trim();
        if (prompt.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        binding.tvPlaceholder.setVisibility(View.GONE);
        binding.btnSave.setEnabled(false);

        // Step 1: Authenticate
        apiService.auth(AUTH_KEY).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    onError(getString(R.string.error_auth));
                    return;
                }

                String signature = response.body().getSignature();
                if (signature == null || signature.isEmpty()) {
                    onError(getString(R.string.error_auth));
                    return;
                }

                currentSignature = signature;

                // Step 2: Generate image — server returns plain string like "images/xxx.jpg"
                GenerateRequest request = new GenerateRequest(signature, prompt);
                apiService.generateImage(AUTH_KEY, request).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            onError(getString(R.string.error_generation));
                            return;
                        }

                        try {
                            // Server returns plain string like "images/xxx.jpg"
                            String raw = response.body().string();
                            // Remove surrounding quotes if present
                            raw = raw.trim();
                            if (raw.startsWith("\"")) raw = raw.substring(1);
                            if (raw.endsWith("\"")) raw = raw.substring(0, raw.length() - 1);
                            raw = raw.trim();

                            if (raw.isEmpty()) {
                                onError(getString(R.string.error_generation));
                                return;
                            }

                            // Build full URL
                            String fullUrl = raw.startsWith("http") ? raw : BASE_URL + raw;
                            lastGeneratedImageUrl = fullUrl;
                            showImage(fullUrl);
                        } catch (Exception e) {
                            e.printStackTrace();
                            onError(getString(R.string.error_generation));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        if (!call.isCanceled()) {
                            onError(getString(R.string.error_network));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                if (!call.isCanceled()) {
                    onError(getString(R.string.error_network));
                }
            }
        });
    }

    private void showImage(String imageUrl) {
        binding.progressBar.setVisibility(View.GONE);
        binding.btnCancel.setVisibility(View.GONE);

        Glide.with(this)
                .load(imageUrl)
                .into(binding.ivImage);

        binding.ivImage.setImageDrawable(null);
        binding.btnSave.setEnabled(true);

        // Download bitmap in background for saving
        executor.execute(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder().url(imageUrl).build();
                okhttp3.Response response = new OkHttpClient().newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                    lastGeneratedBitmap = android.graphics.BitmapFactory.decodeStream(
                            response.body().byteStream(), null, options);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showLoading(boolean show) {
        if (show) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnCancel.setVisibility(View.VISIBLE);
            binding.ivImage.setImageDrawable(null);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnCancel.setVisibility(View.GONE);
        }
    }

    private void onError(String message) {
        showLoading(false);
        binding.tvPlaceholder.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void cancelRequest() {
        if (currentCall != null) {
            currentCall.cancel();
        }
        showLoading(false);
        binding.tvPlaceholder.setVisibility(View.VISIBLE);
    }

    private void checkPermissionAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGallery();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                saveImageToGallery();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void saveImageToGallery() {
        if (lastGeneratedBitmap == null) {
            Toast.makeText(this, R.string.error_save, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "AI_Image_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIImageGenerator");
                }

                android.content.ContentResolver resolver = getContentResolver();
                android.net.Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                if (imageUri != null) {
                    OutputStream out = resolver.openOutputStream(imageUri);
                    if (out != null) {
                        lastGeneratedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        out.close();
                        runOnUiThread(() -> Toast.makeText(this, R.string.success_saved, Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, R.string.error_save, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
