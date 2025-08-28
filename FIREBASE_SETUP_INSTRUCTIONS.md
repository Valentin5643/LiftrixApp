# 🔥 Firebase Setup Instructions - LiftrixApp Cleanup

Follow these exact steps to set up automatic cleanup for orphaned user profiles.

## 📋 What You Need

1. **Firebase CLI installed** on your computer
2. **Admin access** to your Firebase project
3. **5-10 minutes** to complete setup

---

## 🚀 STEP 1: Install Firebase CLI

Open **Command Prompt** or **PowerShell** as Administrator:

```bash
npm install -g firebase-tools
```

If you don't have npm/Node.js installed:
1. Go to https://nodejs.org
2. Download and install the latest LTS version
3. Restart your computer
4. Then run the npm command above

---

## 🔐 STEP 2: Login to Firebase

```bash
firebase login
```

This will open your web browser. Login with the same Google account that has access to your Firebase project.

---

## 📂 STEP 3: Navigate to Your Project

```bash
cd C:\Users\Administrator\LiftrixApp
```

---

## ⚡ STEP 4: Initialize Firebase Functions

```bash
firebase init functions
```

**Answer these questions exactly:**

1. **"Please select an option:"** → Select **"Use an existing project"**

2. **"Select a default Firebase project:"** → Choose your **LiftrixApp project** from the list

3. **"What language would you like to use to write Cloud Functions?"** → Select **JavaScript**

4. **"Do you want to use ESLint to catch probable bugs and enforce style?"** → Type **n** and press Enter

5. **"Do you want to install dependencies with npm now?"** → Type **y** and press Enter

Wait for the installation to complete. This creates a `functions` folder in your project.

---

## 📄 STEP 5: Replace the Functions Code

Now you need to replace the default code with our cleanup code:

### 5A: Replace index.js

1. **Delete** the existing file:
   ```bash
   del functions\index.js
   ```

2. **Copy** our cleanup code:
   ```bash
   copy firebase-functions-index.js functions\index.js
   ```

### 5B: Replace package.json

1. **Delete** the existing file:
   ```bash
   del functions\package.json
   ```

2. **Copy** our package file:
   ```bash
   copy firebase-functions-package.json functions\package.json
   ```

### 5C: Install Dependencies

```bash
cd functions
npm install
cd ..
```

---

## 🚀 STEP 6: Deploy to Firebase

```bash
firebase deploy --only functions
```

**Wait for deployment to complete.** You should see:
```
✔  functions: Finished running predeploy script.
✔  functions[cleanupDeletedUser(us-central1)]: Successful create operation.
✔  functions[bulkCleanupOrphanedData(us-central1)]: Successful create operation.
✔  functions[detectOrphanedData(us-central1)]: Successful create operation.

✔  Deploy complete!
```

---

## 🧹 STEP 7: Clean Up Existing Orphaned Data (One-Time)

### 7A: Get Your Service Account Key

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your LiftrixApp project
3. Click **Settings** (gear icon) → **Project Settings**
4. Click **Service Accounts** tab
5. Click **"Generate new private key"**
6. Save the downloaded file as `service-account-key.json` in your `C:\Users\Administrator\LiftrixApp` folder

### 7B: Install Admin SDK

```bash
npm install firebase-admin
```

### 7C: Run Bulk Cleanup

```bash
node bulk-cleanup-script.js
```

This will:
- Find all orphaned user profiles
- Clean them up automatically
- Show you a summary of what was cleaned

**Example output:**
```
🔍 SEARCHING: Looking for orphaned user profiles...
📊 FOUND: 150 user documents in Firestore
🚨 ORPHANED: User abc123 not found in Firebase Auth - ORPHANED DATA DETECTED
🚨 ORPHANED: User def456 not found in Firebase Auth - ORPHANED DATA DETECTED

🧹 CLEANUP PHASE: Processing 2 orphaned users
[1/2] Processing orphaned user: abc123
✅ CLEANUP SUCCESS: Cleaned up 45 documents/operations for user abc123

🎉 BULK CLEANUP COMPLETED!
📊 TOTAL ORPHANED USERS: 2
✅ SUCCESSFULLY CLEANED: 2
❌ ERRORS: 0
📈 TOTAL OPERATIONS: 89
```

---

## ✅ STEP 8: Verify Setup

### 8A: Check Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/) → Your Project
2. Click **Functions** in the left menu
3. You should see 3 functions:
   - `cleanupDeletedUser` ← Automatic cleanup
   - `bulkCleanupOrphanedData` ← Manual cleanup
   - `detectOrphanedData` ← Daily monitoring

### 8B: Check Your App

1. Build and run your Android app
2. Check the logs - you should see fewer "Profile not found" errors
3. Sync workers should stop retrying infinitely

---

## 📊 Monitoring Your Cleanup

### View Logs
1. Firebase Console → Functions → Logs
2. Look for messages like:
   - `🧹 CLEANUP: Starting cleanup for deleted user`
   - `✅ CLEANUP SUCCESS: Cleaned up X documents`

### Daily Monitoring
- Every day at 2 AM UTC, the system checks for orphaned data
- If more than 5 orphaned profiles are found, it logs an alert
- Check the logs if you want to see daily reports

---

## 🆘 Troubleshooting

### "Command not found" errors
- Make sure you installed Node.js and restarted your computer
- Try opening a new Command Prompt window

### "Permission denied" during deployment
- Make sure you're logged into Firebase with `firebase login`
- Make sure your Google account has admin access to the Firebase project

### "Functions not working"
- Check Firebase Console → Functions → Logs for error messages
- Make sure you copied the code files exactly as instructed

### Still seeing "Profile not found" errors in your app
- The Firebase Functions handle Firestore cleanup
- Your Android app code now handles these errors gracefully
- Run the bulk cleanup script if you haven't already

---

## 🎉 You're Done!

Your LiftrixApp now has:

✅ **Automatic cleanup** - When Firebase Auth users are deleted, their Firestore data is automatically cleaned up

✅ **Bulk cleanup** - Existing orphaned data has been cleaned up

✅ **Smart error handling** - Your app no longer gets stuck in retry loops

✅ **Daily monitoring** - System checks for orphaned data daily and alerts you

✅ **Comprehensive logging** - Easy to monitor and debug cleanup operations

**The sync worker failures should be fixed now!** 🎉