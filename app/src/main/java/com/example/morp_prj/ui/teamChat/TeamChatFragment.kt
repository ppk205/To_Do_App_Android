package com.example.morp_prj.ui.chat

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.morp_prj.R
import com.example.morp_prj.databinding.FragmentTeamChatBinding
import com.example.morp_prj.utils.PreferenceManager

class TeamChatFragment : Fragment(R.layout.fragment_team_chat) {

    private lateinit var binding: FragmentTeamChatBinding
    private lateinit var viewModel: TeamChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var preferenceManager: PreferenceManager

    // Dùng SafeArgs để nhận ID team
    // private val args: TeamChatFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTeamChatBinding.bind(view)
        preferenceManager = PreferenceManager(requireContext())
        viewModel = ViewModelProvider(this)[TeamChatViewModel::class.java]

        val currentUserId = preferenceManager.getUserId() ?: ""
        val currentUserName = preferenceManager.getDisplayName() ?: "Me"

        // Nhận dữ liệu từ Bundle (do cách gọi navigate ở bài trước)
        val teamId = arguments?.getString("teamId") ?: return
        val teamName = arguments?.getString("teamName") ?: "Team Chat"

        binding.tvTeamNameHeader.text = teamName

        // Setup RecyclerView
        adapter = ChatAdapter(currentUserId)
        binding.rvChat.adapter = adapter
        binding.rvChat.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true // Luôn cuộn xuống dưới cùng
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
            if (content.isNotEmpty()) {
                viewModel.sendMessage(teamId, currentUserId, currentUserName, content)
                binding.etMessage.setText("")
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}