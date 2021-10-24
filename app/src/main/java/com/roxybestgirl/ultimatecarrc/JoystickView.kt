package com.roxybestgirl.ultimatecarrc

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt


class JoystickView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), Runnable {
    /*
    INTERFACES
    */
    /**
     * Interface definition for a callback to be invoked when a
     * JoystickView's button is moved
     */
    interface OnMoveListener {
        /**
         * Called when a JoystickView's button has been moved
         * @param angle current angle
         * @param strength current strength
         */
        fun onMove(angle: Int, strength: Int)
    }

    /**
     * Interface definition for a callback to be invoked when a JoystickView
     * is touched and held by multiple pointers.
     */
    interface OnMultipleLongPressListener {
        /**
         * Called when a JoystickView has been touch and held enough time by multiple pointers.
         */
        fun onMultipleLongPress()
    }

    // DRAWING
    private val mPaintCircleButton: Paint
    private val mPaintCircleBorder: Paint
    private val mPaintBackground: Paint
    private var mPaintBitmapButton: Paint? = null
    private var mButtonBitmap: Bitmap? = null

    /**
     * Ratio use to define the size of the button
     */
    private var mButtonSizeRatio: Float = 0f

    /**
     * Ratio use to define the size of the background
     *
     */
    private var mBackgroundSizeRatio: Float = 0f

    // COORDINATE
    private var mPosX: Int = 0
    private var mPosY: Int = 0
    private var mCenterX: Int = 0
    private var mCenterY: Int = 0
    private var mFixedCenterX: Int = 0
    private var mFixedCenterY: Int = 0

    /**
     * Used to adapt behavior whether it is auto-defined center (false) or fixed center (true)
     */
    private var mFixedCenter: Boolean = false
    /**
     * Return the current behavior of the auto re-center button
     * @return True if automatically re-centered or False if not
     */
    /**
     * Set the current behavior of the auto re-center button
     * @param b True if automatically re-centered or False if not
     */
    /**
     * Used to adapt behavior whether the button is automatically re-centered (true)
     * when released or not (false)
     */
    var isAutoReCenterButton: Boolean = false
    /**
     * Return the current behavior of the button stick to border
     * @return True if the button stick to the border otherwise False
     */
    /**
     * Set the current behavior of the button stick to border
     * @param b True if the button stick to the border or False (default) if not
     */
    /**
     * Used to adapt behavior whether the button is stick to border (true) or
     * could be anywhere (when false - similar to regular behavior)
     */
    var isButtonStickToBorder: Boolean = false

    /**
     * Used to enabled/disabled the Joystick. When disabled (enabled to false) the joystick button
     * can't move and onMove is not called.
     */
    private var mEnabled: Boolean = false

    // SIZE
    private var mButtonRadius: Int = 0
    private var mBorderRadius: Int = 0

    /**
     * Alpha of the border (to use when changing color dynamically)
     */
    private var mBorderAlpha: Int = 0

    /**
     * Based on mBorderRadius but a bit smaller (minus half the stroke size of the border)
     */
    private var mBackgroundRadius: Float = 0f

    /**
     * Listener used to dispatch OnMove event
     */
    private var mCallback: OnMoveListener? = null
    private var mLoopInterval: Long = DEFAULT_LOOP_INTERVAL.toLong()
    private var mThread: Thread? = Thread(this)

