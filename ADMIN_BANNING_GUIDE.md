# Admin User Banning System Guide

This document provides comprehensive instructions for administrators on how to use the Firebase Admin SDK user banning system in Liftrix.

## Overview

The Liftrix admin banning system provides secure, server-side user management through Firebase Cloud Functions with Admin SDK. All operations are logged for audit purposes and require proper admin permissions.

## Features

- **Secure Server-Side Banning**: Uses Firebase Admin SDK through Cloud Functions
- **Comprehensive Ban Management**: Ban, unban, search users, and view ban history
- **Severity Levels**: Minor, Moderate, Severe, Critical violations
- **Audit Trail**: Complete logging of all admin actions
- **Permission Validation**: Only users with admin custom claims can perform operations
- **Social Integration**: Automatic removal from discoverable profiles and social contexts

## Prerequisites

### 1. Admin Permission Setup

Before using the banning system, ensure the user has admin permissions:

```bash
# Set admin custom claims using Firebase CLI or Admin SDK
firebase auth:set-claims USER_ID --claims admin=true
```

### 2. Deploy Cloud Functions

Make sure the admin-related Cloud Functions are deployed:

```bash
cd functions
npm install
firebase deploy --only functions:banUser,functions:unbanUser,functions:getUserBanInfo,functions:searchUsers,functions:listBannedUsers
```

## How to Ban Users

### Using the Admin Interface

1. **Access Admin Panel**
   - Navigate to the Admin Ban Management screen in the app
   - Only users with admin permissions can access this feature

2. **Search for Users**
   - Use the "Search Users" tab
   - Enter email address or display name (minimum 3 characters)
   - View search results with current ban status

3. **Ban Process**
   - Click "Ban" button next to the user
   - Fill out the ban dialog:
     - **Reason**: Required, minimum 10 characters explaining the violation
     - **Severity**: Choose from Minor, Moderate, Severe, or Critical
     - **Duration**: Select permanent or specify duration (e.g., "7d", "30d", "1y")
   - Confirm the ban action

### Using Firebase Functions Directly

You can also call the functions directly using the Firebase Admin SDK:

```javascript
// Example: Ban a user
const banUserFunction = firebase.functions().httpsCallable('banUser');

const result = await banUserFunction({
  userId: 'target-user-id',
  reason: 'Violation of community guidelines - inappropriate content',
  severity: 'moderate',
  banDuration: '30d' // or null for permanent
});

console.log('Ban result:', result.data);
```

## Ban Severity Guidelines

### Minor Violations
- **Examples**: Mild spam, minor rule infractions, first-time violations
- **Typical Duration**: 1-7 days
- **Use When**: User behavior needs correction but isn't severely harmful

### Moderate Violations
- **Examples**: Repeated rule violations, inappropriate content, harassment
- **Typical Duration**: 7-30 days
- **Use When**: Clear policy violations that require significant intervention

### Severe Violations
- **Examples**: Hate speech, serious harassment, explicit content
- **Typical Duration**: 30 days - 6 months
- **Use When**: Behavior seriously violates community standards

### Critical Violations
- **Examples**: Illegal content, doxxing, severe threats, ban evasion
- **Typical Duration**: Permanent
- **Use When**: Immediate safety concerns or legal violations

## What Happens When a User is Banned

When you ban a user, the system automatically:

1. **Disables Firebase Authentication**: User cannot sign in
2. **Updates Custom Claims**: Sets `banned: true` with ban metadata
3. **Creates Ban Record**: Stores detailed ban information in Firestore
4. **Updates User Profile**: Marks account as "banned"
5. **Removes from Social Contexts**:
   - Profile becomes non-discoverable
   - Removed from public user searches
   - Social profile set to private
6. **Logs Admin Action**: Creates audit trail record

## How to Unban Users

### Using the Admin Interface

1. **Find Banned User**
   - Use "Banned Users" tab to see all currently banned users
   - Or search for specific user in "Search Users" tab

2. **Unban Process**
   - Click "Unban" button
   - Provide reason for unbanning (optional)
   - Confirm the unban action

3. **User Status After Unban**
   - Firebase Authentication re-enabled
   - Account status changed to "active"
   - Ban records marked as "inactive"
   - Social profile restored (but remains private by default)

