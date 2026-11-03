#version 150
uniform sampler2D Sampler0;
uniform float AlphaCutoff;
in vec4 vertexColor;
in vec3 texCoord;
in vec3 viewPosition;
uniform float NearFade;
uniform float FadePremultiplied;
uniform float AncientsCharge;
uniform float AncientsGlow;
uniform float AncientsTime;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord.xy);
    if (sampleColor.a < AlphaCutoff) discard;
    fragColor = sampleColor * vertexColor;
    float pulse = 0.92 + 0.08 * sin(AncientsTime * 6.2831);
    float edge = smoothstep(0.0, 0.45, sampleColor.a);
    fragColor.rgb *= 1.0 + AncientsGlow * pulse * edge * (0.35 + 0.65 * AncientsCharge);
    float fade = mix(1.0, mix(0.12, 1.0, smoothstep(0.5, 3.0, length(viewPosition))), NearFade);
    fragColor.a *= fade;
    fragColor.rgb *= mix(1.0, fade, FadePremultiplied);
}
