#version 150
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec3 texCoord;
in vec3 viewPosition;
uniform float NearFade;
uniform float FadePremultiplied;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord.xy);
    fragColor = sampleColor * vertexColor;
    float fade = mix(1.0, mix(0.12, 1.0, smoothstep(0.5, 3.0, length(viewPosition))), NearFade);
    fragColor.a *= fade;
    fragColor.rgb *= mix(1.0, fade, FadePremultiplied);
}
