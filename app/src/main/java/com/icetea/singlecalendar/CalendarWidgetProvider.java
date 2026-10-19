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
    public static final String ACTION_RETRY = "com.icetea.singlecalendar.RETRY";
    public static final String ACTION_IMAGE_REFRESHED = "com.icetea.singlecalendar.IMAGE_REFRESHED";

    private static final String PREFS = "widget_theme_state";
    private static final String PREF_DARK = "last_dark_mode";
    private static final String DAILY_DIR = "daily";
    private static final int PRELOAD_MAX_DAYS = 60;
    private static final long RETRY_DELAY_MS = 30 * 60 * 1000L;

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMdd", Locale.ROOT);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    /** 串行化下载任务：锁内执行，避免并发重复下载同一文件。 */
    private static final Object DOWNLOAD_LOCK = new Object();

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
        } else if (ACTION_RETRY.equals(action)) {
            refreshWidgets(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
            scheduleNextUpdate(context);
            scheduleThemeCheck(context);
            rememberCurrentTheme(context);
            refreshWidgets(context);
            preDownloadAvailable(context);
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

        // 无论当前是否有小组件，都先确保今日图片存在：
        // 缓存缺失时后台下载，下载成功后自动刷新小组件并广播给大图页。
        Thread worker = new Thread(() -> ensureTodayImage(appContext), "SingleCalendarEnsure");
        worker.setDaemon(true);
        worker.start();

        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        ComponentName component = new ComponentName(appContext, CalendarWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;

        LocalDate today = LocalDate.now(BEIJING);
        File todayFile = dailyFile(appContext, today, false);
        File todayDarkFile = dailyFile(appContext, today, true);
        Bitmap light = decodeBitmap(todayFile);
        Bitmap dark = decodeBitmap(todayDarkFile);
        if (light != null && dark == null) {
            dark = invertedCopy(light);
            saveBitmap(dark, todayDarkFile);
        }
        boolean isToday = light != null;
        if (light == null) {
            // 今日未就绪时回退展示最近一天缓存，并提示正在加载今日
            File fallback = findLatestCachedFile(appContext, today);
            if (fallback != null) {
                light = decodeBitmap(fallback);
                dark = decodeBitmap(new File(fallback.getAbsolutePath().replace(".jpg", "_dark.jpg")));
                if (light != null && dark == null) dark = invertedCopy(light);
            }
        }
        String footer = isToday
                ? today.format(DISPLAY_DATE) + " · 单向历 · 点击打开设置"
                : "正在加载今日单向历… · 点击打开设置";

        for (int id : ids) {
            RemoteViews views = new RemoteViews(appContext.getPackageName(), R.layout.calendar_widget);
            if (light != null) {
                views.setImageViewBitmap(R.id.calendar_image, roundedCopy(appContext, light));
                if (dark != null) views.setImageViewBitmap(R.id.calendar_image_dark, roundedCopy(appContext, dark));
            }
            views.setTextViewText(R.id.widget_footer, footer);
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
    }

    /**
     * 确保今日图片已缓存；缺失则下载并刷新小组件与大图页。
     * 失败时安排 30 分钟后的自动重试，重试由 ACTION_RETRY 再次进入本方法。
     */
    private static void ensureTodayImage(Context context) {
        synchronized (DOWNLOAD_LOCK) {
            try {
                LocalDate today = LocalDate.now(BEIJING);
                File lightFile = dailyFile(context, today, false);
                if (lightFile.exists()) {
                    File darkFile = dailyFile(context, today, true);
                    if (!darkFile.exists()) {
                        Bitmap light = decodeBitmap(lightFile);
                        if (light != null) saveBitmap(invertedCopy(light), darkFile);
                    }
                    return;
                }
                byte[] bytes = downloadImage(today);
                if (bytes == null) {
                    scheduleRetry(context);
                    updateFooter(context, "今日图片暂不可用 · 稍后自动重试");
                    return;
                }
                Bitmap bitmap = decodeBitmap(bytes);
                if (bitmap == null) {
                    scheduleRetry(context);
                    updateFooter(context, "今日图片暂不可用 · 稍后自动重试");
                    return;
                }
                saveBytes(bytes, lightFile);
                Bitmap darkBitmap = invertedCopy(bitmap);
                saveBitmap(darkBitmap, dailyFile(context, today, true));
                cleanupLegacyCache(context);
                updateAllImages(context, bitmap, darkBitmap,
                        today.format(DISPLAY_DATE) + " · 单向历 · 点击打开设置");
                context.sendBroadcast(new Intent(ACTION_IMAGE_REFRESHED));
            } finally {
                // 锁由 synchronized 自动释放
            }
        }
    }

    /**
     * 滚动预下载：把「服务器已发布、本地未缓存」的日期全部补齐。
     * 服务器按日期连续发布约未来 3 周；遇到尚未发布的日期（HTTP 非 200）即停止，
     * 下次打开 App / 开机 / 每日更新时会再次补齐新发布的日期。
     */
    public static void preDownloadAvailable(Context context) {
        Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            synchronized (DOWNLOAD_LOCK) {
                try {
                    LocalDate cursor = LocalDate.now(BEIJING);
                    for (int i = 0; i < PRELOAD_MAX_DAYS; i++) {
                        File lightFile = dailyFile(appContext, cursor, false);
                        if (lightFile.exists()) {
                            cursor = cursor.plusDays(1);
                            continue;
                        }
                        byte[] bytes = downloadImage(cursor);
                        if (bytes == null) break; // 服务器尚未发布到该日期
                        Bitmap bitmap = decodeBitmap(bytes);
                        if (bitmap == null) break;
                        saveBytes(bytes, lightFile);
                        saveBitmap(invertedCopy(bitmap), dailyFile(appContext, cursor, true));
                        cursor = cursor.plusDays(1);
                    }
                    cleanupLegacyCache(appContext);
                } finally {
                    // 锁由 synchronized 自动释放
                }
            }
        }, "SingleCalendarPreload");
        worker.setDaemon(true);
        worker.start();
    }

    /** 下载指定日期的单向历图片，失败返回 null。 */
    private static byte[] downloadImage(LocalDate date) {
        String imageUrl = "http://img.owspace.com/Public/uploads/Download/"
                + date.format(YEAR) + "/" + date.format(MONTH_DAY) + ".jpg";
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
            if (status != HttpURLConnection.HTTP_OK) return null;
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
            return output.toByteArray();
        } catch (Exception exception) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 下载失败后安排 30 分钟自动重试（多个失败只会保留最近一个闹钟）。 */
    private static void scheduleRetry(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 1005,
                new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_RETRY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = System.currentTimeMillis() + RETRY_DELAY_MS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        }
    }

    private static File dailyFile(Context context, LocalDate date, boolean dark) {
        return new File(new File(context.getFilesDir(), DAILY_DIR),
                date.format(FILE_DATE) + (dark ? "_dark.jpg" : ".jpg"));
    }

    /** 向前最多找 7 天的缓存，用于今日未就绪时的回退展示。 */
    private static File findLatestCachedFile(Context context, LocalDate from) {
        for (int i = 1; i <= 7; i++) {
            File file = dailyFile(context, from.minusDays(i), false);
            if (file.exists()) return file;
        }
        return null;
    }

    /** 清理旧版单文件缓存（升级后不再使用）。 */
    private static void cleanupLegacyCache(Context context) {
        new File(context.getFilesDir(), "single_calendar_latest.jpg").delete();
        new File(context.getFilesDir(), "single_calendar_latest_dark.jpg").delete();
    }

    private static void saveBytes(byte[] bytes, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;
        try (FileOutputStream fileOutput = new FileOutputStream(destination)) {
            fileOutput.write(bytes);
            fileOutput.flush();
        } catch (Exception ignored) {
        }
    }

    private static void saveBitmap(Bitmap bitmap, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;
        try (FileOutputStream fileOutput = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fileOutput)) {
                throw new IllegalStateException("Unable to save image");
            }
            fileOutput.flush();
        } catch (Exception ignored) {
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
        File todayFile = dailyFile(context, LocalDate.now(BEIJING), false);
        Bitmap cachedBitmap = decodeBitmap(todayFile);
        Bitmap cachedDarkBitmap = decodeBitmap(dailyFile(context, LocalDate.now(BEIJING), true));
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
