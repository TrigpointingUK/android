package uk.trigpointing.android.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.auth0.android.result.Credentials;
import com.auth0.android.result.UserProfile;
import com.google.gson.Gson;

/**
 * Helper class to manage storage of Auth0 authentication data.
 * This class exclusively handles Auth0 authentication - no legacy passwords or tokens.
 */
public class AuthPreferences {
    private static final String TAG = "AuthPreferences";
    
    // Auth0 authentication preferences
    private static final String PREF_AUTH0_ACCESS_TOKEN = "auth0_access_token";
    private static final String PREF_AUTH0_ID_TOKEN = "auth0_id_token";
    private static final String PREF_AUTH0_REFRESH_TOKEN = "auth0_refresh_token";
    private static final String PREF_AUTH0_TOKEN_TYPE = "auth0_token_type";
    private static final String PREF_AUTH0_EXPIRES_AT = "auth0_expires_at";
    private static final String PREF_AUTH0_LOGIN_TIMESTAMP = "auth0_login_timestamp";
    private static final String PREF_AUTH0_USER_DATA = "auth0_user_data";
    private static final String PREF_AUTH0_USER_ID = "auth0_user_id";
    private static final String PREF_AUTH0_USER_NAME = "auth0_user_name";
    private static final String PREF_API_USER_DATA = "tuk_api_user_data";
    private static final String PREF_API_USER_ID = "tuk_api_user_id";

    private final SharedPreferences preferences;
    private final Gson gson;

