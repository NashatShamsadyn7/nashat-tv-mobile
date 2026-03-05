package com.nashat.tv;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AppUpdater {

    public static void checkForUpdates(Context context) {
        DatabaseReference updateRef = FirebaseDatabase.getInstance().getReference("appUpdate");
        updateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long serverVersionNum = snapshot.child("versionCode").getValue(Long.class);
                    String apkUrl = snapshot.child("apkUrl").getValue(String.class);
                    String updateMessage = snapshot.child("message").getValue(String.class);
                    Boolean isMandatory = snapshot.child("mandatory").getValue(Boolean.class);

                    int currentVersion = 0;
                    try {
                        android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        currentVersion = pInfo.versionCode;
                    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (serverVersionNum != null && serverVersionNum > currentVersion && apkUrl != null && !apkUrl.isEmpty()) {
                        showUpdateDialog(context, apkUrl, updateMessage, isMandatory != null && isMandatory);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private static void showUpdateDialog(Context context, String apkUrl, String message, boolean isMandatory) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle("Update Available 🚀")
                .setMessage(message != null ? message : "A new version of the app is available. Please update to continue enjoying the best experience.")
                .setCancelable(!isMandatory)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    context.startActivity(intent);
                });

        if (!isMandatory) {
            builder.setNegativeButton("Later", (dialog, which) -> dialog.dismiss());
        }

        builder.show();
    }
}
