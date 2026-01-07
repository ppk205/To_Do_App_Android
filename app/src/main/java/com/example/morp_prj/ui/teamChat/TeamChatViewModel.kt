package com.example.morp_prj.ui.chat

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.ChatMessage
import com.example.morp_prj.data.model.decrypt
import com.example.morp_prj.data.model.encrypt
import com.example.morp_prj.data.remote.SocketManager
import com.example.morp_prj.security.CryptoManager
import com.example.morp_prj.security.KeyManager
import com.google.gson.Gson
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamChatViewModel(private val context: Context) : ViewModel() {
    private val socket = SocketManager.getSocket()
    private val gson = Gson()
    private val keyManager = KeyManager(context)

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        socket?.on("receiveTeamMessage") { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                try {
                    val encryptedMessage = gson.fromJson(data.toString(), ChatMessage::class.java)

                    // 🔓 Get or generate team key (DETERMINISTIC from teamId)
                    var teamKey = keyManager.getCachedTeamKey(encryptedMessage.teamId)

                    if (teamKey == null) {
                        android.util.Log.w("TeamChatViewModel", "⚠️ No key for receiving message, generating deterministic key...")
                        try {
                            // Generate DETERMINISTIC key so all team members have SAME key
                            teamKey = CryptoManager.generateDeterministicTeamKey(encryptedMessage.teamId)
                            keyManager.cacheTeamKey(encryptedMessage.teamId, teamKey)
                            android.util.Log.d("TeamChatViewModel", "✅ Deterministic key generated for receiving")
                        } catch (e: Exception) {
                            android.util.Log.e("TeamChatViewModel", "Failed to generate key", e)
                        }
                    }

                    // Decrypt message
                    val decryptedMessage = if (teamKey != null) {
                        encryptedMessage.decrypt(teamKey)
                    } else {
                        android.util.Log.w("TeamChatViewModel", "No team key for ${encryptedMessage.teamId}, displaying encrypted")
                        encryptedMessage // Fallback: display as-is if no key
                    }

                    val currentList = _messages.value ?: mutableListOf()
                    val newList = ArrayList(currentList)
                    newList.add(decryptedMessage)

                    _messages.postValue(newList)
                } catch (e: Exception) {
                    android.util.Log.e("TeamChatViewModel", "Error processing message", e)
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendMessage(
        teamId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        content: String
    ) {
        try {
            // 🔒 Get team encryption key
            var teamKey = keyManager.getCachedTeamKey(teamId)

            // If no key exists, generate one immediately (DETERMINISTIC)
            if (teamKey == null) {
                android.util.Log.w("TeamChatViewModel", "⚠️ No encryption key found for team $teamId, generating deterministic key...")

                try {
                    // Generate DETERMINISTIC team key so all members get SAME key
                    teamKey = CryptoManager.generateDeterministicTeamKey(teamId)

                    // Cache it
                    keyManager.cacheTeamKey(teamId, teamKey)

                    android.util.Log.d("TeamChatViewModel", "✅ Deterministic encryption key generated for team $teamId")
                } catch (e: Exception) {
                    android.util.Log.e("TeamChatViewModel", "❌ Failed to generate encryption key", e)
                    _errorMessage.postValue("Cannot generate encryption key. Please try again.")
                    return
                }
            }

            // 🔒 Encrypt message content
            val encryptedContent = CryptoManager.encryptWithTeamKey(content, teamKey)

            if (encryptedContent == null) {
                android.util.Log.e("TeamChatViewModel", "Encryption failed")
                _errorMessage.postValue("Failed to encrypt message")
                return
            }

            android.util.Log.d("TeamChatViewModel", "Message encrypted: ${content.take(20)}... -> ${encryptedContent.take(30)}...")

            // Send encrypted message to server
            val data = JSONObject()
            data.put("teamId", teamId)
            data.put("senderId", senderId)
            data.put("senderName", senderName)
            data.put("senderAvatar", senderAvatar)
            data.put("content", encryptedContent) // 🔒 Send encrypted content

            socket?.emit("sendTeamMessage", data)

        } catch (e: Exception) {
            android.util.Log.e("TeamChatViewModel", "Error sending message", e)
            _errorMessage.postValue("Failed to send message: ${e.message}")
        }
    }

    private val teamApiService = RetrofitClient.teamApiService

    fun loadHistory(teamId: String) {
        teamApiService.getTeamMessages(teamId).enqueue(object : Callback<List<ChatMessage>> {
            override fun onResponse(
                call: Call<List<ChatMessage>>,
                response: Response<List<ChatMessage>>
            ) {
                if (response.isSuccessful) {
                    val encryptedHistoryList = response.body() ?: emptyList()

                    // 🔓 Get or generate team key
                    var teamKey = keyManager.getCachedTeamKey(teamId)

                    if (teamKey == null && encryptedHistoryList.isNotEmpty()) {
                        android.util.Log.w("TeamChatViewModel", "⚠️ No key for loading history, generating deterministic key...")
                        try {
                            // Generate DETERMINISTIC key
                            teamKey = CryptoManager.generateDeterministicTeamKey(teamId)
                            keyManager.cacheTeamKey(teamId, teamKey)
                            android.util.Log.d("TeamChatViewModel", "✅ Deterministic key generated for history")
                        } catch (e: Exception) {
                            android.util.Log.e("TeamChatViewModel", "Failed to generate key", e)
                        }
                    }

                    // Decrypt messages
                    val decryptedList = if (teamKey != null) {
                        encryptedHistoryList.map { it.decrypt(teamKey) }
                    } else {
                        android.util.Log.w("TeamChatViewModel", "No team key, displaying encrypted messages")
                        encryptedHistoryList // Fallback: show encrypted if no key
                    }

                    val newList = ArrayList<ChatMessage>()
                    newList.addAll(decryptedList)

                    _messages.postValue(newList)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Undefined Error"
                    _errorMessage.postValue("Server Error: $errorBody")
                }
            }

            override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                android.util.Log.e("TeamChatViewModel", "Cannot load conversation history: ${t.message}", t)
                _errorMessage.postValue("Cannot load messages: ${t.localizedMessage ?: "Connection Error"}")
            }
        })
    }
}