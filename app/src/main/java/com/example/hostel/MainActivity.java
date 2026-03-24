package com.example.hostel;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, login.class));
            });
        }

        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, StudentRegister.class));
            });
        }
    }
}
