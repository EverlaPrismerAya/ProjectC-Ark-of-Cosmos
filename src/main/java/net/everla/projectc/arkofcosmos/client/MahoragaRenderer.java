package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;

public final class MahoragaRenderer extends WardenRenderer {
    public MahoragaRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public void render(Warden entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        super.render(entity,yaw,partial,pose,buffers,light);
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=Vec3.ZERO;ArkFxDraw.fadeEye=null;
        float angle=entity instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity w?w.wheelAngle(partial):0;
        AttackFrame plane=new AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1));
        Vec3 center=new Vec3(0,entity.getBbHeight()+.65,0);
        ArkFxDraw.jujutsu(center,plane,1.5,.95f,angle,4);
    }
}
