package com.group_12.backstage.MyAccount

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group_12.backstage.R
import com.group_12.backstage.databinding.ItemHeaderWelcomeBinding
import com.group_12.backstage.databinding.ItemRowChevronBinding
import com.group_12.backstage.databinding.ItemRowSectionBinding
import com.group_12.backstage.databinding.ItemRowSwitchBinding
import com.group_12.backstage.databinding.ItemRowValueBinding

private const val TYPE_HEADER = 0
private const val TYPE_SECTION = 1
private const val TYPE_CHEVRON = 2
private const val TYPE_SWITCH = 3
private const val TYPE_VALUE = 4

class SettingsAdapter(
    private val navigator: MyAccountNavigator
) : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SettingsItem.Header -> TYPE_HEADER
        is SettingsItem.SectionTitle -> TYPE_SECTION
        is SettingsItem.Chevron -> TYPE_CHEVRON
        is SettingsItem.Switch -> TYPE_SWITCH
        is SettingsItem.ValueRow -> TYPE_VALUE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemHeaderWelcomeBinding.inflate(inf, parent, false))
            TYPE_SECTION -> SectionVH(ItemRowSectionBinding.inflate(inf, parent, false))
            TYPE_CHEVRON -> ChevronVH(ItemRowChevronBinding.inflate(inf, parent, false))
            TYPE_SWITCH -> SwitchVH(ItemRowSwitchBinding.inflate(inf, parent, false))
            else -> ValueVH(ItemRowValueBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingsItem.Header -> (holder as HeaderVH).bind(item, navigator)
            is SettingsItem.SectionTitle -> (holder as SectionVH).bind(item)
            is SettingsItem.Chevron -> (holder as ChevronVH).bind(item, navigator)
            is SettingsItem.Switch -> (holder as SwitchVH).bind(item, navigator)
            is SettingsItem.ValueRow -> (holder as ValueVH).bind(item, navigator)
        }
    }

    class HeaderVH(private val b: ItemHeaderWelcomeBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SettingsItem.Header, nav: MyAccountNavigator) {
            b.title.text = "My Account"
            // If signed in, show just the brand/name. If not, show "Welcome to..."
            if (item.showSignIn) {
                b.subtitle.text = "Welcome to ${item.welcomeBrand}"
                b.signInButton.visibility = View.VISIBLE
                b.signInButton.setOnClickListener { nav.onSignInClicked() }
            } else {
                b.subtitle.text = "Hi, ${item.welcomeBrand}"
                b.signInButton.visibility = View.GONE
                b.signInButton.setOnClickListener(null)
            }
        }
    }

    class SectionVH(private val b: ItemRowSectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SettingsItem.SectionTitle) {
            b.sectionTitle.text = item.title
            b.badge.text = item.badge ?: ""
            b.badge.visibility = if (item.badge == null) View.GONE else View.VISIBLE
        }
    }

    class ChevronVH(private val b: ItemRowChevronBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SettingsItem.Chevron, nav: MyAccountNavigator) {
            b.icon.setImageResource(item.icon)
            b.title.text = item.title
            
            // Change color for Sign Out
            if (item.id == "sign_out") {
                val redColor = ContextCompat.getColor(b.root.context, R.color.colorError)
                b.title.setTextColor(redColor)
                b.icon.setColorFilter(redColor)
            } else {
                val defaultColor = ContextCompat.getColor(b.root.context, R.color.colorOnBackground)
                b.title.setTextColor(defaultColor)
                b.icon.clearColorFilter()
            }

            b.root.setOnClickListener { 
                if (item.id == "sign_out") {
                    nav.onSignOutClicked()
                } else {
                    nav.onChevronClicked(item.id) 
                }
            }
        }
    }

    class SwitchVH(private val b: ItemRowSwitchBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SettingsItem.Switch, nav: MyAccountNavigator) {
            b.icon.setImageResource(item.icon)
            b.title.text = item.title
            b.toggle.isChecked = item.checked
            b.toggle.setOnCheckedChangeListener { _, checked ->
                nav.onSwitchChanged(item.id, checked)
            }
        }
    }

    class ValueVH(private val b: ItemRowValueBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SettingsItem.ValueRow, nav: MyAccountNavigator) {
            b.icon.setImageResource(item.icon)
            b.title.text = item.title
            b.value.text = item.value
            b.edit.setOnClickListener { nav.onEditClicked(item.id) }
            b.edit.visibility = if (item.showEdit) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SettingsItem>() {
            override fun areItemsTheSame(old: SettingsItem, new: SettingsItem): Boolean =
                old::class == new::class && when (old) {
                    is SettingsItem.Header -> true
                    is SettingsItem.SectionTitle -> old.title == (new as SettingsItem.SectionTitle).title
                    is SettingsItem.Chevron -> old.id == (new as SettingsItem.Chevron).id
                    is SettingsItem.Switch -> old.id == (new as SettingsItem.Switch).id
                    is SettingsItem.ValueRow -> old.id == (new as SettingsItem.ValueRow).id
                }

            override fun areContentsTheSame(old: SettingsItem, new: SettingsItem): Boolean = old == new
        }
    }
}
