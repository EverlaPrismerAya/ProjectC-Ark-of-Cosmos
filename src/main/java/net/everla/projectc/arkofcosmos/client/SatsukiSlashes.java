package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.combat.SatsukiDomain;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client presentation of 领域展开·Fumo御厨子. Purely visual: damage is still owned by the installed GR/RF routine.
 * Layers: fine ambient cuts, long sweeping "dismantle" cuts, parallel claw clusters, ground scars with debris,
 * hit cuts on damaged entities, plus a red screen cast, screen-space cuts, camera shake and slicing audio.
 */
@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class SatsukiSlashes {
    /** reveal: ticks the cut takes to "unzip" from one end to the other. */
    private record Slash(Vec3 at,AttackFrame plane,double length,double width,int life,double reveal,boolean refract,
                         int born,int target,Vec3 anchor) {}
    private record ScreenCut(double angle,double offsetX,double offsetY,double length,double thickness,int born) {}
    private static final List<Slash> SLASHES=new ArrayList<>();
    private static final List<ScreenCut> SCREEN=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static Object world;
    private static int clock;
    /** Strength (0–1) of the domain around the camera, smoothed per tick for the screen layers. */
    private static float presence,lastPresence;
    private static void world(){
        var current=Minecraft.getInstance().level;
        if(world!=current){SLASHES.clear();SCREEN.clear();clock=0;presence=lastPresence=0;world=current;SatsukiRefraction.release();}
    }
    private static Vec3 randomDirection(){
        double y=RANDOM.nextDouble()*2-1,angle=RANDOM.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);
        return new Vec3(r*Math.cos(angle),y,r*Math.sin(angle));
    }
    /** Mostly lateral sweeps, the way the shrine's cuts rake across the ground. */
    private static Vec3 sweepDirection(){
        double y=(RANDOM.nextDouble()*2-1)*.38,angle=RANDOM.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);
        return new Vec3(r*Math.cos(angle),y,r*Math.sin(angle));
    }
    private static AttackFrame frame(Vec3 direction){
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        double roll=RANDOM.nextDouble()*Math.PI*2;
        side=side.scale(Math.cos(roll)).add(direction.cross(side).scale(Math.sin(roll)));
        return new AttackFrame(direction,side);
    }
    private static void add(Vec3 at,AttackFrame plane,double length,double width,int life,double reveal,boolean refract){
        SLASHES.add(new Slash(at,plane,length,width,life,reveal,refract,clock,-1,Vec3.ZERO));
    }
    public static void receive(CompoundTag data){
        world();if(world==null)return;
        var random=new Random(data.getLong("Seed"));
        double length=Math.max(2.4,Math.min(10,data.getFloat("Size")*2.5));
        Vec3 at=new Vec3(data.getDouble("X"),data.getDouble("Y"),data.getDouble("Z"));
        for(int i=0;i<3;i++)SLASHES.add(new Slash(at,frame(randomDirection()),length,.26,9,.8+i*.6,true,clock,data.getInt("Entity"),
                new Vec3(.05+random.nextDouble()*.9,.05+random.nextDouble()*.9,.05+random.nextDouble()*.9)));
        var mc=Minecraft.getInstance();
        if(mc.level!=null){
            mc.level.playLocalSound(at.x,at.y,at.z,ArkOfCosmos.audio("true_biome_slice"),SoundSource.HOSTILE,.9f,.9f+RANDOM.nextFloat()*.5f,false);
            for(int i=0;i<6;i++)mc.level.addParticle(ParticleTypes.CRIT,at.x,at.y,at.z,RANDOM.nextGaussian()*.5,RANDOM.nextGaussian()*.5,RANDOM.nextGaussian()*.5);
            if(mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at)<16*16)ArkEffects.addShake(4);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        world();var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        clock++;SLASHES.removeIf(s->clock-s.born()>=s.life());SCREEN.removeIf(c->clock-c.born()>=5);
        lastPresence=presence;
        float inside=0;
        if(net.everla.projectc.arkofcosmos.SatsukiContent.ENABLED){
            Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof SatsukiRinEntity rin&&SatsukiDomain.active(rin)){
                var box=SatsukiDomain.bounds(rin);
                boolean here=box.contains(eye);
                if(here)inside=Math.max(inside,SatsukiDomain.scale(rin));
                ambient(mc,box,here?eye:null);
            }
        }
        presence+=(inside-presence)*(inside>presence?.35f:.12f);
        if(inside>0)immersion(mc,inside);
    }
    private static void ambient(Minecraft mc,AABB box,Vec3 eye){
        // Fine cuts: half spread over the whole domain, half crowding the viewer so the storm is always visible.
        for(int i=0;i<36;i++){
            Vec3 at=eye!=null&&i%2==0?near(box,eye,14):inBox(box);
            add(at,frame(randomDirection()),1.6+RANDOM.nextDouble()*2.4,.12,6,.6,false);
        }
        // Long sweeping cuts that cross the whole field of view.
        for(int i=0;i<5;i++){
            Vec3 at=eye!=null&&i<3?near(box,eye,22):inBox(box);
            double length=14+RANDOM.nextDouble()*22;
            add(at,frame(sweepDirection()),length,.30,9,1.6,true);
            if(eye!=null&&at.distanceToSqr(eye)<9*9)ArkEffects.addShake(3.5);
        }
        // Claw clusters: three or four parallel cuts landing together.
        for(int c=0;c<2;c++){
            Vec3 center=eye!=null?near(box,eye,12):inBox(box);
            AttackFrame plane=frame(sweepDirection());Vec3 across=plane.right();
            int count=3+RANDOM.nextInt(2);double length=5+RANDOM.nextDouble()*5,gap=.55+RANDOM.nextDouble()*.35;
            for(int i=0;i<count;i++){
                Vec3 at=center.add(across.scale((i-(count-1)/2.0)*gap)).add(plane.forward().scale((RANDOM.nextDouble()-.5)*.8));
                add(at,plane,length*(.8+RANDOM.nextDouble()*.3),.22,8,1+i*.35,i==count/2);
            }
        }
        if(eye!=null)groundScars(mc,box,eye);
    }
    /** Flat cuts carved into the terrain, throwing up block debris. */
    private static void groundScars(Minecraft mc,AABB box,Vec3 eye){
        var level=mc.level;
        for(int i=0;i<4;i++){
            double x=eye.x+(RANDOM.nextDouble()*2-1)*20,z=eye.z+(RANDOM.nextDouble()*2-1)*20;
            if(x<box.minX||x>box.maxX||z<box.minZ||z>box.maxZ)continue;
            int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
            if(!level.hasChunkAt(new BlockPos(bx,0,bz)))continue;
            int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx,bz);
            if(top<box.minY||top>box.maxY)continue;
            var ground=new BlockPos(bx,top-1,bz);var state=level.getBlockState(ground);
            if(state.isAir())continue;
            double yaw=RANDOM.nextDouble()*Math.PI*2;
            Vec3 forward=new Vec3(Math.cos(yaw),0,Math.sin(yaw)),right=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
            Vec3 at=new Vec3(x,top+.03,z);double length=3+RANDOM.nextDouble()*6;
            add(at,new AttackFrame(forward,right),length,.28,12,1.2,false);
            var debris=new BlockParticleOption(ParticleTypes.BLOCK,state);
            for(int p=0;p<10;p++){
                Vec3 along=at.add(forward.scale((RANDOM.nextDouble()-.5)*length));
                level.addParticle(debris,along.x,along.y+.1,along.z,RANDOM.nextGaussian()*.12,.25+RANDOM.nextDouble()*.35,RANDOM.nextGaussian()*.12);
            }
        }
    }
    /** Viewer-only layers: screen cuts, camera shake and the slicing soundscape. */
    private static void immersion(Minecraft mc,float strength){
        ArkEffects.addShake(1.4*strength);
        if(RANDOM.nextFloat()<.45f*strength)
            SCREEN.add(new ScreenCut(RANDOM.nextDouble()*Math.PI,(RANDOM.nextDouble()-.5)*.6,(RANDOM.nextDouble()-.5)*.6,
                    .5+RANDOM.nextDouble()*.7,1.5+RANDOM.nextDouble()*2.5,clock));
        Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
        for(int i=0;i<2;i++){
            Vec3 at=eye.add(randomDirection().scale(2+RANDOM.nextDouble()*7));
            mc.level.playLocalSound(at.x,at.y,at.z,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.35f*strength,1.4f+RANDOM.nextFloat()*.6f,false);
        }
        if(RANDOM.nextFloat()<.3f){
            Vec3 at=eye.add(randomDirection().scale(4+RANDOM.nextDouble()*8));
            mc.level.playLocalSound(at.x,at.y,at.z,ArkOfCosmos.audio("fractured_swing"),SoundSource.HOSTILE,.45f*strength,1.2f+RANDOM.nextFloat()*.6f,false);
        }
    }
    private static Vec3 inBox(AABB box){return new Vec3(between(box.minX,box.maxX),between(box.minY,box.maxY),between(box.minZ,box.maxZ));}
    private static Vec3 near(AABB box,Vec3 eye,double radius){
        Vec3 at=eye.add(randomDirection().scale(Math.cbrt(RANDOM.nextDouble())*radius));
        return new Vec3(Math.max(box.minX,Math.min(box.maxX,at.x)),Math.max(box.minY,Math.min(box.maxY,at.y)),Math.max(box.minZ,Math.min(box.maxZ,at.z)));
    }
    private static double between(double min,double max){return min+(max-min)*RANDOM.nextDouble();}

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||SLASHES.isEmpty())return;
        ArkFxDraw.matrix=event.getPoseStack().last().pose();ArkFxDraw.camera=event.getCamera().getPosition();
        // The existing retained-depth pass also handles actual Oculus shader packs.
        var snapshot=List.copyOf(SLASHES);float partial=event.getPartialTick();int now=clock;
        if(!OculusShaderCompat.defer(()->draw(snapshot,now,partial),true))draw(snapshot,now,partial);
    }
    /** Resolved geometry for one frame of one cut. */
    private record Shape(Vec3 from,Vec3 to,Vec3 side,float alpha,float flash) {}
    private static void draw(List<Slash> slashes,int now,float partial){
        SatsukiRefraction.capture();
        ArkFxDraw.ownerView(-1,Vec3.ZERO);
        var mc=Minecraft.getInstance();
        var camera=mc.gameRenderer.getMainCamera();var look=camera.getLookVector();var up=camera.getUpVector();
        Vec3 normal=new Vec3(look.x(),look.y(),look.z()),vertical=new Vec3(up.x(),up.y(),up.z());
        var face=new AttackFrame(normal.cross(vertical).normalize(),vertical);
        List<Shape> shapes=new ArrayList<>(slashes.size());
        for(var s:slashes){
            double age=now-s.born()+partial;
            Vec3 at=s.at();AttackFrame plane=s.plane();
            if(s.target()>=0){
                var target=mc.level==null?null:mc.level.getEntity(s.target());
                if(target!=null){
                    var box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
                    at=new Vec3(box.minX+box.getXsize()*s.anchor().x,box.minY+box.getYsize()*s.anchor().y,box.minZ+box.getZsize()*s.anchor().z);
                    // Project the random interior anchor onto the visible box surface; never recenter the cut.
                    at=box.clip(ArkFxDraw.camera,at).orElse(at);
                    at=at.add(ArkFxDraw.camera.subtract(at).normalize().scale(.015));
                }
                double angle=Math.atan2(s.plane().forward().y,s.plane().forward().x);
                plane=new AttackFrame(face.direction(angle),face.direction(angle+Math.PI/2));
            }
            if(age<s.reveal()*.15)continue;
            double reveal=Math.min(1,age/s.reveal());
            // Width snaps open, then pinches shut; the line itself lingers like a fresh wound.
            double open=Math.min(1,age/1.2),close=Math.max(0,1-Math.max(0,age-s.reveal())/Math.max(1,s.life()-s.reveal()));
            double width=s.width()*open*close;
            double length=s.length()*(.7+.3*close);
            Vec3 start=at.subtract(plane.forward().scale(length/2));
            Vec3 end=start.add(plane.forward().scale(length*reveal));
            float alpha=(float)Math.max(0,Math.min(1,(s.life()-age)/2));
            float flash=(float)Math.max(0,1-Math.max(0,age-reveal*s.reveal())/2.5);
            if(s.refract()){
                float strength=(float)Math.sin(Math.PI*Math.max(0,Math.min(1,age/s.life())));
                SatsukiRefraction.draw(start.add(end).scale(.5),plane,length*reveal,strength);
            }
            shapes.add(new Shape(start,end,plane.right().scale(width/2),alpha,flash));
        }
        // Batched: one draw per layer instead of one per cut.
        batch(shapes,ArkFxDraw.Blend.ADDITIVE,4.5,1.04,s->new float[]{.8f,0,0,.55f*s.flash()*s.alpha()});
        batch(shapes,ArkFxDraw.Blend.NONPREMULTIPLIED,1.7,1.04,s->new float[]{2f/3,0,0,s.alpha()});
        batch(shapes,ArkFxDraw.Blend.NONPREMULTIPLIED,1,1,s->new float[]{0,0,0,s.alpha()});
        batch(shapes,ArkFxDraw.Blend.ADDITIVE,.35,1.08,s->new float[]{1,.85f,.85f,s.flash()*s.alpha()});
    }
    private static void batch(List<Shape> shapes,ArkFxDraw.Blend blend,double widthScale,double lengthScale,
                              java.util.function.Function<Shape,float[]> color){
        ArkFxDraw.begin("thinendedline",blend,false);
        try{
            var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
            boolean any=false;
            for(var s:shapes){
                float[] c=color.apply(s);if(c[3]<=.004f)continue;
                Vec3 mid=s.from().add(s.to()).scale(.5),half=s.to().subtract(s.from()).scale(.5*lengthScale),side=s.side().scale(widthScale);
                // Sample the opaque center: a solid tapered silhouette rather than a soft glow mask.
                for(var point:List.of(mid.subtract(half),mid.add(side),mid.add(half),mid.subtract(side)))
                    ArkFxDraw.vertex(buffer,point,c,.5f,.5f,1);
                any=true;
            }
            var built=buffer.end();
            if(any)BufferUploader.drawWithShader(built);else built.release();
        }finally{ArkFxDraw.end();}
    }

    /** Screen layers, drawn from the HUD pass (also when the HUD is hidden). */
    static void screen(GuiGraphics gui,float partial){
        float strength=lastPresence+(presence-lastPresence)*partial;
        if(strength<.01f&&SCREEN.isEmpty())return;
        int w=gui.guiWidth(),h=gui.guiHeight();
        // Blood-red cast plus a dark crimson vignette closing in from the edges.
        int tint=(int)(strength*40);
        if(tint>0)gui.fill(0,0,w,h,(tint<<24)|0x5A0000);
        int steps=14;double depth=Math.min(w,h)*.22;
        for(int i=0;i<steps;i++){
            int a=(int)(strength*(1-i/(double)steps)*26);if(a<=0)continue;
            int inset=(int)(depth*i/steps),band=(int)Math.ceil(depth/steps);
            int color=(a<<24)|0x1A0000;
            gui.fill(inset,inset,w-inset,inset+band,color);
            gui.fill(inset,h-inset-band,w-inset,h-inset,color);
            gui.fill(inset,inset+band,inset+band,h-inset-band,color);
            gui.fill(w-inset-band,inset+band,w-inset,h-inset-band,color);
        }
        double diagonal=Math.hypot(w,h);
        for(var c:SCREEN){
            double age=clock-c.born()+partial;
            double reveal=Math.min(1,age/1.2);
            float alpha=(float)Math.max(0,Math.min(1,(5-age)/2.5));
            int a=(int)(alpha*255);if(a<4)continue;
            double length=diagonal*c.length();
            float thick=(float)(c.thickness()*Math.max(.15,1-Math.max(0,age-1.2)/3.8));
            var pose=gui.pose();pose.pushPose();
            pose.translate(w/2f+c.offsetX()*w,h/2f+c.offsetY()*h,0);
            pose.mulPose(Axis.ZP.rotation((float)c.angle()));
            pose.scale((float)(length/1000),1,1);
            int x0=-500,x1=(int)(-500+1000*reveal);
            gui.fill(x0,(int)Math.floor(-thick-1),x1,(int)Math.ceil(thick+1),(a<<24)|0xAA0000);
            gui.fill(x0,(int)Math.floor(-thick),x1,(int)Math.ceil(thick),a<<24);
            if(age<2.5){
                int fa=(int)(a*(1-age/2.5));
                gui.fill(x0,0,x1,1,(fa<<24)|0xFFE0E0);
            }
            pose.popPose();
        }
    }
}
