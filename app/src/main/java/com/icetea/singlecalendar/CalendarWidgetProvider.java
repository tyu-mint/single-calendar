package com.icetea.singlecalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CalendarWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.icetea.singlecalendar.REFRESH";
    public static final String ACTION_DAILY_UPDATE = "com.icetea.singlecalendar.DAILY_UPDATE";
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String CACHE_NAME = "single_calendar_latest.jpg";
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMdd", Locale.ROOT);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        scheduleNextUpdate(context);
        refreshWidgets(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            refreshWidgets(context);
        } else if (ACTION_DAILY_UPDATE.equals(action)) {
            scheduleNextUpdate(context);
            refreshWidgets(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            scheduleNextUpdate(context);
            refreshWidgets(context);
        }
    }

    public static void refreshWidgets(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        ComponentName component = new ComponentName(appContext, CalendarWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;

        File cached = new File(appContext.getFilesDir(), CACHE_NAME);
        Bitmap cachedBitmap = decodeBitmap(cached);
        String today = LocalDate.now(BEIJING).format(DISPLAY_DATE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(appContext.getPackageName(), R.layout.calendar_widget);
            if (cachedBitmap != null) {
                views.setImageViewBitmap(R.id.calendar_image, cachedBitmap);
                views.setTextViewText(R.id.widget_footer, today + " · 单向历 · 点击刷新");
            } else {
                views.setTextViewText(R.id.widget_footer, "正在加载今日单向历…");
            }
            PendingIntent click = PendingIntent.getBroadcast(
                    appContext, 1002,
                    new Intent(appContext, CalendarWidgetProvider.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, click);
            views.setOnClickPendingIntent(R.id.widget_footer, click);
            manager.updateAppWidget(id, views);
        }

        Thread worker = new Thread(() -> downloadToday(appContext), "SingleCalendarDownload");
        worker.setDaemon(true);
        worker.start();
    }

    private static void downloadToday(Context context) {
        LocalDate date = LocalDate.now(BEIJING);
        String year = date.format(YEAR);
        String monthDay = date.format(MONTH_DAY);
        String imageUrl = "https://img.owspace.com/Public/uploads/Download/" + year + "/" + monthDay + ".jpg";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SingleCalendarWidget/1.0");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                updateFooter(context, "今日图片暂不可用 · 点击重试");
                return;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 12 * 1024 * 1024) throw new IllegalStateException("Image too large");
                    output.write(buffer, 0, count);
                }
            }
            byte[] bytes = output.toByteArray();
            Bitmap bitmap = decodeBitmap(bytes);
            if (bitmap == null) {
                updateFooter(context, "图片格式异常 · 点击重试");
                return;
            }

            File destination = new File(context.getFilesDir(), CACHE_NAME);
            try (FileOutputStream fileOutput = new FileOutputStream(destination)) {
                fileOutput.write(bytes);
                fileOutput.flush();
            }
            updateAllImages(context, bitmap, date.format(DISPLAY_DATE) + " · 单向历 · 点击刷新");
        } catch (Exception exception) {
            updateFooter(context, "网络暂不可用 · 显示缓存 · 点击重试");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Bitmap decodeBitmap(File file) {
        if (!file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static Bitmap decodeBitmap(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static void updateAllImages(Context context, Bitmap bitmap, String footer) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, CalendarWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
            views.setImageViewBitmap(R.id.calendar_image, bitmap);
            views.setTextViewText(R.id.widget_footer, footer);
            PendingIntent click = PendingIntent.getBroadcast(
                    context, 1002,
                    new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, click);
            views.setOnClickPendingIntent(R.id.widget_footer, click);
            manager.updateAppWidget(id, views);
        }
    }

    private static void updateFooter(Context context, String footer) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, CalendarWidgetProvider.class);
        Bitmap cachedBitmap = decodeBitmap(new File(context.getFilesDir(), CACHE_NAME));
        for (int id : manager.getAppWidgetIds(component)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
            if (cachedBitmap != null) {
                views.setImageViewBitmap(R.id.calendar_image, cachedBitmap);
            }
            views.setTextViewText(R.id.widget_footer, footer);
            PendingIntent click = PendingIntent.getBroadcast(
                    context, 1002,
                    new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, click);
            views.setOnClickPendingIntent(R.id.widget_footer, click);
            manager.updateAppWidget(id, views);
        }
    }

    private static void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        ZonedDateTime now = ZonedDateTime.now(BEIJING);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atTime(0, 5).atZone(BEIJING);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 1001,
                new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_DAILY_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent);
        }
    }
}
