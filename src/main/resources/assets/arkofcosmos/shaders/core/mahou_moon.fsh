#version 150
in vec4 vertexColor;
in vec3 texCoord;
uniform float uTime;
out vec4 fragColor;
// Original procedural art: ivory crescent, indigo corona, orbiting silver runes.
void main() {
    vec2 p=texCoord.xy*2.0-1.0;
    float r=length(p), a=atan(p.y,p.x);
    float ring=exp(-pow((r-.79)*65.0,2.0));
    float halo=exp(-pow((r-.79)*15.0,2.0))*.32;
    float runes=pow(max(0.0,cos(a*24.0-uTime*.7)),24.0)*exp(-pow((r-.9)*80.0,2.0));
    float inner=exp(-pow((r-.63)*100.0,2.0))*.35;
    float tide=(.5+.5*sin(a*5.0-uTime*2.0))*halo;
    float mask=ring+halo+runes+inner+tide;
    if(texCoord.z>.5) {
        float disc=1.0-smoothstep(.65,.68,r);
        float cut=smoothstep(.59,.62,length(p-vec2(.26,.08)));
        mask=disc*cut+halo*.8+runes;
    }
    vec3 color=mix(vec3(.24,.15,.72),vec3(.83,.92,1.0),clamp(ring+runes+mask*.5,0.0,1.0));
    float alpha=clamp(mask,0.0,1.0)*vertexColor.a*(1.0-smoothstep(.96,1.0,r));
    fragColor=vec4(color,alpha);
}
