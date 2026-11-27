package com.kholil.dailyislam.notify;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Daily ayah & hadith notifications (v2.9).
 * JS calls via Capacitor.nativePromise('DailyNotify', ...):
 *   schedule({items:[{id, epochMs, title, body, bigText}]}) — stores the
 *     items and (re)schedules one AlarmManager alarm per item.
 *   cancelAll() — cancels every stored alarm.
 *   checkPermission() / requestPermission() — POST_NOTIFICATIONS (API 33+).
 */
@CapacitorPlugin(name = "DailyNotify", permissions = {
        @Permission(alias = DailyNotifyPlugin.PERM_ALIAS, strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class DailyNotifyPlugin extends Plugin {
    static final String PERM_ALIAS = "notifications";

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray items = call.getArray("items");
        if (items == null) {
            call.reject("items missing");
            return;
        }
        DailyNotifyScheduler.saveAndSchedule(getContext(), items.toString());
        JSObject ret = new JSObject();
        ret.put("scheduled", DailyNotifyScheduler.countStored(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        DailyNotifyScheduler.cancelAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasPermission());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || hasPermission()) {
            JSObject ret = new JSObject();
            ret.put("granted", hasPermission());
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias(PERM_ALIAS, call, "permCallback");
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasPermission());
        call.resolve(ret);
    }

    private boolean hasPermission() {
        Context ctx = getContext();
        if (Build.VERSION.SDK_INT >= 33) {
            return ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        android.app.NotificationManager nm =
                (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }
}
