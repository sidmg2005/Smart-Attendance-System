package com.example.hostel;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TimePicker;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Date;

public class AdminHome extends AppCompatActivity {

    TextView tvDate, tvCode, tvStartTime, tvEndTime;

    Button btnSetLocation, btnChangeCode, btnShareCode,
            btnSetStartTime, btnSetEndTime,
            btnPresent, btnAbsent, btnAdminReport, btnSignOut,
            btnSelectExcel, btnUpdateExcel;

    String currentCode = "123456";
    String startTime = "Not Set";
    String endTime = "Not Set";

    FusedLocationProviderClient fusedLocationClient;
    DatabaseReference locationRef;

    ActivityResultLauncher<String[]> requestPermissionLauncher;
    ActivityResultLauncher<Intent> excelPickerLauncher;
    SharedPreferences prefs;

    String wardenId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_home);

        // 🔔 Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        wardenId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "warden_123";

        // Views
        tvDate = findViewById(R.id.tvDate);
        tvCode = findViewById(R.id.tvCode);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);

        btnSetLocation = findViewById(R.id.btnSetLocation);
        btnChangeCode = findViewById(R.id.changecode);
        btnShareCode = findViewById(R.id.btnShareCode);
        btnSetStartTime = findViewById(R.id.btnSetStartTime);
        btnSetEndTime = findViewById(R.id.btnSetEndTime);

        btnPresent = findViewById(R.id.btnpresent);
        btnAbsent = findViewById(R.id.btnabsent);
        btnAdminReport = findViewById(R.id.btnAdminReport);
        btnSignOut = findViewById(R.id.btnSignOut);

        btnSelectExcel = findViewById(R.id.btnSelectExcel);
        btnUpdateExcel = findViewById(R.id.btnUpdateExcel);
        prefs = getSharedPreferences("HostelPrefs", MODE_PRIVATE);

        excelPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            prefs.edit().putString("excel_uri", uri.toString()).apply();
                            Toast.makeText(this, "Excel file linked successfully!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Date & Code
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        tvDate.setText("Date: " + today);
        tvCode.setText("Code: " + currentCode);

        // Firebase
        locationRef = FirebaseDatabase.getInstance()
                .getReference("HostelLocation")
                .child(wardenId);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Permissions
        requestPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {
                            Boolean fine = result.getOrDefault(
                                    Manifest.permission.ACCESS_FINE_LOCATION, false);
                            if (fine) {
                                getAndSaveLocation();
                            } else {
                                Toast.makeText(this,
                                        "Location permission required",
                                        Toast.LENGTH_LONG).show();
                            }
                        });

        // Buttons
        btnSetLocation.setOnClickListener(v -> checkPermission());

        btnChangeCode.setOnClickListener(v -> {
            currentCode = String.valueOf((int) (Math.random() * 900000) + 100000);
            tvCode.setText("Code: " + currentCode);
            Toast.makeText(this, "New Code: " + currentCode, Toast.LENGTH_SHORT).show();
        });

        btnShareCode.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,
                    "Today's Attendance Code: " + currentCode +
                            "\nStart Time: " + startTime +
                            "\nEnd Time: " + endTime);
            startActivity(Intent.createChooser(intent, "Share Code"));
        });

        btnSetStartTime.setOnClickListener(v -> setTime(true));
        btnSetEndTime.setOnClickListener(v -> setTime(false));

        btnPresent.setOnClickListener(v ->
                startActivity(new Intent(AdminHome.this, Present.class)));

        btnAbsent.setOnClickListener(v ->
                startActivity(new Intent(AdminHome.this, Absent.class)));

        btnAdminReport.setOnClickListener(v ->
                startActivity(new Intent(AdminHome.this, adminreport.class)));

        btnSelectExcel.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            excelPickerLauncher.launch(intent);
        });

        btnUpdateExcel.setOnClickListener(v -> syncExcelData());

        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, login.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        // 🔔 START REAL-TIME NOTIFICATION LISTENER
        listenForNotifications();
    }

    // 🔔 REAL-TIME LISTENER
    private void listenForNotifications() {

        FirebaseDatabase.getInstance()
                .getReference("Notifications")
                .addChildEventListener(new ChildEventListener() {

                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {

                        String title = snapshot.child("title").getValue(String.class);
                        String message = snapshot.child("message").getValue(String.class);

                        if (title != null && message != null) {
                            showNotification(title, message);
                        }
                    }

                    @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String s) {}
                    @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
                    @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String s) {}
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // 🔔 SHOW NOTIFICATION
    private void showNotification(String title, String message) {

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String channelId = "hostel_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Hostel Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // ------------------ EXCEL SYNC LOGIC ------------------

    private void syncExcelData() {
        String uriString = prefs.getString("excel_uri", null);
        if (uriString == null) {
            Toast.makeText(this, "Please Select File first!", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse(uriString);
        Toast.makeText(this, "Syncing data to Excel...", Toast.LENGTH_SHORT).show();

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance");

        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                HashMap<String, String> nameMap = new HashMap<>();
                for (DataSnapshot user : usersSnapshot.getChildren()) {
                    String sId = user.getKey();
                    String name = user.child("name").getValue(String.class);
                    nameMap.put(sId, name != null ? name : "Unknown");
                }

                attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attSnapshot) {
                        java.util.List<ExcelHelper.AttendanceRecord> records = new java.util.ArrayList<>();
                        for (DataSnapshot studentData : attSnapshot.getChildren()) {
                            String sId = studentData.getKey();
                            String sName = nameMap.containsKey(sId) ? nameMap.get(sId) : "Unknown";

                            for (DataSnapshot record : studentData.getChildren()) {
                                String date = record.child("date").getValue(String.class);
                                String status = record.child("status").getValue(String.class);
                                if (date != null && status != null) {
                                    records.add(new ExcelHelper.AttendanceRecord(sId, sName, date, status));
                                }
                            }
                        }

                        // Call external ExcelHelper SAF Implementation
                        ExcelHelper.syncAttendanceToUri(AdminHome.this, uri, records, nameMap);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AdminHome.this, "DB Error", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminHome.this, "DB Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ EXISTING CODE (UNCHANGED) ------------------

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });

        } else {
            getAndSaveLocation();
        }
    }

    private void setTime(boolean start) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        new TimePickerDialog(this, (TimePicker view, int h, int m) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", h, m);
            if (start) {
                startTime = time;
                tvStartTime.setText("Start Time: " + startTime);
            } else {
                endTime = time;
                tvEndTime.setText("End Time: " + endTime);
            }
        }, hour, minute, true).show();
    }

    private void getAndSaveLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please turn ON GPS", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveLocation(location.getLatitude(), location.getLongitude());
                    } else {
                        requestCurrentLocation();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Location Error", Toast.LENGTH_LONG).show());
    }

    private void requestCurrentLocation() {
        CancellationTokenSource cts = new CancellationTokenSource();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveLocation(location.getLatitude(), location.getLongitude());
                    } else {
                        Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveLocation(double lat, double lon) {

        String key = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        HashMap<String, Object> map = new HashMap<>();
        map.put("latitude", lat);
        map.put("longitude", lon);
        map.put("timestamp", System.currentTimeMillis());
        map.put("wardenId", wardenId);
        map.put("code", currentCode);

        locationRef.child(key).setValue(map);

        DatabaseReference globalRef = FirebaseDatabase.getInstance()
                .getReference("WardenLocation");

        globalRef.child("latitude").setValue(lat);
        globalRef.child("longitude").setValue(lon);

        Toast.makeText(this, "Location Saved", Toast.LENGTH_SHORT).show();
    }
}