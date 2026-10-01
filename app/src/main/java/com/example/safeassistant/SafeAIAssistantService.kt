package com.example.safeassistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SafeAIAssistantService : AccessibilityService() {

    companion object {
        private const val TAG = "SafeAIAssistant"
        var instance: SafeAIAssistantService? = null
            private set
    }

    private var currentForegroundPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility Service bound.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            currentForegroundPackage = pkg

            if (!PolicyEngine.isPackagePermitted(pkg)) {
                Log.w(TAG, "Restricted app active: $pkg. Agent controls blocked.")
            }
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun getPermittedScreenHierarchy(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val pkg = root.packageName?.toString() ?: currentForegroundPackage

        if (!PolicyEngine.isPackagePermitted(pkg)) {
            Log.w(TAG, "Refusing screen inspection on restricted package: $pkg")
            return emptyList()
        }

        val textElements = mutableListOf<String>()
        collectNodeText(root, textElements)
        return textElements
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.let { if (it.isNotBlank()) list.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) list.add(it.toString()) }

        for (i in 0 until node.childCount) {
            collectNodeText(node.getChild(i), list)
        }
    }

    fun resolvePackageName(appNameQuery: String): String? {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName
            val label = pm.getApplicationLabel(appInfo).toString()

            if (pkg.contains(appNameQuery, ignoreCase = true) || label.contains(appNameQuery, ignoreCase = true)) {
                if (PolicyEngine.isPackagePermitted(pkg)) {
                    return pkg
                }
            }
        }
        return null
    }

    fun executeLaunchApp(target: String): Boolean {
        val actualPackage = if (!target.contains(".")) {
            resolvePackageName(target) ?: target
        } else {
            target
        }

        if (!PolicyEngine.isPackagePermitted(actualPackage)) {
            Log.e(TAG, "ACCESS DENIED: Violates policy: $actualPackage")
            return false
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(actualPackage)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            true
        } else {
            Log.e(TAG, "No launch intent for: $actualPackage")
            false
        }
    }

    fun executeClickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: currentForegroundPackage

        if (!PolicyEngine.isPackagePermitted(pkg)) {
            Log.e(TAG, "ACCESS DENIED: Screen click blocked on: $pkg")
            return false
        }

        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return success
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent.recycle()
                    return success
                }
                parent = parent.parent
            }
            node.recycle()
        }
        return false
    }

    fun executeInputText(input: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: currentForegroundPackage

        if (!PolicyEngine.isPackagePermitted(pkg)) {
            Log.e(TAG, "ACCESS DENIED: Text input blocked on: $pkg")
            return false
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input)
        }
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focusedNode.recycle()
        return result
    }
}
