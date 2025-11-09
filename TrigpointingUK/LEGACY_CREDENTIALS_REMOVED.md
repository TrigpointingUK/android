# Legacy Username/Password Removal

## Summary

Removed all code that reads or relies on legacy username/password authentication. The app now exclusively uses Auth0 for authentication, and the username is always sourced from the Auth0 user profile.

## What Was Removed

### 1. Legacy Username Display After Settings
**File**: `MainActivity.java` - `preferencesLauncher` callback

**Before**:
```java
if (result.getResultCode() == RESULT_OK) {
    TextView user = findViewById(R.id.txtUserName);
    user.setText(mPrefs.getString("username", ""));  // Legacy!
    populateCounts();
}
```

**After**:
```java
if (result.getResultCode() == RESULT_OK) {
    // Refresh UI after preferences change
    updateUserDisplay();  // Uses Auth0 data
    populateCounts();
}
```

### 2. Legacy Authentication Check in Menu
**File**: `MainActivity.java` - `onCreateOptionsMenu()`

**Before**:
```java
// Check both API authentication and legacy authentication
boolean loggedIn = authPreferences.isLoggedIn() || !prefs.getString("username", "").isEmpty();
```

**After**:
```java
// Check Auth0 authentication only
boolean loggedIn = authPreferences.isLoggedIn();
```

### 3. Legacy Username Fallback in Display
**File**: `MainActivity.java` - `updateUserDisplay()`

**Before**:
```java
if (authPreferences.isLoggedIn()) {
    // Show Auth0 user
    displayName = authPreferences.getDisplayName();
} else {
    // Fallback to legacy username for backward compatibility
    String legacyUsername = prefs.getString("username", "");
    if (!legacyUsername.trim().isEmpty()) {
        displayName = legacyUsername;
        if (devMode) {
            displayName += " (legacy)";  // This showed "Teasel (legacy)"
        }
        isLoggedIn = true;
    }
}
```

**After**:
```java
// Check Auth0 authentication only (no legacy fallback)
if (authPreferences.isLoggedIn()) {
    Log.i(TAG, "updateUserDisplay: User is logged in via Auth0");
    
    if (devMode) {
        displayName = authPreferences.getDisplayNameWithId();
    } else {
        displayName = authPreferences.getDisplayName();
    }
    isLoggedIn = true;
} else {
    Log.i(TAG, "updateUserDisplay: No authentication found");
    displayName = getString(R.string.not_logged_in_status);
    isLoggedIn = false;
}
```

### 4. Legacy Credentials Check in Auto-Sync
**File**: `MainActivity.java` - `checkAndPerformAutoSync()`

**Before**:
```java
if (autoSyncEnabled && !autoSyncAlreadyRun) {
    // Check if user has credentials
    String username = prefs.getString("username", "");
    String password = prefs.getString("plaintextpassword", "");
    
    if (!username.trim().isEmpty() && !password.trim().isEmpty()) {
        // Perform auto sync
        new SyncTask(MainActivity.this, MainActivity.this).execute(false);
    }
}
```

**After**:
```java
if (autoSyncEnabled && !autoSyncAlreadyRun) {
    // Check if user is logged in with Auth0
    if (authPreferences.isLoggedIn()) {
        Log.i(TAG, "checkAndPerformAutoSync: User logged in, performing auto sync");
        prefs.edit().putBoolean(AUTO_SYNC_RUN, true).apply();
        new SyncTask(MainActivity.this, MainActivity.this).execute(false);
    } else {
        Log.i(TAG, "checkAndPerformAutoSync: No credentials found, skipping auto sync");
    }
}
```

## What Remains (For Cleanup)

The `doLogout()` method still clears legacy credentials:
```java
// Clear legacy username/password from default SharedPreferences
editor.remove("username");
editor.remove("plaintextpassword");
```

**Why keep this**: For users upgrading from an older version, this ensures any residual legacy credentials are cleared on logout. This is harmless and provides a clean migration path.

## Where Username Comes From Now

The username is **always** sourced from Auth0:

### Source Chain:

1. **User logs in via Auth0**
   ```
   Auth0Config.login() → WebAuthProvider.login()
   ```

2. **Auth0 returns credentials and user profile**
   ```
   Credentials + UserProfile stored by AuthPreferences
   ```

3. **User profile contains the name**
   ```
   userProfile.getName() → "Teasel"
   userProfile.getId() → "auth0|123456..."
   ```

4. **AuthPreferences provides access**
   ```java
   authPreferences.getDisplayName()          // Returns "Teasel"
   authPreferences.getDisplayNameWithId()    // Returns "Teasel (auth0|123456...)"
   authPreferences.getUserId()               // Returns "auth0|123456..."
   ```

5. **UI displays the name**
   ```java
   updateUserDisplay() → authPreferences.getDisplayName()
   ```

## Benefits

✅ **Single source of truth**: Username always from Auth0  
✅ **No password storage**: App never stores passwords  
✅ **No legacy confusion**: No more "(legacy)" suffix in UI  
✅ **Cleaner codebase**: Removed fallback logic  
✅ **Consistent behavior**: All features use Auth0 data  
✅ **Better security**: No local password storage

## Migration Path for Existing Users

For users who have the app installed with legacy credentials:

1. **First Launch After Update**:
   - App detects Auth0 data is missing
   - Shows "Not logged in"
   - User prompted to login

2. **User Logs In with Auth0**:
   - Auth0 universal login shown
   - User authenticates
   - Auth0 returns user profile with name
   - App stores Auth0 tokens and profile

3. **User Logs Out**:
   - Clears Auth0 data
   - **Also clears any legacy credentials** (cleanup)
   - Shows "Not logged in"

4. **Future Logins**:
   - Always use Auth0
   - No legacy data to interfere

## Testing

### Verify No Legacy Fallback

1. **Fresh install, no login**:
   ```
   Expected: "Not logged in"
   NOT: Any legacy username
   ```

2. **Login with Auth0**:
   ```
   Expected: User's Auth0 name displayed
   NOT: "(legacy)" suffix
   ```

3. **Logout**:
   ```
   Expected: "Not logged in" immediately
   NOT: "Teasel (legacy)" or any username
   ```

4. **Check SharedPreferences**:
   ```bash
   # Should show Auth0 data only (when logged in)
   adb shell run-as uk.trigpointing.android.debug cat /data/data/uk.trigpointing.android.debug/shared_prefs/auth_prefs.xml
   
   # Should NOT contain username or plaintextpassword
   adb shell run-as uk.trigpointing.android.debug cat /data/data/uk.trigpointing.android.debug/shared_prefs/uk.trigpointing.android.debug_preferences.xml | grep -E "(username|plaintextpassword)"
   ```

## Code Search Results

Confirmed no code is **writing** username/password to preferences:
```bash
grep -r "putString(\"username\"" app/src/main/java/
# No results

grep -r "putString(\"plaintextpassword\"" app/src/main/java/
# No results
```

All **reading** of username/password has been removed or is only in logout cleanup.

## Summary

The app is now **100% Auth0-based** for authentication:
- ✅ No code stores username/password
- ✅ No code reads username for display (except logout cleanup)
- ✅ Username always from Auth0 user profile
- ✅ All authentication checks use `authPreferences.isLoggedIn()`
- ✅ Clean migration path for existing users

