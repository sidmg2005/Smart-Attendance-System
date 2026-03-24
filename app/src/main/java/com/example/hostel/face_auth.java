package com.example.hostel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class face_auth extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvInstruction;
    private ProgressBar progressBar;

    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;

    private boolean isFinished = false;
    private Timer timeoutTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_auth_core);

        previewView = findViewById(R.id.previewView);
        tvInstruction = findViewById(R.id.tvInstruction);
        progressBar = findViewById(R.id.progressBar);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);

        String userId = getIntent().getStringExtra("studentId");
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Authentication failed: No Student ID supplied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(options);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finishActivityCancel());
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show();
                    finishActivityCancel();
                }
            }).launch(Manifest.permission.CAMERA);
        } else {
            startCamera();
        }

        // 15 second timeout safely terminating session
        timeoutTimer = new Timer();
        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isFinished) {
                    isFinished = true;
                    runOnUiThread(() -> {
                        Toast.makeText(face_auth.this, "Face detection timeout.", Toast.LENGTH_LONG).show();
                        finishActivityCancel();
                    });
                }
            }
        }, 15000);
    }

    private void finishActivityCancel() {
        if (timeoutTimer != null) timeoutTimer.cancel();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                }

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("FaceAuth", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        if (isFinished) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (isFinished) return;

                        if (faces.size() == 0) {
                            if (tvInstruction != null) tvInstruction.setText("No face detected...");
                        } else if (faces.size() > 1) {
                            if (tvInstruction != null) tvInstruction.setText("Multiple faces detected! Please isolate target.");
                        } else {
                            // Valid single Face Presence natively confirmed
                            isFinished = true;
                            if (timeoutTimer != null) timeoutTimer.cancel();

                            runOnUiThread(() -> {
                                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                                if (tvInstruction != null) tvInstruction.setText("Face Verified!");
                                setResult(RESULT_OK);
                                finish();
                            });
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeoutTimer != null) timeoutTimer.cancel();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
