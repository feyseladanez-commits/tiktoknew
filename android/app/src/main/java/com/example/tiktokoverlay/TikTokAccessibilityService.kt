package com.example.tiktokoverlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TikTokAccessibilityService : AccessibilityService() {

    private var lastSentUsername: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val username = findUsername(root)

        if (username != null && username != lastSentUsername) {
            lastSentUsername = username
            val intent = Intent(ACTION_USERNAME_UPDATE).apply {
                putExtra(EXTRA_USERNAME, username)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
    }

    /**
     * IMPORTANT: This pattern-matches on text starting with "@", which is a starting
     * guess, not a confirmed TikTok node ID. TikTok's actual UI tree needs to be
     * inspected with uiautomatorviewer (bundled with Android SDK tools) on a real
     * device with TikTok open, to find the real resource-id or content-description
     * used for the creator's username. Update this function once you've confirmed it —
     * this is the single most likely thing to need adjusting, and to break again
     * whenever TikTok updates its app.
     */
    private fun findUsername(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (text != null && text.startsWith("@") && text.length in 2..40) return text
        if (desc != null && desc.startsWith("@") && desc.length in 2..40) return desc

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUsername(child)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        const val ACTION_USERNAME_UPDATE = "com.example.tiktokoverlay.USERNAME_UPDATE"
        const val EXTRA_USERNAME = "username"
    }
}