    /**
     * Listener used to dispatch MultipleLongPress event
     */
    private var mOnMultipleLongPressListener: OnMultipleLongPressListener? = null
    private val mHandlerMultipleLongPress: Handler = Handler()
    private val mRunnableMultipleLongPress: Runnable
    private var mMoveTolerance: Int = 0
    /**
     * Return the current direction allowed for the button to move
     * @return Actually return an integer corresponding to the direction:
     * - A negative value is horizontal axe,
     * - A positive value is vertical axe,
     * - Zero means both axes
     */
    /**
     * Set the current authorized direction for the button to move
     * @param direction the value will define the authorized direction:
     * - any negative value (such as -1) for horizontal axe
     * - any positive value (such as 1) for vertical axe
     * - zero (0) for the full direction (both axes)
     */
    /**
     * The allowed direction of the button is define by the value of this parameter:
     * - a negative value for horizontal axe
     * - a positive value for vertical axe
     * - zero for both axes
     */
    var buttonDirection: Int = 0

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs) {}

    private fun initPosition() {
        // get the center of view to position circle
        mPosX = width / 2
        mCenterX = mPosX
        mFixedCenterX = mCenterX
        mPosY = width / 2
        mCenterY = mPosY
        mFixedCenterY = mCenterY
    }

    /**
     * Draw the background, the border and the button
     * @param canvas the canvas on which the shapes will be drawn
     */
    override fun onDraw(canvas: Canvas) {
        // Draw the background
        canvas.drawCircle(
            mFixedCenterX.toFloat(),
            mFixedCenterY.toFloat(),
            mBackgroundRadius,
            mPaintBackground
        )

        // Draw the circle border
        canvas.drawCircle(
            mFixedCenterX.toFloat(),
            mFixedCenterY.toFloat(),
            mBorderRadius.toFloat(),
            mPaintCircleBorder
        )

        // Draw the button from image
        if (mButtonBitmap != null) {
            canvas.drawBitmap(
                mButtonBitmap!!, (
                        (mPosX + mFixedCenterX) - mCenterX - mButtonRadius).toFloat(), (
                        (mPosY + mFixedCenterY) - mCenterY - mButtonRadius).toFloat(),
                mPaintBitmapButton
            )
        } else {
            canvas.drawCircle(
                (
                        mPosX + mFixedCenterX - mCenterX).toFloat(), (
                        mPosY + mFixedCenterY - mCenterY).toFloat(),
                mButtonRadius.toFloat(),
                mPaintCircleButton
            )
        }
    }

    /**
     * This is called during layout when the size of this view has changed.
     * Here we get the center of the view and the radius to draw all the shapes.
     *
     * @param w Current width of this view.
     * @param h Current height of this view.
     * @param oldW Old width of this view.
     * @param oldH Old height of this view.
     */
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        initPosition()

        // radius based on smallest size : height OR width
        val d: Int = w.coerceAtMost(h)
        mButtonRadius = (d / 2 * mButtonSizeRatio).toInt()
        mBorderRadius = (d / 2 * mBackgroundSizeRatio).toInt()
        mBackgroundRadius = mBorderRadius - (mPaintCircleBorder.strokeWidth / 2)
        if (mButtonBitmap != null) mButtonBitmap =
            Bitmap.createScaledBitmap(mButtonBitmap!!, mButtonRadius * 2, mButtonRadius * 2, true)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // setting the measured values to resize the view to a certain width and height
        val d: Int = measure(widthMeasureSpec).coerceAtMost(measure(heightMeasureSpec))
        setMeasuredDimension(d, d)
    }

    private fun measure(measureSpec: Int): Int {
        return if (MeasureSpec.getMode(measureSpec) == MeasureSpec.UNSPECIFIED) {
            // if no bounds are specified return a default size (200)
            DEFAULT_SIZE
        } else {
            // As you want to fill the available space
            // always return the full available bounds.
            MeasureSpec.getSize(measureSpec)
        }
    }
    /*
    USER EVENT
     */
    /**
     * Handle touch screen motion event. Move the button according to the
     * finger coordinate and detect longPress by multiple pointers only.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // if disabled we don't move the
        if (!mEnabled) {
            return true
        }


        // to move the button according to the finger coordinate
        // (or limited to one axe according to direction option
        mPosY =
            if (buttonDirection < 0) mCenterY else event.y.toInt() // direction negative is horizontal axe
        mPosX =
            if (buttonDirection > 0) mCenterX else event.x.toInt() // direction positive is vertical axe
        if (event.action == MotionEvent.ACTION_UP) {

            ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong()).start()
            postDelayed( Runnable {
                // stop listener because the finger left the touch screen
                mThread!!.interrupt()
                // re-center the button or not (depending on settings)
                if (isAutoReCenterButton) {

                    resetButtonPosition()

                    // update now the last strength and angle which should be zero after resetButton
                    if (mCallback != null) mCallback!!.onMove(angle, strength)
                }

                // if mAutoReCenterButton is false we will send the last strength and angle a bit
                // later only after processing new position X and Y otherwise it could be above the border limit
            }, resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong()).start()
            if (mThread != null && mThread!!.isAlive) {
                mThread!!.interrupt()
            }
            mThread = Thread(this)
            mThread!!.start()
            if (mCallback != null) mCallback!!.onMove(angle, strength)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->                 // when the first touch occurs we update the center (if set to auto-defined center)
                if (!mFixedCenter) {
                    mCenterX = mPosX
                    mCenterY = mPosY
                }
            MotionEvent.ACTION_POINTER_DOWN -> {

                // when the second finger touch
                if (event.pointerCount == 2) {
                    mHandlerMultipleLongPress.postDelayed(
                        mRunnableMultipleLongPress,
                        (ViewConfiguration.getLongPressTimeout() * 2).toLong()
                    )
                    mMoveTolerance = MOVE_TOLERANCE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                mMoveTolerance--
                if (mMoveTolerance == 0) {
                    mHandlerMultipleLongPress.removeCallbacks(mRunnableMultipleLongPress)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {

                // when the last multiple touch is released
                if (event.pointerCount == 2) {
                    mHandlerMultipleLongPress.removeCallbacks(mRunnableMultipleLongPress)
                }
            }
        }
        val abs: Double = sqrt(
            ((mPosX - mCenterX) * (mPosX - mCenterX)
                    + (mPosY - mCenterY) * (mPosY - mCenterY)).toDouble()
        )

        // (abs > mBorderRadius) means button is too far therefore we limit to border
        // (buttonStickBorder && abs != 0) means wherever is the button we stick it to the border except when abs == 0
        if (abs > mBorderRadius || (isButtonStickToBorder && abs != 0.0)) {
            mPosX = ((mPosX - mCenterX) * mBorderRadius / abs + mCenterX).toInt()
            mPosY = ((mPosY - mCenterY) * mBorderRadius / abs + mCenterY).toInt()
        }
        if (!isAutoReCenterButton) {
            // Now update the last strength and angle if not reset to center
            if (mCallback != null) mCallback!!.onMove(angle, strength)
        }


        // to force a new draw
        invalidate()
        return true
    }
    /*
    GETTERS
     */// make it as a regular counter-clock protractor
    /**
     * Process the angle following the 360° counter-clock protractor rules.
     * @return the angle of the button
     */
    private val angle: Int
        get() {
            val angle: Int =
                Math.toDegrees(
                    atan2(
                        (mCenterY - mPosY).toDouble(),
                        (mPosX - mCenterX).toDouble()
                    )
                )
                    .toInt()
            return if (angle < 0) angle + 360 else angle // make it as a regular counter-clock protractor
        }

    /**
     * Process the strength as a percentage of the distance between the center and the border.
     * @return the strength of the button
     */
    private val strength: Int
        get() = (100 * sqrt(
            ((mPosX - mCenterX)
                    * (mPosX - mCenterX) + (mPosY - mCenterY)
                    * (mPosY - mCenterY)).toDouble()
        ) / mBorderRadius).toInt()

    /**
     * Reset the button position to the center.
     */
    fun resetButtonPosition() {
        mPosX = mCenterX
        mPosY = mCenterY
    }

    /**
     * Return the state of the joystick. False when the button don't move.
     * @return the state of the joystick
     */
    override fun isEnabled(): Boolean {
        return mEnabled
    }
    /**
     * Return the size of the button (as a ratio of the total width/height)
     * Default is 0.25 (25%).
     * @return button size (value between 0.0 and 1.0)
     */
    /**
     * Set the joystick button size (as a fraction of the real width/height)
     * By default it is 25% (0.25).
     * @param newRatio between 0.0 and 1.0
     */
    var buttonSizeRatio: Float
        get() {
            return mButtonSizeRatio
        }
        set(newRatio) {
            if ((newRatio > 0.0f) and (newRatio <= 1.0f)) {
                mButtonSizeRatio = newRatio
            }
        }

    /**
     * Return the size of the background (as a ratio of the total width/height)
     * Default is 0.75 (75%).
     * @return background size (value between 0.0 and 1.0)
     */
    fun getmBackgroundSizeRatio(): Float {
        return mBackgroundSizeRatio
    }

    /**
     * Return the relative X coordinate of button center related
     * to top-left virtual corner of the border
     * @return coordinate of X (normalized between 0 and 100)
     */
    val normalizedX: Int
        get() {
            if (width == 0) {
                return 50
            }
            return ((mPosX - mButtonRadius) * 100.0f / (width - mButtonRadius * 2)).roundToInt()
        }

    /**
     * Return the relative Y coordinate of the button center related
     * to top-left virtual corner of the border
     * @return coordinate of Y (normalized between 0 and 100)
     */
    val normalizedY: Int
        get() {
            if (height == 0) {
                return 50
            }
            return ((mPosY - mButtonRadius) * 100.0f / (height - mButtonRadius * 2)).roundToInt()
        }
    /**
     * Return the alpha of the border
     * @return it should be an integer between 0 and 255 previously set
     */
    /**
     * Set the border alpha for this JoystickView.
     * @param alpha the transparency of the border between 0 and 255
     */
    var borderAlpha: Int
        get() {
            return mBorderAlpha
        }
        set(alpha) {
            mBorderAlpha = alpha
            mPaintCircleBorder.alpha = alpha
            invalidate()
        }
    /*
   SETTERS
    */
    /**
     * Set an image to the button with a drawable
     * @param d drawable to pick the image
     */
    /*fun setButtonDrawable(d: Drawable?) {
        if (d != null) {
            if (d is BitmapDrawable) {
                mButtonBitmap = d.bitmap
                if (mButtonRadius != 0) {
                    mButtonBitmap = Bitmap.createScaledBitmap(
                        mButtonBitmap,
                        mButtonRadius * 2,
                        mButtonRadius * 2,
                        true
                    )
                }
                if (mPaintBitmapButton != null) mPaintBitmapButton = Paint()
            }
        }
    }*/

    /**
     * Set the button color for this JoystickView.
     * @param color the color of the button
     */
    fun setButtonColor(color: Int) {
        mPaintCircleButton.color = color
        invalidate()
    }

    /**
     * Set the border color for this JoystickView.
     * @param color the color of the border
     */
    fun setBorderColor(color: Int) {
        mPaintCircleBorder.color = color
        if (color != Color.TRANSPARENT) {
            mPaintCircleBorder.alpha = mBorderAlpha
        }
        invalidate()
    }

    /**
     * Set the background color for this JoystickView.
     * @param color the color of the background
     */
    override fun setBackgroundColor(color: Int) {
        mPaintBackground.color = color
        invalidate()
    }

    /**
     * Set the border width for this JoystickView.
     * @param width the width of the border
     */
    fun setBorderWidth(width: Int) {
        mPaintCircleBorder.strokeWidth = width.toFloat()
        mBackgroundRadius = mBorderRadius - (width / 2.0f)
        invalidate()
    }

    /**
     * Register a callback to be invoked when this JoystickView's button is moved
     * @param l The callback that will run
     */
    fun setOnMoveListener(l: OnMoveListener?) {
        setOnMoveListener(l, DEFAULT_LOOP_INTERVAL)
    }

    /**
     * Register a callback to be invoked when this JoystickView's button is moved
     * @param l The callback that will run
     * @param loopInterval Refresh rate to be invoked in milliseconds
     */
    fun setOnMoveListener(l: OnMoveListener?, loopInterval: Int) {
        mCallback = l
        mLoopInterval = loopInterval.toLong()
    }

    /**
     * Register a callback to be invoked when this JoystickView is touch and held by multiple pointers
     * @param l The callback that will run
     */
    fun setOnMultiLongPressListener(l: OnMultipleLongPressListener?) {
        mOnMultipleLongPressListener = l
    }

    /**
     * Set the joystick center's behavior (fixed or auto-defined)
     * @param fixedCenter True for fixed center, False for auto-defined center based on touch down
     */
    fun setFixedCenter(fixedCenter: Boolean) {
        // if we set to "fixed" we make sure to re-init position related to the width of the joystick
        if (fixedCenter) {
            initPosition()
        }
        mFixedCenter = fixedCenter
        invalidate()
    }

    /**
     * Enable or disable the joystick
     * @param enabled False mean the button won't move and onMove won't be called
     */
    override fun setEnabled(enabled: Boolean) {
        mEnabled = enabled
    }

    /**
     * Set the joystick button size (as a fraction of the real width/height)
     * By default it is 75% (0.75).
     * Not working if the background is an image.
     * @param newRatio between 0.0 and 1.0
     */
    fun setBackgroundSizeRatio(newRatio: Float) {
        if ((newRatio > 0.0f) && (newRatio <= 1.0f)) {
            mBackgroundSizeRatio = newRatio
        }
    }

    /*
   IMPLEMENTS
    */
    // Runnable
    override fun run() {
        while (!Thread.interrupted()) {
            post { if (mCallback != null) mCallback!!.onMove(angle, strength) }
            try {
                Thread.sleep(mLoopInterval)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    companion object {
        /*
    CONSTANTS
    */
        /**
         * Default refresh rate as a time in milliseconds to send move values through callback
         */
        private val DEFAULT_LOOP_INTERVAL: Int = 50 // in milliseconds

        /**
         * Used to allow a slight move without cancelling MultipleLongPress
         */
        private val MOVE_TOLERANCE: Int = 10

        /**
         * Default color for button
         */
        private val DEFAULT_COLOR_BUTTON: Int = Color.BLACK

        /**
         * Default color for border
         */
        private val DEFAULT_COLOR_BORDER: Int = Color.TRANSPARENT

        /**
         * Default alpha for border
         */
        private val DEFAULT_ALPHA_BORDER: Int = 255

        /**
         * Default background color
         */
        private val DEFAULT_BACKGROUND_COLOR: Int = Color.TRANSPARENT

        /**
         * Default View's size
         */
        private val DEFAULT_SIZE: Int = 200

        /**
         * Default border's width
         */
        private val DEFAULT_WIDTH_BORDER: Int = 3

        /**
         * Default behavior to fixed center (not auto-defined)
         */
        private val DEFAULT_FIXED_CENTER: Boolean = true

        /**
         * Default behavior to auto re-center button (automatically recenter the button)
         */
        private val DEFAULT_AUTO_RECENTER_BUTTON: Boolean = true

        /**
         * Default behavior to button stickToBorder (button stay on the border)
         */
        private val DEFAULT_BUTTON_STICK_TO_BORDER: Boolean = false

        /**
         * Default value.
         * Both direction correspond to horizontal and vertical movement
         */
        var BUTTON_DIRECTION_BOTH: Int = 0
    }
    /**
     * Constructor that is called when inflating a JoystickView from XML. This is called
     * when a JoystickView is being constructed from an XML file, supplying attributes
     * that were specified in the XML file.
     * @param context The Context the JoystickView is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the JoystickView.
     */
    /*
    CONSTRUCTORS
     */
    /**
     * Simple constructor to use when creating a JoystickView from code.
     * Call another constructor passing null to Attribute.
     * @param context The Context the JoystickView is running in, through which it can
     * access the current theme, resources, etc.
     */
    init {
        val styledAttributes: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.JoystickView,
            0, 0
        )
        val buttonColor: Int
        val borderColor: Int
        val backgroundColor: Int
        val borderWidth: Int
        val buttonDrawable: Drawable?
        try {
            buttonColor = styledAttributes.getColor(
                R.styleable.JoystickView_JV_buttonColor,
                DEFAULT_COLOR_BUTTON
            )
            borderColor = styledAttributes.getColor(
                R.styleable.JoystickView_JV_borderColor,
                DEFAULT_COLOR_BORDER
            )
            mBorderAlpha = styledAttributes.getInt(
                R.styleable.JoystickView_JV_borderAlpha,
                DEFAULT_ALPHA_BORDER
            )
            backgroundColor = styledAttributes.getColor(
                R.styleable.JoystickView_JV_backgroundColor,
                DEFAULT_BACKGROUND_COLOR
            )
            borderWidth = styledAttributes.getDimensionPixelSize(
                R.styleable.JoystickView_JV_borderWidth,
                DEFAULT_WIDTH_BORDER
            )
            mFixedCenter = styledAttributes.getBoolean(
                R.styleable.JoystickView_JV_fixedCenter,
                DEFAULT_FIXED_CENTER
            )
            isAutoReCenterButton = styledAttributes.getBoolean(
                R.styleable.JoystickView_JV_autoReCenterButton,
                DEFAULT_AUTO_RECENTER_BUTTON
            )
            isButtonStickToBorder = styledAttributes.getBoolean(
                R.styleable.JoystickView_JV_buttonStickToBorder,
                DEFAULT_BUTTON_STICK_TO_BORDER
            )
            buttonDrawable = styledAttributes.getDrawable(R.styleable.JoystickView_JV_buttonImage)
            mEnabled = styledAttributes.getBoolean(R.styleable.JoystickView_JV_enabled, true)
            mButtonSizeRatio = styledAttributes.getFraction(
                R.styleable.JoystickView_JV_buttonSizeRatio,
                1,
                1,
                0.25f
            )
            mBackgroundSizeRatio = styledAttributes.getFraction(
                R.styleable.JoystickView_JV_backgroundSizeRatio,
                1,
                1,
                0.75f
            )
            buttonDirection = styledAttributes.getInteger(
                R.styleable.JoystickView_JV_buttonDirection,
                BUTTON_DIRECTION_BOTH
            )
        } finally {
            styledAttributes.recycle()
        }

        // Initialize the drawing according to attributes
        mPaintCircleButton = Paint()
        mPaintCircleButton.isAntiAlias = true
        mPaintCircleButton.color = buttonColor
        mPaintCircleButton.style = Paint.Style.FILL
        if (buttonDrawable != null) {
            if (buttonDrawable is BitmapDrawable) {
                mButtonBitmap = buttonDrawable.bitmap
                mPaintBitmapButton = Paint()
            }
        }
        mPaintCircleBorder = Paint()
        mPaintCircleBorder.isAntiAlias = true
        mPaintCircleBorder.color = borderColor
        mPaintCircleBorder.style = Paint.Style.STROKE
        mPaintCircleBorder.strokeWidth = borderWidth.toFloat()
        if (borderColor != Color.TRANSPARENT) {
            mPaintCircleBorder.alpha = mBorderAlpha
        }
        mPaintBackground = Paint()
        mPaintBackground.isAntiAlias = true
        mPaintBackground.color = backgroundColor
        mPaintBackground.style = Paint.Style.FILL


        // Init Runnable for MultiLongPress
        mRunnableMultipleLongPress = object : Runnable {
            override fun run() {
                if (mOnMultipleLongPressListener != null) mOnMultipleLongPressListener!!.onMultipleLongPress()
            }
        }
    }
}