package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.combat.SatsukiDomain;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class SatsukiSlashes {
    private record Slash(Vec3 at,AttackFrame plane,double length,int born,int target,Vec3 anchor) {}
    private static final List<Slash> SLASHES=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static Object world;
    private static int clock;
    private static void world(){
        var current=Minecraft.getInstance().level;
        if(world!=current){SLASHES.clear();clock=0;world=current;SatsukiRefraction.release();}
    }
    private static void add(Vec3 at,double length){
        add(at,length,-1,Vec3.ZERO);
    }
    private static void add(Vec3 at,double length,int target,Vec3 anchor){
        double y=RANDOM.nextDouble()*2-1,angle=RANDOM.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);
        Vec3 direction=new Vec3(r*Math.cos(angle),y,r*Math.sin(angle));
        Vec3 side=direction.cross(Math.abs(y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        side=side.scale(Math.cos(angle)).add(direction.cross(side).scale(Math.sin(angle)));
        SLASHES.add(new Slash(at,new AttackFrame(direction,side),length,clock,target,anchor));
    }
    public static void receive(CompoundTag data){
        world();if(world==null)return;
        var random=new Random(data.getLong("Seed"));
        for(int i=0;i<3;i++)add(new Vec3(data.getDouble("X"),data.getDouble("Y"),data.getDouble("Z")),data.getFloat("Size")*2.5,
                data.getInt("Entity"),new Vec3(.05+random.nextDouble()*.9,.05+random.nextDouble()*.9,.05+random.nextDouble()*.9));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        world();var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        clock++;SLASHES.removeIf(s->clock-s.born()>=(s.target()>=0?8:6));
        if(!net.everla.projectc.arkofcosmos.SatsukiContent.ENABLED)return;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof SatsukiRinEntity rin&&SatsukiDomain.active(rin)){
            var box=SatsukiDomain.bounds(rin);
            for(int i=0;i<48;i++)add(new Vec3(randomBetween(box.minX,box.maxX),randomBetween(box.minY,box.maxY),randomBetween(box.minZ,box.maxZ)),1.6+RANDOM.nextDouble()*2.4);
        }
    }
    private static double randomBetween(double min,double max){return min+(max-min)*RANDOM.nextDouble();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||SLASHES.isEmpty())return;
        ArkFxDraw.matrix=event.getPoseStack().last().pose();ArkFxDraw.camera=event.getCamera().getPosition();
        // The existing retained-depth pass also handles actual Oculus shader packs.
        var snapshot=List.copyOf(SLASHES);float partial=event.getPartialTick();int now=clock;
        if(!OculusShaderCompat.defer(()->draw(snapshot,now,partial),true))draw(snapshot,now,partial);
    }
    private static void draw(List<Slash> slashes,int now,float partial){
        SatsukiRefraction.capture();
        ArkFxDraw.ownerView(-1,Vec3.ZERO);
        for(var s:slashes){
            int life=s.target()>=0?8:6;
            float alpha=Math.max(0,Math.min(1,(life-(now-s.born()+partial))/2));
            Vec3 at=s.at();AttackFrame plane=s.plane();
            if(s.target()>=0){
                var mc=Minecraft.getInstance();var target=mc.level==null?null:mc.level.getEntity(s.target());
                if(target!=null){
                    var box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
                    at=new Vec3(box.minX+box.getXsize()*s.anchor().x,box.minY+box.getYsize()*s.anchor().y,box.minZ+box.getZsize()*s.anchor().z);
                    // Project the random interior anchor onto the visible box surface; never recenter the cut.
                    at=box.clip(ArkFxDraw.camera,at).orElse(at);
                    at=at.add(ArkFxDraw.camera.subtract(at).normalize().scale(.015));
                }
                var camera=mc.gameRenderer.getMainCamera();var look=camera.getLookVector();var up=camera.getUpVector();
                Vec3 normal=new Vec3(look.x(),look.y(),look.z()),vertical=new Vec3(up.x(),up.y(),up.z());
                var face=new AttackFrame(normal.cross(vertical).normalize(),vertical);
                double angle=Math.atan2(s.plane().forward().y,s.plane().forward().x);
                plane=new AttackFrame(face.direction(angle),face.direction(angle+Math.PI/2));
            }
            double shrink=Math.max(0,1-(now-s.born()+partial)/life);
            double width=(s.target()>=0?.16:.12)*shrink;
            double length=s.target()>=0?Math.max(2.4,Math.min(10,s.length())):s.length();
            length*=shrink;
            float strength=(float)Math.sin(Math.PI*Math.max(0,Math.min(1,(now-s.born()+partial)/life)));
            SatsukiRefraction.draw(at,plane,length,strength);
            // A narrow, non-emissive rim keeps the black core legible against dark mobs and fog.
            cut(at,plane,width*1.65,length*1.04,new float[]{2f/3,0,0,alpha});
            cut(at,plane,width,length,new float[]{0,0,0,alpha});
        }
    }
    private static void cut(Vec3 at,AttackFrame plane,double width,double length,float[] color){
        ArkFxDraw.begin("thinendedline",ArkFxDraw.Blend.NONPREMULTIPLIED,false);
        try{
            Vec3 tip=plane.forward().scale(length/2),side=plane.right().scale(width/2);
            var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
            // Sample the opaque center, giving the cut a solid tapered silhouette instead of a faint glow mask.
            for(var point:List.of(at.subtract(tip),at.add(side),at.add(tip),at.subtract(side)))
                ArkFxDraw.vertex(buffer,point,color,.5f,.5f,1);
            BufferUploader.drawWithShader(buffer.end());
        }finally{ArkFxDraw.end();}
    }
}
