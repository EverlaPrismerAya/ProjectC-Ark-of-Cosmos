package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.everla.projectc.arkofcosmos.combat.FumoAppearance;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Satsuki Rin's invulnerability visuals, drawn from her renderer at the model's render origin.
 * <ul>
 * <li>Shield: Zhongli's Jade Shield (amber bubble, fresnel rim, Geo hexagonal surface lines, sweeping glint,
 *     orbiting fragments around the lower half, pop-in / shatter) with the brown rock fragments replaced by golden notes.</li>
 * <li>Links: flowing red note lines from her to each of her Reimu-Fumo obsidian monoliths.</li>
 * </ul>
 */
public final class SatsukiShieldVisuals {
    private SatsukiShieldVisuals(){}
    private static final ResourceLocation NOTE=ResourceLocation.fromNamespaceAndPath("minecraft","textures/particle/note.png");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/misc/white.png");
    private static final int FULL_BRIGHT=15728880;
    private static final float FORM=8,BREAK=7;
    private static final int LON=64,LAT=32,FRAGMENTS=8;

    private static final class State {boolean on;long changed=Long.MIN_VALUE/2;long linksTick=Long.MIN_VALUE;List<Entity> links=List.of();}
    private static final Map<SatsukiRinEntity,State> STATES=new WeakHashMap<>();

    public static void render(SatsukiRinEntity rin,float partial,PoseStack pose,MultiBufferSource buffers){
        State state=STATES.computeIfAbsent(rin,k->new State());
        long now=rin.level().getGameTime();float time=now+partial;
        boolean on=rin.shielded();
        if(on!=state.on){state.on=on;state.changed=now;}
        float since=time-state.changed;
        Quaternionf facing=Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        Vec3 origin=rin.getPosition(partial);
        if(on||since<BREAK)shield(rin,pose,buffers,facing,origin,time,on,since);
        links(rin,state,pose,buffers,facing,origin,partial,time,now);
    }

    /** Culling bounds that also cover the monolith links. */
    public static AABB bounds(SatsukiRinEntity rin){
        AABB box=rin.getBoundingBox().inflate(1.5);
        State state=STATES.get(rin);
        if(state!=null)for(Entity link:state.links)box=box.minmax(link.getBoundingBox());
        return box;
    }

