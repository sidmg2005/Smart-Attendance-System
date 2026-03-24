package com.example.hostel;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.view.PreviewView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;
import java.util.HashMap;

public class Student_Attendance extends AppCompatActivity {

    // Allowed coordinates (Hostel / College fixed location)
    private static final double ALLOWED_LATITUDE = 12.9716; 
    private static final double ALLOWED_LONGITUDE = 77.5946;
    private static final float ALLOWED_RADIUS_METERS = 100.0f;

    PreviewView previewView;
    Button btnMarkAttendance, btnLeaveRequest, btnSignOut;

    // ✅ Bottom buttons
    Button btnHome, btnReport;

    DatabaseReference attendanceRef, studentsRef, leaveRef;

    String studentId, studentName = "Student";
    boolean isFaceVerified = false;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance);

        TextView tvDate = findViewById(R.id.tvDate);
        Calendar calendar = Calendar.getInstance();
        String currentDate = calendar.get(Calendar.DAY_OF_MONTH) + "/" +
                (calendar.get(Calendar.MONTH) + 1) + "/" +
                calendar.get(Calendar.YEAR);
        tvDate.setText("Date: " + currentDate);

        previewView = findViewById(R.id.previewView);

        btnMarkAttendance = findViewById(R.id.btnMarkAttendance);
        btnLeaveRequest = findViewById(R.id.btnSendLeave);

        // ✅ Initialize buttons
        btnHome = findViewById(R.id.btnHome);
        btnReport = findViewById(R.id.btnReport);
        btnSignOut = findViewById(R.id.btnSignOut);

        studentId = getIntent().getStringExtra("studentId");
        if (studentId == null) {
            studentId = "unknown";
        }

        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance");
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        leaveRef = FirebaseDatabase.getInstance().getReference("LeaveRequests");

        // Fetch actual student name for reports
        studentsRef.child(studentId).child("name").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                studentName = snapshot.getValue(String.class);
            }
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLoc = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    if (fineLoc) {
                        checkLocationBeforeAuth();
                    } else {
                        Toast.makeText(this, "Location permission required for attendance", Toast.LENGTH_LONG).show();
                        btnMarkAttendance.setEnabled(true);
                    }
                }
        );

        btnMarkAttendance.setOnClickListener(v -> {
            btnMarkAttendance.setEnabled(false); // 🔥 Prevent multiple clicks
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            } else {
                checkLocationBeforeAuth();
            }
        });

        btnLeaveRequest.setOnClickListener(v -> showLeaveDialog());

        // ✅ Home Button Click
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(Student_Attendance.this, login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // ✅ Report Button Click
        btnReport.setOnClickListener(v -> {
            Intent intent = new Intent(Student_Attendance.this, StudentReport.class);
            intent.putExtra("studentId", studentId);
            startActivity(intent);
        });

        // ✅ SignOut Button Click
        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Student_Attendance.this, login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Re-enable the button if it was disabled
        if (btnMarkAttendance != null) {
            btnMarkAttendance.setEnabled(true);
        }

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            boolean verified = data.getBooleanExtra("faceVerified", false);
            String reason = data.getStringExtra("reason");

            if (verified) {
                isFaceVerified = true;
                markAttendance("Present");
            } else {
                if ("not_matched".equals(reason)) {
                    Toast.makeText(this, "Face not matched", Toast.LENGTH_SHORT).show();
                } else if ("no_face".equals(reason)) {
                    Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
                } else if (reason != null && !reason.isEmpty()) {
                    Toast.makeText(this, "Verification failed: " + reason, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean isLocationEnabled() {
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }

    private void checkLocationBeforeAuth() {
        if (!isLocationEnabled()) {
             Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show();
             btnMarkAttendance.setEnabled(true);
             return;
        }

        Toast.makeText(this, "Verifying Location...", Toast.LENGTH_SHORT).show();
        CancellationTokenSource cts = new CancellationTokenSource();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(studentLocation -> {
                        if (studentLocation != null) {
                            verifyLocationWithWardenAndProceed(studentLocation);
                        } else {
                            Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
                            btnMarkAttendance.setEnabled(true);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Location Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnMarkAttendance.setEnabled(true);
                    });
        }
    }

    private void verifyLocationWithWardenAndProceed(Location studentLoc) {
        DatabaseReference wardenLocationRef = FirebaseDatabase.getInstance().getReference("WardenLocation");
        wardenLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("latitude") && snapshot.hasChild("longitude")) {
                    double wardenLat = snapshot.child("latitude").getValue(Double.class);
                    double wardenLon = snapshot.child("longitude").getValue(Double.class);

                    Location allowedLoc = new Location("");
                    allowedLoc.setLatitude(wardenLat);
                    allowedLoc.setLongitude(wardenLon);

                    float distanceInMeters = studentLoc.distanceTo(allowedLoc);

                    if (distanceInMeters <= 100.0f) {
                        // Location is correct! Now do face auth.
                        Intent intent = new Intent(Student_Attendance.this, face_auth.class);
                        intent.putExtra("studentId", studentId);
                        startActivityForResult(intent, 1001);
                    } else {
                        Toast.makeText(Student_Attendance.this, "You are not in allowed location", Toast.LENGTH_LONG).show();
                        btnMarkAttendance.setEnabled(true);
                    }
                } else {
                    Toast.makeText(Student_Attendance.this, "Warden location not set yet!", Toast.LENGTH_SHORT).show();
                    btnMarkAttendance.setEnabled(true);
                }
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                Toast.makeText(Student_Attendance.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                btnMarkAttendance.setEnabled(true);
            }
        });
    }

    private void markAttendance(String status) {

        long currentTime = System.currentTimeMillis();
        String currentDate = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(new java.util.Date(currentTime));

        // Organize properly for reports: Attendance -> studentId -> key
        String id = attendanceRef.child(studentId).push().getKey();
        if (id != null) {

            HashMap<String, Object> map = new HashMap<>();
            map.put("studentId", studentId);
            map.put("studentName", studentName);
            map.put("date", currentDate);
            map.put("status", status);
            map.put("timestamp", currentTime);

            attendanceRef.child(studentId).child(id).setValue(map)
                    .addOnSuccessListener(aVoid -> {

                        if (status.equals("Present")) {
                            Toast.makeText(this, "Face verified successfully", Toast.LENGTH_SHORT).show();
                        } // if Absent, the failure toast was already fired.

                        HashMap<String, Object> notification = new HashMap<>();
                        notification.put("title", "Attendance Alert");
                        notification.put("message", studentName + " marked " + status);
                        notification.put("studentId", studentId);
                        notification.put("timestamp", currentTime);

                        FirebaseDatabase.getInstance()
                                .getReference("Notifications")
                                .push()
                                .setValue(notification);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed ❌", Toast.LENGTH_SHORT).show());
        }
    }

    private void showLeaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Leave Request");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 20);
        layout.setBackgroundColor(0xFFFFFFFF);

        layout.addView(createLabel("Student Name"));
        EditText etStudentName = new EditText(this);
        etStudentName.setHint("Enter Name");
        etStudentName.setText(studentName);
        etStudentName.setSingleLine(true);
        etStudentName.setBackgroundResource(android.R.drawable.edit_text);
        layout.addView(etStudentName);

        final Calendar currentCalendar = Calendar.getInstance();

        layout.addView(createLabel("From Date"));
        TextView tvFromDate = new TextView(this);
        tvFromDate.setText(currentCalendar.get(Calendar.DAY_OF_MONTH) + "/" +
                (currentCalendar.get(Calendar.MONTH) + 1) + "/" +
                currentCalendar.get(Calendar.YEAR));
        tvFromDate.setPadding(20, 20, 20, 20);
        tvFromDate.setBackgroundColor(0xFFEFEFEF);
        layout.addView(tvFromDate);

        final Calendar fromCalendar = Calendar.getInstance();
        tvFromDate.setOnClickListener(v -> new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    fromCalendar.set(year, month, dayOfMonth);
                    tvFromDate.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
                }, fromCalendar.get(Calendar.YEAR),
                fromCalendar.get(Calendar.MONTH),
                fromCalendar.get(Calendar.DAY_OF_MONTH)).show());

        layout.addView(createLabel("To Date"));
        TextView tvToDate = new TextView(this);
        tvToDate.setText(currentCalendar.get(Calendar.DAY_OF_MONTH) + "/" +
                (currentCalendar.get(Calendar.MONTH) + 1) + "/" +
                currentCalendar.get(Calendar.YEAR));
        tvToDate.setPadding(20, 20, 20, 20);
        tvToDate.setBackgroundColor(0xFFEFEFEF);
        layout.addView(tvToDate);

        final Calendar toCalendar = Calendar.getInstance();
        tvToDate.setOnClickListener(v -> new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    toCalendar.set(year, month, dayOfMonth);
                    tvToDate.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
                }, toCalendar.get(Calendar.YEAR),
                toCalendar.get(Calendar.MONTH),
                toCalendar.get(Calendar.DAY_OF_MONTH)).show());

        layout.addView(createLabel("Reason"));
        EditText etReason = new EditText(this);
        etReason.setHint("Enter leave reason");
        etReason.setMinLines(3);
        etReason.setGravity(Gravity.TOP | Gravity.START);
        etReason.setBackgroundColor(0xFFEFEFEF);
        layout.addView(etReason);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String name = etStudentName.getText().toString().trim();
            String fromDate = tvFromDate.getText().toString();
            String toDate = tvToDate.getText().toString();
            String reason = etReason.getText().toString().trim();

            if (name.isEmpty() || reason.isEmpty() ||
                    fromDate.isEmpty() || toDate.isEmpty()) {
                Toast.makeText(this, "Please fill all details ❌", Toast.LENGTH_SHORT).show();
                return;
            }

            sendLeaveRequest(studentId, name, reason, fromDate, toDate);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    private void sendLeaveRequest(String id, String name, String reason, String fromDate, String toDate) {

        String key = leaveRef.push().getKey();
        if (key == null) return;

        HashMap<String, Object> map = new HashMap<>();
        map.put("studentId", id);
        map.put("studentName", name);
        map.put("reason", reason);
        map.put("fromDate", fromDate);
        map.put("toDate", toDate);
        map.put("status", "Pending");

        leaveRef.child(key).setValue(map)
                .addOnSuccessListener(aVoid -> {

                    Toast.makeText(this, "Leave Request Sent ✅", Toast.LENGTH_SHORT).show();

                    HashMap<String, Object> notification = new HashMap<>();
                    notification.put("title", "Leave Request");
                    notification.put("message", name + " sent a leave request");
                    notification.put("studentId", id);
                    notification.put("timestamp", System.currentTimeMillis());

                    FirebaseDatabase.getInstance()
                            .getReference("Notifications")
                            .push()
                            .setValue(notification);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed ❌", Toast.LENGTH_SHORT).show());
    }

    private TextView createLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16f);
        label.setPadding(0, 10, 0, 5);
        return label;
    }
}