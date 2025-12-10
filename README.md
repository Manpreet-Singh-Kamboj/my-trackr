# MyTrackr – Receipt & Budget Tracker (MVP)

[![Android](https://img.shields.io/badge/Platform-Android-green)](https://www.android.com/) 
[![Java](https://img.shields.io/badge/Language-Java-orange)](https://www.java.com/) 
[![Firebase](https://img.shields.io/badge/Backend-Firebase-blue)](https://firebase.google.com/) 
[![ML Kit](https://img.shields.io/badge/OCR-ML%20Kit-red)](https://developers.google.com/ml-kit) 
[![Dark Mode](https://img.shields.io/badge/DarkMode-Supported-purple)](https://developer.android.com/) 
[![Multilingual](https://img.shields.io/badge/Multilingual-English%2FHindi%2FFrench%2FChinese-lightgrey)](https://developer.android.com/)

**MyTrackr** is a mobile-first Android app that allows users to scan, store, and manage receipts while tracking monthly expenses. It supports reminders, budget notifications, CSV/PDF export, dark mode, and multilingual support for a global audience.

---

## Screens

1. **Home** – Quick access to recent receipts and key actions  
2. **Expense Tracker** – View and manage monthly spending  
3. **Dashboard** – Filter by week, month, year, or custom range  
4. **Profile** – Manage settings, language, and app preferences  

---

## Features

- **OCR-based Receipt Scanning**  
  - Capture receipts via camera or gallery  
  - Extract details using **Google ML Kit Text Recognition**  
  - Process and standardize receipts with **Gemini**  

- **Receipt Management**  
  - Standardized receipt formatting  
  - Filter receipts by date (latest first)  
  - Download receipt images anytime  

- **Budget Tracking**  
  - Monthly budget tracking with auto-summed spending  
  - Budget filter by month  
  - Weekly budget notifications every Monday at **7 AM**  

- **Reminders**  
  - Replacement reminders: automatic notification **6 days** after receipt date  
  - Custom reminder times  

- **Export**  
  - CSV export for current year’s expenses (store, amount, tax, etc.)  

- **Dark Mode Support**  
  - Seamless light/dark theme switching  

- **Multilingual Support**  
  - English, Hindi, French, and Chinese  

---

## Tech Stack

- **Mobile App:** Android (Java)  
- **Authentication & Storage:** Firebase Auth, Firestore, Firebase Storage  
- **OCR:** Google ML Kit Text Recognition  
- **Receipt Processing:** Gemini  
- **Export:** Android PDF Generator, CSV export  
- **Notifications & Reminders:** Android native notification system  

---

## Demo

https://github.com/user-attachments/assets/1ee60842-1a6b-44d9-9420-5fd7672a66df

---

## Future Improvements

- AI-powered expense insights and suggestions  
- Multi-user/family budget tracking  
- Cloud backup & sync  

---

## Getting Started

1. Clone the repository:  
   ```bash
   git clone https://github.com/yourusername/MyTrackr.git
