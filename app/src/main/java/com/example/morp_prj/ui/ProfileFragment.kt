package com.example.morp_prj.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.databinding.FragmentProfileBinding
import com.example.morp_prj.utils.PreferenceManager
import com.example.morp_prj.data.model.User
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Biến cờ kiểm soát trạng thái
    private var isEditing = false
    private var currentUser: User? = null
    private var selectedAvatarBase64: String? = null // Store selected avatar as Base64

    // Launcher chọn ảnh
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // 1. Preview ảnh ngay lập tức
                binding.ivAvatar.setImageURI(imageUri)

                // 2. Convert image to Base64 and store
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    // Resize bitmap to reduce storage size (max 300x300)
                    val resizedBitmap = resizeBitmap(bitmap, 300, 300)

                    // Convert to Base64
                    val outputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    selectedAvatarBase64 = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    Toast.makeText(context, "Ảnh đã được chọn. Nhấn Save để lưu.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        setupListeners()
        updateUIState(false) // Mặc định là chế độ View
    }

    private fun loadUserData() {
        currentUser = PreferenceManager.getUser(requireContext())

        currentUser?.let { user ->
            // Fill dữ liệu vào các trường
            binding.tvUserName.text = user.displayName // Hiển thị fullName
            binding.etFullName.setText(user.displayName) // Hiển thị fullName
            binding.etFullName.visibility = View.VISIBLE // Hiển thị trường fullName
            binding.etPhone.setText(user.phone ?: "")
            binding.etEmail.setText(user.email)
            binding.etUsername.setText(user.username)
            binding.etBio.setText(user.bio ?: "")
            // Các trường như avatarId, verified, createdAt, updatedAt nếu cần hiển thị thì thêm vào đây

            // Load Avatar (hỗ trợ cả Base64 và URL)
            if (!user.avatarUrl.isNullOrEmpty()) {
                if (user.avatarUrl.startsWith("data:image")) {
                    // Decode Base64 image
                    try {
                        val base64String = user.avatarUrl.substringAfter("base64,")
                        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        binding.ivAvatar.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        binding.ivAvatar.setImageResource(R.drawable.ic_profile_unselected)
                    }
                } else {
                    // Load from URL using Glide
                    Glide.with(this)
                        .load(user.avatarUrl)
                        .placeholder(R.drawable.ic_profile_unselected)
                        .error(R.drawable.ic_profile_unselected)
                        .into(binding.ivAvatar)
                }
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_profile_unselected)
            }
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
        // Các trường khác nếu cần

        if (newDisplayName.isBlank()) {
            Toast.makeText(context, "Tên hiển thị không được để trống", Toast.LENGTH_SHORT).show()
            return
        }

        // Xác định avatarUrl - nếu có ảnh mới được chọn thì dùng Base64, không thì giữ nguyên
        val newAvatarUrl = selectedAvatarBase64 ?: currentUser?.avatarUrl

        // Cập nhật User Object immutably using copy()
        currentUser = currentUser?.copy(
            displayName = newDisplayName,
            phone = if (newPhone.isBlank()) null else newPhone,
            bio = if (newBio.isBlank()) null else newBio,
            avatarUrl = newAvatarUrl
        )

        // Lưu Local
        currentUser?.let { PreferenceManager.saveUser(requireContext(), it) }

        // Cập nhật UI Header
        binding.tvUserName.text = newDisplayName

        // Reset selected avatar
        selectedAvatarBase64 = null

        // Tắt chế độ Edit
        updateUIState(false)
        Toast.makeText(context, "Lưu thành công (Local)", Toast.LENGTH_SHORT).show()
    }

    // Helper function to resize bitmap
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight

        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
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