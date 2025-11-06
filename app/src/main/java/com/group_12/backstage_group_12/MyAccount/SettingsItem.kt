package com.group_12.backstage_group_12.MyAccount

import androidx.annotation.DrawableRes

sealed class SettingsItem {
    data class Header(val welcomeBrand: String, val showSignIn: Boolean) : SettingsItem()
    data class SectionTitle(val title: String, val badge: String? = null) : SettingsItem()
    data class Chevron(val id: String, val title: String, @DrawableRes val icon: Int) : SettingsItem()
    data class Switch(val id: String, val title: String, val checked: Boolean, @DrawableRes val icon: Int) : SettingsItem()
    data class ValueRow(
        val id: String,
        val title: String,
        val value: String,
        @DrawableRes val icon: Int,
        val showEdit: Boolean = false
    ) : SettingsItem()
}
