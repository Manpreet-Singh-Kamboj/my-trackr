package com.mytrackr.receipts.data.models;

public class ProfileMenuItem {
        int icon;
        String title;
        Runnable action;

        public ProfileMenuItem(int icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
        public int getIcon(){
            return icon;
        }
        public String getTitle(){
            return title;
        }
        public Runnable getAction(){
            return action;
        }
}
