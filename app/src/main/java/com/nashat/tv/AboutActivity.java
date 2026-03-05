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

        // WhatsApp click
        findViewById(R.id.btnWhatsapp).setOnClickListener(v -> {
            try {
                String url = "https://api.whatsapp.com/send?phone=9647510462910";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            }
        });

        // Telegram click
        findViewById(R.id.btnTelegram).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/iosbb"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Telegram", Toast.LENGTH_SHORT).show();
            }
        });

        // Facebook click
        findViewById(R.id.btnFacebook).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.facebook_url))));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Facebook", Toast.LENGTH_SHORT).show();
            }
        });

        // Instagram click
        findViewById(R.id.btnInstagram).setOnClickListener(v -> {
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