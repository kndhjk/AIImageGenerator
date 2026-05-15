package com.cs702.aigenerator;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Main Activity for AI Image Generator - CS702 Assignment.
 *
 * Security features (Fortify part):
 * - API key stored via XOR+Base64 obfuscation (NativeKeyStore)
 * - SSL Certificate Pinning via OkHttp + SecurityConfig
 * - Encrypted network traffic with pinned certificates
 * - Cancel button for user control over network operations
 */
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ApiService apiService;
    private Call<?> currentCall;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Bitmap lastGeneratedBitmap;
    private String lastGeneratedImageUrl;

    private static final String TAG = "AIImageGen.Main";

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

        Log.d(TAG, "onCreate: initializing ApiService");
        setupApiService();
        setupClickListeners();

        RootDetector.RootCheckResult rootCheck = RootDetector.check(getApplicationContext());
        if (rootCheck.isRooted) {
            StringBuilder sb = new StringBuilder();
            for (String w : rootCheck.warnings) sb.append("• ").append(w).append("\n");
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("\u26a0\ufe0f Security Warning")
                .setMessage("Root/Jailbreak detected:\n\n" + sb.toString() + "Your API key may be at risk.")
                .setPositiveButton("I Understand", null)
                .setCancelable(false)
                .show();
        }

        RuntimeGuard.ThreatReport threatReport = RuntimeGuard.inspect(getApplicationContext());
        if (!RuntimeGuard.isDebugBuild(getApplicationContext()) && threatReport.suspicious) {
            StringBuilder sb = new StringBuilder();
            for (String r : threatReport.reasons) sb.append("• ").append(r).append("\n");
            binding.btnGenerate.setEnabled(false);
            new AlertDialog.Builder(this)
                .setTitle("Security Lock")
                .setMessage("Sensitive operations are blocked in this environment:\n\n" + sb)
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show();
        }

        Log.d(TAG, "onCreate: done, btnGenerate=" + binding.btnGenerate);
    }

    private void setupApiService() {
        Log.d(TAG, "setupApiService: building OkHttpClient with logging");

        // FORTIFY: Use SSL pinning via SecurityConfig
        OkHttpClient.Builder builder = SecurityConfig.buildSecureOkHttpClient(getApplicationContext()).newBuilder();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        if (RuntimeGuard.isDebugBuild(getApplicationContext())) {
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        } else {
            logging.setLevel(HttpLoggingInterceptor.Level.NONE);
        }

        OkHttpClient client = builder.build();
        Log.d(TAG, "setupApiService: OkHttpClient built, logging=" + logging.getLevel());

        String baseUrl = NativeKeyStore.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("baseUrl unavailable");
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
        Log.d(TAG, "setupApiService: ApiService created, baseUrl=" + retrofit.baseUrl());
    }

    private void setupClickListeners() {
        binding.btnGenerate.setOnClickListener(v -> {
            Log.d(TAG, "btnGenerate clicked!");
            generateImage();
        });
        binding.btnSave.setOnClickListener(v -> checkPermissionAndSave());
        binding.btnCancel.setOnClickListener(v -> cancelRequest());
    }

    private void generateImage() {
        Log.d(TAG, "generateImage: START");

        String prompt = binding.etPrompt.getText().toString().trim();
        Log.d(TAG, "generateImage: prompt received");

        if (prompt.isEmpty()) {
            Log.d(TAG, "generateImage: empty prompt");
            Toast.makeText(this, R.string.error_empty_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        if (RuntimeGuard.shouldBlockSensitiveOps(getApplicationContext())) {
            onError("Security policy blocked this request on the current device");
            return;
        }

        showLoading(true);
        binding.placeholderContainer.setVisibility(View.GONE);
        binding.btnSave.setEnabled(false);

        String apiKey = NativeKeyStore.getApiKey(getApplicationContext());
        if (!NativeKeyStore.isKeyValid(apiKey)) {
            Log.d(TAG, "generateImage: invalid key state");
            showLoading(false);
            Toast.makeText(MainActivity.this, R.string.error_key_config, Toast.LENGTH_LONG).show();
            return;
        }

        startGeneration(prompt);
    }

    private void startGeneration(String prompt) {
        Log.d(TAG, "generateImage: calling apiService.auth()");
        // Step 1: Authenticate with API key
        apiService.auth().enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                Log.d(TAG, "auth onResponse: isSuccessful=" + response.isSuccessful() + " code=" + response.code());
                if (!response.isSuccessful() || response.body() == null) {
                    Log.d(TAG, "auth failed: " + response.code());
                    onError(getString(R.string.error_auth));
                    return;
                }

                String signature = response.body().getSignature();
                Log.d(TAG, "auth success: signature received");

                if (signature == null || signature.isEmpty()) {
                    onError(getString(R.string.error_auth));
                    return;
                }

                // Step 2: Generate image with signature from auth
                GenerateRequest request = new GenerateRequest(signature, prompt);
                Log.d(TAG, "generateImage: calling apiService.generateImage()");
                apiService.generateImage(request).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        Log.d(TAG, "generate onResponse: isSuccessful=" + response.isSuccessful() + " code=" + response.code());
                        if (!response.isSuccessful() || response.body() == null) {
                            onError(getString(R.string.error_generation));
                            return;
                        }

                        try {
                            // Server returns plain string like "images/xxx.jpg"
                            String raw = response.body().string().trim();
                            if (raw.startsWith("\"")) raw = raw.substring(1, raw.length() - 1);
                            raw = raw.trim();

                            Log.d(TAG, "generate raw response: " + raw);
                            if (raw.isEmpty()) {
                                onError(getString(R.string.error_generation));
                                return;
                            }

                            String baseUrl = NativeKeyStore.getBaseUrl();
                            String fullUrl = raw.startsWith("http") ? raw : baseUrl + raw;
                            lastGeneratedImageUrl = fullUrl;
                            showImage(fullUrl);
                        } catch (Exception e) {
                            e.printStackTrace();
                            onError(getString(R.string.error_generation));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.d(TAG, "generate onFailure: " + t.getMessage());
                        if (!call.isCanceled()) {
                            onError(getString(R.string.error_network));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.d(TAG, "auth onFailure: " + t.getMessage());
                if (!call.isCanceled()) {
                    onError(getString(R.string.error_network));
                }
            }
        });
    }

    private void showImage(String imageUrl) {
        Log.d(TAG, "showImage: " + imageUrl);
        showLoading(false);

        Glide.with(this)
                .load(imageUrl)
                .into(binding.ivImage);

        binding.btnSave.setEnabled(true);

        // Download bitmap in background for saving
        executor.execute(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder().url(imageUrl).build();
                okhttp3.Response response = new OkHttpClient().newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    lastGeneratedBitmap = BitmapFactory.decodeStream(
                            response.body().byteStream(), null, options);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showLoading(boolean show) {
        if (show) {
            binding.loadingOverlay.setVisibility(View.VISIBLE);
            binding.btnCancel.setVisibility(View.VISIBLE);
            binding.ivImage.setImageDrawable(null);
        } else {
            binding.loadingOverlay.setVisibility(View.GONE);
            binding.btnCancel.setVisibility(View.GONE);
        }
    }

    private void onError(String message) {
        Log.d(TAG, "onError: " + message);
        showLoading(false);
        binding.placeholderContainer.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void cancelRequest() {
        if (currentCall != null) {
            currentCall.cancel();
        }
        showLoading(false);
        binding.placeholderContainer.setVisibility(View.VISIBLE);
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