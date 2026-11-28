package com.kholil.muslimpath.notify;

import android.content.Intent;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Auto-silent at jamaat time (v2.21).
 * JS calls via Capacitor.nativePromise('JamaatSilent', ...):
 *   schedule({items:[{id, startMs, endMs, mode}]}) — stores the silent
 *     windows and (re)schedules one start + one end alarm per window.
 *     mode is an AudioManager ringer-mode constant (0 = silent,
 *     1 = vibrate). startMs is already jamaat time minus the 5-minute
 *     lead; endMs is when the previous ringer mode is restored.
 *   cancelAll() — cancels every window and restores the ringer mode if a
 *     window is currently active.
 *   checkDndAccess() — whether the user has granted Do Not Disturb
 *     (notification policy) access, without which Android will not let
 *     the app change the ringer mode.
 *   openDndSettings() — opens the system Do Not Disturb access page.
 */
@CapacitorPlugin(name = "JamaatSilent")
public class JamaatSilentPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray items = call.getArray("items");
        if (items == null) {
            call.reject("items missing");
            return;
        }
        JamaatSilentScheduler.saveAndSchedule(getContext(), items.toString());
        JSObject ret = new JSObject();
        ret.put("scheduled", JamaatSilentScheduler.countStored(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        JamaatSilentScheduler.cancelAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void checkDndAccess(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", JamaatSilentScheduler.hasDndAccess(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void openDndSettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("settings unavailable");
        }
    }
}
