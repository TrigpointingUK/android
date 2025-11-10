package uk.trigpointing.android;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.mikepenz.aboutlibraries.LibsBuilder;

import uk.trigpointing.android.common.BaseActivity;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView appNameVersion = findViewById(R.id.app_name_version);
        TextView buildDate = findViewById(R.id.build_date);
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            long versionCode;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = packageInfo.getLongVersionCode();
            } else {
                @SuppressWarnings("deprecation")
                int legacyVersionCode = packageInfo.versionCode;
                versionCode = legacyVersionCode;
            }
            appNameVersion.setText(getString(R.string.app_name_version, versionName, versionCode));
            buildDate.setText(getString(R.string.build_date, BuildConfig.BUILD_TIME));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Button privacyPolicy = findViewById(R.id.privacy_policy);
        privacyPolicy.setOnClickListener(v -> {
            Intent intent = new Intent(this, uk.trigpointing.android.common.WebViewActivity.class);
            intent.putExtra(uk.trigpointing.android.common.WebViewActivity.EXTRA_URL, "https://wiki.trigpointing.uk/TrigpointingUK:Privacy_policy");
            startActivity(intent);
        });

        Button openSourceLicenses = findViewById(R.id.open_source_licenses);
        openSourceLicenses.setOnClickListener(v -> {
            @SuppressWarnings("deprecation")
            LibsBuilder builder = new LibsBuilder();
            builder.withActivityTitle("Open Source Licenses")
                    .withAboutVersionShown(true)
                    .start(this);
        });

        Button acknowledgements = findViewById(R.id.acknowledgements);
        acknowledgements.setOnClickListener(v -> {
            Intent intent = new Intent(this, AcknowledgementsActivity.class);
            startActivity(intent);
        });
    }
}
