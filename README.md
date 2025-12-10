# 📲 MyTrackr (MVP)

**MyTrackr** is a mobile-first Android app that helps users effortlessly scan, store, and track receipts while also managing monthly budgets. The app leverages **OCR (ML Kit)** and **AI-powered formatting** to unify receipt data into a clean, consistent format for easy searching, exporting, and financial insights.

--- 

## 🎥 Demo

https://github.com/user-attachments/assets/1ee60842-1a6b-44d9-9420-5fd7672a66df

---

## 🚀 Features

### 🛬 Onboarding

- Landing / onboarding pages explaining:
  - What the app does
  - Benefits of managing receipts digitally
  - Budget tracking overview

### 🔐 Authentication & Access

- User Registration & Login
- **Google SSO integration via Firebase**
- **IAM (Identity & Access Management):**
  - **User** – can scan, store, search, and track receipts
  - **Admin** – extended privileges (e.g., managing entitlements, system usage reports)

### 📸 Receipt Scanning

- Capture receipt image via **Camera** or select from **Gallery**
- Preview before saving
- **OCR with ML Kit Text Recognition** to extract:
  - Store name
  - Items purchased
  - Prices & taxes

### 💾 Receipt Storage

- Store scanned receipts with:
  - Original image
  - Extracted + AI-formatted details
- Unified bill format regardless of layout differences
- **Basic search**:
  - By store name
  - By date

### 👤 User Profile

- **My Receipts / Bills** – personal dashboard
- Export receipts as **PDF** (for tax filing or record keeping)

### 💰 Budget Tracker

- Set a **monthly budget**
- Track total spend (auto-summed from uploaded receipts)
- **Smart suggestions**:
  - AI-powered tips to lower expenses
  - Alerts when nearing or exceeding budget

---

## 🏗️ Tech Stack

- **Frontend (Mobile):** Android (Java)
- **Authentication:** Firebase Auth (Google SSO)
- **OCR:** Google ML Kit – Text Recognition
- **Storage:** Firebase Firestore / Realtime Database (for structured receipt data) + Firebase Storage (for images)
- **AI Formatting:** LLM/AI pipeline (to standardize receipt structure)
- **Export:** PDF generator (Android native libraries)
