package uk.trigpointing.android.trigdetails;

import java.util.ArrayList;
import java.util.Locale;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.BaseTabActivity;
import uk.trigpointing.android.common.StringLoader;
import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.TrigLog;
import uk.trigpointing.android.api.AuthPreferences;
import uk.trigpointing.android.api.TrigApiClient;
import android.widget.Toast;

public class TrigDetailsLoglistTab extends BaseTabActivity {
    private static final String TAG="TrigDetailsLoglistTab";
    
    private long                         mTrigId;
    private StringLoader                 mStrLoader;
    private ArrayList<TrigLog>             mTrigLogs;
    private TrigDetailsLoglistAdapter     mTrigLogsAdapter;
    private TextView                     mEmptyView;
    private ListView                     mListView;
    private AuthPreferences authPreferences;
    private TrigApiClient trigApiClient;


    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.triglogs);
        
        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
        if (extras == null) {return;}
        mTrigId = extras.getLong(DbHelper.TRIG_ID);
        Log.i(TAG, "Trig_id = "+mTrigId);

        // Find ListView and set up adapter
        mListView = findViewById(android.R.id.list);
        mTrigLogs = new ArrayList<TrigLog>();
        mTrigLogsAdapter = new TrigDetailsLoglistAdapter(TrigDetailsLoglistTab.this, R.layout.triglogrow, mTrigLogs);
        mListView.setAdapter(mTrigLogsAdapter);

        // find view for empty list notification
        mEmptyView = findViewById(android.R.id.empty);
        mListView.setEmptyView(mEmptyView);
        
        // get list of logs
        populateLogs(false);
    }
    
    
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
            populateLogs(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }




    private void populateLogs(boolean refresh) {
        mEmptyView.setText(R.string.downloadingLogs);
        if (authPreferences == null) {
            authPreferences = new AuthPreferences(this);
        }
        if (!authPreferences.isLoggedIn()) {
            Toast.makeText(this, R.string.toastPleaseLogin, Toast.LENGTH_LONG).show();
            return;
        }
        if (trigApiClient == null) {
            trigApiClient = new TrigApiClient(this);
        }

        mTrigLogs.clear();
        mTrigLogsAdapter.notifyDataSetChanged();

        fetchTrigLogsPage(null, refresh);
    }

    private void fetchTrigLogsPage(String nextLink, boolean refresh) {
        trigApiClient.listTrigLogs(mTrigId, 100, nextLink, new TrigApiClient.ApiCallback<TrigApiClient.TrigLogPage>() {
            @Override
            public void onSuccess(TrigApiClient.TrigLogPage result) {
                runOnUiThread(() -> {
                    if (result != null && result.data != null) {
                        for (TrigApiClient.TrigLog log : result.data) {
                            Condition condition = Condition.fromCode(log.condition);
                            TrigLog tl = new TrigLog(log.user_name, log.date, condition, log.comment);
                            mTrigLogs.add(tl);
                        }
                        mTrigLogsAdapter.notifyDataSetChanged();
                        mEmptyView.setText(R.string.noLogs);
                        if (result.links != null && result.links.next != null && !result.links.next.isEmpty()) {
                            fetchTrigLogsPage(result.links.next, refresh);
                        }
                    } else {
                        mEmptyView.setText(R.string.noLogs);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load trig logs: " + errorMessage);
                    Toast.makeText(TrigDetailsLoglistTab.this, getString(R.string.error_loading_logs, errorMessage), Toast.LENGTH_LONG).show();
                    mEmptyView.setText(R.string.noLogs);
                });
            }
        });
    }

    // Allow parent activity to trigger a refresh for the current trigpoint
    public void refreshLogsFromParent() {
        Log.i(TAG, "refreshLogsFromParent");
        populateLogs(true);
    }

    
}