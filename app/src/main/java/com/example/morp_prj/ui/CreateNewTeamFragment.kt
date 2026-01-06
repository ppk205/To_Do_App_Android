package com.example.morp_prj.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.CreateTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.CloudinaryHelper
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CreateNewTeamFragment : Fragment() {

    private lateinit var etTeamName: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etTagInput: TextInputEditText
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var btnCreateTeam: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var imgTeamAvatar: ImageView
    private lateinit var btnChangeAvatar: ImageView
    private lateinit var switchMemberDirectory: androidx.appcompat.widget.SwitchCompat

    private lateinit var preferenceManager: PreferenceManager

    // Danh sách tags lưu trữ tạm thời
    private val tagsList = mutableListOf<String>()

    private var selectedAvatarUri: Uri? = null
    private var uploadedAvatarUrl: String? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri = data?.data
            if (uri != null) {
                selectedAvatarUri = uri
                Glide.with(this).load(uri).centerCrop().into(imgTeamAvatar)
                uploadAvatar(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_new_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())
        initViews(view)
        setupListeners()
        setupTagInput()
    }

    private fun initViews(view: View) {
        etTeamName = view.findViewById(R.id.etTeamName)
        etDescription = view.findViewById(R.id.etDescription)
        etTagInput = view.findViewById(R.id.etTagInput)
        chipGroupTags = view.findViewById(R.id.chipGroupTags)
        btnCreateTeam = view.findViewById(R.id.btnCreateTeam)
        btnBack = view.findViewById(R.id.btnBack)
        imgTeamAvatar = view.findViewById(R.id.imgTeamAvatar)
        btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar)
        switchMemberDirectory = view.findViewById(R.id.switchMemberDirectory)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Mở chọn ảnh từ gallery
        val imagePickerAction = View.OnClickListener {
            openImagePicker()
        }
        btnChangeAvatar.setOnClickListener(imagePickerAction)
        imgTeamAvatar.setOnClickListener(imagePickerAction)

        btnCreateTeam.setOnClickListener {
            validateAndCreateTeam()
        }
    }

    private fun setupTagInput() {
        etTagInput.setOnEditorActionListener { v, actionId, event ->
            // Kiểm tra nếu người dùng nhấn Done trên bàn phím hoặc Enter cứng
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val tagText = etTagInput.text.toString().trim()
                if (tagText.isNotEmpty()) {
                    addTagChip(tagText)
                    etTagInput.text?.clear() // Xóa text sau khi add
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun addTagChip(text: String) {
        // Tránh trùng lặp
        if (tagsList.contains(text)) return

        val chip = Chip(requireContext())
        chip.text = text
        chip.isCloseIconVisible = true // Hiện nút xóa
        chip.setOnCloseIconClickListener {
            chipGroupTags.removeView(chip)
            tagsList.remove(text)
        }

        chipGroupTags.addView(chip)
        tagsList.add(text)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Team Avatar"))
    }

    private fun uploadAvatar(uri: Uri) {
        // Ensure Cloudinary is ready
        CloudinaryHelper.init(requireContext().applicationContext)

        // Show quick UI hint
        Toast.makeText(requireContext(), "Uploading avatar...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val result = CloudinaryHelper.uploadImage(uri)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { url ->
                        uploadedAvatarUrl = url
                        Toast.makeText(requireContext(), "Avatar uploaded", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        uploadedAvatarUrl = null
                        Toast.makeText(requireContext(), "Upload failed: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                        // Reset preview to placeholder on error
                        imgTeamAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
                    }
                )
            }
        }
    }

    private fun validateAndCreateTeam() {
        val name = etTeamName.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (name.isEmpty()) {
            etTeamName.error = "Team name is required"
            return
        }

        val userId = preferenceManager.getUserId() ?: return

        // Tạo request object
        val request = CreateTeamRequest(
            name = name,
            description = description,
            createdBy = userId,
            tags = tagsList,
            avatarUrl = uploadedAvatarUrl,
            allowMemberDirectory = switchMemberDirectory.isChecked
        )

        // Disable button to prevent double click
        btnCreateTeam.isEnabled = false
        btnCreateTeam.text = "Creating..."

        // Gọi API
        RetrofitClient.teamApiService.createTeam(request).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful && response.body() != null) {
                    val createdTeam = response.body()!!

                    // Create group chat automatically
                    createTeamGroupChat(createdTeam)
                } else {
                    btnCreateTeam.isEnabled = true
                    btnCreateTeam.text = "Create Team"
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(context, "Failed to create team: $errorBody", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Team>, t: Throwable) {
                btnCreateTeam.isEnabled = true
                btnCreateTeam.text = "Create Team"
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createTeamGroupChat(team: Team) {
        val currentUserId = preferenceManager.getUserId()

        CoroutineScope(Dispatchers.IO).launch {
            try {

                withContext(Dispatchers.Main) {
                    Log.d("CreateNewTeamFragment", "Auto-created group chat for team: ${team.name}")
                    val inviteCode = team.inviteCode ?: "No Code"
                    // Gửi kết quả về fragment cha nếu cần (để refresh list)
                    parentFragmentManager.setFragmentResult("team_created", Bundle())
                    // Hiển thị Dialog thành công
                    onTeamCreatedSuccess(inviteCode)
                }
            } catch (e: Exception) {
                Log.e("CreateNewTeamFragment", "Failed to auto-create group chat: ${e.message}")
                withContext(Dispatchers.Main) {
                    val inviteCode = team.inviteCode ?: "No Code"
                    parentFragmentManager.setFragmentResult("team_created", Bundle())
                    onTeamCreatedSuccess(inviteCode)
                }
            }
        }
    }

    private fun onTeamCreatedSuccess(inviteCode: String) {
        // Inflate layout mới
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_invite_code_display, null)

        val tvCode = dialogView.findViewById<TextView>(R.id.tvInviteCode)
        val btnCopy = dialogView.findViewById<View>(R.id.btnCopy) // Bây giờ là ImageView
        val btnGoToTeam = dialogView.findViewById<View>(R.id.btnGoToTeam)

        tvCode.text = inviteCode

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Invite Code", inviteCode)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        btnGoToTeam.setOnClickListener {
            dialog.dismiss()
            findNavController().popBackStack()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}