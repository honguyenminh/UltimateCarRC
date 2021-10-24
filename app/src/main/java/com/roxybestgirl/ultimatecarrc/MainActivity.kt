package com.roxybestgirl.ultimatecarrc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import com.roxybestgirl.ultimatecarrc.JoystickView.OnMoveListener
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {
    // Some global fields
    private var fabMenuIsOpened = false
    private var leftIsVertical = true
    private var shortAnimationDuration: Int = 0

    private var lastVerticalAngle = 0
    private var lastVerticalStrength:Byte = 0
    private var lastHorizAngle = 0
    private var lastHorizStrength = 0


    // Socket object
    private lateinit var ws: WebSocket

    // Declare view fields
    private lateinit var settingFab: FloatingActionButton
    private lateinit var swapSideBtn: FloatingActionButton
    private lateinit var connectionInfoBtn: FloatingActionButton
    private lateinit var fabMenuOverlay: View
    private lateinit var leftIcon: ImageView
    private lateinit var rightIcon: ImageView
    private lateinit var rightJoystick: JoystickView
    private lateinit var leftJoystick: JoystickView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Show "connecting" snackbar until connected
        Snackbar.make(
            findViewById(R.id.main_frame_layout),
            "Connecting to car at 192.168.1.184 / WebSocket",
            Snackbar.LENGTH_INDEFINITE)
            .show()

        // Init socket
        ws = WebSocketFactory().setVerifyHostname(false)
            .createSocket("ws://192.168.1.184:80/ws")
        val thread = Thread {
            while (!ws.isOpen) {
                try {
                    ws.connect()
                } catch (e: Exception) {
                    Thread.sleep(5000)
                }
            }
            Snackbar.make(
                findViewById(R.id.main_frame_layout),   // View to draw on
                "Connected to 192.168.1.184/ws on port 80", //Message
                Snackbar.LENGTH_SHORT)  // Duration
                .show()
        }
        thread.start()

        // Initialize view fields
        settingFab = findViewById(R.id.setting_fab)
        shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
        swapSideBtn = findViewById(R.id.swap_side_btn)
        connectionInfoBtn = findViewById(R.id.connection_info_btn)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        leftIcon = findViewById(R.id.left_icon)
        rightIcon = findViewById(R.id.right_icon)
        leftJoystick = findViewById(R.id.left_joystick)
        rightJoystick = findViewById(R.id.right_joystick)

    /**********************************************************************************************/

        // Event listeners
        fabMenuOverlay.setOnClickListener {
            if (fabMenuIsOpened) closeFabMenuAnim()
        }
        settingFab.setOnClickListener {
            if (!fabMenuIsOpened) {
                openFabMenuAnim()
            }
            else {
                closeFabMenuAnim()
            }
        }
        swapSideBtn.setOnClickListener {
            if (leftIsVertical) {
                leftIsVertical = false
                ObjectAnimator.ofFloat(leftIcon, "rotation", 0f, 90f)
                    .setDuration(shortAnimationDuration.toLong()).start()
                ObjectAnimator.ofFloat(rightIcon, "rotation", 90f, 0f)
                    .setDuration(shortAnimationDuration.toLong()).start()
                /**
                 * The allowed direction of the button is define by the value of this parameter:
                 * - a negative value for horizontal axe
                 * - a positive value for vertical axe
                 * - zero for both axes
                 */
                leftJoystick.buttonDirection = -1
                rightJoystick.buttonDirection = 1
            }
            else {
                leftIsVertical = true
                ObjectAnimator.ofFloat(leftIcon, "rotation", 90f, 0f)
                    .setDuration(shortAnimationDuration.toLong()).start()
                ObjectAnimator.ofFloat(rightIcon, "rotation", 0f, 90f)
                    .setDuration(shortAnimationDuration.toLong()).start()
                leftJoystick.buttonDirection = 1
                rightJoystick.buttonDirection = -1
            }
            closeFabMenuAnim()
        }

        // Joystick interact event
        arrayOf(leftJoystick, rightJoystick).forEach { joystick ->
            joystick.setOnMoveListener(object : OnMoveListener {
                override fun onMove(angle: Int, strength: Int) {
                    if (!ws.isOpen) return
                    // > 0 is vertical joystick
                    if (joystick.buttonDirection > 0) {
                        when (angle) {
                            // Stop vertical
                            0 -> {
                                if (lastVerticalAngle == 0) return
                                lastVerticalAngle = 0
                                lastVerticalStrength = 0
                                ws.sendText("N")
                            }

                            // Map 0-100 strength to 50-255 analog signal
                            // Forward
                            90 -> {
                                val value = (strength * 2.05 + 50).roundToInt().toByte()
                                if ((lastVerticalAngle == 90) and (lastVerticalStrength == value)) return
                                lastVerticalAngle = 90
                                lastVerticalStrength = value
                                ws.sendBinary(byteArrayOf('F'.toByte(), value))
                            }
                            // Backward
                            270 -> {
                                val value = (strength * 2.05 + 50).roundToInt().toByte()
                                if ((lastVerticalAngle == 270) and (lastVerticalStrength == value)) return
                                lastVerticalAngle = 270
                                lastVerticalStrength = value
                                ws.sendBinary(byteArrayOf('B'.toByte(), value))
                            }
                        }
                    }
                    // < 0 is horizontal joystick
                    else if (joystick.buttonDirection < 0) {
                        if ((lastHorizAngle == angle) and (lastHorizStrength == strength/2)) return
                        // Right or stop
                        if (angle == 0) {
                            // If stop
                            if (strength == 0) {
                                lastHorizAngle = 0
                                lastHorizStrength = 0
                                ws.sendText("X")
                            }
                            // Map 0-100 strength to 50 degree max
                            else {
                                lastHorizAngle = 0
                                lastHorizStrength = strength/2
                                ws.sendText("R" + (90 + (strength / 2)))
                            }
                        }
                        // Left
                        else if (angle == 180) {
                            lastHorizAngle = 180
                            lastHorizStrength = strength/2
                            // Map 0-100 strength to 50 degree max
                            ws.sendText("L" + (90 - (strength / 2)))
                        }
                    }
                }
            }, 100)
        }
    }

