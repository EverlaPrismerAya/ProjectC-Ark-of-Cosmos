#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
out vec4 vertexColor;
out vec3 fPos;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fPos = vec3((UV0.x * 256.0 - 100.5) / 50.0, (UV0.y * 256.0 - 56.0) / 50.0, -1.0);
    texCoord = UV0;
    vertexColor = Color;
}
