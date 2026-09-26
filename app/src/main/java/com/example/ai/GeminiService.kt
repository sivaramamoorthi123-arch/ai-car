package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.domain.model.GeminiRobotCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiService {
    private val tag = "GeminiService"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val model = "gemini-3.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    suspend fun parseUserIntentToCommand(userInput: String): Result<GeminiRobotCommand> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Add your key in the Secrets panel or test via Demo Mode.")
            )
        }

        try {
            val systemPrompt = """
                You are the autonomous robotics mission planner for an ESP32 robot car equipped with 2 DC motors, TB6612FNG driver, ultrasonic obstacle distance sensors, robot face screen, and ESP32-CAM.
                Translate the human's natural language request into a validated high-level JSON command.
                NEVER emit individual motor pulses. The robot handles real-time motor control locally.
                
                Strict JSON Output Format:
                {
                  "type": "ROBOT_COMMAND",
                  "action": "MOVE" | "TURN" | "STOP" | "SEARCH_OBJECT" | "APPROACH_OBJECT" | "FOLLOW_OBJECT" | "RETURN_HOME" | "EXPLORE" | "SPEAK" | "FACE" | "STATUS",
                  "direction": "FORWARD" | "BACKWARD" | "LEFT" | "RIGHT" | "STOP" | null,
                  "distance_cm": number (e.g. 20, 50, null if continuous),
                  "turn_angle": number (e.g. 90, 180, null),
                  "speed_percent": number (10 to 100),
                  "target": string or null (e.g. "table", "chair", "person"),
                  "face_expression": "NORMAL" | "HAPPY" | "SAD" | "ANGRY" | "SURPRISED" | "THINKING" | "LISTENING" | "SPEAKING" | "SLEEPING" | "ERROR",
                  "speech": string (brief friendly response the robot speaks),
                  "reasoning": string (brief explanation of mission plan)
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                // Contents
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userInput)
                            })
                        })
                    })
                })
                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                // Generation Config (request application/json)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "$baseUrl?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(tag, "Gemini API failed (${response.code}): $responseBody")
                    return@withContext Result.failure(Exception("Gemini error (${response.code}): ${response.message}"))
                }

                val parsedJson = JSONObject(responseBody)
                val candidates = parsedJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(Exception("No response from Gemini"))
                }

                val content = candidates.getJSONObject(0).getJSONObject("content")
                val text = content.getJSONArray("parts").getJSONObject(0).getString("text")

                val commandJson = JSONObject(text.trim())
                val command = GeminiRobotCommand(
                    type = commandJson.optString("type", "ROBOT_COMMAND"),
                    action = commandJson.optString("action", "MOVE").uppercase(),
                    direction = if (commandJson.has("direction") && !commandJson.isNull("direction")) commandJson.optString("direction").uppercase() else null,
                    distance_cm = if (commandJson.has("distance_cm") && !commandJson.isNull("distance_cm")) commandJson.optInt("distance_cm") else null,
                    turn_angle = if (commandJson.has("turn_angle") && !commandJson.isNull("turn_angle")) commandJson.optInt("turn_angle") else null,
                    speed_percent = if (commandJson.has("speed_percent") && !commandJson.isNull("speed_percent")) commandJson.optInt("speed_percent") else 50,
                    target = if (commandJson.has("target") && !commandJson.isNull("target")) commandJson.optString("target") else null,
                    face_expression = commandJson.optString("face_expression", "NORMAL").uppercase(),
                    speech = commandJson.optString("speech", "Executing mission."),
                    reasoning = commandJson.optString("reasoning", "Autonomous plan.")
                )

                Result.success(command)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error invoking Gemini: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeVisionFrame(bitmap: Bitmap, prompt: String = "Identify main objects, obstacle hazards, and distance estimation for this robot navigation scene."): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are an AI vision sensor for an autonomous robot car. Analyze the scene concisely: 1. Main detected objects. 2. Estimated distance. 3. Safe navigation recommendation. Prompt: $prompt")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                })
            }

            val url = "$baseUrl?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Vision error (${response.code}): ${response.message}"))
                }

                val parsed = JSONObject(responseBody)
                val candidates = parsed.optJSONArray("candidates")
                val text = candidates?.getJSONObject(0)?.getJSONObject("content")?.getJSONArray("parts")?.getJSONObject(0)?.getString("text")
                    ?: "No vision analysis returned"
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
