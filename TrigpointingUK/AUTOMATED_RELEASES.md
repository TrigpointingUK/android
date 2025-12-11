# Automated Play Console Uploads

This guide covers how to automate uploading releases to Google Play Console, eliminating manual toil.

## Option 1: Gradle Play Publisher Plugin ⭐ (Recommended)

**Best for**: Solo developers or small teams  
**Setup time**: 15 minutes  
**Maintenance**: Very low

### What It Automates

✅ Uploads AAB to Play Console  
✅ Uploads native debug symbols automatically  
✅ Uploads ProGuard mappings  
✅ Creates releases with release notes  
✅ Manages staged rollouts  
✅ Promotes between tracks (internal → beta → production)

### One-Command Release

```bash
# Build and publish to production track
./gradlew publishReleaseBundle

# Or publish to internal testing first
./gradlew publishReleaseBundle --track internal
```

---

## Setup Instructions

### Step 1: Add the Plugin

**Edit `settings.gradle`** and add the plugin version:

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id 'com.android.application' version '8.12.3'
        id 'org.jetbrains.kotlin.android' version '2.2.0'
        // ... existing plugins ...
        
        // Add this line:
        id 'com.github.triplet.play' version '3.11.0'
    }
}
```

**Edit `app/build.gradle`** and apply the plugin:

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
    id 'com.google.firebase.firebase-perf' apply false
    id 'com.mikepenz.aboutlibraries.plugin'
    id 'jacoco'
    
    // Add this line:
    id 'com.github.triplet.play'
}
```

### Step 2: Create Google Play Service Account

1. **Go to Google Play Console:**
   - Settings → API access
   - Click "Create new service account"

2. **In Google Cloud Console** (opens automatically):
   - Create a new service account
   - Name it: `trigpointinguk-play-publisher`
   - Click "Create and Continue"
   
3. **Grant permissions:**
   - Role: "Service Account User"
   - Click "Continue" → "Done"

4. **Back in Play Console:**
   - Click "Grant access" on your new service account
   - Permissions needed:
     - ✅ View app information and download bulk reports
     - ✅ Release to production, exclude devices, and use Play App Signing
     - ✅ Release to testing tracks
     - ✅ Manage testing tracks and edit tester lists
   - Click "Invite user" → "Send invite"

5. **Generate JSON Key:**
   - Go back to Google Cloud Console → IAM & Admin → Service Accounts
   - Click on your service account
   - Keys tab → Add Key → Create new key
   - Choose JSON
   - **Download and save securely!** This file gives full access to your Play Console.

### Step 3: Store the Service Account Key

**IMPORTANT**: Never commit this file to git!

Create the directory:
```bash
mkdir -p ~/.config/play-publisher
```

Move the downloaded JSON file:
```bash
mv ~/Downloads/trigpointinguk-*.json ~/.config/play-publisher/trigpointinguk-service-account.json
chmod 600 ~/.config/play-publisher/trigpointinguk-service-account.json
```

### Step 4: Configure the Plugin

**Add to `app/build.gradle`** (after the `android` block):

```groovy
play {
    // Path to service account JSON key
    serviceAccountCredentials = file("${System.getProperty('user.home')}/.config/play-publisher/trigpointinguk-service-account.json")
    
    // Default track (can override with --track parameter)
    track = "production"
    
    // Release status
    releaseStatus = "completed"  // or "draft" to review before publishing
    
    // Upload native debug symbols automatically
    uploadNativeDebugSymbols = true
    
    // Default release notes (can be customized per release)
    defaultToAppBundles = true
}
```

### Step 5: (Optional) Release Notes

Create release notes in your project:

```
app/src/main/play/release-notes/
├── en-US/
│   └── default.txt
└── en-GB/
    └── default.txt
```

**Example `app/src/main/play/release-notes/en-US/default.txt`:**
```
• Fixed crash when viewing trig logs
• Performance improvements
• Bug fixes and stability improvements
```

Or use version-specific notes:
```
app/src/main/play/release-notes/en-US/production.txt
```

---

## Usage

### Build and Publish

```bash
# Publish to production
./gradlew publishReleaseBundle

# Publish to internal testing first (recommended)
./gradlew publishReleaseBundle --track internal

# Publish to beta
./gradlew publishReleaseBundle --track beta

# Create a draft (review before publishing)
./gradlew publishReleaseBundle --release-status draft
```

