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
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

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
    public static final String ACTION_THEME_CHECK = "com.icetea.singlecalendar.THEME_CHECK";
    private static final String PREFS = "widget_theme_state";
    private static final String PREF_DARK = "last_dark_mode";
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String CACHE_NAME = "single_calendar_latest.jpg";
    private static final String CACHE_DARK_NAME = "single_calendar_latest_dark.jpg";
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMdd", Locale.ROOT);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        scheduleNextUpdate(context);
        scheduleThemeCheck(context);
        rememberCurrentTheme(context);
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
        } else if (ACTION_THEME_CHECK.equals(action)) {
            boolean dark = isDarkMode(context);
            boolean previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(PREF_DARK, dark);
            if (dark != previous) {
                rememberCurrentTheme(context);
                refreshWidgets(context);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
            scheduleNextUpdate(context);
            scheduleThemeCheck(context);
            rememberCurrentTheme(context);
            refreshWidgets(context);
        }
    }

    private static void rememberCurrentTheme(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_DARK, isDarkMode(context)).apply();
    }

    private static void scheduleThemeCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 1002,
                new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_THEME_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 60_000L,
                60_000L, pendingIntent);
    }

    public static void refreshWidgets(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        ComponentName component = new ComponentName(appContext, CalendarWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;

        File cached = new File(appContext.getFilesDir(), CACHE_NAME);
        File cachedDark = new File(appContext.getFilesDir(), CACHE_DARK_NAME);
        Bitmap cachedBitmap = decodeBitmap(cached);
        Bitmap cachedDarkBitmap = decodeBitmap(cachedDark);
        if (cachedBitmap != null && cachedDarkBitmap == null) cachedDarkBitmap = invertedCopy(cachedBitmap);
        String today = LocalDate.now(BEIJING).format(DISPLAY_DATE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(appContext.getPackageName(), R.layout.calendar_widget);
            if (cachedBitmap != null) {
                views.setImageViewBitmap(R.id.calendar_image, roundedCopy(appContext, cachedBitmap));
                if (cachedDarkBitmap != null) views.setImageViewBitmap(R.id.calendar_image_dark, roundedCopy(appContext, cachedDarkBitmap));
                views.setTextViewText(R.id.widget_footer, today + " · 单向历 · 点击打开设置");
            } else {
                views.setTextViewText(R.id.widget_footer, "正在加载今日单向历… · 点击打开设置");
            }
            PendingIntent imageClick = PendingIntent.getActivity(
                    appContext, 1003,
                    new Intent(appContext, ImageViewerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent settingsClick = PendingIntent.getActivity(
                    appContext, 1004,
                    new Intent(appContext, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, imageClick);
            views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick);
            views.setOnClickPendingIntent(R.id.widget_footer, settingsClick);
            manager.updateAppWidget(id, views);
        }

        OneTimeWorkRequest refreshRequest = new OneTimeWorkRequest.Builder(CalendarRefreshWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                "single_calendar_refresh", ExistingWorkPolicy.REPLACE, refreshRequest);
    }

    static boolean downloadToday(Context context) {
        LocalDate date = LocalDate.now(BEIJING);
        String year = date.format(YEAR);
        String monthDay = date.format(MONTH_DAY);
        String imageUrl = "http://img.owspace.com/Public/uploads/Download/" + year + "/" + monthDay + ".jpg";
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
                return false;
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
                return false;
            }
            File destination = new File(context.getFilesDir(), CACHE_NAME);
            try (FileOutputStream fileOutput = new FileOutputStream(destination)) {
                fileOutput.write(bytes);
                fileOutput.flush();
            }
            Bitmap darkBitmap = invertedCopy(bitmap);
            File darkDestination = new File(context.getFilesDir(), CACHE_DARK_NAME);
            try (FileOutputStream darkOutput = new FileOutputStream(darkDestination)) {
                if (!darkBitmap.compress(Bitmap.CompressFormat.JPEG, 95, darkOutput)) {
                    throw new IllegalStateException("Unable to save dark image");
                }
                darkOutput.flush();
            }
            updateAllImages(context, bitmap, darkBitmap, date.format(DISPLAY_DATE) + " · 单向历 · 点击打开设置");
            return true;
        } catch (Exception exception) {
            updateFooter(context, "网络暂不可用 · 显示缓存 · 点击重试");
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static Bitmap roundedCopy(Context context, Bitmap source) {
        float radius = 12f * context.getResources().getDisplayMetrics().density;
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Path path = new Path();
        path.addRoundRect(0f, 0f, source.getWidth(), source.getHeight(), radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawBitmap(source, 0f, 0f, paint);
        return output;
    }

    private static Bitmap invertedCopy(Bitmap source) {
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(copy);
        Paint paint = new Paint();
        ColorMatrix matrix = new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return copy;
    }

    private static Bitmap decodeBitmap(File file) {
        if (!file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) sample *= 2;
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
        while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static void updateAllImages(Context context, Bitmap lightBitmap, Bitmap darkBitmap, String footer) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, CalendarWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
            views.setImageViewBitmap(R.id.calendar_image, roundedCopy(context, lightBitmap));
            views.setImageViewBitmap(R.id.calendar_image_dark, roundedCopy(context, darkBitmap));
            views.setTextViewText(R.id.widget_footer, footer);
            PendingIntent imageClick = PendingIntent.getActivity(
                    context, 1003,
                    new Intent(context, ImageViewerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent settingsClick = PendingIntent.getActivity(
                    context, 1004,
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, imageClick);
            views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick);
            views.setOnClickPendingIntent(R.id.widget_footer, settingsClick);
            manager.updateAppWidget(id, views);
        }
    }

    private static void updateFooter(Context context, String footer) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, CalendarWidgetProvider.class);
        Bitmap cachedBitmap = decodeBitmap(new File(context.getFilesDir(), CACHE_NAME));
        Bitmap cachedDarkBitmap = decodeBitmap(new File(context.getFilesDir(), CACHE_DARK_NAME));
        if (cachedBitmap != null && cachedDarkBitmap == null) cachedDarkBitmap = invertedCopy(cachedBitmap);
        for (int id : manager.getAppWidgetIds(component)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
            if (cachedBitmap != null) views.setImageViewBitmap(R.id.calendar_image, roundedCopy(context, cachedBitmap));
            if (cachedDarkBitmap != null) views.setImageViewBitmap(R.id.calendar_image_dark, roundedCopy(context, cachedDarkBitmap));
            views.setTextViewText(R.id.widget_footer, footer + " · 点击打开设置");
            PendingIntent imageClick = PendingIntent.getActivity(
                    context, 1003,
                    new Intent(context, ImageViewerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent settingsClick = PendingIntent.getActivity(
                    context, 1004,
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.calendar_image, imageClick);
            views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick);
            views.setOnClickPendingIntent(R.id.widget_footer, settingsClick);
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