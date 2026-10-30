#version 150
in vec3 Position;
in vec4 Color;
in vec3 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 FadeEye;
out vec4 vertexColor;
out vec3 texCoord;
out vec3 viewPosition;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
    viewPosition = Position - FadeEye;
}
