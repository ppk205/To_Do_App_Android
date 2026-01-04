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
        // Lắng nghe các sự kiện chung của Team
        socket?.on("team_update") { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                _teamEvents.postValue(data)
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