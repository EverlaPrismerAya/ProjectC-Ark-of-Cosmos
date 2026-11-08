package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.ViolenceEntity;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

public final class ViolenceRenderer extends EntityRenderer<ViolenceEntity> {
    public ViolenceRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(ViolenceEntity e){return NativeSprites.texture("item/violence");}
    @Override public boolean shouldRender(ViolenceEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(ViolenceEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        Vec3 at=vector(s,"Position"),base=e.getPosition(partial);var f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        var d=new NativeSprites.Draw(pose,buffers,base,e.ownerId());var history=s.getList("Trail",10);
        boolean javelin=s.getBoolean("Javelin");double age=s.getDouble("Age");
        float opacity=javelin?1:(float)Math.min(1,age/15);
        if(javelin&&!s.getBoolean("Returning"))d.sprite("item/violence",at,f,s.getDouble("Rotation"),1,1,71,71,WHITE,false,true,light);
        else for(int i=Math.min(5,history.size()-1);i>=0;i--){
            double alpha=(1-Math.pow(i/6d,1.4))*opacity;
            d.sprite("item/violence",at,f,history.getCompound(i).getDouble("Rotation")-Math.PI/2+(s.getBoolean("Returning")?0:.2),1,1,71,71,mul(WHITE,alpha),false,true,light,0,true);
        }
        int count=javelin?history.size():Math.min(history.size(),Math.min((int)age,36));
        if(count<5)return;
        Vec3[] points=new Vec3[count];
        for(int i=0;i<count;i++){
            var old=history.getCompound(i);points[i]=vector(old,"Position");
            if(!javelin)points[i]=points[i].subtract(f.direction(old.getDouble("Rotation")+Math.PI/4).scale(142/32d));
        }
        var matrix=new org.joml.Matrix4f(pose.last().pose());
        Runnable trail=()->trail(points,f,matrix,base,opacity,(e.level().getGameTime()+partial)/20,e.getId(),javelin?25:24);
        if(!OculusShaderCompat.defer(trail))trail.run();
    }
    private static Vec3 catmull(Vec3[] p,double t){
        double index=t*(p.length-1);int i=Math.min(p.length-2,(int)index);double v=index-i,v2=v*v,v3=v2*v;
        Vec3 b=p[i],c=p[i+1],a=i>0?p[i-1]:b.scale(2).subtract(c),d=i+2<p.length?p[i+2]:c.scale(2).subtract(b);
        return a.scale(-.5*v3+v2-.5*v).add(b.scale(1.5*v3-2.5*v2+1)).add(c.scale(-1.5*v3+2*v2+.5*v)).add(d.scale(.5*v3-.5*v2));
    }
    private static void trail(Vec3[] history,AttackFrame frame,org.joml.Matrix4f matrix,Vec3 base,float opacity,double time,int identity,int count){
        if(ArkFxDraw.trailShader==null)return;
        var previous=ArkFxDraw.matrix;var camera=ArkFxDraw.camera;ArkFxDraw.matrix=matrix;ArkFxDraw.camera=base;
        ArkFxDraw.begin("violence_streak",ArkFxDraw.Blend.ALPHA,true);
        try{
            var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.TRIANGLES,ArkFxDraw.FORMAT);
            Vec3[][] edges=new Vec3[count][2];float[][] colors=new float[count][];float[] widths=new float[count];
            for(int i=0;i<count;i++){
                double t=i/(double)(count-1);Vec3 point=catmull(history,t),next=catmull(history,Math.min(1,t+1d/count));
                Vec3 tangent=(i==count-1?point.subtract(catmull(history,Math.max(0,t-1d/count))):next.subtract(point)).normalize();
                Vec3 side=frame.normal().cross(tangent).normalize();
                double tip=net.minecraft.util.Mth.clamp((t-.01)/.03,0,1);tip=tip*tip*(3-2*tip);
                double body=Math.pow(net.minecraft.util.Mth.clamp((t-1)/(.04-1),0,1),.9);
                float width=(float)(Math.pow(tip*body,.1)*30);widths[i]=Math.max(.0001f,width);
                edges[i][0]=point.subtract(side.scale(width/16));edges[i][1]=point.add(side.scale(width/16));
                double pulse=.15+.6*(Math.cos(time*-9+t*6+identity*2)*.5+.5);
                float[] front=mix(mix(rgb(255,145,115),rgb(113,0,159),pulse),rgb(139,0,0),.5);
                colors[i]=mul(mix(front,rgb(255,145,115),Math.pow(t,1.2)),Math.pow(1-t,1.1)*opacity);
            }
            for(int i=0;i<count-1;i++)for(int index:new int[]{i*2,i*2+1,i*2+2,i*2+2,i*2+1,i*2+3}){
                int p=index/2,edge=index%2;float w=widths[p];
                ArkFxDraw.vertex(buffer,edges[p][edge],colors[p],p/(float)(count-1),.5f+(edge==0?-1:1)*w*.5f,w);
            }
            BufferUploader.drawWithShader(buffer.end());
        }finally{ArkFxDraw.end();ArkFxDraw.matrix=previous;ArkFxDraw.camera=camera;}
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void renderer(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(ArkOfCosmos.VIOLENCE_ATTACK.get(),ViolenceRenderer::new);}
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.VIOLENCE.get(),ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),(stack,level,entity,seed)->entity instanceof net.minecraft.world.entity.player.Player p&&net.everla.projectc.arkofcosmos.combat.ViolenceCombat.active(p)?1:0));}
    }
}
