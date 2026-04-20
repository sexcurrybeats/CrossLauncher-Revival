/**
 * Created by EmiyaSyahriel, 20/05/2021.
 * JNI Exports for use in Android and possibly LWJGL-based Java applications
 */
#include <jni.h>
#include "../../wave/WAVE.HPP"

#define EXTC extern "C"

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_create(JNIEnv *env, jobject obj){
        cxl_wave_start();
    }

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_destroy(JNIEnv *env, jobject obj){
        cxl_wave_destroy();
    }

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_draw(JNIEnv *env, jobject obj, jfloat deltaTime){
        cxl_wave_draw(deltaTime);
    }

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setup(JNIEnv *env, jobject obj, jint w, jint h){
        cxl_wave_resize(w,h);
    }

EXTC JNIEXPORT void JNICALL
    Java_id_psw_vshlauncher_livewallpaper_NativeGL_setPaused(JNIEnv *env, jobject thiz, jboolean paused) {
        wave_state.paused = paused;
    }

EXTC JNIEXPORT jboolean JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_getPaused(JNIEnv *env, jobject thiz) { return wave_state.paused; }

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setWaveStyle(JNIEnv *env, jobject obj, jbyte mode){
    wave_state.style = static_cast<WAVE_STYLE>(mode);
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setBackgroundMonth(JNIEnv *env, jobject obj, jbyte mode){
    wave_state.bg_month = (MONTH_COLOR_INDEX)mode;
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setSpeed(JNIEnv *env, jobject obj, jfloat speed){
    wave_state.speed = speed;
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setBackgroundColor(JNIEnv *env, jobject obj, jint top, jint bottom){
    vec4 a = cxl_i2v4(top), b = cxl_i2v4(bottom);
    wave_state.colors.bg_a = vec3(b.y, b.z, b.w);
    wave_state.colors.bg_b = vec3(a.y, a.z, a.w);
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setBackgroundTexture(
        JNIEnv *env,
        jobject obj,
        jint width,
        jint height,
        jintArray pixels
) {
    if(width <= 0 || height <= 0 || pixels == nullptr) {
        wave_state.bg.texture_enabled = false;
        wave_state.bg.texture_dirty = false;
        return;
    }
    const int pixel_count = width * height;
    if(pixel_count <= 0 || pixel_count > BG_TEXTURE_MAX_PIXELS) {
        wave_state.bg.texture_enabled = false;
        wave_state.bg.texture_dirty = false;
        return;
    }
    if(env->GetArrayLength(pixels) < pixel_count) {
        wave_state.bg.texture_enabled = false;
        wave_state.bg.texture_dirty = false;
        return;
    }

    jint* argb = env->GetIntArrayElements(pixels, nullptr);
    if(argb == nullptr) return;

    for(int i = 0; i < pixel_count; ++i){
        const unsigned int color = static_cast<unsigned int>(argb[i]);
        byte* out = &wave_state.bg.texture_pixels[i * 4];
        out[0] = static_cast<byte>((color >> 16) & 0xFF);
        out[1] = static_cast<byte>((color >> 8) & 0xFF);
        out[2] = static_cast<byte>(color & 0xFF);
        out[3] = static_cast<byte>((color >> 24) & 0xFF);
    }

    env->ReleaseIntArrayElements(pixels, argb, JNI_ABORT);
    wave_state.bg.texture_width = width;
    wave_state.bg.texture_height = height;
    wave_state.bg.texture_enabled = true;
    wave_state.bg.texture_dirty = true;
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_clearBackgroundTexture(JNIEnv *env, jobject obj){
    wave_state.bg.texture_enabled = false;
    wave_state.bg.texture_dirty = false;
    wave_state.bg.texture_width = 0;
    wave_state.bg.texture_height = 0;
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setForegroundColor(JNIEnv *env, jobject obj, jint edge, jint center){
    wave_state.colors.fg_a = cxl_i2v4a(edge);
    wave_state.colors.fg_b = cxl_i2v4a(center);
}

EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setVerticalScale(JNIEnv* env, jobject obj, jfloat scale){
    wave_state.vert_scale = scale;
}
EXTC JNIEXPORT void JNICALL Java_id_psw_vshlauncher_livewallpaper_NativeGL_setBgDayNightMode(JNIEnv *env, jobject thiz, jboolean dayNight) {
    wave_state.day_night = dayNight;
}