    // ------------------------------------------------------------------ Jade Shield
    private static void shield(SatsukiRinEntity rin,PoseStack pose,MultiBufferSource buffers,Quaternionf facing,Vec3 origin,float time,boolean on,float since){
        float radius=Math.max(rin.getBbWidth(),rin.getBbHeight())*.66f;
        Vec3 center=new Vec3(0,rin.getBbHeight()*.5,0);
        // Formation: quick pop with slight overshoot. Break: expand and fade out while fragments fly off.
        float scale,alpha,burst=0;
        if(on){float t=Math.min(1,since/FORM);scale=(float)backOut(t);alpha=Math.min(1,t*1.6f);}
        else{float t=Math.min(1,since/BREAK);scale=1+.25f*t;alpha=1-t;burst=t;}
        if(alpha<=.01f||scale<=.01f)return;
        Vec3 camera=Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition().subtract(origin.add(center));
        sphere(pose,buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)),center,radius*scale,camera,time,alpha);
        // Orbiting fragments around the lower half of the bubble (Zhongli's rocks -> golden notes).
        var notes=buffers.getBuffer(RenderType.entityTranslucentEmissive(NOTE));
        for(int i=0;i<FRAGMENTS;i++){
            double angle=time*.045+2*Math.PI*i/FRAGMENTS;
            double ring=radius*(1.02+.06*Math.sin(i*2.3))*scale+burst*2.2;
            double y=center.y-radius*.42+.12*Math.sin(time*.11+i*1.9)+burst*(i%2==0?.6:-.2);
            float size=(.34f+.06f*(i%3))*(on?Math.min(1,since/FORM):1);
            float shimmer=.85f+.15f*(float)Math.sin(time*.2+i);
            note(pose,notes,facing,new Vec3(Math.cos(angle)*ring,y,Math.sin(angle)*ring),size,1f*shimmer,.8f*shimmer,.28f*shimmer,alpha);
        }
    }

    private static void sphere(PoseStack pose,VertexConsumer out,Vec3 c,double r,Vec3 camera,float time,float alpha){
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
        // A glint band sweeps up the bubble every few seconds.
        double sweep=((time*.025)%1.6)-.3;
        for(int la=0;la<LAT;la++)for(int lo=0;lo<LON;lo++){
            int[][] quad={{la,lo},{la+1,lo},{la+1,lo+1},{la,lo+1}};
            for(int[] q:quad){
                double v=(double)q[0]/LAT,u=(double)q[1]/LON;
                double theta=v*Math.PI,phi=u*2*Math.PI;
                Vec3 normal=new Vec3(Math.sin(theta)*Math.cos(phi),Math.cos(theta),Math.sin(theta)*Math.sin(phi));
                Vec3 p=c.add(normal.scale(r));
                // Fresnel: almost clear facing the camera, bright amber at the rim.
                double view=Math.abs(normal.dot(camera.subtract(normal.scale(r)).normalize()));
                double fresnel=Math.pow(1-view,2.2);
                // Geo hexagonal surface lines on (longitude arc, latitude arc).
                double line=hexLine((phi-Math.PI)*Math.sin(theta)*r,(theta-Math.PI/2)*r,.32);
                double glint=Math.exp(-Math.pow(((1-v)-sweep)/.06,2))*.35;
                double a=(.07+.62*fresnel+.30*line+glint)*alpha;
                float red=1f,green=(float)(.78+.15*line+.12*glint),blue=(float)(.30+.25*line+.3*glint);
                out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(red,Math.min(1,green),Math.min(1,blue),(float)Math.min(1,a))
                        .uv((float)u,(float)v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                        .normal(n,(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
            }
        }
    }
    /** 1 on hexagon edges, 0 inside (pointy hex tiling of cell size s). */
    private static double hexLine(double x,double y,double s){
        double px=x/s,py=y/s,rx=1,ry=1.7320508;
        double ax=mod(px,rx)-rx/2,ay=mod(py,ry)-ry/2,bx=mod(px-rx/2,rx)-rx/2,by=mod(py-ry/2,ry)-ry/2;
        double gx,gy;if(ax*ax+ay*ay<bx*bx+by*by){gx=ax;gy=ay;}else{gx=bx;gy=by;}
        double d=Math.max(Math.abs(gx)*.5+Math.abs(gy)*.8660254,Math.abs(gx));
        return smooth(.40,.5,d);
    }

    // ------------------------------------------------------------------ Monolith links
    private static void links(SatsukiRinEntity rin,State state,PoseStack pose,MultiBufferSource buffers,Quaternionf facing,Vec3 origin,float partial,float time,long now){
        if(state.linksTick!=now){
            state.linksTick=now;
            state.links=rin.isDeadOrDying()?List.of():rin.level().getEntitiesOfClass(com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith.class,
                    rin.getBoundingBox().inflate(128),m->m.isAlive()&&m instanceof FumoAppearance f&&f.ark$fumo()>0).stream().map(Entity.class::cast).toList();
        }
        if(state.links.isEmpty())return;
        var notes=buffers.getBuffer(RenderType.entityTranslucentEmissive(NOTE));
        Vec3 from=new Vec3(0,rin.getBbHeight()*.6,0);
        for(Entity monolith:state.links){
            Vec3 to=monolith.getPosition(partial).subtract(origin).add(0,monolith.getBbHeight()*.5,0);
            double length=to.subtract(from).length();if(length<.5)continue;
            double spacing=.85,flow=(time*.12)%spacing;
            for(double d=flow;d<length;d+=spacing){
                Vec3 p=from.lerp(to,d/length);
                // Fade at both ends so the line reads as flowing toward the monolith.
                float edge=(float)Math.min(1,Math.min(d,length-d)/1.5);
                float pulse=.75f+.25f*(float)Math.sin(time*.3-d*.8);
                note(pose,notes,facing,p,.42f,1f*pulse,.12f*pulse,.18f*pulse,edge);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static void note(PoseStack pose,VertexConsumer out,Quaternionf facing,Vec3 at,float size,float r,float g,float b,float alpha){
        if(alpha<=.01f)return;
        pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(facing);pose.scale(size,size,size);
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();int a=(int)(Math.min(1,alpha)*255);
        int ri=(int)(Math.min(1,r)*255),gi=(int)(Math.min(1,g)*255),bi=(int)(Math.min(1,b)*255);
        float[][] corners={{-.5f,-.5f,0,1},{.5f,-.5f,1,1},{.5f,.5f,1,0},{-.5f,.5f,0,0}};
        for(float[] c:corners)out.vertex(m,c[0],c[1],0).color(ri,gi,bi,a).uv(c[2],c[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n,0,0,1).endVertex();
        pose.popPose();
    }
    private static double mod(double a,double b){return a-b*Math.floor(a/b);}
    private static double smooth(double e0,double e1,double x){double t=Math.max(0,Math.min(1,(x-e0)/(e1-e0)));return t*t*(3-2*t);}
    private static double backOut(double t){double c=1.70158,u=t-1;return 1+(c+1)*u*u*u+c*u*u;}
}
