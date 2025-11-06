package com.group_12.backstage_group_12.MyAccount

interface MyAccountNavigator {
    fun onSignInClicked()
    fun onChevronClicked(id: String)
    fun onSwitchChanged(id: String, enabled: Boolean)
    fun onEditClicked(id: String)
}
