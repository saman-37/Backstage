package com.group_12.backstage_group_12.MyAccount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.group_12.backstage_group_12.databinding.FragmentMyAccountBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyAccountFragment : Fragment(), MyAccountNavigator {

    private var _binding: FragmentMyAccountBinding? = null
    private val binding get() = _binding!!

    private val vm: MyAccountViewModel by viewModels()
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SettingsAdapter(this)

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        // progress bar only for demo async loads
        binding.progress.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collectLatest { adapter.submitList(it) }
        }
    }

    // --- Navigator callbacks ---
    override fun onSignInClicked() {
        Snackbar.make(binding.root, "TODO: Launch Firebase Auth flow", Snackbar.LENGTH_SHORT).show()
        // e.g., startActivity(FirebaseAuthUI.getInstance()...) or your custom screen
    }

    override fun onChevronClicked(id: String) {
        Snackbar.make(binding.root, "Open: $id", Snackbar.LENGTH_SHORT).show()
    }

    override fun onSwitchChanged(id: String, enabled: Boolean) {
        vm.updateToggle(id, enabled)
    }

    override fun onEditClicked(id: String) {
        Snackbar.make(binding.root, "Edit: $id", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
