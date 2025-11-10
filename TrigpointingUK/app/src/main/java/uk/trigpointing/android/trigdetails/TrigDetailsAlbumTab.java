package uk.trigpointing.android.trigdetails;

import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.widget.TextView;
import android.widget.Toast;
import uk.trigpointing.android.api.AuthPreferences;
import uk.trigpointing.android.api.TrigApiClient;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.BaseTabActivity;
import uk.trigpointing.android.common.DisplayBitmapActivity;
import uk.trigpointing.android.types.TrigPhoto;

public class TrigDetailsAlbumTab extends BaseTabActivity {
    private long mTrigId;
    private static final String TAG="TrigDetailsAlbumTab";
    private ArrayList<TrigPhoto>     mTrigPhotos;
    private TrigDetailsAlbumGridAdapter mGridAdapter;
    private TextView                 mEmptyView;
    private AuthPreferences authPreferences;
    private TrigApiClient trigApiClient;

    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trigalbum);
        
        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
        if (extras == null) {return;}
        mTrigId = extras.getLong(DbHelper.TRIG_ID);
        Log.i(TAG, "Trig_id = "+mTrigId);

        authPreferences = new AuthPreferences(this);
        trigApiClient = new TrigApiClient(this);

        // Set up grid RecyclerView similar to OS Map tab
        RecyclerView recycler = findViewById(R.id.trigalbum_recycler);
        mEmptyView = findViewById(android.R.id.empty);
        mTrigPhotos = new ArrayList<TrigPhoto>();
        mGridAdapter = new TrigDetailsAlbumGridAdapter(this, mTrigPhotos);
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int columns = Math.max(2, Math.min(3, screenWidth / 500));
        recycler.setLayoutManager(new GridLayoutManager(this, columns));
        recycler.setNestedScrollingEnabled(false);
        recycler.setAdapter(mGridAdapter);
        int spacingPx = dpToPx(16);
        recycler.addItemDecoration(new GridSpacingItemDecoration(columns, spacingPx, false));

        mGridAdapter.setOnItemClickListener(new TrigDetailsAlbumGridAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                String url = mTrigPhotos.get(position).getPhotoURL();
                Intent i = new Intent(TrigDetailsAlbumTab.this, DisplayBitmapActivity.class);
                i.putExtra("URL", url);
                Log.i(TAG, "Clicked photo at URL: " + url);
                startActivity(i);
            }
        });

        // get list of photos
        populatePhotos(false);        
    }
    
    
    protected void onListItemClick(android.widget.ListView l, View v, int position, long id) {}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.trigdetailsmenu, menu);
        return result;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.refresh) {
            Log.i(TAG, "refresh");
            refreshAlbumFromParent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void populatePhotos(boolean refresh) {
        mEmptyView.setText(R.string.downloadingPhotos);
        if (!authPreferences.isLoggedIn()) {
            Toast.makeText(this, R.string.toastPleaseLogin, Toast.LENGTH_LONG).show();
            mTrigPhotos.clear();
            mGridAdapter.notifyDataSetChanged();
            mEmptyView.setText(R.string.noPhotos);
            return;
        }
        mTrigPhotos.clear();
        mGridAdapter.notifyDataSetChanged();
        fetchTrigPhotosPage(null);
    }

    private void fetchTrigPhotosPage(String nextLink) {
        trigApiClient.listTrigPhotos(mTrigId, 100, nextLink, new TrigApiClient.ApiCallback<TrigApiClient.TrigPhotoPage>() {
            @Override
            public void onSuccess(TrigApiClient.TrigPhotoPage result) {
                runOnUiThread(() -> {
                    if (result != null && result.items != null) {
                        for (TrigApiClient.TrigPhotoItem item : result.items) {
                            TrigPhoto tp = new TrigPhoto(
                                    item.name != null ? item.name : "",
                                    item.text_desc != null ? item.text_desc : "",
                                    item.photo_url,
                                    item.icon_url,
                                    item.user_name,
                                    item.log_date
                            );
                            tp.setSubject(item.type);
                            tp.setIspublic("Y".equalsIgnoreCase(item.public_ind));
                            tp.setLogID(item.log_id);
                            mTrigPhotos.add(tp);
                        }
                        mGridAdapter.notifyDataSetChanged();
                        if (result.pagination != null && result.pagination.total > 0) {
                            mEmptyView.setVisibility(View.GONE);
                        } else {
                            mEmptyView.setVisibility(View.VISIBLE);
                            mEmptyView.setText(R.string.noPhotos);
                        }
                        if (result.links != null && result.links.next != null && !result.links.next.isEmpty()) {
                            fetchTrigPhotosPage(result.links.next);
                        }
                    } else {
                        mEmptyView.setVisibility(View.VISIBLE);
                        mEmptyView.setText(R.string.noPhotos);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load trig photos: " + errorMessage);
                    Toast.makeText(TrigDetailsAlbumTab.this, getString(R.string.error_loading_photos, errorMessage), Toast.LENGTH_LONG).show();
                    if (mTrigPhotos.isEmpty()) {
                        mEmptyView.setVisibility(View.VISIBLE);
                        mEmptyView.setText(R.string.noPhotos);
                    }
                });
            }
        });
    }

    public void refreshAlbumFromParent() {
        try {
            if (mGridAdapter != null) {
                mGridAdapter.clearImageCaches();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear image cache", e);
        }

        // Recreate adapter to clear any in-memory caches
        RecyclerView recycler = findViewById(R.id.trigalbum_recycler);
        if (recycler != null) {
            mGridAdapter = new TrigDetailsAlbumGridAdapter(this, mTrigPhotos);
            recycler.setAdapter(mGridAdapter);
            mGridAdapter.setOnItemClickListener(new TrigDetailsAlbumGridAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(int position) {
                    String url = mTrigPhotos.get(position).getPhotoURL();
                    Intent i = new Intent(TrigDetailsAlbumTab.this, DisplayBitmapActivity.class);
                    i.putExtra("URL", url);
                    Log.i(TAG, "Clicked photo at URL: " + url);
                    startActivity(i);
                }
            });
        }

        // Force reload of photo list and thumbnails
        populatePhotos(true);
    }



    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // Even grid spacing decoration similar to OS Map tab
    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, android.view.View view,
                RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;
            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }

    
}