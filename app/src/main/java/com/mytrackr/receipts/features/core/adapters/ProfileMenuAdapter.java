package com.mytrackr.receipts.features.core.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.ProfileMenuItem;

import java.util.List;

public class ProfileMenuAdapter extends RecyclerView.Adapter<ProfileMenuAdapter.ProfileMenuViewHolder> {
    private final List<ProfileMenuItem> profileMenuItems;
    public ProfileMenuAdapter(List<ProfileMenuItem> profileMenuItems){
        this.profileMenuItems = profileMenuItems;
    }

    @NonNull
    @Override
    public ProfileMenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_menu_item,parent,false);
        return new ProfileMenuViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileMenuViewHolder holder, int position) {
        ProfileMenuItem profileMenuItem = profileMenuItems.get(position);
        holder.bind(profileMenuItem);
    }

    @Override
    public int getItemCount() {
        return profileMenuItems.size();
    }

    static class ProfileMenuViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        ImageView arrow;
        public ProfileMenuViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.menu_icon);
            title = itemView.findViewById(R.id.menu_title);
            arrow = itemView.findViewById(R.id.menu_arrow);
        }
        public void bind(ProfileMenuItem profileMenuItem){
            icon.setImageResource(profileMenuItem.getIcon());
            title.setText(profileMenuItem.getTitle());
            itemView.setOnClickListener(v -> {
                if (profileMenuItem.getAction() != null) {
                    profileMenuItem.getAction().run();
                }
            });
        }
    }

}
