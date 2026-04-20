package id.psw.vshlauncher

import android.app.ActivityManager
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jakewharton.threetenabp.AndroidThreeTen
import id.psw.vshlauncher.livewallpaper.NativeGL
import id.psw.vshlauncher.submodules.*
import id.psw.vshlauncher.types.*
import id.psw.vshlauncher.types.Stack
import id.psw.vshlauncher.types.items.XmbAppItem
import id.psw.vshlauncher.types.items.XmbCustomLaunchItem
import id.psw.vshlauncher.types.items.XmbItemCategory
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.types.items.XmbNodeItem
import id.psw.vshlauncher.typography.FontCollections
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.filterBySearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception
import java.lang.StringBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

class Vsh : Application() {

    data class CustomLaunchItemRecord(
        val itemId: String,
        val type: String,
        val title: String,
        val categoryId: String,
        val parentNodeId: String = "",
        val target: String = "",
        val fileUri: String = "",
        val platformId: String = "unknown",
        val emulatorAdapterId: String = "",
        val packageName: String = "",
        val componentName: String = "",
        val gamebootEnabled: Boolean = true,
        val hidden: Boolean = false,
        val iconRef: String = ""
    )

    private data class EmulatorAdapter(
        val id: String,
        val platformId: String,
        val label: String,
        val packageName: String,
        val componentName: String = "",
        val launchStyle: String
    )

    data class BuiltInNodeLaunchOverride(
        val nodeId: String,
        val type: String,
        val target: String,
        val label: String
    )

    companion object {
        val MEMORY_STICK_LOADING_NODE_IDS = setOf("photo_ms", "music_ms", "video_ms", "game_ms", "apps_ms")
        private lateinit var _appFont : Typeface
        val AppFont get() = _appFont
        const val TAG = "VshApp"
        const val ITEM_CATEGORY_HOME = "vsh_home"
        const val ITEM_CATEGORY_APPS = "vsh_apps"
        const val ITEM_CATEGORY_GAME = "vsh_game"
        const val ITEM_CATEGORY_VIDEO = "vsh_video"
        const val ITEM_CATEGORY_SHORTCUT = "vsh_shortcut"
        const val ITEM_CATEGORY_PHOTO = "vsh_photos"
        const val ITEM_CATEGORY_MUSIC = "vsh_music"
        const val ITEM_CATEGORY_NETWORK = "vsh_network"
        const val ITEM_CATEGORY_PSN = "vsh_psn"
        const val ITEM_CATEGORY_SETTINGS = "vsh_settings"
        const val NODE_NETWORK_ONLINE_MANUALS = "network_online_manuals"
        const val NODE_NETWORK_REMOTE_PLAY = "network_remote_play"
        const val NODE_NETWORK_INTERNET_RADIO = "network_internet_radio"
        const val NODE_NETWORK_RSS_CHANNEL = "network_rss_channel"
        const val NODE_NETWORK_SEARCH = "network_search"
        const val NODE_PSN_ACCOUNT_MANAGEMENT = "psn_account_management"
        const val NODE_PSN_STORE = "psn_store"
        const val NODE_APPS_MEMORY_STICK = "apps_ms"
        const val COPY_DATA_SIZE_BUFFER = 10240
        const val ACT_REQ_INSTALL_PACKAGE = 4496
        const val ACT_REQ_MEDIA_LISTING = 0x9121
        const val ACT_REQ_LOG_EXPORT = 0x6200
        const val ACT_REQ_PICK_CUSTOM_ICON = 0x7000
        const val ACT_REQ_PICK_CUSTOM_BACKDROP = 0x7100
        const val ACT_REQ_PICK_CUSTOM_BACKDROP_OVERLAY = 0x7200
        const val ACT_REQ_PICK_CUSTOM_BACK_SOUND = 0x7300
        const val ACT_REQ_PICK_CUSTOM_ANIM_ICON = 0x7400
        const val ACT_REQ_PICK_CUSTOM_PORT_BACKDROP = 0x7500
        const val ACT_REQ_PICK_CUSTOM_PORT_BACKDROP_OVERLAY = 0x7600
        const val ACT_REQ_PICK_SHELL_ICON = 0x7700
        const val ACT_REQ_EXPORT_XTF = 0x7800
        const val ACT_REQ_IMPORT_XTF = 0x7900
        const val ACT_REQ_PICK_CUSTOM_LAUNCH_FILE = 0x7A00
        const val ITEM_BUILTIN_ICON_BITMAP_SIZE = 300

        val CONFIGURABLE_BUILTIN_LAUNCH_NODE_IDS = setOf(
            NODE_PSN_ACCOUNT_MANAGEMENT,
            NODE_PSN_STORE,
            NODE_NETWORK_ONLINE_MANUALS,
            NODE_NETWORK_REMOTE_PLAY,
            NODE_NETWORK_INTERNET_RADIO,
            NODE_NETWORK_RSS_CHANNEL,
            NODE_NETWORK_SEARCH
        )

        private val DEFAULT_HIDDEN_UNCONFIGURED_BUILTIN_NODE_IDS = setOf(
            NODE_PSN_ACCOUNT_MANAGEMENT,
            NODE_PSN_STORE,
            NODE_NETWORK_ONLINE_MANUALS,
            NODE_NETWORK_REMOTE_PLAY,
            NODE_NETWORK_INTERNET_RADIO,
            NODE_NETWORK_RSS_CHANNEL
        )

        val dbgMemInfo = Debug.MemoryInfo()
        val actMemInfo = ActivityManager.MemoryInfo()
    }

    var showDebuggerCount = 0

    private var appUserUid = 0
    /** Used as user's identifier when using an external storage */
    val UserUid get() = appUserUid

    val selectStack = Stack<String>()
    var aggressiveUnloading = true
    val runtimeTriageList = ArrayList<String>()
    var xmbView : XmbView? = null

    val haveXmbView get() = xmbView != null
    val safeXmbView get()= xmbView!!

    lateinit var mainHandle : Handler
    var playAnimatedIcon = true

    val categories = arrayListOf<XmbItemCategory>()
    /** Return all item in current selected category or current active item, including the hidden ones */
    val items : ArrayList<XmbItem>? get(){
        try {
            var root = categories.visibleItems.find { it.id == selectedCategoryId }?.content
            var i = 0
            while(root != null && i < selectStack.size){
                root = root.find { it.id == selectStack[i]}?.content
                i++
            }
            if(root != null){
                if(root.indexOfFirst { it.id == selectedItemId } < 0 && root.size > 0){
                    selectedItemId = root[0].id
                }
            }
            return root
        }catch(e:Exception){
            e.printStackTrace()
            vsh.postNotification(R.drawable.ic_error, e.javaClass.name, e.toString())
        }
        return arrayListOf()
    }

    val activeParent : XmbItem? get(){
        var root = categories.visibleItems.find { it.id == selectedCategoryId }
        var i = 0
        while(root != null && i < selectStack.size){
            root = root.content?.find { it.id == selectStack[i]}
            i++
        }
        return root
    }

    val hoveredItem : XmbItem? get() = items?.find { it.id == selectedItemId }
    private var _waveShouldRefresh = false
    var waveShouldReReadPreferences : Boolean get() {
        val r = _waveShouldRefresh
        _waveShouldRefresh = false
        return r
    }

    set(v) { _waveShouldRefresh = v }

    var selectedCategoryId = ITEM_CATEGORY_APPS
    var selectedItemId = ""

    val itemCursorX get() = categories.visibleItems.indexOfFirst { it.id == selectedCategoryId }
    val itemCursorY get() = (items?.visibleItems?.filterBySearch(this)?.indexOfFirst { it.id == selectedItemId } ?: -1).coerceAtLeast(0)
    var itemOffsetX = 0.0f
    var itemOffsetY = 0.0f
    var itemBackdropAlphaTime = 0.0f
    var _prioritizeTvIntent = false

    val isInRoot : Boolean get() = selectStack.size == 0
    var notificationLastCheckTime = 0L
    val notifications = arrayListOf<XmbNotification>()
    val threadPool: ExecutorService = Executors.newCachedThreadPool()
    val loadingHandles = arrayListOf<XmbLoadingHandle>()
    val hiddenCategories = arrayListOf<String>()
    var itemEditing : XmbItem? = null

