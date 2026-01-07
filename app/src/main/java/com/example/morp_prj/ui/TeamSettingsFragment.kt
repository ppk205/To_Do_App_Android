package com.example.morp_prj.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.data.model.UpdateTeamRequest
import com.example.morp_prj.utils.CloudinaryHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TeamSettingsFragment : Fragment(R.layout.fragment_team_settings) {

    private var teamId: String = ""
    private var userRole: String = "member"

    // UI Components
    private lateinit var etTeamName: EditText
    private lateinit var etDescription: EditText
    private lateinit var tvInviteCode: TextView
    private lateinit var btnRefreshCode: ImageButton
    private lateinit var btnSaveChanges: AppCompatButton
    private lateinit var btnDeleteTeam: AppCompatButton

    // Components cho Tags (ChipGroup)
    private lateinit var etNewTag: EditText
    private lateinit var btnAddTag: ImageButton
    private lateinit var chipGroupTags: ChipGroup

    // Avatar components
    private lateinit var imgAvatar: ImageView
    private lateinit var btnChangeAvatar: ImageButton
    private var uploadedAvatarUrl: String? = null
    private var selectedAvatarUri: Uri? = null

    // Allow member directory switch
    private lateinit var switchMemberDirectory: androidx.appcompat.widget.SwitchCompat

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                selectedAvatarUri = it
                Glide.with(this).load(it).centerCrop().into(imgAvatar)
                uploadAvatar(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId", "")
            userRole = it.getString("role", "member")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRoleBasedUI()
        loadTeamDetails()
        setupEvents(view)
    }

    private fun initViews(view: View) {
        // Ánh xạ các view cơ bản
        etTeamName = view.findViewById(R.id.etTeamName)
        etDescription = view.findViewById(R.id.etDescription)
        tvInviteCode = view.findViewById(R.id.tvInviteCode)
        btnRefreshCode = view.findViewById(R.id.btnRefreshCode)
        btnSaveChanges = view.findViewById(R.id.btnSaveChanges)
        btnDeleteTeam = view.findViewById(R.id.btnDeleteTeam)

        // Ánh xạ các view cho phần Tags
        etNewTag = view.findViewById(R.id.etNewTag)
        btnAddTag = view.findViewById(R.id.btnAddTag)
        chipGroupTags = view.findViewById(R.id.chipGroupTags)

        // Ánh xạ các view cho phần Avatar
        imgAvatar = view.findViewById(R.id.imgTeamAvatar)
        btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar)

        // Ánh xạ switch cho member directory
        switchMemberDirectory = view.findViewById(R.id.switchMemberDirectory)
    }

    private fun setupRoleBasedUI() {
        if (userRole.equals("manager", ignoreCase = true)) {
            btnDeleteTeam.visibility = View.VISIBLE
        } else {
            btnDeleteTeam.visibility = View.GONE
        }
    }

    private fun setupEvents(view: View) {
        // Nút Back
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Lưu thay đổi
        btnSaveChanges.setOnClickListener {
            updateTeamInfo()
        }

        // Tạo mã mời mới
        btnRefreshCode.setOnClickListener {
            showConfirmDialog("Refresh Invite Code", "The old code will be invalid. Continue?") {
                generateNewInviteCode()
            }
        }

        // Xóa Team
        btnDeleteTeam.setOnClickListener {
            showConfirmDialog("Delete Team", "This action cannot be undone. Are you sure?") {
                deleteTeam()
            }
        }

        // --- SỰ KIỆN THÊM TAG ---

        // 1. Bấm nút dấu cộng (+)
        btnAddTag.setOnClickListener {
            addNewTag()
        }

        // 2. Bấm phím Enter (Done) trên bàn phím ảo
        etNewTag.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                addNewTag()
                true
            } else {
                false
            }
        }

        // Chọn ảnh đại diện
        btnChangeAvatar.setOnClickListener { openImagePicker() }
        imgAvatar.setOnClickListener { openImagePicker() }
    }

    // --- LOGIC XỬ LÝ TAGS (CHIP GROUP) ---

    private fun addNewTag() {
        val text = etNewTag.text.toString().trim()
        if (text.isNotEmpty()) {
            // Kiểm tra trùng lặp (nếu cần)
            if (!isTagExists(text)) {
                addChipToGroup(text)
                etNewTag.text.clear() // Xóa ô nhập sau khi thêm
            } else {
                Toast.makeText(context, "Tag already exists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isTagExists(tagText: String): Boolean {
        for (i in 0 until chipGroupTags.childCount) {
            val chip = chipGroupTags.getChildAt(i) as? Chip
            if (chip?.text.toString().equals(tagText, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun addChipToGroup(tagText: String) {
        val chip = Chip(requireContext())
        chip.text = tagText
        chip.isCloseIconVisible = true // Hiển thị nút 'X' để xóa

        // Xử lý sự kiện xóa chip
        chip.setOnCloseIconClickListener {
            chipGroupTags.removeView(chip)
        }

        // Style cho Chip (Optional: Đặt màu nền xám nhạt)
        try {
            chip.setChipBackgroundColorResource(R.color.bg_tag_grey_color_state)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        } catch (e: Exception) {
            // Fallback nếu không tìm thấy resource màu
        }

        chipGroupTags.addView(chip)
    }

    // --- API CALLS ---

    private fun loadTeamDetails() {
        if (teamId.isEmpty()) return

        RetrofitClient.teamApiService.getTeamDetail(teamId).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful) {
                    val team = response.body()
                    etTeamName.setText(team?.name)
                    etDescription.setText(team?.description)
                    tvInviteCode.text = team?.inviteCode ?: "NO CODE"
                    uploadedAvatarUrl = team?.avatarUrl
                    Glide.with(requireContext())
                        .load(team?.avatarUrl)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imgAvatar)

                    // Load Tags từ server lên ChipGroup
                    chipGroupTags.removeAllViews()
                    team?.tags?.forEach { tag ->
                        addChipToGroup(tag)
                    }

                    // Load member directory setting
                    switchMemberDirectory.isChecked = team?.allowMemberDirectory ?: false
                }
            }
            override fun onFailure(call: Call<Team>, t: Throwable) {
                Toast.makeText(context, "Failed to load team info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTeamInfo() {
        val name = etTeamName.text.toString().trim()
        val desc = etDescription.text.toString().trim()

        // Lấy danh sách tags từ ChipGroup
        val tagsList = mutableListOf<String>()
        for (i in 0 until chipGroupTags.childCount) {
            val chip = chipGroupTags.getChildAt(i) as? Chip
            chip?.let {
                tagsList.add(it.text.toString())
            }
        }

        if (name.isEmpty()) {
            etTeamName.error = "Name is required"
            return
        }

        // Tạo request object (bao gồm tags)
        val request = UpdateTeamRequest(
            name = name,
            description = desc,
            tags = tagsList,
            avatarUrl = uploadedAvatarUrl,
            allowMemberDirectory = switchMemberDirectory.isChecked
        )

        RetrofitClient.teamApiService.updateTeam(teamId, request).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Team updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp() // Quay lại màn hình trước
                } else {
                    Toast.makeText(context, "Update failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Team>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun generateNewInviteCode() {
        RetrofitClient.teamApiService.regenerateInviteCode(teamId).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful) {
                    val newCode = response.body()?.inviteCode
                    tvInviteCode.text = newCode
                    Toast.makeText(context, "New code generated", Toast.LENGTH_SHORT).show()
                } else {
                    // --- THÊM LOG ĐỂ DEBUG ---
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string()
                    Log.e("TeamSettings", "Error Code: $errorCode, Body: $errorBody")

                    Toast.makeText(context, "Failed: $errorCode", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Team>, t: Throwable) {
                Log.e("TeamSettings", "Network Error", t) // Log lỗi mạng/Retrofit
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteTeam() {
        RetrofitClient.teamApiService.deleteTeam(teamId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Team deleted", Toast.LENGTH_LONG).show()
                    exitToHome()
                } else {
                    Toast.makeText(context, "Delete failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun exitToHome() {
        val nav = activity?.findNavController(R.id.nav_host_fragment) ?: findNavController()
        nav.popBackStack(nav.graph.startDestinationId, false)
        runCatching { nav.navigate(R.id.menu_team) }
    }

    // Hàm tiện ích hiển thị Dialog xác nhận
    private fun showConfirmDialog(title: String, msg: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Team Avatar"))
    }

    private fun uploadAvatar(uri: Uri) {
        CloudinaryHelper.init(requireContext().applicationContext)
        Toast.makeText(requireContext(), "Uploading avatar...", Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = CloudinaryHelper.uploadImage(uri)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                result.fold(
                    onSuccess = { url ->
                        uploadedAvatarUrl = url
                        Toast.makeText(requireContext(), "Avatar uploaded", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        uploadedAvatarUrl = null
                        Toast.makeText(requireContext(), "Upload failed: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}