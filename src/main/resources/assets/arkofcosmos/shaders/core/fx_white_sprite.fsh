#version 150
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec3 texCoord;
out vec4 fragColor;
void main() {
    float alpha = texture(Sampler0, texCoord.xy).a * vertexColor.a;
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}
