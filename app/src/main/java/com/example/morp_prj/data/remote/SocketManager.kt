package com.example.morp_prj.data.remote

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private const val SERVER_URL = "http://192.168.1.5:3001"

    private var mSocket: Socket? = null

    /**
     * Khởi tạo Socket với Token lấy từ Login/AuthRepository
     */
    @Synchronized
    fun connect(token: String) {
        if (mSocket != null && mSocket!!.connected()) {
            Log.d(TAG, "Socket đã kết nối rồi")
            return
        }

        try {
            val options = IO.Options().apply {
                // Force new connection
                forceNew = true

                // Enable auto reconnection
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000

                // Increase timeout
                timeout = 20000

                // Try WebSocket first, fallback to polling
                transports = arrayOf(WebSocket.NAME, "polling")

                // Authentication
                auth = mapOf("token" to token)

                // Extra headers (optional)
                extraHeaders = mapOf(
                    "Authorization" to listOf("Bearer $token")
                )
            }

            Log.d(TAG, "Đang kết nối tới: $SERVER_URL")
            mSocket = IO.socket(SERVER_URL, options)

            setupGlobalListeners()
            mSocket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Lỗi URI Socket: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo Socket: ${e.message}", e)
        }
    }

    private fun setupGlobalListeners() {
        mSocket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "✅ Đã kết nối Socket thành công! ID: ${mSocket?.id()}")
        }

        mSocket?.on("connecting") {
            Log.d(TAG, "🔄 Đang kết nối Socket...")
        }

        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = if (args.isNotEmpty()) args[0] else "Unknown error"
            Log.e(TAG, "❌ Lỗi kết nối Socket: $error")

            // Log detailed error info
            if (args.isNotEmpty() && args[0] is Exception) {
                (args[0] as? Exception)?.printStackTrace()
            }
        }

        mSocket?.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = if (args.isNotEmpty()) args[0] else "Unknown"
            Log.w(TAG, "⚠️ Socket đã ngắt kết nối: $reason")
        }

        mSocket?.on("reconnect") { args ->
            val attempt = if (args.isNotEmpty()) args[0] else "?"
            Log.d(TAG, "🔄 Reconnected sau $attempt lần thử")
        }

        mSocket?.on("reconnect_attempt") { args ->
            val attempt = if (args.isNotEmpty()) args[0] else "?"
            Log.d(TAG, "🔄 Đang thử reconnect lần thứ $attempt...")
        }

        mSocket?.on("reconnect_error") { args ->
            val error = if (args.isNotEmpty()) args[0] else "Unknown"
            Log.e(TAG, "❌ Lỗi reconnect: $error")
        }

        mSocket?.on("reconnect_failed") {
            Log.e(TAG, "❌ Reconnect thất bại hoàn toàn!")
        }

        mSocket?.on("error") { args ->
            // Bắt lỗi từ middleware (VD: TOKEN_INVALID)
            val error = if (args.isNotEmpty()) args[0] else "Unknown"
            Log.e(TAG, "❌ Lỗi từ server: $error")
        }
    }

    fun getSocket(): Socket? = mSocket

    fun isConnected(): Boolean = mSocket?.connected() ?: false

    fun disconnect() {
        Log.d(TAG, "Ngắt kết nối Socket...")
        mSocket?.disconnect()
        mSocket?.off() // Remove all listeners
        mSocket = null
    }
}