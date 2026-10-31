#version 150
in vec4 vertexColor;
in vec3 texCoord;
uniform float uTime;
uniform float uMode;
out vec4 fragColor;
void main(){
    vec2 p=texCoord.xy*2.-1.;float r=length(p),a=atan(p.y,p.x);
    vec3 c=uMode<.5?vec3(1.,.025,.06):uMode<1.5?vec3(.025,.22,1.):vec3(.65,.025,1.);
    float core=exp(-r*r*14.);float ring=exp(-pow((r-.6)*22.,2.));
    float spiral=pow(.5+.5*sin(a*5.+r*27.-uTime*5.),5.)*exp(-r*2.)*smoothstep(0.,.13,r);
    // Bright colored body, white-hot center and broad halo remain legible against daylight.
    float body=exp(-r*r*5.5)*.65;
    float halo=exp(-pow((r-.65)*7.,2.))*.65;
    float alpha=core+body+halo+ring+spiral*1.8;
    c=mix(c,vec3(1.),core*.85);
    if(uMode>2.5&&uMode<3.5){alpha=spiral*2.+ring*.35+core*.25;c=mix(c,vec3(.08,0.,.22),core);}
    if(uMode>3.5){
        float spokes=exp(-pow(sin(a*4.-uTime*4.)*35.,2.))*step(.25,r)*step(r,.85);
        alpha=exp(-pow((r-.57)*60.,2.))+spokes+exp(-r*r*170.);
        for(int i=0;i<8;i++){float q=float(i)*.785398+uTime;vec2 tip=vec2(cos(q),sin(q))*.84;alpha+=exp(-pow(length(p-tip)*24.,2.));}
        c=vec3(1.,.86,.48);
    }
    fragColor=vec4(c*(1.7+core*2.),clamp(alpha,0.,1.)*vertexColor.a*(1.-smoothstep(.92,1.,r)));
}
