package com.example.hostel;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.app.DatePickerDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Calendar;
import android.os.Environment;

public class adminreport extends AppCompatActivity {

    private TextView tvTotalStudents, tvPendingLeaves, tvApprovedLeaves;
    private Button btnGenerateCsv, btnSelectCsv, btnGenerateExcel;
    private RecyclerView recyclerLeaves;

    private LeaveRequestAdapter leaveAdapter;
    private List<LeaveRequest> leaveRequestList;

    private DatabaseReference studentsRef, leavesRef;

    private Uri selectedCsvUri = null;

    private static final int PERMISSION_REQUEST_CODE = 100;

    private ActivityResultLauncher<Intent> csvPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminreport);

        tvTotalStudents = findViewById(R.id.tvTotalStudents);
        tvPendingLeaves = findViewById(R.id.tvPendingLeaves);
        tvApprovedLeaves = findViewById(R.id.tvApprovedLeaves);
        btnGenerateCsv = findViewById(R.id.btnGenerateCsv);
        btnSelectCsv = findViewById(R.id.btnSelectCsv);
        btnGenerateExcel = findViewById(R.id.btnGenerateExcel);
        recyclerLeaves = findViewById(R.id.recyclerLeaves);

        leaveRequestList = new ArrayList<>();

        leaveAdapter = new LeaveRequestAdapter(this, leaveRequestList, (request, status) -> {
            if (request != null && request.getRequestId() != null) {
                leavesRef.child(request.getRequestId()).child("status")
                        .setValue(status)
                        .addOnSuccessListener(aVoid -> {

                            Toast.makeText(adminreport.this, "Request " + status, Toast.LENGTH_SHORT).show();

                            // 🔔 SEND NOTIFICATION TO STUDENT (UPDATED)
                            HashMap<String, Object> notification = new HashMap<>();
                            notification.put("title", "Leave Status Update");
                            notification.put("message", request.getStudentName() + " your leave is " + status);
                            notification.put("studentId", request.getStudentId());
                            notification.put("timestamp", System.currentTimeMillis());

                            FirebaseDatabase.getInstance()
                                    .getReference("Notifications")
                                    .push()
                                    .setValue(notification);

                            leaveRequestList.remove(request);
                            leaveAdapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(adminreport.this, "Failed to update status", Toast.LENGTH_SHORT).show();
                        });
            }
        });

        recyclerLeaves.setLayoutManager(new LinearLayoutManager(this));
        recyclerLeaves.setAdapter(leaveAdapter);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        leavesRef = FirebaseDatabase.getInstance().getReference("LeaveRequests");

        loadDashboardCounts();
        loadPendingLeaveRequests();

        csvPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCsvUri = result.getData().getData();
                        Toast.makeText(this, "CSV file selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        btnSelectCsv.setOnClickListener(v -> pickCsvFile());

        btnGenerateCsv.setOnClickListener(v -> {
            if (selectedCsvUri == null) {
                pickCsvFile();
            } else {
                if (checkPermission()) createOrUpdateCsv();
                else requestPermission();
            }
        });

        btnGenerateExcel.setOnClickListener(v -> {
            if (checkPermission()) {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(adminreport.this, (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(java.util.Locale.getDefault(), "%02d-%02d-%04d", dayOfMonth, month + 1, year);
                    createAttendanceExcel(selectedDate);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            } else {
                requestPermission(200);
            }
        });
    }

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        csvPickerLauncher.launch(intent);
    }

    private void loadDashboardCounts() {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tvTotalStudents.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        leavesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long pending = 0, approved = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.child("status").getValue() != null) {
                        String status = child.child("status").getValue(String.class);
                        if ("Pending".equals(status)) pending++;
                        else if ("Approved".equals(status)) approved++;
                    }
                }
                tvPendingLeaves.setText(String.valueOf(pending));
                tvApprovedLeaves.setText(String.valueOf(approved));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadPendingLeaveRequests() {
        leavesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                leaveRequestList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    LeaveRequest req = ds.getValue(LeaveRequest.class);
                    if (req != null) {
                        req.setRequestId(ds.getKey());
                        if ("Pending".equals(req.getStatus())) leaveRequestList.add(req);
                    }
                }
                leaveAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void createOrUpdateCsv() {
        leavesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(adminreport.this, "No leave requests to export", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    OutputStream outputStream;

                    if (selectedCsvUri != null) {
                        outputStream = getContentResolver().openOutputStream(selectedCsvUri, "rwt");
                    } else {
                        String fileName = "LeaveReport.csv";
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (uri == null) throw new Exception("Cannot create CSV file");
                            outputStream = getContentResolver().openOutputStream(uri);
                            selectedCsvUri = uri;
                        } else {
                            outputStream = openFileOutput(fileName, MODE_PRIVATE);
                        }
                    }

                    StringBuilder csvData = new StringBuilder();
                    csvData.append("Student ID,Student Name,From Date,To Date,Reason,Status\n");

                    for (DataSnapshot ds : snapshot.getChildren()) {
                        LeaveRequest req = ds.getValue(LeaveRequest.class);
                        if (req != null) {
                            csvData.append(req.getStudentId() != null ? req.getStudentId() : "-").append(",")
                                    .append(req.getStudentName() != null ? req.getStudentName() : "-").append(",")
                                    .append(req.getFromDate() != null ? req.getFromDate() : "-").append(",")
                                    .append(req.getToDate() != null ? req.getToDate() : "-").append(",")
                                    .append(req.getReason() != null ? req.getReason() : "-").append(",")
                                    .append(req.getStatus() != null ? req.getStatus() : "Pending").append("\n");
                        }
                    }

                    outputStream.write(csvData.toString().getBytes());
                    outputStream.flush();
                    outputStream.close();

                    Toast.makeText(adminreport.this, "CSV saved/updated successfully", Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(adminreport.this, "Failed to save/update CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(adminreport.this, "Failed to fetch leave requests: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createAttendanceExcel(String selectedDate) {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentsSnapshot) {
                // Extract all students to ensure everyone is listed
                List<String[]> allStudents = new ArrayList<>();
                for (DataSnapshot ds : studentsSnapshot.getChildren()) {
                    String sId = ds.child("studentId").getValue(String.class);
                    if (sId == null) sId = ds.getKey();
                    String sName = ds.child("name").getValue(String.class);
                    allStudents.add(new String[]{sId, sName});
                }

                DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance");
                attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(adminreport.this, "No attendance records found.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    Workbook workbook = null;
                    Sheet sheet = null;
                    String fileName = "attendance.xlsx";

                    boolean isExisting = false;
                    Uri existingUri = null;
                    File existingFile = null;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        String[] projection = new String[]{MediaStore.Downloads._ID};
                        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
                        String[] selectionArgs = new String[]{fileName};
                        try (android.database.Cursor cursor = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)) {
                            if (cursor != null && cursor.moveToFirst()) {
                                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                                existingUri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                                isExisting = true;
                            }
                        }
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        existingFile = new File(dir, fileName);
                        if (existingFile.exists()) {
                            isExisting = true;
                        }
                    }

                    if (isExisting) {
                        java.io.InputStream is = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && existingUri != null) {
                            is = getContentResolver().openInputStream(existingUri);
                        } else if (existingFile != null) {
                            is = new java.io.FileInputStream(existingFile);
                        }
                        if (is != null) {
                            workbook = new XSSFWorkbook(is);
                            is.close();
                            sheet = workbook.getSheetAt(0);
                        }
                    }

                    if (workbook == null || sheet == null) {
                        workbook = new XSSFWorkbook();
                        sheet = workbook.createSheet("Attendance");
                    }

                    int lastRow = sheet.getLastRowNum();
                    java.util.Set<String> existingEntries = new java.util.HashSet<>();
                    for (int i = 1; i <= lastRow; i++) {
                        Row r = sheet.getRow(i);
                        if (r != null) {
                            org.apache.poi.ss.usermodel.Cell idCell = r.getCell(0);
                            org.apache.poi.ss.usermodel.Cell dateCell = r.getCell(2);
                            if (idCell != null && dateCell != null) {
                                String eId = idCell.getStringCellValue();
                                String eDate = dateCell.getStringCellValue();
                                existingEntries.add(eId + "_" + eDate);
                            }
                        }
                    }

                    int rowNum = lastRow == 0 && sheet.getRow(0) == null ? 0 : lastRow + 1;
                    if (rowNum == 0) {
                        Row headerRow = sheet.createRow(0);
                        headerRow.createCell(0).setCellValue("Student ID");
                        headerRow.createCell(1).setCellValue("Name");
                        headerRow.createCell(2).setCellValue("Date");
                        headerRow.createCell(3).setCellValue("Status");
                        rowNum = 1;
                    }

                    java.util.HashMap<String, String> recordedStatus = new java.util.HashMap<>();

                    for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                        for (DataSnapshot recordSnapshot : studentSnapshot.getChildren()) {
                            String studentId = recordSnapshot.child("studentId").getValue(String.class);
                            String name = recordSnapshot.child("studentName").getValue(String.class);
                            String date = recordSnapshot.child("date").getValue(String.class);
                            String status = recordSnapshot.child("status").getValue(String.class);

                            if (selectedDate.equals(date)) {
                                String key = (studentId != null ? studentId : "-") + "_" + date;
                                recordedStatus.put(key, status);
                            }
                        }
                    }

                    // For the selected date, iterate over every student
                    for (String[] student : allStudents) {
                        String sId = student[0];
                        String sName = student[1];
                        String key = (sId != null ? sId : "-") + "_" + selectedDate;

                        // Skip if this exact row was already parsed from an old Excel file
                        if (existingEntries.contains(key)) continue;

                        String finalStatus = recordedStatus.containsKey(key) ? recordedStatus.get(key) : "Absent";
                        
                        existingEntries.add(key);

                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(sId != null ? sId : "-");
                        row.createCell(1).setCellValue(sName != null ? sName : "-");
                        row.createCell(2).setCellValue(selectedDate);
                        row.createCell(3).setCellValue(finalStatus);
                    }

                    OutputStream outputStream;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (isExisting && existingUri != null) {
                            outputStream = getContentResolver().openOutputStream(existingUri, "rwt");
                        } else {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (uri == null) throw new Exception("Cannot create Excel file uri");
                            outputStream = getContentResolver().openOutputStream(uri);
                        }
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists()) dir.mkdirs();
                        File file = new File(dir, fileName);
                        outputStream = new FileOutputStream(file);
                    }

                    workbook.write(outputStream);
                    outputStream.flush();
                    outputStream.close();
                    Toast.makeText(adminreport.this, "Excel appended safely as " + fileName, Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(adminreport.this, "Failed to save Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }

                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(adminreport.this, "Failed to fetch students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() { requestPermission(PERMISSION_REQUEST_CODE); }

    private void requestPermission(int tag) {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                tag);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                createOrUpdateCsv();
            } else if (requestCode == 200) {
                // If permission granted, still need to prompt for date. Easiest is to ask user to click again.
                Toast.makeText(this, "Permission granted! Please click Export Excel again to select a date.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Permission denied. Cannot save file.", Toast.LENGTH_SHORT).show();
        }
    }

    public static class LeaveRequest {
        private String studentId, studentName, fromDate, toDate, reason, status, requestId;

        public LeaveRequest() {}

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }

        public String getFromDate() { return fromDate; }
        public void setFromDate(String fromDate) { this.fromDate = fromDate; }

        public String getToDate() { return toDate; }
        public void setToDate(String toDate) { this.toDate = toDate; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
    }
}