package com.example.hostel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LeaveRequestAdapter extends RecyclerView.Adapter<LeaveRequestAdapter.ViewHolder> {

    private final Context context;
    private final List<adminreport.LeaveRequest> leaveList;
    private final LeaveRequestListener listener;

    public interface LeaveRequestListener {
        void onActionClick(adminreport.LeaveRequest request, String status);
    }

    public LeaveRequestAdapter(Context context, List<adminreport.LeaveRequest> leaveList, LeaveRequestListener listener) {
        this.context = context;
        this.leaveList = leaveList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_leave_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        adminreport.LeaveRequest req = leaveList.get(position);

        holder.tvStudentName.setText(req.getStudentName());
        holder.tvDates.setText(req.getFromDate() + " → " + req.getToDate());
        holder.tvReason.setText("Reason: " + req.getReason());
        holder.tvStatus.setText("Status: " + req.getStatus());

        holder.btnApprove.setOnClickListener(v -> listener.onActionClick(req, "Approved"));
        holder.btnReject.setOnClickListener(v -> listener.onActionClick(req, "Rejected"));
    }

    @Override
    public int getItemCount() {
        return leaveList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvDates, tvReason, tvStatus;
        Button btnApprove, btnReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvDates = itemView.findViewById(R.id.tvDates);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}