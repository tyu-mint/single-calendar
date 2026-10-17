package com.icetea.singlecalendar;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
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
        instructions.setText("添加桌面小组件\n\n1. 长按桌面空白处\n2. 选择「小组件」\n3. 找到「单向历」并添加\n\n图片按北京时间每日更新。点击小组件即可手动刷新。");
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
