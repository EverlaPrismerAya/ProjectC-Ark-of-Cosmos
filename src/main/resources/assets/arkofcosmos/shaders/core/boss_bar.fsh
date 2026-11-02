#version 150
uniform sampler2D Sampler0;
uniform sampler2D Cosmic0, Cosmic1, Cosmic2, Cosmic3, Cosmic4, Cosmic5, Cosmic6, Cosmic7, Cosmic8, Cosmic9;
uniform float uTime;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 fPos;
out vec4 fragColor;
#define M_PI 3.14159265358979323846
uniform float Frame0, Frame1, Frame2, Frame3, Frame4, Frame5, Frame6, Frame7, Frame8, Frame9;
vec2 layerUv(vec2 pos, float angle) {
    float c = cos(angle), s = sin(angle);
    return mat2(c, -s, s, c) * pos * 16.0;
}
vec4 frame(sampler2D sheet, vec2 uv, float index) {
    vec2 size = vec2(textureSize(sheet, 0));
    // SimpleTexture exposes the whole animation strip, not an atlas sprite.
    vec2 pixel = vec2(0.5) + uv * (size.x - 1.0);
    pixel.y += index * size.x;
    return texture(sheet, pixel / size);
}
vec4 star(int n, vec2 uv) {
    if (n == 0) return frame(Cosmic0, uv, Frame0); if (n == 1) return frame(Cosmic1, uv, Frame1);
    if (n == 2) return frame(Cosmic2, uv, Frame2); if (n == 3) return frame(Cosmic3, uv, Frame3);
    if (n == 4) return frame(Cosmic4, uv, Frame4); if (n == 5) return frame(Cosmic5, uv, Frame5);
    if (n == 6) return frame(Cosmic6, uv, Frame6); if (n == 7) return frame(Cosmic7, uv, Frame7);
    if (n == 8) return frame(Cosmic8, uv, Frame8); return frame(Cosmic9, uv, Frame9);
}
void main() {
    vec4 mask = texture(Sampler0, texCoord);
    vec3 col = vec3(0.1, 0.0, 0.0);
    float pulse = mod(uTime, 400.0) / 400.0;
    col.g = sin(pulse*M_PI*2.0)*0.075+0.225;
    col.b = cos(pulse*M_PI*2.0)*0.05+0.3;
    for (int i=0;i<16;i++) {
        int j=i+7; float r1=(j*j*4321+j*8)*2.0, r2=(j+1)*(j+1)*(j+1)*239.0+(j+1)*37.0;
        float r3=r1*347.4+r2*63.4;
        vec2 uv = layerUv(fPos.xy, mod(r3,2.0*M_PI) + uTime * 0.025);
        uv.y += uTime * 0.0032;
        int tu = int(mod(floor(uv.x), 16.0));
        int tv = int(mod(floor(uv.y), 16.0));
        int symbol = int(mod((171 * tu + 489 * tv + 303 * (i + 31) + 17209) ^ 10, 101));
        if (symbol < 10) {
            vec2 suv = fract(uv);
            vec4 icon = star(symbol, suv);
            col += icon.rgb * icon.a * 0.035 * 3.0;
        }
    }
    fragColor = vec4(clamp(col * vertexColor.rgb,0.0,1.0), mask.r * mask.a * vertexColor.a);
}
