package uk.trigpointing.android.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.callback.Callback;
import com.auth0.android.provider.WebAuthProvider;
import com.auth0.android.result.Credentials;
import com.auth0.android.result.UserProfile;
import com.auth0.android.authentication.AuthenticationAPIClient;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import uk.trigpointing.android.R;
import uk.trigpointing.android.BuildConfig;

/**
 * Auth0 configuration and authentication manager
 * Handles Auth0 universal login, token management, and user profile retrieval
 */
public class Auth0Config {
    private static final String TAG = "Auth0Config";
    
    // Auth0 configuration loaded from resources
    private final String auth0Domain;
    private final String auth0ClientId;
    private final String auth0RedirectUri; // legacy; prefer buildRedirectUri()
    private final String auth0Audience;
    private final String auth0Connection;
    
    private final Auth0 auth0;
    private final Context context;
    
    public Auth0Config(Context context) {
        this.context = context;
        
        // Load configuration from resources
        this.auth0Domain = context.getString(R.string.auth0_domain);
        this.auth0ClientId = context.getString(R.string.auth0_client_id);
        this.auth0RedirectUri = context.getString(R.string.auth0_redirect_uri);
        this.auth0Audience = context.getString(R.string.auth0_audience);
        this.auth0Connection = context.getString(R.string.auth0_connection);
        
        this.auth0 = new Auth0(auth0ClientId, auth0Domain);
        
        Log.i(TAG, "Auth0Config initialized with domain: " + auth0Domain + ", clientId: " + auth0ClientId + ", connection: " + auth0Connection);
    }
    
    /**
     * Interface for Auth0 authentication callbacks
     */
    public interface Auth0Callback {
        void onSuccess(Credentials credentials, UserProfile userProfile);
        void onError(AuthenticationException error);
    }
    
    /**
     * Interface for Auth0 user profile callbacks
     */
    public interface UserProfileCallback {
        void onSuccess(UserProfile userProfile);
        void onError(AuthenticationException error);
    }
    
    /**
     * Interface for Auth0 logout callbacks
     */
    public interface LogoutCallback {
        void onSuccess();
        void onError(AuthenticationException error);
    }
    
    /**
     * Interface for Auth0 token refresh callbacks
     */
    public interface RefreshCallback {
        void onSuccess(Credentials credentials);
        void onError(AuthenticationException error);
    }
    
    /**
     * Start Auth0 universal login flow
     */
    public void login(Auth0Callback callback) {
        Log.i(TAG, "Starting Auth0 universal login");
        
        Uri redirectUri = Uri.parse(auth0RedirectUri);
        String scheme = redirectUri.getScheme();
        Log.i(TAG, "Using scheme: " + scheme + " with redirect: " + auth0RedirectUri);
        
        WebAuthProvider.login(auth0)
                .withScheme(scheme)
                .withScope("openid profile email offline_access")
                .withAudience(auth0Audience)
                .withConnection(auth0Connection)  // Use connection from resources
                .withRedirectUri(auth0RedirectUri)  // Use resource-defined redirect URI
                .start(context, new Callback<Credentials, AuthenticationException>() {
                    @Override
                    public void onSuccess(Credentials credentials) {
                        Log.i(TAG, "Auth0 login successful, getting user profile");
                        
                        // Get user profile after successful authentication
                        getUserProfile(credentials, new UserProfileCallback() {
                            @Override
                            public void onSuccess(UserProfile userProfile) {
                                String name = (userProfile != null && userProfile.getName() != null)
                                        ? userProfile.getName()
                                        : "Unknown";
                                Log.i(TAG, "User profile retrieved successfully: " + name);
                                callback.onSuccess(credentials, userProfile);
                            }
                            
                            @Override
                            public void onError(AuthenticationException error) {
                                Log.e(TAG, "Failed to get user profile", error);
                                FirebaseCrashlytics.getInstance().recordException(error);
                                callback.onError(error);
                            }
                        });
                    }
                    
                    @Override
                    public void onFailure(AuthenticationException error) {
                        Log.e(TAG, "Auth0 login failed", error);
                        FirebaseCrashlytics.getInstance().recordException(error);
                        callback.onError(error);
                    }
                });
    }
    
