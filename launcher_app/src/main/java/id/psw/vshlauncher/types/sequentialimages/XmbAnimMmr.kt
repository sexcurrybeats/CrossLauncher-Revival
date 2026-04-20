package id.psw.vshlauncher.types.sequentialimages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.Build
import id.psw.vshlauncher.FittingMode
import id.psw.vshlauncher.views.drawBitmap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class XmbAnimMmr(file:String) : XmbFrameAnimation() {
    companion object{
        private const val TARGET_PREVIEW_WIDTH = 320
        private const val TARGET_PREVIEW_HEIGHT = 176
        private const val FRAME_REQUEST_INTERVAL = 1.0f / 12.0f
        private val frameFetchingThreadPool = Executors.newFixedThreadPool(2)
    }

    private val retriever = MediaMetadataRetriever().apply {
        setDataSource(file)
    }

    override val frameCount: Int get() {
        var retval = 0
        try{
            retval = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
        }catch(e:Exception){
        }
        return retval
    }

    override var currentTime: Float = 0f
    private var pHasRecycled = false
    private var pDuration = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toInt().coerceAtLeast(1)
    override val hasRecycled: Boolean get() = pHasRecycled
    private val compBitmap = Bitmap.createBitmap(TARGET_PREVIEW_WIDTH,TARGET_PREVIEW_HEIGHT,Bitmap.Config.ARGB_8888)
    private val tmpRectF = RectF(0f,0f,TARGET_PREVIEW_WIDTH.toFloat(),TARGET_PREVIEW_HEIGHT.toFloat())
    private val composer = Canvas(compBitmap)
    private val frameRequestInFlight = AtomicBoolean(false)
    private var frameRequestElapsed = FRAME_REQUEST_INTERVAL
    private var hasRenderedFrame = false

    private fun dispatchImageGetter(frameTimeUs: Long){
        if(hasRecycled || !frameRequestInFlight.compareAndSet(false, true)) return
        frameFetchingThreadPool.execute {
            try {
                if(hasRecycled) return@execute
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        TARGET_PREVIEW_WIDTH,
                        TARGET_PREVIEW_HEIGHT
                    )
                } else {
                    retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                if(frame != null){
                    synchronized(composer) {
                        composer.drawBitmap(frame, null, tmpRectF, null, FittingMode.FIT, 0.5f, 0.5f)
                        hasRenderedFrame = true
                    }
                    frame.recycle()
                }
            } catch (_: Exception) {
            } finally {
                frameRequestInFlight.set(false)
            }
        }
    }

    override fun getFrame(deltaTime: Float): Bitmap {
        if(hasRecycled) throw IllegalAccessException("Image has been destroyed.")
        currentTime += deltaTime
        frameRequestElapsed += deltaTime
        if(!hasRenderedFrame || frameRequestElapsed >= FRAME_REQUEST_INTERVAL){
            val frameTimeUs = (((currentTime * 1000.0f).toLong() % pDuration) * 1000L)
            dispatchImageGetter(frameTimeUs)
            frameRequestElapsed = 0.0f
        }
        return compBitmap
    }

    override fun recycle() {
        try{
            pHasRecycled = true
            compBitmap.recycle()
            retriever.release()
        }catch(e:Exception){}
    }

}
