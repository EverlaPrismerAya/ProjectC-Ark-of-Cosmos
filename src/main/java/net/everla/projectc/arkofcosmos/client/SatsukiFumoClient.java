package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.combat.FumoAppearance;
import net.everla.projectc.arkofcosmos.combat.SatsukiFumo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
public final class SatsukiFumoClient {
    private static ModelFumo<Entity> model;
    private static ModelFumo<Entity> model(){
        if(model==null)model=new ModelFumo<>(ModelFumo.createBodyLayer().bakeRoot());
        return model;
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light,int overlay,int variant,float scale,float yaw){
        draw(pose,buffers,light,overlay,variant,scale,yaw,1);
    }
    private static void draw(PoseStack pose,MultiBufferSource buffers,int light,int overlay,int variant,float scale,float yaw,float alpha){
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
        pose.scale(-scale,-scale,scale);pose.translate(0,-1.5,0);
        var texture=ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/entity/fumo/"+SatsukiFumo.TEXTURES[variant]+".png");
        model().renderToBuffer(pose,buffers.getBuffer(alpha<1?RenderType.entityTranslucent(texture):RenderType.entityCutoutNoCull(texture)),
                light,overlay,1,1,1,alpha);
        pose.popPose();
    }
    public static boolean render(Entity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(!(entity instanceof FumoAppearance appearance)||appearance.ark$fumo()<=0)return false;
        var viewer=net.minecraft.client.Minecraft.getInstance().player;
        if(entity.isInvisible()&&(viewer==null||entity.isInvisibleTo(viewer)))return true;
        // Upstream Fumo is one block tall and about 0.77 blocks wide in its seated pose.
        float scale=Math.min(entity.getBbHeight(),entity.getBbWidth()/.77f);
        var living=(net.minecraft.world.entity.LivingEntity)entity;
        draw(pose,buffers,light,OverlayTexture.pack(0,living.hurtTime>0||living.deathTime>0),appearance.ark$fumo(),scale,
                Mth.rotLerp(partial,living.yBodyRotO,living.yBodyRot),entity.isInvisible()?.15f:1);
        return true;
    }
}
