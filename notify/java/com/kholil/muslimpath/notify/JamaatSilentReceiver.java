package com.kholil.muslimpath.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/** Fires at a jamaat silent window's start (silence) and end (restore). */
public class JamaatSilentReceiver extends BroadcastReceiver {
    public static final String ACTION_START = "com.kholil.muslimpath.JAMAAT_SILENT_START";
    public static final String ACTION_END = "com.kholil.muslimpath.JAMAAT_SILENT_END";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int mode = intent.getIntExtra("mode", AudioManager.RINGER_MODE_SILENT);
            JamaatSilentScheduler.applyStart(context, mode);
        } else if (ACTION_END.equals(action)) {
            JamaatSilentScheduler.applyEnd(context);
        }
    }
}
