package com.example.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Esp32RestClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(ip: String, port: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/status"
        try {
            val request = Request.Builder().url(url).build()
            val start = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    Pair(true, "ESP32 responding ($elapsed ms): ${body.take(60)}")
                } else {
                    Pair(false, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage ?: "Timeout"}")
        }
    }

    suspend fun captureSnapshot(camUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(camUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
