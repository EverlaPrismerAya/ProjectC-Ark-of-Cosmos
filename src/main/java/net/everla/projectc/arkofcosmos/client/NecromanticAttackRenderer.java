package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import static net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity.*;

/** Original sprite sheets and Rancor shaders, projected into the confirmed world-space planes. */
public final class NecromanticAttackRenderer extends EntityRenderer<NecromanticAttackEntity> {
    public NecromanticAttackRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(NecromanticAttackEntity e){return ArkFxDraw.texture("rancor_circle");}
    @Override public boolean shouldRender(NecromanticAttackEntity e,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){return e.kind()==Kind.RANCOR||e.kind()==Kind.CONGREGATION||super.shouldRender(e,frustum,x,y,z);}
    @Override public void render(NecromanticAttackEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=e.getPosition(partial);ArkFxDraw.fadeEye=null;
        if(OculusShaderCompat.defer(()->draw(e,partial,light),true))return;draw(e,partial,light);
    }
    private void draw(NecromanticAttackEntity e,float partial,int light){
        var state=e.view();Vec3 at=e.getPosition(partial);float opacity=state.getFloat("Opacity"),scale=state.getFloat("Scale");int age=e.age();
        switch(e.kind()){
            case RANCOR->{
                Vec3 forward=vector(state,"Direction").normalize();if(forward.lengthSqr()<.5)return;
                Vec3 right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.normalize();
                AttackFrame plane=new AttackFrame(right,forward.cross(right).normalize());
                circle("rancor_circle_glow",at,plane,state.getFloat("Rotation"),scale*1.075,opacity,true,partial);
                circle("rancor_circle",at,plane,state.getFloat("Rotation"),scale,opacity*.7f,false,partial);
                circle("rancor_circle_inner_glow",at,plane,0,scale*1.075,opacity*.5f,true,partial);
                circle("rancor_circle_inner",at,plane,0,scale,opacity*.7f,false,partial);
                if(age>=179)beam(at,forward,state.getDouble("Length"),Math.min(2,1+.15*(age-178)),partial);
            }
            case ARM->{
                int frame=state.getInt("Variant");double angle=state.getFloat("Rotation");double radius=opacity*scale*5/16d;
                for(int i=0;i<6;i++){Vec3 offset=NativeSprites.billboard().direction(i*Math.PI/3).scale(radius);sprite("rancor_arm",at.add(offset),angle,scale,1,new float[]{opacity*scale*.4f,0,0,0},frame,6);}
                sprite("rancor_arm",at,angle,scale,1,new float[]{opacity,opacity,opacity,opacity},frame,6);
            }
            case SMALL_CINDER->sprite("rancor_small_cinder",at,0,scale,scale,new float[]{1,1,1,1},state.getInt("Variant"),3);
            default->{}
        }
    }
    static void sprite(String texture,Vec3 at,double angle,double sx,double sy,float[] tint,int frame,int frames){int[] size=ArkFxDraw.size(texture);int h=size[1]/frames;ArkFxDraw.sprite(texture,at,NativeSprites.billboard(),angle,sx,sy,size[0]/2d,h/2d,tint,ArkFxDraw.Blend.ALPHA,0,frame*h,size[0],h);}
    private static void circle(String texture,Vec3 at,AttackFrame plane,double angle,double scale,float opacity,boolean glow,float partial){
        if(scale<=0||opacity<=0)return;var shader=ArkFxDraw.rancorShader;if(shader==null)return;
        ArkFxDraw.begin(texture,glow?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA,false);
        try{
            shader.safeGetUniform("Mode").set(0f);shader.safeGetUniform("uTime").set(ArkFxDraw.time());
            int[] size=ArkFxDraw.size(texture);shader.safeGetUniform("ImageRatio").set(size[1]/(float)ArkFxDraw.size("rancor_circle")[1]);RenderSystem.setShader(()->shader);
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
            Vec3 x=plane.direction(angle).scale(size[0]*scale/32),y=plane.direction(angle+Math.PI/2).scale(size[1]*scale/32);float[] color={opacity,opacity,opacity,opacity};
            ArkFxDraw.vertex(out,at.subtract(x).subtract(y),color,0,0,1);ArkFxDraw.vertex(out,at.subtract(x).add(y),color,0,1,1);ArkFxDraw.vertex(out,at.add(x).add(y),color,1,1,1);ArkFxDraw.vertex(out,at.add(x).subtract(y),color,1,0,1);BufferUploader.drawWithShader(out.end());
        }finally{ArkFxDraw.end();}
    }
    private static void beam(Vec3 start,Vec3 forward,double length,double scale,float partial){
        if(length<=0||ArkFxDraw.rancorShader==null)return;
        ArkFxDraw.begin("terraria_perlin",ArkFxDraw.Blend.ALPHA,true);
        try{
            var shader=ArkFxDraw.rancorShader;shader.safeGetUniform("Mode").set(1f);shader.safeGetUniform("uTime").set(ArkFxDraw.time());RenderSystem.setShader(()->shader);
            Vec3 a=forward.cross(new Vec3(0,1,0));if(a.lengthSqr()<.001)a=new Vec3(1,0,0);a=a.normalize();Vec3 b=forward.cross(a).normalize();
            // Closed cross-section: the beam has thickness from every viewing direction.
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
            for(int step=0;step<92;step++)for(int side=0;side<12;side++){
                double u0=step/92d,u1=(step+1)/92d,v0=side/12d,v1=(side+1)/12d;
                beamVertex(out,start,forward,a,b,length,scale,u0,v0);beamVertex(out,start,forward,a,b,length,scale,u0,v1);beamVertex(out,start,forward,a,b,length,scale,u1,v1);beamVertex(out,start,forward,a,b,length,scale,u1,v0);
            }BufferUploader.drawWithShader(out.end());
        }finally{ArkFxDraw.end();}
    }
    private static void beamVertex(BufferBuilder out,Vec3 at,Vec3 forward,Vec3 a,Vec3 b,double length,double scale,double u,double v){
        Vec3 point=at.add(forward.scale(u*length)).add(a.scale(Math.cos(v*Math.PI*2)*scale*20/16)).add(b.scale(Math.sin(v*Math.PI*2)*scale*20/16));
        double sourceLength=length*16;float mix=(float)(Math.cos(ArkFxDraw.time()*.67-u/Math.max(1,sourceLength)*29)*.5+.5);
        float alpha=(float)(lerpValue(.97,.9,u)*lerpValue(0,Math.min(.5,15/Math.max(1,sourceLength)),u)*Math.pow(lerpValue(60,270,sourceLength),3)*2);
        ArkFxDraw.vertex(out,point,new float[]{(.5f+.5f*mix)*alpha,.5f*alpha,(1-.5f*mix)*alpha,alpha},(float)u,(float)v,1);
    }
}
