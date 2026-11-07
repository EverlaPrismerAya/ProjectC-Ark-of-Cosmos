#version 150
uniform sampler2D Sampler0;
uniform float uTime;
in vec4 vertexColor;
in vec3 texCoord;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1,0)),f.x),mix(hash(cell+vec2(0,1)),hash(cell+vec2(1,1)),f.x),f.y);
}
void main() {
    vec2 uv=texCoord.xy;
    vec2 flow=uv*vec2(7.0,5.0)+vec2(0,uTime*2.3);
    float turbulence=noise(flow)*.65+noise(flow*2.1)*.25+texture(Sampler0,fract(flow*.13)).r*.1;
    float taper=mix(.12,.5,uv.y);
    float envelope=(1.0-smoothstep(taper*.45,taper,abs(uv.x-.5)))*smoothstep(0.0,.2,uv.y)*(1.0-smoothstep(.8,1.0,uv.y));
    float flame=smoothstep(.35,.7,turbulence+uv.y*.3)*envelope;
    vec3 color=mix(vec3(.48,.01,.16),vec3(1.0,.36,.08),smoothstep(.4,.85,turbulence));
    fragColor=vec4(color,flame*vertexColor.a*.7);
    if(fragColor.a<.015)discard;
}
