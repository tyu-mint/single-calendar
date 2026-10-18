package com.icetea.singlecalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.widget.RemoteViews
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        scheduleNextUpdate(context)
        scheduleThemeCheck(context)
        rememberCurrentTheme(context)
        refreshWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        val action = intent?.action ?: return
        when (action) {
            ACTION_REFRESH -> refreshWidgets(context)
            ACTION_DAILY_UPDATE -> {
                scheduleNextUpdate(context)
                refreshWidgets(context)
            }
            ACTION_THEME_CHECK -> {
                val dark = isDarkMode(context)
                val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(PREF_DARK, dark)
                if (dark != previous) {
                    rememberCurrentTheme(context)
                    refreshWidgets(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                scheduleNextUpdate(context)
                scheduleThemeCheck(context)
                rememberCurrentTheme(context)
                refreshWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.icetea.singlecalendar.REFRESH"
        const val ACTION_DAILY_UPDATE = "com.icetea.singlecalendar.DAILY_UPDATE"
        const val ACTION_THEME_CHECK = "com.icetea.singlecalendar.THEME_CHECK"

        private const val PREFS = "widget_theme_state"
        private const val PREF_DARK = "last_dark_mode"
        private val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")
        private const val CACHE_NAME = "single_calendar_latest.jpg"
        private const val CACHE_DARK_NAME = "single_calendar_latest_dark.jpg"
        private val YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT)
        private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMdd", Locale.ROOT)
        private val DISPLAY_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

        private fun rememberCurrentTheme(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_DARK, isDarkMode(context))
                .apply()
        }

        private fun scheduleThemeCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                Intent(context, CalendarWidgetProvider::class.java).setAction(ACTION_THEME_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + 60_000L,
                60_000L,
                pendingIntent
            )
        }

        @JvmStatic
        fun refreshWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, CalendarWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val cached = File(appContext.filesDir, CACHE_NAME)
            val cachedDark = File(appContext.filesDir, CACHE_DARK_NAME)
            val cachedBitmap = decodeBitmap(cached)
            var cachedDarkBitmap = decodeBitmap(cachedDark)
            if (cachedBitmap != null && cachedDarkBitmap == null) {
                cachedDarkBitmap = invertedCopy(cachedBitmap)
            }
            val today = LocalDate.now(BEIJING).format(DISPLAY_DATE)
            for (id in ids) {
                val views = RemoteViews(appContext.packageName, R.layout.calendar_widget)
                if (cachedBitmap != null) {
                    views.setImageViewBitmap(
                        R.id.calendar_image,
                        roundedCopy(appContext, cachedBitmap)
                    )
                    if (cachedDarkBitmap != null) {
                        views.setImageViewBitmap(
                            R.id.calendar_image_dark,
                            roundedCopy(appContext, cachedDarkBitmap)
                        )
                    }
                    views.setTextViewText(R.id.widget_footer, "$today · 单向历 · 点击打开设置")
                } else {
                    views.setTextViewText(R.id.widget_footer, "正在加载今日单向历… · 点击打开设置")
                }

                val imageClick = PendingIntent.getActivity(
                    appContext,
                    1003,
                    Intent(appContext, ImageViewerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val settingsClick = PendingIntent.getActivity(
                    appContext,
                    1004,
                    Intent(appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.calendar_image, imageClick)
                views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick)
                views.setOnClickPendingIntent(R.id.widget_footer, settingsClick)
                manager.updateAppWidget(id, views)
            }

            Thread({ downloadToday(appContext) }, "SingleCalendarDownload").apply {
                isDaemon = true
                start()
            }
        }

        private fun downloadToday(context: Context) {
            val date = LocalDate.now(BEIJING)
            val year = date.format(YEAR)
            val monthDay = date.format(MONTH_DAY)
            val imageUrl =
                "http://img.owspace.com/Public/uploads/Download/$year/$monthDay.jpg"
            var connection: HttpURLConnection? = null
            try {
                val openedConnection = URL(imageUrl).openConnection() as HttpURLConnection
                connection = openedConnection
                openedConnection.requestMethod = "GET"
                openedConnection.connectTimeout = 12_000
                openedConnection.readTimeout = 15_000
                openedConnection.instanceFollowRedirects = true
                openedConnection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android) SingleCalendarWidget/1.0"
                )
                openedConnection.setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
                )
                openedConnection.setRequestProperty("Cache-Control", "no-cache")
                if (openedConnection.responseCode != HttpURLConnection.HTTP_OK) {
                    updateFooter(context, "今日图片暂不可用 · 点击重试")
                    return
                }

                val output = ByteArrayOutputStream()
                openedConnection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > 12 * 1024 * 1024) {
                            throw IllegalStateException("Image too large")
                        }
                        output.write(buffer, 0, count)
                    }
                }

                val bytes = output.toByteArray()
                val bitmap = decodeBitmap(bytes)
                if (bitmap == null) {
                    updateFooter(context, "图片格式异常 · 点击重试")
                    return
                }

                FileOutputStream(File(context.filesDir, CACHE_NAME)).use { fileOutput ->
                    fileOutput.write(bytes)
                    fileOutput.flush()
                }

                val darkBitmap = invertedCopy(bitmap)
                FileOutputStream(File(context.filesDir, CACHE_DARK_NAME)).use { darkOutput ->
                    if (!darkBitmap.compress(Bitmap.CompressFormat.JPEG, 95, darkOutput)) {
                        throw IllegalStateException("Unable to save dark image")
                    }
                    darkOutput.flush()
                }
                updateAllImages(
                    context,
                    bitmap,
                    darkBitmap,
                    date.format(DISPLAY_DATE) + " · 单向历 · 点击打开设置"
                )
            } catch (exception: Exception) {
                updateFooter(context, "网络暂不可用 · 显示缓存 · 点击重试")
            } finally {
                connection?.disconnect()
            }
        }

        private fun isDarkMode(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        private fun roundedCopy(context: Context, source: Bitmap): Bitmap {
            val radius = 12f * context.resources.displayMetrics.density
            val output = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val path = Path()
            path.addRoundRect(
                0f,
                0f,
                source.width.toFloat(),
                source.height.toFloat(),
                radius,
                radius,
                Path.Direction.CW
            )
            canvas.clipPath(path)
            canvas.drawBitmap(source, 0f, 0f, paint)
            return output
        }

        private fun invertedCopy(source: Bitmap): Bitmap {
            val copy = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
            val canvas = Canvas(copy)
            val paint = Paint()
            val matrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            canvas.drawBitmap(source, 0f, 0f, paint)
            return copy
        }

        private fun decodeBitmap(file: File): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private fun decodeBitmap(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        private fun updateAllImages(
            context: Context,
            lightBitmap: Bitmap,
            darkBitmap: Bitmap,
            footer: String
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(component)) {
                val views = RemoteViews(context.packageName, R.layout.calendar_widget)
                views.setImageViewBitmap(
                    R.id.calendar_image,
                    roundedCopy(context, lightBitmap)
                )
                views.setImageViewBitmap(
                    R.id.calendar_image_dark,
                    roundedCopy(context, darkBitmap)
                )
                views.setTextViewText(R.id.widget_footer, footer)

                val imageClick = PendingIntent.getActivity(
                    context,
                    1003,
                    Intent(context, ImageViewerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val settingsClick = PendingIntent.getActivity(
                    context,
                    1004,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.calendar_image, imageClick)
                views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick)
                views.setOnClickPendingIntent(R.id.widget_footer, settingsClick)
                manager.updateAppWidget(id, views)
            }
        }

        private fun updateFooter(context: Context, footer: String) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarWidgetProvider::class.java)
            val cachedBitmap = decodeBitmap(File(context.filesDir, CACHE_NAME))
            var cachedDarkBitmap = decodeBitmap(File(context.filesDir, CACHE_DARK_NAME))
            if (cachedBitmap != null && cachedDarkBitmap == null) {
                cachedDarkBitmap = invertedCopy(cachedBitmap)
            }

            for (id in manager.getAppWidgetIds(component)) {
                val views = RemoteViews(context.packageName, R.layout.calendar_widget)
                if (cachedBitmap != null) {
                    views.setImageViewBitmap(
                        R.id.calendar_image,
                        roundedCopy(context, cachedBitmap)
                    )
                }
                if (cachedDarkBitmap != null) {
                    views.setImageViewBitmap(
                        R.id.calendar_image_dark,
                        roundedCopy(context, cachedDarkBitmap)
                    )
                }
                views.setTextViewText(R.id.widget_footer, "$footer · 点击打开设置")

                val imageClick = PendingIntent.getActivity(
                    context,
                    1003,
                    Intent(context, ImageViewerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val settingsClick = PendingIntent.getActivity(
                    context,
                    1004,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.calendar_image, imageClick)
                views.setOnClickPendingIntent(R.id.calendar_image_dark, imageClick)
                views.setOnClickPendingIntent(R.id.widget_footer, settingsClick)
                manager.updateAppWidget(id, views)
            }
        }

        private fun scheduleNextUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = ZonedDateTime.now(BEIJING)
            val next = now.toLocalDate().plusDays(1).atTime(0, 5).atZone(BEIJING)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                Intent(context, CalendarWidgetProvider::class.java).setAction(ACTION_DAILY_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(),
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(),
                    pendingIntent
                )
            }
        }
    }
}
