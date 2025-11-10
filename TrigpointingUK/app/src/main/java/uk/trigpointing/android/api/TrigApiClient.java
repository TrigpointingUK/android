package uk.trigpointing.android.api;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import uk.trigpointing.android.BuildConfig;

/**
 * API client for write operations using Auth0 authentication.
 * This client handles all create/update/delete operations that require authentication.
 */
public class TrigApiClient {
    private static final String TAG = "TrigApiClient";
    // Use BuildConfig to allow different API URLs for debug/release builds
    private static final String API_BASE_HOST = BuildConfig.TRIG_API_BASE;
    private static final String API_BASE_URL = API_BASE_HOST + "/v1";
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final AuthPreferences authPreferences;
    private final Auth0Config auth0Config;

    public static String getApiBaseHost() {
        return API_BASE_HOST;
    }
    
    public static String getApiBaseUrl() {
        return API_BASE_URL;
    }
    
    public TrigApiClient(Context context) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd")
                .create();
        this.authPreferences = new AuthPreferences(context);
        this.auth0Config = new Auth0Config(context);
    }

    /**
     * Generic callback interface for API operations
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    /**
     * Ensure we have a valid Auth0 access token, refreshing if necessary
     */
    private CompletableFuture<String> ensureValidToken() {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // Check if we have a valid token
        if (authPreferences.isAuth0LoggedIn() && !authPreferences.shouldRefreshAuth0Token()) {
            String token = authPreferences.getAuth0AccessToken();
            if (token != null && !token.isEmpty()) {
                future.complete(token);
                return future;
            }
        }
        
        // Need to refresh token
        String refreshToken = authPreferences.getAuth0RefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            future.completeExceptionally(new Exception("No refresh token available. Please log in again."));
            return future;
        }
        
        Log.i(TAG, "Refreshing Auth0 access token");
        auth0Config.refreshToken(refreshToken, new Auth0Config.RefreshCallback() {
            @Override
            public void onSuccess(com.auth0.android.result.Credentials credentials) {
                Log.i(TAG, "Token refresh successful");
                authPreferences.storeAuth0Data(credentials, null);
                future.complete(credentials.getAccessToken());
            }
            
            @Override
            public void onError(com.auth0.android.authentication.AuthenticationException error) {
                Log.e(TAG, "Token refresh failed", error);
                future.completeExceptionally(new Exception("Token refresh failed: " + error.getMessage()));
            }
        });
        
        return future;
    }

    /**
     * Create a new log entry
     */
    public void createLog(LogCreateRequest request, ApiCallback<LogResponse> callback) {
        Log.i(TAG, "createLog: Creating log for trig " + request.trigId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String jsonBody = gson.toJson(request.toApiPayload());
                    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                    
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/logs?trig_id=" + request.trigId)
                            .post(body)
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Content-Type", "application/json")
                            .build();
                    
                    Log.d(TAG, "createLog: Sending POST to " + API_BASE_URL + "/logs");
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "createLog: Response code: " + response.code());
                        
                        if (response.isSuccessful()) {
                            LogResponse logResponse = gson.fromJson(responseBody, LogResponse.class);
                            return new ApiResult<>(true, logResponse, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<LogResponse>(false, null, errorMsg);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "createLog: Network error", e);
                    return new ApiResult<LogResponse>(false, null, "Network error: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "createLog: Unexpected error", e);
                    return new ApiResult<LogResponse>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            Log.e(TAG, "createLog: Exception", throwable);
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Update an existing log entry
     */
    public void updateLog(int logId, LogUpdateRequest request, ApiCallback<LogResponse> callback) {
        Log.i(TAG, "updateLog: Updating log " + logId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String jsonBody = gson.toJson(request.toApiPayload());
                    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                    
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/logs/" + logId)
                            .patch(body)
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Content-Type", "application/json")
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        
                        if (response.isSuccessful()) {
                            LogResponse logResponse = gson.fromJson(responseBody, LogResponse.class);
                            return new ApiResult<>(true, logResponse, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<LogResponse>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<LogResponse>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Delete a log entry
     */
    public void deleteLog(int logId, ApiCallback<Void> callback) {
        Log.i(TAG, "deleteLog: Deleting log " + logId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/logs/" + logId)
                            .delete()
                            .addHeader("Authorization", "Bearer " + token)
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        if (response.isSuccessful() || response.code() == 204) {
                            return new ApiResult<Void>(true, null, null);
                        } else {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<Void>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<Void>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(null);
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Upload a photo
     */
    public void uploadPhoto(PhotoUploadRequest request, ApiCallback<PhotoResponse> callback) {
        Log.i(TAG, "uploadPhoto: Uploading photo for log " + request.logId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File photoFile = new File(request.photoPath);
                    if (!photoFile.exists()) {
                        return new ApiResult<PhotoResponse>(false, null, "Photo file not found: " + request.photoPath);
                    }
                    
                    RequestBody photoBody = RequestBody.create(photoFile, MediaType.parse("image/jpeg"));
                    
                    MultipartBody.Builder builder = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", photoFile.getName(), photoBody)
                            .addFormDataPart("caption", request.caption != null ? request.caption : "")
                            .addFormDataPart("text_desc", request.description != null ? request.description : "")
                            .addFormDataPart("type", request.type)
                            .addFormDataPart("license", request.license);
                    
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/photos?log_id=" + request.logId)
                            .post(builder.build())
                            .addHeader("Authorization", "Bearer " + token)
                            .build();
                    
                    Log.d(TAG, "uploadPhoto: Uploading to " + API_BASE_URL + "/photos");
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        
                        if (response.isSuccessful()) {
                            PhotoResponse photoResponse = gson.fromJson(responseBody, PhotoResponse.class);
                            return new ApiResult<>(true, photoResponse, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<PhotoResponse>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "uploadPhoto: Error", e);
                    return new ApiResult<PhotoResponse>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Update photo metadata
     */
    public void updatePhoto(int photoId, PhotoUpdateRequest request, ApiCallback<PhotoResponse> callback) {
        Log.i(TAG, "updatePhoto: Updating photo " + photoId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String jsonBody = gson.toJson(request.toApiPayload());
                    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                    
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/photos/" + photoId)
                            .patch(body)
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Content-Type", "application/json")
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        
                        if (response.isSuccessful()) {
                            PhotoResponse photoResponse = gson.fromJson(responseBody, PhotoResponse.class);
                            return new ApiResult<>(true, photoResponse, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<PhotoResponse>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<PhotoResponse>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Delete a photo
     */
    public void deletePhoto(int photoId, ApiCallback<Void> callback) {
        Log.i(TAG, "deletePhoto: Deleting photo " + photoId);
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/photos/" + photoId)
                            .delete()
                            .addHeader("Authorization", "Bearer " + token)
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        if (response.isSuccessful() || response.code() == 204) {
                            return new ApiResult<Void>(true, null, null);
                        } else {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<Void>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<Void>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(null);
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Get current user profile
     */
    public void getCurrentUser(ApiCallback<UserProfile> callback) {
        Log.i(TAG, "getCurrentUser: Fetching current user profile");
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/users/me?include=stats,prefs")
                            .get()
                            .addHeader("Authorization", "Bearer " + token)
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        
                        if (response.isSuccessful()) {
                            UserProfile userProfile = gson.fromJson(responseBody, UserProfile.class);
                            return new ApiResult<>(true, userProfile, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<UserProfile>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<UserProfile>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Update current user profile
     */
    public void updateCurrentUser(UserProfileUpdateRequest request, ApiCallback<UserProfile> callback) {
        Log.i(TAG, "updateCurrentUser: Updating user profile");
        
        ensureValidToken().thenCompose(token -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String jsonBody = gson.toJson(request.toApiPayload());
                    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                    
                    Request httpRequest = new Request.Builder()
                            .url(API_BASE_URL + "/users/me")
                            .patch(body)
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Content-Type", "application/json")
                            .build();
                    
                    try (Response response = httpClient.newCall(httpRequest).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        
                        if (response.isSuccessful()) {
                            UserProfile userProfile = gson.fromJson(responseBody, UserProfile.class);
                            return new ApiResult<>(true, userProfile, null);
                        } else {
                            String errorMsg = parseErrorMessage(responseBody, response.code());
                            return new ApiResult<UserProfile>(false, null, errorMsg);
                        }
                    }
                } catch (Exception e) {
                    return new ApiResult<UserProfile>(false, null, "Error: " + e.getMessage());
                }
            });
        }).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    public void listTrigLogs(long trigId, int limit, String pageUrl, ApiCallback<TrigLogPage> callback) {
        ensureValidToken().thenCompose(token -> CompletableFuture.supplyAsync(() -> {
            try {
                String url;
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    url = BuildConfig.TRIG_API_BASE + pageUrl;
                } else {
                    url = API_BASE_URL + "/trigs/" + trigId + "/logs?limit=" + limit + "&skip=0";
                }
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Authorization", "Bearer " + token)
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        TrigLogPage trigLogPage = parseTrigLogPage(responseBody);
                        return new ApiResult<>(true, trigLogPage, null);
                    } else {
                        String errorMsg = parseErrorMessage(responseBody, response.code());
                        return new ApiResult<TrigLogPage>(false, null, errorMsg);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "listTrigLogs: Unexpected error", e);
                return new ApiResult<TrigLogPage>(false, null, "Error: " + e.getMessage());
            }
        })).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    private TrigLogPage parseTrigLogPage(String json) {
        JsonElement element = gson.fromJson(json, JsonElement.class);
        TrigLogPage page = new TrigLogPage();
        page.data = new java.util.ArrayList<>();
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                JsonArray items = obj.getAsJsonArray("items");
                for (JsonElement item : items) {
                    TrigLog log = gson.fromJson(item, TrigLog.class);
                    page.data.add(log);
                }
            }
            if (obj.has("pagination") && obj.get("pagination").isJsonObject()) {
                page.pagination = gson.fromJson(obj.get("pagination"), Pagination.class);
            }
            if (obj.has("links") && obj.get("links").isJsonObject()) {
                page.links = gson.fromJson(obj.get("links"), Links.class);
            }
        }
        return page;
    }

    public void listTrigPhotos(long trigId, int limit, String pageUrl, ApiCallback<TrigPhotoPage> callback) {
        ensureValidToken().thenCompose(token -> CompletableFuture.supplyAsync(() -> {
            try {
                String url;
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    url = BuildConfig.TRIG_API_BASE + pageUrl;
                } else {
                    url = API_BASE_URL + "/trigs/" + trigId + "/photos?limit=" + limit + "&skip=0";
                }
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Authorization", "Bearer " + token)
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        TrigPhotoPage photoPage = parseTrigPhotoPage(responseBody);
                        return new ApiResult<>(true, photoPage, null);
                    } else {
                        String errorMsg = parseErrorMessage(responseBody, response.code());
                        return new ApiResult<TrigPhotoPage>(false, null, errorMsg);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "listTrigPhotos: Unexpected error", e);
                return new ApiResult<TrigPhotoPage>(false, null, "Error: " + e.getMessage());
            }
        })).thenAccept(result -> {
            if (result.isSuccess()) {
                callback.onSuccess(result.getData());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Authentication error: " + throwable.getMessage());
            return null;
        });
    }

    private TrigPhotoPage parseTrigPhotoPage(String json) {
        JsonElement element = gson.fromJson(json, JsonElement.class);
        TrigPhotoPage page = new TrigPhotoPage();
        page.items = new java.util.ArrayList<>();
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                JsonArray items = obj.getAsJsonArray("items");
                for (JsonElement item : items) {
                    TrigPhotoItem photo = gson.fromJson(item, TrigPhotoItem.class);
                    page.items.add(photo);
                }
            }
            if (obj.has("pagination") && obj.get("pagination").isJsonObject()) {
                page.pagination = gson.fromJson(obj.get("pagination"), Pagination.class);
            }
            if (obj.has("links") && obj.get("links").isJsonObject()) {
                page.links = gson.fromJson(obj.get("links"), Links.class);
            }
        }
        return page;
    }

    /**
     * Parse error message from API response
     */
    private String parseErrorMessage(String responseBody, int statusCode) {
        try {
            Log.w(TAG, "API error " + statusCode + " body: " + responseBody);
            ErrorResponse errorResponse = gson.fromJson(responseBody, ErrorResponse.class);
            if (errorResponse != null && errorResponse.getDetail() != null) {
                return errorResponse.getDetail();
            }
            // Attempt to parse FastAPI-style error list
            JsonElement element = gson.fromJson(responseBody, JsonElement.class);
            if (element != null && element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("detail")) {
                    JsonElement detail = obj.get("detail");
                    if (detail.isJsonArray() && detail.getAsJsonArray().size() > 0) {
                        JsonObject first = detail.getAsJsonArray().get(0).getAsJsonObject();
                        if (first.has("msg")) {
                            return first.get("msg").getAsString();
                        }
                    } else if (detail.isJsonPrimitive()) {
                        return detail.getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse API error body", e);
        }
        return "API error: HTTP " + statusCode;
    }

    /**
     * Helper class to hold API results
     */
    private static class ApiResult<T> {
        private final boolean success;
        private final T data;
        private final String errorMessage;

        public ApiResult(boolean success, T data, String errorMessage) {
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public T getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    // Request/Response models

    public static class LogCreateRequest {
        public long trigId;
        public String date;        // yyyy-MM-dd
        public String time;        // HH:mm:ss
        public Integer osgbEastings;
        public Integer osgbNorthings;
        public String osgbGridref;
        public String fbNumber;
        public String condition;    // Single character: G, D, P, etc.
        public String comment;
        public Integer score;       // 0-10
        public String source;       // W = web/android

        public java.util.Map<String, Object> toApiPayload() {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("date", date);
            payload.put("time", time);
            if (osgbEastings != null) payload.put("osgb_eastings", osgbEastings);
            if (osgbNorthings != null) payload.put("osgb_northings", osgbNorthings);
            if (osgbGridref != null) payload.put("osgb_gridref", osgbGridref);
            payload.put("fb_number", fbNumber != null ? fbNumber : "");
            payload.put("condition", condition);
            payload.put("comment", comment != null ? comment : "");
            payload.put("score", score != null ? score : 0);
            payload.put("source", source != null ? source : "W");
            return payload;
        }
    }

    public static class LogUpdateRequest {
        public String date;
        public String time;
        public Integer osgbEastings;
        public Integer osgbNorthings;
        public String osgbGridref;
        public String fbNumber;
        public String condition;
        public String comment;
        public Integer score;
        public String source;

        public java.util.Map<String, Object> toApiPayload() {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            if (date != null) payload.put("date", date);
            if (time != null) payload.put("time", time);
            if (osgbEastings != null) payload.put("osgb_eastings", osgbEastings);
            if (osgbNorthings != null) payload.put("osgb_northings", osgbNorthings);
            if (osgbGridref != null) payload.put("osgb_gridref", osgbGridref);
            if (fbNumber != null) payload.put("fb_number", fbNumber);
            if (condition != null) payload.put("condition", condition);
            if (comment != null) payload.put("comment", comment);
            if (score != null) payload.put("score", score);
            if (source != null) payload.put("source", source);
            return payload;
        }
    }

    public static class LogResponse {
        public int id;
        public int trig_id;
        public int user_id;
        public String date;
        public String time;
        public Integer osgb_eastings;
        public Integer osgb_northings;
        public String osgb_gridref;
        public String fb_number;
        public String condition;
        public String comment;
        public int score;
        public String source;
        public String trig_name;
        public String user_name;
    }

    public static class PhotoUploadRequest {
        public long logId;
        public String photoPath;
        public String caption;
        public String description;
        public String type;        // T, F, L, P, O
        public String license;     // Y, C, N
    }

    public static class PhotoUpdateRequest {
        public String caption;
        public String description;
        public String type;
        public String license;

        public java.util.Map<String, Object> toApiPayload() {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            if (caption != null) payload.put("caption", caption);
            if (description != null) payload.put("text_desc", description);
            if (type != null) payload.put("type", type);
            if (license != null) payload.put("license", license);
            return payload;
        }
    }

    public static class PhotoResponse {
        public int id;
        public int log_id;
        public int user_id;
        public String type;
        public int filesize;
        public int height;
        public int width;
        public int icon_filesize;
        public int icon_height;
        public int icon_width;
        public String caption;
        public String text_desc;
        public String license;
        public String photo_url;
        public String icon_url;
        public String user_name;
        public Integer trig_id;
        public String trig_name;
        public String log_date;
    }

    public static class UserProfile {
        public int id;
        public String name;
        public String firstname;
        public String surname;
        public String homepage;
        public String about;
        public String member_since;
        public String auth0_user_id;
        public UserStats stats;
        public UserPrefs prefs;
    }

    public static class UserStats {
        public int total_logs;
        public int total_trigs_logged;
        public int total_photos;
    }

    public static class UserPrefs {
        public int status_max;
        public String distance_ind;
        public String public_ind;
        public String online_map_type;
        public String online_map_type2;
        public String email;
        public String email_valid;
    }

    public static class UserProfileUpdateRequest {
        public String name;
        public String email;
        public String firstname;
        public String surname;
        public String homepage;
        public String about;
        public Integer status_max;
        public String distance_ind;
        public String public_ind;
        public String online_map_type;
        public String online_map_type2;

        public java.util.Map<String, Object> toApiPayload() {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            if (name != null) payload.put("name", name);
            if (email != null) payload.put("email", email);
            if (firstname != null) payload.put("firstname", firstname);
            if (surname != null) payload.put("surname", surname);
            if (homepage != null) payload.put("homepage", homepage);
            if (about != null) payload.put("about", about);
            if (status_max != null) payload.put("status_max", status_max);
            if (distance_ind != null) payload.put("distance_ind", distance_ind);
            if (public_ind != null) payload.put("public_ind", public_ind);
            if (online_map_type != null) payload.put("online_map_type", online_map_type);
            if (online_map_type2 != null) payload.put("online_map_type2", online_map_type2);
            return payload;
        }
    }

    public static class TrigLogPage {
        public java.util.List<TrigLog> data;
        public Pagination pagination;
        public Links links;
    }

    public static class TrigLog {
        public int id;
        public int trig_id;
        public String date;
        public String condition;
        public String comment;
        public String user_name;
    }

    public static class Pagination {
        public int total;
        public int limit;
        public int offset;
        public boolean has_more;
    }

    public static class Links {
        public String self;
        public String next;
        public String prev;
    }

    public static class TrigPhotoPage {
        public java.util.List<TrigPhotoItem> items;
        public Pagination pagination;
        public Links links;
    }

    public static class TrigPhotoItem {
        public int id;
        public long log_id;
        public long user_id;
        public String type;
        public long filesize;
        public int height;
        public int width;
        public long icon_filesize;
        public int icon_height;
        public int icon_width;
        public String name;
        public String text_desc;
        public String public_ind;
        public String photo_url;
        public String icon_url;
        public String user_name;
        public long trig_id;
        public String trig_name;
        public String log_date;
    }
}

