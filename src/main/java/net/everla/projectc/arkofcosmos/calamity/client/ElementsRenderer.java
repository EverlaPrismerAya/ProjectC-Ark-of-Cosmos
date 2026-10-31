package net.everla.projectc.arkofcosmos.calamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;
import net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

/** Source PreDraw, BasicTint and XNA blend states using original sprites. */
public final class ElementsRenderer extends EntityRenderer<ElementsEntity> {
    public ElementsRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(ElementsEntity e){return NativeSprites.texture("elements/rending_scissors_right");}
    @Override public boolean shouldRender(ElementsEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(ElementsEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        Vec3 base=new Vec3(net.minecraft.util.Mth.lerp(partial,e.xOld,e.getX()),net.minecraft.util.Mth.lerp(partial,e.yOld,e.getY()),net.minecraft.util.Mth.lerp(partial,e.zOld,e.getZ()));
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=base;ArkFxDraw.ownerView(e.ownerId(),Vec3.ZERO);
        try {
        Draw d=new Draw(new NativeSprites.Draw(pose,buffers,base,e.ownerId()));
        AttackFrame f=new AttackFrame(vector(s,"F"),vector(s,"R"));Vec3 p=vector(s,"Position"),c=vector(s,"Center");
        double r=s.getDouble("Rotation"),scale=s.getDouble("Scale"),age=s.getDouble("Age"),left=s.getDouble("Left"),time=(e.level().getGameTime()+partial)/20;
        int kind=s.getInt("Kind"),mode=s.getInt("Mode");boolean charged=s.getInt("Charge")>0;
        if(kind==BLADE){
            if(mode<2){
                Vec3 origin=c.add(f.direction(r).scale(10/16d));
                if(age>10){int i=0;for(CompoundTag old:ElementsEffects.history(e.getId())){
                    if(old.getInt("Age")>=age)continue;if(i>=10)break;
                    double oldR=old.getDouble("Rotation"),size=scale-.2*i/10d;
                    float[] color=mul(hsl(i/10d*.1,charged?.9:.6),.15);
                    if(charged){Vec3 other=origin.add(f.direction(r).scale(70/16d*scale));sprite(d,"rending_scissors_left_glow",other,f,oldR+Math.PI/4+(mode==1?Math.PI*3/16:0),size,size,20,109,color,false,false,15728880);}
                    sprite(d,"rending_scissors_"+(mode==1&&!charged?"left":"right")+"_glow",origin,f,oldR+(mode==1&&!charged?Math.PI/2:Math.PI/4),size,size,mode==1&&!charged?NativeSprites.width("elements/rending_scissors_left")/2d:0,NativeSprites.height("elements/rending_scissors_"+(mode==1&&!charged?"left":"right")),color,false,false,15728880);i++;
                }}
                if(charged){Vec3 other=origin.add(f.direction(r).scale(70/16d*scale));blade(d,other,f,r+Math.PI/4+(mode==1?Math.PI*3/16:0),scale,true,20,109,light);blade(d,origin,f,r+Math.PI/4,scale,false,0,NativeSprites.height("elements/rending_scissors_right"),light);}
                else blade(d,origin,f,r+(mode==1?Math.PI/2:Math.PI/4),scale,mode==1,mode==1?NativeSprites.width("elements/rending_scissors_left")/2d:0,NativeSprites.height("elements/rending_scissors_"+(mode==1?"left":"right")),light);
                double t=age/35;
                if(t>.5){double offset=(-Math.PI/8+Math.PI/8*t+(mode==1?Math.PI/4:0))*(mode==0?1:-1);d.centered("fx/trientcircularsmear",c,f,f.angle(vector(s,"Direction"))+Math.PI+offset,scale*2.3,scale*2.3,mul(hsl((age-17.5)/17.5*.15+(mode==1?.85:0),.6),.5*Math.sin(t*Math.PI)),true);}
            }else{
                double snap=(20-left)/20,throwT=(80-left)/80;
                if(charged){double a=mode==3?lerp(-.2591814,Math.PI*17/40,clamp(snap+.5)):lerp(0,-.2591814,clamp(throwT*2));blade(d,p,f,r+a,scale,true,22,109,light);}
                else if(mode==3){double t=clamp(snap+.25);t=t*t*(3-2*t);blade(d,c.lerp(p,t),f,f.angle(vector(s,"Direction"))+Math.PI/2,scale,true,22,109,light);}
                blade(d,p,f,r+Math.PI/4,scale,false,51,86,light);
                double opacity=mode==3?Math.cos(snap*Math.PI/2):Math.sin(throwT*Math.PI);
                d.centered("fx/trientcircularsmear",p,f,r+Math.PI,scale*1.4,scale*1.4,mul(hsl((35-left-17.5)/17.5*.15,.6),.5*opacity),true);
            }
        }else if(kind==PARRY){
            double t=age/15,close=ArkCurves.parryClose((float)t),thrust=ArkCurves.parryThrust((float)t);
            Vec3 at=c.add(f.forward().scale((15+thrust*50)/16));
            blade(d,at,f,lerp(Math.PI*3/16,Math.PI*7/16,close),scale,true,22,109,light);
            blade(d,at,f,lerp(Math.PI/2,Math.PI/4,close),scale,false,51,86,light);
        }else if(kind==BLAST){
            Vec3 lineOrigin=p.subtract(f.right().scale(10/16d));
            double snap=clamp(age/25),hold=clamp((age-25)/15),stitch=clamp((age-32.5)/37.5),length=ElementsRules.thrust(age)*242;
            d.sprite("fx/bloomline",lineOrigin,f,Math.PI/2,.2*(1-snap),length,NativeSprites.width("fx/bloomline")/2d,NativeSprites.height("fx/bloomline"),mix(ORANGE_RED,WHITE,snap),true,false,15728880);
            if(hold<=.4){double close=ArkCurves.blastClose((float)snap),fade=(.4-hold)/.4;Vec3 at=p.add(f.forward().scale(ElementsRules.thrust(age)*200/16d));
                sprite(d,"rending_scissors_left",at,f,lerp(Math.PI*3/16,Math.PI*7/16,close)+Math.toRadians(6),1.4,1.4,22,109,mul(rgb(255,105,180),fade*.9),true,false,15728880);
                sprite(d,"rending_scissors_right",at,f,lerp(Math.PI/2,Math.PI/4,close)+Math.toRadians(6),1.4,1.4,51,86,mul(rgb(255,165,0),fade*fade*.9),true,false,15728880);
            }
            if(hold>0){double fade=stitch<.75?1:1-(stitch-.75)*4;
                Vec3 stitchedOrigin=lineOrigin.add(hold>.2?Vec3.ZERO:f.direction(Math.PI/4+range(-Math.PI*2,Math.PI*2)).scale(Math.sqrt(2)*(1-hold*5)*.5/16));
                d.sprite("fx/thinendedline",stitchedOrigin,f,Math.PI/2,.2*fade,length/NativeSprites.height("fx/thinendedline"),NativeSprites.width("fx/thinendedline")/2d,NativeSprites.height("fx/thinendedline"),mul(mix(WHITE,mul(ORANGE_RED,.7),Math.sin(hold*Math.PI/2)),fade),true,false,15728880);
                var angles=s.getList("Angles",5);for(int i=0;i<ElementsRules.stitches(age);i++){
                    double life=Math.max(1,age-ElementsRules.stitchBorn(i)+1),t=clamp(life/7),size=(1+Math.sin(t*Math.PI)*.3)*.85;
                    if(ElementsRules.stitches(age)==8)size*=1-(age-32.5-11.25)/37.5*.7*.8;
                    Vec3 at=stitchedOrigin.add(f.forward().scale(length*(i+.5)/8/16));
                    d.centered("fx/thinendedline",at,f,Math.PI/2+angles.getFloat(i),.2*size,(Math.sin(i/7d*Math.PI)*.5+.5)*size,mix(WHITE,mul(CORNFLOWER,.7),Math.sin(t*Math.PI/2)),true);
                }
            }
        }else{
            Vec3 v=vector(s,"Velocity").normalize(),side=f.normal().cross(v).normalize();if(side.lengthSqr()<.5)side=f.right();AttackFrame flight=new AttackFrame(v,side);
            String name=kind==NEEDLE?"solar_needle":"elemental_glass_star";int cache=kind==NEEDLE?5:6,index=0;
            if(kind==NEEDLE&&charged){
                float[] tintColor=needleTint(age);ArkFxDraw.Tint tint=new ArkFxDraw.Tint(tintColor[0],tintColor[1],tintColor[2],1);
                int[] dimensions=ArkFxDraw.size("elements/solar_needle");
                for(int i=0;i<8;i++)ArkFxDraw.sprite("elements/solar_needle",p.add(flight.direction(i*Math.PI/4+Math.PI/2).scale(needleOutlineRadius(age)/16)),flight,Math.PI/2,scale,scale,dimensions[0]/2d,dimensions[1]/2d,mix(WHITE,ORANGE_RED,age/30),ArkFxDraw.Blend.ALPHA,0,0,dimensions[0],dimensions[1],tint);
            }
            for(CompoundTag old:ElementsEffects.history(e.getId())){if(old.getInt("Age")>=age)continue;if(index>=cache)break;Vec3 at=vector(old,"Position");double angle=kind==NEEDLE?Math.PI/2:r;
                d.centered("elements/"+name,at,kind==NEEDLE?flight:f,angle,scale,scale,mul(WHITE,(cache-index)/(double)cache*(kind==NEEDLE?.6:1)),false);index++;
            }
            d.sprite("elements/"+name,p,kind==NEEDLE?flight:f,kind==NEEDLE?Math.PI/2:r,scale,scale,NativeSprites.width("elements/"+name)/2d,NativeSprites.height("elements/"+name)/2d,WHITE,false,true,15728880);
            double glowScale=kind==NEEDLE?scale/2.4:1;
            Vec3 at=kind==NEEDLE?p.subtract(v.scale(30.5/16d*glowScale)):p;AttackFrame face=NativeSprites.billboard();
            float[] color=kind==NEEDLE?hsl(time*.6,.85):hsl(Math.sin(time*15)*.05+.08,.55);double ratio=NativeSprites.height("fx/sparkle")/(double)NativeSprites.height("fx/bloomcircle");
            d.centered("fx/bloomcircle",at,face,0,4*ratio*glowScale,4*ratio*glowScale,mul(color,kind==NEEDLE?.5:.6),true);
            d.centered("fx/sparkle",at,face,time*8+Math.PI/4,1.5*glowScale,1.5*glowScale,mul(color,kind==NEEDLE?.5:1),true);d.centered("fx/sparkle",at,face,time*8,2*glowScale,2*glowScale,WHITE,true);
        }
        } finally {ArkFxDraw.fadeEye=null;}
    }
    public static double needleOutlineRadius(double age){return Math.max(0,Math.min(3,age/30*4));}
    public static float[] needleTint(double age){
        float[] color=mix(WHITE,ORANGE_RED,age/30);double max=Math.max(color[0],Math.max(color[1],color[2])),min=Math.min(color[0],Math.min(color[1],color[2])),delta=max-min;
        double light=(max+min)/2,saturation=delta==0?0:delta/(1-Math.abs(2*light-1));
        double hue=delta==0?0:(max==color[0]?(color[1]-color[2])/delta:max==color[1]?(color[2]-color[0])/delta+2:(color[0]-color[1])/delta+4)/6;
        // Main.rgbToHsl then Main.hslToRgb(1-h,s,l); do not replace with RGB inversion.
        float[] tinted=hsl(1-hue,light);
        return mix(new float[]{(float)light,(float)light,(float)light,1},tinted,saturation);
    }
    private record Draw(NativeSprites.Draw nativeDraw){
        void centered(String name,Vec3 p,AttackFrame f,double r,double sx,double sy,float[] color,boolean add){sprite(name,p,f,r,sx,sy,NativeSprites.width(name)/2d,NativeSprites.height(name)/2d,color,add,false,15728880);}
        void sprite(String name,Vec3 p,AttackFrame f,double r,double sx,double sy,double ox,double oy,float[] color,boolean add,boolean thick,int light){
            if(thick&&!name.endsWith("_glow")){nativeDraw.sprite(name,p,f,r,sx,sy,ox,oy,color,false,true,light);if(nativeDraw.buffers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source)source.endBatch();return;}
            String texture=name.startsWith("fx/")?name.substring(3):name;
            if(thick){
                Vec3 depth=f.normal().scale(1.5*sx/16+.002);
                ArkFxDraw.sprite(texture,p.add(depth),f,r,sx,sy,ox,oy,color,ArkFxDraw.Blend.ALPHA);
                ArkFxDraw.sprite(texture,p.subtract(depth),f,r,sx,sy,ox,oy,color,ArkFxDraw.Blend.ALPHA);
            }else ArkFxDraw.sprite(texture,p,f,r,sx,sy,ox,oy,color,add?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA);
        }
    }
    private static void blade(Draw d,Vec3 p,AttackFrame f,double r,double scale,boolean left,double x,double y,int light){String name="rending_scissors_"+(left?"left":"right");sprite(d,name,p,f,r,scale,scale,x,y,WHITE,false,true,light);sprite(d,name+"_glow",p,f,r,scale,scale,x,y,WHITE,false,true,15728880);}
    private static void sprite(Draw d,String name,Vec3 p,AttackFrame f,double r,double sx,double sy,double x,double y,float[] color,boolean add,boolean thick,int light){d.sprite("elements/"+name,p,f,r,sx,sy,x,y,color,add,thick,light);}
}
