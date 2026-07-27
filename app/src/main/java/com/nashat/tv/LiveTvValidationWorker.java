package com.nashat.tv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LiveTvValidationWorker extends Worker {

    private static final String UNIQUE_NAME = "live-tv-validation";
    private static final int SAMPLE_SIZE = 30;

    public static void schedule(@NonNull Context context) {
        int freq = SettingsActivity.getRefreshFrequency(context);
        WorkManager wm = WorkManager.getInstance(context);

        // 0=manual, 3=never -> cancel any scheduled work
        if (freq == 0 || freq == 3) {
            wm.cancelUniqueWork(UNIQUE_NAME);
            return;
        }

        long days = (freq == 1) ? 1 : 7;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(LiveTvValidationWorker.class, days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();

        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public LiveTvValidationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        LiveTvChannelRepository repository = new LiveTvChannelRepository(getApplicationContext());
        LiveTvChannelValidator validator = new LiveTvChannelValidator();
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {true};

        repository.loadChannelsOnce(new LiveTvChannelRepository.LoadCallback() {
            @Override
            public void onLoaded(List<Channel> channels, String sourcePath) {
                List<Channel> sample = pickSample(channels);
                List<Channel> updated = new ArrayList<>(sample.size());
                for (Channel channel : sample) {
                    updated.add(validator.validate(channel));
                }
                LiveTvChannelCache.mergeValidationResults(getApplicationContext(), updated);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                success[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        return success[0] ? Result.success() : Result.retry();
    }

    private List<Channel> pickSample(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return new ArrayList<>();
        }
        List<Channel> copy = new ArrayList<>(channels);
        Collections.shuffle(copy);
        int size = Math.min(SAMPLE_SIZE, copy.size());
        return copy.subList(0, size);
    }
}
