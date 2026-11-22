#version 150

#define M_PI 3.1415926535897932384626433832795

#moj_import <fog.glsl>

const int cosmiccount = 10;
const int cosmicoutof = 101;
const float lightmix = 0.2f;

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;
uniform int threadripper;
uniform vec4 maskUv;
uniform vec2 viewLean;

uniform float yaw;
uniform float pitch;
uniform float externalScale;

uniform float opacity;

uniform mat2 cosmicuvs[cosmiccount];

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;

out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle)
{

    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

vec4 cosmicStars(vec4 dir, float fieldScale, bool crisp) {
    float oneOverExternalScale = 1.0 / fieldScale;
    const int uvtiles = 16;
    vec4 col = vec4(0.0);
    vec4 ray;

    // draw the layers
    for (int i = 0; i < 16; i++) {
        int mult = 16 - i;

        // get semi-random stuff
        int j = i + 7;
        float rand1 = (j * j * 4321 + j * 8) * 2.0F;
        int k = j + 1;
        float rand2 = (k * k * k * 239 + k * 37) * 3.6F;
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        // random rotation matrix by random rotation around random axis
        vec3 axis = normalize(vec3(sin(rand1), sin(rand2), cos(rand3)));

        // apply
        ray = dir * rotationMatrix(axis, mod(rand3, 2 * M_PI));

        // calcuate the UVs from the final ray
        float rawu = 0.5 + (atan(ray.z, ray.x) / (2 * M_PI));
        float rawv = 0.5 + (asin(ray.y) / M_PI);

        // get UV scaled for layers and offset by time;
        float scale = mult * 0.5 + 2.75;
        float u = rawu * scale * fieldScale;
        float v = (rawv + time * 0.0002 * oneOverExternalScale) * scale * 0.6 * fieldScale;

        vec2 tex = vec2(u, v);

        // tile position of the current uv
        int tu = int(mod(floor(u * uvtiles), uvtiles));
        int tv = int(mod(floor(v * uvtiles), uvtiles));

        // get pseudorandom variants
        int position = ((171 * tu) + (489 * tv) + (303 * (i + 31)) + 17209) ^ 10;
        int symbol = int(mod(position, cosmicoutof));
        int rotation = crisp ? (position & 7) : int(mod(pow(tu, float(tv)) + tu + 3 + tv * i, 8));
        bool flip = false;
        if (rotation >= 4) {
            rotation -= 4;
            flip = true;
        }

        // if it's an icon, then add the color!
        if (symbol >= 0 && symbol < cosmiccount) {

            vec2 cosmictex = vec2(1.0, 1.0);
            vec4 tcol = vec4(1.0, 0.0, 0.0, 1.0);

            // get uv within the tile
            float ru = crisp ? fract(u * uvtiles) : clamp(mod(u, 1.0) * uvtiles - tu, 0.0, 1.0);
            float rv = crisp ? fract(v * uvtiles) : clamp(mod(v, 1.0) * uvtiles - tv, 0.0, 1.0);

            if (flip) {
                ru = 1.0 - ru;
            }

            float oru = ru;
            float orv = rv;

            // rotate uvs if necessary
            if (rotation == 1) {
                oru = 1.0 - rv;
                orv = ru;
            } else if (rotation == 2) {
                oru = 1.0 - ru;
                orv = 1.0 - rv;
            } else if (rotation == 3) {
                oru = rv;
                orv = 1.0 - ru;
            }

            // get the icon uvs for the tile
            float umin = cosmicuvs[symbol][0][0];
            float umax = cosmicuvs[symbol][1][0];
            float vmin = cosmicuvs[symbol][0][1];
            float vmax = cosmicuvs[symbol][1][1];

            // interpolate based on tile uvs
            cosmictex.x = umin * (1.0 - oru) + umax * oru;
            cosmictex.y = vmin * (1.0 - orv) + vmax * orv;

            // These are discrete atlas tiles; derivative-based LOD crosses sprite boundaries.
            if (crisp) {
                vec2 inset = 0.5 / vec2(textureSize(Sampler0, 0));
                cosmictex = clamp(cosmictex, vec2(umin, vmin) + inset, vec2(umax, vmax) - inset);
                tcol = textureLod(Sampler0, cosmictex, 0.0);
                // Some frames of the largest star touch the tile edge. Fade only there.
                vec2 margin = min(vec2(oru, orv), 1.0 - vec2(oru, orv));
                tcol.r *= smoothstep(0.0, 0.055, min(margin.x, margin.y));
            } else tcol = texture(Sampler0, cosmictex);

            // set the alpha, blending out at the bunched ends
            float a = tcol.r * (0.5 + (1.0 / mult) * 1.0) * (1.0 - smoothstep(0.15, 0.48, abs(rawv - 0.5)));

            // get fancy colours
            float r = (mod(rand1, 29.0) / 29.0) * 0.3 + 0.4;
            float g = (mod(rand2, 35.0) / 35.0) * 0.4 + 0.6;
            float b = (mod(rand1, 17.0) / 17.0) * 0.3 + 0.7;

            // mix the colours
            col = col + vec4(r, g, b, 1) * a;
        }
    }
    return col;
}

float fieldHash(vec2 p) {
    vec3 h = fract(vec3(p.xyx) * 0.1031);
    h += dot(h, h.yzx + 33.33);
    return fract((h.x + h.y) * h.z);
}

float cloudNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(fieldHash(i), fieldHash(i + vec2(1, 0)), f.x),
               mix(fieldHash(i + vec2(0, 1)), fieldHash(i + 1.0), f.x), f.y);
}

