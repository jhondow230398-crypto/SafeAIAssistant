package com.example.safeassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeAIAgent(
    context: Context,
    modelPath: String = "/data/local/tmp/llm_model.bin",
    private val onStatusUpdate: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val llmManager = LocalLlmManager(context, modelPath)

    init {
        scope.launch(Dispatchers.IO) {
            val ready = llmManager.initialize()
            Log.i("SafeAIAgent", "LLM initialized: $ready")
        }
    }

    fun executeIntent(userGoal: String) {
        val service = SafeAIAssistantService.instance
        if (service == null) {
            onStatusUpdate("Error: Accessibility Service not enabled.")
            return
        }

        // Fast-path heuristic for QCam launch requests
        if (userGoal.contains("qcam", ignoreCase = true) && userGoal.contains("open", ignoreCase = true)) {
            val launched = service.executeLaunchApp("qcam")
            onStatusUpdate(if (launched) "Launched QCam" else "Failed to launch QCam")
            return
        }

        scope.launch {
            val visibleNodes = service.getPermittedScreenHierarchy()
            val decision = llmManager.decideNextAction(userGoal, visibleNodes)

            if (decision.action == ActionType.LAUNCH_APP && !PolicyEngine.isPackagePermitted(decision.target)) {
                onStatusUpdate("Blocked: Policy disallows '${decision.target}'")
                return@launch
            }

            when (decision.action) {
                ActionType.LAUNCH_APP -> service.executeLaunchApp(decision.target)
                ActionType.CLICK_TEXT -> service.executeClickByText(decision.target)
                ActionType.TYPE_TEXT -> service.executeInputText(decision.target)
                ActionType.DO_NOTHING -> onStatusUpdate(decision.reasoning)
            }
        }
    }

    fun release() {
        llmManager.close()
    }
}