### Promote Between Tracks

```bash
# Promote from internal to beta
./gradlew promoteReleaseArtifact --from-track internal --promote-track beta

# Promote from beta to production
./gradlew promoteReleaseArtifact --from-track beta --promote-track production
```

### Staged Rollout

**In `app/build.gradle`:**
```groovy
play {
    // ... other config ...
    
    // Start with 10% rollout
    userFraction = 0.1
}
```

Then increase gradually:
```bash
./gradlew publishReleaseBundle --user-fraction 0.25  # 25%
./gradlew publishReleaseBundle --user-fraction 0.50  # 50%
./gradlew publishReleaseBundle --user-fraction 1.0   # 100%
```

---

## Typical Workflow

### Conservative Approach (Recommended)

```bash
# 1. Update version in app/build.gradle
# 2. Test with staging build
./gradlew assembleStaging && adb install -r app/build/outputs/apk/staging/app-staging.apk

# 3. Publish to internal testing
./gradlew publishReleaseBundle --track internal

# 4. Test with internal testers, monitor crashes

# 5. Promote to production
./gradlew promoteReleaseArtifact --from-track internal --promote-track production
```

### Aggressive Approach

```bash
# 1. Update version
# 2. Test staging build
./gradlew assembleStaging

# 3. Publish directly to production
./gradlew publishReleaseBundle
```

---

## Option 2: Fastlane

**Best for**: Teams using Fastlane for iOS too  
**Setup time**: 30 minutes  
**Maintenance**: Medium

Install Fastlane:
```bash
sudo gem install fastlane
```

Initialize:
```bash
cd /home/ianh/dev/android/TrigpointingUK
fastlane init
```

Deploy:
```bash
fastlane deploy
```

---

## Option 3: GitHub Actions (CI/CD)

**Best for**: Automated releases on git push  
**Setup time**: 1 hour  
**Maintenance**: Low

Create `.github/workflows/release.yml`:

```yaml
name: Release to Play Store

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build Release Bundle
        run: ./gradlew bundleRelease
      
      - name: Upload to Play Store
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: uk.trigpointing.android
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: production
          status: completed
```

**Trigger release:**
```bash
git tag v2.1.44
git push origin v2.1.44
```

---

## Comparison

| Method | Setup Time | Best For | Pros | Cons |
|--------|------------|----------|------|------|
| **Gradle Play Publisher** | 15 min | Solo dev, simple setup | One command, low maintenance | Runs locally |
| **Fastlane** | 30 min | Cross-platform teams | Powerful, many features | Ruby dependency |
| **GitHub Actions** | 1 hour | Automated CI/CD | Fully automated | More complex setup |
| **Manual Upload** | 0 min | One-time releases | No setup | Tedious, error-prone |

---

## Recommendation

For your use case, I recommend **Gradle Play Publisher** because:

1. ✅ **Quick setup** - 15 minutes, mostly waiting for Google Cloud
2. ✅ **Single command** - `./gradlew publishReleaseBundle`
3. ✅ **Handles everything** - AAB, symbols, mappings, release notes
4. ✅ **No CI/CD needed** - Run from your laptop when ready
5. ✅ **Low maintenance** - Just works™

You can always graduate to GitHub Actions later if you want full automation.

---

## Security Notes

### Service Account JSON Key

- ⚠️ **NEVER commit to git!**
- Store in `~/.config/play-publisher/` (outside project)
- Set permissions: `chmod 600 service-account.json`
- Add to `.gitignore`:
  ```
  *.json
  !google-services.json
  !google-services.template.json
  ```

### Backup the Key

- Keep a backup in your password manager
- If lost, revoke in Google Cloud Console and create a new one
- Each key can be tracked/revoked independently

---

## Next Steps

Would you like me to:

1. ✅ **Set up Gradle Play Publisher** (recommended)
   - I'll update your gradle files
   - You just need to create the service account

2. 🚀 **Set up GitHub Actions** (full automation)
   - Create workflow file
   - Configure secrets

3. 📖 **Just keep the manual process**
   - Use the RELEASE_CHECKLIST.md
   - Upload manually each time

Let me know which you prefer!



