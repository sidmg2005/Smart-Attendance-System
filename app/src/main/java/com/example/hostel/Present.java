package com.example.hostel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Present extends AppCompatActivity {

    TextView tvDayDate, tvPresentCount;
    RecyclerView recyclerViewPresent;

    ArrayList<StudentAttendanceModel> studentList;
    PresentAdapter adapter;

    DatabaseReference attendanceRef, studentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_present);

        tvDayDate = findViewById(R.id.tvDayDate);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        recyclerViewPresent = findViewById(R.id.recyclerViewPresent);

        studentList = new ArrayList<>();
        adapter = new PresentAdapter(studentList);
        recyclerViewPresent.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPresent.setAdapter(adapter);

        // Current date
        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        tvDayDate.setText(currentDate);

        // Firebase references
        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance");
        studentRef = FirebaseDatabase.getInstance().getReference("Students");

        loadAttendance(currentDate);
    }

    private void loadAttendance(String date) {
        attendanceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                int count = 0;

                for (DataSnapshot studentSnap : snapshot.getChildren()) {
                    String studentId = studentSnap.getKey();
                    boolean isPresentToday = false;

                    for (DataSnapshot attSnap : studentSnap.getChildren()) {
                        String attDate = attSnap.child("date").getValue(String.class);
                        String status = attSnap.child("status").getValue(String.class);

                        if (date.equals(attDate) && "Present".equals(status)) {
                            isPresentToday = true;
                            Long ts = attSnap.child("timestamp").getValue(Long.class);
                            String time = ts != null ? new SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    .format(new Date(ts)) : "--:--";

                            fetchStudentName(studentId, studentName -> {
                                studentList.add(new StudentAttendanceModel(studentId, studentName, time));
                                adapter.notifyDataSetChanged();
                                tvPresentCount.setText(String.valueOf(studentList.size()));
                            });
                        }
                    }

                    if (!isPresentToday) {
                        // Optional: add absent logic here
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Present.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchStudentName(String studentId, OnNameFetchedListener listener) {
        studentRef.child(studentId).child("name").get().addOnCompleteListener(task -> {
            String name = task.isSuccessful() && task.getResult().exists()
                    ? task.getResult().getValue(String.class)
                    : "Unknown";
            listener.onFetched(name);
        });
    }

    interface OnNameFetchedListener {
        void onFetched(String name);
    }

    // -------------------- MODEL CLASS --------------------
    public static class StudentAttendanceModel {
        private String studentId;
        private String studentName;
        private String time;

        public StudentAttendanceModel() { }

        public StudentAttendanceModel(String studentId, String studentName, String time) {
            this.studentId = studentId;
            this.studentName = studentName;
            this.time = time;
        }

        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getTime() { return time; }
    }

    // -------------------- ADAPTER CLASS --------------------
    public static class PresentAdapter extends RecyclerView.Adapter<PresentAdapter.ViewHolder> {

        private ArrayList<StudentAttendanceModel> studentList;

        public PresentAdapter(ArrayList<StudentAttendanceModel> studentList) {
            this.studentList = studentList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_present_student, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StudentAttendanceModel student = studentList.get(position);
            holder.tvRegNo.setText(student.getStudentId());
            holder.tvName.setText(student.getStudentName());
            holder.tvTime.setText(student.getTime());
        }

        @Override
        public int getItemCount() {
            return studentList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRegNo, tvName, tvTime;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRegNo = itemView.findViewById(R.id.tvRegNo);
                tvName = itemView.findViewById(R.id.tvName);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }
}