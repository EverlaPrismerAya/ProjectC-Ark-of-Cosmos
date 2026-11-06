#version 150
// Calamity Effects/HellBall.fx, pinned 1a8cebd. Ordinary-world source parameters.
uniform sampler2D Sampler0;
uniform float uTime;
uniform float uOpacity;
uniform vec3 uShieldColor;
in vec3 texCoord;
out vec4 fragColor;
void main() {
    vec2 uv = texCoord.xy;
    float distanceFromCenter = length(uv - vec2(0.5)) * 2.0;
    if (distanceFromCenter > 1.0) { fragColor = vec4(0.0); return; }
    vec2 blown = pow(abs(uv - vec2(0.5)) * 2.0, vec2(3.2));
    vec2 blownUpUV = -blown.yx * 0.4 * 0.5 + uv * (vec2(1.0) + blown.yx * 0.4);
    blownUpUV *= 0.6;
    // HLSL remainder is truncation based, including negative values.
    float scroll = blownUpUV.y + uTime;
    blownUpUV.y = scroll - trunc(scroll);
    vec4 noiseColor = texture(Sampler0, blownUpUV);
    noiseColor += pow(distanceFromCenter, 6.0);
    if (distanceFromCenter > 0.95) noiseColor *= 1.0 - (distanceFromCenter - 0.95) / 0.05;
    fragColor = noiseColor * vec4(mix(uShieldColor, vec3(0.0), pow(distanceFromCenter, 4.0)), uOpacity);
}
