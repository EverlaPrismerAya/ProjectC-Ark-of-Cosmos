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
            @Override public void setupAnim(MahouTsukaiEntity boss,float swing,float amount,float age,float headYaw,float pitch){
                super.setupAnim(boss,swing,amount,age,headYaw,pitch);
                if(boss.raisedHands()){leftArm.xRot=rightArm.xRot=-2.8f;leftArm.zRot=-.3f;rightArm.zRot=.3f;}
                else if(!boss.defeating() && boss.spell()!=0){leftArm.xRot=rightArm.xRot=-1.45f;}
                leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);
            }
            @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float r,float g,float b,float a){
                super.renderToBuffer(pose,buffer,light,OverlayTexture.NO_OVERLAY,r,g,b,a);
            }
        }, .5f);
        addLayer(new OdamaneHaloLayer(this));
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }
    @Override public void render(MahouTsukaiEntity boss,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        super.render(boss,yaw,partial,pose,buffers,light);
        if(boss.isInvisible())return;
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
        ArkFxDraw.matrix=pose.last().pose();
        ArkFxDraw.camera=Vec3.ZERO;
        ArkFxDraw.fadeEye=null;
        if(!boss.defeating() && boss.fusionShield()){
            ArkFxDraw.moon(new Vec3(0,.1,0),new net.everla.projectc.arkofcosmos.util.AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1)),MahouTsukaiEntity.FUSION_SHIELD_RADIUS/.79,.8f,(boss.tickCount+partial)/20f,false);
        }
        ArkFxDraw.matrix=pose.last().pose();
        if (boss.defeating()) return;
        ArkFxDraw.forcefield(new Vec3(0,boss.getBbHeight()/2,0),
                NativeSprites.billboard(),boss.phase(),boss.totalHealthProgress(),
                .75f,(boss.tickCount+partial)/20f,MahouMusic.postMusicHit(boss),boss.fusionShield()?MahouTsukaiEntity.FUSION_SHIELD_RADIUS:0);
    }
    @Override public ResourceLocation getTextureLocation(MahouTsukaiEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "textures/entity/everlaprismer.png");
    }
}
