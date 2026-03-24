package com.example.hostel;

public class NotificationModel {

    private String title, message, studentId;
    private long timestamp;

    public NotificationModel() {}

    // 🔹 Getters with null safety
    public String getTitle() {
        return title != null ? title : "";
    }

    public String getMessage() {
        return message != null ? message : "";
    }

    public String getStudentId() {
        return studentId != null ? studentId : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    // 🔹 Setters (unchanged)
    public void setTitle(String title) {
        this.title = title;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}