package com.icetea.singlecalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ImageViewerActivity extends Activity {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private ImageView imageView;
    private TextView hint;
    private File cachedImage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideHint = () -> { if (hint != null) hint.setVisibility(View.GONE); };

    /** 今日图片下载完成后由 Provider 广播，本页收到后自动刷新大图。 */
    private final BroadcastReceiver imageRefreshedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CalendarWidgetProvider.ACTION_IMAGE_REFRESHED.equals(intent.getAction())) {
                handler.post(ImageViewerActivity.this::loadImage);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = isDarkMode();
        int background = dark ? Color.rgb(35, 36, 41) : Color.rgb(244, 240, 231);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        imageView = new ImageView(this);
        imageView.setBackgroundColor(background);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setContentDescription("今日单向历大图");
        root.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hint = new TextView(this);
        hint.setTextColor(dark ? Color.WHITE : Color.rgb(102, 95, 85));
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(12), dp(24), dp(12));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(hint, hintParams);
        imageView.setOnLongClickListener(view -> {
            showImageActions();
            return true;
        });
        setContentView(root);

        registerRefreshReceiver();
        loadImage();
        // 打开大图页即主动触发一次今日更新：若今日图片缺失会立即后台下载，
        // 下载完成后通过 ACTION_IMAGE_REFRESHED 广播自动刷新本页与桌面小组件。
        CalendarWidgetProvider.refreshWidgets(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imageView != null) loadImage();
    }

    private void registerRefreshReceiver() {
        IntentFilter filter = new IntentFilter(CalendarWidgetProvider.ACTION_IMAGE_REFRESHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(imageRefreshedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(imageRefreshedReceiver, filter);
        }
    }

    private void loadImage() {
        LocalDate today = LocalDate.now(BEIJING);
        File todayFile = dailyFile(today);
        cachedImage = todayFile.exists() ? todayFile : latestCachedBefore(today);
        if (cachedImage == null || !cachedImage.exists()) {
            imageView.setImageDrawable(null);
            hint.setText("正在获取今日单向历…");
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(cachedImage.getAbsolutePath(), options);
        if (bitmap != null) {
            imageView.setImageBitmap(isDarkMode() ? invertedCopy(bitmap) : bitmap);
            if (todayFile.exists()) {
                hint.setText("长按图片显示更多功能");
                hint.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideHint);
                handler.postDelayed(hideHint, 2000);
            } else {
                // 今日图未就绪，回退展示最近缓存，同时等待后台下载完成自动刷新
                hint.setText("正在加载今日单向历…");
                hint.setVisibility(View.VISIBLE);
            }
        } else {
            hint.setText("图片暂时无法显示");
        }
    }

    private File dailyFile(LocalDate date) {
        return new File(new File(getFilesDir(), "daily"),
                date.format(FILE_DATE) + ".jpg");
    }

    private File latestCachedBefore(LocalDate today) {
        for (int i = 1; i <= 7; i++) {
            File file = dailyFile(today.minusDays(i));
            if (file.exists()) return file;
        }
        return null;
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private Bitmap invertedCopy(Bitmap source) {
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
        android.graphics.Canvas canvas = new android.graphics.Canvas(copy);
        android.graphics.Paint paint = new android.graphics.Paint();
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideHint);
        try {
            unregisterReceiver(imageRefreshedReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void showImageActions() {
        if (cachedImage == null || !cachedImage.exists()) {
            Toast.makeText(this, "暂时没有可用的图片", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("单向历图片")
                .setItems(new String[]{"保存到相册", "分享图片", "删除应用"}, (dialog, which) -> {
                    if (which == 0) {
                        saveToGallery();
                    } else if (which == 1) {
                        shareImage();
                    } else if (which == 2) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }).show();
    }

    private void saveToGallery() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "单向历-" +
                    LocalDate.now(BEIJING).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)) + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/单向历");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Unable to create media item");
            try (FileInputStream input = new FileInputStream(cachedImage);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Unable to open media output");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            }
            Toast.makeText(this, "已保存到相册的「单向历」文件夹", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareImage() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cachedImage);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享单向历"));
        } catch (Exception e) {
            Toast.makeText(this, "暂时无法分享图片", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
