package com.example.offlinemediavault;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Раз в полдня сверяет дату и при необходимости меняет иконку приложения. */
public class IconJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        try { IconPlan.applyAuto(this); } catch (Throwable ignore) { }
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) { return false; }
}
