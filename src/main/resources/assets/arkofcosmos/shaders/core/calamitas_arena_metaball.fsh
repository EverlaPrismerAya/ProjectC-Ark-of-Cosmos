#version 150
// Calamity 1a8cebd MetaballEdgeShader + ScalArenaMetaball's wave correction.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 uScreenSize;
uniform vec2 LayerSize;
uniform vec2 LayerOffset;
uniform vec2 SingleFrameScreenOffset;
uniform vec4 EdgeColor;
uniform vec4 LayerColor;
uniform float WaveLayer;
in vec4 vertexColor;
in vec2 texCoord;
out vec4 fragColor;

vec4 contents(vec2 topLeftCoords) {
    // FBO rows run upwards, while the pinned SpriteBatch shader uses top-left coordinates.
    return texture(Sampler0, vec2(topLeftCoords.x, 1.0 - topLeftCoords.y));
}
void main() {
    vec2 coords = vec2(texCoord.x, 1.0 - texCoord.y);
    vec4 baseColor = contents(coords);
    float alphaOffset = baseColor.a == 0.0 ? 1.0 : 0.0;
    vec2 twoPixels = vec2(2.0) / uScreenSize;
    float left = contents(coords - vec2(twoPixels.x, 0)).a + alphaOffset;
    float right = contents(coords + vec2(twoPixels.x, 0)).a + alphaOffset;
    float top = contents(coords - vec2(0, twoPixels.y)).a + alphaOffset;
    float bottom = contents(coords + vec2(0, twoPixels.y)).a + alphaOffset;
    float interior = 1.0 - clamp(step(left, 0.0) + step(right, 0.0)
                              + step(top, 0.0) + step(bottom, 0.0), 0.0, 1.0);
    vec2 overlayCoords = (coords + LayerOffset + SingleFrameScreenOffset) * uScreenSize / LayerSize;
    vec4 layer = texture(Sampler1, overlayCoords);
    // Original PNG stays unchanged; source PrepareSpriteBatch rewrites all four channels from red.
    if (WaveLayer > 0.5) layer = vec4(layer.r);
    vec4 defaultColor = layer * baseColor * vertexColor * LayerColor;
    fragColor = defaultColor * interior + EdgeColor * vertexColor * (1.0 - interior);
    // Detect silhouettes on the unoccluded mask, then apply terrain visibility separately.
    // This prevents an opaque foreground block from acquiring a false metaball outline.
    float visibleAlpha = texture(Sampler2, texCoord).a;
    fragColor *= baseColor.a > 0.0 ? clamp(visibleAlpha / baseColor.a, 0.0, 1.0) : 0.0;
}
