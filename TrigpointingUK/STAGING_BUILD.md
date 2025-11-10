# Staging Build Variant

## Overview

The `staging` build variant allows you to test **release-like builds locally** with R8 code shrinking and optimization enabled, without needing to upload to Google Play. This helps catch ProGuard/R8 issues (like the GSON/Coil error) before they reach production.

## Build Configuration

The staging variant is configured in `app/build.gradle` with the following characteristics:

### Key Features

✅ **R8 Code Shrinking**: `minifyEnabled true` - catches obfuscation/optimization issues  
✅ **Resource Shrinking**: `shrinkResources true` - removes unused resources  
✅ **Debug Signing**: Uses debug keystore - no release key configuration needed  
✅ **Debug Package Name**: `uk.trigpointing.android.debug` - shares Firebase config with debug  
✅ **Test API**: Uses `api.trigpointing.me` - safe for testing  
✅ **Non-Debuggable**: R8 optimizations actually run (unlike debug builds)  
✅ **No Crashlytics Upload**: Mapping files aren't uploaded to Firebase  

### What Makes It Different

| Feature | Debug | Staging | Release |
|---------|-------|---------|---------|
| Minify/R8 | ❌ | ✅ | ✅ |
| Debuggable | ✅ | ❌ | ❌ |
| Signing | Debug | Debug | Release |
| API | trigpointing.me | trigpointing.me | trigpointing.uk |
| Package | .debug | .debug | (base) |
| Firebase Upload | ❌ | ❌ | ✅ |

## How to Use

### In Android Studio

1. Open **View → Tool Windows → Build Variants**
2. In the "Build Variants" panel, select **staging** from the dropdown
3. Click **Run** (green play button) or **Build → Build Bundle(s) / APK(s) → Build APK(s)**

The app will build, install, and run just like a debug build, but with full R8 optimization.

### From Command Line

```bash
# Build the APK
./gradlew assembleStaging

# Install to connected device
adb install -r app/build/outputs/apk/staging/app-staging.apk

# Or build and install in one step
./gradlew installStaging
```

### Output Location

Built APK: `app/build/outputs/apk/staging/app-staging.apk`

## When to Use Staging

Use the staging variant when:

- 🔍 **Testing R8 issues** - Verify ProGuard rules work correctly
- 🚀 **Before Play Store upload** - Catch runtime errors that only appear with minification
- 🐛 **Debugging release crashes** - Reproduce issues that only happen in optimized builds
- ✅ **Validating fixes** - Ensure your ProGuard rule changes work

**Don't use staging for**:
- Regular development (use debug)
- Automated testing (tests use debug by default)
- Production releases (use release)

## Important Notes

### Cannot Install Alongside Debug

Since staging uses the same package name as debug (`uk.trigpointing.android.debug`), you cannot have both installed at the same time. Installing one will replace the other.

### Firebase Configuration

Staging shares the same Firebase configuration as debug builds via `src/staging/google-services.json`. This is intentional to avoid needing to register staging as a separate app in Firebase Console.

### Performance Monitoring

Firebase Performance Monitoring is disabled for staging builds to avoid polluting production metrics with local test data.

### Crashlytics

Crashlytics is enabled but mapping file uploads are disabled (`mappingFileUploadEnabled false`). This means crashes will be logged but stack traces won't be deobfuscated.

## Troubleshooting

### Build Fails with "No matching client found"

Make sure `src/staging/google-services.json` exists and contains the debug package name.

### R8 Warnings or Errors

Check `app/proguard-rules.pro` and add keep rules for classes that need to be preserved. See the existing rules for examples.

### App Crashes at Runtime

This is exactly what staging is for! The crash likely happens due to R8 optimization. Add appropriate ProGuard keep rules for the affected classes.

Common fixes:
- Add `-keep` rules for data model classes used with GSON/Jackson
- Keep classes that use reflection
- Keep native method declarations

## Recent ProGuard Fixes

The following keep rules were added to fix the Coil/GSON error:

```groovy
# Keep all inner classes in TrigApiClient that are used as data models
-keep class uk.trigpointing.android.api.TrigApiClient$** { *; }

# Keep Coil classes that might be referenced indirectly by GSON
-keep class coil.size.** { *; }
-dontwarn coil.size.**
```

See `app/proguard-rules.pro` for the complete configuration.

## Summary

The staging build variant is your safety net for catching R8/ProGuard issues before they reach users. Use it liberally when making changes that might affect serialization, reflection, or other runtime behaviors that R8 optimization could break.

**Pro tip**: Make staging builds part of your pre-release checklist!

