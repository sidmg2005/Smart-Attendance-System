package com.example.hostel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Patterns;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import android.graphics.Rect;
import java.util.Arrays;

public class StudentRegister extends AppCompatActivity {

    EditText etName, etEmail, etStudentId, etPassword, etPhone;
    Button btnGallery, btnCamera, btnRegister;
    ImageView imageView;

    Bitmap studentImage;

    DatabaseReference databaseReference;
    private FaceDetector faceDetector;

    private boolean isFaceDetected = false;

    ActivityResultLauncher<Intent> galleryLauncher;
    ActivityResultLauncher<Intent> cameraLauncher;
    ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_register);

        FirebaseApp.initializeApp(this);

        // 🔥 FIXED: Correct Firebase URL added
        databaseReference = FirebaseDatabase
                .getInstance("https://hostel-attendance-ce06a-default-rtdb.firebaseio.com/")
                .getReference("Students");

        etName = findViewById(R.id.etStudentName);
        etEmail = findViewById(R.id.etStudentEmail);
        etStudentId = findViewById(R.id.etStudentId);
        etPassword = findViewById(R.id.etStudentPassword);
        etPhone = findViewById(R.id.etStudentPhone);

        btnGallery = findViewById(R.id.btnSelectPhoto);
        btnCamera = findViewById(R.id.btnCapturePhoto);
        btnRegister = findViewById(R.id.btnStudentRegister);
        imageView = findViewById(R.id.evStudentLogo);

        initLaunchers();

        btnGallery.setOnClickListener(v -> openGallery());
        btnCamera.setOnClickListener(v -> openCamera());
        btnRegister.setOnClickListener(v -> registerStudent());

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private void initLaunchers() {

        permissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) openCamera();
                            else Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                        });

        galleryLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                try {
                                    studentImage = MediaStore.Images.Media.getBitmap(
                                            getContentResolver(), result.getData().getData());
                                    imageView.setImageBitmap(studentImage);
                                    processImageForEmbedding(studentImage);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });

        cameraLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK &&
                                    result.getData() != null &&
                                    result.getData().getExtras() != null) {

                                studentImage = (Bitmap) result.getData().getExtras().get("data");
                                imageView.setImageBitmap(studentImage);
                                processImageForEmbedding(studentImage);
                            }
                        });
    }

    private void processImageForEmbedding(Bitmap bitmap) {
        if (bitmap == null) return;
        Toast.makeText(this, "Validating face...", Toast.LENGTH_SHORT).show();

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() == 0) {
                        Toast.makeText(this, "No face detected. Try again.", Toast.LENGTH_LONG).show();
                    } else if (faces.size() > 1) {
                        Toast.makeText(this, "❌ Multiple faces detected! Only one face allowed.", Toast.LENGTH_LONG).show();
                    } else {
                        isFaceDetected = true;
                        Toast.makeText(this, "Valid Face Detected! You may register.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Face detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void openCamera() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            permissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void registerStudent() {

        if (!isFaceDetected || studentImage == null) {
            Toast.makeText(this, "Please capture or select a photo containing exactly one clearly visible face.", Toast.LENGTH_LONG).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String studentId = etStudentId.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        // ✅ VALIDATION
        if (name.isEmpty()) {
            etName.setError("Enter Name");
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter Valid Email");
            return;
        }

        if (studentId.length() < 4) {
            etStudentId.setError("Min 4 characters");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Min 6 characters");
            return;
        }

        if (phone.length() != 10) {
            etPhone.setError("Enter 10 digit phone");
            return;
        }

        String id = studentId;

        // 🔥 Disable button to prevent multiple clicks
        btnRegister.setEnabled(false);
        Toast.makeText(this, "Checking ID...", Toast.LENGTH_SHORT).show();

        databaseReference.child(id).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                Toast.makeText(this, "Student ID already registered!", Toast.LENGTH_LONG).show();
                btnRegister.setEnabled(true);
            } else {
                Toast.makeText(this, "Finalizing Multi-Face Registration...", Toast.LENGTH_SHORT).show();
                proceedWithRegistration(name, email, studentId, password, phone, id);
            }
        });
    }

    private void proceedWithRegistration(String name, String email, String studentId, String password, String phone, String id) {
        if (studentImage == null) {
            Toast.makeText(this, "Profile image is missing!", Toast.LENGTH_SHORT).show();
            btnRegister.setEnabled(true);
            return;
        }

        Toast.makeText(this, "Uploading assets securely...", Toast.LENGTH_SHORT).show();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            studentImage.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] imageData = baos.toByteArray();

            StorageReference storageRef = FirebaseStorage.getInstance().getReference("student_faces").child(id + ".jpg");

            // 🔥 FIX: Wait for putBytes to fully succeed before querying getDownloadUrl
            storageRef.putBytes(imageData)
                    .addOnSuccessListener(taskSnapshot -> {
                        storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();

                            HashMap<String, Object> studentMap = new HashMap<>();
                            studentMap.put("name", name);
                            studentMap.put("email", email);
                            studentMap.put("studentId", studentId);
                            studentMap.put("password", password);
                            studentMap.put("phone", phone);
                            studentMap.put("imageUrl", imageUrl);

                            databaseReference.child(id).setValue(studentMap)
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(this, "Registration Successful ✅", Toast.LENGTH_SHORT).show();

                                        etName.setText("");
                                        etEmail.setText("");
                                        etStudentId.setText("");
                                        etPassword.setText("");
                                        etPhone.setText("");
                                        imageView.setImageResource(R.mipmap.ic_launcher);
                                        studentImage = null;
                                        isFaceDetected = false;

                                        btnRegister.setEnabled(true);
                                    })
                                    .addOnFailureListener(e -> {
                                        android.util.Log.e("FirebaseDB", "DB Save Error: " + e.getMessage());
                                        Toast.makeText(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        btnRegister.setEnabled(true);
                                    });
                        }).addOnFailureListener(e -> {
                            Toast.makeText(this, "URL Fetch Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            btnRegister.setEnabled(true);
                        });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Storage Upload Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnRegister.setEnabled(true);
                    });

        } catch (Exception ex) {
            Toast.makeText(this, "Encoding Exception: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            btnRegister.setEnabled(true);
        }
    }

    private String encodeImagePreviewFallback(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private Bitmap decodeImage(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}