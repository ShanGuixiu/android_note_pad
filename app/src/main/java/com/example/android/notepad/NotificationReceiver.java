package com.example.android.notepad;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class NotificationReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取笔记信息
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");
        Uri noteUri = intent.getData();

        // 创建点击通知后打开的意图
        Intent openIntent = new Intent(Intent.ACTION_EDIT, noteUri);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 替换原有的构建通知代码段
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // API 16+ 使用标准 Builder 方式
            builder = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_menu_alarm)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);
        } else {
            // API 11-15 使用兼容方式构建通知
            builder = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_menu_alarm)
                    .setContentIntent(pendingIntent);

            // 设置自动取消标志
            Notification notification = builder.getNotification();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
        }

        // 发送通知
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
        }else
            System.out.println("notificationManager is null");

    }
}