float maskRed(vec2 uv) {
    // Smooth the mask independently: the block atlas must remain nearest for Cosmos and other sprites.
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    ivec2 lo = ivec2(round(maskUv.xy * atlasSize));
    ivec2 hi = ivec2(round(maskUv.zw * atlasSize)) - 1;
    vec2 pos = mix(maskUv.xy, maskUv.zw, uv) * atlasSize - 0.5;
    ivec2 px = ivec2(floor(pos));
    vec2 phase = fract(pos);
    float a = texelFetch(Sampler0, clamp(px, lo, hi), 0).r;
    float b = texelFetch(Sampler0, clamp(px + ivec2(1, 0), lo, hi), 0).r;
    float c = texelFetch(Sampler0, clamp(px + ivec2(0, 1), lo, hi), 0).r;
    float d = texelFetch(Sampler0, clamp(px + ivec2(1, 1), lo, hi), 0).r;
    return mix(mix(a, b, phase.x), mix(c, d, phase.x), phase.y);
}

vec3 threadripperColor(vec2 uv, float mask) {
    float lengthUv = (uv.x + 1.0 - uv.y) * 0.5;
    float crossUv = (uv.x + uv.y - 1.0) * 3.3;
    vec2 q = vec2(lengthUv * 5.8, crossUv);
    float clock = mod(time, 72000.0);
    vec2 deep = q + viewLean * 0.80 + vec2(-clock * 0.017, 0.0);
    vec2 front = q * 1.85 + viewLean * 2.45 + vec2(clock * 0.043, 7.31);
    float warp = cloudNoise(deep * 0.55 + 16.0);
    float body = cloudNoise(deep + vec2(warp * 2.6, warp)) * 0.71
               + cloudNoise(deep * 2.1 - vec2(0, warp)) * 0.29;
    float ridge = 1.0 - abs(cloudNoise(front + vec2(warp * 2.9, 0)) * 2.0 - 1.0);
    float filament = smoothstep(0.70, 0.96, ridge);
    vec3 color = mix(vec3(0.015, 0.018, 0.068), vec3(0.076, 0.15, 0.38), smoothstep(0.34, 0.68, body));
    color += mix(vec3(0.50, 0.073, 0.69), vec3(0.01, 0.53, 0.77), smoothstep(0.36, 0.67, body))
             * filament * (0.22 + 0.21 * smoothstep(0.46, 0.72, body));

    float scan = 1.0 - smoothstep(0.019, 0.165, abs(fract(lengthUv - clock / 79.0) - 0.5));
    // Uneven streams: columns drift at different rates and drop most bits between uneven bursts.
    vec2 grid = vec2(crossUv * 3.2, lengthUv * 16.0);
    float lane = floor(grid.x);
    float laneSeed = fieldHash(vec2(lane, 27.0));
    vec2 cell = vec2(grid.x, grid.y * mix(0.74, 1.33, laneSeed) - time * mix(0.038, 0.147, laneSeed) + laneSeed * 11.0);
    vec2 key = floor(cell);
    float bit = fieldHash(key + vec2(7.2, 4.9));
    vec2 shift = vec2(fieldHash(key + 13.7) - 0.5, fieldHash(key + 37.1) - 0.5) * vec2(0.38, 0.34);
    vec2 l = (fract(cell) - 0.5 - shift) * mix(0.86, 1.42, fieldHash(key + 53.8));
    vec2 p = abs(l);
    float zero = max(max(p.x - 0.23, p.y - 0.34), -max(p.x - 0.12, p.y - 0.23));
    float one = min(max(p.x - 0.06, p.y - 0.34), max(p.x - 0.19, abs(l.y + 0.28) - 0.048));
    float glyph = 1.0 - smoothstep(-0.04, 0.09, mix(zero, one, step(0.50, bit)));
    float present = step(0.49, fieldHash(key + 19.0));
    float flicker = 0.37 + 0.63 * smoothstep(-0.35, 0.76, sin(time * mix(0.10, 0.27, bit) + bit * 25.0));
    float trace = (1.0 - smoothstep(0.025, 0.11, p.x)) * step(0.75, fieldHash(key + 2.6))
                  * (1.0 - smoothstep(0.04, 0.31, abs(l.y)));
    float detail = 1.0 - smoothstep(0.35, 0.95, max(fwidth(grid.x), fwidth(grid.y) * 1.15));
    color += vec3(0.26, 0.88, 1.0) * scan * (0.13 + (glyph * present * flicker * 0.55 + trace * 0.24) * detail);

    // Continuous blade coordinates give the rim one long highlight, not one outline per mask texel.
    float edge = smoothstep(0.10, 0.65, abs(crossUv)) * smoothstep(0.055, 0.55, mask)
               * smoothstep(0.42, 0.90, lengthUv);
    float glint = pow(0.5 + 0.5 * cos(lengthUv * 10.0 - time * 0.125 + viewLean.x * 6.5), 4.0);
    return color + vec3(0.32, 0.77, 1.0) * edge * (0.44 + 0.46 * glint + scan * 0.32);
}

