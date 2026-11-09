#version 150
// Port of LuaSTG THlib deatheff (player.lua): every circle is drawn with the inverting
// "_rev_white" blend, so overlapping circles cancel (XOR parity).
//   first  (frame 0):  centre r=12px/f, four circles at (+-35,+-35)px r=8px/f
//   second (frame 30): centre r=12px/f
// 60 fps frames -> 20 tps ticks (x3), 16 px per block. Offsets lie in the screen plane.
uniform sampler2D SceneSampler;
uniform vec2 ScreenSize;
uniform mat4 InverseViewProjection;
uniform vec3 Center;
uniform vec3 Right;
uniform vec3 Up;
uniform float Age;
out vec4 fragColor;
float circle(vec3 ray,vec3 center,float radius){
    if(radius<=0.0)return 0.0;
    float along=dot(ray,center);
    float distanceToRay=length(center-ray*max(along,0.0));
    return step(distanceToRay,radius);
}
void main(){
    vec2 uv=gl_FragCoord.xy/ScreenSize;
    vec4 farPoint=InverseViewProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(farPoint.xyz/farPoint.w);
    float frames=max(Age,0.0)*3.0;
    float size=frames*12.0/16.0,size1=frames*8.0/16.0,size2=max(frames-30.0,0.0)*12.0/16.0;
    float offset=35.0/16.0;
    float parity=circle(ray,Center,size)+circle(ray,Center,size2);
    for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2)
        parity+=circle(ray,Center+Right*(float(sx)*offset)+Up*(float(sy)*offset),size1);
    float inversion=mod(parity,2.0);
    vec3 scene=texture(SceneSampler,uv).rgb;
    fragColor=vec4(mix(scene,1.0-scene,inversion),1.0);
}
