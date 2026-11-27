package com.kholil.dailyislam.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Posts one daily ayah / hadith notification when its alarm fires. */
public class DailyNotifyReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "daily_ayah_hadis";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        String big = intent.getStringExtra("bigText");
        int id = intent.getIntExtra("id", 1);
        if (title == null || title.isEmpty()) title = "Muslim Path";
        if (body == null) body = "";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "প্রতিদিনের আয়াত ও হাদিস", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("প্রতিদিন সকাল ৬টায় আজকের আয়াত ও হাদিস");
            nm.createNotificationChannel(ch);
        }
        PendingIntent content = null;
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            content = PendingIntent.getActivity(context, id, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(big != null && !big.isEmpty() ? big : body))
                .setAutoCancel(true);
        if (content != null) b.setContentIntent(content);
        nm.notify(id, b.build());
    }
}
