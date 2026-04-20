package id.psw.vshlauncher.submodules

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.addToCategory
import id.psw.vshlauncher.sdkAtLeast
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.types.items.XmbNodeItem
import id.psw.vshlauncher.types.media.LinearMediaList
import id.psw.vshlauncher.types.media.MediaData
import id.psw.vshlauncher.types.media.MusicData
import id.psw.vshlauncher.types.media.PhotoData
import id.psw.vshlauncher.types.media.VideoData
import id.psw.vshlauncher.types.media.XmbMusicItem
import id.psw.vshlauncher.types.media.XmbPhotoItem
import id.psw.vshlauncher.types.media.XmbVideoItem
import id.psw.vshlauncher.views.dialogviews.ConfirmDialogView
import id.psw.vshlauncher.views.dialogviews.SetWallpaperDialogView
import id.psw.vshlauncher.xmb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaListingSubmodule(private val vsh : Vsh) : IVshSubmodule {
    private data class PhotoBucketListing(
        val id: String,
        val displayName: String,
        val identityKey: String,
        val priority: Int,
        val sortKey: String,
        val isJunkLike: Boolean,
        val items: ArrayList<XmbPhotoItem> = arrayListOf()
    )

    private val linearMediaList = LinearMediaList()
    private val photoBuckets = linkedMapOf<String, PhotoBucketListing>()
    private var mediaListingStarted = false
    private var pendingRefresh = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable {
        pendingRefresh = false
        mediaListingStart()
    }

    private fun requestBufferedRefresh() {
        if(pendingRefresh) return
        pendingRefresh = true
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, 1500)
    }

    companion object {
        const val RQI_PICK_PHOTO_DIR = 1024 + 0xFE
        const val TAG = "MediaListing"
        private const val PHOTO_BUCKET_PROP = "photo_bucket_generated"
    }

    val isListingInProgress: Boolean get() = mediaListingStarted

    private val videoProjection = arrayOf(
        MediaStore.Video.VideoColumns._ID,          /* long _id */
        MediaStore.Video.VideoColumns.DISPLAY_NAME, /* string display_name */
        MediaStore.Video.VideoColumns.DATA,         /* string absolute_path */
        MediaStore.Video.VideoColumns.SIZE,         /* long size */
        MediaStore.Video.VideoColumns.DURATION,     /* long duration_ms */
        MediaStore.Video.VideoColumns.MIME_TYPE     /* string mime_type */
    )

    private val audioProjection = arrayOf(
        MediaStore.Audio.AudioColumns._ID,       /* long _id */
        MediaStore.Audio.AudioColumns.DATA,      /* string absolute_path */
        MediaStore.Audio.AudioColumns.TITLE,     /* string title */
        MediaStore.Audio.AudioColumns.ALBUM,     /* string album */
        MediaStore.Audio.AudioColumns.ARTIST,    /* string artist */
        MediaStore.Audio.AudioColumns.SIZE,      /* long size */
        MediaStore.Audio.AudioColumns.DURATION,  /* long duration_ms */
        MediaStore.Audio.AudioColumns.MIME_TYPE  /* string mime_type */
    )

    private val photoProjection = arrayOf(
        MediaStore.Images.ImageColumns._ID,          /* long _id */
        MediaStore.Images.ImageColumns.DISPLAY_NAME, /* string display_name */
        MediaStore.Images.ImageColumns.DATA,         /* string absolute_path */
        MediaStore.Images.ImageColumns.SIZE,         /* long size */
        MediaStore.Images.ImageColumns.DATE_ADDED,   /* long date_added */
        MediaStore.Images.ImageColumns.MIME_TYPE,    /* string mime_type */
        MediaStore.Images.ImageColumns.BUCKET_ID,    /* string/long bucket_id */
        MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, /* string bucket_display_name */
        MediaStore.MediaColumns.RELATIVE_PATH        /* string relative_path */
    )

    override fun onCreate() {
        mediaListingRegisterObserver()
    }

    override fun onDestroy() {
        mediaListingUnregisterObserver()
    }

    fun <T> Cursor.getValue(id: String, getter : Cursor.(Int) -> T, defVal : T)  : T {
        val kId = getColumnIndex(id)
        if(kId < 0) return defVal
        return getter(this, kId)
    }

    private fun Cursor.getStringValue(id: String, defVal: String): String {
        val kId = getColumnIndex(id)
        if (kId < 0 || isNull(kId)) return defVal
        return getString(kId) ?: defVal
    }

    private fun cleanMediaListing(){
        for(cat in vsh.categories){
            fun checkAndRemove(content: ArrayList<XmbItem>) {
                synchronized(content) {
                    val toRemove = arrayListOf<XmbItem>()
                    for(item in content){
                        if(item is XmbMusicItem || item is XmbPhotoItem || item is XmbVideoItem){
                            toRemove.add(item)
                            item.onScreenInvisible(item)
                        } else if (item is id.psw.vshlauncher.types.items.XmbNodeItem) {
                            checkAndRemove(item.content)
                            if(item.getProperty(PHOTO_BUCKET_PROP, false)) {
                                toRemove.add(item)
                            }
                        }
                    }
                    content.removeAll(toRemove.toSet())
                }
            }

            checkAndRemove(cat.content)
        }

        for(i in linearMediaList.musics) i.onScreenInvisible(i)
        for(i in linearMediaList.photos) i.onScreenInvisible(i)
        for(i in linearMediaList.videos) i.onScreenInvisible(i)

        linearMediaList.musics.clear()
        linearMediaList.photos.clear()
        linearMediaList.videos.clear()
        photoBuckets.clear()
    }

    private fun addVideoListing(root: Uri, cursor: Cursor){
        try {
            // TODO : Use getString for Unknowns
            val id    = cursor.getValue(videoProjection[0], Cursor::getLong, 0L)
            val name  = cursor.getValue(videoProjection[1], Cursor::getString, "Unknown.3gp")
            val path  = cursor.getValue(videoProjection[2], Cursor::getString, "/dev/null")
            val size  = cursor.getValue(videoProjection[3], Cursor::getLong, 0L)
            val dur   = cursor.getValue(videoProjection[4], Cursor::getLong, 0L)
            val mime  = cursor.getValue(videoProjection[5], Cursor::getString, "video/*")
            val uri   = ContentUris.withAppendedId(root, id)

            linearMediaList.videos.add(
                XmbVideoItem(vsh, VideoData(id, uri, path, name, size, dur, mime))
            )
        }catch(cio: CursorIndexOutOfBoundsException){
            cio.printStackTrace()
        }
    }
    private fun addAudioListing(root: Uri, cursor: Cursor){
        try {
            // TODO : Use getString for Unknowns
            val id    = cursor.getValue(audioProjection[0], Cursor::getLong,   0L)
            val path  = cursor.getValue(audioProjection[1], Cursor::getString, "/dev/null")
            val title = cursor.getValue(audioProjection[2], Cursor::getString, "No Title")
            val album = cursor.getValue(audioProjection[3], Cursor::getString, "No Album")
            val artis = cursor.getValue(audioProjection[4], Cursor::getString, "Unknown Artist")
            val size  = cursor.getValue(audioProjection[5], Cursor::getLong,   0L)
            val dur   = cursor.getValue(audioProjection[6], Cursor::getLong,   0L)
            val mime  = cursor.getValue(audioProjection[7], Cursor::getString, "audio/*")
            val uri   = ContentUris.withAppendedId(root, id)

            linearMediaList.musics.add(
                XmbMusicItem(vsh, MusicData(id, uri, path, title, album, artis, size, dur, mime))
            )
        }catch(cio: CursorIndexOutOfBoundsException){
            cio.printStackTrace()
        }
    }

    fun createMediaMenuItems(_itemMenus : ArrayList<XmbMenuItem>, data: MediaData){
        // Play Media
        _itemMenus.add(XmbMenuItem.XmbMenuItemLambda(
            { vsh.getString(R.string.media_play ) },
            {false}, 0)
        {
            vsh.openFileOnExternalApp(File(data.data), false, vsh.getString(R.string.media_play))
        })

        // Open With
        _itemMenus.add(XmbMenuItem.XmbMenuItemLambda(
            { vsh.getString(R.string.media_open_with) },
            {false}, 1)
        {
            vsh.openFileOnExternalApp(File(data.data), true, vsh.getString(R.string.media_open_with))
        })

        if (data is PhotoData) {
            _itemMenus.add(XmbMenuItem.XmbMenuItemLambda(
                { vsh.getString(R.string.media_set_as_wallpaper) },
                { false }, 2)
            {
                val submenu = arrayListOf<XmbMenuItem>(
                    XmbMenuItem.XmbMenuItemLambda(
                        { vsh.getString(R.string.media_set_as_launcher_wallpaper) },
                        { false },
                        0
                    ) {
                        SetWallpaperDialogView.setLauncherWallpaper(vsh, data.uri)
                    },
                    XmbMenuItem.XmbMenuItemLambda(
                        { vsh.getString(R.string.media_set_as_device_wallpaper) },
                        { false },
                        1
                    ) {
                        SetWallpaperDialogView.setDeviceWallpaper(vsh, vsh.xmb, data.uri)
                    }
                )
                vsh.xmbView?.widgets?.sideMenu?.show(submenu)
            })
        }

        // Delete File
        _itemMenus.add(XmbMenuItem.XmbMenuItemLambda(
            { vsh.getString(R.string.media_delete) },
            {false}, 3)
        {
            vsh.xmbView?.showDialog(
                ConfirmDialogView(
                    vsh.safeXmbView,
                    vsh.getString(R.string.media_delete),
                    R.drawable.ic_delete,
                    vsh.getString(R.string.media_delete_confirmation).format(data.data)
                ){ confirmed ->
                    if(confirmed){
                        deleteMedia(data.uri)
                    }
                })
        })

        // Share File (You cannot copy since launcher have no direct interface to storage other than it's own)
        _itemMenus.add(XmbMenuItem.XmbMenuItemLambda(
            { vsh.getString(R.string.media_share) },
            {false}, 4)
        {
            val i= Intent(Intent.ACTION_SEND)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.setDataAndType(data.uri, vsh.contentResolver.getType(data.uri))
            i.putExtra(Intent.EXTRA_STREAM, data.uri)

            vsh.xmb.startActivity(Intent.createChooser(i, vsh.getString(R.string.media_share)))
        })
    }


    private fun deleteMedia(uri:Uri){
        try {
            vsh.contentResolver.delete(uri, null, null)
            // requestBufferedRefresh() // ContentObserver will trigger this
        }catch (scEx: SecurityException){
            if(sdkAtLeast(Build.VERSION_CODES.Q)){
                val rse = scEx as RecoverableSecurityException
                val isr = IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build()
                vsh.xmb.mediaDeletionActivityResult.launch(isr)
            }
        }
    }

    private fun beginMediaListingVideo(){
        val vidCols = if(sdkAtLeast(30))
            arrayOf(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            )
        else arrayOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        for(vidCol in vidCols){
            val vidCur = vsh.contentResolver.query(vidCol, videoProjection, null, null, null)

            if(vidCur != null){
                vidCur.moveToFirst()
                while(!vidCur.isAfterLast){
                    addVideoListing(vidCol, vidCur)
                    vidCur.moveToNext()
                }
                vidCur.close()
            }
        }
    }

    private fun beginMediaListingAudio(){
        val sndCols = if(sdkAtLeast(30))
            arrayOf(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            )
        else arrayOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )

        for(sndCol in sndCols){
            val sndCur = vsh.contentResolver.query(sndCol, audioProjection, null, null, null)

            if(sndCur != null){
                sndCur.moveToFirst()
                while(!sndCur.isAfterLast){
                    addAudioListing(sndCol, sndCur)
                    sndCur.moveToNext()
                }
                sndCur.close()
            }
        }
    }

    fun addPhotoDirectory(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            vsh.xmb.startActivityForResult(i, RQI_PICK_PHOTO_DIR)
        }
    }

    fun onDirectoryPicked(i: Intent){

    }

    private fun addPhotoListing(root: Uri, cursor: Cursor){
        try {
            val id    = cursor.getValue(photoProjection[0], Cursor::getLong, 0L)
            val name  = cursor.getStringValue(photoProjection[1], "Unknown.jpg")
            val path  = cursor.getStringValue(photoProjection[2], "/dev/null")
            val size  = cursor.getValue(photoProjection[3], Cursor::getLong, 0L)
            val date  = cursor.getValue(photoProjection[4], Cursor::getLong, 0L)
            val mime  = cursor.getStringValue(photoProjection[5], "image/*")
            val bucketId = cursor.getStringValue(photoProjection[6], "")
            val bucketName = cursor.getStringValue(photoProjection[7], "")
            val relativePath = cursor.getStringValue(photoProjection[8], "")
            val uri   = ContentUris.withAppendedId(root, id)

            val bucket = resolvePhotoBucket(root, bucketId, bucketName, relativePath, path) ?: return
            val dateStr = android.text.format.DateFormat.format("yyyy/MM/dd", date * 1000L).toString()
            val photoItem = XmbPhotoItem(vsh, PhotoData(id, uri, path, name, size, dateStr, mime))

            linearMediaList.photos.add(photoItem)
            bucket.items.add(photoItem)
        }catch(cio: CursorIndexOutOfBoundsException){
            cio.printStackTrace()
        }
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim('/')
    }

    private fun shouldIgnorePhoto(path: String, relativePath: String): Boolean {
        val lowerPath = normalizePath(path).lowercase()
        val lowerRelativePath = normalizePath(relativePath).lowercase()
        return listOf(
            "/android/data/",
            "/android/obb/",
            "/cache/",
            "/.thumbnails/",
            "/thumbnails/",
            "/tmp/",
            "/temp/"
        ).any { marker ->
            lowerPath.contains(marker) || lowerRelativePath.contains(marker.trim('/'))
        }
    }

    private fun isJunkLikeBucket(path: String, bucketName: String): Boolean {
        val lowerPath = normalizePath(path).lowercase()
        val lowerName = bucketName.lowercase()

        val strongJunkMarkers = listOf(
            "/android/media/",
            "retroarch",
            "ppsspp",
            "duckstation",
            "aethersx2",
            "nethersx2",
            "dolphin emulator",
            "citra",
            "melonds",
            "textures",
            "texture",
            "savestate",
            "save state",
            "states",
            "shader",
            "cover",
            "boxart",
            "cache"
        )

        return strongJunkMarkers.any { marker ->
            lowerPath.contains(marker) || lowerName.contains(marker)
        }
    }

    private fun toStablePhotoBucketIdentity(relativePath: String, absoluteParentPath: String, bucketName: String): String {
        val normalizedRelativePath = normalizePath(relativePath)
        val normalizedAbsoluteParent = normalizePath(absoluteParentPath)
        val fallbackName = bucketName.ifBlank { File(normalizedAbsoluteParent).name }

        return when {
            normalizedRelativePath.equals("DCIM/Camera", true) ||
                normalizedAbsoluteParent.endsWith("/DCIM/Camera", true) -> "photos/dcim/camera"
            normalizedRelativePath.contains("Screenshots", true) ||
                fallbackName.equals("Screenshots", true) -> "photos/screenshots"
            normalizedRelativePath.startsWith("Download", true) ||
                normalizedRelativePath.startsWith("Downloads", true) ||
                fallbackName.equals("Downloads", true) ||
                fallbackName.equals("Download", true) -> "photos/downloads"
            normalizedRelativePath.startsWith("Pictures", true) -> "photos/${normalizedRelativePath.lowercase()}"
            normalizedRelativePath.startsWith("DCIM", true) -> "photos/${normalizedRelativePath.lowercase()}"
            normalizedRelativePath.isNotBlank() -> "photos/${normalizedRelativePath.lowercase()}"
            normalizedAbsoluteParent.isNotBlank() -> "photos/${normalizedAbsoluteParent.lowercase()}"
            else -> "photos/${fallbackName.lowercase()}"
        }
    }

    private fun resolvePhotoBucket(root: Uri, bucketId: String, bucketName: String, relativePath: String, absolutePath: String): PhotoBucketListing? {
        if (shouldIgnorePhoto(absolutePath, relativePath)) return null

        val normalizedRelativePath = normalizePath(relativePath)
        val absoluteParentPath = normalizePath(File(absolutePath).parent ?: "")
        val bucketLabelFallback = bucketName.ifBlank {
            File(absoluteParentPath).name.ifBlank { vsh.getString(R.string.category_photo) }
        }
        val identityKey = toStablePhotoBucketIdentity(relativePath, absoluteParentPath, bucketLabelFallback)
        val isJunkLike = isJunkLikeBucket(normalizedRelativePath.ifBlank { absoluteParentPath }, bucketLabelFallback)

        val (displayName, priority) = when {
            normalizedRelativePath.equals("DCIM/Camera", true) ||
                absoluteParentPath.endsWith("/DCIM/Camera", true) -> "DCIM / Camera" to 0
            normalizedRelativePath.contains("Screenshots", true) ||
                bucketLabelFallback.equals("Screenshots", true) -> "Screenshots" to 1
            normalizedRelativePath.startsWith("Download", true) ||
                normalizedRelativePath.startsWith("Downloads", true) ||
                bucketLabelFallback.equals("Downloads", true) ||
                bucketLabelFallback.equals("Download", true) -> "Downloads" to 2
            normalizedRelativePath.startsWith("Pictures", true) -> normalizedRelativePath to 3
            normalizedRelativePath.startsWith("DCIM", true) -> normalizedRelativePath to 4
            isJunkLike -> bucketLabelFallback to 100
            normalizedRelativePath.isNotBlank() -> normalizedRelativePath to 10
            else -> bucketLabelFallback to 10
        }

        val nodeId = "photo_bucket_" + identityKey
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        return photoBuckets.getOrPut(nodeId) {
            PhotoBucketListing(
                id = nodeId,
                displayName = displayName,
                identityKey = identityKey,
                priority = priority,
                sortKey = displayName.lowercase(),
                isJunkLike = isJunkLike
            )
        }
    }

    private fun beginMediaListingPhoto(){
        val imgCols = if(sdkAtLeast(30))
            arrayOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            )
        else arrayOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI
        )

        for(imgCol in imgCols){
            val imgCur = vsh.contentResolver.query(
                imgCol,
                photoProjection,
                null,
                null,
                "${MediaStore.Images.ImageColumns.DATE_ADDED} DESC"
            )

            if(imgCur != null){
                imgCur.moveToFirst()
                while(!imgCur.isAfterLast){
                    addPhotoListing(imgCol, imgCur)
                    imgCur.moveToNext()
                }
                imgCur.close()
            }
        }
    }

    var hasPermissionCached = false
        private set

    val hasPermission: Boolean get() {
        var ok =  true
        if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            ok = vsh.hasPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO ) && ok
            ok = vsh.hasPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO ) && ok
            ok = vsh.hasPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES) && ok
        }
        hasPermissionCached = ok
        return ok
    }

    fun requestPermission(){
        if(!hasPermission){
            val names = arrayListOf<String>()

            // names.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                names.add(Manifest.permission.READ_MEDIA_AUDIO )
                names.add(Manifest.permission.READ_MEDIA_VIDEO )
                names.add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            val arr = Array(names.size){ i -> names[i] }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                vsh.xmb.requestPermissions(arr, RQI_PICK_PHOTO_DIR)
            }
        }
    }

    fun mediaListingStart(){
        // No permission, return
        if(!hasPermission) return

        vsh.lifeScope.launch {
            // Return directly if media already started
            if(mediaListingStarted) return@launch

            withContext(Dispatchers.Main) {
                cleanMediaListing()
            }

            withContext(Dispatchers.IO) {
                mediaListingStarted = true
                try {
                    beginMediaListingVideo()
                    beginMediaListingAudio()
                    beginMediaListingPhoto()

                    withContext(Dispatchers.Main) {
                        addMediaToCategories()
                    }
                } finally {
                    // Update Items
                    mediaListingStarted = false
                }
            }
        }
    }

    private fun addMediaToCategories() {
        vsh.lifeScope.launch(Dispatchers.Main) {
            for(l in linearMediaList.musics){
                Log.d(TAG, "Music : ${l.id}")
                vsh.addToCategory(Vsh.ITEM_CATEGORY_MUSIC, l)
            }

            for(l in linearMediaList.videos){
                Log.d(TAG, "Video : ${l.id}")
                vsh.addToCategory(Vsh.ITEM_CATEGORY_VIDEO, l)
            }

            val photoNode = vsh.categories
                .find { it.id == Vsh.ITEM_CATEGORY_PHOTO }
                ?.findNode("photo_ms") as? XmbNodeItem

            if(photoNode != null){
                photoBuckets.values
                    .filter { bucket -> !bucket.isJunkLike || bucket.items.size >= 3 }
                    .sortedWith(compareBy<PhotoBucketListing> { it.priority }.thenBy { it.sortKey })
                    .forEach { bucket ->
                        Log.d(TAG, "Photo bucket : ${bucket.displayName}")
                        val bucketNode = XmbNodeItem(
                            vsh,
                            bucket.id,
                            bucket.displayName,
                            R.drawable.category_photo,
                            "${bucket.items.size} photos"
                        ).apply {
                            setProperty(PHOTO_BUCKET_PROP, true)
                        }

                        bucket.items.forEach { photo ->
                            Log.d(TAG, "Photo : ${photo.id}")
                            bucketNode.addItem(photo)
                        }

                        photoNode.addItem(bucketNode)
                    }
            }
        }
    }

    private val videoObserver : ContentObserver by lazy {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.i(TAG, "Video media changed, requesting buffered refresh. uri=$uri")
                requestBufferedRefresh()
            }
        }
        o
    }

    private val audioObserver : ContentObserver by lazy {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.i(TAG, "Audio media changed, requesting buffered refresh. uri=$uri")
                requestBufferedRefresh()
            }
        }
        o
    }

    private fun mediaListingRegisterObserver(){
        vsh.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true, videoObserver)

        vsh.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true, audioObserver)

        vsh.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, photoObserver)
    }

    private fun mediaListingUnregisterObserver(){
        vsh.contentResolver.unregisterContentObserver(videoObserver)
        vsh.contentResolver.unregisterContentObserver(audioObserver)
        vsh.contentResolver.unregisterContentObserver(photoObserver)
    }

    private val photoObserver : ContentObserver by lazy {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.i(TAG, "Photo media changed, requesting buffered refresh. uri=$uri")
                requestBufferedRefresh()
            }
        }
        o
    }
}
