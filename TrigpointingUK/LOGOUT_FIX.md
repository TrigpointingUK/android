# Logout Issue Fix

## Problem

When logging out from the debug build:
1. User was redirected to Auth0
2. Auth0 showed a white/blank screen
3. After pressing back, the user appeared still logged in
4. Had to manually add logout callback URL to Auth0 "Allowed Logout URLs"

## Root Causes

### 1. Hardcoded Scheme
**File**: `Auth0Config.java`

Both `login()` and `logout()` were using a hardcoded scheme:
```java
.withScheme("uk.trigpointing.android")
```

This doesn't work for debug builds which have package name `uk.trigpointing.android.debug`.

**Fix**: Use the package name dynamically:
```java
String scheme = context.getPackageName();
.withScheme(scheme)
```

This automatically uses:
- `uk.trigpointing.android` for release builds
- `uk.trigpointing.android.debug` for debug builds

### 2. Missing Return URL
**File**: `Auth0Config.java`

The logout wasn't specifying where to return to after logout.

**Fix**: Added explicit return URL:
```java
.withReturnToUrl(auth0RedirectUri)
```

### 3. Callback Handler Confusion
**File**: `MainActivity.java`

The `handleAuth0Callback()` method was treating all callbacks the same way, not distinguishing between login and logout callbacks.

**Fix**: Added logic to detect callback type:
```java
String code = data.getQueryParameter("code");
String error = data.getQueryParameter("error");

if (code != null || error != null) {
    // Login callback - has authorization code or error
    Log.i(TAG, "Login callback detected");
} else {
    // Logout callback - no code parameter
    Log.i(TAG, "Logout callback detected, no action needed");
}
```

### 4. Legacy Credentials Not Cleared
**File**: `MainActivity.java`

The app has a fallback to display legacy username/password credentials. When Auth0 logout cleared Auth0 data, the legacy `username` and `plaintextpassword` were still in SharedPreferences, causing the UI to show "Teasel (legacy)" even after logout.

**Fix**: Added legacy credential clearing to `doLogout()`:
```java
// Clear legacy username/password from default SharedPreferences
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
SharedPreferences.Editor editor = prefs.edit();
editor.remove("username");
editor.remove("plaintextpassword");
editor.commit();  // Synchronous
```

## Changes Made

### 1. Auth0Config.java

**Login Method**:
```java
public void login(Auth0Callback callback) {
    // Use package name as scheme (handles both release and debug builds automatically)
    String scheme = context.getPackageName();
    Log.i(TAG, "Using scheme: " + scheme);
    
    WebAuthProvider.login(auth0)
            .withScheme(scheme)  // Changed from hardcoded
            // ... rest of config
}
```

**Logout Method**:
```java
public void logout(LogoutCallback callback) {
    // Use package name as scheme (handles both release and debug builds automatically)
    String scheme = context.getPackageName();
    Log.i(TAG, "Using scheme for logout: " + scheme);
    
    WebAuthProvider.logout(auth0)
            .withScheme(scheme)                // Changed from hardcoded
            .withReturnToUrl(auth0RedirectUri) // Added explicit return URL
            // ... rest of config
}
```

### 2. MainActivity.java

**Improved Callback Handling**:
```java
private void handleAuth0Callback(android.net.Uri data) {
    // ... validation ...
    
    // Check if this is a logout callback (no code parameter) or login callback (has code parameter)
    String code = data.getQueryParameter("code");
    String error = data.getQueryParameter("error");
    
    if (code != null || error != null) {
        // This is a login callback (has code or error)
        Log.i(TAG, "Valid Auth0 login callback detected");
        // Process login
    } else {
        // This is a logout callback (no code parameter)
        Log.i(TAG, "Logout callback detected, no action needed");
        // Process logout
    }
    
    // Always call resume for Auth0 SDK
    Intent intent = new Intent();
    intent.setData(data);
    WebAuthProvider.resume(intent);
}
```

## How Logout Works Now

### Flow

1. **User selects "Logout" from menu**
   ```
   doLogout() is called
   ```

2. **Auth0 data is cleared immediately**
   ```
   authPreferences.clearAuthData() - synchronous commit()
   ```

3. **Legacy credentials are cleared**
   ```
   prefs.remove("username")
   prefs.remove("plaintextpassword")
   editor.commit() - synchronous
   ```

4. **Database logs cleared**
   ```
   DbHelper.clearUserLogs()
   ```