    val M = SubmoduleManager(this)
    var isTv = false
    var shouldShowExitOption = false

    var isNowRendering = false

    var useInternalWave = true
    var lifeScope : LifecycleCoroutineScope = ProcessLifecycleOwner.get().lifecycleScope
    private var memoryStickFreeSpaceCache = ""
    private var memoryStickFreeSpaceLastReadAt = 0L
    private var pendingCustomLaunchFileCategoryId = ""
    private var pendingCustomLaunchFileName = ""

    private fun reloadPreference() {
        setActiveLocale(readSerializedLocale(M.pref.get(PrefEntry.SYSTEM_LANGUAGE, "")))
        CifLoader.videoIconMode = VideoIconMode.fromInt(M.pref.get(PrefEntry.VIDEO_ICON_PLAY_MODE, 0))
        val o = M.pref.get(PrefEntry.SYSTEM_VISIBLE_APP_DESC, XmbAppItem.DescriptionDisplay.PackageName.ordinal)
        XmbAppItem.descriptionDisplay = enumFromInt(o)
        XmbAdaptiveIconRenderer.Companion.AdaptiveRenderSetting.iconPriority =
            M.pref.get(PrefEntry.ICON_RENDERER_PRIORITY, 0b01111000)
        val tvAsDefault = isTv.select(1, 0)
        _prioritizeTvIntent = M.pref.get(PrefEntry.LAUNCHER_TV_INTENT_FIRST, tvAsDefault) != 0
    }

    override fun onCreate() {
        Logger.init(this)
        AndroidThreeTen.init(this)
        mainHandle = Handler(mainLooper)
        isTv = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }else{
            @Suppress("DEPRECATION") // TV Feature before Lollipop (IDK if there is exists)
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        }

