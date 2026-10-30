#version 150
uniform sampler2D Sampler0;
uniform float AlphaCutoff;
uniform vec3 uColor;
uniform float uOpacity;
uniform float NearFade;
uniform float FadePremultiplied;
in vec4 vertexColor;
in vec3 texCoord;
in vec3 viewPosition;
out vec4 fragColor;
void main() {
    // Calamity Effects/BasicTint.fx, Recolor: rbg is intentional; COLOR0 is unused.
    vec4 color = texture(Sampler0, texCoord.xy);
    if (color.a < AlphaCutoff) discard;
    float originalAlpha = color.a;
    color.rbg = mix(color.rbg, uColor, uOpacity);
    fragColor = color * originalAlpha;
    float fade = mix(1.0, mix(0.12, 1.0, smoothstep(0.5, 3.0, length(viewPosition))), NearFade);
    fragColor.a *= fade;
    fragColor.rgb *= mix(1.0, fade, FadePremultiplied);
}
