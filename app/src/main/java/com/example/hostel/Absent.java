package com.example.hostel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

public class Absent extends AppCompatActivity {

    TextView tvDayDate, tvAbsentCount;
    RecyclerView recyclerViewAbsent;

    ArrayList<StudentAttendanceModel> absentList;
    AbsentAdapter adapter;

    DatabaseReference studentsRef, attendanceRef, timeRef;

    long endTimeMillis = 0; // Attendance end time in millis

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_absent);

        tvDayDate = findViewById(R.id.tvDayDate);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        recyclerViewAbsent = findViewById(R.id.recyclerViewAbsent);

        absentList = new ArrayList<>();
        adapter = new AbsentAdapter(absentList);
        recyclerViewAbsent.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAbsent.setAdapter(adapter);

        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        tvDayDate.setText(currentDate);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(currentDate);
        timeRef = FirebaseDatabase.getInstance().getReference("AttendanceTime").child(currentDate);

        // Load end time first
        loadEndTime();
    }

    private void loadEndTime() {
        timeRef.child("endTime").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long ts = snapshot.getValue(Long.class);
                if (ts != null) {
                    endTimeMillis = ts;
                }
                // Once endTime is fetched, load absent students
                loadAbsentStudents();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void loadAbsentStudents() {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentsSnapshot) {
                attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attendanceSnapshot) {
                        absentList.clear();
                        long now = System.currentTimeMillis();

                        for (DataSnapshot studentSnap : studentsSnapshot.getChildren()) {
                            String studentId = studentSnap.getKey();
                            String studentName = studentSnap.child("name").getValue(String.class);

                            boolean isPresent = false;
                            if (attendanceSnapshot.hasChild(studentId)) {
                                // Check if attendance was marked before end time
                                for (DataSnapshot attSnap : attendanceSnapshot.child(studentId).getChildren()) {
                                    Long ts = attSnap.child("timestamp").getValue(Long.class);
                                    if (ts != null && ts <= endTimeMillis) {
                                        isPresent = true;
                                        break;
                                    }
                                }
                            }

                            // If not present before end time → absent
                            if (!isPresent) {
                                absentList.add(new StudentAttendanceModel(studentId, studentName, "--:--"));
                            }
                        }

                        tvAbsentCount.setText(String.valueOf(absentList.size()));
                        if (absentList.isEmpty()) tvAbsentCount.setText("0 (All Present)");

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ---------------- MODEL CLASS ----------------
    public static class StudentAttendanceModel {
        String studentId;
        String studentName;
        String time;

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

    // ---------------- ADAPTER CLASS ----------------
    public static class AbsentAdapter extends RecyclerView.Adapter<AbsentAdapter.ViewHolder> {

        ArrayList<StudentAttendanceModel> list;

        public AbsentAdapter(ArrayList<StudentAttendanceModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_absent_student, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StudentAttendanceModel student = list.get(position);
            holder.tvRegNo.setText(student.getStudentId());
            holder.tvName.setText(student.getStudentName());
            holder.tvTime.setText(student.getTime());

            // Alternate row colors
            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(0xFFFFE6E6); // Light red
            } else {
                holder.itemView.setBackgroundColor(0xFFFFFFFF);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
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
