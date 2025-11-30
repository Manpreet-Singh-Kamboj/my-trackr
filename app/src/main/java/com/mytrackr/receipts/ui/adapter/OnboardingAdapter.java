package com.mytrackr.receipts.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mytrackr.receipts.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final OnboardingItem[] onboardingItems = {
            new OnboardingItem(
                    R.drawable.ic_onboarding_welcome,
                    "Welcome to MyTrackr",
                    "Your personal receipt management companion that makes tracking expenses simple and efficient."
            ),
            new OnboardingItem(
                    R.drawable.ic_onboarding_scan,
                    "Scan & Store Receipts",
                    "Easily capture and store your receipts with our smart scanning technology. Never lose a receipt again!"
            ),
            new OnboardingItem(
                    R.drawable.ic_onboarding_budget,
                    "Track Your Budget",
                    "Set monthly budgets and get insights into your spending patterns to stay on top of your finances."
            ),
            new OnboardingItem(
                    R.drawable.ic_onboarding_insights,
                    "Get Insights",
                    "View detailed analytics and reports about your spending habits to make better financial decisions."
            )
    };

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingItem item = onboardingItems[position];
        holder.imageView.setImageResource(item.getImageResource());
        holder.titleTextView.setText(item.getTitle());
        holder.descriptionTextView.setText(item.getDescription());
    }

    @Override
    public int getItemCount() {
        return onboardingItems.length;
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleTextView;
        TextView descriptionTextView;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_onboarding);
            titleTextView = itemView.findViewById(R.id.tv_title);
            descriptionTextView = itemView.findViewById(R.id.tv_description);
        }
    }

    private static class OnboardingItem {
        private final int imageResource;
        private final String title;
        private final String description;

        OnboardingItem(int imageResource, String title, String description) {
            this.imageResource = imageResource;
            this.title = title;
            this.description = description;
        }

        public int getImageResource() {
            return imageResource;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }
}