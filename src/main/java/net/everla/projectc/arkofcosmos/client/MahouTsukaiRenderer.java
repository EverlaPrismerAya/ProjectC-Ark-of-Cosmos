package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class MahouTsukaiRenderer extends MobRenderer<MahouTsukaiEntity, PlayerModel<MahouTsukaiEntity>> {

    public MahouTsukaiRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<MahouTsukaiEntity>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true){
            private float retreatOpacity=1;
            @Override public void setupAnim(MahouTsukaiEntity boss,float swing,float amount,float age,float headYaw,float pitch){
                retreatOpacity=boss.retreatOpacity();
                super.setupAnim(boss,swing,amount,age,headYaw,pitch);
                if(boss.raisedHands()){leftArm.xRot=rightArm.xRot=-2.8f;leftArm.zRot=-.3f;rightArm.zRot=.3f;}
                else if(!boss.defeating() && boss.spell()!=0){leftArm.xRot=rightArm.xRot=-1.45f;}
                leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);
            }
            @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float r,float g,float b,float a){
                super.renderToBuffer(pose,buffer,light,OverlayTexture.NO_OVERLAY,r,g,b,a*retreatOpacity);
            }
        }, .5f);
        addLayer(new OdamaneHaloLayer(this));
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }
    @Override public boolean shouldRender(MahouTsukaiEntity boss,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        double radius=boss.fusionShield()?MahouTsukaiEntity.FUSION_SHIELD_RADIUS:2.6;
        return super.shouldRender(boss,frustum,x,y,z)||!boss.defeating()&&boss.shouldRenderAtSqrDistance(boss.distanceToSqr(x,y,z))&&frustum.isVisible(boss.getBoundingBox().inflate(radius));
    }
    @Override public void render(MahouTsukaiEntity boss,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        super.render(boss,yaw,partial,pose,buffers,light);
        if(boss.isInvisible())return;
        var view=boss.infernumView();
        if(!boss.defeating()&&"ExplosiveCharges".equals(view.getString("Attack"))&&view.getInt("Frame")>=132){
            Vec3 velocity=net.everla.projectc.arkofcosmos.entity.MahouInfernumAttackEntity.vector(view,"Velocity");
            var frame=net.everla.projectc.arkofcosmos.calamity.client.GalaxiaRenderer.lineFrame(velocity,NativeSprites.billboard());
            var draw=new NativeSprites.Draw(pose,buffers,Vec3.ZERO,-1);
            Vec3 center=new Vec3(0,boss.getBbHeight()/2,0);
            for(int i=0;i<3;i++){Vec3 at=center.subtract(velocity.scale(i*2));float[] color=net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.mul(net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.CORNFLOWER,(3-i)/3d);draw.centered("fx/sunderingscissorsleft",at,frame,Math.PI/4,.35,.35,color,false);draw.centered("fx/sunderingscissorsleftglow",at,frame,Math.PI/4,.35,.35,color,true);}
        }
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
        ArkFxDraw.matrix=pose.last().pose();
        ArkFxDraw.camera=Vec3.ZERO;
        ArkFxDraw.fadeEye=null;
        if(net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat.defer(()->renderShield(boss,partial),true))return;
        renderShield(boss,partial);
    }
    private static void renderShield(MahouTsukaiEntity boss,float partial){
        if(!boss.defeating() && boss.fusionShield()){
            ArkFxDraw.moon(new Vec3(0,.1,0),new net.everla.projectc.arkofcosmos.util.AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1)),MahouTsukaiEntity.FUSION_SHIELD_RADIUS/.79,.8f,(boss.tickCount+partial)/20f,false);
        }
        if (boss.defeating()) return;
        ArkFxDraw.forcefield(new Vec3(0,boss.getBbHeight()/2,0),
                NativeSprites.billboard(),boss.phase(),boss.totalHealthProgress(),
                .75f*boss.retreatOpacity(),(boss.tickCount+partial)/20f,MahouMusic.postMusicHit(boss),boss.fusionShield()?MahouTsukaiEntity.FUSION_SHIELD_RADIUS:0);
    }
    @Override protected net.minecraft.client.renderer.RenderType getRenderType(MahouTsukaiEntity boss,boolean visible,boolean translucent,boolean glowing){
        return boss.retreatOpacity()<1?net.minecraft.client.renderer.RenderType.entityTranslucent(getTextureLocation(boss)):super.getRenderType(boss,visible,translucent,glowing);
    }
    @Override public ResourceLocation getTextureLocation(MahouTsukaiEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "textures/entity/everlaprismer.png");
    }
}
