#version 150
in vec4 vertexColor;
out vec4 fragColor;
// Calamity StandardPrimitiveShader: no texture, lighting or edge attenuation.
void main() { fragColor = vertexColor; }
