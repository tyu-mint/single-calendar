package com.icetea.singlecalendar;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String LAUNCHER_ALIAS = "com.icetea.singlecalendar.LauncherAlias";
    private static final String PREFS = "setup_state";
    private static final String KEY_RETURNED_FROM_APP_INFO = "returned_from_app_info";

    private Button shortcutButton;
    private boolean openedAppInfoForShortcut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int background = dark ? Color.rgb(35, 36, 41) : Color.rgb(244, 240, 231);
        int foreground = dark ? Color.WHITE : Color.rgb(49, 44, 38);
        int secondary = dark ? Color.LTGRAY : Color.rgb(102, 95, 85);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(36), dp(28), dp(28));
        root.setBackgroundColor(background);

        TextView title = new TextView(this);
        title.setText("单向历");
        title.setTextColor(foreground);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("把今天的单向历，放在桌面上。");
        subtitle.setTextColor(secondary);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        TextView instructions = new TextView(this);
        instructions.setText("添加桌面小组件\n\n1. 长按桌面空白处\n2. 选择「小组件」\n3. 找到「单向历」并添加\n\n图片按北京时间每日更新。点击小组件即可手动刷新。");
        instructions.setTextColor(dark ? Color.WHITE : Color.rgb(72, 66, 58));
        instructions.setTextSize(16);
        instructions.setLineSpacing(dp(4), 1.0f);
        instructions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(36);
        root.addView(instructions, instructionParams);

        shortcutButton = new Button(this);
        updateShortcutButtonLabel();
        shortcutButton.setOnClickListener(view -> onShortcutButtonClick());
        LinearLayout.LayoutParams shortcutButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortcutButtonParams.topMargin = dp(24);
        root.addView(shortcutButton, shortcutButtonParams);

        TextView shortcutNote = new TextView(this);
        shortcutNote.setText("首次使用时，请在应用信息中允许「创建桌面快捷方式」。返回后点击按钮即可尝试一键添加；是否能直接添加仍由系统桌面决定。");
        shortcutNote.setTextColor(secondary);
        shortcutNote.setTextSize(12);
        shortcutNote.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams shortcutNoteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortcutNoteParams.topMargin = dp(4);
        root.addView(shortcutNote, shortcutNoteParams);

        Button refresh = new Button(this);
        refresh.setText("刷新今日单向历");
        refresh.setOnClickListener(view -> {
            CalendarWidgetProvider.refreshWidgets(this);
            refresh.setText("已发起刷新");
            refresh.postDelayed(() -> refresh.setText("刷新今日单向历"), 1600);
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        root.addView(refresh, buttonParams);

        Switch hideIcon = new Switch(this);
        hideIcon.setText("隐藏应用图标");
        hideIcon.setTextColor(dark ? Color.WHITE : Color.rgb(72, 66, 58));
        hideIcon.setTextSize(16);
        hideIcon.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.topMargin = dp(16);
        root.addView(hideIcon, switchParams);

        TextView switchNote = new TextView(this);
        switchNote.setText("隐藏后仍可使用桌面小组件；再次打开本设置，可点击小组件底部文字。");
        switchNote.setTextColor(secondary);
        switchNote.setTextSize(12);
        switchNote.setLineSpacing(dp(2), 1.0f);
        root.addView(switchNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ComponentName launcherComponent = new ComponentName(this, LAUNCHER_ALIAS);
        int componentState = getPackageManager().getComponentEnabledSetting(launcherComponent);
        hideIcon.setChecked(componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        hideIcon.setOnCheckedChangeListener((buttonView, checked) -> {
            getPackageManager().setComponentEnabledSetting(launcherComponent,
                    checked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Toast.makeText(this,
                    checked ? "应用图标已隐藏" : "应用图标已显示",
                    Toast.LENGTH_SHORT).show();
        });

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);
        // 打开设置页即主动触发一次今日更新（缺失时后台下载）
        CalendarWidgetProvider.refreshWidgets(this);
        // 滚动预下载：补齐「已发布但未缓存」的未来日期图片
        CalendarWidgetProvider.preDownloadAvailable(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shortcutButton != null && openedAppInfoForShortcut) {
            openedAppInfoForShortcut = false;
            updateShortcutButtonLabel();
        }
    }

    private void onShortcutButtonClick() {
        boolean returned = getPreferences(MODE_PRIVATE)
                .getBoolean(KEY_RETURNED_FROM_APP_INFO, false);
        if (!returned) {
            openedAppInfoForShortcut = true;
            getPreferences(MODE_PRIVATE).edit()
                    .putBoolean(KEY_RETURNED_FROM_APP_INFO, true).apply();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                openedAppInfoForShortcut = false;
                getPreferences(MODE_PRIVATE).edit()
                        .remove(KEY_RETURNED_FROM_APP_INFO).apply();
                Toast.makeText(this, "无法打开应用信息，请在系统设置中手动查找「单向历」",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            requestAddWidget();
        }
    }

    private void updateShortcutButtonLabel() {
        if (shortcutButton == null) return;
        boolean returned = getPreferences(MODE_PRIVATE)
                .getBoolean(KEY_RETURNED_FROM_APP_INFO, false);
        shortcutButton.setText(returned ? "一键添加小组件" : "前往允许创建快捷方式");
    }

    private void requestAddWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager manager = getSystemService(AppWidgetManager.class);
            ComponentName provider = new ComponentName(this, CalendarWidgetProvider.class);
            if (manager != null && manager.isRequestPinAppWidgetSupported()) {
                // Use Android's standard widget-pinning API without launcher-specific extras.
                // No success callback is needed for the current flow.
                boolean requested = manager.requestPinAppWidget(provider, null, null);
                if (requested) {
                    Toast.makeText(this, "已发起添加请求", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("手动添加桌面小组件")
                .setMessage("当前桌面启动器不支持应用内一键添加。请返回桌面，长按空白处，打开「小组件」，找到「单向历」并添加。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
