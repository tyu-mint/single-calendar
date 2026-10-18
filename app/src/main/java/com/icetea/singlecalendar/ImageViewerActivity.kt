package com.icetea.singlecalendar

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ImageViewerActivity : Activity() {
    private lateinit var imageView: ImageView
    private lateinit var hint: TextView
    private var cachedImage: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideHint = Runnable {
        if (::hint.isInitialized) hint.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = isDarkMode()
        val background = if (dark) Color.rgb(35, 36, 41) else Color.rgb(244, 240, 231)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = FrameLayout(this).apply { setBackgroundColor(background) }
        imageView = ImageView(this).apply {
            setBackgroundColor(background)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "今日单向历大图"
        }
        root.addView(
            imageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        hint = TextView(this).apply {
            setTextColor(if (dark) Color.WHITE else Color.rgb(102, 95, 85))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val hintParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(hint, hintParams)

        imageView.setOnLongClickListener {
            showImageActions()
            true
        }
        setContentView(root)
        loadImage()
    }

    override fun onResume() {
        super.onResume()
        if (::imageView.isInitialized) loadImage()
    }

    private fun loadImage() {
        val imageFile = File(filesDir, "single_calendar_latest.jpg")
        cachedImage = imageFile
        if (!imageFile.exists()) {
            imageView.setImageDrawable(null)
            hint.text = "正在获取今日单向历…"
            CalendarWidgetProvider.refreshWidgets(this)
            return
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
        if (bitmap != null) {
            imageView.setImageBitmap(if (isDarkMode()) invertedCopy(bitmap) else bitmap)
            hint.text = "长按图片显示更多功能"
            hint.visibility = View.VISIBLE
            handler.removeCallbacks(hideHint)
            handler.postDelayed(hideHint, 2000)
        } else {
            hint.text = "图片暂时无法显示"
        }
    }

    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

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

    override fun onDestroy() {
        handler.removeCallbacks(hideHint)
        super.onDestroy()
    }

    private fun showImageActions() {
        val imageFile = cachedImage
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "暂时没有可用的图片", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("单向历图片")
            .setItems(arrayOf("保存到相册", "分享图片", "删除应用")) { _, which ->
                when (which) {
                    0 -> saveToGallery()
                    1 -> shareImage()
                    2 -> {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    private fun saveToGallery() {
        val imageFile = cachedImage
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "暂时没有可用的图片", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "单向历-" + LocalDate.now(ZoneId.of("Asia/Shanghai"))
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)) + ".jpg"
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/单向历"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create media item")
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open media output")
            FileInputStream(imageFile).use { input ->
                output.use { destination ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        destination.write(buffer, 0, count)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, done, null, null)
            }
            Toast.makeText(this, "已保存到相册的「单向历」文件夹", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Toast.makeText(this, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val imageFile = cachedImage
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "暂时没有可用的图片", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "分享单向历"))
        } catch (exception: Exception) {
            Toast.makeText(this, "暂时无法分享图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