    /**
     * Get user profile from Auth0
     */
    public void getUserProfile(Credentials credentials, UserProfileCallback callback) {
        Log.i(TAG, "Getting user profile from Auth0");

        try {
            AuthenticationAPIClient client = new AuthenticationAPIClient(auth0);
            client.userInfo(credentials.getAccessToken())
                    .start(new Callback<UserProfile, AuthenticationException>() {
                        @Override
                        public void onSuccess(UserProfile profile) {
                            callback.onSuccess(profile);
                        }

                        @Override
                        public void onFailure(AuthenticationException error) {
                            Log.w(TAG, "UserInfo request failed; proceeding without profile", error);
                            // Do not fail the whole login if userinfo is unavailable; continue with tokens only
                            callback.onSuccess(null);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting user profile", e);
            // As a fallback, continue without a profile
            callback.onSuccess(null);
        }
    }
    
    /**
     * Logout from Auth0
     */
    public void logout(LogoutCallback callback) {
        Log.i(TAG, "Starting Auth0 logout");
        
        Uri redirectUri = Uri.parse(auth0RedirectUri);
        String scheme = redirectUri.getScheme();
        Log.i(TAG, "Using scheme for logout: " + scheme + " with returnTo: " + auth0RedirectUri);
        
        WebAuthProvider.logout(auth0)
                .withScheme(scheme)
                .withReturnToUrl(auth0RedirectUri)  // Add explicit return URL
                .start(context, new Callback<Void, AuthenticationException>() {
                    @Override
                    public void onSuccess(Void payload) {
                        Log.i(TAG, "Auth0 logout successful");
                        callback.onSuccess();
                    }
                    
                    @Override
                    public void onFailure(AuthenticationException error) {
                        Log.e(TAG, "Auth0 logout failed", error);
                        FirebaseCrashlytics.getInstance().recordException(error);
                        callback.onError(error);
                    }
                });
    }
    
    /**
     * Get the Auth0 instance for direct API calls
     */
    public Auth0 getAuth0() {
        return auth0;
    }
    
    /**
     * Get the redirect URI for Auth0
     */
    public String getRedirectUri() {
        return auth0RedirectUri;
    }
    
    /**
     * Get the Auth0 domain
     */
    public String getDomain() {
        return auth0Domain;
    }
    
    /**
     * Get the Auth0 client ID
     */
    public String getClientId() {
        return auth0ClientId;
    }
    
    /**
     * Get the Auth0 audience
     */
    public String getAudience() {
        return auth0Audience;
    }

    /**
     * Refresh Auth0 access token using refresh token
     */
    public void refreshToken(String refreshToken, RefreshCallback callback) {
        Log.i(TAG, "Refreshing Auth0 access token");
        
        try {
            AuthenticationAPIClient client = new AuthenticationAPIClient(auth0);
            client.renewAuth(refreshToken)
                    .addParameter("audience", auth0Audience)
                    .start(new Callback<Credentials, AuthenticationException>() {
                        @Override
                        public void onSuccess(Credentials credentials) {
                            Log.i(TAG, "Token refresh successful");
                            callback.onSuccess(credentials);
                        }
                        
                        @Override
                        public void onFailure(AuthenticationException error) {
                            Log.e(TAG, "Token refresh failed", error);
                            FirebaseCrashlytics.getInstance().recordException(error);
                            callback.onError(error);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing token", e);
            FirebaseCrashlytics.getInstance().recordException(e);
            callback.onError(new AuthenticationException(e.getMessage(), e));
        }
    }
    
    /**
     * Build redirect URI matching the manifest intent-filter and current applicationId.
     * Format: uk.trigpointing.android://{domain}/android/{applicationId}/callback
     */
    // buildRedirectUri() no longer used; keeping redirect from resources for clarity/config parity
}
