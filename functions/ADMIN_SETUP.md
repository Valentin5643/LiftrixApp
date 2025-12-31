# Admin Dashboard Setup Guide

## Step 1: Download Service Account Key

1. Visit Firebase Console:
   https://console.firebase.google.com/project/liftrix-390cf/settings/serviceaccounts/adminsdk

2. Click **"Generate new private key"**

3. Save the downloaded JSON file as:
   ```
   C:\Users\Administrator\LiftrixApp\functions\service-account-key.json
   ```

4. ⚠️ **IMPORTANT**: Add to `.gitignore` (already done):
   ```
   functions/service-account-key.json
   ```

## Step 2: Grant Admin Privileges

Run this command from the `functions` directory:

```bash
cd C:\Users\Administrator\LiftrixApp\functions
node setAdminClaim.js valijianu98@gmail.com
```

Expected output:
```
✅ Admin claim set successfully for user: valijianu98@gmail.com
User ID: <firebase-uid>
```

## Step 3: Access Admin Dashboard

1. **Visit**: https://liftrix-390cf.web.app

2. **Login** with your admin account:
   - Email: valijianu98@gmail.com
   - Password: (your Firebase Auth password)

3. **Important**: You may need to **sign out and sign back in** for the admin claim to take effect.

## Step 4: Verify Admin Access

After logging in, you should see:
- Content moderation dashboard
- User reports
- AI safety reports
- User management tools

If you see "Permission denied" or "Not authorized", try:
1. Sign out completely
2. Close all browser tabs
3. Sign back in
4. The custom claim should now be active

## Alternative: Manual Claim via Firebase Console

If the script doesn't work, you can set the claim manually:

1. Go to: https://console.firebase.google.com/project/liftrix-390cf/authentication/users

2. Find your user (valijianu98@gmail.com)

3. Click the user → Edit user

4. In the **Custom claims** section, add:
   ```json
   {"admin": true}
   ```

5. Save and sign out/in on the admin dashboard

## Troubleshooting

### Error: "Cannot find module './service-account-key.json'"
- Make sure you downloaded the service account key from Step 1
- Verify the file is named exactly: `service-account-key.json`
- Check it's in the correct directory: `functions/`

### Error: "User not found"
- Make sure the user has signed up via Firebase Authentication first
- Check the email is spelled correctly
- Verify in Firebase Console → Authentication → Users

### Admin Dashboard shows "Permission Denied"
- Sign out and sign back in (custom claims are cached)
- Check Firebase Console → Authentication → Users → Custom claims
- Verify `{"admin": true}` is set

### Can't access Firebase Console
- Make sure you're logged in with the Firebase project owner account
- Check project permissions: Console → Settings → Users and permissions

## Security Notes

- ⚠️ **NEVER commit service-account-key.json to git**
- ⚠️ Only grant admin access to trusted users
- ⚠️ Admin users can delete posts, ban users, and access all reports
- ⚠️ All moderation actions are logged to `moderation_actions` collection

## Next Steps

Once you have admin access:
1. Test content moderation workflows
2. Review any existing user reports
3. Set up admin notification preferences
4. Document your moderation policies
