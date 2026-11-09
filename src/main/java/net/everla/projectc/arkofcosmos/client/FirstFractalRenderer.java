package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.FirstFractalEntity;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

public final class FirstFractalRenderer extends EntityRenderer<FirstFractalEntity> {
    private final PlayerModel<AbstractClientPlayer> regular,slim;
    public FirstFractalRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=0;
        regular=new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false);
        slim=new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);
    }
    @Override public ResourceLocation getTextureLocation(FirstFractalEntity e){return NativeSprites.texture("fx/first_fractal_swords");}
    @Override public boolean shouldRender(FirstFractalEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    public static float[] color(float hue,double alpha){
        int rgb=net.minecraft.util.Mth.hsvToRgb(hue,1,1);return new float[]{((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,(float)alpha};
    }
    private static double lerp(double a,double b,double v){return Math.max(0,Math.min(1,(v-a)/(b-a)));}
    @Override public void render(FirstFractalEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var s=e.renderState(partial);if(s.isEmpty())return;
        double age=s.getDouble("Age"),opacity=FirstFractalEntity.opacity(age);if(opacity<=0)return;
        Vec3 at=vector(s,"Position"),velocity=vector(s,"Velocity"),base=e.getPosition(partial);
        AttackFrame f=new AttackFrame(vector(s,"F"),vector(s,"R"));double angle=f.angle(velocity);
        var d=new NativeSprites.Draw(pose,buffers,base,e.ownerId());
        if(e.level().getEntity(e.ownerId()) instanceof AbstractClientPlayer p)clone(p,at.subtract(velocity.normalize().scale(42/16d)),velocity,base,opacity,pose,buffers);
        // Source Main.DrawProjWithStarryTrail: three orbiting flares and two white pulses.
        Vec3 direction=velocity.normalize();double time=(e.level().getGameTime()+partial)/20d;
        double distance=1;
        if(e.level().getEntity(e.ownerId()) instanceof AbstractClientPlayer p)distance=lerp(0,7.5,at.distanceTo(p.getBoundingBox().getCenter()));
        double fade=lerp(60,50,age)*lerp(0,15,age)*distance;
        double scale=(-.3*(1-lerp(60,40,age)*lerp(0,20,age))-lerp(15,0,age))*distance;
        double[] sizes={1.5,1.1,1.3};
        for(int i=0;i<3;i++){
            Vec3 orbit=f.direction(time*Math.PI*2+i*Math.PI*2/3).scale(2/16d);
            d.sprite("fx/first_fractal_streak",at.add(direction.scale(7.5/16)).add(orbit),f,angle-Math.PI/2,sizes[i]+scale,sizes[i]+scale,17,10,color(s.getFloat("Hue"),.15*fade*fade),true,false,15728880);
        }
        for(int i=0;i<2;i++){
            double t=(time% .5/.5+i*.5)%1,pulse=1-Math.abs(t*2-1);
            d.sprite("fx/first_fractal_streak",at.add(direction.scale(2.5/16)),f,angle-Math.PI/2,.3+t*.5,.3+t*.5,17,10,new float[]{1,1,1,(float)(.5*fade*pulse)},true,false,15728880);
        }
        double flare=lerp(15,30,age)*lerp(45,30,age);
        for(int i=0;i<2;i++){
            double a=i*Math.PI/2,sy=(i==0?2:5)*flare;
            d.sprite("fx/first_fractal_star",at,f,a,.5*flare,sy,36,36,color(s.getFloat("Hue"),opacity*.5*flare),true,false,15728880);
            d.sprite("fx/first_fractal_star",at,f,a,.3*flare,sy*.6,36,36,new float[]{1,1,1,(float)(opacity*.45*flare)},true,false,15728880);
        }
        int variant=s.getInt("Variant");
        d.thickRegion("fx/first_fractal_swords",at,f,angle+Math.PI/4,1,45,42,new float[]{.9f,.9f,.9f,(float)(opacity*.45)},variant*90,0,90,84,false,15728880);
    }
    private void clone(AbstractClientPlayer p,Vec3 center,Vec3 velocity,Vec3 base,double opacity,PoseStack pose,MultiBufferSource buffers){
        var model=p.getModelName().equals("slim")?slim:regular;
        model.setAllVisible(true);model.crouching=false;model.riding=false;model.young=false;model.attackTime=0;
        model.rightArmPose=model.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        // setupAnim resets model parts; no living entity state is changed to draw the afterimage.
        model.setupAnim(p,0,0,0,0,0);
        float pitch=(float)Math.asin(-velocity.normalize().y);
        model.head.xRot=pitch;model.head.yRot=0;
        model.body.xRot=0;model.body.yRot=0;
        model.rightArm.xRot=-(float)Math.PI/2+pitch;model.rightArm.yRot=0;model.rightArm.zRot=.1f;
        model.leftArm.xRot=-.25f;model.leftArm.yRot=0;model.leftArm.zRot=-.15f;
        model.rightLeg.xRot=.25f;model.leftLeg.xRot=-.25f;
        model.hat.copyFrom(model.head);model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);model.leftPants.copyFrom(model.leftLeg);model.jacket.copyFrom(model.body);
        model.hat.visible=p.isModelPartShown(PlayerModelPart.HAT);model.jacket.visible=p.isModelPartShown(PlayerModelPart.JACKET);
        model.rightSleeve.visible=p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);model.leftSleeve.visible=p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightPants.visible=p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);model.leftPants.visible=p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        var mc=Minecraft.getInstance();double fade=1;
        if(mc.getCameraEntity()==p&&mc.options.getCameraType().isFirstPerson())fade=ArkFxDraw.nearOpacity(center.distanceTo(mc.gameRenderer.getMainCamera().getPosition()));
        Vec3 at=center.subtract(base);pose.pushPose();
        try{
            pose.translate(at.x,at.y-.9,at.z);
            pose.mulPose(Axis.YP.rotation((float)(Math.PI-Math.atan2(-velocity.x,velocity.z))));
            pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
            model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(p.getSkinTextureLocation())),15728880,OverlayTexture.NO_OVERLAY,1,1,1,(float)(opacity*fade));
        }finally{pose.popPose();}
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    // Forge listener wrapper names omit the enclosing class; keep this simple name unique.
    public static final class FirstFractalRendererRegistration {
        @SubscribeEvent public static void renderer(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ArkOfCosmos.FIRST_FRACTAL_ATTACK.get(),FirstFractalRenderer::new);}
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.FIRST_FRACTAL.get(),ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),(stack,level,entity,seed)->entity instanceof net.minecraft.world.entity.player.Player p&&net.everla.projectc.arkofcosmos.combat.FirstFractalCombat.active(p)?1:0));}
    }
}