### Using Firebase Functions Directly

```javascript
// Example: Unban a user
const unbanUserFunction = firebase.functions().httpsCallable('unbanUser');

const result = await unbanUserFunction({
  userId: 'target-user-id',
  reason: 'Appeal approved - user demonstrated understanding of community guidelines'
});

console.log('Unban result:', result.data);
```

## Viewing Ban Information

### User Ban History

To get detailed information about a user's ban history:

```javascript
const getUserBanInfoFunction = firebase.functions().httpsCallable('getUserBanInfo');

const result = await getUserBanInfoFunction({
  userId: 'target-user-id'
});

console.log('User ban info:', result.data);
```

This returns:
- Firebase user information
- User profile data
- Complete ban history
- Current ban status

### List All Banned Users

```javascript
const listBannedUsersFunction = firebase.functions().httpsCallable('listBannedUsers');

const result = await listBannedUsersFunction({
  limit: 50,
  offset: 0,
  severity: 'severe' // optional filter
});

console.log('Banned users:', result.data);
```

## Admin Action Logs

All admin actions are logged for auditing purposes. Logs include:
- Action type (BAN_USER, UNBAN_USER, etc.)
- Performing admin ID
- Target user ID
- Action details (reason, severity, etc.)
- Timestamp
- Success/failure status

View logs in the "Admin Logs" tab of the admin interface.

## Troubleshooting

### Common Issues

1. **"Access Denied" Error**
   - Verify admin custom claims are set correctly
   - Check Firebase Auth token contains `admin: true`

2. **"User not found" Error**
   - Confirm the user ID exists in Firebase Auth
   - Check for typos in user ID

3. **Function Call Timeout**
   - Check Firebase project connectivity
   - Verify Cloud Functions are deployed and running

4. **Sharing Not Working After Implementation**
   - Ensure FileProvider authority matches in AndroidManifest.xml
   - Verify READ_EXTERNAL_STORAGE permission if needed
   - Check that image files exist and are readable

### Verification Steps

1. **Test Admin Permissions**:
   ```bash
   # Check if user has admin claims
   firebase auth:get-claims USER_ID
   ```

2. **Verify Functions Deployment**:
   ```bash
   firebase functions:list
   ```

3. **Check Function Logs**:
   ```bash
   firebase functions:log
   ```

## Security Considerations

- **Admin Access**: Only grant admin permissions to trusted users
- **Audit Trail**: Regularly review admin action logs
- **Ban Appeals**: Consider implementing an appeal process
- **Data Privacy**: Ensure ban reasons comply with privacy policies
- **Backup Strategy**: Maintain backups of ban records for legal compliance

## Best Practices

1. **Clear Documentation**: Always provide specific reasons for bans
2. **Proportional Response**: Match severity to the actual violation
3. **Warning System**: Consider warnings before bans for minor violations
4. **Appeal Process**: Provide users with a way to appeal bans
5. **Regular Review**: Periodically review and update ban policies
6. **Staff Training**: Ensure all admins understand policies and procedures

## API Reference

### Ban User Function
- **Function**: `banUser`
- **Parameters**: `userId`, `reason`, `severity`, `banDuration`
- **Returns**: Ban confirmation with ban ID and timestamp

### Unban User Function  
- **Function**: `unbanUser`
- **Parameters**: `userId`, `reason`
- **Returns**: Unban confirmation with timestamp

### Search Users Function
- **Function**: `searchUsers`
- **Parameters**: `query`, `limit`
- **Returns**: List of users matching search criteria

### Get Ban Info Function
- **Function**: `getUserBanInfo`
- **Parameters**: `userId`
- **Returns**: Comprehensive user and ban information

### List Banned Users Function
- **Function**: `listBannedUsers`
- **Parameters**: `limit`, `offset`, `severity`
- **Returns**: Paginated list of banned users

## Support

For additional help with the admin banning system:
1. Check the logs for detailed error messages
2. Verify all prerequisites are met
3. Test with a non-production user first
4. Review Firebase Console for function execution details

Remember: With great power comes great responsibility. Use the banning system fairly and consistently to maintain a positive community environment.