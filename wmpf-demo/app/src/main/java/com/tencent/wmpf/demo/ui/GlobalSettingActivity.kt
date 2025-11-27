package com.tencent.wmpf.demo.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayoutMediator
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
    val uiType: UIType,                 // UI组件类型
    var uiComponent: View? = null       // UI组件引用
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

    private lateinit var btnSaveSettings: Button
    private lateinit var btnResetDefaults: Button
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout

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
        btnSaveSettings = findViewById(R.id.btn_save_settings)
        btnResetDefaults = findViewById(R.id.btn_reset_defaults)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
    }

    private fun setupListeners() {

        btnSaveSettings.setOnClickListener {
            saveSettings()
        }

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
                            key,
                            currentValue,
                            defaultValue,
                            validOptions,
                            uiType
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "加载配置项失败: key=$key", e)
                    }
                }

                groupSettings()

                runOnUiThread {
                    setupTabs()
                }
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
        key: String,
        defaultValue: String,
        validOptions: Array<String>?
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
                key.startsWith("configDarkMode") || key.startsWith("enableDarkMode") ||
                        key.startsWith("configUI") || key.startsWith("enableLeftCapsule") ||
                        key.startsWith("enableKeyboard") -> basicKeys.add(key)

                key.startsWith("enableSingleProcess") || key.startsWith("configMaxContainer") ||
                        key.startsWith("configMaxMiniProgram") || key.startsWith("configSuspend") ||
                        key.startsWith("configSuicide") || key.startsWith("configNoBackground") ||
                        key.startsWith("configKeepAlive") -> processKeys.add(key)

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
     * 设置TabLayout和ViewPager2
     */
    private fun setupTabs() {
        val groupNames = settingGroups.keys.toList()

        val adapter = SettingPagerAdapter(this, settingGroups) { fragment, position ->
            val groupName = groupNames.getOrNull(position) ?: return@SettingPagerAdapter
            val keys = settingGroups[groupName] ?: return@SettingPagerAdapter

            val container = fragment.getContainer()
            container?.let {
                for (key in keys) {
                    val item = settingItems[key] ?: continue
                    val view = createSettingView(item)
                    it.addView(view)
                }
            }
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = groupNames.getOrNull(position) ?: ""
        }.attach()
    }

    /**
     * 创建单个配置项的UI组件
     */
    private fun createSettingView(item: SettingItem): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16.dpToPx(), 0, 0)
            }
        }

        // 标题
        val title = TextView(this).apply {
            text = item.key
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8.dpToPx())
            }
        }
        container.addView(title)

        // 根据类型创建组件
        val component = when (item.uiType) {
            UIType.BOOLEAN_SWITCH -> createSwitch(item)
            UIType.ENUM_RADIO -> createRadioGroup(item)
            UIType.ENUM_SPINNER -> createSpinner(item)
            UIType.TEXT_INPUT -> createEditText(item)
        }

        item.uiComponent = component
        container.addView(component)

        return container
    }

    /**
     * 创建Switch组件
     */
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun createSwitch(item: SettingItem): Switch {
        val switch = Switch(this).apply {
            isChecked = item.currentValue == "true"
            setOnCheckedChangeListener { _, isChecked ->
                item.currentValue = if (isChecked) "true" else "false"
            }
        }
        return switch
    }

    /**
     * 创建RadioGroup组件
     */
    private fun createRadioGroup(item: SettingItem): RadioGroup {
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        item.validOptions?.forEachIndexed { _, option ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = option
                tag = option
            }
            radioGroup.addView(radioButton)
        }

        // 设置选中项
        item.validOptions?.indexOf(item.currentValue)?.let { index ->
            if (index >= 0 && index < radioGroup.childCount) {
                radioGroup.check(radioGroup.getChildAt(index).id)
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val checkedView = radioGroup.findViewById<RadioButton>(checkedId)
            item.currentValue = checkedView?.tag as? String ?: item.currentValue
        }

        return radioGroup
    }

    /**
     * 创建Spinner组件
     */
    private fun createSpinner(item: SettingItem): Spinner {
        val spinner = Spinner(this)
        val options = item.validOptions ?: arrayOf()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val selectedIndex = options.indexOf(item.currentValue)
        if (selectedIndex >= 0) {
            spinner.setSelection(selectedIndex)
        }

        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    item.currentValue = options[position]
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        return spinner
    }

    /**
     * 创建EditText组件
     */
    private fun createEditText(item: SettingItem): EditText {
        val editText = EditText(this).apply {
            setText(item.currentValue)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    item.currentValue = s?.toString() ?: ""
                }
            })
        }
        return editText
    }

    /**
     * 加载当前设置值并更新UI
     */
//    private fun loadCurrentSettings() {
//        invokeWMPFApi("加载设置", true) {
//            try {
//                for ((key, item) in settingItems) {
//                    try {
//                        val value = settingApi.getValue(key)
//                        item.currentValue = value
//
//                        runOnUiThread {
//                            updateUIComponent(item)
//                        }
//                    } catch (e: Exception) {
//                        Log.e(TAG, "加载配置项失败: key=$key", e)
//                    }
//                }
//
//                runOnUiThread {
//                    Toast.makeText(this@GlobalSettingActivity, "设置加载成功", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            } catch (e: WMPFApiException) {
//                Log.e(TAG, "加载设置失败", e)
//                runOnUiThread {
//                    Toast.makeText(
//                        this@GlobalSettingActivity,
//                        "加载设置失败: ${e.message}",
//                        Toast.LENGTH_LONG
//                    ).show()
//                }
//            }
//        }
//    }

    /**
     * 更新UI组件显示
     */
    private fun updateUIComponent(item: SettingItem) {
        when (val component = item.uiComponent) {
            is Switch -> {
                component.isChecked = item.currentValue == "true"
            }

            is RadioGroup -> {
                item.validOptions?.indexOf(item.currentValue)?.let { index ->
                    if (index >= 0 && index < component.childCount) {
                        component.check(component.getChildAt(index).id)
                    }
                }
            }

            is Spinner -> {
                val index = item.validOptions?.indexOf(item.currentValue) ?: -1
                if (index >= 0) {
                    component.setSelection(index)
                }
            }

            is EditText -> {
                component.setText(item.currentValue)
            }
        }
    }

    /**
     * 保存当前UI中的设置值
     */
    private fun saveSettings() {
        invokeWMPFApi("保存设置", true) {
            try {
                for ((key, item) in settingItems) {
                    try {
                        settingApi.setSetting(key, item.currentValue)
                    } catch (e: Exception) {
                        Log.e(TAG, "保存配置项失败: key=$key", e)
                    }
                }
            } catch (e: WMPFApiException) {
                Log.e(TAG, "保存设置失败", e)
                runOnUiThread {
                    Toast.makeText(
                        this@GlobalSettingActivity,
                        "保存设置失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 恢复默认值
     */
    private fun resetToDefaults() {
        invokeWMPFApi("恢复默认值", true) {
            try {
                for ((key, item) in settingItems) {
                    try {
                        val defaultValue = settingApi.getDefaultValue(key)
                        validateDefaultValue(key, defaultValue, item.validOptions)
                        item.currentValue = defaultValue

                        runOnUiThread {
                            updateUIComponent(item)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复配置项默认值失败: key=$key", e)
                    }
                }

                runOnUiThread {
                    Toast.makeText(
                        this@GlobalSettingActivity,
                        "已恢复所有默认值，正在保存...",
                        Toast.LENGTH_SHORT
                    ).show()
                    // 恢复默认值后自动保存
                    saveSettings()
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
