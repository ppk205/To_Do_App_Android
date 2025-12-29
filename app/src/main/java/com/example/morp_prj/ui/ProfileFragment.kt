package com.example.morp_prj.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.databinding.FragmentProfileBinding
import com.example.morp_prj.data.model.User
import com.example.morp_prj.data.repository.SessionTaskManager
import com.example.morp_prj.security.SecureTokenStorage
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val sessionTaskManager by lazy { SessionTaskManager(requireContext()) }
    private val prefs by lazy { PreferenceManager(requireContext()) }

    private var isEditing = false
    private var currentUser: User? = null

    // Launcher chọn ảnh
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // Preview ảnh ngay lập tức
                binding.ivAvatar.setImageURI(imageUri)
                Toast.makeText(context, "Đã chọn ảnh (Preview). Cần API để lưu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (prefs.isGuest()) {
            // If you have guest prompt destination, navigate; otherwise just show a message and return
            val actionId = resources.getIdentifier("action_profile_to_guestPrompt", "id", requireContext().packageName)
            if (actionId != 0) {
                findNavController().navigate(actionId)
            } else {
                Toast.makeText(requireContext(), "Guest mode: please login to view profile", Toast.LENGTH_SHORT).show()
            }
            return
        }

        loadUserData()
        setupListeners()
        updateUIState(false) // mặc định là chế độ View
    }

    private fun setupListeners() {
        // Edit
        binding.ivEdit.setOnClickListener {
            updateUIState(true)
        }

        // Cancel
        binding.btnCancel.setOnClickListener {
            loadUserData()
            updateUIState(false)
        }

        // Save
        binding.btnSave.setOnClickListener {
            saveChanges()
        }

        // Change Avatar
        binding.btnChangeAvatar.setOnClickListener {
            openGallery()
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            performLogout()
        }

        // Change Password
        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(context, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
        }

        // Social links
        binding.btnGithub.setOnClickListener { openLink(binding.etGithub.text.toString()) }
        binding.btnLinkedin.setOnClickListener { openLink(binding.etLinkedin.text.toString()) }
        binding.btnWeb.setOnClickListener { openLink(binding.etWebsite.text.toString()) }
    }

    private fun updateUIState(enableEdit: Boolean) {
        isEditing = enableEdit

        if (enableEdit) {
            binding.ivEdit.visibility = View.GONE
            binding.btnSave.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnChangeAvatar.visibility = View.VISIBLE
            binding.btnChangePassword.visibility = View.VISIBLE
            binding.socialIconsContainer.visibility = View.GONE
        } else {
            binding.ivEdit.visibility = View.VISIBLE
            binding.btnSave.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
            binding.btnChangePassword.visibility = View.GONE
            binding.socialIconsContainer.visibility = View.VISIBLE
        }

        // Email & Username always locked
        binding.etEmail.isEnabled = false
        binding.etUsername.isEnabled = false

        binding.etFullName.isEnabled = enableEdit
        binding.etPhone.isEnabled = enableEdit
        binding.etBio.isEnabled = enableEdit
        binding.etGithub.isEnabled = enableEdit
        binding.etLinkedin.isEnabled = enableEdit
        binding.etWebsite.isEnabled = enableEdit

        // Optional: add a color hint when editing
        if (enableEdit) {
            binding.ivEdit.setColorFilter(Color.parseColor("#2196F3"))
        } else {
            binding.ivEdit.clearColorFilter()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun saveChanges() {
        val newDisplayName = binding.etFullName.text.toString()
        val newPhone = binding.etPhone.text.toString()
        val newBio = binding.etBio.text.toString()

        if (newDisplayName.isBlank()) {
            Toast.makeText(context, "Tên hiển thị không được để trống", Toast.LENGTH_SHORT).show()
            return
        }

        currentUser = currentUser?.copy(
            displayName = newDisplayName,
            phone = if (newPhone.isBlank()) null else newPhone,
            bio = if (newBio.isBlank()) null else newBio
        )

        currentUser?.let { PreferenceManager.saveUser(requireContext(), it) }
        binding.tvUserName.text = newDisplayName

        updateUIState(false)
        Toast.makeText(context, "Lưu thành công (Local)", Toast.LENGTH_SHORT).show()
    }

    private fun openLink(url: String) {
        if (url.isBlank()) {
            Toast.makeText(requireContext(), "Chưa có đường link", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val urlToOpen = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể mở link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        currentUser = PreferenceManager.getUser(requireContext())

        currentUser?.let { user ->
            binding.tvUserName.text = user.displayName
            binding.etFullName.setText(user.displayName)
            binding.etPhone.setText(user.phone ?: "")
            binding.etEmail.setText(user.email)
            binding.etUsername.setText(user.username)
            binding.etBio.setText(user.bio ?: "")

            // Avatar
            if (!user.avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(user.avatarUrl)
                    .placeholder(R.drawable.ic_profile_unselected)
                    .error(R.drawable.ic_profile_unselected)
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_profile_unselected)
            }
        }
    }

    private fun performLogout() {
        val tokenStorage = SecureTokenStorage(requireContext())

        lifecycleScope.launch {
            // Delete only server tasks for this user (keep local)
            try {
                sessionTaskManager.onLogout()
            } catch (t: Throwable) {
                android.util.Log.w("ProfileFragment", "Task cleanup on logout failed", t)
            }

            // Clear auth
            try {
                tokenStorage.clearTokens()
            } catch (t: Throwable) {
                android.util.Log.w("ProfileFragment", "Failed to clear secure tokens", t)
            }

            prefs.clearLoginData()

            Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show()

            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.main_nav, true)
                .build()
            findNavController().navigate(R.id.login_fragment, null, navOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}