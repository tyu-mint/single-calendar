package com.icetea.singlecalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
    private ImageView imageView;
    private TextView hint;
    private File cachedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244, 240, 231));
        getWindow().setNavigationBarColor(Color.rgb(244, 240, 231));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244, 240, 231));
        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.rgb(244, 240, 231));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setContentDescription("今日单向历大图");
        root.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hint = new TextView(this);
        hint.setTextColor(Color.rgb(102, 95, 85));
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
        loadImage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imageView != null) loadImage();
    }

    private void loadImage() {
        cachedImage = new File(getFilesDir(), "single_calendar_latest.jpg");
        if (!cachedImage.exists()) {
            imageView.setImageDrawable(null);
            hint.setText("正在获取今日单向历…");
            CalendarWidgetProvider.refreshWidgets(this);
            return;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cachedImage.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 3000) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(cachedImage.getAbsolutePath(), options);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            hint.setText("北京时间 " + LocalDate.now(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                    + " · 长按图片保存或分享");
        } else {
            hint.setText("图片暂时无法显示");
        }
    }

    private void showImageActions() {
        if (cachedImage == null || !cachedImage.exists()) {
            Toast.makeText(this, "暂时没有可用的图片", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("单向历图片")
                .setItems(new String[]{"保存到相册", "分享图片"}, (dialog, which) -> {
                    if (which == 0) saveToGallery();
                    else shareImage();
                }).show();
    }

    private void saveToGallery() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "单向历-" +
                    LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)) + ".jpg");
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
