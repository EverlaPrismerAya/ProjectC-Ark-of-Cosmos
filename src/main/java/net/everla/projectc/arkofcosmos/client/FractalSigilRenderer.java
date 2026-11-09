package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.everla.projectc.arkofcosmos.entity.FractalSigilEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Ground "fractal sigil": rainbow First Fractal palette, outer/inner rings, a nested star of three rotated squares
 * and twelve spokes. It brightens through the trap lead-up, spins hard while slashing, then fades.
 */
public final class FractalSigilRenderer extends EntityRenderer<FractalSigilEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/misc/white.png");
    private static final int FULL_BRIGHT=15728880;
    public FractalSigilRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(FractalSigilEntity e){return WHITE;}
    @Override public void render(FractalSigilEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.tickCount+partial,t=age-e.lead();
        float intensity;
        if(t<0)intensity=e.lead()<=0?1:.25f+.75f*(age/e.lead());              // lead-up: charge
        else if(t<FractalSigilEntity.ACTIVE)intensity=1;                         // slashing
        else intensity=1-Math.min(1,(t-FractalSigilEntity.ACTIVE)/FractalSigilEntity.FADE);
        if(intensity<=0)return;
        float radius=e.radius()*(t<0&&e.lead()>0?.6f+.4f*Math.min(1,age/e.lead()):1);
        float spin=(float)(age*(t>=0?.12:.03));
        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        pose.pushPose();pose.translate(0,.03,0);
        Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
        float hue=(age*.01f)%1,w=.07f;
        ring(out,m,n,radius,w,hue,intensity,spin);
        ring(out,m,n,radius*.82f,w*.7f,hue+.15f,intensity*.8f,-spin);
        ring(out,m,n,radius*.35f,w*.6f,hue+.3f,intensity*.8f,spin*1.5f);
        for(int s=0;s<3;s++)polygon(out,m,n,4,radius*.8f,spin+s*(float)Math.PI/6,w*.6f,hue+.1f*s,intensity*.9f);
        for(int i=0;i<12;i++){
            float a=-spin*.5f+i*(float)Math.PI/6;
            segment(out,m,n,radius*.35f*(float)Math.cos(a),radius*.35f*(float)Math.sin(a),radius*.82f*(float)Math.cos(a),radius*.82f*(float)Math.sin(a),w*.4f,hue+i/12f,intensity*.6f);
        }
        pose.popPose();
    }
    private static void ring(VertexConsumer out,Matrix4f m,Matrix3f n,float r,float w,float hue,float alpha,float spin){
        int count=64;
        for(int i=0;i<count;i++){
            float a0=spin+i*(float)Math.PI*2/count,a1=spin+(i+1)*(float)Math.PI*2/count;
            segment(out,m,n,r*(float)Math.cos(a0),r*(float)Math.sin(a0),r*(float)Math.cos(a1),r*(float)Math.sin(a1),w,hue+i/(float)count,alpha);
        }
    }
    private static void polygon(VertexConsumer out,Matrix4f m,Matrix3f n,int sides,float r,float spin,float w,float hue,float alpha){
        for(int i=0;i<sides;i++){
            float a0=spin+i*(float)Math.PI*2/sides,a1=spin+(i+1)*(float)Math.PI*2/sides;
            segment(out,m,n,r*(float)Math.cos(a0),r*(float)Math.sin(a0),r*(float)Math.cos(a1),r*(float)Math.sin(a1),w,hue+i/(float)sides*.25f,alpha);
        }
    }
    /** Flat glowing line on the ground plane (x,z). */
    private static void segment(VertexConsumer out,Matrix4f m,Matrix3f n,float x0,float z0,float x1,float z1,float w,float hue,float alpha){
        float dx=x1-x0,dz=z1-z0,len=(float)Math.sqrt(dx*dx+dz*dz);if(len<1e-5)return;
        float nx=-dz/len*w,nz=dx/len*w;
        int rgb=net.minecraft.util.Mth.hsvToRgb(((hue%1)+1)%1,.85f,1);
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255,a=(int)(Math.max(0,Math.min(1,alpha))*255);
        vertex(out,m,n,x0-nx,z0-nz,r,g,b,a);vertex(out,m,n,x0+nx,z0+nz,r,g,b,a);
        vertex(out,m,n,x1+nx,z1+nz,r,g,b,a);vertex(out,m,n,x1-nx,z1-nz,r,g,b,a);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Matrix3f n,float x,float z,int r,int g,int b,int a){
        out.vertex(m,x,0,z).color(r,g,b,a).uv(.5f,.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n,0,1,0).endVertex();
    }
}
