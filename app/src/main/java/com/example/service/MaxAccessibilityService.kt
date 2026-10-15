package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MaxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _lastActivePackage.value = pkg
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceConnected.value = false
        }
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performOpenNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun performOpenQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun tapByTextOrCoordinate(target: String): Pair<Boolean, String> {
        val trimmed = target.trim()
        // Check if target is coordinates like "540,1200" or "540, 1200"
        val coordRegex = Regex("""^(\d+)\s*[,xX]\s*(\d+)$""")
        val match = coordRegex.matchEntire(trimmed)
        if (match != null) {
            val x = match.groupValues[1].toFloatOrNull() ?: return false to "Invalid X coordinate"
            val y = match.groupValues[2].toFloatOrNull() ?: return false to "Invalid Y coordinate"
            val dispatched = dispatchTapGesture(x, y)
            return if (dispatched) {
                true to "Dispatched gesture tap at ($x, $y) via AccessibilityService"
            } else {
                false to "AccessibilityService gesture dispatch failed at ($x, $y)"
            }
        }

        // Otherwise search active window nodes by text or contentDescription
        val root = rootInActiveWindow
            ?: return false to "No active window content available to AccessibilityService"

        val matchingNode = findNodeByTextOrDescription(root, trimmed)
        if (matchingNode != null) {
            val clicked = clickNodeOrParent(matchingNode)
            return if (clicked) {
                true to "Verified click on UI element matching '$trimmed' in package ${root.packageName ?: "unknown"}"
            } else {
                false to "Found UI element '$trimmed' but it is not clickable"
            }
        }
        return false to "Could not find UI element matching '$trimmed' on the current screen"
    }

    fun performScroll(direction: String): Pair<Boolean, String> {
        val root = rootInActiveWindow
            ?: return false to "No active window content available to scroll"

        val scrollable = findScrollableNode(root)
        val action = when (direction.lowercase()) {
            "up", "backward", "right", "upar" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        if (scrollable != null && scrollable.performAction(action)) {
            return true to "Verified scroll ($direction) on scrollable container in ${root.packageName}"
        }

        // Fallback to swipe gesture if no scrollable node reported
        val displayMetrics = resources.displayMetrics
        val midX = displayMetrics.widthPixels / 2f
        val startY = if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            displayMetrics.heightPixels * 0.75f
        } else {
            displayMetrics.heightPixels * 0.25f
        }
        val endY = if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            displayMetrics.heightPixels * 0.25f
        } else {
            displayMetrics.heightPixels * 0.75f
        }

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) {
            true to "Dispatched scroll swipe gesture ($direction)"
        } else {
            false to "Could not scroll current window"
        }
    }

    fun performTextEntry(targetHint: String?, textToEnter: String): Pair<Boolean, String> {
        val root = rootInActiveWindow
            ?: return false to "No active window content available for text entry"

        val targetNode = if (!targetHint.isNullOrBlank()) {
            findNodeByTextOrDescription(root, targetHint) ?: findEditableNode(root)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        }

        if (targetNode == null) {
            return false to "No editable text field found in active window"
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                textToEnter
            )
        }
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (success) {
            true to "Verified text entry into editable field in ${root.packageName}"
        } else {
            false to "Editable field rejected ACTION_SET_TEXT in ${root.packageName}"
        }
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findNodeByTextOrDescription(
        root: AccessibilityNodeInfo,
        query: String
    ): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText(query)
        if (!byText.isNullOrEmpty()) {
            return byText.first()
        }
        return traverseForDescription(root, query.lowercase())
    }

    private fun traverseForDescription(
        node: AccessibilityNodeInfo?,
        queryLower: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase()
        val text = node.text?.toString()?.lowercase()
        if ((desc != null && desc.contains(queryLower)) || (text != null && text.contains(queryLower))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = traverseForDescription(node.getChild(i), queryLower)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile
        var instance: MaxAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastActivePackage = MutableStateFlow<String?>(null)
        val lastActivePackage: StateFlow<String?> = _lastActivePackage.asStateFlow()

        fun isEnabledInSettings(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName = "${context.packageName}/${MaxAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expectedComponentName, ignoreCase = true) ||
                    componentNameString.contains(MaxAccessibilityService::class.java.simpleName)
                ) {
                    return true
                }
            }
            return false
        }
    }
}
