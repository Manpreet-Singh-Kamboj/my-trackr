package com.mytrackr.receipts.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.DetailItem;

import java.util.ArrayList;
import java.util.List;

public class CategoryDetailAdapter extends RecyclerView.Adapter<CategoryDetailAdapter.ViewHolder> {
    private List<DetailItem> items;

    public CategoryDetailAdapter(List<DetailItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public void updateList(List<DetailItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetailItem item = items.get(position);
        holder.tvItemName.setText(item.getItemName());
        holder.tvStoreInfo.setText(item.getStoreName() + " | " + item.getDate());
        holder.tvAmount.setText(String.format("$%.2f", item.getAmount()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemName, tvStoreInfo, tvAmount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvStoreInfo = itemView.findViewById(R.id.tvStoreInfo);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }
}

