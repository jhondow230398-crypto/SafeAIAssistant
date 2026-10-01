package com.example.safeassistant

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var agent: SafeAIAgent? = null
    private var voiceManager: OfflineVoiceManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        agent = SafeAIAgent(
            context = applicationContext,
            modelPath = "/data/local/tmp/llm_model.bin"
        ) { status ->
            android.util.Log.d("OverlayAgent", status)
        }

        setupOverlay()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager.addView(overlayView, layoutParams)

        val fab = overlayView.findViewById<View>(R.id.fabBubble)
        val cardInputPanel = overlayView.findViewById<CardView>(R.id.cardInputPanel)
        val etIntent = overlayView.findViewById<EditText>(R.id.etOverlayIntent)
        val tvVoiceStatus = overlayView.findViewById<TextView>(R.id.tvVoiceStatus)
        val btnSend = overlayView.findViewById<Button>(R.id.btnOverlaySend)
        val btnClose = overlayView.findViewById<Button>(R.id.btnOverlayClose)
        val btnMic = overlayView.findViewById<ImageButton>(R.id.btnOverlayMic)

        voiceManager = OfflineVoiceManager(
            context = this,
            onResult = { spokenText ->
                etIntent.setText(spokenText)
                tvVoiceStatus.visibility = View.GONE
                agent?.executeIntent(spokenText)
                toggleInputPanel(cardInputPanel, false)
            },
            onStatusChanged = { status ->
                tvVoiceStatus.text = status
                tvVoiceStatus.visibility = View.VISIBLE
            }
        )

        btnMic.setOnClickListener {
            voiceManager?.startListening()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        val touchSlop = 10

        fab.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < touchSlop && diffY < touchSlop) {
                        toggleInputPanel(cardInputPanel, true)
                    }
                    true
                }
                else -> false
            }
        }

        btnSend.setOnClickListener {
            val query = etIntent.text.toString().trim()
            if (query.isNotEmpty()) {
                agent?.executeIntent(query)
                etIntent.text.clear()
                toggleInputPanel(cardInputPanel, false)
            }
        }

        btnClose.setOnClickListener {
            voiceManager?.stopListening()
            tvVoiceStatus.visibility = View.GONE
            toggleInputPanel(cardInputPanel, false)
        }
    }

    private fun toggleInputPanel(panel: CardView, show: Boolean) {
        if (show) {
            panel.visibility = View.VISIBLE
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            panel.visibility = View.GONE
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(overlayView, layoutParams)
    }

    private fun startAsForeground() {
        val channelId = "safe_assistant_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Assistant Overlay Active",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Safe AI Assistant Active")
            .setContentText("Tap floating icon to trigger actions.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager?.destroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        agent?.release()
    }
}
