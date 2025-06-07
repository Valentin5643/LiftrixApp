#!/bin/bash

# Firebase deployment script for Liftrix
# This script deploys Firebase Functions, Firestore rules, and Storage rules

set -e

echo "🚀 Starting Firebase deployment for Liftrix..."

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI is not installed. Please install it first:"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Check if user is logged in
if ! firebase projects:list &> /dev/null; then
    echo "❌ Please login to Firebase first:"
    echo "firebase login"
    exit 1
fi

# Deploy Firestore rules
echo "📋 Deploying Firestore rules..."
firebase deploy --only firestore:rules

# Deploy Storage rules
echo "📁 Deploying Storage rules..."
firebase deploy --only storage

# Deploy Functions (if functions directory exists)
if [ -d "functions" ]; then
    echo "⚡ Deploying Firebase Functions..."
    firebase deploy --only functions
else
    echo "⚠️  Functions directory not found, skipping functions deployment"
fi

# Deploy Firestore indexes
echo "🗂️  Deploying Firestore indexes..."
firebase deploy --only firestore:indexes

echo "✅ Firebase deployment completed successfully!"
echo "🌐 Check your Firebase Console for deployment status"

# TODO: Add Firebase Analytics deployment steps
# Placeholder for Firebase deploy script 