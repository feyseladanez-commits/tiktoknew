package com.example.tiktokoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleContainer: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private var isExpanded = false
    private var currentUsername = "@waiting"

    private var collapsedSizePx = 0
    private var expandedWidthPx = 0
    private var expandedHeightPx = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val usernameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val username = intent.getStringExtra(TikTokAccessibilityService.EXTRA_USERNAME) ?: return
            currentUsername = username
            if (isExpanded) {
                bubbleContainer.findViewById<TextView>(R.id.tvUsername)?.text = username
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = resources.displayMetrics
        collapsedSizePx = (48 * dm.density).toInt()
        expandedWidthPx = (dm.widthPixels * 0.55).toInt()
        expandedHeightPx = (dm.heightPixels * 0.10).toInt().coerceAtLeast((170 * dm.density).toInt())

        bubbleContainer = FrameLayout(this)

        params = WindowManager.LayoutParams(
            collapsedSizePx,
            collapsedSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        showCollapsed()
        windowManager.addView(bubbleContainer, params)

        val filter = IntentFilter(TikTokAccessibilityService.ACTION_USERNAME_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usernameReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usernameReceiver, filter)
        }
    }

    // ---------- Collapsed state ----------

    private fun showCollapsed() {
        bubbleContainer.removeAllViews()

        val icon = TextView(this).apply {
            text = "\uD83C\uDF81" // gift emoji as a placeholder — swap for an ImageView + your app icon
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bubble_bg)
        }

        addDragAndTapBehavior(icon)

        bubbleContainer.addView(
            icon,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
    }

    /**
     * Handles both dragging the bubble around AND tapping it to expand.
     * A movement threshold distinguishes a tap from a drag.
     */
    private fun addDragAndTapBehavior(view: View) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        val dragThresholdPx = 12

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (!isDragging && (Math.abs(dx) > dragThresholdPx || Math.abs(dy) > dragThresholdPx)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleContainer, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) expand()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Expanded state ----------

    private fun expand() {
        isExpanded = true
        bubbleContainer.removeAllViews()

        val panel = LayoutInflater.from(this).inflate(R.layout.overlay_donate_panel, bubbleContainer, false)
        val tvUsername = panel.findViewById<TextView>(R.id.tvUsername)
        val etAmount = panel.findViewById<EditText>(R.id.etAmount)
        val btnDonate = panel.findViewById<Button>(R.id.btnDonate)
        val btnClose = panel.findViewById<ImageView>(R.id.btnClose)
        val tvStatus = panel.findViewById<TextView>(R.id.tvStatus)

        tvUsername.text = currentUsername

        btnClose.setOnClickListener { collapse() }

        btnDonate.setOnClickListener {
            val amountText = etAmount.text.toString()
            if (amountText.isBlank()) {
                tvStatus.text = "Enter an amount first"
                return@setOnClickListener
            }
            if (ApiClient.authToken.isNullOrEmpty()) {
                tvStatus.text = "Please log in from the app first"
                return@setOnClickListener
            }

            btnDonate.isEnabled = false
            tvStatus.text = "Starting payment…"

            serviceScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        ApiClient.service.initiateDonation(
                            ApiClient.bearer(),
                            InitiateDonationRequest(
                                tiktok_username = currentUsername.removePrefix("@"),
                                amount = amountText
                            )
                        )
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val checkoutUrl = body.checkout_url
                        if (checkoutUrl != null) {
                            openCheckout(checkoutUrl)
                            tvStatus.text = "Complete payment in the browser"
                        } else {
                            // Test mode: backend has no payment provider connected yet,
                            // so the donation was recorded directly with no real payment.
                            tvStatus.text = "Sent! (test mode — no real payment)"
                        }
                    } else {
                        tvStatus.text = "Could not start payment"
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Network error"
                } finally {
                    btnDonate.isEnabled = true
                }
            }
        }

        bubbleContainer.addView(panel)

        params.width = expandedWidthPx
        params.height = expandedHeightPx
        windowManager.updateViewLayout(bubbleContainer, params)
    }

    private fun openCheckout(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun collapse() {
        isExpanded = false
        params.width = collapsedSizePx
        params.height = collapsedSizePx
        windowManager.updateViewLayout(bubbleContainer, params)
        showCollapsed()
    }

    // ---------- Foreground service plumbing (required on Android 8+) ----------

    private fun startForegroundWithNotification() {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating donate bubble",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channelId.let { channel })
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Creator Donate")
            .setContentText("Floating bubble is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::bubbleContainer.isInitialized) {
            windowManager.removeView(bubbleContainer)
        }
        unregisterReceiver(usernameReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
