#version 150
uniform sampler2D SceneSampler;
uniform vec2 ScreenSize;
in vec4 vertexColor;
in vec3 texCoord;
out vec4 fragColor;
void main(){
    vec2 p=texCoord.xy*2.0-1.0;
    float rim=pow(max(0.0,1.0-abs(p.x)),0.5)*pow(max(0.0,1.0-abs(p.y)),2.0);
    float strength=vertexColor.a*rim;
    vec2 gradient=vec2(dFdx(texCoord.y),dFdy(texCoord.y));
    vec2 normal=gradient/max(length(gradient),0.000001);
    vec2 offset=normal*(p.y<0.0?-1.0:1.0)*strength*18.0/ScreenSize;
    vec2 uv=gl_FragCoord.xy/ScreenSize;
    vec2 lo=vec2(0.5)/ScreenSize,hi=vec2(1.0)-lo;
    vec3 color=vec3(texture(SceneSampler,clamp(uv+offset*2.5,lo,hi)).r,
                    texture(SceneSampler,clamp(uv+offset,lo,hi)).g,
                    texture(SceneSampler,clamp(uv-offset*0.5,lo,hi)).b);
    fragColor=vec4(color,strength);
}
