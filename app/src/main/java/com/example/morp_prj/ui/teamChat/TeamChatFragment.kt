package com.example.morp_prj.ui.chat

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.morp_prj.R
import com.example.morp_prj.databinding.FragmentTeamChatBinding
import com.example.morp_prj.utils.PreferenceManager

class TeamChatFragment : Fragment(R.layout.fragment_team_chat) {

    private lateinit var binding: FragmentTeamChatBinding
    private lateinit var viewModel: TeamChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var preferenceManager: PreferenceManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTeamChatBinding.bind(view)
        preferenceManager = PreferenceManager(requireContext())

        // Use factory to inject Context for encryption
        val factory = TeamChatViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[TeamChatViewModel::class.java]

        val currentUserId = preferenceManager.getUserId() ?: ""
        val currentUserName = preferenceManager.getDisplayName() ?: "Me"

        // Nhận dữ liệu từ Bundle
        val teamId = arguments?.getString("teamId") ?: return
        val teamName = arguments?.getString("teamName") ?: "Team Chat"

        binding.tvTeamNameHeader.text = teamName

        viewModel.loadHistory(teamId)

        // Setup RecyclerView
        adapter = ChatAdapter(currentUserId)
        binding.rvChat.adapter = adapter
        binding.rvChat.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }

        // Quan sát tin nhắn về
        viewModel.messages.observe(viewLifecycleOwner) { msgs ->
            android.util.Log.d("ChatDebug", "UI nhận được list size: ${msgs.size}")

            adapter.submitList(msgs)
            if (msgs.isNotEmpty()) {
                binding.rvChat.smoothScrollToPosition(msgs.size - 1)
            }
        }

        // Gửi tin nhắn
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            val currentAvatar = preferenceManager.getAvatarUrl()

            if (content.isNotEmpty()) {
                viewModel.sendMessage(teamId, currentUserId, currentUserName, currentAvatar, content)
                binding.etMessage.setText("")
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Auto-scroll to bottom when keyboard opens or new message arrives
        binding.etMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.itemCount > 0) {
                binding.rvChat.postDelayed({
                    binding.rvChat.smoothScrollToPosition(adapter.itemCount - 1)
                }, 100)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}