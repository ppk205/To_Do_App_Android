package com.example.morp_prj.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.User
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager
    private var isGuest = false
    private var isEditMode = false

    // UI Components
    private lateinit var tvUserName: TextView
    private lateinit var etFullName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etBio: EditText
    private lateinit var etEmail: EditText
    private lateinit var etUsername: EditText
    private lateinit var etGithub: EditText
    private lateinit var etLinkedin: EditText
    private lateinit var etWebsite: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var ivEdit: ImageView
    private lateinit var btnChangePassword: MaterialButton
    private lateinit var btnChangeAvatar: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnGithub: ImageView
    private lateinit var btnLinkedin: ImageView
    private lateinit var btnWeb: ImageView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton

    // Image picker activity result launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri: Uri? ->
        if (imageUri != null) {
            handleImageSelected(imageUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())
        isGuest = preferenceManager.isGuest()

        // Initialize UI components
        initializeViews(view)

        if (isGuest) {
            // Guest không thể chỉnh sửa profile
            disableEditFields(view)
            showGuestMessage()
        } else {
            // User đã đăng nhập - lấy dữ liệu từ API
            loadUserDataFromAPI()
            setupEditButton()
            setupLogoutButton()
            setupChangePasswordButton()
            setupChangeAvatarButton()
            setupSaveButton()
            setupCancelButton()
            setupSocialLinkButtons()
            disableAllEditFields()
        }
    }

    private fun initializeViews(view: View) {
        tvUserName = view.findViewById(R.id.tvUserName)
        etFullName = view.findViewById(R.id.etFullName)
        etPhone = view.findViewById(R.id.etPhone)
        etBio = view.findViewById(R.id.etBio)
        etEmail = view.findViewById(R.id.etEmail)
        etUsername = view.findViewById(R.id.etUsername)
        etGithub = view.findViewById(R.id.etGithub)
        etLinkedin = view.findViewById(R.id.etLinkedin)
        etWebsite = view.findViewById(R.id.etWebsite)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        ivEdit = view.findViewById(R.id.ivEdit)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnGithub = view.findViewById(R.id.btnGithub)
        btnLinkedin = view.findViewById(R.id.btnLinkedin)
        btnWeb = view.findViewById(R.id.btnWeb)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun setupEditButton() {
        ivEdit.isClickable = true
        ivEdit.setOnClickListener {
            enterEditMode()
        }
    }

    private fun enterEditMode() {
        isEditMode = true
        enableAllEditFields()
        btnChangePassword.visibility = View.VISIBLE
        btnChangeAvatar.visibility = View.VISIBLE
        btnSave.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
        btnLogout.visibility = View.GONE
        ivEdit.setColorFilter(Color.parseColor("#2196F3"))
        Toast.makeText(requireContext(), "Chế độ chỉnh sửa", Toast.LENGTH_SHORT).show()
    }

    private fun exitEditMode(saveData: Boolean = false) {
        isEditMode = false
        disableAllEditFields()
        btnChangePassword.visibility = View.GONE
        btnChangeAvatar.visibility = View.GONE
        btnSave.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnLogout.visibility = View.VISIBLE
        ivEdit.clearColorFilter()

        if (saveData) {
            saveUserData()
        }
    }

    private fun enableAllEditFields() {
        etFullName.isEnabled = true
        etPhone.isEnabled = true
        etBio.isEnabled = true
        etEmail.isEnabled = true
        etUsername.isEnabled = true
        etGithub.isEnabled = true
        etLinkedin.isEnabled = true
        etWebsite.isEnabled = true
    }

    private fun disableAllEditFields() {
        etFullName.isEnabled = false
        etPhone.isEnabled = false
        etBio.isEnabled = false
        etEmail.isEnabled = false
        etUsername.isEnabled = false
        etGithub.isEnabled = false
        etLinkedin.isEnabled = false
        etWebsite.isEnabled = false
    }

    private fun setupLogoutButton() {
        btnLogout.setOnClickListener {
            preferenceManager.clearLoginData()
            Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.login_fragment)
        }
    }

    private fun setupChangePasswordButton() {
        btnChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Chuyển đến trang đổi mật khẩu", Toast.LENGTH_SHORT).show()
            // TODO: Implement change password navigation
        }
    }

    private fun setupChangeAvatarButton() {
        btnChangeAvatar.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            exitEditMode(saveData = true)
        }
    }

    private fun setupCancelButton() {
        btnCancel.setOnClickListener {
            exitEditMode(saveData = false)
        }
    }

    private fun handleImageSelected(imageUri: Uri) {
        try {
            Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .into(ivAvatar)

            // TODO: Upload ảnh lên server
            Toast.makeText(requireContext(), "Ảnh đã được chọn. Ấn Save để cập nhật", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSocialLinkButtons() {
        btnGithub.setOnClickListener {
            openLink(etGithub.text.toString())
        }

        btnLinkedin.setOnClickListener {
            openLink(etLinkedin.text.toString())
        }

        btnWeb.setOnClickListener {
            openLink(etWebsite.text.toString())
        }
    }

    private fun openLink(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Chưa có đường link", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val urlToOpen = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể mở link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUserData() {
        val token = preferenceManager.getToken()
        if (token == null) {
            Toast.makeText(requireContext(), "Không có token", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val updatedUser = User(
                    id = preferenceManager.getUserId() ?: "",
                    username = etUsername.text.toString(),
                    displayName = etFullName.text.toString(),
                    email = etEmail.text.toString(),
                    phone = etPhone.text.toString(),
                    bio = etBio.text.toString(),
                    avatarUrl = null,
                    avatarId = null,
                    createdAt = null,
                    hashedPassword = TODO(),
                    verified = TODO(),
                    updatedAt = TODO(),
                )

                // TODO: Call API to update user profile
                Toast.makeText(requireContext(), "Cập nhật thông tin thành công", Toast.LENGTH_SHORT).show()

                // Update local preference
                preferenceManager.saveLoginData(
                    userId = updatedUser.id,
                    username = updatedUser.username,
                    displayName = updatedUser.displayName,
                    email = updatedUser.email
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserDataFromAPI() {
        val token = preferenceManager.getToken()
        if (token == null) {
            loadUserDataFromPreference()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApiService.getProfile("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    displayUserData(user)
                    updatePreferenceWithUserData(user)
                } else {
                    loadUserDataFromPreference()
                    Toast.makeText(requireContext(), "Không thể tải dữ liệu từ server", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadUserDataFromPreference()
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserDataFromPreference() {
        val displayName = preferenceManager.getDisplayName() ?: "User"
        val email = preferenceManager.getEmail() ?: ""
        val username = preferenceManager.getUsername() ?: ""

        tvUserName.text = displayName
        etFullName.setText(displayName)
        etEmail.setText(email)
        etUsername.setText(username)
        etPhone.setText("")
        etBio.setText("")
        etGithub.setText("")
        etLinkedin.setText("")
        etWebsite.setText("")
    }

    private fun displayUserData(user: User) {
        tvUserName.text = user.displayName
        etFullName.setText(user.displayName)
        etEmail.setText(user.email)
        etUsername.setText(user.username)
        etPhone.setText(user.phone ?: "")
        etBio.setText(user.bio ?: "")

        // Load avatar nếu có URL
        if (!user.avatarUrl.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(user.avatarUrl)
                    .circleCrop()
                    .into(ivAvatar)
            } catch (e: Exception) {
                // Nếu lỗi load ảnh, giữ ảnh mặc định
            }
        }
    }

    private fun updatePreferenceWithUserData(user: User) {
        preferenceManager.saveLoginData(
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            email = user.email
        )
    }

    private fun disableEditFields(view: View) {
        disableViewRecursive(view)
    }

    private fun disableViewRecursive(view: View) {
        if (view is EditText) {
            view.isEnabled = false
            view.alpha = 0.6f
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                disableViewRecursive(view.getChildAt(i))
            }
        }
    }

    private fun showGuestMessage() {
        Toast.makeText(requireContext(), "Bạn đang là khách. Đăng nhập để chỉnh sửa thông tin", Toast.LENGTH_SHORT).show()
    }
}