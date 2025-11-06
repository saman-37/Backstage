package com.yourcompany.backstage.myaccount

interface MyAccountNavigator {
    fun onSignInClicked()
    fun onChevronClicked(id: String)
    fun onSwitchChanged(id: String, enabled: Boolean)
    fun onEditClicked(id: String)
}
