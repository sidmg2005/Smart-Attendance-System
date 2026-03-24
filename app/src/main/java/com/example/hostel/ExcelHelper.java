package com.example.hostel;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExcelHelper {

    /**
     * Data Transfer Object for carrying Firebase attendance payloads to POI Excel natively.
     */
    public static class AttendanceRecord {
        public String studentId;
        public String name;
        public String date;
        public String status;

        public AttendanceRecord(String studentId, String name, String date, String status) {
            this.studentId = studentId;
            this.name = name;
            this.date = date;
            this.status = status;
        }
    }

    /**
     * Completely Storage Access Framework (SAF) aligned bulk processor.
     * Takes an Admin-selected `Uri` and syncs bulk attendance into it safely securely.
     */
    public static void syncAttendanceToUri(Context context, Uri uri, List<AttendanceRecord> records, Map<String, String> studentMap) {
        new Thread(() -> {
            Workbook workbook = null;
            InputStream is = null;
            OutputStream os = null;

            try {
                // Rule 2 & 7: Open existing file seamlessly via URI InputStream
                is = context.getContentResolver().openInputStream(uri);
                if (is == null) {
                    Log.e("ExcelHelper", "Failed to resolve InputStream from SAF URI.");
                    return;
                }

                try {
                    workbook = new XSSFWorkbook(is);
                } catch (Exception e) {
                    Log.e("ExcelHelper", "Corrupted or completely empty selected workbook! Aborting to prevent destroying file. Error: " + e.getMessage());
                    if (is != null) is.close();
                    if (workbook != null) workbook.close();
                    return;
                }

                is.close();
                is = null;

                // --- 1. POPULATE ABSENT BY DEFAULT LOGIC ---
                Set<String> allDates = new HashSet<>();
                for (AttendanceRecord r : records) {
                    allDates.add(r.date);
                }

                for (String date : allDates) {
                    for (String studentId : studentMap.keySet()) {
                        boolean found = false;
                        for (AttendanceRecord r : records) {
                            if (r.studentId.equals(studentId) && r.date.equals(date)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            records.add(new AttendanceRecord(studentId, studentMap.get(studentId), date, "Absent"));
                        }
                    }
                }

                // --- 2. DAILY ATTENDANCE APPENDING ---
                Sheet dailySheet = workbook.getSheet("Daily_Attendance");
                if (dailySheet == null) {
                    // Try legacy sheet name before creating new to prevent discarding older implementations
                    dailySheet = workbook.getSheet("Attendance");
                    if (dailySheet != null) {
                        workbook.setSheetName(workbook.getSheetIndex("Attendance"), "Daily_Attendance");
                    } else {
                        dailySheet = workbook.createSheet("Daily_Attendance");
                        createDailyHeader(dailySheet);
                    }
                }

                int lastRowNum = dailySheet.getLastRowNum();

                // Append deduplication check
                for (AttendanceRecord record : records) {
                    boolean updated = false;

                    for (int i = 1; i <= lastRowNum; i++) {
                        Row row = dailySheet.getRow(i);
                        if (row != null) {
                            Cell idCell = row.getCell(0);
                            Cell dateCell = row.getCell(2);

                            if (idCell != null && dateCell != null) {
                                String existingId = getCellString(idCell);
                                String existingDate = getCellString(dateCell);

                                if (record.studentId.equals(existingId) && record.date.equals(existingDate)) {
                                    Cell statusCell = row.getCell(3);
                                    if (statusCell == null) statusCell = row.createCell(3);
                                    statusCell.setCellValue(record.status);
                                    updated = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!updated) {
                        lastRowNum++;
                        Row newRow = dailySheet.createRow(lastRowNum);
                        newRow.createCell(0).setCellValue(record.studentId);
                        newRow.createCell(1).setCellValue(record.name);
                        newRow.createCell(2).setCellValue(record.date);
                        newRow.createCell(3).setCellValue(record.status);
                    }
                }

                // --- 3. COMPUTE SUMMARY DATA EXPLICITLY FROM DAILY SHEET (To capture offline edits explicitly) ---
                Map<String, WeekStats> weeklyStats = new HashMap<>();
                Map<String, MonthStats> monthlyStats = new HashMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                Calendar cal = Calendar.getInstance();

                int totalDailyRows = dailySheet.getLastRowNum();
                for (int i = 1; i <= totalDailyRows; i++) {
                    Row row = dailySheet.getRow(i);
                    if (row == null) continue;

                    String sId = getCellString(row.getCell(0));
                    String sName = getCellString(row.getCell(1));
                    String dateStr = getCellString(row.getCell(2));
                    String status = getCellString(row.getCell(3));

                    if (sId.isEmpty() || dateStr.isEmpty()) continue;

                    try {
                        Date d = sdf.parse(dateStr);
                        if (d != null) {
                            cal.setTime(d);
                            int week = cal.get(Calendar.WEEK_OF_YEAR);
                            int month = cal.get(Calendar.MONTH) + 1; // 1-12
                            int year = cal.get(Calendar.YEAR);

                            String weekKey = sId + "_" + year + "_W" + week;
                            String monthKey = sId + "_" + year + "-" + String.format(Locale.getDefault(), "%02d", month);

                            // Weekly Check
                            if (!weeklyStats.containsKey(weekKey)) {
                                weeklyStats.put(weekKey, new WeekStats(sId, sName, "Week " + week + ", " + year));
                            }
                            WeekStats ws = weeklyStats.get(weekKey);
                            if (status.equalsIgnoreCase("Present")) ws.present++;
                            else ws.absent++;

                            // Monthly Check
                            if (!monthlyStats.containsKey(monthKey)) {
                                monthlyStats.put(monthKey, new MonthStats(sId, sName, month + "/" + year));
                            }
                            MonthStats ms = monthlyStats.get(monthKey);
                            if (status.equalsIgnoreCase("Present")) ms.present++;
                            else ms.absent++;
                        }
                    } catch (Exception ignored) {}
                }

                // --- 4. WEEKLY SUMMARY REBUILDING ---
                int wIndex = workbook.getSheetIndex("Weekly_Summary");
                if (wIndex != -1) workbook.removeSheetAt(wIndex);
                Sheet weeklySheet = workbook.createSheet("Weekly_Summary");
                createWeeklyHeader(weeklySheet);

                int wRow = 1;
                for (WeekStats ws : weeklyStats.values()) {
                    Row row = weeklySheet.createRow(wRow++);
                    row.createCell(0).setCellValue(ws.sId);
                    row.createCell(1).setCellValue(ws.sName);
                    row.createCell(2).setCellValue(ws.weekLabel);
                    row.createCell(3).setCellValue(ws.present);
                    row.createCell(4).setCellValue(ws.absent);
                }

                // --- 5. MONTHLY SUMMARY REBUILDING ---
                int mIndex = workbook.getSheetIndex("Monthly_Summary");
                if (mIndex != -1) workbook.removeSheetAt(mIndex);
                Sheet monthlySheet = workbook.createSheet("Monthly_Summary");
                createMonthlyHeader(monthlySheet);

                int mRow = 1;
                for (MonthStats ms : monthlyStats.values()) {
                    Row row = monthlySheet.createRow(mRow++);
                    row.createCell(0).setCellValue(ms.sId);
                    row.createCell(1).setCellValue(ms.sName);
                    row.createCell(2).setCellValue(ms.monthLabel);
                    row.createCell(3).setCellValue(ms.present);
                    row.createCell(4).setCellValue(ms.absent);
                    
                    int total = ms.present + ms.absent;
                    double pct = total == 0 ? 0.0 : ((double) ms.present / total) * 100.0;
                    row.createCell(5).setCellValue(String.format(Locale.getDefault(), "%.2f%%", pct));
                }

                // --- 6. SAF OUTPUT EXPORT --
                os = context.getContentResolver().openOutputStream(uri, "wt");
                if (os != null) {
                    workbook.write(os);
                    os.flush();
                }

                Log.d("ExcelHelper", "Appended 3-Tier Summaries to URI successfully.");

            } catch (Exception e) {
                Log.e("ExcelHelper", "Unexpected Error generating Excel file: " + e.getMessage(), e);
            } finally {
                try {
                    if (is != null) is.close();
                    if (os != null) os.close();
                    if (workbook != null) workbook.close();
                } catch (Exception e) {
                    Log.e("ExcelHelper", "Error closing streams: " + e.getMessage());
                }
            }
        }).start();
    }

    private static class WeekStats {
        String sId, sName, weekLabel;
        int present = 0, absent = 0;
        WeekStats(String id, String name, String label) { this.sId = id; this.sName = name; this.weekLabel = label; }
    }

    private static class MonthStats {
        String sId, sName, monthLabel;
        int present = 0, absent = 0;
        MonthStats(String id, String name, String label) { this.sId = id; this.sName = name; this.monthLabel = label; }
    }

    private static void createDailyHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Student ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Date");
        header.createCell(3).setCellValue("Status");
    }

    private static void createWeeklyHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Student ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Week");
        header.createCell(3).setCellValue("Total Present");
        header.createCell(4).setCellValue("Total Absent");
    }

    private static void createMonthlyHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Student ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Month");
        header.createCell(3).setCellValue("Total Present");
        header.createCell(4).setCellValue("Total Absent");
        header.createCell(5).setCellValue("Attendance %");
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            try {
                double d = cell.getNumericCellValue();
                if (d == (long) d) {
                    return String.format(java.util.Locale.getDefault(), "%d", (long) d);
                }
                return String.valueOf(d);
            } catch (Exception ex) {
                return "";
            }
        }
    }
}
