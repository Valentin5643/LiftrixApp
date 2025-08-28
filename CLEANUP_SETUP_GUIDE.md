# 🧹 LiftrixApp Orphaned Profile Cleanup Setup Guide

This guide will help you set up automatic cleanup for orphaned user profiles that are causing sync worker failures.

## 🚨 What's the Problem?

When Firebase Auth users get deleted, their data remains in Firestore, causing:
- Sync workers to fail with "Profile not found" errors
- Infinite retry loops
- PERMISSION_DENIED errors when trying to sync

## ✅ What We're Fixing

1. **Client-side fixes** (already implemented in your Kotlin code):
   - Detects orphaned profiles
   - Stops infinite retry loops
   - Cleans up local Room database
   - Logs issues for server-side cleanup

2. **Server-side cleanup** (you need to deploy this):
   - Automatically deletes Firestore data when Firebase Auth user is deleted
   - Bulk cleanup script for existing orphaned data

---

## 📋 Setup Steps

### Step 1: Install Firebase CLI

1. Open Command Prompt or PowerShell
2. Install Firebase CLI:
   ```bash
   npm install -g firebase-tools
   ```
3. Login to Firebase:
   ```bash
   firebase login
   ```
4. Navigate to your project directory:
   ```bash
   cd C:\Users\Administrator\LiftrixApp
   ```

### Step 2: Initialize Firebase Functions

1. Initialize Firebase Functions in your project:
   ```bash
   firebase init functions
   ```
   
2. When prompted:
   - **Select your Firebase project** (choose your LiftrixApp project)
   - **Language**: Choose JavaScript (easier for beginners)
   - **ESLint**: Choose No (unless you want code linting)
   - **Install dependencies**: Choose Yes

This creates a `functions` folder in your project.

### Step 3: Replace Functions Code

The initialization created basic files. You need to replace them with our cleanup code.

Go to the next section to get the exact code files.

---

## 🔧 Firebase Functions Code

I'll create all the files you need in the next steps. You just need to copy them to the right locations.

### Step 4: Deploy Functions

After copying all the code files (provided below), deploy to Firebase:

```bash
firebase deploy --only functions
```

### Step 5: Test the Setup

1. The functions will automatically run when you delete a Firebase Auth user
2. Check the Firebase Console > Functions logs to see if cleanup is working
3. Use the bulk cleanup function for existing orphaned data

---

## 📊 Monitoring

After setup, you can:

1. **Check logs**: Firebase Console > Functions > Logs
2. **Monitor cleanup**: Look for "Successfully cleaned up data for user" messages
3. **App logs**: Your Android app will log detected orphaned profiles

---

## 🆘 If You Get Stuck

1. **Firebase CLI issues**: Make sure you're logged into the correct Firebase account
2. **Permission errors**: Ensure your Firebase project has Functions enabled
3. **Deployment fails**: Check that you've copied all files correctly

The next files contain all the code you need to copy - just follow the file paths exactly.