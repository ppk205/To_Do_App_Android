package com.example.morp_prj.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.morp_prj.data.model.ChatMessage
import com.example.morp_prj.data.remote.SocketManager
import com.google.gson.Gson
import org.json.JSONObject

class TeamChatViewModel : ViewModel() {
    private val socket = SocketManager.getSocket()
    private val gson = Gson()

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

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

    fun sendMessage(teamId: String, senderId: String, senderName: String, content: String) {
        val data = JSONObject()
        data.put("teamId", teamId)
        data.put("senderId", senderId)
        data.put("senderName", senderName)
        data.put("content", content)

        socket?.emit("sendTeamMessage", data)
    }
}