#!/bin/bash

# Firebase deployment script for Liftrix
# This script deploys Firebase Functions, Firestore rules, Storage rules, and Analytics configuration

set -e

echo "🚀 Starting Firebase deployment for Liftrix..."

# Color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}$1${NC}"
}

print_success() {
    echo -e "${GREEN}$1${NC}"
}

print_warning() {
    echo -e "${YELLOW}$1${NC}"
}

print_error() {
    echo -e "${RED}$1${NC}"
}

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    print_error "❌ Firebase CLI is not installed. Please install it first:"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Check if user is logged in
if ! firebase projects:list &> /dev/null; then
    print_error "❌ Please login to Firebase first:"
    echo "firebase login"
    exit 1
fi

# Get current project info
print_status "📋 Current Firebase project:"
firebase use --current

# Validate Firestore rules before deployment
print_status "🔍 Validating Firestore rules..."
if [ -f "firestore.rules" ]; then
    if firebase firestore:rules:validate firestore.rules; then
        print_success "✅ Firestore rules validation passed"
    else
        print_error "❌ Firestore rules validation failed. Please fix the rules before deployment."
        exit 1
    fi
else
    print_warning "⚠️  No firestore.rules file found"
fi

# Validate Storage rules if they exist
if [ -f "storage.rules" ]; then
    print_status "🔍 Validating Storage rules..."
    # Note: Firebase CLI doesn't have built-in storage rules validation
    print_warning "⚠️  Storage rules validation not available in CLI - rules will be validated during deployment"
fi

# Deploy Firestore rules
print_status "📋 Deploying Firestore rules..."
if firebase deploy --only firestore:rules; then
    print_success "✅ Firestore rules deployed successfully"
else
    print_error "❌ Failed to deploy Firestore rules"
    exit 1
fi

# Deploy Storage rules
print_status "📁 Deploying Storage rules..."
if firebase deploy --only storage; then
    print_success "✅ Storage rules deployed successfully"
else
    print_error "❌ Failed to deploy Storage rules"
    exit 1
fi

# Deploy Firestore indexes
print_status "🗂️  Deploying Firestore indexes..."
if firebase deploy --only firestore:indexes; then
    print_success "✅ Firestore indexes deployed successfully"
else
    print_error "❌ Failed to deploy Firestore indexes"
    exit 1
fi

# Deploy Functions (if functions directory exists)
if [ -d "functions" ]; then
    print_status "⚡ Deploying Firebase Functions..."
    if firebase deploy --only functions; then
        print_success "✅ Firebase Functions deployed successfully"
    else
        print_error "❌ Failed to deploy Firebase Functions"
        exit 1
    fi
else
    print_warning "⚠️  Functions directory not found, skipping functions deployment"
fi

# Analytics and Crashlytics are configured automatically via SDK
print_status "📊 Analytics and Crashlytics configuration:"
print_success "✅ Firebase Analytics: Enabled via Android SDK"
print_success "✅ Firebase Crashlytics: Enabled via Android SDK"
print_warning "ℹ️  Analytics events and crash reports will appear in Firebase Console after app usage"

# Display deployment summary
print_status "📋 Deployment Summary:"
print_success "✅ Firestore rules deployed"
print_success "✅ Storage rules deployed"
print_success "✅ Firestore indexes deployed"
if [ -d "functions" ]; then
    print_success "✅ Firebase Functions deployed"
fi
print_success "✅ Analytics and Crashlytics configured"

print_success "🎉 Firebase deployment completed successfully!"
print_status "🌐 Check your Firebase Console for deployment status"
print_status "📊 Analytics: https://console.firebase.google.com/project/$(firebase use --current)/analytics"
print_status "🐛 Crashlytics: https://console.firebase.google.com/project/$(firebase use --current)/crashlytics" 