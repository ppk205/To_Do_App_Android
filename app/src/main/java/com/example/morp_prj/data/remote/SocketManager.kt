package com.example.morp_prj.data.remote

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private const val SERVER_URL = "http://10.0.2.2:3001" // Sử dụng IP máy nếu chạy local bằng máy thật nhee

    private var mSocket: Socket? = null

    /**
     * Khởi tạo Socket với Token lấy từ Login/AuthRepository
     */
    @Synchronized
    fun connect(token: String) {
        if (mSocket != null && mSocket!!.connected()) {
            return // Đã kết nối rồi thì thôi
        }

        try {
            val options = IO.Options().apply {
                reconnection = true
                forceNew = true
                auth = mapOf("token" to token)
            }

            mSocket = IO.socket(SERVER_URL, options)

            setupGlobalListeners()
            mSocket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Lỗi URI Socket: ${e.message}")
        }
    }

    private fun setupGlobalListeners() {
        mSocket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "Đã kết nối Socket thành công! ID: ${mSocket?.id()}")
        }

        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Lỗi kết nối: ${args[0]}")
        }

        mSocket?.on("error") { args ->
            // Bắt lỗi từ middleware (VD: TOKEN_INVALID)
            Log.e(TAG, "Lỗi từ server: ${args[0]}")
        }
    }

    fun getSocket(): Socket? = mSocket

    fun disconnect() {
        mSocket?.disconnect()
        mSocket = null
    }
}