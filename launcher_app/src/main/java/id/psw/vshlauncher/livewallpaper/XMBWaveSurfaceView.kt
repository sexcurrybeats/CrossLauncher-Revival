package id.psw.vshlauncher.livewallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import id.psw.vshlauncher.Logger
import id.psw.vshlauncher.select
import id.psw.vshlauncher.submodules.settings.WaveSettings
import id.psw.vshlauncher.vsh
import kotlin.concurrent.thread


open class XMBWaveSurfaceView : GLSurfaceView {

    companion object{
        const val PREF_NAME = "libwave_setting"
        const val KEY_STYLE = "wave_style"
        const val KEY_SPEED = "wave_speed"
        const val KEY_DTIME = "wave_bg_daytime"
        const val KEY_MONTH = "wave_bg_month"
        const val KEY_FRAMERATE = "wave_fps"
        const val KEY_COLOR_BACK_A = "wave_cback_a"
        const val KEY_COLOR_BACK_B = "wave_cback_b"
        const val KEY_COLOR_FORE_A = "wave_cfore_a"
        const val KEY_COLOR_FORE_B = "wave_cfore_b"
        const val KEY_BACKGROUND_PRESET = "wave_bg_preset"
    }

    private val TAG = "glsurface.sprx"

    private var isPaused = false
    private var renderVsync = false
    private var threadSleepDuration = 0

    constructor(context: Context?): super(context){
        init()
    }
    constructor(context: Context?, attrs: AttributeSet): super(context, attrs){
        init()
    }

    constructor(context:Context?, attrs: AttributeSet, styleSet: Int) : super(context, attrs){
        init()
    }

    lateinit var renderer : XMBWaveRenderer

    fun readPreferences(applyToNative: Boolean = true){
        Logger.d(TAG, "Re-reading preferences...")
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (applyToNative) {
            val backA = prefs.getInt(KEY_COLOR_BACK_A, Color.argb(255,0,128,255))
            val backB = prefs.getInt(KEY_COLOR_BACK_B, Color.argb(255,0,0,255))
            val foreA = prefs.getInt(KEY_COLOR_FORE_A, Color.argb(255,255,255,255))
            val foreB = prefs.getInt(KEY_COLOR_FORE_B, Color.argb(0,255,255,255))
            NativeGL.setWaveStyle(prefs.getInt(KEY_STYLE, WaveSettings.DEFAULT_WAVE_STYLE.toInt()).toByte())
            NativeGL.setBgDayNightMode(prefs.getBoolean(KEY_DTIME, WaveSettings.DEFAULT_WAVE_DAY_NIGHT))
            NativeGL.setBackgroundMonth(prefs.getInt(KEY_MONTH, WaveSettings.DEFAULT_WAVE_MONTH).toByte())
            NativeGL.clearBackgroundTexture()
            NativeGL.setSpeed(prefs.getFloat(KEY_SPEED, WaveSettings.DEFAULT_WAVE_SPEED))
            NativeGL.setBackgroundColor(backA, backB)
            NativeGL.setForegroundColor(foreA, foreB)
        }
        val fps = prefs.getInt(KEY_FRAMERATE, 0)
        renderVsync = fps <= 0
        threadSleepDuration = 16
        if(fps > 0){
            threadSleepDuration = 1000 / fps
        }
        Logger.d(TAG, "${renderVsync.select("VSync","FPS")} - ${threadSleepDuration}ms per frame")
    }

    fun init(){
        setEGLConfigChooser(8,8,8,0,8,8)
        setEGLContextClientVersion(2)
        renderer = XMBWaveRenderer()
        context.vsh.waveShouldReReadPreferences = true
        renderer.surfaceView = this
        readPreferences(applyToNative = false)
        setRenderer(renderer)
        renderMode = renderVsync.select(RENDERMODE_CONTINUOUSLY, RENDERMODE_WHEN_DIRTY)
        if(!renderVsync){
            thread {
                // Only render if uses self managed frame rate looper, AKA not the System VSync
                while(!renderVsync){
                    postInvalidate()
                    Thread.sleep(threadSleepDuration.toLong())
                }
            }.apply { name = "wave_frame_rate_man" }
        }
        holder.setFormat(PixelFormat.TRANSLUCENT)
        Logger.d(TAG, "Wave Surface Initialized")
    }

    fun checkPreferenceReRead(){
        if(context.vsh.waveShouldReReadPreferences){
            readPreferences()
            context.vsh.waveShouldReReadPreferences = false
            renderMode = renderVsync.select(RENDERMODE_CONTINUOUSLY, RENDERMODE_WHEN_DIRTY)
        }
    }

    fun refreshNativePreferences(forceStyleNudge: Boolean = false) {
        context.vsh.waveShouldReReadPreferences = false
        queueEvent {
            if (forceStyleNudge) {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val currentStyle = prefs.getInt(KEY_STYLE, WaveSettings.DEFAULT_WAVE_STYLE.toInt()).toByte()
                val nudgeStyle = when (currentStyle) {
                    XMBWaveRenderer.WAVE_TYPE_PS3_NORMAL -> XMBWaveRenderer.WAVE_TYPE_PS3_BLINKS
                    else -> XMBWaveRenderer.WAVE_TYPE_PS3_NORMAL
                }
                NativeGL.setWaveStyle(nudgeStyle)
                NativeGL.setWaveStyle(currentStyle)
            }
            readPreferences()
            requestRender()
        }
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