        M.onCreate()
        reloadPreference()
        vsh.lifeScope.launch(Dispatchers.IO) {
            FontCollections.init(this@Vsh)
        }
        // Fresco.initialize(this)
        notificationLastCheckTime = SystemClock.uptimeMillis()
        registerInternalCategory()
        // M.apps.reloadAppList() // Deferred to Xmb.onCreate or first frame
        reloadShortcutList()
        M.settings.fillSettings()
        addHomeScreen()
        installBroadcastReceivers()
        super.onCreate()
    }

    private fun installBroadcastReceivers() {
        // Package Install / uninstall
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                postNotification(null, "Updating database...","Device package list has been changed, updating list...")
                M.apps.reloadAppList()
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    private var shouldExit = false
    var doMemoryInfoGrab = false
    private lateinit var meminfoThread : Thread
    private fun memoryInfoReaderFunc(){
        /* Disable memory usage reading :
        // Most Android phone would spams "memtrack module not found" or something like that
        val actman = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        while(!shouldExit){
            if(doMemoryInfoGrab){
                Debug.getMemoryInfo(dbgMemInfo)
                actman.getMemoryInfo(actMemInfo)
            }
            Thread.sleep(1000)
        }*/
    }

    private fun listLogAllSupportedLocale() {
        val sb = StringBuilder()
        sb.appendLine("Listed supported languages / locales in this app: ")
        supportedLocaleList.forEach {
            sb.appendLine("- ${getStringLocale(it, R.string.category_games)}")
        }
        Logger.d(TAG, sb.toString())
    }

    fun moveCursorX(right:Boolean){
        val items = categories.visibleItems
        if(items.isNotEmpty()){
            M.audio.preventPlayMedia = false
            var cIdx = items.indexOfFirst { it.id == selectedCategoryId }
            cIdx = cIdx.coerceIn(0, items.size - 1)
            val oldIdx = cIdx
            items[cIdx].lastSelectedItemId = selectedItemId // Save last category item id
            items[cIdx].content?.forEach { it.isHovered = false }
            if(right) cIdx++ else cIdx--
            cIdx = cIdx.coerceIn(0, items.size - 1)
            if(cIdx != oldIdx) {
                itemOffsetX = right.select(0.72f, -0.72f)
                itemBackdropAlphaTime = 0.0f
            }
            selectedItemId = items[cIdx].lastSelectedItemId // Load new category item id
            items[cIdx].content?.forEach { it.isHovered = it.id == selectedItemId }
            selectedCategoryId = items[cIdx].id
            xmbView?.widgets?.sideMenu?.selectedIndex = 0
            M.audio.playSfx(SfxType.Selection)

            xmbView?.screens?.mainMenu?.verticalMenu?.nameTextXOffset = 0.0f
            xmbView?.screens?.mainMenu?.verticalMenu?.descTextXOffset = 0.0f
        }
    }

    fun queryTexture(customId:String) : ArrayList<File> =FileQuery(VshBaseDirs.VSH_RESOURCES_DIR).withNames("$customId.png").execute(this)

    fun loadTexture(@DrawableRes d : Int, w:Int, h:Int, whiteFallback:Boolean = false ) : Bitmap {
        return ResourcesCompat.getDrawable(resources, d, null)?.toBitmap(w, h) ?: whiteFallback.select(XmbItem.WHITE_BITMAP, XmbItem.TRANSPARENT_BITMAP)
    }
    fun loadTexture(@DrawableRes d : Int, whiteFallback:Boolean = false ) : Bitmap {
        val dwb =ResourcesCompat.getDrawable(resources, d, null)
        return dwb?.toBitmap(dwb.intrinsicWidth, dwb.intrinsicHeight) ?: whiteFallback.select(XmbItem.WHITE_BITMAP, XmbItem.TRANSPARENT_BITMAP)
    }

    val allAppEntries = arrayListOf<XmbAppItem>()

    fun loadTexture(@DrawableRes d: Int, customId:String, w:Int, h:Int, whiteFallback: Boolean) : Bitmap{
        var retval : Bitmap? = null
        val file = queryTexture(customId).find { it.exists() }
        if(file != null){
            try {
                retval = BitmapFactory.decodeFile(file.absolutePath)
                val dr = retval.scale(w, h)
                retval.recycle()
                retval = dr
            }catch(e:Exception){
                postNotification(R.drawable.ic_error, e.javaClass.name, "Failed to decode file ${file.absolutePath} : ${e.message}")
            }
        }

        return retval ?: loadTexture(d, w, h, whiteFallback)
    }
    fun loadTexture(@DrawableRes d: Int, customId:String, whiteFallback: Boolean) : Bitmap{
        var retval : Bitmap? = null
        val file = queryTexture(customId).find { it.exists() }
        if(file != null){
            try {
                retval = BitmapFactory.decodeFile(file.absolutePath)
            }catch(e:Exception){
                postNotification(R.drawable.ic_error, e.javaClass.name, "Failed to decode file ${file.absolutePath} : ${e.message}")
            }
        }

        return retval ?: loadTexture(d, whiteFallback)
    }

    fun moveCursorY(bottom:Boolean){
        try{
            val items = items?.visibleItems?.filterBySearch(this)
            if(items != null){
                M.audio.preventPlayMedia = false
                if(items.isNotEmpty()){
                    var cIdx = items.indexOfFirst { it.id == selectedItemId }

                    if(cIdx == -1 && items.isNotEmpty()) cIdx = 0

                    val oldIdx = cIdx
                    if(bottom) cIdx++ else cIdx--
                    if(cIdx < 0) cIdx = items.size - 1
                    if(cIdx >= items.size ) cIdx = 0

                    cIdx  = cIdx.coerceIn(0, items.size - 1)
                    if(cIdx != oldIdx) {
                        itemOffsetY = bottom.select(0.72f, -0.72f)
                        itemBackdropAlphaTime = 0.0f
                    }
                    selectedItemId = items[cIdx].id
                }

                xmbView?.screens?.mainMenu?.verticalMenu?.nameTextXOffset = 0.0f
                xmbView?.screens?.mainMenu?.verticalMenu?.descTextXOffset = 0.0f

                // Update hovering
                items.forEach {
                    it.isHovered = it.id == selectedItemId
                }
                xmbView?.widgets?.sideMenu?.selectedIndex = 0
                M.audio.playSfx(SfxType.Selection)
            }
        }catch (e:ArrayIndexOutOfBoundsException){
            //
        }
    }

    fun doCategorySorting(){
        if(isInRoot){
            val cat = categories.visibleItems.find { it.id == selectedCategoryId }
            if(cat is XmbItemCategory){
                if(cat.sortable){
                    cat.onSwitchSort()
                    xmbView?.screens?.mainMenu?.sortHeaderDisplay = 5.25f
                }
            }
        }
    }

    fun launchActiveItem(){
        val item = hoveredItem
            if(item != null){
            if (tryLaunchBuiltInNodeOverride(item.id)) {
                M.audio.preventPlayMedia = true
                return
            }
            if(item.id == "photo_camera"){
                M.audio.preventPlayMedia = true
                openSystemCamera()
                return
            }
            if(item.id == "network_browser"){
                M.audio.preventPlayMedia = true
                openInternetBrowser()
                return
            }
            if(item.hasContent){
                M.audio.preventPlayMedia = false
                Logger.d(TAG, "Found content in item ${item.id}, pushing to content stack...")
                if (item.id in MEMORY_STICK_LOADING_NODE_IDS) {
                    xmbView?.screens?.mainMenu?.showStorageNodeLoadingSpinner(item.id)
                }
                if(isInRoot){
                    categories.find { it.id == selectedCategoryId }?.lastSelectedItemId = selectedItemId
                }else{
                    activeParent?.lastSelectedItemId = selectedItemId
                }

                selectedItemId = item.lastSelectedItemId
                selectStack.push(item.id)
                itemOffsetX = 0.8f
                M.audio.playSfx(SfxType.Confirm)
            }else{
                M.audio.preventPlayMedia = true
                item.launch()
            }
        }
    }

    fun launchWithGameboot(skipForItem: Boolean = false, launchAction: () -> Unit) {
        val gameboot = xmbView?.screens?.gameBoot
        if (gameboot == null) {
            launchAction()
            return
        }
        gameboot.bootInto(skipForItem, launchAction)
    }

    fun backStep(){
        if(selectStack.size > 0){
            M.audio.preventPlayMedia = false
            selectStack.pull()
            val lastItem = selectedItemId
            selectedItemId = if(selectStack.size == 0) {
                categories.find {it.id == selectedCategoryId}?.lastSelectedItemId ?: ""
            } else {
                activeParent?.lastSelectedItemId ?: ""
            }
            hoveredItem?.lastSelectedItemId =lastItem
            itemOffsetX = -0.8f
            M.audio.playSfx(SfxType.Cancel)
        }
    }

    fun isCategoryHidden(id:String) : Boolean = hiddenCategories.find { it == id } != null

    private fun readBuiltInNodeSet(prefKey: String): MutableSet<String> {
        return M.pref.get(prefKey, emptySet<String>())?.toMutableSet() ?: mutableSetOf()
    }

    private fun writeBuiltInNodeSet(prefKey: String, nodeIds: Set<String>) {
        M.pref.set(prefKey, nodeIds.filter { it.isNotBlank() }.toSet()).push()
    }

    fun isBuiltInNodeHidden(nodeId: String): Boolean {
        return readBuiltInNodeSet(PrefEntry.HIDDEN_BUILTIN_NODES).contains(nodeId)
    }

    fun isBuiltInNodeDefaultHiddenUntilConfigured(nodeId: String): Boolean {
        return nodeId in DEFAULT_HIDDEN_UNCONFIGURED_BUILTIN_NODE_IDS
    }

    fun isBuiltInNodeEffectivelyHidden(nodeId: String): Boolean {
        if (isBuiltInNodeHidden(nodeId)) return true
        if (!isBuiltInNodeDefaultHiddenUntilConfigured(nodeId)) return false
        return getBuiltInNodeLaunchOverride(nodeId) == null
    }

    fun isBuiltInNodeDeleted(nodeId: String): Boolean {
        return readBuiltInNodeSet(PrefEntry.DELETED_BUILTIN_NODES).contains(nodeId)
    }

    fun setBuiltInNodeHidden(nodeId: String, hidden: Boolean) {
        val nodes = readBuiltInNodeSet(PrefEntry.HIDDEN_BUILTIN_NODES)
        if (hidden) {
            nodes.add(nodeId)
        } else {
            nodes.remove(nodeId)
        }
        writeBuiltInNodeSet(PrefEntry.HIDDEN_BUILTIN_NODES, nodes)
        if (hidden && selectedItemId == nodeId) {
            selectedItemId = categories.firstNotNullOfOrNull { category ->
                if (category.content.any { it.id == nodeId }) {
                    category.content.visibleItems.firstOrNull()?.id
                } else {
                    null
                }
            } ?: ""
        }
    }

    fun deleteBuiltInDefaultNode(categoryId: String, nodeId: String) {
        val deletedNodes = readBuiltInNodeSet(PrefEntry.DELETED_BUILTIN_NODES)
        deletedNodes.add(nodeId)
        writeBuiltInNodeSet(PrefEntry.DELETED_BUILTIN_NODES, deletedNodes)
        setBuiltInNodeHidden(nodeId, false)

        removeFromCategory(categoryId, nodeId)
        if (selectedItemId == nodeId) {
            selectedItemId = categories.find { it.id == categoryId }?.content?.visibleItems?.firstOrNull()?.id ?: ""
        }
    }

    private fun XmbItemCategory.addBuiltInDefaultNode(
        nodeId: String,
        nameId: Int,
        iconId: Int,
        launchAction: (XmbNodeItem) -> Unit
    ) {
        if (isBuiltInNodeDeleted(nodeId)) return
        content.add(XmbNodeItem(
            this@Vsh,
            nodeId,
            nameId,
            iconId,
            builtInDefault = true,
            userHideable = true,
            userDeletable = true,
            builtInCategoryId = id,
            launchAction = launchAction
        ))
    }

    private fun formatMemoryStickFreeSpace(bytes: Long): String {
        val megabytes = bytes / (1024.0 * 1024.0)
        if (megabytes >= 1024.0) {
            val gigabytes = megabytes / 1024.0
            val formatted = if (gigabytes >= 10.0) {
                "%.0f GB".format(gigabytes)
            } else {
                "%.1f GB".format(gigabytes)
            }
            return "${getString(R.string.settings_systeminfo_storage_usage)}: $formatted"
        }

        val formatted = when {
            megabytes >= 100.0 -> "%.0f MB".format(megabytes)
            megabytes >= 1.0 -> "%.1f MB".format(megabytes)
            else -> "<1 MB"
        }
        return "${getString(R.string.settings_systeminfo_storage_usage)}: $formatted"
    }

    fun getMemoryStickFreeSpaceText(): String {
        val now = SystemClock.elapsedRealtime()
        if (now - memoryStickFreeSpaceLastReadAt < 2500L && memoryStickFreeSpaceCache.isNotBlank()) {
            return memoryStickFreeSpaceCache
        }

        val targetDir = getExternalFilesDirs(null).firstOrNull() ?: filesDir
        memoryStickFreeSpaceCache = try {
            formatMemoryStickFreeSpace(StatFs(targetDir.absolutePath).availableBytes)
        } catch (_: Exception) {
            ""
        }
        memoryStickFreeSpaceLastReadAt = now
        return memoryStickFreeSpaceCache
    }

    fun usesPspStorageRootLabel(): Boolean {
        return M.pref.get(PrefEntry.STORAGE_ROOT_LABEL_STYLE, 0) == 1
    }

    fun getStorageRootLabel(): String {
        return getString(
            if (usesPspStorageRootLabel()) {
                R.string.common_memory_stick
            } else {
                R.string.common_internal_storage
            }
        )
    }

    private fun registerInternalCategory(){
        try {
            val storageRootLabel = getStorageRootLabel()
            categories.add(XmbItemCategory(this, ITEM_CATEGORY_SETTINGS, R.string.category_settings, R.drawable.category_setting, defaultSortIndex = 1))

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_PHOTO, R.string.category_photo, R.drawable.category_photo, true, defaultSortIndex = 2).apply {
                content.add(XmbNodeItem(this@Vsh, "photo_camera", R.string.common_digital_camera, R.drawable.category_photo))
                content.add(XmbNodeItem(this@Vsh, "photo_ms", storageRootLabel, R.drawable.category_photo).apply {
                    setValueGetter { this@Vsh.getMemoryStickFreeSpaceText() }
                })
            })

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_MUSIC, R.string.category_music, R.drawable.category_music, true, defaultSortIndex = 3).apply {
                content.add(XmbNodeItem(this@Vsh, "music_ms", storageRootLabel, R.drawable.category_music).apply {
                    setValueGetter { this@Vsh.getMemoryStickFreeSpaceText() }
                })
                lastSelectedItemId = "music_ms"
            })

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_VIDEO, R.string.category_videos, R.drawable.category_video, true, defaultSortIndex = 4).apply {
                content.add(XmbNodeItem(this@Vsh, "video_ms", storageRootLabel, R.drawable.category_video).apply {
                    setValueGetter { this@Vsh.getMemoryStickFreeSpaceText() }
                })
            })

            val hasRecentGames = M.pref.get(PrefEntry.RECENT_GAME_IDS, "")
                .lineSequence()
                .any { it.isNotBlank() }

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_GAME, R.string.category_games, R.drawable.category_games, true, defaultSortIndex = 5).apply {
                content.add(XmbNodeItem(this@Vsh, "game_saved_data", R.string.common_recent_games, R.drawable.icon_clock))
                content.add(XmbNodeItem(this@Vsh, "game_ms", storageRootLabel, R.drawable.ic_folder).apply {
                    setValueGetter { this@Vsh.getMemoryStickFreeSpaceText() }
                })
                content.add(XmbNodeItem(this@Vsh, "game_android", R.string.category_game_android, R.drawable.category_apps))
                lastSelectedItemId = if (hasRecentGames) "game_saved_data" else "game_ms"
            })

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_NETWORK, R.string.category_network, R.drawable.icon_network, true, defaultSortIndex = 6).apply {
                content.add(XmbNodeItem(this@Vsh, "network_browser", R.string.common_browser, R.drawable.icon_network))
                content.add(XmbNodeItem(
                    this@Vsh,
                    NODE_NETWORK_SEARCH,
                    R.string.common_search,
                    R.drawable.ic_search,
                    launchAction = { this@Vsh.openInternetSearch() }
                ))
                addBuiltInDefaultNode(NODE_NETWORK_ONLINE_MANUALS, R.string.common_online_instruction_manuals, R.drawable.category_help) {
                    this@Vsh.openOnlineInstructionManuals()
                }
                addBuiltInDefaultNode(NODE_NETWORK_REMOTE_PLAY, R.string.common_remote_play, R.drawable.category_shortcut) {
                    this@Vsh.openNetworkPlaceholder(R.string.common_remote_play, R.string.network_remote_play_placeholder)
                }
                addBuiltInDefaultNode(NODE_NETWORK_INTERNET_RADIO, R.string.common_internet_radio, R.drawable.ic_component_audio) {
                    this@Vsh.openNetworkPlaceholder(R.string.common_internet_radio, R.string.network_internet_radio_placeholder)
                }
                addBuiltInDefaultNode(NODE_NETWORK_RSS_CHANNEL, R.string.common_rss_channel, R.drawable.category_notifications) {
                    this@Vsh.openNetworkPlaceholder(R.string.common_rss_channel, R.string.network_rss_channel_placeholder)
                }
            })

            val psnCategory = XmbItemCategory(this, ITEM_CATEGORY_PSN, R.string.category_psn, R.drawable.icon_users, true, defaultSortIndex = 7).apply {
                addBuiltInDefaultNode(NODE_PSN_ACCOUNT_MANAGEMENT, R.string.common_account_management, R.drawable.icon_users) {
                    this@Vsh.openAccountManagementPlaceholder()
                }
                addBuiltInDefaultNode(NODE_PSN_STORE, R.string.common_playstation_store, R.drawable.icon_storage) {
                    this@Vsh.openPlayStationStore()
                }
            }
            if (psnCategory.content.visibleItems.isNotEmpty()) {
                categories.add(psnCategory)
            }

            categories.add(XmbItemCategory(this, ITEM_CATEGORY_APPS, R.string.category_apps, R.drawable.category_apps, true, defaultSortIndex = 8).apply {
                content.add(XmbNodeItem(
                    this@Vsh,
                    NODE_APPS_MEMORY_STICK,
                    storageRootLabel,
                    R.drawable.category_apps,
                    builtInDefault = true,
                    builtInCategoryId = ITEM_CATEGORY_APPS
                ).apply {
                    setValueGetter { this@Vsh.getMemoryStickFreeSpaceText() }
                })
                lastSelectedItemId = NODE_APPS_MEMORY_STICK
            })

            loadCustomNodes()
            loadCustomLaunchItems()
            categories.sortBy { it.sortIndex }
        }catch(e:Exception){

        }
    }

    private fun loadCustomNodes() {
        val data = M.pref.get(PrefEntry.CUSTOM_NODES, "")
        if(data.isNotEmpty()){
            data.split("|").forEach { nodeData ->
                val parts = nodeData.split(":")
                if(parts.size >= 3){
                    val catId = parts[0]
                    val nodeId = parts[1]
                    val nodeName = parts[2]
                    val category = categories.find { it.id == catId }
                    if(category != null && category.content.none { it.id == nodeId }){
                        category.content.add(XmbNodeItem(this, nodeId, nodeName, R.drawable.category_apps))
                    }
                }
            }
        }
    }

    fun addCustomNode(categoryId: String, nodeId: String, name: String) {
        val category = categories.find { it.id == categoryId } ?: return
        if(category.content.none { it.id == nodeId }){
            category.content.add(XmbNodeItem(this, nodeId, name, R.drawable.category_apps))
            val current = M.pref.get(PrefEntry.CUSTOM_NODES, "")
            val newData = if(current.isEmpty()) "$categoryId:$nodeId:$name" else "$current|$categoryId:$nodeId:$name"
            M.pref.set(PrefEntry.CUSTOM_NODES, newData).push()
        }
    }

    fun removeCustomNode(categoryId: String, nodeId: String) {
        val category = categories.find { it.id == categoryId } ?: return
        category.content.removeAll { it.id == nodeId }
        val current = M.pref.get(PrefEntry.CUSTOM_NODES, "")
        val nodes = current.split("|").toMutableList()
        nodes.removeAll { it.startsWith("$categoryId:$nodeId:") }
        M.pref.set(PrefEntry.CUSTOM_NODES, nodes.joinToString("|")).push()
        // Re-route apps that were in this node
            allAppEntries.forEach {
            if(it.appCategory == categoryId && it.appNodeOverride == nodeId){
                it.appNodeOverride = ""
            }
        }
        M.apps.reloadAppList()
    }

    fun restart() {
        val pm = vsh.packageManager
        val sndi = pm.getLaunchIntentForPackage(vsh.packageName)
        val cmpn = sndi?.component
        if(cmpn!= null){
            val rsti = Intent.makeRestartActivityTask(cmpn)
            vsh.startActivity(rsti)
        }
        exitProcess(0)
    }

    override fun onTerminate() {
        M.onDestroy()
        VulkanisirSubmodule.close()
        NativeGL.destroy()
        super.onTerminate()
    }

    fun showAppInfo(app: XmbAppItem) {
        val i = Intent()
        i.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        i.data = Uri.fromParts("package", app.resInfo.activityInfo.applicationInfo.packageName, null)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
    }

    fun hasPermission(vararg str : String) : Boolean {
        if (sdkAtLeast(Build.VERSION_CODES.M)) {
            for(s in str){
                if(checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED)
                    return false
            }
        }
        return true
    }

    fun runtimeTriageCheck(id:String) : Boolean {
        if(!runtimeTriageList.contains(id))
        {
            runtimeTriageList.add(id)
            return true
        }
        return false
    }

    fun openFileOnExternalApp(apk: File, chooser: Boolean = false, chooserTitle : String = "") {
        if(haveXmbView){
            val xmb =safeXmbView.context.xmb
            xmb.runOnUiThread {
                val authority = BuildConfig.APPLICATION_ID + ".fileprovider"
                val u = FileProvider.getUriForFile(xmb, authority, apk)
                var i = Intent(Intent.ACTION_VIEW)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                i.setDataAndType(u, contentResolver.getType(u))
                i.putExtra(Intent.EXTRA_STREAM, u)
                i.data = u

                if(chooser){
                    i = Intent.createChooser(i, chooserTitle)
                }

                xmb.startActivity(i)
            }
        }

    }

    fun openInternetBrowser() {
        val browserUri = Uri.parse("https://www.google.com/")
        val viewIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolved = if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            packageManager.resolveActivity(viewIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(viewIntent, 0)
        }

        if (resolved?.activityInfo == null) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                getString(R.string.error_no_browser_description)
            )
            return
        }

        val launchMode = M.pref.get(PrefEntry.INTERNET_BROWSER_LAUNCH_MODE, 0)
        val launchIntent = if (launchMode == 1) {
            Intent.createChooser(viewIntent, getString(R.string.settings_system_browser_launch_mode_ask))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        } else {
            packageManager.getLaunchIntentForPackage(resolved.activityInfo.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                ?: viewIntent
        }

        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(R.string.error_no_browser_description)
            )
        }
    }

    fun openInternetSearch() {
        openUrlOrNotify("https://www.google.com/search?q=", R.string.error_no_browser_description)
    }

    private fun encodeLaunchField(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decodeLaunchField(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
    }

    private fun decodeLegacyCustomLaunchItemRecord(serialized: String): CustomLaunchItemRecord? {
        return try {
            val parts = serialized.split(":")
            if (parts.size < 5) return null
            CustomLaunchItemRecord(
                decodeLaunchField(parts[1]),
                decodeLaunchField(parts[3]),
                decodeLaunchField(parts[2]),
                decodeLaunchField(parts[0]),
                target = decodeLaunchField(parts[4])
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun customLaunchRecordToJson(record: CustomLaunchItemRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.itemId)
            put("type", record.type)
            put("title", record.title)
            put("categoryId", record.categoryId)
            put("parentNodeId", record.parentNodeId)
            put("target", record.target)
            put("fileUri", record.fileUri)
            put("platformId", record.platformId)
            put("emulatorAdapterId", record.emulatorAdapterId)
            put("packageName", record.packageName)
            put("componentName", record.componentName)
            put("gamebootEnabled", record.gamebootEnabled)
            put("hidden", record.hidden)
            put("iconRef", record.iconRef)
        }
    }

    private fun customLaunchRecordFromJson(json: JSONObject): CustomLaunchItemRecord? {
        val itemId = json.optString("id").ifBlank { return null }
        val type = json.optString("type").ifBlank { return null }
        val title = json.optString("title").ifBlank { return null }
        val categoryId = json.optString("categoryId").ifBlank { ITEM_CATEGORY_GAME }
        return CustomLaunchItemRecord(
            itemId = itemId,
            type = type,
            title = title,
            categoryId = categoryId,
            parentNodeId = json.optString("parentNodeId"),
            target = json.optString("target"),
            fileUri = json.optString("fileUri"),
            platformId = json.optString("platformId", "unknown"),
            emulatorAdapterId = json.optString("emulatorAdapterId"),
            packageName = json.optString("packageName"),
            componentName = json.optString("componentName"),
            gamebootEnabled = json.optBoolean("gamebootEnabled", true),
            hidden = json.optBoolean("hidden", false),
            iconRef = json.optString("iconRef")
        )
    }

    private fun encodeBuiltInNodeLaunchOverride(record: BuiltInNodeLaunchOverride): String {
        return listOf(
            record.nodeId,
            record.type,
            record.target,
            record.label
        ).joinToString(":") { encodeLaunchField(it) }
    }

    private fun decodeBuiltInNodeLaunchOverride(serialized: String): BuiltInNodeLaunchOverride? {
        return try {
            val parts = serialized.split(":")
            if (parts.size < 4) return null
            BuiltInNodeLaunchOverride(
                decodeLaunchField(parts[0]),
                decodeLaunchField(parts[1]),
                decodeLaunchField(parts[2]),
                decodeLaunchField(parts[3])
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getCustomLaunchItemRecords(): List<CustomLaunchItemRecord> {
        val raw = M.pref.get(PrefEntry.CUSTOM_LAUNCH_ITEMS, "")
        if (raw.isBlank()) return emptyList()
        if (raw.trimStart().startsWith("[")) {
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { index ->
                    customLaunchRecordFromJson(arr.getJSONObject(index))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        val migrated = raw.split("|").mapNotNull { entry ->
            if (entry.isBlank()) null else decodeLegacyCustomLaunchItemRecord(entry)
        }
        if (migrated.isNotEmpty()) {
            writeCustomLaunchItemRecords(migrated)
        }
        return migrated
    }

    private fun writeCustomLaunchItemRecords(records: List<CustomLaunchItemRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(customLaunchRecordToJson(it)) }
        M.pref.set(
            PrefEntry.CUSTOM_LAUNCH_ITEMS,
            arr.toString()
        ).push()
    }

    fun getBuiltInNodeLaunchOverrides(): List<BuiltInNodeLaunchOverride> {
        return M.pref.get(PrefEntry.BUILTIN_NODE_LAUNCH_OVERRIDES, "")
            .split("|")
            .mapNotNull { entry ->
                if (entry.isBlank()) null else decodeBuiltInNodeLaunchOverride(entry)
            }
    }

    fun getBuiltInNodeLaunchOverride(nodeId: String): BuiltInNodeLaunchOverride? {
        return getBuiltInNodeLaunchOverrides().firstOrNull { it.nodeId == nodeId }
    }

    private fun writeBuiltInNodeLaunchOverrides(records: List<BuiltInNodeLaunchOverride>) {
        M.pref.set(
            PrefEntry.BUILTIN_NODE_LAUNCH_OVERRIDES,
            records.joinToString("|") { encodeBuiltInNodeLaunchOverride(it) }
        ).push()
    }

    fun isBuiltInLaunchTargetConfigurable(nodeId: String): Boolean {
        return nodeId in CONFIGURABLE_BUILTIN_LAUNCH_NODE_IDS
    }

    fun setBuiltInNodeAppLaunchOverride(nodeId: String, packageName: String, label: String) {
        if (!isBuiltInLaunchTargetConfigurable(nodeId)) return
        val record = BuiltInNodeLaunchOverride(nodeId, "app", packageName, label)
        writeBuiltInNodeLaunchOverrides(
            getBuiltInNodeLaunchOverrides().filterNot { it.nodeId == nodeId } + record
        )
    }

    fun setBuiltInNodeUrlLaunchOverride(nodeId: String, target: String, label: String = target) {
        if (!isBuiltInLaunchTargetConfigurable(nodeId)) return
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return
        val record = BuiltInNodeLaunchOverride(nodeId, "url", cleanTarget, label.trim().ifBlank { cleanTarget })
        writeBuiltInNodeLaunchOverrides(
            getBuiltInNodeLaunchOverrides().filterNot { it.nodeId == nodeId } + record
        )
    }

    fun resetBuiltInNodeLaunchOverride(nodeId: String) {
        writeBuiltInNodeLaunchOverrides(
            getBuiltInNodeLaunchOverrides().filterNot { it.nodeId == nodeId }
        )
    }

    private fun makeCustomLaunchItemId(name: String): String {
        val slug = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "item" }
        return "custom_launch_${slug}_${System.currentTimeMillis()}"
    }

    private fun updateCustomLaunchItemRecord(updatedRecord: CustomLaunchItemRecord) {
        writeCustomLaunchItemRecords(
            getCustomLaunchItemRecords().map {
                if (it.itemId == updatedRecord.itemId) updatedRecord else it
            }
        )
    }

    fun toggleCustomLaunchItemGameboot(itemId: String) {
        val liveRecord = getCustomLaunchItemRecords().firstOrNull { it.itemId == itemId } ?: return
        updateCustomLaunchItemRecord(liveRecord.copy(gamebootEnabled = !liveRecord.gamebootEnabled))
    }

    private fun targetContentForCustomLaunchItem(record: CustomLaunchItemRecord): ArrayList<XmbItem>? {
        val category = categories.find { it.id == record.categoryId } ?: return null
        if (record.parentNodeId.isBlank()) return category.content
        val parent = category.content.filterIsInstance<XmbNodeItem>().firstOrNull { it.id == record.parentNodeId }
            ?: return category.content
        return parent.content
    }

    private fun createCustomLaunchItem(record: CustomLaunchItemRecord): XmbItem {
        val menu = arrayListOf<XmbMenuItem>()

        lateinit var customItem: XmbCustomLaunchItem

        if (record.type == "file") {
            menu.add(XmbMenuItem.XmbMenuItemLambda({
                getString(R.string.settings_system_set_emulator_app)
            }, { false }, 1) {
                val knownAdapters = installedCompatibleEmulatorAdapters(record)
                val knownPackages = knownAdapters.map { it.packageName }.toSet()
                val apps = allAppEntries.toList().sortedBy { it.displayName.lowercase() }
                val appMenu = arrayListOf<XmbMenuItem>()
                knownAdapters.forEachIndexed { index, adapter ->
                    appMenu.add(XmbMenuItem.XmbMenuItemLambda({
                        adapterMenuLabel(adapter)
                    }, { false }, index) {
                        updateCustomLaunchItemRecord(record.copy(
                            platformId = normalizedPlatformForAdapter(record, adapter),
                            packageName = adapter.packageName,
                            emulatorAdapterId = adapter.id,
                            componentName = adapter.componentName
                        ))
                        xmbView?.showSideMenu(false)
                    })
                }
                apps.forEachIndexed { index, app ->
                    val packageName = app.resInfo.activityInfo.packageName
                    if (knownPackages.contains(packageName)) return@forEachIndexed
                    appMenu.add(XmbMenuItem.XmbMenuItemLambda({ app.displayName }, { false }, knownAdapters.size + index) {
                        val adapterId = inferEmulatorAdapterId(record.platformId, packageName)
                        val adapter = knownEmulatorAdapters().firstOrNull { it.id == adapterId }
                        updateCustomLaunchItemRecord(record.copy(
                            platformId = adapter?.let { normalizedPlatformForAdapter(record, it) } ?: record.platformId,
                            packageName = packageName,
                            emulatorAdapterId = adapterId,
                            componentName = "",
                        ))
                        xmbView?.showSideMenu(false)
                    })
                }
                xmbView?.showSideMenu(appMenu)
            })

            if (record.packageName.isNotBlank()) {
                menu.add(XmbMenuItem.XmbMenuItemLambda({
                    getString(R.string.settings_system_reset_emulator_app)
                }, { false }, 2) {
                    updateCustomLaunchItemRecord(record.copy(
                        packageName = "",
                        emulatorAdapterId = "",
                        componentName = ""
                    ))
                    xmbView?.showSideMenu(false)
                })
            }

            menu.add(XmbMenuItem.XmbMenuItemLambda({
                val liveRecord = getCustomLaunchItemRecords().firstOrNull { it.itemId == record.itemId } ?: record
                getString(
                    liveRecord.gamebootEnabled.select(
                        R.string.app_disable_gameboot,
                        R.string.app_enable_gameboot
                    )
                )
            }, { false }, 3) {
                toggleCustomLaunchItemGameboot(record.itemId)
                xmbView?.showSideMenu(false)
            })
        }

        menu.add(XmbMenuItem.XmbMenuItemLambda({
            getString(R.string.view_app_info)
        }, { false }, 10) {
            xmbView?.showSideMenu(false)
            xmbView?.showDialog(id.psw.vshlauncher.views.dialogviews.CustomLaunchItemInfoDialogView(safeXmbView, customItem))
        })

        menu.add(XmbMenuItem.XmbMenuItemLambda({ getString(R.string.common_delete) }, { false }, 99) {
            xmbView?.showSideMenu(false)
            xmbView?.showDialog(id.psw.vshlauncher.views.dialogviews.ConfirmDialogView(
                safeXmbView,
                record.title,
                R.drawable.category_setting,
                getString(R.string.settings_system_launch_item_delete_confirm)
            ) { confirm ->
                if (confirm) {
                    removeCustomLaunchItem(record.itemId)
                }
            })
        })

        customItem = XmbCustomLaunchItem(
            this,
            record,
            if (record.type == "file") R.drawable.ic_folder else R.drawable.icon_network,
            menu
        ) {
                val liveRecord = getCustomLaunchItemRecords().firstOrNull { it.itemId == record.itemId } ?: record
                when (liveRecord.type) {
                    "file" -> openCustomFileLaunchTarget(liveRecord)
                    else -> openCustomLaunchTarget(liveRecord.target)
                }
        }
        return customItem
    }

    private fun startCustomLaunchAssetPicker(
        item: XmbCustomLaunchItem,
        requestCode: Int,
        mimeType: String,
        extraMimeTypes: Array<String>? = null
    ) {
        itemEditing = item
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            if (extraMimeTypes != null) {
                putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
            }
        }
        xmbView?.context?.xmb?.startActivityForResult(intent, requestCode)
        xmbView?.showSideMenu(false)
    }

    private fun loadCustomLaunchItems() {
        getCustomLaunchItemRecords().forEach { record ->
            if (record.hidden) return@forEach
            val content = targetContentForCustomLaunchItem(record) ?: return@forEach
            if (content.any { it.id == record.itemId }) return@forEach
            if (record.type == "url" || record.type == "file") {
                content.add(createCustomLaunchItem(record))
            }
        }
    }

    fun addCustomUrlLaunchItem(categoryId: String, name: String, target: String) {
        val category = categories.find { it.id == categoryId } ?: return
        val cleanName = name.trim()
        val cleanTarget = target.trim()
        if (cleanName.isBlank() || cleanTarget.isBlank()) return

        val record = CustomLaunchItemRecord(
            itemId = makeCustomLaunchItemId(cleanName),
            type = "url",
            title = cleanName,
            categoryId = categoryId,
            target = cleanTarget
        )
        category.content.add(createCustomLaunchItem(record))
        writeCustomLaunchItemRecords(getCustomLaunchItemRecords() + record)
    }

    fun addCustomFileLaunchItem(name: String, target: String, platformId: String = "unknown") {
        val cleanName = name.trim()
        val cleanTarget = target.trim()
        if (cleanName.isBlank() || cleanTarget.isBlank()) return

        val record = CustomLaunchItemRecord(
            itemId = makeCustomLaunchItemId(cleanName),
            type = "file",
            title = cleanName,
            categoryId = ITEM_CATEGORY_GAME,
            parentNodeId = "game_ms",
            fileUri = cleanTarget,
            platformId = platformId
        )
        targetContentForCustomLaunchItem(record)?.add(createCustomLaunchItem(record))
        writeCustomLaunchItemRecords(getCustomLaunchItemRecords() + record)
    }

    fun removeCustomLaunchItem(itemId: String) {
        categories.forEach { category ->
            category.content.removeAll { it.id == itemId }
            category.content.filterIsInstance<XmbNodeItem>().forEach { node ->
                node.content.removeAll { it.id == itemId }
            }
        }
        writeCustomLaunchItemRecords(
            getCustomLaunchItemRecords().filterNot { it.itemId == itemId }
        )
        if (selectedItemId == itemId) {
            selectedItemId = items?.visibleItems?.firstOrNull()?.id ?: ""
        }
    }

    fun openAccountManagementPlaceholder() {
        postNotification(
            R.drawable.icon_account,
            getString(R.string.common_account_management),
            getString(R.string.psn_account_management_placeholder)
        )
    }

    fun openNetworkPlaceholder(titleId: Int, messageId: Int) {
        postNotification(
            R.drawable.icon_network,
            getString(titleId),
            getString(messageId)
        )
    }

    fun openUrlOrNotify(url: String, errorMessageId: Int) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolved = if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0)
        }

        if (resolved?.activityInfo == null) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                getString(errorMessageId)
            )
            return
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(errorMessageId)
            )
        }
    }

    fun openCustomLaunchTarget(target: String) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                getString(R.string.error_custom_launch_item_description)
            )
            return
        }

        val uri = Uri.parse(trimmed)
        val normalized = if (uri.scheme.isNullOrBlank()) {
            "https://$trimmed"
        } else {
            trimmed
        }
        openUrlOrNotify(normalized, R.string.error_custom_launch_item_description)
    }

    fun startCustomFileLaunchItemPicker(categoryId: String, name: String) {
        val cleanName = name.trim()
        if (categoryId.isBlank() || cleanName.isBlank()) return
        pendingCustomLaunchFileCategoryId = categoryId
        pendingCustomLaunchFileName = cleanName

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        try {
            xmbView?.context?.xmb?.startActivityForResult(
                Intent.createChooser(intent, getString(R.string.settings_system_add_file_launch_item)),
                ACT_REQ_PICK_CUSTOM_LAUNCH_FILE
            )
        } catch (e: Exception) {
            pendingCustomLaunchFileCategoryId = ""
            pendingCustomLaunchFileName = ""
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(R.string.error_custom_launch_item_description)
            )
        }
    }

    fun finishCustomFileLaunchItemPick(data: Intent?) {
        val uri = data?.data
        val name = pendingCustomLaunchFileName
        pendingCustomLaunchFileCategoryId = ""
        pendingCustomLaunchFileName = ""

        if (uri == null || name.isBlank()) return

        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some providers do not grant persistable permissions; keep the URI anyway and let launch failure be explicit.
        }

        val displayName = getOpenableDisplayName(uri)
        addCustomFileLaunchItem(name.ifBlank { displayName }, uri.toString(), inferPlatformId(displayName))
        postNotification(
            R.drawable.ic_folder,
            getString(R.string.settings_system_add_file_launch_item),
            displayName.ifBlank { name }
        )
    }

    private fun getOpenableDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment.orEmpty()
        } catch (_: Exception) {
            uri.lastPathSegment.orEmpty()
        }
    }

    private fun inferPlatformId(displayName: String): String {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "iso", "chd", "gz" -> "ps2"
            "cso" -> "psp"
            "pbp" -> "ps1"
            "nsp", "xci", "nca", "nro" -> "switch"
            "nes", "sfc", "smc", "gba", "gb", "gbc", "n64", "z64", "v64", "nds", "3ds" -> ext
            else -> "unknown"
        }
    }

    private fun inferEmulatorAdapterId(platformId: String, packageName: String): String {
        return knownEmulatorAdapters()
            .firstOrNull { it.packageName == packageName && adapterMatchesPlatform(it, platformId) }
            ?.id
            ?: "android_package"
    }

    private fun knownEmulatorAdapters(): List<EmulatorAdapter> {
        return listOf(
            EmulatorAdapter(
                id = "ps2_aethersx2",
                platformId = "ps2",
                label = "AetherSX2 / NetherSX2",
                packageName = "xyz.aethersx2.android",
                componentName = "xyz.aethersx2.android.EmulationActivity",
                launchStyle = "ps2_boot_path"
            ),
            EmulatorAdapter(
                id = "psp_ppsspp",
                platformId = "psp",
                label = "PPSSPP",
                packageName = "org.ppsspp.ppsspp",
                launchStyle = "android_view_uri"
            ),
            EmulatorAdapter(
                id = "psp_ppsspp_gold",
                platformId = "psp",
                label = "PPSSPP Gold",
                packageName = "org.ppsspp.ppssppgold",
                launchStyle = "android_view_uri"
            ),
            EmulatorAdapter(
                id = "switch_eden",
                platformId = "switch",
                label = "Eden",
                packageName = "dev.eden.eden_emulator",
                launchStyle = "switch_tech_discovered"
            ),
            EmulatorAdapter(
                id = "switch_yuzu",
                platformId = "switch",
                label = "Yuzu",
                packageName = "org.yuzu.yuzu_emu",
                componentName = "org.yuzu.yuzu_emu.activities.EmulationActivity",
                launchStyle = "switch_tech_discovered"
            ),
            EmulatorAdapter(
                id = "switch_yuzu_ea",
                platformId = "switch",
                label = "Yuzu Early Access",
                packageName = "org.yuzu.yuzu_emu.ea",
                componentName = "org.yuzu.yuzu_emu.activities.EmulationActivity",
                launchStyle = "switch_tech_discovered"
            ),
            EmulatorAdapter(
                id = "switch_sudachi",
                platformId = "switch",
                label = "Sudachi",
                packageName = "org.sudachi.sudachi_emu",
                launchStyle = "switch_tech_discovered"
            ),
            EmulatorAdapter(
                id = "switch_citron",
                platformId = "switch",
                label = "Citron",
                packageName = "org.citron.citron_emu",
                launchStyle = "switch_tech_discovered"
            ),
            EmulatorAdapter(
                id = "switch_uzuy",
                platformId = "switch",
                label = "Uzuy",
                packageName = "org.uzuy.uzuy_emu",
                launchStyle = "switch_tech_discovered"
            )
        )
    }

    private fun adapterMatchesPlatform(adapter: EmulatorAdapter, platformId: String): Boolean {
        return platformId == "unknown" || adapter.platformId == platformId
    }

    private fun customLaunchFileExtension(record: CustomLaunchItemRecord): String {
        val uri = try {
            Uri.parse(record.fileUri)
        } catch (_: Exception) {
            null
        }
        val candidates = arrayListOf(record.title, uri?.lastPathSegment ?: "")
        if (uri != null && uri.scheme == "content") {
            try {
                candidates.add(DocumentsContract.getDocumentId(uri))
            } catch (_: Exception) {
                // Not every content URI is a DocumentsProvider URI.
            }
        }

        return candidates.firstNotNullOfOrNull { candidate ->
            candidate
                .substringAfterLast('.', "")
                .substringBefore('?')
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        } ?: ""
    }

    private fun adapterMatchesRecord(adapter: EmulatorAdapter, record: CustomLaunchItemRecord): Boolean {
        if (adapterMatchesPlatform(adapter, record.platformId)) return true
        val extension = customLaunchFileExtension(record)
        return extension == "iso" && adapter.platformId in setOf("ps2", "psp")
    }

    private fun normalizedPlatformForAdapter(
        record: CustomLaunchItemRecord,
        adapter: EmulatorAdapter
    ): String {
        val extension = customLaunchFileExtension(record)
        if (extension == "iso" && adapter.platformId in setOf("ps2", "psp")) {
            return adapter.platformId
        }
        return if (record.platformId == "unknown") adapter.platformId else record.platformId
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun installedCompatibleEmulatorAdapters(platformId: String): List<EmulatorAdapter> {
        return knownEmulatorAdapters()
            .filter { adapterMatchesPlatform(it, platformId) }
            .filter { isPackageInstalled(it.packageName) }
    }

    private fun installedCompatibleEmulatorAdapters(record: CustomLaunchItemRecord): List<EmulatorAdapter> {
        return knownEmulatorAdapters()
            .filter { adapterMatchesRecord(it, record) }
            .filter { isPackageInstalled(it.packageName) }
    }

    private fun resolveEmulatorAdapter(record: CustomLaunchItemRecord): EmulatorAdapter? {
        val knownAdapters = knownEmulatorAdapters()
        val explicitAdapter = knownAdapters.firstOrNull {
            it.id == record.emulatorAdapterId &&
                adapterMatchesRecord(it, record) &&
                (record.packageName.isBlank() || it.packageName == record.packageName) &&
                isPackageInstalled(it.packageName)
        }
        if (explicitAdapter != null) return explicitAdapter

        val packageAdapter = knownAdapters.firstOrNull {
            it.packageName == record.packageName &&
                adapterMatchesRecord(it, record) &&
                isPackageInstalled(it.packageName)
        }
        if (packageAdapter != null) return packageAdapter

        if (record.packageName.isBlank() && record.emulatorAdapterId.isBlank()) {
            val installed = installedCompatibleEmulatorAdapters(record)
            if (installed.size == 1) return installed.first()
        }
        return null
    }

    private fun adapterMenuLabel(adapter: EmulatorAdapter): String {
        val appLabel = try {
            val appInfo = if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                packageManager.getApplicationInfo(adapter.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(adapter.packageName, 0)
            }
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            adapter.label
        }
        return if (appLabel.equals(adapter.label, ignoreCase = true)) appLabel else "${adapter.label} ($appLabel)"
    }

    private fun tryLaunchEmulatorAdapter(record: CustomLaunchItemRecord, uri: Uri): Boolean {
        val adapter = resolveEmulatorAdapter(record) ?: return false
        val adapterRecord = record.copy(
            packageName = adapter.packageName,
            emulatorAdapterId = adapter.id,
            componentName = adapter.componentName
        )
        return when (adapter.launchStyle) {
            "ps2_boot_path" -> launchAetherSx2(adapterRecord, uri)
            "android_view_uri" -> launchAndroidViewUri(adapterRecord, uri, adapter)
            "switch_tech_discovered" -> launchSwitchTechDiscovered(adapterRecord, uri, adapter)
            else -> false
        }
    }

    private fun launchAetherSx2(record: CustomLaunchItemRecord, uri: Uri): Boolean {
        val packageName = record.packageName
        if (packageName.isBlank()) return false
        val bootPath = resolveExternalStorageDocumentPath(uri) ?: uri.toString()
        val usesContentUri = bootPath == uri.toString()

        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, "$packageName.EmulationActivity")
            putExtra("bootPath", bootPath)
            if (usesContentUri) {
                data = uri
                clipData = ClipData.newUri(contentResolver, record.title, uri)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (usesContentUri) {
            try {
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some providers cannot be pre-granted; ClipData/data flags still cover normal SAF URIs.
            }
        }

        return try {
            Logger.d(TAG, "Launching PS2 adapter ${record.title} with $packageName bootPath=$bootPath")
            startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.w(TAG, "PS2 adapter launch failed for ${record.title}: ${e.message}")
            false
        }
    }

    private fun resolveExternalStorageDocumentPath(uri: Uri): String? {
        if (uri.scheme != "content" || uri.authority != "com.android.externalstorage.documents") return null
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) {
            return null
        }
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2 || parts[1].isBlank()) return null

        val basePath = if (parts[0].equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/${parts[0]}"
        }
        return File(basePath, parts[1]).absolutePath
    }

    private fun launchAndroidViewUri(
        record: CustomLaunchItemRecord,
        uri: Uri,
        adapter: EmulatorAdapter
    ): Boolean {
        val packageName = record.packageName
        if (packageName.isBlank()) return false
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(packageName)
            setDataAndType(uri, mimeType)
            clipData = ClipData.newUri(contentResolver, record.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // SAF permissions are already persisted when picked; this grant helps direct emulator launches.
        }

        return try {
            Logger.d(TAG, "Launching ${adapter.label} adapter ${record.title} with $packageName data=$uri")
            startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.w(TAG, "${adapter.label} adapter launch failed for ${record.title}: ${e.message}")
            launchAndroidViewUriExplicitFallback(record, uri, adapter, mimeType)
        }
    }

    private fun launchAndroidViewUriExplicitFallback(
        record: CustomLaunchItemRecord,
        uri: Uri,
        adapter: EmulatorAdapter,
        mimeType: String
    ): Boolean {
        val packageName = record.packageName
        val components = arrayOf(
            adapter.componentName,
            "$packageName.PpssppActivity",
            "$packageName.MainActivity"
        ).filter { it.isNotBlank() }.distinct()

        for (componentName in components) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(packageName, componentName)
                setDataAndType(uri, mimeType)
                clipData = ClipData.newUri(contentResolver, record.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                Logger.d(TAG, "Launching ${adapter.label} explicit fallback ${record.title} with $componentName data=$uri")
                startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try the next common Android emulator activity shape.
            }
        }
        return false
    }

    private fun launchSwitchTechDiscovered(
        record: CustomLaunchItemRecord,
        uri: Uri,
        adapter: EmulatorAdapter
    ): Boolean {
        val packageName = record.packageName
        if (packageName.isBlank()) return false

        val intent = Intent("android.nfc.action.TECH_DISCOVERED").apply {
            setPackage(packageName)
            data = uri
            clipData = ClipData.newUri(contentResolver, record.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // SAF permissions are already persisted when picked; this grant helps FileProvider/content URI launches.
        }

        return try {
            Logger.d(TAG, "Launching ${adapter.label} Switch adapter ${record.title} with $packageName data=$uri")
            startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.w(TAG, "${adapter.label} Switch adapter launch failed for ${record.title}: ${e.message}")
            launchSwitchTechDiscoveredExplicitFallback(record, uri, adapter)
        }
    }

    private fun launchSwitchTechDiscoveredExplicitFallback(
        record: CustomLaunchItemRecord,
        uri: Uri,
        adapter: EmulatorAdapter
    ): Boolean {
        val packageName = record.packageName
        val components = arrayOf(
            adapter.componentName,
            "org.yuzu.yuzu_emu.activities.EmulationActivity",
            "$packageName.activities.EmulationActivity",
            "$packageName.EmulationActivity"
        ).filter { it.isNotBlank() }.distinct()

        for (componentName in components) {
            val intent = Intent("android.nfc.action.TECH_DISCOVERED").apply {
                component = ComponentName(packageName, componentName)
                data = uri
                clipData = ClipData.newUri(contentResolver, record.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                Logger.d(TAG, "Launching ${adapter.label} explicit fallback ${record.title} with $componentName data=$uri")
                startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try the next known Android Switch-emulator activity shape.
            }
        }
        return false
    }

    fun openCustomFileLaunchTarget(record: CustomLaunchItemRecord) {
        val uri = try {
            Uri.parse(record.fileUri)
        } catch (_: Exception) {
            null
        }

        if (uri == null) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                getString(R.string.error_custom_launch_file_description)
            )
            return
        }

        if (record.platformId != "unknown" && record.packageName.isBlank() && record.emulatorAdapterId.isBlank()) {
            val compatibleAdapters = installedCompatibleEmulatorAdapters(record)
            if (compatibleAdapters.size > 1) {
                postNotification(
                    R.drawable.category_games,
                    getString(R.string.settings_system_set_emulator_app),
                    getString(R.string.settings_system_choose_emulator_required)
                )
                return
            }
        }

        launchWithGameboot(skipForItem = !record.gamebootEnabled) {
            openCustomFileLaunchTargetNow(record, uri)
        }
    }

    private fun openCustomFileLaunchTargetNow(record: CustomLaunchItemRecord, uri: Uri) {
        if (tryLaunchEmulatorAdapter(record, uri)) {
            return
        }

        val mimeType = contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (record.packageName.isNotBlank()) {
                setPackage(record.packageName)
            }
            if (record.componentName.isNotBlank()) {
                component = ComponentName.unflattenFromString(record.componentName)
            }
        }

        try {
            if (record.packageName.isBlank() && record.componentName.isBlank()) {
                startActivity(Intent.createChooser(intent, getString(R.string.settings_system_add_file_launch_item)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(R.string.error_custom_launch_file_description)
            )
        }
    }

    private fun launchPackageOrNotify(packageName: String, label: String) {
        val intents = arrayListOf<Intent?>()
        val phone = packageManager.getLaunchIntentForPackage(packageName)

        if (sdkAtLeast(21)) {
            val tv = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            if (_prioritizeTvIntent) {
                intents.addAllV(tv, phone)
            } else {
                intents.addAllV(phone, tv)
            }
        } else {
            intents.add(phone)
        }

        for (intent in intents) {
            if (intent == null) continue
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Try the next available launch intent.
            }
        }

        postNotification(
            R.drawable.ic_error,
            label.ifBlank { getString(R.string.error_common_header) },
            getString(R.string.error_custom_launch_item_description)
        )
    }

    private fun tryLaunchBuiltInNodeOverride(nodeId: String): Boolean {
        val override = getBuiltInNodeLaunchOverride(nodeId) ?: return false
        when (override.type) {
            "app" -> launchPackageOrNotify(override.target, override.label)
            "url" -> openCustomLaunchTarget(override.target)
            else -> return false
        }
        return true
    }

    fun openOnlineInstructionManuals() {
        val url = M.pref.get(
            PrefEntry.NETWORK_ONLINE_MANUALS_URL,
            "https://github.com/EmiyaSyahriel/CrossLauncher"
        )
        openUrlOrNotify(url, R.string.error_no_browser_description)
    }

    fun openPlayStationStore() {
        val packageLaunch = packageManager.getLaunchIntentForPackage("com.android.vending")
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        if (packageLaunch != null) {
            try {
                startActivity(packageLaunch)
                return
            } catch (_: Exception) {
                // Fall through to URI-based launch paths.
            }
        }

        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(marketIntent)
            return
        } catch (_: Exception) {
            // Fall through to web fallback.
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(webIntent)
        } catch (e: Exception) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(R.string.error_no_play_store_description)
            )
        }
    }

    fun openSystemCamera() {
        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolved = if (sdkAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            packageManager.resolveActivity(cameraIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(cameraIntent, 0)
        }

        if (resolved?.activityInfo == null) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                getString(R.string.error_no_camera_description)
            )
            return
        }

        try {
            startActivity(cameraIntent)
        } catch (e: Exception) {
            postNotification(
                R.drawable.ic_error,
                getString(R.string.error_common_header),
                e.localizedMessage ?: getString(R.string.error_no_camera_description)
            )
        }
    }


    fun hasPermissionGranted(permission : String) : Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
