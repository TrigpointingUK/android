# Build Variant Configuration

The TrigpointingUK Android app is configured with separate environments for **Debug** and **Release** builds, each targeting different backend infrastructures.

## Overview

| Build Type | Domain | Auth0 Domain | API Base URL | App ID Suffix |
|------------|--------|--------------|--------------|---------------|
| **Release (Production)** | trigpointing.uk | auth.trigpointing.uk | https://api.trigpointing.uk/v1 | (none) |
| **Debug (Testing)** | trigpointing.me | auth.trigpointing.me | https://api.trigpointing.me/v1 | .debug |

## Configuration Files

### Production (Release)

**File**: `app/src/main/res/values/auth0_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="auth0_domain">auth.trigpointing.uk</string>
    <string name="auth0_client_id">IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3</string>
    <string name="auth0_redirect_uri">uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback</string>
    <string name="auth0_audience">https://api.trigpointing.uk/</string>
</resources>
```

### Debug (Testing)

**File**: `app/src/debug/res/values/auth0_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Debug build uses trigpointing.me domain -->
    <string name="auth0_domain">auth.trigpointing.me</string>
    <string name="auth0_client_id">YOUR_DEBUG_CLIENT_ID_HERE</string>
    <string name="auth0_redirect_uri">uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback</string>
    <string name="auth0_audience">https://api.trigpointing.me/</string>
</resources>
```

⚠️ **Important**: Update `YOUR_DEBUG_CLIENT_ID_HERE` with your actual debug Auth0 client ID.

## Build Configuration

### Gradle Build Types

**File**: `app/build.gradle`

```gradle
buildTypes {
    debug {
        applicationIdSuffix ".debug"
        versionNameSuffix "-debug"
        enableUnitTestCoverage true
        enableAndroidTestCoverage true
        
        // Debug build uses trigpointing.me domain
        buildConfigField "String", "API_BASE_URL", "\"https://api.trigpointing.me/v1\""
        
        // Override Auth0 manifest placeholders for debug build
        manifestPlaceholders = [
            auth0Domain: "auth.trigpointing.me",
            auth0Scheme: "uk.trigpointing.android.debug"
        ]
    }
    release {
        minifyEnabled true
        shrinkResources true
        
        // Production build uses trigpointing.uk domain
        buildConfigField "String", "API_BASE_URL", "\"https://api.trigpointing.uk/v1\""
        
        // ... other release config
    }
}
```

## How It Works

### 1. Resource Overlay

Android's build system uses a **resource overlay** system:
- Files in `app/src/main/res/` are used for all build types
- Files in `app/src/debug/res/` **override** main resources for debug builds
- Files in `app/src/release/res/` would override for release builds (not currently used)

When you build a debug APK, Android automatically uses the debug version of `auth0_config.xml`.

### 2. BuildConfig Fields

The `buildConfigField` declarations in `build.gradle` create compile-time constants:

```java
// In your code:
String apiUrl = BuildConfig.API_BASE_URL;

// For debug builds, this equals: "https://api.trigpointing.me/v1"
// For release builds, this equals: "https://api.trigpointing.uk/v1"
```

This is used in `TrigApiClient.java`:

```java
private static final String API_BASE_URL = BuildConfig.API_BASE_URL;
```

### 3. Manifest Placeholders

The `manifestPlaceholders` are used in `AndroidManifest.xml` for deep link configuration:

```xml
<data
    android:scheme="${auth0Scheme}"
    android:host="${auth0Domain}"
    android:path="/android/${applicationId}/callback" />
```

This generates different callback URLs for each build type:
- **Debug**: `uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback`
- **Release**: `uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback`

### 4. Dynamic Callback Validation

In `MainActivity.java`, the callback validation is now dynamic:

```java
String expectedAuth0Domain = getString(R.string.auth0_domain);

if (data.getScheme() != null && data.getScheme().startsWith("uk.trigpointing.android") &&
    data.getHost() != null && data.getHost().equals(expectedAuth0Domain)) {
    // Valid callback
}
```

This automatically uses the correct domain for each build type.

## Auth0 Dashboard Configuration

### Production Auth0 (auth.trigpointing.uk)

**Application Settings**:
- **Domain**: auth.trigpointing.uk
- **Client ID**: IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3
- **Application Type**: Native
- **Allowed Callback URLs**:
  ```
  uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback
  ```
