package com.mytrackr.receipts.data.models;


public class User {
    private String uid;
    private String fullName;
    private String email;
    private String profilePicture;

    // Constructor
    public User() { }
    public User(String uid, String fullName, String email, String profilePicture) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.profilePicture = profilePicture;
    }

    // Getters
    public String getUid() {
        return uid;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    // Setters
    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setFullName(String name) {
        this.fullName = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }
}

