package com.nashat.tv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView appVersion = findViewById(R.id.appVersion);

        // Dynamically set version
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            appVersion.setText("Version " + version);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Email row click
        findViewById(R.id.emailRow).setOnClickListener(v -> {
            try {
                Intent email = new Intent(Intent.ACTION_SENDTO);
                email.setData(Uri.parse("mailto:"));
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
                email.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - Bug Report / Feedback");
                startActivity(Intent.createChooser(email, getString(R.string.contact_us)));
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });

        // Instagram row click
        findViewById(R.id.instagramRow).setOnClickListener(v -> {
            try {
                // Try opening Instagram app first
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://instagram.com/_u/iosbb0"));
                intent.setPackage("com.instagram.android");
                startActivity(intent);
            } catch (Exception e) {
                // Fallback to browser
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.instagram_url))));
            }
        });

        // Facebook row click
        findViewById(R.id.facebookRow).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.facebook_url))));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Facebook", Toast.LENGTH_SHORT).show();
            }
        });

        // Rate App button
        MaterialButton btnRate = findViewById(R.id.btnRateApp);
        if (btnRate != null) {
            btnRate.setOnClickListener(v -> {
                try {
                    Uri uri = Uri.parse("market://details?id=" + getPackageName());
                    Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                    goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    startActivity(goToMarket);
                } catch (Exception e) {
                    Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName());
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}