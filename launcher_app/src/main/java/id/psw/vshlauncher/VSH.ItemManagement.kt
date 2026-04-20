package id.psw.vshlauncher

import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbAppItem
import id.psw.vshlauncher.types.items.XmbItemCategory
import id.psw.vshlauncher.types.items.XmbNodeItem

data class RoutingResult(
    val categoryId: String,
    val nodeId: String?,
    val isSuccess: Boolean
)

private const val RECENT_GAMES_NODE_ID = "game_saved_data"
private const val RECENT_GAMES_LIMIT = 12
private val MEDIA_SOURCE_NODE_IDS = setOf("photo_ms", "music_ms", "video_ms")

private fun isAppBlockedFromMediaSourceNode(categoryId: String, nodeId: String): Boolean {
    return categoryId in setOf(Vsh.ITEM_CATEGORY_PHOTO, Vsh.ITEM_CATEGORY_MUSIC, Vsh.ITEM_CATEGORY_VIDEO) &&
            nodeId in MEDIA_SOURCE_NODE_IDS
}

fun Vsh.resolveRouting(item: XmbItem) : RoutingResult {
    var categoryId = ""
    var nodeId : String? = null

    if(item is XmbAppItem){
        categoryId = item.appCategory
        if(item.appNodeOverride.isNotEmpty() && !isAppBlockedFromMediaSourceNode(categoryId, item.appNodeOverride)){
            nodeId = item.appNodeOverride
        }
    }

    // Default Routing
    if(categoryId.isEmpty()){
        categoryId = when(item){
            is XmbAppItem -> M.apps.isAGame(item.resInfo).select(Vsh.ITEM_CATEGORY_GAME, Vsh.ITEM_CATEGORY_APPS)
            else -> ""
        }
    }

    // Default Node Routing
    if(nodeId == null){
        nodeId = when(categoryId) {
            Vsh.ITEM_CATEGORY_GAME -> "game_android"
            Vsh.ITEM_CATEGORY_APPS -> Vsh.NODE_APPS_MEMORY_STICK
            Vsh.ITEM_CATEGORY_MUSIC -> (item !is XmbAppItem).select("music_ms", null)
            Vsh.ITEM_CATEGORY_PHOTO -> (item !is XmbAppItem).select("photo_ms", null)
            Vsh.ITEM_CATEGORY_VIDEO -> (item !is XmbAppItem).select("video_ms", null)
            else -> null
        }
    }

    return RoutingResult(categoryId, nodeId, categoryId.isNotEmpty())
}

fun Vsh.addToCategory(categoryId: String, item: XmbItem) : Boolean {
    val routing = resolveRouting(item)
    val finalCatId = if(categoryId.isEmpty()) routing.categoryId else categoryId
    val finalNodeId = routing.nodeId

    synchronized(categories){
        val category = categories.find { it.id == finalCatId } ?: return false
        
        if(finalNodeId != null){
            val node = category.findNode(finalNodeId)
            if(node is XmbNodeItem){
                node.addItem(item)
                return true
            }
        }
        
        category.addItem(item)
        return true
    }
}

fun Vsh.getItemFromCategory(categoryId: String, itemId: String): XmbItem? {
    synchronized(categories) {
        val category = categories.find { it.id == categoryId } ?: return null

        fun findInContent(content: ArrayList<XmbItem>): XmbItem? {
            for (item in content) {
                if (item.id == itemId) return item
                if (item is XmbNodeItem) {
                    val found = findInContent(item.content)
                    if (found != null) return found
                }
            }
            return null
        }

        return findInContent(category.content)
    }
}

fun Vsh.removeFromCategory(categoryId: String, itemId: String): Boolean {
    synchronized(categories) {
        val category = categories.find { it.id == categoryId } ?: return false

        fun removeFromContent(content: ArrayList<XmbItem>): Boolean {
            val removed = content.removeAll { it.id == itemId }
            if (removed) return true
            for (item in content) {
                if (item is XmbNodeItem) {
                    if (removeFromContent(item.content)) return true
                }
            }
            return false
        }

        return removeFromContent(category.content)
    }
}

fun Vsh.swapCategory(itemId: String, categoryFrom: String, categoryTo: String): Boolean {
    synchronized(categories) {
        var item: XmbItem? = null
        var srcCat: XmbItemCategory? = null

        // 1. Try to find the item in the hinted category first
        if (categoryFrom.isNotEmpty()) {
            srcCat = categories.find { it.id == categoryFrom }
            if (srcCat != null) {
                item = getItemFromCategory(srcCat.id, itemId)
            }
        }

        // 2. Fallback: Search all categories if not found in hint
        if (item == null) {
            for (cat in categories) {
                item = getItemFromCategory(cat.id, itemId)
                if (item != null) {
                    srcCat = cat
                    break
                }
            }
        }

        // 3. Find destination category
        var dstCatId = categoryTo
        if (dstCatId.isEmpty() && item != null) {
            dstCatId = resolveRouting(item).categoryId
        }
        val dstCat = categories.find { it.id == dstCatId }

        if (srcCat != null && item != null && dstCat != null) {
            // 4. Robust move: Remove first, then add.
            // This prevents issues when moving within the same category (node moves)
            // where adding before removing could lead to unexpected removal of the new instance.
            removeFromCategory(srcCat.id, itemId)
            addToCategory(dstCat.id, item)

            dstCat.onSwitchSortFunc
            return true
        } else {
            vsh.postNotification(
                R.drawable.ic_error,
                vsh.getString(R.string.error_category_switch_failed_title),
                vsh.getString(R.string.error_category_switch_failed_desc).format(itemId, categoryFrom, categoryTo),
                5.0f
            )
        }
    }
    return false
}

private fun Vsh.readRecentGameIds(): List<String> {
    return M.pref.get(PrefEntry.RECENT_GAME_IDS, "")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun Vsh.writeRecentGameIds(ids: List<String>) {
    M.pref.set(PrefEntry.RECENT_GAME_IDS, ids.joinToString("\n"))
}

fun Vsh.refreshRecentGamesNode() {
    synchronized(categories) {
        val gameCategory = categories.find { it.id == Vsh.ITEM_CATEGORY_GAME } ?: return
        val recentNode = gameCategory.findNode(RECENT_GAMES_NODE_ID) as? XmbNodeItem ?: return
        val recentIds = readRecentGameIds()
        val recentItems = recentIds.mapNotNull { recentId ->
            allAppEntries.find { it.id == recentId && M.apps.isAGame(it.resInfo) }
        }

        synchronized(recentNode.content) {
            recentNode.content.clear()
            recentItems.forEach(recentNode::addItem)
        }

        val sanitizedIds = recentItems.map { it.id }
        if (sanitizedIds != recentIds) {
            writeRecentGameIds(sanitizedIds)
        }
    }
}

fun Vsh.recordRecentGameLaunch(appItemId: String) {
    val app = allAppEntries.find { it.id == appItemId } ?: return
    if (!M.apps.isAGame(app.resInfo)) return

    val reordered = ArrayList<String>(RECENT_GAMES_LIMIT)
    reordered.add(appItemId)
    readRecentGameIds()
        .filter { it != appItemId }
        .take(RECENT_GAMES_LIMIT - 1)
        .forEach(reordered::add)

    writeRecentGameIds(reordered)
    refreshRecentGamesNode()
}

