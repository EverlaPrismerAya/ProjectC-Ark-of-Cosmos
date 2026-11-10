#version 150
// Satsuki Rin's boss-bar fill: the vertex colour replaces the texture colour, while the bar texture's
// own alpha remains the clipping mask (the same silhouette RF's native fill blit uses).
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float mask = texture(Sampler0, texCoord).a;
    if (mask * vertexColor.a < 0.004) discard;
    fragColor = vec4(vertexColor.rgb, mask * vertexColor.a);
}
