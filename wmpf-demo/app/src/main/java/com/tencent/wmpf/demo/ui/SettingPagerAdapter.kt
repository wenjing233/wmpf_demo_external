package com.tencent.wmpf.demo.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val groups: Map<String, List<String>>,
    private val onFragmentViewCreated: (SettingTabFragment, Int) -> Unit = { _, _ -> }
) : FragmentStateAdapter(fragmentActivity) {

    private val groupNames = groups.keys.toList()

    override fun getItemCount(): Int = groups.size

    override fun createFragment(position: Int): Fragment {
        val groupName = groupNames[position]
        val settingKeys = groups[groupName] ?: emptyList()
        val fragment = SettingTabFragment.newInstance(groupName, settingKeys)
        
        // 设置回调，在Fragment的view创建后调用
        fragment.onViewCreatedCallback = { frag ->
            onFragmentViewCreated(frag, position)
        }
        
        return fragment
    }

    fun getGroupName(position: Int): String = groupNames[position]
}

