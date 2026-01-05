package com.example.morp_prj.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.morp_prj.data.remote.SocketManager
import org.json.JSONObject

class TeamRealtimeRepository {
    private val socket = SocketManager.getSocket()

    private val _teamEvents = MutableLiveData<JSONObject>()
    val teamEvents: LiveData<JSONObject> = _teamEvents

    init {
        // Backend emits: teamTaskCreated, receiveTeamMessage, teamTask:statusChanged, team:memberJoined, team:joinRequest
        val events = listOf(
            "teamTaskCreated",
            "receiveTeamMessage",
            "teamTask:statusChanged",
            "team:memberJoined",
            "team:joinRequest",
        )

        events.forEach { eventName ->
            socket?.on(eventName) { args ->
                if (args.isNotEmpty()) {
                    val payload = when (val first = args[0]) {
                        is JSONObject -> first
                        else -> JSONObject(first.toString())
                    }
                    // Attach event name for UI routing if needed
                    payload.put("_event", eventName)
                    _teamEvents.postValue(payload)
                }
            }
        }
    }

    /**
     * Gọi khi người dùng mở màn hình chi tiết Team
     * Backend: socket.on('joinTeam', (teamId) => ...)
     */
    fun joinTeamRoom(teamId: String) {
        if (socket?.connected() == true) {
            Log.d("TeamRepo", "Joining room: team:$teamId")
            socket.emit("joinTeam", teamId)
        } else {
            Log.w("TeamRepo", "Socket chưa kết nối, không thể join room")
        }
    }

    /**
     * Gọi khi người dùng thoát màn hình Team
     * Backend: socket.on('leaveTeam', (teamId) => ...)
     */
    fun leaveTeamRoom(teamId: String) {
        if (socket?.connected() == true) {
            Log.d("TeamRepo", "Leaving room: team:$teamId")
            socket.emit("leaveTeam", teamId)
        }
    }
}