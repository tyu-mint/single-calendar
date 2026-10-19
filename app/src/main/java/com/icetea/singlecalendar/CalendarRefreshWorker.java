package com.icetea.singlecalendar;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Performs calendar image downloads in WorkManager so the work can continue
 * after the daily alarm receiver returns or the process is reclaimed.
 */
public final class CalendarRefreshWorker extends Worker {
    public CalendarRefreshWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        boolean updated = CalendarWidgetProvider.downloadToday(getApplicationContext());
        if (updated) {
            return Result.success();
        }

        // Keep retrying with WorkManager's capped exponential backoff.
        // The periodic safety-net worker also continues checking if this work
        // is delayed or cancelled by the operating system.
        return Result.retry();
    }
}
