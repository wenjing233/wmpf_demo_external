package com.tencent.wmpf.demo.ui

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.tencent.wmpf.cli.api.WMPF
import com.tencent.wmpf.cli.api.WMPFApiException
import com.tencent.wmpf.demo.R

enum class UIType {
    BOOLEAN_SWITCH,  // Switch组件，用于true/false选项
    ENUM_RADIO,      // RadioGroup组件，用于≤3个固定选项
    ENUM_SPINNER,    // Spinner组件，用于>3个固定选项
    TEXT_INPUT       // EditText组件，用于无固定选项的文本输入
}

/**
 * 配置项数据模型
 * 所有值统一使用String类型，与WMPF API接口保持一致
 */
data class SettingItem(
    val key: String,                    // 配置项键名
    var currentValue: String,           // 当前值
    val defaultValue: String,           // 默认值
    val validOptions: Array<String>?,   // 有效选项列表
    val uiType: UIType                  // UI组件类型
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SettingItem

        if (key != other.key) return false
        if (currentValue != other.currentValue) return false
        if (defaultValue != other.defaultValue) return false
        if (validOptions != null) {
            if (other.validOptions == null) return false
            if (!validOptions.contentEquals(other.validOptions)) return false
        } else if (other.validOptions != null) return false
        if (uiType != other.uiType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + currentValue.hashCode()
        result = 31 * result + defaultValue.hashCode()
        result = 31 * result + (validOptions?.contentHashCode() ?: 0)
        result = 31 * result + uiType.hashCode()
        return result
    }
}

class GlobalSettingActivity : ApiActivity() {
    companion object {
        private const val TAG = "GlobalSettingActivity"
    }

    // 配置项映射：key -> SettingItem
    private val settingItems = mutableMapOf<String, SettingItem>()

    // 配置项分组：分组名称 -> 配置项key列表
    private val settingGroups = mutableMapOf<String, MutableList<String>>()

    private lateinit var containerSettings: LinearLayout
    private lateinit var btnResetDefaults: Button

    private val settingApi by lazy {
        WMPF.getInstance().settingApi
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_setting)

        initViews()
        setupListeners()

