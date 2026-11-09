#version 150
uniform sampler2D SceneSampler;
uniform vec2 ScreenSize;
uniform mat4 InverseViewProjection;
uniform vec3 Center;
uniform float Age;
out vec4 fragColor;
float sphere(vec3 ray,vec3 center,float radius){
    float along=dot(ray,center);
    float distanceToRay=length(center-ray*max(along,0.0));
    return (1.0-smoothstep(max(0.0,radius-0.12),radius+0.12,distanceToRay))*step(0.01,radius);
}
void main(){
    vec2 uv=gl_FragCoord.xy/ScreenSize;
    vec4 farPoint=InverseViewProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(farPoint.xyz/farPoint.w);
    float radius=8.0*(1.0-exp(-max(Age,0.0)*0.22));
    float outer=0.0;
    for(int axis=0;axis<3;axis++)for(int sign=-1;sign<=1;sign+=2){
        vec3 offset=vec3(0);offset[axis]=float(sign)*2.0;
        outer=max(outer,sphere(ray,Center+offset,radius));
    }
    float innerRadius=11.0*(1.0-exp(-max(Age-7.0,0.0)*0.22));
    float inner=sphere(ray,Center,innerRadius);
    float inversion=abs(outer-inner)*(1.0-smoothstep(26.0,40.0,Age));
    vec3 scene=texture(SceneSampler,uv).rgb;
    fragColor=vec4(mix(scene,1.0-scene,inversion),1.0);
}