    public AuthPreferences(Context context) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.gson = new Gson();
    }

    /**
     * Store Auth0 authentication data from successful login
     */
    public void storeAuth0Data(Credentials credentials, UserProfile userProfile) {
        String name = (userProfile != null && userProfile.getName() != null) ? userProfile.getName() : "Unknown";
        Log.i(TAG, "Storing Auth0 authentication data for user: " + name);
        
        SharedPreferences.Editor editor = preferences.edit();
        
        // Store token data
        editor.putString(PREF_AUTH0_ACCESS_TOKEN, credentials.getAccessToken());
        editor.putString(PREF_AUTH0_ID_TOKEN, credentials.getIdToken());
        if (credentials.getRefreshToken() != null) {
            editor.putString(PREF_AUTH0_REFRESH_TOKEN, credentials.getRefreshToken());
        }
        editor.putString(PREF_AUTH0_TOKEN_TYPE, credentials.getType());
        
        // Store expiration time (absolute timestamp)
        if (credentials.getExpiresAt() != null) {
            editor.putLong(PREF_AUTH0_EXPIRES_AT, credentials.getExpiresAt().getTime());
        }
        
        editor.putLong(PREF_AUTH0_LOGIN_TIMESTAMP, System.currentTimeMillis());
        
        // Store user profile data
        if (userProfile != null) {
            editor.putString(PREF_AUTH0_USER_ID, userProfile.getId());
            if (userProfile.getName() != null) {
                editor.putString(PREF_AUTH0_USER_NAME, userProfile.getName());
            }
            
            // Store full user profile as JSON
            String userJson = gson.toJson(userProfile);
            editor.putString(PREF_AUTH0_USER_DATA, userJson);
        }
        
        editor.apply();
        Log.i(TAG, "Auth0 authentication data stored successfully");
    }
    
    /**
     * Store the Trigpointing API user profile returned by /v1/users/me
     */
    public void storeApiUser(TrigApiClient.UserProfile apiUser) {
        SharedPreferences.Editor editor = preferences.edit();
        if (apiUser == null) {
            editor.remove(PREF_API_USER_DATA);
            editor.remove(PREF_API_USER_ID);
        } else {
            editor.putString(PREF_API_USER_DATA, gson.toJson(apiUser));
            editor.putInt(PREF_API_USER_ID, apiUser.id);
            Log.i(TAG, "Stored API user profile for user id " + apiUser.id);
        }
        editor.apply();
    }
    
    /**
     * Retrieve the stored Trigpointing API user profile, if available
     */
    public TrigApiClient.UserProfile getApiUser() {
        String userJson = preferences.getString(PREF_API_USER_DATA, null);
        if (userJson != null) {
            try {
                return gson.fromJson(userJson, TrigApiClient.UserProfile.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse API user profile JSON", e);
            }
        }
        return null;
    }
    
    /**
     * Retrieve the stored Trigpointing API user id, if available
     */
    public Integer getApiUserId() {
        if (preferences.contains(PREF_API_USER_ID)) {
            int id = preferences.getInt(PREF_API_USER_ID, 0);
            return id > 0 ? id : null;
        }
        return null;
    }
    
    /**
     * Clear all stored authentication data
     */
    public void clearAuthData() {
        Log.i(TAG, "Clearing all authentication data");
        
        SharedPreferences.Editor editor = preferences.edit();
        
        // Clear Auth0 data
        editor.remove(PREF_AUTH0_ACCESS_TOKEN);
        editor.remove(PREF_AUTH0_ID_TOKEN);
        editor.remove(PREF_AUTH0_REFRESH_TOKEN);
        editor.remove(PREF_AUTH0_TOKEN_TYPE);
        editor.remove(PREF_AUTH0_EXPIRES_AT);
        editor.remove(PREF_AUTH0_LOGIN_TIMESTAMP);
        editor.remove(PREF_AUTH0_USER_DATA);
        editor.remove(PREF_AUTH0_USER_ID);
        editor.remove(PREF_AUTH0_USER_NAME);
        editor.remove(PREF_API_USER_DATA);
        editor.remove(PREF_API_USER_ID);
        
        // Use commit() instead of apply() to ensure synchronous clearing before UI updates
        editor.commit();
        Log.i(TAG, "All authentication data cleared (synchronous)");
    }
    
    /**
     * Get stored Auth0 access token
     */
    public String getAuth0AccessToken() {
        return preferences.getString(PREF_AUTH0_ACCESS_TOKEN, null);
    }
    
    /**
     * Get stored Auth0 ID token
     */
    public String getAuth0IdToken() {
        return preferences.getString(PREF_AUTH0_ID_TOKEN, null);
    }
    
    /**
     * Get stored Auth0 refresh token
     */
    public String getAuth0RefreshToken() {
        return preferences.getString(PREF_AUTH0_REFRESH_TOKEN, null);
    }
    
    /**
     * Get stored Auth0 token type
     */
    public String getAuth0TokenType() {
        return preferences.getString(PREF_AUTH0_TOKEN_TYPE, null);
    }
    
    /**
     * Get the absolute expiration timestamp (milliseconds since epoch)
     */
    public long getAuth0ExpiresAt() {
        return preferences.getLong(PREF_AUTH0_EXPIRES_AT, 0);
    }
    
    /**
     * Get the timestamp when the user logged in via Auth0
     */
    public long getAuth0LoginTimestamp() {
        return preferences.getLong(PREF_AUTH0_LOGIN_TIMESTAMP, 0);
    }
    
    /**
     * Get stored Auth0 user ID
     */
    public String getAuth0UserId() {
        return preferences.getString(PREF_AUTH0_USER_ID, null);
    }
    
    /**
     * Get stored Auth0 user name
     */
    public String getAuth0UserName() {
        return preferences.getString(PREF_AUTH0_USER_NAME, null);
    }
    
    /**
     * Get stored Auth0 user profile
     */
    public UserProfile getAuth0UserProfile() {
        String userJson = preferences.getString(PREF_AUTH0_USER_DATA, null);
        if (userJson != null) {
            try {
                return gson.fromJson(userJson, UserProfile.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse Auth0 user profile JSON", e);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Check if user is currently logged in with valid Auth0 token
     */
    public boolean isAuth0LoggedIn() {
        String token = getAuth0AccessToken();
        if (token == null || token.isEmpty()) {
            return false;
        }

        // Check if token has expired
        long expiresAt = getAuth0ExpiresAt();
        if (expiresAt > 0) {
            long currentTime = System.currentTimeMillis();
            return currentTime < expiresAt;
        }

        // If we don't have expiration info, assume logged in if we have a token
        return true;
    }
    
    /**
     * Check if the Auth0 token should be refreshed (expires within 5 minutes)
     */
    public boolean shouldRefreshAuth0Token() {
        String token = getAuth0AccessToken();
        if (token == null || token.isEmpty()) {
            return false;
        }

        long expiresAt = getAuth0ExpiresAt();
        if (expiresAt > 0) {
            long currentTime = System.currentTimeMillis();
            long fiveMinutesFromNow = currentTime + (5 * 60 * 1000L); // 5 minutes in milliseconds
            
            // Refresh if token expires within 5 minutes
            return fiveMinutesFromNow >= expiresAt;
        }

        // If we don't have expiration info, don't refresh
        return false;
    }
    
    /**
     * Get display name for the user
     */
    public String getDisplayName() {
        TrigApiClient.UserProfile apiUser = getApiUser();
        if (apiUser != null && apiUser.name != null && !apiUser.name.isEmpty()) {
            return apiUser.name;
        }
        String name = getAuth0UserName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        
        UserProfile profile = getAuth0UserProfile();
        if (profile != null && profile.getName() != null) {
            return profile.getName();
        }
        
        return "User";
    }
    
    /**
     * Get display name with user ID for developer mode
     */
    public String getDisplayNameWithId() {
        String name = getDisplayName();
        Integer apiUserId = getApiUserId();
        if (apiUserId != null) {
            return name + " (#" + apiUserId + ")";
        }
        String userId = getAuth0UserId();
        if (userId != null && !userId.isEmpty()) {
            return name + " (" + userId + ")";
        }
        return name;
    }
    
    /**
     * Check if user is logged in (primary method for app-wide use)
     */
    public boolean isLoggedIn() {
        return isAuth0LoggedIn();
    }
    
    /**
     * Get user ID (for compatibility with existing code)
     */
    public String getUserId() {
        Integer apiUserId = getApiUserId();
        if (apiUserId != null) {
            return String.valueOf(apiUserId);
        }
        return getAuth0UserId();
    }
}
