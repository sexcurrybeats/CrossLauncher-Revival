package id.psw.vshlauncher.types.items

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.views.dialogviews.ConfirmDialogView
import id.psw.vshlauncher.views.dialogviews.WaitForAndroidSettingDialogView
import id.psw.vshlauncher.xmb

class XmbAndroidSettingShortcutItem(
    vsh: Vsh,
    @DrawableRes private val iconId:Int,
    @StringRes private val nameId:Int,
    @StringRes private val descId: Int,
    private val intentLaunchId:String
) : XmbItem(vsh) {
    private val _iconRef = vsh.M.iconManager.getSettingsIcon(intentLaunchId, iconId)

    override val displayName: String get() = vsh.getString(nameId)
    override val description: String get() = vsh.getString(descId)
    override val hasDescription: Boolean
        get() = description.isNotBlank()
    override val isIconLoaded: Boolean get() = _iconRef.isLoaded
    override val icon: Bitmap get() = _iconRef.bitmap
    override val hasIcon: Boolean
        get() = icon != TRANSPARENT_BITMAP

    override var hasContent: Boolean = false
    override var content: ArrayList<XmbItem> = arrayListOf()

    override val id: String = intentLaunchId
    private var _isActivityExists = false
    override val isHidden: Boolean
        get() = !_isActivityExists

    internal var useComponentInstead = false

    init {
        val i = Intent(intentLaunchId)
        i.flags = i.flags or Intent.FLAG_ACTIVITY_NEW_TASK
        _isActivityExists = i.resolveActivityInfo(vsh.packageManager, 0) != null

        // For testing
        if(intentLaunchId.startsWith("id.psw.vshlauncher")){
            _isActivityExists = true
        }
    }

    private fun launchSetting(xmb:XmbItem){
        val v =vsh.xmbView ?: return
        v.showDialog(WaitForAndroidSettingDialogView(v, displayName, intentLaunchId, useComponentInstead))
    }

    override val onLaunch: (XmbItem) -> Unit
        get() = ::launchSetting
}
