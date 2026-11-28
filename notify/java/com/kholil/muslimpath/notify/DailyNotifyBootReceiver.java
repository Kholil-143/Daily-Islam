package com.kholil.muslimpath.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reschedules the stored daily notifications after a device reboot. */
public class DailyNotifyBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            DailyNotifyScheduler.scheduleStored(context);
            /* v2.21: also restore the jamaat auto-silent schedule, and fix
               the ringer mode if the reboot happened mid-window. */
            JamaatSilentScheduler.scheduleStored(context);
            JamaatSilentScheduler.reconcile(context);
        }
    }
}
