#version 150
uniform sampler2D Sampler0;
uniform float uTime;
uniform float Mode;
uniform float ImageRatio;
in vec4 vertexColor;
in vec3 texCoord;
out vec4 fragColor;
void main(){
    if(Mode<0.5){
        // RancorMagicCircle.fx; world geometry supplies its original perspective/squash.
        vec4 base=texture(Sampler0,texCoord.xy);
        float adjustedY=0.5+mix(-ImageRatio,ImageRatio,texCoord.y)*0.5;
        float fade=abs(sin(adjustedY+uTime*0.5));
        float luminosity=(base.r+base.g+base.b)/3.0;
        vec4 result=base*vec4(mix(vec3(1,0,0),vec3(0,0,1),fade),1);
        result*=1.0+luminosity*0.5;
        fragColor=(result*0.7+base*0.5)*base.a*vertexColor;
    }else{
        // Calamity Flame.fx; Sampler0 is the original Terraria Perlin texture.
        vec4 color=vertexColor;vec2 coords=texCoord.xy;
        coords.y=(coords.y-0.5)/texCoord.z+0.5;
        float noise=texture(Sampler0,vec2(fract(coords.y),fract(coords.x-uTime*1.4))).r*pow(coords.x,0.04);
        float opacity=mix(1.45,1.95,noise)*color.a;
        opacity*=pow(max(0.0,sin(coords.y*3.141)),mix(1.0,6.0,pow(coords.x,2.0)));
        opacity*=pow(max(0.0,sin(coords.x*3.141)),0.4);
        opacity*=(noise*1.5+1.0)*mix(0.4,0.9,noise);
        vec3 transformColor=mix(vec3(1,205.0/255.0,119.0/255.0),vec3(1,76.0/255.0,79.0/255.0),noise);
        color.rgb=mix(color.rgb,transformColor,noise);
        fragColor=color*opacity*1.6;
    }
}