5. **UI updates immediately**
   ```
   updateUserDisplay()  // Shows "Not logged in"
   invalidateOptionsMenu()  // Menu shows "Login"
   ```

6. **Auth0 browser logout is initiated**
   ```
   auth0Config.logout() with callbacks
   ```

4. **Browser opens to Auth0 logout endpoint**
   ```
   https://auth.trigpointing.me/v2/logout?client_id=...&returnTo=...
   ```

7. **Auth0 clears browser session and redirects back**
   ```
   uk.trigpointing.android.debug://auth.trigpointing.me/...
   ```

8. **App receives callback**
   ```
   handleAuth0Callback() detects logout (no code parameter)
   WebAuthProvider.resume() completes the logout flow
   ```

9. **Logout callback fires**
   ```
   onSuccess() updates UI again and shows "Logged out successfully" toast
   ```

### Why It's Better

- ✅ **Works for both debug and release**: Automatically uses correct scheme
- ✅ **Proper return URL**: Auth0 knows where to redirect after logout
- ✅ **Immediate UI feedback**: User sees "Not logged in" right away
- ✅ **Browser session cleared**: Prevents auto-login on next attempt
- ✅ **No white screen**: Proper return URL prevents blank page
- ✅ **Legacy credentials cleared**: Removes old username/password data
- ✅ **Synchronous clearing**: Data removed before UI updates

## Auth0 Dashboard Configuration

You still need these logout URLs configured in your Auth0 applications:

### Production (trigpointing.uk)
```
uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback
```

### Debug (trigpointing.me)
```
uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
```

⚠️ **Note**: These must be in both "Allowed Callback URLs" AND "Allowed Logout URLs" in Auth0 Dashboard.

## Testing

### Test Logout Flow

1. **Install the debug APK**
   ```bash
   ./gradlew installDebug
   ```

2. **Login with Auth0**
   - Tap "Login with Auth0" from menu
   - Complete authentication
   - Verify you see your username displayed

3. **Logout**
   - Tap "Logout" from menu
   - Should see:
     - UI immediately updates to "Not logged in"
     - Browser briefly opens to Auth0
     - Browser automatically redirects back to app
     - Toast: "Logged out successfully"

4. **Verify logout**
   - Tap "Login with Auth0" again
   - Should be prompted to login (not auto-logged-in)

### Expected Logcat Output

```
Auth0Config: Starting Auth0 logout
Auth0Config: Using scheme for logout: uk.trigpointing.android.debug
MainActivity: Logout callback detected, no action needed
Auth0Config: Auth0 logout successful
MainActivity: Logged out successfully
```

## Troubleshooting

### Issue: Still seeing white screen

**Check**:
1. Verify logout URL is in Auth0 Dashboard "Allowed Logout URLs"
2. Check logcat for scheme being used:
   ```bash
   adb logcat | grep "Using scheme for logout"
   ```
3. Ensure the scheme matches what's configured in Auth0

### Issue: User still appears logged in

**Check**:
1. Verify `clearAuthData()` is being called
2. Verify legacy credentials are being cleared:
   ```bash
   adb logcat | grep "Cleared legacy username/password"
   ```
3. Check SharedPreferences (both auth_prefs and default):
   ```bash
   # Auth0 data
   adb shell run-as uk.trigpointing.android.debug cat /data/data/uk.trigpointing.android.debug/shared_prefs/auth_prefs.xml
   
   # Legacy data
   adb shell run-as uk.trigpointing.android.debug cat /data/data/uk.trigpointing.android.debug/shared_prefs/uk.trigpointing.android.debug_preferences.xml
   ```
4. Should show no auth0 tokens and no username/plaintextpassword

### Issue: Logout callback not being handled

**Check**:
1. Verify AndroidManifest.xml has the intent filter for the scheme
2. Check logcat:
   ```bash
   adb logcat | grep "handleAuth0Callback"
   ```

## Summary

✅ **Fixed**: Dynamic scheme detection works for both debug and release builds  
✅ **Fixed**: Logout now specifies return URL to prevent white screen  
✅ **Fixed**: Callback handler distinguishes between login and logout  
✅ **Fixed**: Legacy username/password credentials cleared on logout  
✅ **Fixed**: Synchronous data clearing ensures immediate UI updates  
✅ **Improved**: Better logging for debugging auth flow  
✅ **Working**: Local data is cleared immediately on logout  
✅ **Working**: Browser session is properly cleaned up

The logout flow should now work smoothly without white screens, lingering sessions, or legacy credential interference!

