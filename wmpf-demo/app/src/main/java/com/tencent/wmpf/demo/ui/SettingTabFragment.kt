package com.tencent.wmpf.demo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tencent.wmpf.demo.R

class SettingTabFragment : Fragment() {
    companion object {
        private const val ARG_GROUP_NAME = "group_name"
        private const val ARG_SETTING_KEYS = "setting_keys"

        fun newInstance(groupName: String, settingKeys: List<String>): SettingTabFragment {
            return SettingTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_NAME, groupName)
                    putStringArrayList(ARG_SETTING_KEYS, ArrayList(settingKeys))
                }
            }
        }
    }

    var groupName: String? = null
        private set
    var settingKeys: List<String>? = null
        private set
    var onViewCreatedCallback: ((SettingTabFragment) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupName = it.getString(ARG_GROUP_NAME)
            settingKeys = it.getStringArrayList(ARG_SETTING_KEYS)?.toList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewCreatedCallback?.invoke(this)
    }

    fun getContainer(): ViewGroup? {
        return view?.findViewById(R.id.container_settings)
    }
}