/**************************************************************************************************/

    // Animation for fab menu
    private fun openFabMenuAnim() {
        fabMenuIsOpened = true
        ObjectAnimator.ofFloat(settingFab, "rotation", 0f, 90f)
            .setDuration((shortAnimationDuration * 1.5).toLong()).start()
        settingFab.setImageResource(R.drawable.ic_round_close_24)
        fabMenuOverlay.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(fabMenuOverlay, "alpha", 0f, 0.2f)
            .setDuration((shortAnimationDuration * 1.5).toLong()).start()

        val handler = Handler()
        swapSideBtn.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(shortAnimationDuration.toLong())
                .setListener(null)
        }
        handler.postDelayed({
            connectionInfoBtn.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(shortAnimationDuration.toLong())
                    .setListener(null)
            }
        }, shortAnimationDuration.toLong() / 2)
    }
    private fun closeFabMenuAnim() {
        fabMenuIsOpened = false
        ObjectAnimator.ofFloat(settingFab, "rotation", 90f, 0f)
            .setDuration((shortAnimationDuration * 1.5).toLong()).start()
        settingFab.setImageResource(R.drawable.ic_baseline_settings_24)
        ObjectAnimator.ofFloat(fabMenuOverlay, "alpha", 0.2f, 0f)
            .setDuration((shortAnimationDuration * 1.5).toLong()).start()

        val handler = Handler()
        connectionInfoBtn.animate()
            .alpha(0f)
            .setDuration(shortAnimationDuration.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    connectionInfoBtn.visibility = View.GONE
                }
            })
        handler.postDelayed({
            swapSideBtn.animate()
                .alpha(0f)
                .setDuration(shortAnimationDuration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        swapSideBtn.visibility = View.GONE
                        fabMenuOverlay.visibility = View.GONE
                    }
                })
        }, shortAnimationDuration.toLong() / 3)
    }

/**************************************************************************************************/

    // Full screen stuff
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
    private fun hideSystemUI() {
        // Sticky immersive mode
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            // Make ui fullscreen behind navigation
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Actually hiding system UI
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onDestroy() {
        super.onDestroy()
        ws.disconnect()
    }
}