precision highp float;

attribute vec2 POSITION;
attribute vec2 TEXCOORD0;
varying vec2 screenPos;

#define nrange(a) ((a + 1.0) / 2.0)

vec2 uv_data(){
    return TEXCOORD0;
}

void main(){
    screenPos = uv_data();
    gl_Position = vec4(POSITION, 0.0, 1.0);
}