        // 加载所有配置项
        loadAllSettings()
    }

    private fun initViews() {
        containerSettings = findViewById(R.id.container_settings)
        btnResetDefaults = findViewById(R.id.btn_reset_defaults)
    }

    private fun setupListeners() {
        btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }
    }

    /**
     * 加载所有配置项并初始化UI
     */
    private fun loadAllSettings() {
        invokeWMPFApi("加载所有配置项", false) {
            try {
                val allKeys = settingApi.allKeys
                val keysString = allKeys.joinToString(", ", "[", "]")
                Log.d(TAG, "获取到 ${allKeys.size} 个配置项: $keysString")

                for (key in allKeys) {
                    try {
                        val validOptions = try {
                            settingApi.getAllSelectItem(key)
                        } catch (_: Exception) {
                            null
                        }
                        val currentValue = settingApi.getValue(key)
                        val defaultValue = settingApi.getDefaultValue(key)
                        val uiType = determineUIType(validOptions)

                        // 验证默认值 for debug
                        // validateDefaultValue(key, defaultValue, validOptions)

                        settingItems[key] = SettingItem(
                            key, currentValue, defaultValue, validOptions, uiType
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "加载配置项失败: key=$key", e)
                    }
                }

                groupSettings()

                runOnUiThread { createAllSettingsUI() }
            } catch (e: WMPFApiException) {
                Log.e(TAG, "加载所有配置项失败", e)
                runOnUiThread {
                    Toast.makeText(
                        this@GlobalSettingActivity,
                        "加载配置项失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 验证默认值是否符合规范
     * for debug
     */
    private fun validateDefaultValue(
        key: String, defaultValue: String, validOptions: Array<String>?
    ) {
        val kv = "键： key=$key 实际可选值: validOptions=${validOptions.contentToString()}"
        Log.d(TAG, kv)
        if (validOptions != null && !validOptions.contains(defaultValue)) {
            val errorMsg =
                "默认值不符合规范: key=$key, defaultValue=$defaultValue, validOptions=${validOptions.contentToString()}"
            Log.e(TAG, errorMsg)
            // assert(false) { errorMsg }
        }
    }

    /**
     * 判断UI组件类型（仅用于UI展示）
     */
    private fun determineUIType(validOptions: Array<String>?): UIType {
        return when {
            validOptions == null -> UIType.TEXT_INPUT
            validOptions.size == 2 && validOptions.contains("true") && validOptions.contains("false") -> UIType.BOOLEAN_SWITCH
            validOptions.size in 2..3 -> UIType.ENUM_RADIO
            validOptions.size > 3 -> UIType.ENUM_SPINNER
            else -> UIType.TEXT_INPUT
        }
    }

    /**
     * 配置项分组
     */
    private fun groupSettings() {
        settingGroups.clear()

        // 基础设置
        val basicKeys = mutableListOf<String>()
        // 进程设置
        val processKeys = mutableListOf<String>()
        // 摄像头设置
        val cameraKeys = mutableListOf<String>()
        // 高级设置(其他)
        val advancedKeys = mutableListOf<String>()

        for (key in settingItems.keys) {
            when {
                key.startsWith("configDarkMode") || key.startsWith("enableDarkMode") || key.startsWith(
                    "configUI"
                ) || key.startsWith("enableLeftCapsule") || key.startsWith("enableKeyboard") -> basicKeys.add(
                    key
                )

                key.startsWith("enableSingleProcess") || key.startsWith("configMaxContainer") || key.startsWith(
                    "configMaxMiniProgram"
                ) || key.startsWith("configSuspend") || key.startsWith("configSuicide") || key.startsWith(
                    "configNoBackground"
                ) || key.startsWith("configKeepAlive") -> processKeys.add(key)

                key.startsWith("openVoice") || key.contains("Camera") -> cameraKeys.add(key)

                else -> advancedKeys.add(key)
            }
        }

        if (basicKeys.isNotEmpty()) settingGroups["基础设置"] = basicKeys
        if (processKeys.isNotEmpty()) settingGroups["进程设置"] = processKeys
        if (cameraKeys.isNotEmpty()) settingGroups["摄像头设置"] = cameraKeys
        if (advancedKeys.isNotEmpty()) settingGroups["高级设置"] = advancedKeys
    }

    /**
     * 构建单页 UI
     */
    private fun createAllSettingsUI() {
        containerSettings.removeAllViews()

        val groupOrder = listOf("基础设置", "进程设置", "摄像头设置", "高级设置")
        for (groupName in groupOrder) {
            val keys = settingGroups[groupName] ?: continue
            addGroupHeader(groupName)
            for (key in keys) {
                val item = settingItems[key] ?: continue
                val button = createSettingButton(item)
                containerSettings.addView(button)
            }
        }
    }

    private fun addGroupHeader(groupName: String) {
        val header = TextView(this).apply {
            text = groupName
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    this@GlobalSettingActivity, android.R.color.darker_gray
                )
            )
            setPadding(0, 24.dpToPx(), 0, 8.dpToPx())
        }
        val divider = View(this).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT, 1.dpToPx()
            )
            setBackgroundColor(
                ContextCompat.getColor(
                    this@GlobalSettingActivity, android.R.color.darker_gray
                )
            )
        }
        containerSettings.addView(header)
        containerSettings.addView(divider)
    }

    private fun createSettingButton(item: SettingItem): Button {
        val valueColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val textSpannable = SpannableStringBuilder().apply {
            append(item.key)
            append("  ")
            val start = length
            append(item.currentValue)
            setSpan(
                ForegroundColorSpan(valueColor), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(0.9f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return Button(this).apply {
            isAllCaps = false
            setText(textSpannable, TextView.BufferType.SPANNABLE)
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT, ViewGroup.MarginLayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 6.dpToPx(), 0, 0)
            }
            setOnClickListener { showEditDialog(item) }
        }
    }

    private fun showEditDialog(item: SettingItem) {
        when (item.uiType) {
            UIType.BOOLEAN_SWITCH -> showBooleanDialog(item)
            UIType.ENUM_RADIO -> showOptionsDialog(item)
            UIType.ENUM_SPINNER -> showOptionsDialog(item)
            UIType.TEXT_INPUT -> showTextInputDialog(item)
        }
    }

    private fun showBooleanDialog(item: SettingItem) {
        val options = arrayOf("true", "false")
        val currentIndex = options.indexOf(item.currentValue)
        AlertDialog.Builder(this).setTitle(item.key)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                saveSettingImmediately(item, options[which])
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    private fun showOptionsDialog(item: SettingItem) {
        val options = item.validOptions ?: return
        val currentIndex = options.indexOf(item.currentValue)
        AlertDialog.Builder(this).setTitle(item.key)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                saveSettingImmediately(item, options[which])
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    private fun showTextInputDialog(item: SettingItem) {
        val input = EditText(this).apply { setText(item.currentValue) }
        AlertDialog.Builder(this).setTitle(item.key).setView(input)
            .setPositiveButton("确定") { _, _ ->
                saveSettingImmediately(item, input.text.toString())
            }.setNegativeButton("取消", null).show()
    }

    private fun saveSettingImmediately(item: SettingItem, newValue: String) {
        invokeWMPFApi("保存配置", false) {
            try {
                settingApi.setSetting(item.key, newValue)
                item.currentValue = newValue
                runOnUiThread {
                    createAllSettingsUI()
                    Toast.makeText(
                        this@GlobalSettingActivity,
                        "已保存: ${item.key} = $newValue",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存失败: ${item.key}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@GlobalSettingActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 恢复默认值：二次确认 + 批量恢复 + 刷新 UI
     */
    private fun resetToDefaults() {
        AlertDialog.Builder(this).setTitle("恢复默认值").setMessage("确定要恢复所有配置为默认值吗？")
            .setPositiveButton("确定") { _, _ -> performResetToDefaults() }
            .setNegativeButton("取消", null).show()
    }

    private fun performResetToDefaults() {
        invokeWMPFApi("恢复默认值", true) {
            try {
                for ((key, item) in settingItems) {
                    try {
                        val defaultValue = settingApi.getDefaultValue(key)
                        item.currentValue = defaultValue
                        settingApi.setSetting(key, defaultValue)
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复配置项默认值失败: key=$key", e)
                    }
                }

                runOnUiThread {
                    createAllSettingsUI()
                    Toast.makeText(
                        this@GlobalSettingActivity, "已恢复所有默认值", Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: WMPFApiException) {
                Log.e(TAG, "恢复默认值失败", e)
                runOnUiThread {
                    Toast.makeText(
                        this@GlobalSettingActivity,
                        "恢复默认值失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * dp转px
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
