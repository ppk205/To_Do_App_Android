package com.example.morp_prj.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.ChatMessage
import com.example.morp_prj.data.remote.SocketManager
import com.google.gson.Gson
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamChatViewModel : ViewModel() {
    private val socket = SocketManager.getSocket()
    private val gson = Gson()

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        socket?.on("receiveTeamMessage") { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                try {
                    val message = gson.fromJson(data.toString(), ChatMessage::class.java)
                    val currentList = _messages.value ?: mutableListOf()
                    val newList = ArrayList(currentList)
                    newList.add(message)

                    _messages.postValue(newList)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendMessage(teamId: String, senderId: String, senderName: String, senderAvatar: String?, content: String) {
        val data = JSONObject()
        data.put("teamId", teamId)
        data.put("senderId", senderId)
        data.put("senderName", senderName)
        data.put("senderAvatar", senderAvatar)
        data.put("content", content)

        socket?.emit("sendTeamMessage", data)
    }

    private val teamApiService = RetrofitClient.teamApiService

    fun loadHistory(teamId: String) {
        teamApiService.getTeamMessages(teamId).enqueue(object : Callback<List<ChatMessage>> {
            override fun onResponse(
                call: Call<List<ChatMessage>>,
                response: Response<List<ChatMessage>>
            ) {
                if (response.isSuccessful) {
                    val historyList = response.body() ?: emptyList()

                    val newList = ArrayList<ChatMessage>()
                    newList.addAll(historyList)

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