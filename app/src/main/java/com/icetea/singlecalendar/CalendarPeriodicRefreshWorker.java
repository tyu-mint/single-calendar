package com.icetea.singlecalendar;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Periodic safety net for devices that delay or miss the midnight alarm.
 * The provider records the Beijing date only after both image caches are
 * successfully written. A failure is retried, while later periodic runs also
 * recover from exhausted one-shot retries.
 */
public final class CalendarPeriodicRefreshWorker extends Worker {
    public CalendarPeriodicRefreshWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        boolean updated = CalendarWidgetProvider.downloadToday(getApplicationContext());
        return updated ? Result.success() : Result.retry();
    }
}
