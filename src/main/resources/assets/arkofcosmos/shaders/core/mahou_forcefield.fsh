#version 150
// Calamity Team SupremeShieldShader.fx, HLSL-to-GLSL port; phase palette and original health flicker.
in vec4 vertexColor;
in vec3 texCoord;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec3 uColor;
uniform vec3 uSecondaryColor;
uniform float uTime;
uniform float uHealthRatio;
uniform float uTintStrength;
uniform float uPostMusicHit;
uniform float uRandom;
out vec4 fragColor;
float inverseLerp(float x,float a,float b){return clamp((x-a)/(b-a),0.0,1.0);}
void main(){
    vec4 baseColor=texture(Sampler0,texCoord.xy);
    float noise=texture(Sampler1,vec2(texCoord.x,fract(texCoord.y+uTime*.7))).r;
    float start=.45+sin(uTime*3.0)*.04;
    float r=length(texCoord.xy-vec2(.5))*1.414;
    vec3 color=mix(uColor,uSecondaryColor,pow(r,.6));
    float alpha=pow(inverseLerp(r,0.0,start),2.6);
    float ring=sin(3.141*inverseLerp(r,start,start+.1))*1.96;
    if(r<start)alpha*=mix(1.0,2.6,noise);
    else alpha=mix(alpha,ring,inverseLerp(r,start,start+.05));
    float power=(uHealthRatio<.6?.1:0.0)+(uHealthRatio<.3?.25:0.0);
    power+=uPostMusicHit*.61;
    if(uHealthRatio<.05)power+=mix(.7,1.0,uRandom);
    if(uHealthRatio<=.01)power+=.08;
    float flicker=mix(1.0,max(1.0-power,.56),pow(abs(cos(uTime*mix(3.0,5.0,power))),24.0));
    float healthOpacity=uHealthRatio<=.01?.6:uHealthRatio<.05?.75:1.0;
    // Upstream dims the palette, not output alpha: SupremeShield ignores sampleColor.
    color*=flicker*healthOpacity*vertexColor.a;
    alpha*=mix(1.0,.56,r)*2.45*.65;
    // Preserve the upstream overbright RGB. Clamping before multiplying the palette
    // flattens both the Perlin detail and bright rim into a solid disc.
    alpha=r>=start+.1?0.0:max(alpha,0.0);
    fragColor=vec4(mix(baseColor.rgb,color,uTintStrength)*alpha,alpha);
}
