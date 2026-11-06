#version 150
in vec3 Position;
in vec4 Color;
in vec3 UV0;
out vec4 vertexColor;
out vec2 texCoord;
void main() {
    gl_Position = vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0.xy;
}
