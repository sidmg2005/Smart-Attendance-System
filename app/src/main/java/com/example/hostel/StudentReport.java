package com.example.hostel;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class StudentReport extends AppCompatActivity {

    TextView tvStudentId, tvWeeklyPresent, tvWeeklyAbsent, tvMonthlyPresent, tvMonthlyAbsent;
    LinearLayout dailyContainer;
    Button btnSignOut, btnHome, btnReport, btnBack;

    String studentId;
    String studentName = "Unknown";
    DatabaseReference attendanceRef;
    DatabaseReference studentRef;

    private List<AttendanceRecord> allRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_report);

        tvStudentId = findViewById(R.id.tvStudentId);
        tvWeeklyPresent = findViewById(R.id.tvWeeklyPresent);
        tvWeeklyAbsent = findViewById(R.id.tvWeeklyAbsent);
        tvMonthlyPresent = findViewById(R.id.tvMonthlyPresent);
        tvMonthlyAbsent = findViewById(R.id.tvMonthlyAbsent);
        dailyContainer = findViewById(R.id.dailyContainer);

        btnSignOut = findViewById(R.id.btnSignOut);
        btnHome = findViewById(R.id.btnHome);
        btnReport = findViewById(R.id.btnReport);
        btnBack = findViewById(R.id.btnBack);

        studentId = getIntent().getStringExtra("studentId");
        if (studentId == null || studentId.isEmpty()) {
            Toast.makeText(this, "Student ID not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvStudentId.setText("Student ID: " + studentId);
        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(studentId);
        studentRef = FirebaseDatabase.getInstance().getReference("Students").child(studentId);

        allRecords = new ArrayList<>();

        // Fetch student name first, then load attendance
        studentRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    studentName = snapshot.getValue(String.class);
                }
                loadAttendance();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadAttendance();
            }
        });

        btnReport.setOnClickListener(v -> generateExcelReport());
        btnBack.setOnClickListener(v -> finish());
        
        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(StudentReport.this, login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(StudentReport.this, login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadAttendance() {
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dailyContainer.removeAllViews();
                allRecords.clear();

                int weeklyPresent = 0, weeklyAbsent = 0;
                int monthlyPresent = 0, monthlyAbsent = 0;
                int dailyAbsent = 0;

                Calendar today = Calendar.getInstance();
                int currentWeek = today.get(Calendar.WEEK_OF_YEAR);
                int currentMonth = today.get(Calendar.MONTH);
                int currentYear = today.get(Calendar.YEAR);

                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                String todayDate = sdf.format(today.getTime());

                for (DataSnapshot record : snapshot.getChildren()) {
                    String dateStr = record.child("date").getValue(String.class);
                    String status = record.child("status").getValue(String.class);
                    if (dateStr == null) continue;
                    if (status == null) status = "Absent";

                    TextView tv = new TextView(StudentReport.this);
                    tv.setText("📅 " + dateStr + " → " + status);
                    dailyContainer.addView(tv);

                    AttendanceRecord rec = new AttendanceRecord();
                    rec.dateStr = dateStr;
                    rec.status = status;
                    rec.isToday = false;
                    rec.isThisWeek = false;
                    rec.isThisMonth = false;

                    try {
                        Calendar recordDate = Calendar.getInstance();
                        recordDate.setTime(sdf.parse(dateStr));

                        int recordWeek = recordDate.get(Calendar.WEEK_OF_YEAR);
                        int recordMonth = recordDate.get(Calendar.MONTH);
                        int recordYear = recordDate.get(Calendar.YEAR);

                        if (dateStr.equals(todayDate)) {
                            rec.isToday = true;
                            if (status.equalsIgnoreCase("Absent")) dailyAbsent++;
                        }

                        if (recordYear == currentYear && recordWeek == currentWeek) {
                            rec.isThisWeek = true;
                            if (status.equalsIgnoreCase("Present")) weeklyPresent++;
                            else weeklyAbsent++;
                        }

                        if (recordYear == currentYear && recordMonth == currentMonth) {
                            rec.isThisMonth = true;
                            if (status.equalsIgnoreCase("Present")) monthlyPresent++;
                            else monthlyAbsent++;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    allRecords.add(rec);
                }

                tvWeeklyPresent.setText("Present: " + weeklyPresent);
                tvWeeklyAbsent.setText("Absent: " + weeklyAbsent);
                tvMonthlyPresent.setText("Present: " + monthlyPresent);
                tvMonthlyAbsent.setText("Absent: " + monthlyAbsent);

                TextView tvDailyAbsentView = new TextView(StudentReport.this);
                tvDailyAbsentView.setText("Today's Absent: " + dailyAbsent);
                dailyContainer.addView(tvDailyAbsentView);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentReport.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateExcelReport() {
        if (allRecords.isEmpty()) {
            Toast.makeText(this, "No attendance data to export!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Workbook workbook = new XSSFWorkbook();

            // Create 3 Sheets
            Sheet sheetDaily = workbook.createSheet("Daily");
            Sheet sheetWeekly = workbook.createSheet("Weekly");
            Sheet sheetMonthly = workbook.createSheet("Monthly");

            setupSheet(sheetDaily, "Daily Attendance", true, false, false);
            setupSheet(sheetWeekly, "Weekly Attendance", false, true, false);
            setupSheet(sheetMonthly, "Monthly Attendance", false, false, true);

            String fileName = "Attendance_Report_" + studentId + ".xlsx";
            Uri fileUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                fileUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (fileUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(fileUri);
                    workbook.write(os);
                    os.flush();
                    os.close();
                    workbook.close();

                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(fileUri, values, null, null);

                    Toast.makeText(this, "Excel saved in Downloads folder", Toast.LENGTH_LONG).show();
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File excelFile = new File(downloadsDir, fileName);
                OutputStream os = new FileOutputStream(excelFile);
                workbook.write(os);
                os.flush();
                os.close();
                workbook.close();

                Toast.makeText(this, "Excel saved: " + excelFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Excel Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupSheet(Sheet sheet, String title, boolean filterDaily, boolean filterWeekly, boolean filterMonthly) {
        // Headers
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Student ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Date");
        header.createCell(3).setCellValue("Status");

        int rowIndex = 1;
        for (AttendanceRecord rec : allRecords) {
            boolean include = false;
            if (filterDaily && rec.isToday) include = true;
            if (filterWeekly && rec.isThisWeek) include = true;
            if (filterMonthly && rec.isThisMonth) include = true;

            // If it's a general export covering everything without filters, or if it matches
            if (include) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(studentId);
                row.createCell(1).setCellValue(studentName);
                row.createCell(2).setCellValue(rec.dateStr);
                row.createCell(3).setCellValue(rec.status);
            }
        }
    }

    private static class AttendanceRecord {
        String dateStr;
        String status;
        boolean isToday;
        boolean isThisWeek;
        boolean isThisMonth;
    }
}