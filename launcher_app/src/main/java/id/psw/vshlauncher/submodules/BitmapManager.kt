package id.psw.vshlauncher.submodules

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import id.psw.vshlauncher.Logger
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.sdkAtLeast
import id.psw.vshlauncher.views.asBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class BitmapManager(private val ctx: Vsh) : IVshSubmodule {
    companion object {
        lateinit var instance : BitmapManager
        private const val TAG = "Bitman"
    }

    internal data class BitmapCache (
        val id : String,
        var bitmap : Bitmap?,
        var isLoading : Boolean = false,
        var isLoaded : Boolean = false,
        @Volatile var refCount : Int = 0,
        @Volatile var lastReleased : Long = 0L
            )

    internal val cache = ConcurrentHashMap<String, BitmapCache>()
    private lateinit var dclWhite : Bitmap
    private lateinit var dclBlack : Bitmap
    private lateinit var dclTrans : Bitmap
    private var keepLoader : Boolean = false
    private var printDebug = false
    private fun approximateBitmapSize(bmp: Bitmap) : Int{
        var pxp = when(bmp.config){
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 1
        }
        pxp = if(sdkAtLeast(Build.VERSION_CODES.O)){
            when(bmp.config){
                Bitmap.Config.RGBA_F16 -> 8
                Bitmap.Config.HARDWARE -> 16
                else -> pxp
            }
        }else{
            pxp
        }

        return pxp * bmp.width * bmp.height
    }

    val bitmapCount : Int get() = cache.count { it.value.bitmap != null }

    val totalCacheSize : Long get() {
        var l = 0L
        cache.values.forEach {
            val bmp = it.bitmap
            if(bmp != null){
                l += approximateBitmapSize(bmp).toLong()
            }
        }
        return l
    }

    val queueCount : Int get() {
        synchronized(loadQueue) {
            return loadQueue.size
        }
    }

    override fun onCreate() {
        instance = this
        try {
            dclWhite = Bitmap.createBitmap(intArrayOf(Color.WHITE), 0, 1, 1, 1, Bitmap.Config.ARGB_8888)
            dclBlack = Bitmap.createBitmap(intArrayOf(Color.BLACK), 0, 1, 1, 1, Bitmap.Config.ARGB_8888)
            dclTrans = Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT), 0, 1, 1, 1, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to create default bitmaps : ${e.message}")
        }
        Thread({ loaderThreadMain() }, "BitmanLoader").start()
    }

    override fun onDestroy() {
        keepLoader = false
        releaseAll()
    }

    private val loadQueue = mutableListOf<BitmapRef>()

    private fun logInfo(message: () -> String) {
        if (printDebug) {
            Logger.i(TAG, message())
        }
    }

    private fun loaderThreadMain(){
        keepLoader = true
        logInfo { "Loader thread started" }
        while(keepLoader){
            var q : BitmapRef? = null
            synchronized(loadQueue){
                if(loadQueue.isNotEmpty()){
                    q = loadQueue.removeAt(0)
                }
            }

            if(q != null){
                val item = q!!
                val h = cache[item.id]
                
                if(h != null && h.refCount > 0){
                    try{
                        val result = item.loader()
                        h.bitmap = result
                        if(result != null){
                            val sz = approximateBitmapSize(result).toLong()
                            h.isLoaded = true
                            logInfo { "[${item.id}] Loaded - ${sz.asBytes()}" }
                        }else{
                            Logger.w(TAG, "[${item.id}] Loader returned null")
                            h.isLoaded = false
                        }
                    }catch (e:Throwable){
                        Logger.e(TAG, "[${item.id}] Load Exception : ${e.message}")
                        h.isLoaded = false
                    }
                }
                h?.isLoading = false
            }else{
                cleanup()
                try { Thread.sleep(50L) } catch(_:Exception) {}
            }
        }
    }

    private fun findHandle(ref: BitmapRef) : BitmapCache? = cache[ref.id]

    fun load(bitmapRef: BitmapRef) {
        var handle = cache[bitmapRef.id]
        if (handle == null) {
            val newH = BitmapCache(bitmapRef.id, null, isLoading = false, isLoaded = false, refCount = 0)
            handle = cache.putIfAbsent(bitmapRef.id, newH) ?: newH
        }

        val shouldQueue = !handle.isLoading && (!handle.isLoaded || handle.bitmap == null)

        if (shouldQueue) {
            handle.isLoading = true
            synchronized(loadQueue) {
                if (loadQueue.none { it.id == bitmapRef.id }) {
                    loadQueue.add(bitmapRef)
                }
            }
        }

        handle.refCount++
        handle.lastReleased = 0L

        if (handle.refCount > 1) {
            logInfo { "[${handle.id}] - Reference Add : ${handle.refCount}" }
        } else {
            logInfo { "[${handle.id}] - Load Queued" }
        }
    }

    fun cleanup(){
        val now = System.currentTimeMillis()
        val toRemove = cache.filter { 
            val h = it.value
            h.refCount <= 0 && (now - h.lastReleased > 5000L) // 5s grace period
        }
        toRemove.forEach { (id, h) ->
            h.bitmap?.recycle()
            cache.remove(id)
        }
    }

    fun get(bitmapRef: BitmapRef) : Bitmap {
        val handle = cache[bitmapRef.id]
        if (handle == null) {
            load(bitmapRef)
        }
        return cache[bitmapRef.id]?.bitmap ?: when(bitmapRef.defColor){
            BitmapRef.FallbackColor.Black -> dclBlack
            BitmapRef.FallbackColor.White -> dclWhite
            BitmapRef.FallbackColor.Transparent -> dclTrans
        }
    }

    fun isLoading(bitmapRef: BitmapRef) : Boolean =
        cache[bitmapRef.id]?.isLoading == true

    fun release(bitmapRef: BitmapRef){
        cache[bitmapRef.id]?.let { handle ->
            handle.refCount--
            if(handle.refCount <= 0){
                handle.lastReleased = System.currentTimeMillis()
            }
            logInfo { "[${handle.id}] - Reference Min ${handle.refCount}" }
        }
    }

    fun forceEvict(id: String) {
        cache.remove(id)?.let { h ->
            h.bitmap?.recycle()
            logInfo { "[$id] Force evicted from cache" }
        }
    }

    fun releaseAll(){
        cache.values.forEach { it.bitmap?.recycle() }
        cache.clear()
    }

    fun isLoaded(ref: BitmapRef): Boolean = cache[ref.id]?.isLoaded == true
}
