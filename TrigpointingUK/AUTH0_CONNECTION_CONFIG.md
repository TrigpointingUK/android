# Parameterized Auth0 Connection Configuration

## Summary

The Auth0 connection name is now parameterized by build variant, allowing different connections for production and debug environments.

## Configuration

### Production (Release)
**File**: `app/src/main/res/values/auth0_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="auth0_domain">auth.trigpointing.uk</string>
    <string name="auth0_client_id">IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3</string>
    <string name="auth0_redirect_uri">uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback</string>
    <string name="auth0_audience">https://api.trigpointing.uk/</string>
    <string name="auth0_connection">tuk-users</string>
</resources>
```

### Debug
**File**: `app/src/debug/res/values/auth0_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Debug build uses trigpointing.me domain -->
    <string name="auth0_domain">auth.trigpointing.me</string>
    <string name="auth0_client_id">5ioMqw42iotFnKs5jgDQ8a1WZ2LnqpTr</string>
    <string name="auth0_redirect_uri">uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback</string>
    <string name="auth0_audience">https://api.trigpointing.me/</string>
    <string name="auth0_connection">tme-users</string>
</resources>
```

## Implementation

### Auth0Config.java

**Added connection field**:
```java
private final String auth0Connection;
```

**Load from resources**:
```java
public Auth0Config(Context context) {
    // ... other config
    this.auth0Connection = context.getString(R.string.auth0_connection);
    
    Log.i(TAG, "Auth0Config initialized with domain: " + auth0Domain + 
              ", clientId: " + auth0ClientId + 
              ", connection: " + auth0Connection);
}
```

**Use in login**:
```java
WebAuthProvider.login(auth0)
    .withScheme(scheme)
    .withScope("openid profile email offline_access")
    .withAudience(auth0Audience)
    .withConnection(auth0Connection)  // Use connection from resources
    .withRedirectUri(auth0RedirectUri)
    .start(context, callbacks);
```

## Build Variant Summary

| Component | Production (Release) | Debug |
|-----------|---------------------|-------|
| **Auth0 Domain** | auth.trigpointing.uk | auth.trigpointing.me |
| **Auth0 Connection** | tuk-users | tme-users |
| **Client ID** | IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3 | 5ioMqw42iotFnKs5jgDQ8a1WZ2LnqpTr |
| **API Base URL** | https://api.trigpointing.uk/v1 | https://api.trigpointing.me/v1 |
| **Package Name** | uk.trigpointing.android | uk.trigpointing.android.debug |

## Auth0 Dashboard Requirements

### Production Auth0 (auth.trigpointing.uk)

**Connection**: `tuk-users`
- Must be enabled for the production application
- Type: Database Connection (Username-Password-Authentication style)

**Application Settings**:
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
- **Allowed Connections**: `tuk-users` (enabled)

### Debug Auth0 (auth.trigpointing.me)

**Connection**: `tme-users`
- Must be enabled for the debug application
- Type: Database Connection (Username-Password-Authentication style)

**Application Settings**:
- **Client ID**: 5ioMqw42iotFnKs5jgDQ8a1WZ2LnqpTr
- **Application Type**: Native
- **Allowed Callback URLs**:
  ```
  uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
  ```
- **Allowed Logout URLs**:
  ```
  uk.trigpointing.android.debug://auth.trigpointing.me/android/uk.trigpointing.android.debug/callback
  ```
- **Allowed Connections**: `tme-users` (enabled)

## How It Works

### Resource Overlay System

Android's build system automatically selects the right configuration:

1. **Debug Build**:
   ```
   Uses: app/src/debug/res/values/auth0_config.xml
   Connection: tme-users
   Domain: auth.trigpointing.me
   ```

2. **Release Build**:
   ```
   Uses: app/src/main/res/values/auth0_config.xml
   Connection: tuk-users
   Domain: auth.trigpointing.uk
   ```

### Login Flow

1. User taps "Login with Auth0"
2. `Auth0Config.login()` is called
3. Reads `auth0_connection` from resources (build-variant specific)
4. Calls Auth0 with `.withConnection(auth0Connection)`
5. Auth0 Universal Login page opens
6. Shows **only** the specified connection (`tuk-users` or `tme-users`)
7. User authenticates
8. App receives tokens

## Testing

### Debug Build

```bash
./gradlew installDebug
adb logcat | grep "Auth0Config"
```

Expected log output:
```
Auth0Config: Auth0Config initialized with domain: auth.trigpointing.me, 
             clientId: 5ioMqw42iotFnKs5jgDQ8a1WZ2LnqpTr, 
             connection: tme-users
```

### Release Build

```bash
./gradlew installRelease
adb logcat | grep "Auth0Config"
```

Expected log output:
```
Auth0Config: Auth0Config initialized with domain: auth.trigpointing.uk, 
             clientId: IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3, 
             connection: tuk-users
```

## Troubleshooting

### Error: "access denied" or Connection Not Found

**Cause**: The specified connection is not enabled for the application in Auth0 Dashboard.

**Fix**:
1. Go to Auth0 Dashboard for the appropriate environment
2. Navigate to: **Applications** → Your App → **Connections** tab
3. Ensure the connection (`tuk-users` or `tme-users`) is **enabled**
4. Check that the connection name matches exactly (case-sensitive)

### Error: Wrong Connection Being Used

**Check which build variant you're running**:
```bash
# Debug
adb shell pm list packages | grep debug
# Should show: uk.trigpointing.android.debug

# Release
adb shell pm list packages | grep -v debug | grep trigpointing
# Should show: uk.trigpointing.android
```

### Verify Connection Name in Auth0 Dashboard

1. **Production**: auth.trigpointing.uk
   - Go to: **Connections** → **Database** → Find connection name
   - Verify it's exactly `tuk-users`

2. **Debug**: auth.trigpointing.me
   - Go to: **Connections** → **Database** → Find connection name
   - Verify it's exactly `tme-users`

## Benefits

✅ **Environment Isolation**: Different user databases for prod/debug  
✅ **Automatic Selection**: Build system chooses correct connection  
✅ **No Code Changes**: Switch by changing build variant  
✅ **Cleaner Testing**: Test accounts separate from production  
✅ **Configuration Flexibility**: Easy to add more environments

## Adding More Connections

To add additional environments (e.g., staging):

1. Create `app/src/staging/res/values/auth0_config.xml`
2. Add connection name: `<string name="auth0_connection">staging-users</string>`
3. Configure Auth0 Dashboard with `staging-users` connection
4. Build with staging variant

The connection will automatically be used based on the build variant!