- **Allowed Logout URLs**:
  ```
  uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback
  ```

### Debug Auth0 (auth.trigpointing.me)

**Application Settings**:
- **Domain**: auth.trigpointing.me
- **Client ID**: [Your debug client ID]
- **Application Type**: Native
- **Allowed Callback URLs**:
  ```
  uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
  ```
- **Allowed Logout URLs**:
  ```
  uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
  ```

## Building and Testing

### Build Debug APK

```bash
cd /home/ianh/dev/android/TrigpointingUK
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

This build will:
- Use `auth.trigpointing.me` for authentication
- Call APIs at `https://api.trigpointing.me/v1`
- Have package name `uk.trigpointing.android.debug`

### Build Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

This build will:
- Use `auth.trigpointing.uk` for authentication
- Call APIs at `https://api.trigpointing.uk/v1`
- Have package name `uk.trigpointing.android`

### Install Both Simultaneously

Because debug builds have `.debug` suffix, you can install both versions on the same device:

```bash
# Install release
adb install app/build/outputs/apk/release/app-release.apk

# Install debug (different package name, won't conflict)
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Verifying Configuration

### Check Build Type in Logcat

The app logs the API URL on startup:

```
adb logcat | grep "TrigApiClient"
```

You should see:
- Debug: Requests to `https://api.trigpointing.me/v1/...`
- Release: Requests to `https://api.trigpointing.uk/v1/...`

### Check Auth0 Domain

```
adb logcat | grep "Auth0Config"
```

You should see:
- Debug: Authentication against `auth.trigpointing.me`
- Release: Authentication against `auth.trigpointing.uk`

## Backend Requirements

Ensure your backend infrastructure is set up for both environments:

### Production (trigpointing.uk)
- [ ] API server running at `https://api.trigpointing.uk/`
- [ ] Auth0 custom domain configured for `auth.trigpointing.uk`
- [ ] Database contains production data
- [ ] SSL certificates valid

### Debug (trigpointing.me)
- [ ] API server running at `https://api.trigpointing.me/`
- [ ] Auth0 custom domain configured for `auth.trigpointing.me`
- [ ] Database contains test data (separate from production)
- [ ] SSL certificates valid

## Troubleshooting

### Issue: Wrong API endpoint being used

**Check**: Verify you're building the correct variant
```bash
./gradlew clean
./gradlew assembleDebug  # or assembleRelease
```

### Issue: Auth0 redirect not working

**Check**: 
1. Verify the callback URL is registered in Auth0 Dashboard
2. Check the manifest placeholders in `build.gradle`
3. Verify the Auth0 custom domain is active

### Issue: Debug build uses production data

**Symptom**: Debug app shows production trigpoints

**Fix**: This is expected if both backends share the same database. Consider:
- Using a separate database for the debug environment
- Adding a database filter by environment
- Using test user accounts that only have access to test data

## Adding More Build Variants

If you need additional environments (e.g., staging), you can add product flavors:

```gradle
productFlavors {
    production {
        dimension "environment"
    }
    staging {
        dimension "environment"
        buildConfigField "String", "API_BASE_URL", "\"https://api-staging.trigpointing.uk/v1\""
    }
}
```

This would create variants like `productionDebug`, `productionRelease`, `stagingDebug`, `stagingRelease`.

## Security Notes

- ✅ Debug builds are signed with a debug keystore (not suitable for distribution)
- ✅ Release builds require a release keystore and password
- ✅ Both environments use separate Auth0 tenants for security isolation
- ✅ API tokens are never hardcoded; they're obtained via Auth0 login
- ✅ Debug and release apps can't interfere with each other (different package names)

## Next Steps

1. ✅ Update configuration files (DONE)
2. ✅ Update build.gradle (DONE)
3. ✅ Update TrigApiClient to use BuildConfig (DONE)
4. ✅ Update callback validation (DONE)
5. ⏳ Add your debug Auth0 client ID to `app/src/debug/res/values/auth0_config.xml`
6. ⏳ Configure Auth0 dashboard for debug environment
7. ⏳ Set up backend API at `api.trigpointing.me`
8. ⏳ Test debug build end-to-end
9. ⏳ Test release build end-to-end

