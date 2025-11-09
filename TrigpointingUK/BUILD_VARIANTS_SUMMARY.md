# Build Variant Setup - Summary

## What Was Done

Set up separate configurations for **Debug** and **Release** builds to target different backend environments.

## Changes Made

### 1. Debug Auth0 Configuration
**File**: `app/src/debug/res/values/auth0_config.xml`
- Changed domain from `auth.trigpointing.uk` → `auth.trigpointing.me`
- Updated redirect URI to use `.me` domain
- Updated audience to use `.me` API
- ⚠️ **Action Required**: Replace `YOUR_DEBUG_CLIENT_ID_HERE` with your actual debug client ID

### 2. Build Configuration
**File**: `app/build.gradle`
- Added `buildConfigField` for `API_BASE_URL`:
  - Debug: `https://api.trigpointing.me/v1`
  - Release: `https://api.trigpointing.uk/v1`
- Updated `manifestPlaceholders` for debug:
  - `auth0Domain`: `auth.trigpointing.me`
  - `auth0Scheme`: `uk.trigpointing.android.debug`

### 3. API Client
**File**: `TrigApiClient.java`
- Changed from hardcoded URL to `BuildConfig.API_BASE_URL`
- Added import for `uk.trigpointing.android.BuildConfig`
- Now automatically uses correct API endpoint based on build type

### 4. Callback Validation
**File**: `MainActivity.java`
- Updated `handleAuth0Callback()` to dynamically read Auth0 domain from resources
- Now works with both `.uk` (production) and `.me` (debug) domains
- Uses `getString(R.string.auth0_domain)` instead of hardcoded value

## Configuration Summary

| Component | Production (Release) | Debug |
|-----------|---------------------|-------|
| **Auth0 Domain** | auth.trigpointing.uk | auth.trigpointing.me |
| **API Base URL** | https://api.trigpointing.uk/v1 | https://api.trigpointing.me/v1 |
| **Package Name** | uk.trigpointing.android | uk.trigpointing.android.debug |
| **Callback URL** | uk.trigpointing.android://auth.trigpointing.uk/... | uk.trigpointing.android.debug://auth.trigpointing.me/... |

## Benefits

1. ✅ **Parallel Installation**: Debug and release apps can be installed simultaneously (different package names)
2. ✅ **Isolated Environments**: Production and test data completely separated
3. ✅ **Safe Testing**: Test against `.me` backend without affecting production
4. ✅ **Automatic Configuration**: No code changes needed when switching build types

## What You Need to Do

### 1. Update Debug Client ID
Edit `app/src/debug/res/values/auth0_config.xml`:
```xml
<string name="auth0_client_id">YOUR_ACTUAL_DEBUG_CLIENT_ID</string>
```

### 2. Configure Auth0 Dashboard (Debug Environment)
Add these callback URLs to your debug Auth0 application:
```
uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
```

### 3. Verify Backend Infrastructure
Ensure these endpoints are accessible:
- Production: `https://api.trigpointing.uk/v1/`
- Debug: `https://api.trigpointing.me/v1/`

## Testing

### Build Debug
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
# Uses: auth.trigpointing.me + api.trigpointing.me
```

### Build Release
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
# Uses: auth.trigpointing.uk + api.trigpointing.uk
```

## Verification

Both builds completed successfully:
- ✅ Debug build: Compiles without errors
- ✅ Release build: Compiles without errors
- ✅ ProGuard/R8 optimization: Working correctly

## Documentation

See `BUILD_VARIANTS.md` for comprehensive details on:
- How the configuration system works
- Troubleshooting steps
- Auth0 dashboard setup
- Adding additional build variants

