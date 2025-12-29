package com.example.morp_prj.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
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
import com.example.morp_prj.utils.PreferenceManager
import com.example.morp_prj.data.model.User
import com.example.morp_prj.data.repository.AuthRepository
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Biến cờ kiểm soát trạng thái
    private var isEditing = false
    private var currentUser: User? = null
    private var selectedAvatarUri: Uri? = null // Store selected avatar URI

    // Launcher chọn ảnh
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // 1. Preview ảnh ngay lập tức
                binding.ivAvatar.setImageURI(imageUri)

                // 2. Lưu URI để upload sau
                selectedAvatarUri = imageUri

                Toast.makeText(context, "Ảnh đã được chọn. Nhấn Save để lưu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if user is in guest mode
        val prefs = PreferenceManager(requireContext())
        if (prefs.isGuest()) {
            // Navigate to guest prompt instead of showing profile content
            findNavController().navigate(R.id.action_profile_to_guestPrompt)
            return
        }

        loadUserData()
        fetchProfileFromServer() // Fetch fresh data from server
        setupListeners()
        updateUIState(false) // Mặc định là chế độ View
    }

    /**
     * Fetch profile from server and update UI
     */
    private fun fetchProfileFromServer() {
        val authRepo = AuthRepository(requireContext())

        lifecycleScope.launch {
            val result = authRepo.fetchProfile()

            result.onSuccess { user ->
                // Save to local cache
                PreferenceManager.saveUser(requireContext(), user)
                currentUser = user

                // Update UI with fresh data
                updateUIWithUser(user)
            }.onFailure { error ->
                // Silently fail - keep using cached data
                // Only show error if it's critical
                if (currentUser == null) {
                    Toast.makeText(context, "Không thể tải profile: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadUserData() {
        currentUser = PreferenceManager.getUser(requireContext())
        currentUser?.let { user ->
            updateUIWithUser(user)
        }
    }

    /**
     * Update UI fields with user data
     */
    private fun updateUIWithUser(user: User) {
        // Fill dữ liệu vào các trường
        binding.tvUserName.text = user.displayName // Hiển thị fullName
        binding.etFullName.setText(user.displayName) // Hiển thị fullName
        binding.etFullName.visibility = View.VISIBLE // Hiển thị trường fullName
        binding.etPhone.setText(user.phone ?: "")
        binding.etEmail.setText(user.email)
        binding.etUsername.setText(user.username)
        binding.etBio.setText(user.bio ?: "")
        // Các trường như avatarId, verified, createdAt, updatedAt nếu cần hiển thị thì thêm vào đây

        // Load Avatar từ server URL
        if (!user.avatarUrl.isNullOrEmpty()) {
            // Build full URL: http://localhost:3001/uploads/avatars/filename.jpg
            val baseUrl = "http://10.0.2.2:3001" // Android emulator localhost
            val fullUrl = if (user.avatarUrl.startsWith("http")) {
                user.avatarUrl
            } else {
                "$baseUrl${user.avatarUrl}"
            }

            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_profile_unselected)
                .error(R.drawable.ic_profile_unselected)
                .into(binding.ivAvatar)
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_profile_unselected)
        }
    }

    private fun setupListeners() {
        // 1. Nút Bút Chì (Góc phải) -> Bật chế độ sửa
        binding.ivEdit.setOnClickListener {
            updateUIState(true)
        }

        // 2. Nút Cancel -> Hủy sửa, quay về chế độ xem
        binding.btnCancel.setOnClickListener {
            loadUserData() // Reset lại dữ liệu cũ
            updateUIState(false)
        }

        // 3. Nút Save -> Lưu và quay về chế độ xem
        binding.btnSave.setOnClickListener {
            saveChanges()
        }

        // 4. Nút Đổi Avatar (Chỉ hiện khi đang sửa)
        binding.btnChangeAvatar.setOnClickListener {
            openGallery()
        }

        // 5. Logout
        binding.btnLogout.setOnClickListener {
            performLogout()
        }

        // 6. Change Password (Optional)
        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(context, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIState(enableEdit: Boolean) {
        isEditing = enableEdit

        // Logic Ẩn/Hiện nút dựa trên trạng thái
        if (enableEdit) {
            // Đang sửa: Ẩn nút Edit, Hiện bộ nút Save/Cancel
            binding.ivEdit.visibility = View.GONE
            binding.btnSave.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnChangeAvatar.visibility = View.VISIBLE
            binding.btnChangePassword.visibility = View.VISIBLE
            binding.socialIconsContainer.visibility = View.GONE // Ẩn icon MXH cho đỡ rối (tùy chọn)
        } else {
            // Đang xem: Hiện nút Edit, Ẩn bộ nút Save/Cancel
            binding.ivEdit.visibility = View.VISIBLE
            binding.btnSave.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
            binding.btnChangePassword.visibility = View.GONE
            binding.socialIconsContainer.visibility = View.VISIBLE
        }

        // Enable/Disable các ô nhập liệu
        // Email & Username luôn luôn bị khóa (theo yêu cầu)
        binding.etEmail.isEnabled = false
        binding.etUsername.isEnabled = false

        // Các trường khác cho phép sửa khi enableEdit = true
        binding.etFullName.isEnabled = enableEdit // Corrected from etDisplayName
        binding.etPhone.isEnabled = enableEdit
        binding.etBio.isEnabled = enableEdit
        binding.etGithub.isEnabled = enableEdit
        binding.etLinkedin.isEnabled = enableEdit
        binding.etWebsite.isEnabled = enableEdit
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

        // Hiện loading
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Đang lưu..."

        // Gọi API qua Repository
        val authRepo = AuthRepository(requireContext())

        lifecycleScope.launch {
            val result = authRepo.updateProfile(
                displayName = newDisplayName,
                phone = if (newPhone.isBlank()) null else newPhone,
                bio = if (newBio.isBlank()) null else newBio,
                avatarUri = selectedAvatarUri // Pass URI instead of Base64
            )

            result.onSuccess { response ->
                // Update thành công trên Server -> Cập nhật Local Preference từ response server (Single Source of Truth)
                response.user?.let { updatedUser ->
                    PreferenceManager.saveUser(requireContext(), updatedUser)
                    currentUser = updatedUser // Update biến tạm trong Fragment

                    // Update UI
                    binding.tvUserName.text = updatedUser.displayName
                    updateUIWithUser(updatedUser) // Reload lại UI từ data mới
                }

                Toast.makeText(context, "Đã cập nhật profile thành công!", Toast.LENGTH_SHORT).show()
                updateUIState(false)
                selectedAvatarUri = null // Reset selection
            }.onFailure { error ->
                Toast.makeText(context, "Lỗi cập nhật: ${error.message}", Toast.LENGTH_LONG).show()
                // Không reset UI state để user có thể retry
            }

            // Reset nút Save
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Save"
        }
    }


    private fun performLogout() {
        // Xóa dữ liệu preferences (clearLoginData sẽ reset hasSeenOnboarding về false)
        PreferenceManager.clear(requireContext())


        // Xóa tokens trong SecureTokenStorage
        com.example.morp_prj.security.SecureTokenStorage(requireContext()).clearAll()

        // Navigate đến màn hình login và xóa toàn bộ backstack
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.main_nav, true)
            .build()
        findNavController().navigate(R.id.login_fragment, null, navOptions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}