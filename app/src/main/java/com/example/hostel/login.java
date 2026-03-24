package com.example.hostel;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class login extends AppCompatActivity {

    EditText etUserId, etPassword;
    RadioGroup roleGroup;
    Button btnLogin;

    private final String WARDEN_ID = "warden001";
    private final String WARDEN_PASS = "warden@123";

    private DatabaseReference dbRef, studentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUserId = findViewById(R.id.etUserId);
        etPassword = findViewById(R.id.etPassword);
        roleGroup = findViewById(R.id.roleGroup);
        btnLogin = findViewById(R.id.btnLogin);

        dbRef = FirebaseDatabase.getInstance("https://hostel-attendance-ce06a-default-rtdb.firebaseio.com/").getReference("login_logs");
        studentRef = FirebaseDatabase.getInstance("https://hostel-attendance-ce06a-default-rtdb.firebaseio.com/").getReference("Students");

        btnLogin.setOnClickListener(v -> {
            String userId = etUserId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            int selectedRoleId = roleGroup.getCheckedRadioButtonId();

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(login.this, "Enter all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false); // Disable during check
            Toast.makeText(this, "Checking credentials...", Toast.LENGTH_SHORT).show();

            if (selectedRoleId == R.id.rbWarden) {
                handleWardenLogin(userId, password);
            } else if (selectedRoleId == R.id.rbStudent) {
                handleStudentLogin(userId, password);
            } else {
                Toast.makeText(login.this, "Please select a role", Toast.LENGTH_SHORT).show();
                btnLogin.setEnabled(true);
            }
        });
    }

    private void handleWardenLogin(String userId, String password) {
        if (userId.equals(WARDEN_ID) && password.equals(WARDEN_PASS)) {
            Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
            saveLoginToFirebase(userId, "Warden");
            startActivity(new Intent(this, AdminHome.class));
            finish();
        } else {
            Toast.makeText(this, "Invalid Password", Toast.LENGTH_SHORT).show();
            btnLogin.setEnabled(true);
        }
    }

    private void handleStudentLogin(String studentId, String password) {
        studentRef.child(studentId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    String dbPassword = snapshot.child("password").getValue(String.class);

                    if (dbPassword != null && dbPassword.equals(password)) {
                        Toast.makeText(login.this, "Login Successful", Toast.LENGTH_SHORT).show();
                        saveLoginToFirebase(studentId, "Student");

                        Intent intent = new Intent(login.this, Student_Attendance.class);
                        intent.putExtra("studentId", studentId);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(login.this, "Invalid Password", Toast.LENGTH_SHORT).show();
                        btnLogin.setEnabled(true);
                    }
                } else {
                    Toast.makeText(login.this, "Student not registered", Toast.LENGTH_SHORT).show();
                    btnLogin.setEnabled(true);
                }
            } else {
                Toast.makeText(login.this, "Database Error", Toast.LENGTH_SHORT).show();
                btnLogin.setEnabled(true);
            }
        });
    }

    private void saveLoginToFirebase(String userId, String role) {
        String loginId = dbRef.push().getKey();
        if (loginId != null) {
            HashMap<String, Object> loginData = new HashMap<>();
            loginData.put("userId", userId);
            loginData.put("role", role);
            loginData.put("timestamp", System.currentTimeMillis());

            dbRef.child(loginId).setValue(loginData)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(login.this, "Login saved", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(login.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
}