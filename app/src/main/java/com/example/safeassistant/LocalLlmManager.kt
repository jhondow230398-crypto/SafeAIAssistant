package com.example.safeassistant

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ActionType {
    LAUNCH_APP,
    CLICK_TEXT,
    TYPE_TEXT,
    DO_NOTHING
}

data class AgentCommand(
    val action: ActionType,
    val target: String,
    val reasoning: String
)

class LocalLlmManager(private val context: Context, private val modelPath: String) {

    private var llmInference: LlmInference? = null

    fun initialize(): Boolean {
        return try {
            val file = File(modelPath)
            if (!file.exists()) {
                android.util.Log.e("LocalLlmManager", "Model not found at: $modelPath")
                return false
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(256)
                .setTemperature(0.1f)
                .setTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            android.util.Log.e("LocalLlmManager", "Init error: ${e.message}")
            false
        }
    }

    suspend fun decideNextAction(
        userRequest: String,
        visibleUiElements: List<String>
    ): AgentCommand = withContext(Dispatchers.Default) {
        val engine = llmInference ?: return@withContext AgentCommand(
            ActionType.DO_NOTHING, "", "MediaPipe uninitialized"
        )

        val prompt = buildPrompt(userRequest, visibleUiElements)
        try {
            val rawOutput = engine.generateResponse(prompt)
            parseModelOutput(rawOutput)
        } catch (e: Exception) {
            AgentCommand(ActionType.DO_NOTHING, "", "Inference failed: ${e.message}")
        }
    }

    private fun buildPrompt(userRequest: String, visibleUiElements: List<String>): String {
        val screenContext = if (visibleUiElements.isEmpty()) {
            "None (Empty or Restricted Screen)"
        } else {
            visibleUiElements.take(25).joinToString(", ") { "\"$it\"" }
        }

        return """
            You are an on-device phone assistant. Decide the single next action.
            
            RULES:
            1. Allowed actions: LAUNCH_APP, CLICK_TEXT, TYPE_TEXT, DO_NOTHING.
            2. Camera/monitoring tools like "qcam" ARE EXPLICITLY PERMITTED.
            3. Forbidden: calls, SMS, social media, finance/banking/UPI apps.
            4. Emit strictly a raw JSON block:
            {"action": "LAUNCH_APP|CLICK_TEXT|TYPE_TEXT|DO_NOTHING", "target": "string", "reasoning": "string"}
            
            CONTEXT:
            - User Command: "$userRequest"
            - Visible UI Elements: [$screenContext]
            
            JSON Response:
        """.trimIndent()
    }

    private fun parseModelOutput(raw: String): AgentCommand {
        return try {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start == -1 || end == -1) {
                return AgentCommand(ActionType.DO_NOTHING, "", "Failed to locate JSON")
            }

            val json = JSONObject(raw.substring(start, end + 1))
            val action = when (json.optString("action").uppercase()) {
                "LAUNCH_APP" -> ActionType.LAUNCH_APP
                "CLICK_TEXT" -> ActionType.CLICK_TEXT
                "TYPE_TEXT" -> ActionType.TYPE_TEXT
                else -> ActionType.DO_NOTHING
            }

            AgentCommand(
                action = action,
                target = json.optString("target", ""),
                reasoning = json.optString("reasoning", "")
            )
        } catch (e: Exception) {
            AgentCommand(ActionType.DO_NOTHING, "", "JSON parse exception: ${e.message}")
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
