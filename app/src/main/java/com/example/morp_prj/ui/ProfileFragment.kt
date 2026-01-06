package com.example.morp_prj.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.example.morp_prj.utils.CloudinaryHelper
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Biến cờ kiểm soát trạng thái
    private var isEditing = false
    private var currentUser: User? = null
    private var selectedAvatarUri: Uri? = null // Store selected avatar URI
    private var uploadedCloudinaryLink: String? = null // Store uploaded Cloudinary link
    private var readOnly: Boolean = false
    private var targetUserId: String? = null

    // Launcher chọn ảnh
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // 1. Preview ảnh ngay lập tức
                binding.ivAvatar.setImageURI(imageUri)

                // 2. Lưu URI để upload sau
                selectedAvatarUri = imageUri
                uploadedCloudinaryLink = null // Reset cloudinary link khi chọn ảnh mới

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

        arguments?.let {
            readOnly = it.getBoolean("readOnly", false)
            targetUserId = it.getString("userId")
        }

        if (!readOnly) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { }
            })
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Initialize Cloudinary
        CloudinaryHelper.init(requireContext())

        // Check if user is in guest mode
        val prefs = PreferenceManager(requireContext())
        if (prefs.isGuest()) {
            // Navigate to guest prompt instead of showing profile content
            findNavController().navigate(R.id.action_profile_to_guestPrompt)
            return
        }

        if (readOnly && targetUserId != null) {
            // Fetch full profile from server by userId
            fetchUserProfileById(targetUserId!!)
            setupListeners(readOnly = true)
            updateUIState(false)
            return
        }

        loadUserData()
        fetchProfileFromServer() // Fetch fresh data from server
        setupListeners(readOnly = false)
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

    /**
     * Fetch other user's profile by ID (for read-only view)
     */
    private fun fetchUserProfileById(userId: String) {
        val authRepo = AuthRepository(requireContext())

        lifecycleScope.launch {
            val result = authRepo.fetchUserById(userId)

            result.onSuccess { user ->
                currentUser = user
                // Update UI with fetched user data
                updateUIWithUser(user)
            }.onFailure { error ->
                // Try to use fallback data from arguments
                updateUIWithArgsFallback()
                Toast.makeText(
                    context,
                    "Không thể tải đầy đủ thông tin profile",
                    Toast.LENGTH_SHORT
                ).show()
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

        // Bind social URLs
        binding.etGithub.setText(user.githubUrl ?: "")
        binding.etLinkedin.setText(user.linkedinUrl ?: "")
        binding.etWebsite.setText(user.websiteUrl ?: "")

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

    private fun setupListeners(readOnly: Boolean = false) {
        if (readOnly) {
            binding.ivEdit.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
            binding.btnChangePassword.visibility = View.GONE
            binding.btnLogout.visibility = View.GONE
            binding.socialIconsContainer.visibility = View.VISIBLE
            binding.lblGithub.visibility = View.GONE
            binding.etGithub.visibility = View.GONE
            binding.lblLinkedin.visibility = View.GONE
            binding.etLinkedin.visibility = View.GONE
            binding.lblWebsite.visibility = View.GONE
            binding.etWebsite.visibility = View.GONE
            binding.etFullName.isEnabled = false
            binding.etPhone.isEnabled = false
            binding.etBio.isEnabled = false
            binding.etGithub.isEnabled = false
            binding.etLinkedin.isEnabled = false
            binding.etWebsite.isEnabled = false
            return
        }

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
            (requireActivity() as? MainActivity)?.disconnectSocket()
            performLogout()
        }

        // 6. Change Password
        binding.btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_changePassword)
        }

        // 7. Social icons click handlers
        binding.btnGithub.setOnClickListener {
            openUrl(currentUser?.githubUrl, "GitHub")
        }

        binding.btnLinkedin.setOnClickListener {
            openUrl(currentUser?.linkedinUrl, "LinkedIn")
        }

        binding.btnWeb.setOnClickListener {
            openUrl(currentUser?.websiteUrl, "Website")
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

            // ẨN social icons, HIỆN EditText và label cho social
            binding.socialIconsContainer.visibility = View.GONE
            binding.lblGithub.visibility = View.VISIBLE
            binding.etGithub.visibility = View.VISIBLE
            binding.lblLinkedin.visibility = View.VISIBLE
            binding.etLinkedin.visibility = View.VISIBLE
            binding.lblWebsite.visibility = View.VISIBLE
            binding.etWebsite.visibility = View.VISIBLE
        } else {
            // Đang xem: Hiện nút Edit, Ẩn bộ nút Save/Cancel
            binding.ivEdit.visibility = View.VISIBLE
            binding.btnSave.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
            binding.btnChangePassword.visibility = View.GONE

            // HIỆN social icons, ẨN EditText và label cho social
            binding.socialIconsContainer.visibility = View.VISIBLE
            binding.lblGithub.visibility = View.GONE
            binding.etGithub.visibility = View.GONE
            binding.lblLinkedin.visibility = View.GONE
            binding.etLinkedin.visibility = View.GONE
            binding.lblWebsite.visibility = View.GONE
            binding.etWebsite.visibility = View.GONE
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

    /**
     * Upload selected image to Cloudinary
     */
    private fun uploadImageToCloudinary(imageUri: Uri) {
        // Show progress
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Đang upload..."

        lifecycleScope.launch {
            val result = CloudinaryHelper.uploadImage(imageUri)

            result.onSuccess { cloudinaryLink ->
                uploadedCloudinaryLink = cloudinaryLink
                Toast.makeText(context, "Upload thành công lên Cloudinary!", Toast.LENGTH_SHORT).show()

                // Auto save after upload
                val newDisplayName = binding.etFullName.text.toString()
                val newPhone = binding.etPhone.text.toString()
                val newBio = binding.etBio.text.toString()
                val newGithubUrl = binding.etGithub.text.toString()
                val newLinkedinUrl = binding.etLinkedin.text.toString()
                val newWebsiteUrl = binding.etWebsite.text.toString()

                performSave(newDisplayName, newPhone, newBio, cloudinaryLink, newGithubUrl, newLinkedinUrl, newWebsiteUrl)
            }.onFailure { error ->
                Toast.makeText(context, "Lỗi upload: ${error.message}", Toast.LENGTH_LONG).show()

                // Reset button
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Save"
            }
        }
    }

    private fun saveChanges() {
        val newDisplayName = binding.etFullName.text.toString()
        val newPhone = binding.etPhone.text.toString()
        val newBio = binding.etBio.text.toString()
        val newGithubUrl = binding.etGithub.text.toString()
        val newLinkedinUrl = binding.etLinkedin.text.toString()
        val newWebsiteUrl = binding.etWebsite.text.toString()

        if (newDisplayName.isBlank()) {
            Toast.makeText(context, "Tên hiển thị không được để trống", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user selected an image but hasn't uploaded to Cloudinary yet
        if (selectedAvatarUri != null && uploadedCloudinaryLink == null) {
            // Upload to Cloudinary first
            uploadImageToCloudinary(selectedAvatarUri!!)
            return
        }

        // Save profile (with or without Cloudinary link)
        performSave(newDisplayName, newPhone, newBio, uploadedCloudinaryLink, newGithubUrl, newLinkedinUrl, newWebsiteUrl)
    }

    /**
     * Save profile with optional Cloudinary avatar URL
     */
    private fun performSave(displayName: String, phone: String, bio: String, avatarUrl: String?, githubUrl: String, linkedinUrl: String, websiteUrl: String) {
        // Hiện loading
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Đang lưu..."

        // Gọi API qua Repository
        val authRepo = AuthRepository(requireContext())

        lifecycleScope.launch {
            val result = authRepo.updateProfile(
                displayName = displayName,
                phone = if (phone.isBlank()) null else phone,
                bio = if (bio.isBlank()) null else bio,
                avatarUrl = avatarUrl,
                githubUrl = if (githubUrl.isBlank()) null else githubUrl,
                linkedinUrl = if (linkedinUrl.isBlank()) null else linkedinUrl,
                websiteUrl = if (websiteUrl.isBlank()) null else websiteUrl
            )

            result.onSuccess { response ->
                // Log response để debug
                android.util.Log.d("ProfileFragment", "✅ Update success response: ${response.user}")

                // Update thành công trên Server
                response.user?.let { updatedUser ->
                    android.util.Log.d("ProfileFragment", "📝 Avatar URL from server: ${updatedUser.avatarUrl}")

                    PreferenceManager.saveUser(requireContext(), updatedUser)
                    currentUser = updatedUser

                    // Update UI
                    binding.tvUserName.text = updatedUser.displayName
                    updateUIWithUser(updatedUser)
                }

                Toast.makeText(context, "Đã cập nhật profile thành công!", Toast.LENGTH_SHORT).show()
                updateUIState(false)
                selectedAvatarUri = null
                uploadedCloudinaryLink = null
            }.onFailure { error ->
                android.util.Log.e("ProfileFragment", "❌ Update failed: ${error.message}")
                Toast.makeText(context, "Lỗi cập nhật: ${error.message}", Toast.LENGTH_LONG).show()
            }

            // Reset nút Save
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Save"
        }
    }

    private fun updateUIWithArgsFallback() {
        if (!readOnly) return
        val displayName = arguments?.getString("displayName")
        val email = arguments?.getString("email")
        val avatarUrl = arguments?.getString("avatarUrl")
        if (displayName.isNullOrBlank() && email.isNullOrBlank() && avatarUrl.isNullOrBlank()) return
        binding.tvUserName.text = displayName ?: ""
        binding.etFullName.setText(displayName ?: "")
        binding.etEmail.setText(email ?: "")
        if (!avatarUrl.isNullOrBlank()) {
            val baseUrl = "http://10.0.2.2:3001"
            val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "$baseUrl$avatarUrl"
            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_profile_unselected)
                .error(R.drawable.ic_profile_unselected)
                .into(binding.ivAvatar)
        }
    }

    private fun fetchProfileForDisplay(userId: String) {
        // In read-only mode we already bind passed args; skip network fetch of current user
        updateUIWithArgsFallback()
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

    /**
     * Utility function to ensure URL has protocol
     */
    private fun ensureUrlProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }

    /**
     * Open URL in browser with error handling
     */
    private fun openUrl(url: String?, platformName: String) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "Chưa cập nhật thông tin $platformName", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val validUrl = ensureUrlProtocol(url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở link $platformName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}