vec4 rotateByView(vec4 dir) {
    float sb = sin(pitch), cb = cos(pitch);
    dir.yz = vec2(dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb);
    float sa = sin(-yaw), ca = cos(-yaw);
    return vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0);
}

void main (void)
{
    vec4 mask = texture(Sampler0, texCoord0.xy);
    if (threadripper != 0) {
        vec2 uv = (texCoord0 - maskUv.xy) / (maskUv.zw - maskUv.xy);
        // The blade UVs frame the sky like a small window; rotating the camera reveals
        // another direction of the same star field, even when the held blade stays still on screen.
        vec2 sphere = (uv - 0.5) * 0.53 + mat2(1, 1, 1, -1) * viewLean * 0.29;
        vec3 stars = cosmicStars(normalize(rotateByView(vec4(sphere, 1.0, 0.0))), 0.40, true).rgb;
        float coverage = maskRed(uv);
        vec3 sky = clamp(threadripperColor(uv, coverage), 0.0, 1.0);
        vec3 lit = 1.0 - (1.0 - sky) * (1.0 - clamp(stars * 2.15, 0.0, 1.0));
        vec4 color = vec4(lit, smoothstep(0.015, 0.58, coverage) * opacity);
        fragColor = linear_fog(color * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // background color - deep dark tone
    vec4 col = vec4(0.05, 0.0, 0.08, 1.0);

    float pulse = mod(time, 400) / 400.0;

    col.g = sin(pulse * M_PI * 2) * 0.05 + 0.1;
    col.b = cos(pulse * M_PI * 2) * 0.05 + 0.15;

    vec4 dir = normalize(rotateByView(vec4(-fPos, 0.0)));

    col += cosmicStars(dir, externalScale, false);

    // apply lighting
    vec3 shade = vertexColor.rgb * (lightmix) + vec3(1.0 - lightmix, 1.0 - lightmix, 1.0 - lightmix);
    col.rgb *= shade;

    // apply mask
    col.a *= mask.r * opacity;

    col = clamp(col, 0.0, 1.0);

    fragColor = linear_fog(col * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
