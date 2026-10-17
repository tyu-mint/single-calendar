package com.icetea.singlecalendar;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String LAUNCHER_ALIAS = "com.icetea.singlecalendar.LauncherAlias";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244, 240, 231));
        getWindow().setNavigationBarColor(Color.rgb(244, 240, 231));
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(52), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(244, 240, 231));

        TextView title = new TextView(this);
        title.setText("单向历");
        title.setTextColor(Color.rgb(49, 44, 38));
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("把今天的单向历，放在桌面上。");
        subtitle.setTextColor(Color.rgb(102, 95, 85));
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        TextView instructions = new TextView(this);
        instructions.setText("添加桌面小组件\\n\\n1. 长按桌面空白处\\n2. 选择「小组件」\\n3. 找到「单向历」并添加\\n\\n图片按北京时间每日更新。点击小组件即可手动刷新。");
        instructions.setTextColor(Color.rgb(72, 66, 58));
        instructions.setTextSize(16);
        instructions.setLineSpacing(dp(4), 1.0f);
        instructions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(44);
        root.addView(instructions, instructionParams);

        Button refresh = new Button(this);
        refresh.setText("刷新今日单向历");
        refresh.setOnClickListener(view -> {
            CalendarWidgetProvider.refreshWidgets(this);
            refresh.setText("已发起刷新");
            refresh.postDelayed(() -> refresh.setText("刷新今日单向历"), 1600);
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(36);
        root.addView(refresh, buttonParams);

        Switch hideIcon = new Switch(this);
        hideIcon.setText("隐藏应用图标");
        hideIcon.setTextColor(Color.rgb(72, 66, 58));
        hideIcon.setTextSize(16);
        hideIcon.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.topMargin = dp(20);
        root.addView(hideIcon, switchParams);

        TextView switchNote = new TextView(this);
        switchNote.setText("隐藏后仍可使用桌面小组件；再次打开本设置，可点击小组件底部文字。");
        switchNote.setTextColor(Color.rgb(130, 120, 108));
        switchNote.setTextSize(12);
        switchNote.setLineSpacing(dp(2), 1.0f);
        root.addView(switchNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ComponentName launcherComponent = new ComponentName(this, LAUNCHER_ALIAS);
        int componentState = getPackageManager().getComponentEnabledSetting(launcherComponent);
        hideIcon.setChecked(componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        hideIcon.setOnCheckedChangeListener((buttonView, checked) -> {
            getPackageManager().setComponentEnabledSetting(
                    launcherComponent,
                    checked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            android.widget.Toast.makeText(this,
                    checked ? "应用图标已隐藏" : "应用图标已显示",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

        TextView date = new TextView(this);
        date.setText("北京时间 " + LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)));
        date.setTextColor(Color.rgb(130, 120, 108));
        date.setTextSize(12);
        date.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(24);
        root.addView(date, dateParams);

        setContentView(root);
        CalendarWidgetProvider.refreshWidgets(this);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
