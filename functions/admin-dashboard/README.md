# Liftrix Admin Moderation Dashboard

Web-based admin dashboard for content moderation and user management.

## Features

- 🔒 **Admin-Only Access**: Firebase Auth with admin custom claims verification
- 📊 **Content Reports**: View and filter user-generated content reports
- ⚡ **Real-Time Updates**: Firestore listeners for instant report updates
- 🎯 **Moderation Actions**:
  - Hide inappropriate content
  - Delete violating content
  - Warn users about policy violations
  - Temporarily suspend user accounts
  - Dismiss false reports
- 🔍 **Advanced Filtering**: Filter by content type, reason, status, and date
- 📝 **Audit Trail**: All actions logged with admin ID, timestamp, and notes

## Prerequisites

- Node.js 18+ and npm
- Firebase project with admin users configured
- Admin custom claim set on user accounts (use `functions/setAdminClaim.js`)

## Setup Instructions

### 1. Install Dependencies

```bash
cd functions/admin-dashboard
npm install
```

### 2. Configure Firebase

Copy `.env.example` to `.env` and fill in your Firebase project credentials:

```bash
cp .env.example .env
```

Edit `.env` with your Firebase configuration from the Firebase Console:

```
VITE_FIREBASE_API_KEY=your_api_key
VITE_FIREBASE_AUTH_DOMAIN=your_project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=your_project_id
VITE_FIREBASE_STORAGE_BUCKET=your_project.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=your_sender_id
VITE_FIREBASE_APP_ID=your_app_id
```

### 3. Set Admin Claims

Before you can log in, you need to set the admin custom claim on a user account. From the project root:

```bash
cd functions
node setAdminClaim.js <user_email>
```

This will grant admin privileges to the specified user.

### 4. Run Development Server

```bash
npm run dev
```

The dashboard will be available at `http://localhost:3000`

### 5. Build for Production

```bash
npm run build
```

The production build will be in the `dist/` directory.

## Deployment to Firebase Hosting

### Option 1: Deploy to Firebase Hosting

1. Update `firebase.json` in the project root to include the admin dashboard:

```json
{
  "hosting": {
    "public": "functions/admin-dashboard/dist",
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**"
    ],
    "rewrites": [
      {
        "source": "**",
        "destination": "/index.html"
      }
    ]
  }
}
```

2. Build and deploy:

```bash
npm run build
firebase deploy --only hosting
```

### Option 2: Deploy to Separate Site

Create a separate hosting site for the admin dashboard:

```bash
firebase hosting:sites:create liftrix-admin
```

Then deploy specifically to that site:

```bash
firebase deploy --only hosting:liftrix-admin
```

## Security Considerations

### Authentication & Authorization

- **Admin Custom Claims**: Only users with `admin: true` custom claim can access
- **Client-Side Verification**: Token claims checked on every authentication state change
- **Auto-Logout**: Non-admin users are automatically signed out

### Best Practices

1. **Use HTTPS**: Always access via HTTPS in production
2. **Environment Variables**: Never commit `.env` file to version control
3. **Audit Logs**: All moderation actions are logged with admin ID and timestamp
4. **Two-Factor Authentication**: Enable 2FA for admin accounts in Firebase Console

## Data Model

### Content Reports (content_reports collection)

```javascript
{
  id: string,
  content_id: string,
  content_type: "POST" | "COMMENT" | "PROFILE",
  content_owner_id: string,
  reporter_id: string,
  reason: "SPAM" | "HARASSMENT" | "HATE_SPEECH" | "INAPPROPRIATE" | "MISINFORMATION" | "OTHER",
  reporter_notes: string,
  status: "pending" | "actioned" | "dismissed",
  content_snapshot: object,
  created_at: Timestamp,
  actioned_at: Timestamp,
  actioned_by_admin: string,
  action_taken: "hide" | "delete" | "warn" | "suspend",
  admin_notes: string
}
```

## Troubleshooting

### "Access denied: Admin privileges required"

- Verify the user has the admin custom claim: Run `node setAdminClaim.js <email>`
- Check Firebase Console > Authentication > Users > Custom Claims

### Reports not loading

- Check Firebase Console > Firestore > content_reports collection exists
- Verify Firestore Security Rules allow admin read access
- Check browser console for errors

### Real-time updates not working

- Ensure Firestore Security Rules allow admin read access
- Check network connectivity
- Verify Firebase project configuration in `.env`

## Architecture Notes

### Why Web Dashboard?

- **Separation of Concerns**: Admin tools separated from user-facing mobile app
- **Desktop Efficiency**: Better UX for reviewing multiple reports
- **No App Store Approval**: Can deploy updates instantly without app review
- **Cross-Platform**: Works on any device with a web browser

### Limitations

- **Server-Side Actions Recommended**: Some actions (delete, suspend) should use Cloud Functions
  for proper authorization and data integrity
- **Soft Deletes**: Content deletion is soft (is_deleted flag) to preserve audit trail
- **Basic Suspension**: User suspension uses Firestore flag; Firebase Auth custom claims
  should be updated via Cloud Function for production

## Future Enhancements

- [ ] User search and profile management
- [ ] Bulk moderation actions
- [ ] Report analytics and trends
- [ ] Automated moderation suggestions (ML-based)
- [ ] Appeal system for suspended users
- [ ] Export reports to CSV
- [ ] Email notifications for critical reports
- [ ] Mobile-responsive design improvements

## Support

For issues or questions, contact the development team or file an issue in the repository.

## License

Proprietary - Liftrix App
