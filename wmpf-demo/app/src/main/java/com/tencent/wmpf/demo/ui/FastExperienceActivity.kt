package com.tencent.wmpf.demo.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.tencent.wmpf.cli.api.WMPF
import com.tencent.wmpf.cli.api.WMPFAccountApi
import com.tencent.wmpf.cli.api.WMPFMiniProgramApi.LandscapeMode
import com.tencent.wmpf.cli.model.WMPFSize
import com.tencent.wmpf.cli.model.WMPFStartAppParams
import com.tencent.wmpf.cli.model.WMPFStartAppParams.WMPFAppType
import com.tencent.wmpf.cli.model.protocol.WMPFStartAppRequest
import com.tencent.wmpf.cli.window.WMPFFloatWindowOrientationSpecific
import com.tencent.wmpf.cli.window.WMPFFloatWindowSpecific
import com.tencent.wmpf.demo.R

class FastExperienceActivity : ApiActivity() {
    private lateinit var appIdView: TextView
    private lateinit var pathView: TextView
    private val perf by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }
    private var appType = WMPFAppType.APP_TYPE_RELEASE

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fast_experience)

        appIdView = findViewById(R.id.et_launch_app_id)
        pathView = findViewById(R.id.et_launch_path)

        val savedAppId = perf.getString("appId", DEFAULT_APP_ID)
        if (!savedAppId.isNullOrBlank()) {
            appIdView.text = savedAppId
        }
        val savedPath = perf.getString("path", DEFAULT_PATH)
        if (!savedPath.isNullOrBlank()) {
            pathView.text = savedPath
        }

        findViewById<RadioGroup>(R.id.rg_app_type).setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.app_type_release -> {
                    appType = WMPFAppType.APP_TYPE_RELEASE
                }

                R.id.app_type_dev -> {
                    appType = WMPFAppType.APP_TYPE_DEV
                }

                R.id.app_type_trial -> {
                    appType = WMPFAppType.APP_TYPE_EXP
                }
            }
        }

        var landscapeMode = LandscapeMode.NORMAL
        findViewById<Spinner>(R.id.choose_landscape).apply {
            this.adapter = ArrayAdapter.createFromResource(
                this@FastExperienceActivity,
                R.array.landscapes,
                R.layout.support_simple_spinner_dropdown_item
            )
            this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, view: View?, p2: Int, p3: Long) {
                    landscapeMode = LandscapeMode.values()[p2]
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    landscapeMode = LandscapeMode.NORMAL
                }

            }
        }

        findViewById<Button>(R.id.btn_launch_wxa_app).setOnClickListener {
            val param = createStartAppParams()
            invokeWMPFApi("启动小程序", true) {
                assertLoginState(param.appType)
                WMPF.getInstance().miniProgramApi.launchMiniProgram(
                    param, false, landscapeMode
                )
            }
        }

        findViewById<Button>(R.id.btn_launch_wxa_app_in_float).setOnClickListener {
            val param = createStartAppParams()
            invokeWMPFApi("以悬浮窗口启动小程序",true){
                assertLoginState(param.appType)
                WMPF.getInstance().miniProgramApi.launchMiniProgram(
                    WMPFStartAppRequest(
                        param,false,landscapeMode
                    ).apply {
                        val portraitWidth = 500
                        val portraitHeight = 700
                        val landscapeWidth = 700
                        val landscapeHeight = 500
                        val x = 0
                        val y = 0
                        specific = WMPFFloatWindowSpecific(portraitWidth,
                            portraitHeight,
                            landscapeWidth,
                            landscapeHeight,
                            15F,
                            x,
                            y,
                            true,
                            Gravity.CENTER,
                            TYPE_APPLICATION_OVERLAY
                        ).apply {
                            var landscapeModeInFloat = WMPFFloatWindowSpecific.Orientation.PORTRAIT
                            if(landscapeMode == LandscapeMode.LANDSCAPE
                                || landscapeMode == LandscapeMode.LANDSCAPE_COMPAT){
                                landscapeModeInFloat = WMPFFloatWindowSpecific.Orientation.LANDSCAPE_LOCKED
                            }
                            orientation = landscapeModeInFloat
                            portraitSpec.apply {
                                isDraggableHorizontally = true
                                isDraggableVertically = true
                                dragAreaHeight = 100
                                dragBarSize = WMPFSize(landscapeWidth,100)
                            }
                            landscapeSpec.apply {
                                isDraggableHorizontally = true
                                isDraggableVertically = true
                                dragAreaHeight = 100
                                dragBarSize = WMPFSize(landscapeWidth,100)
                            }
                        }
                })
            }
        }

        findViewById<Button>(R.id.btn_launch_login).setOnClickListener {
            invokeWMPFApi("扫码登录", true) {
                WMPF.getInstance().accountApi.login(WMPFAccountApi.WMPFLoginUIStyle.FULLSCREEN)
            }
        }

        findViewById<Button>(R.id.btn_close_wxa_app).setOnClickListener {
            val appId = appIdView.text.toString()
            invokeWMPFApi("关闭小程序", true) {
                WMPF.getInstance().miniProgramApi.closeWxaApp(appId, false)
            }
        }

        findViewById<Button>(R.id.btn_launch_remote_debug).setOnClickListener {
            invokeWMPFApi("扫码打开小程序", true) {
                WMPF.getInstance().miniProgramApi.launchByQRScanCode()
            }
        }

        findViewById<Button>(R.id.btn_preload).setOnClickListener {
            invokeWMPFApi("预加载", true) {
                WMPF.getInstance().miniProgramApi.preload(null)
            }
        }

        findViewById<Button>(R.id.btn_warmup).setOnClickListener {
            val param = createStartAppParams()
            invokeWMPFApi("预热", true) {
                assertLoginState(param.appType)
                WMPF.getInstance().miniProgramApi.warmUpApp(param)
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("预热完成")
                        .setNegativeButton("启动小程序") { _, _ ->
                            invokeWMPFApi("launchMiniProgram") {
                                WMPF.getInstance().miniProgramApi.launchMiniProgram(param)
                            }
                        }.show()
                }
            }
        }
    }

    private fun createStartAppParams(): WMPFStartAppParams {
        val appId = appIdView.text.toString()
        val path = pathView.text.toString()
        perf.edit().putString("appId", appId).putString("path", path).apply()
        return WMPFStartAppParams(appId, path, appType)
    }

    private fun assertLoginState(type: WMPFAppType) {
        if (type == WMPFAppType.APP_TYPE_EXP || type == WMPFAppType.APP_TYPE_DEV) {
            if (!WMPF.getInstance().accountApi.isLogin) {
                throw Exception("启动${if (type === WMPFAppType.APP_TYPE_EXP) "体验" else "开发"}版小程序请先登录")
            }
        }
    }

    companion object {
        private const val TAG = "FastExperienceActivity"
        private const val DEFAULT_APP_ID = ""
        private const val DEFAULT_PATH = ""
    }
}
