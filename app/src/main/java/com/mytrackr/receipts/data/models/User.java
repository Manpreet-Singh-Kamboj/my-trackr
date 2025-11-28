package com.mytrackr.receipts.data.models;


public class User {
    private String uid;
    private String fullName;
    private String email;
    private String profilePicture;
    private String aboutMe;
    private String phoneNo;
    private String city;

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

    public String getAboutMe() {
        return aboutMe;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public String getCity() {
        return city;
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

    public void setAboutMe(String name) {
        this.aboutMe = name;
    }

    public void setPhoneNo(String email) {
        this.phoneNo = email;
    }

    public void setCity(String name) {
        this.city = name;
    }

}

