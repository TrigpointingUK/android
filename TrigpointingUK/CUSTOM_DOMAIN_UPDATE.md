# Auth0 Custom Domain Migration

## Summary

The application has been updated to use the custom Auth0 domain `auth.trigpointing.uk` instead of the tenant domain `trigpointing.eu.auth0.com`.

## Changes Made

### Configuration Files Updated

1. **`app/src/main/res/values/auth0_config.xml`** (Production)
   - Domain: `auth.trigpointing.uk`
   - Redirect URI: `uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback`

2. **`app/src/debug/res/values/auth0_config.xml`** (Debug)
   - Domain: `auth.trigpointing.uk`
   - Redirect URI: `uk.trigpointing.android.debug://auth.trigpointing.uk/android/uk.trigpointing.android.debug/callback`

3. **`app/build.gradle`**
   - Updated manifestPlaceholders for both release and debug builds

4. **`app/src/main/java/uk/trigpointing/android/MainActivity.java`**
   - Updated callback host validation to use custom domain

## Why Custom Domains?

Using a custom domain provides:

1. **Branding**: Users see `auth.trigpointing.uk` instead of `trigpointing.eu.auth0.com`
2. **Trust**: Custom domain increases user trust and looks more professional
3. **Control**: You control the SSL certificate and DNS
4. **Consistency**: Same domain family across your entire infrastructure

## Auth0 SDK Compatibility

The Auth0 Android SDK **fully supports custom domains**. When you configure a custom domain:

- ✅ All authentication endpoints use the custom domain
- ✅ Token endpoints use the custom domain
- ✅ User info endpoints use the custom domain
- ✅ Logout endpoints use the custom domain
- ✅ JWKS (public key) endpoints use the custom domain

**You do NOT need to use the tenant domain (`trigpointing.eu.auth0.com`) for any API calls from the mobile app.**

## Auth0 Dashboard Configuration Required

Before testing, ensure these settings in your Auth0 Dashboard:

### 1. Custom Domain Setup
- Navigate to: **Branding > Custom Domains**
- Verify `auth.trigpointing.uk` status is **"Ready"**
- Ensure SSL certificate is valid and trusted

### 2. Application Callback URLs
Add both production and debug redirect URIs:
```
uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback
uk.trigpointing.android.debug://auth.trigpointing.uk/android/uk.trigpointing.android.debug/callback
```

### 3. Application Logout URLs
Add the same URLs as above.

### 4. Verify Application Settings
- Application Type: **Native**
- Token Endpoint Authentication Method: **None**
- Grant Types: **Authorization Code**, **Refresh Token**
- OIDC Conformant: **Yes**

## Testing

### Test Custom Domain Availability
```bash
# Verify DNS resolution
nslookup auth.trigpointing.uk

# Test HTTPS endpoint
curl https://auth.trigpointing.uk/.well-known/openid-configuration
```

### Test Universal Login URL
Open this URL in a browser to verify the login page loads:
```
https://auth.trigpointing.uk/authorize?client_id=IEBodjQvHMuDTS5vNVeve5j8YKQcYBN3&response_type=code&redirect_uri=uk.trigpointing.android://auth.trigpointing.uk/android/uk.trigpointing.android/callback&scope=openid%20profile%20email&audience=https://api.trigpointing.uk/
```

## Troubleshooting

### Issue: "Unable to resolve host"
**Cause**: DNS not configured or custom domain not active
**Solution**: 
1. Check Auth0 Dashboard > Branding > Custom Domains
2. Verify domain status is "Ready"
3. Test DNS: `nslookup auth.trigpointing.uk`

### Issue: SSL Certificate Error
**Cause**: Custom domain SSL certificate not properly configured
**Solution**: 
1. Check Auth0 Dashboard > Branding > Custom Domains
2. Verify SSL certificate is valid
3. Re-verify domain if needed

### Issue: Redirect URI Mismatch
**Cause**: Callback URLs not updated in Auth0 Dashboard
**Solution**: 
1. Add both production and debug redirect URIs to Auth0 Dashboard
2. Ensure exact match including scheme (`uk.trigpointing.android://`)

## Rollback Plan

If needed, you can revert to the tenant domain by changing these files:

1. `app/src/main/res/values/auth0_config.xml`
2. `app/src/debug/res/values/auth0_config.xml`
3. `app/build.gradle`
4. `app/src/main/java/uk/trigpointing/android/MainActivity.java`

Search for `auth.trigpointing.uk` and replace with `trigpointing.eu.auth0.com`.

## Build Verification

✅ Build successful after domain update
✅ No compilation errors
✅ APK ready for testing

## Next Steps

1. ✅ Update configuration files (DONE)
2. ✅ Verify build succeeds (DONE)
3. ⏳ Configure Auth0 Dashboard with new redirect URIs
4. ⏳ Test login flow with custom domain
5. ⏳ Test token refresh
6. ⏳ Test logout flow
7. ⏳ Verify API calls work with Auth0 tokens

