package com.example.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class Esp32CameraStreamer(
    private val scope: CoroutineScope
) {
    private val tag = "Esp32CameraStreamer"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _liveFrame = MutableStateFlow<Bitmap?>(null)
    val liveFrame: StateFlow<Bitmap?> = _liveFrame.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private var streamJob: Job? = null

    fun startStream(url: String) {
        stopStream()
        _isStreaming.value = true
        _streamError.value = null

        streamJob = scope.launch(Dispatchers.IO) {
            try {
                // If snapshot polling URL (e.g. /capture or /jpg), loop snapshots
                if (url.endsWith(".jpg") || url.contains("/capture") || url.contains("/snapshot")) {
                    pollSnapshots(url)
                } else {
                    // Try MJPEG stream or fallback to polling
                    readMjpegStream(url)
                }
            } catch (e: Exception) {
                Log.e(tag, "Camera stream error: ${e.message}")
                _streamError.value = "Camera offline: ${e.message}"
                _isStreaming.value = false
            }
        }
    }

    private suspend fun pollSnapshots(url: String) {
        val request = Request.Builder().url(url).build()
        while (scope.isActive && _isStreaming.value) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null) {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                _liveFrame.value = bmp
                                _streamError.value = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient frame loss
            }
            delay(100L) // ~10 FPS snapshot polling
        }
    }

    private suspend fun readMjpegStream(streamUrl: String) {
        val request = Request.Builder().url(streamUrl).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _streamError.value = "HTTP ${response.code} from ESP32-CAM"
                    return
                }

                val body = response.body ?: return
                val inputStream: InputStream = body.byteStream()
                val buffer = ByteArray(65536)
                val frameBuffer = mutableListOf<Byte>()

                var bytesRead: Int
                while (scope.isActive && _isStreaming.value) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    for (i in 0 until bytesRead) {
                        frameBuffer.add(buffer[i])
                        val size = frameBuffer.size
                        // Detect JPEG End of Image marker 0xFF, 0xD9
                        if (size >= 2 && frameBuffer[size - 2] == 0xFF.toByte() && frameBuffer[size - 1] == 0xD9.toByte()) {
                            // Find Start of Image 0xFF, 0xD8
                            val startIndex = findJpegStart(frameBuffer)
                            if (startIndex >= 0) {
                                val jpegBytes = frameBuffer.subList(startIndex, size).toByteArray()
                                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                if (bmp != null) {
                                    _liveFrame.value = bmp
                                }
                            }
                            frameBuffer.clear()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _streamError.value = e.localizedMessage ?: "Stream disconnect"
        }
    }

    private fun findJpegStart(buffer: List<Byte>): Int {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }

    fun stopStream() {
        _isStreaming.value = false
        streamJob?.cancel()
        streamJob = null
    }
}
