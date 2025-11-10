# Release Checklist

This checklist ensures all required files are uploaded to Google Play Console for each release.

## Pre-Release Testing

- [ ] Test staging build locally to catch R8/ProGuard issues
  ```bash
  ./gradlew assembleStaging
  adb install app/build/outputs/apk/staging/app-staging.apk
  ```
- [ ] Run unit tests: `./gradlew test`
- [ ] Run instrumentation tests: `./gradlew connectedAndroidTest`
- [ ] Verify ProGuard rules are up to date

## Version Bump

- [ ] Update `versionCode` in `app/build.gradle`
- [ ] Update `versionName` in `app/build.gradle`
- [ ] Commit version bump changes

## Build Release Bundle

```bash
cd /home/ianh/dev/android/TrigpointingUK
./gradlew clean bundleRelease
```

This will generate:
- **App Bundle**: `app/build/outputs/bundle/release/app-release.aab`
- **Native Debug Symbols**: `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`
- **ProGuard Mapping**: `app/build/outputs/mapping/release/mapping.txt`

## Upload to Play Console

### 1. Upload App Bundle
- Go to Play Console → TrigpointingUK → Release → Production
- Click "Create new release"
- Upload `app-release.aab`

### 2. Upload Native Debug Symbols ⚠️
**IMPORTANT**: This must be done manually for each release!

- In the same release screen, find "App bundle" section
- Look for "Native debug symbols" 
- Click "Upload debug symbols"
- Select: `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`

**Why?** Native libraries from dependencies (Firebase, Auth0, etc.) contain native code. Uploading symbols helps Google Play deobfuscate native crash reports.

### 3. Verify ProGuard Mapping Upload (Automatic)
Firebase Crashlytics automatically uploads the ProGuard mapping file (`mapping.txt`) thanks to:
```groovy
firebaseCrashlytics {
    mappingFileUploadEnabled true
}
```

You can verify this in Firebase Console → Crashlytics → Settings.

## Post-Release

- [ ] Add release notes
- [ ] Roll out to testing track first (if available)
- [ ] Monitor crash reports in:
  - Firebase Crashlytics
  - Google Play Console → Vitals
- [ ] Tag the release in git:
  ```bash
  git tag -a v2.1.44 -m "Release 2.1.44"
  git push origin v2.1.44
  ```

## File Locations Summary

| File | Location | Upload Method |
|------|----------|---------------|
| App Bundle (.aab) | `app/build/outputs/bundle/release/app-release.aab` | Manual |
| Native Symbols (.zip) | `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` | Manual |
| ProGuard Mapping (.txt) | `app/build/outputs/mapping/release/mapping.txt` | Automatic (Crashlytics) |

## Debug Symbol Levels

Current configuration (`app/build.gradle`):
```groovy
ndk {
    debugSymbolLevel 'FULL'
}
```

Options:
- `NONE` - No symbols (not recommended)
- `SYMBOL_TABLE` - Basic symbols
- `FULL` - Full debug info (recommended for Play Console)

## Troubleshooting

### "This App Bundle contains native code" warning

This warning appears when you upload an AAB without the native debug symbols zip file. To fix:

1. Build the release bundle: `./gradlew bundleRelease`
2. Find the symbols: `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`
3. Upload in Play Console under the release's "Native debug symbols" section

### Symbols file not found

If the symbols file doesn't exist after building, verify:
```bash
# Check if native libraries are present in the AAB
unzip -l app/build/outputs/bundle/release/app-release.aab | grep "\.so$"
```

If you see `.so` files, symbols should be generated. If not present, the warning shouldn't appear.

### Why are symbols needed?

Native libraries (from Firebase, Auth0, image processing, etc.) contain compiled C/C++ code. When these crash, the stack traces contain memory addresses. Debug symbols map these addresses back to readable function names and line numbers.

Without symbols:
```
  #00 pc 0002f4ac  /data/app/.../lib/arm64/libfirebase.so
```

With symbols:
```
  #00 pc 0002f4ac  /data/app/.../lib/arm64/libfirebase.so (firebase::Initialize+124)
```

## Current Release

**Version**: 2.1.44 (code 44)  
**Last Updated**: 2025-11-10

## Quick Commands

```bash
# Clean build of release bundle
./gradlew clean bundleRelease

# List generated files
ls -lh app/build/outputs/bundle/release/
ls -lh app/build/outputs/native-debug-symbols/release/

# Extract version info
grep "versionCode\|versionName" app/build.gradle

# Test staging build (catches R8 issues)
./gradlew assembleStaging && adb install -r app/build/outputs/apk/staging/app-staging.apk
```

## References

- [Android Debug Symbols Documentation](https://developer.android.com/studio/build/shrink-code#native-crash-support)
- [Play Console Native Crash Support](https://support.google.com/googleplay/android-developer/answer/9848633)
- Staging Build Guide: See `STAGING_BUILD.md`

