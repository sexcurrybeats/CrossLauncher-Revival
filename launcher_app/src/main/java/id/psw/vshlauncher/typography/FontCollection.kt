package id.psw.vshlauncher.typography

import android.graphics.Typeface
import id.psw.vshlauncher.Vsh

object FontCollections {
    /**
     * This app will try load font at path `"Internal Storage/Android/data/id.psw.vshlauncher/dev_flash/data/font/VSH-CustomFont.ttf"`
     *
     * This path is intended for user-provided fonts that mimic the shell look.
     * Public releases should only ship fonts with clear redistribution rights.
     */
    var masterFont : Typeface = Typeface.SANS_SERIF
    /**
     * The bundled button symbol font `"/assets/vshbtn.ttf"` is a lightweight helper for controller glyph rendering.
     */
    var buttonFont : Typeface = Typeface.DEFAULT

    const val TAG = "fntmgr.self"
    private const val FONT_NAME = "VSH-CustomFont"

    fun init(ctx: Vsh){
        ctx.M.font.initialize()
    }
}
