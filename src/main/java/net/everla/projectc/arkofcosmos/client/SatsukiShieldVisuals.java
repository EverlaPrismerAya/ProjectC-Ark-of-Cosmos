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
 * <li>Shield: Zhongli's Jade Shield (open-ended amber light column with fresnel rim, upward streaks and glint,
 *     rising golden specks, segmented gauge arc, orbiting fragments, rise-in / shatter) with the rock fragments
 *     replaced by golden notes.</li>
 * <li>Links: flowing red note lines from her to each of her Reimu-Fumo obsidian monoliths.</li>
 * </ul>
 */
public final class SatsukiShieldVisuals {
    private SatsukiShieldVisuals(){}
    private static final ResourceLocation NOTE=ResourceLocation.fromNamespaceAndPath("minecraft","textures/particle/note.png");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/misc/white.png");
    private static final int FULL_BRIGHT=15728880;
    private static final float FORM=8,BREAK=7;
    /** Column height relative to her hitbox; the open top fades out well above her head. */
    private static final float PILLAR=1.9f;
    private static final int SIDES=96,RINGS=24,FRAGMENTS=8,SPARKLES=28,ARC_SEGMENTS=3;

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

    /** Culling bounds that also cover the shield column and the monolith links. */
    public static AABB bounds(SatsukiRinEntity rin){
        AABB box=rin.getBoundingBox().inflate(1.5).expandTowards(0,rin.getBbHeight()*(PILLAR-1),0);
        State state=STATES.get(rin);
        if(state!=null)for(Entity link:state.links)box=box.minmax(link.getBoundingBox());
        return box;
    }

    // ------------------------------------------------------------------ Jade Shield
    private static void shield(SatsukiRinEntity rin,PoseStack pose,MultiBufferSource buffers,Quaternionf facing,Vec3 origin,float time,boolean on,float since){
        float body=rin.getBbHeight();
        float radius=Math.max(.95f,rin.getBbWidth()*1.6f);
        // Formation: the column rises from the ground with a slight radial overshoot.
        // Break: it widens and fades while the fragments fly outwards.
        float scale,rise,alpha,burst=0;
        if(on){float t=Math.min(1,since/FORM);scale=(float)backOut(t);rise=(float)(1-Math.pow(1-t,3));alpha=Math.min(1,t*1.6f);}
        else{float t=Math.min(1,since/BREAK);scale=1+.3f*t;rise=1;alpha=1-t;burst=t;}
        if(alpha<=.01f||scale<=.01f)return;
        Vec3 camera=Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition().subtract(origin);
        var white=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        cylinder(pose,white,radius*scale,body*PILLAR*rise,camera,time,alpha);
        sparkles(pose,white,facing,radius*scale,body*rise,time,alpha);
        arc(pose,white,facing,radius*scale,body*.5f,alpha*Math.min(1,rise*1.4f));
        // Fragments orbiting the column at body height (Zhongli's rocks -> golden notes).
        var notes=buffers.getBuffer(RenderType.entityTranslucentEmissive(NOTE));
        for(int i=0;i<FRAGMENTS;i++){
            double angle=time*.04*(i%2==0?1:-.8)+2*Math.PI*i/FRAGMENTS;
            double ring=radius*(1.08+.14*Math.sin(i*2.3))*scale+burst*2.2;
            double y=body*(.18+.68*((i*.618)%1))*rise+.1*Math.sin(time*.09+i*1.9)+burst*(i%2==0?.6:-.2);
            float size=(.32f+.06f*(i%3))*(on?Math.min(1,since/FORM):1);
            float shimmer=.85f+.15f*(float)Math.sin(time*.2+i);
            note(pose,notes,facing,new Vec3(Math.cos(angle)*ring,y,Math.sin(angle)*ring),size,1f*shimmer,.8f*shimmer,.28f*shimmer,alpha);
        }
    }

