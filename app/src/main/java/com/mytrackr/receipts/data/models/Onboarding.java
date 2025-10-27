package com.mytrackr.receipts.data.models;

public class Onboarding {
    private final int imageResource;
    private final String title;
    private final String description;

    public Onboarding(int imageResource, String title, String description) {
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