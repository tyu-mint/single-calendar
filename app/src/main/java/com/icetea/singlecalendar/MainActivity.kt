package com.icetea.singlecalendar

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val LAUNCHER_ALIAS = "com.icetea.singlecalendar.LauncherAlias"
        private const val KEY_RETURNED_FROM_APP_INFO = "returned_from_app_info"
    }

    private var shortcutButton: Button? = null
    private var openedAppInfoForShortcut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val background = if (dark) Color.rgb(35, 36, 41) else Color.rgb(244, 240, 231)
        val foreground = if (dark) Color.WHITE else Color.rgb(49, 44, 38)
        val secondary = if (dark) Color.LTGRAY else Color.rgb(102, 95, 85)

        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val scrollView = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(background)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(36), dp(28), dp(28))
            setBackgroundColor(background)
        }

        val title = TextView(this).apply {
            text = "单向历"
            setTextColor(foreground)
            textSize = 32f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "把今天的单向历，放在桌面上。"
            setTextColor(secondary)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val subtitleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
        root.addView(subtitle, subtitleParams)

        val instructions = TextView(this).apply {
            text = "添加桌面小组件\n\n1. 长按桌面空白处\n2. 选择「小组件」\n3. 找到「单向历」并添加\n\n图片按北京时间每日更新。点击小组件即可手动刷新。"
            setTextColor(if (dark) Color.WHITE else Color.rgb(72, 66, 58))
            textSize = 16f
            setLineSpacing(dp(4).toFloat(), 1.0f)
            gravity = Gravity.CENTER
        }
        val instructionParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(36) }
        root.addView(instructions, instructionParams)

        val shortcut = Button(this)
        shortcutButton = shortcut
        updateShortcutButtonLabel()
        shortcut.setOnClickListener { onShortcutButtonClick() }
        val shortcutButtonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) }
        root.addView(shortcut, shortcutButtonParams)

        val shortcutNote = TextView(this).apply {
            text = "首次使用时，请在应用信息中允许「创建桌面快捷方式」。返回后点击按钮即可尝试一键添加；是否能直接添加仍由系统桌面决定。"
            setTextColor(secondary)
            textSize = 12f
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
        val shortcutNoteParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
        root.addView(shortcutNote, shortcutNoteParams)

        val refresh = Button(this).apply {
            text = "刷新今日单向历"
            setOnClickListener {
                CalendarWidgetProvider.refreshWidgets(this@MainActivity)
                text = "已发起刷新"
                postDelayed({ text = "刷新今日单向历" }, 1600)
            }
        }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        root.addView(refresh, buttonParams)

        val hideIcon = Switch(this).apply {
            text = "隐藏应用图标"
            setTextColor(if (dark) Color.WHITE else Color.rgb(72, 66, 58))
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        }
        val switchParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        root.addView(hideIcon, switchParams)

        val switchNote = TextView(this).apply {
            text = "隐藏后仍可使用桌面小组件；再次打开本设置，可点击小组件底部文字。"
            setTextColor(secondary)
            textSize = 12f
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
        root.addView(
            switchNote,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val launcherComponent = ComponentName(this, LAUNCHER_ALIAS)
        val componentState = packageManager.getComponentEnabledSetting(launcherComponent)
        hideIcon.isChecked = componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        hideIcon.setOnCheckedChangeListener { _, checked ->
            packageManager.setComponentEnabledSetting(
                launcherComponent,
                if (checked) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Toast.makeText(
                this,
                if (checked) "应用图标已隐藏" else "应用图标已显示",
                Toast.LENGTH_SHORT
            ).show()
        }

        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scrollView)
        CalendarWidgetProvider.refreshWidgets(this)
    }

    override fun onResume() {
        super.onResume()
        if (shortcutButton != null && openedAppInfoForShortcut) {
            openedAppInfoForShortcut = false
            updateShortcutButtonLabel()
        }
    }

    private fun onShortcutButtonClick() {
        val returned = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_RETURNED_FROM_APP_INFO, false)
        if (!returned) {
            openedAppInfoForShortcut = true
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean(KEY_RETURNED_FROM_APP_INFO, true)
                .apply()
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (exception: Exception) {
                openedAppInfoForShortcut = false
                getPreferences(MODE_PRIVATE).edit()
                    .remove(KEY_RETURNED_FROM_APP_INFO)
                    .apply()
                Toast.makeText(
                    this,
                    "无法打开应用信息，请在系统设置中手动查找「单向历」",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            requestAddWidget()
        }
    }

    private fun updateShortcutButtonLabel() {
        val button = shortcutButton ?: return
        val returned = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_RETURNED_FROM_APP_INFO, false)
        button.text = if (returned) "一键添加小组件" else "前往允许创建快捷方式"
    }

    private fun requestAddWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(this, CalendarWidgetProvider::class.java)
            if (manager != null && manager.isRequestPinAppWidgetSupported) {
                val requested = manager.requestPinAppWidget(provider, null, null)
                if (requested) {
                    Toast.makeText(this, "已发起添加请求", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("手动添加桌面小组件")
            .setMessage("当前桌面启动器不支持应用内一键添加。请返回桌面，长按空白处，打开「小组件」，找到「单向历」并添加。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