    /** Open-ended amber light column: no caps, bright fresnel rim, upward streaks, fading out toward the open top. */
    private static void cylinder(PoseStack pose,VertexConsumer out,float r,float h,Vec3 camera,float time,float alpha){
        if(h<=.01f)return;
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
        double sweep=((time*.02)%1.5)-.25;
        for(int la=0;la<RINGS;la++)for(int lo=0;lo<SIDES;lo++){
            int[][] quad={{la,lo},{la+1,lo},{la+1,lo+1},{la,lo+1}};
            for(int[] q:quad){
                double v=(double)q[0]/RINGS,phi=2*Math.PI*q[1]/SIDES;
                double nx=Math.cos(phi),nz=Math.sin(phi);
                double x=nx*r,y=v*h,z=nz*r;
                // Horizontal fresnel: clear where the wall faces the camera, bright along the silhouette.
                double dx=camera.x-x,dz=camera.z-z,len=Math.sqrt(dx*dx+dz*dz);
                double view=len<1e-4?1:Math.abs((nx*dx+nz*dz)/len);
                double fresnel=Math.pow(1-view,2.4);
                // Soft ground contact, brightest low, long fade to nothing at the open top.
                double profile=smooth(0,.05,v)*Math.pow(1-v,1.6);
                // Faint light streaks drifting upward.
                double streak=Math.pow(Math.max(0,Math.sin(phi*11+1.3*Math.sin(phi*4+time*.01))),6)
                        *(.5+.5*Math.sin(v*9-time*.09+phi*3));
                double glint=Math.exp(-Math.pow((v-sweep)/.05,2))*.3;
                double a=(.06+.75*fresnel+.12*streak+glint)*profile*alpha;
                float g=(float)(.74+.2*fresnel+.15*glint),b=(float)(.28+.35*fresnel*fresnel+.3*glint);
                out.vertex(m,(float)x,(float)y,(float)z).color(1f,Math.min(1,g),Math.min(1,b),(float)Math.min(1,a))
                        .uv((float)q[1]/SIDES,(float)v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                        .normal(n,(float)nx,0,(float)nz).endVertex();
            }
        }
    }
    /** Small golden specks rising through and around the column. */
    private static void sparkles(PoseStack pose,VertexConsumer out,Quaternionf facing,float r,float body,float time,float alpha){
        for(int i=0;i<SPARKLES;i++){
            double seed=i*.754877,life=(time*.012+seed*3.1)%1;
            double angle=2*Math.PI*((seed*7.3)%1)+time*.01;
            double ring=r*(.45+.75*((seed*5.1)%1));
            double y=body*(.05+1.25*life);
            float fade=(float)(smooth(0,.15,life)*(1-smooth(.6,1,life)));
            float size=.05f+.05f*(float)((seed*11.7)%1);
            quad(pose,out,facing,new Vec3(Math.cos(angle)*ring,y,Math.sin(angle)*ring),size,1f,.86f,.42f,alpha*fade);
        }
    }
    /** Segmented amber arc hugging the column's silhouette, like the Jade Shield's strength gauge. */
    private static void arc(PoseStack pose,VertexConsumer out,Quaternionf facing,float r,float y,float alpha){
        if(alpha<=.01f)return;
        pose.pushPose();pose.translate(0,y,0);pose.mulPose(facing);
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
        float inner=r*1.06f,outer=inner+.07f,span=(float)Math.toRadians(38),gap=(float)Math.toRadians(3);
        int a=(int)(Math.min(1,alpha)*235);
        for(int s=0;s<ARC_SEGMENTS;s++){
            float from=-span+2*span*s/ARC_SEGMENTS+gap*.5f,to=-span+2*span*(s+1)/ARC_SEGMENTS-gap*.5f;
            for(int k=0;k<6;k++){
                float a0=from+(to-from)*k/6,a1=from+(to-from)*(k+1)/6;
                float[][] pts={{inner,a0},{outer,a0},{outer,a1},{inner,a1}};
                for(float[] p:pts)out.vertex(m,(float)Math.cos(p[1])*p[0],(float)Math.sin(p[1])*p[0],0).color(255,184,40,a)
                        .uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n,0,0,1).endVertex();
            }
        }
        pose.popPose();
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
    private static void note(PoseStack pose,VertexConsumer out,Quaternionf facing,Vec3 at,float size,float r,float g,float b,float alpha){quad(pose,out,facing,at,size,r,g,b,alpha);}
    /** Camera-facing textured quad. */
    private static void quad(PoseStack pose,VertexConsumer out,Quaternionf facing,Vec3 at,float size,float r,float g,float b,float alpha){
        if(alpha<=.01f)return;
        pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(facing);pose.scale(size,size,size);
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();int a=(int)(Math.min(1,alpha)*255);
        int ri=(int)(Math.min(1,r)*255),gi=(int)(Math.min(1,g)*255),bi=(int)(Math.min(1,b)*255);
        float[][] corners={{-.5f,-.5f,0,1},{.5f,-.5f,1,1},{.5f,.5f,1,0},{-.5f,.5f,0,0}};
        for(float[] c:corners)out.vertex(m,c[0],c[1],0).color(ri,gi,bi,a).uv(c[2],c[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n,0,0,1).endVertex();
        pose.popPose();
    }
    private static double smooth(double e0,double e1,double x){double t=Math.max(0,Math.min(1,(x-e0)/(e1-e0)));return t*t*(3-2*t);}
    private static double backOut(double t){double c=1.70158,u=t-1;return 1+(c+1)*u*u*u+c*u*u;}
}
