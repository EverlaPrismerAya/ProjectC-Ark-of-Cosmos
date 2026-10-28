#version 150
uniform sampler2D Sampler0;
uniform float uTime;
in vec4 vertexColor;
in vec3 texCoord;
in vec3 viewPosition;
uniform float NearFade;
uniform float FadePremultiplied;
out vec4 fragColor;
// Calamity Effects/FadedUVMapStreak.fx, TrailPass, without changing constants.
void main() {
    vec2 coords = texCoord.xy;
    coords.y = (coords.y - 0.5) / texCoord.z + 0.5;
    float opacity = texture(Sampler0, vec2(fract(coords.x - uTime * 2.5), coords.y)).r;
    float power = mix(3.0, 10.0, coords.x);
    opacity = mix(pow(sin(coords.y * 3.141), power), opacity, coords.x);
    if (coords.x > 0.7)
        opacity *= pow(1.0 - (coords.x - 0.7) / 0.3, 6.0);
    fragColor = vertexColor * opacity * 1.5;
    float fade = mix(1.0, mix(0.12, 1.0, smoothstep(0.5, 3.0, length(viewPosition))), NearFade);
    fragColor.a *= fade;
    fragColor.rgb *= mix(1.0, fade, FadePremultiplied);
}
