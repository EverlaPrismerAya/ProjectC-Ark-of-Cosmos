#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec2 PackedLight;
uniform float WhiteMix;
uniform float DustOverlay;
uniform vec4 DustColor;
uniform float AlphaCutoff;
in vec4 vertexColor;
in vec3 texCoord;
in vec3 viewPosition;
uniform float NearFade;
uniform float FadePremultiplied;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord.xy);
    if (sampleColor.a < AlphaCutoff) discard;
    vec4 sourceColor = vertexColor;
    sourceColor.rgb *= mix(texture(Sampler2, PackedLight).rgb, vec3(1.0), WhiteMix);
    if (DustOverlay > 0.5) sourceColor = clamp(DustColor - (vec4(1.0) - sourceColor), 0.0, 1.0);
    fragColor = sampleColor * sourceColor;
    // Keep this curve in sync with ArkFxDraw.nearOpacity and the other three sprite/trail fragments.
    float fade = mix(1.0, mix(0.12, 1.0, smoothstep(0.5, 3.0, length(viewPosition))), NearFade);
    fragColor.a *= fade;
    fragColor.rgb *= mix(1.0, fade, FadePremultiplied);
}
