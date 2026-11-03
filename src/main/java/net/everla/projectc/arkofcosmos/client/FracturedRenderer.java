package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.calamity.client.FracturedEffects;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.FracturedEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.Blend.ALPHA;

/** Original Fractured Ark PreDraw, through the depth-aware GLSL/Oculus sprite path. */
public final class FracturedRenderer extends EntityRenderer<FracturedEntity> {
    private static final String BLADE="item/fractured_ark", GLOW="item/fractured_ark_glow";
    public FracturedRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(FracturedEntity e) { return ArkFxDraw.texture(BLADE); }
    @Override public boolean shouldRender(FracturedEntity e, Frustum f, double x, double y, double z) { return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z)); }
    @Override public void render(FracturedEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        int kind=s.getInt("Kind");double age=s.getDouble("Age"),scale=s.getDouble("Scale"),angle=s.getDouble("Rotation");
        if(kind==FracturedEntity.BEAM&&age<5 || kind==FracturedEntity.BLADE&&age>=35)return;
        AttackFrame f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        Vec3 base=new Vec3(net.minecraft.util.Mth.lerp(partial,e.xOld,e.getX()),net.minecraft.util.Mth.lerp(partial,e.yOld,e.getY()),net.minecraft.util.Mth.lerp(partial,e.zOld,e.getZ()));
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=base;ArkFxDraw.ownerView(e.ownerId(),Vec3.ZERO);
        try {
            Vec3 at=vector(s,"Position"),center=vector(s,"Center");var player=e.level().getEntity(e.ownerId());
            if(player!=null)center=player.getPosition(partial).add(0,player.getBbHeight()/2,0);
            ListTag trail=s.getList("Trail",10);int[] size=ArkFxDraw.size(BLADE);
            if(kind==FracturedEntity.BEAM) {
                for(int i=0;i<trail.size();i++) {
                    CompoundTag old=trail.getCompound(i);float fade=(5-i)/5f*.6f;
                    ArkFxDraw.litSprite(GLOW,vector(old,"Position"),f,old.getDouble("Rotation"),scale,size[0]/2d,size[1]/2d,mul(WHITE,fade),0,0,size[0],size[1],light,0);
                }
                return;
            }
            Vec3 hilt=kind==FracturedEntity.BLADE?center.add(f.direction(angle).scale(10/16d)):at;
            if(kind==FracturedEntity.BLADE&&age>10)for(int i=0;i<trail.size();i++) {
                float[] color=ArkFxDraw.hsl(i/10f*.7f,1,s.getInt("Charge")>0?.9f:.6f,1);
                ArkFxDraw.sprite(GLOW,hilt,f,trail.getCompound(i).getDouble("Rotation")+Math.PI/4,scale-.2*i/10,scale-.2*i/10,0,size[1],mul(color,.15),ALPHA);
            }
            ArkFxDraw.litExtrudedBlade(BLADE,hilt,f,angle+Math.PI/4,scale,0,size[1],WHITE,light,0);
            ArkFxDraw.litExtrudedBlade(GLOW,hilt,f,angle+Math.PI/4,scale,0,size[1],WHITE,light,.75f);
            int success=FracturedEffects.parriedAt(s);
            if(kind==FracturedEntity.PARRY&&success>=0) {
                double elapsed=Math.max(1,age-success+1),ratio=elapsed/15;
                if(ratio<1)ArkFxDraw.sprite(GLOW,hilt.add(f.forward().scale(33/16d)),f,angle+Math.PI/4,scale+ratio,scale+ratio,0,36,mul(ArkFxDraw.hsl(ArkFxDraw.time()%1,1,.8f,1),1-ratio),ALPHA,24,0,36,36);
            }
        } finally {ArkFxDraw.fadeEye=null;}
    }
}
