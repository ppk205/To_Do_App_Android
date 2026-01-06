package com.example.morp_prj.data.remote

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException
import com.example.morp_prj.data.api.RetrofitClient

object SocketManager {
    private const val TAG = "SocketManager"
    // Use the same base URL as RetrofitClient for consistency
    private val SERVER_URL: String
        get() = RetrofitClient.getBaseUrl()

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