package net.everla.projectc.arkofcosmos.client.vanilla;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.everla.projectc.arkofcosmos.combat.vanilla.SpearMaterial;
/** Animation curves adapted from Mojang 26.1.2 SpearAnimations. */
public final class SpearAnimations {
    private static float progress(float t,float a,float b) { return Mth.clamp((t-a)/(b-a),0,1); }
    private static void around(PoseStack pose,org.joml.Quaternionf rotation,float x,float y,float z) { pose.translate(x,y,z);pose.mulPose(rotation);pose.translate(-x,-y,-z); }
   private static float hitFeedbackAmount(final float ticksSinceFeedbackStart) {
      return 0.4F * (Ease.outQuart(progress(ticksSinceFeedbackStart, 1.0F, 3.0F)) - Ease.inOutSine(progress(ticksSinceFeedbackStart, 3.0F, 10.0F)));
   }

   public static void firstPersonUse(
      final float ticksSinceKineticHitFeedback, final PoseStack poseStack, final float timeHeld, final HumanoidArm arm, final ItemStack itemStack
   ) {
      SpearMaterial kineticWeapon = itemStack.getItem() instanceof SpearItem spear ? spear.material : null;
      if (kineticWeapon != null) {
         SpearAnimations.UseParams params = SpearAnimations.UseParams.fromKineticWeapon(kineticWeapon, timeHeld);
         int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
         poseStack.translate(
            (double)(
               (float)invert
                  * (params.raiseProgress() * 0.15F + params.raiseProgressEnd() * -0.05F + params.swayProgress() * -0.1F + params.swayScaleSlow() * 0.005F)
            ),
            (double)(params.raiseProgress() * -0.075F + params.raiseProgressMiddle() * 0.075F + params.swayScaleFast() * 0.01F),
            (double)params.raiseProgressStart() * 0.05 + (double)params.raiseProgressEnd() * -0.05 + (double)(params.swayScaleSlow() * 0.005F)
         );
         around(poseStack, 
            Axis.XP
               .rotationDegrees(
                  -65.0F * Ease.inOutBack(params.raiseProgress())
                     - 35.0F * params.lowerProgress()
                     + 100.0F * params.raiseBackProgress()
                     + -0.5F * params.swayScaleFast()
               ),
            0.0F,
            0.1F,
            0.0F
         );
         around(poseStack, 
            Axis.YN
               .rotationDegrees(
                  (float)invert * (-90.0F * progress(params.raiseProgress(), 0.5F, 0.55F) + 90.0F * params.swayProgress() + 2.0F * params.swayScaleSlow())
               ),
            (float)invert * 0.15F,
            0.0F,
            0.0F
         );
         poseStack.translate(0.0F, -hitFeedbackAmount(ticksSinceKineticHitFeedback), 0.0F);
      }
   }

   public static void firstPersonAttack(final float attack, final PoseStack poseStack, final int invert, final HumanoidArm arm) {
      float startingAmount = Ease.inOutSine(progress(attack, 0.0F, 0.05F));
      float middleAmount = Ease.outBack(progress(attack, 0.05F, 0.2F));
      float endingAmount = Ease.inOutExpo(progress(attack, 0.4F, 1.0F));
      poseStack.translate(
         (float)invert * 0.1F * (startingAmount - middleAmount), -0.075F * (startingAmount - endingAmount), 0.65F * (startingAmount - middleAmount)
      );
      poseStack.mulPose(Axis.XP.rotationDegrees(-70.0F * (startingAmount - endingAmount)));
      poseStack.translate(0.0, 0.0, -0.25 * (double)(endingAmount - middleAmount));
   }

   static record UseParams(
      float raiseProgress,
      float raiseProgressStart,
      float raiseProgressMiddle,
      float raiseProgressEnd,
      float swayProgress,
      float lowerProgress,
      float raiseBackProgress,
      float swayIntensity,
      float swayScaleSlow,
      float swayScaleFast
   ) {
      public static SpearAnimations.UseParams fromKineticWeapon(final SpearMaterial kineticWeapon, final float time) {
         int finishRaisingTick = kineticWeapon.delayTicks();
         int finishSwayingTick = kineticWeapon.dismountTicks() + finishRaisingTick;
         int startSwayingTick = finishSwayingTick - 20;
         int finishLoweringTick = kineticWeapon.knockbackTicks() + finishRaisingTick;
         int startLoweringTick = finishLoweringTick - 40;
         int finishRaisingBackTick = kineticWeapon.damageTicks() + finishRaisingTick;
         float raiseProgress = SpearAnimations.progress(time, 0.0F, (float)finishRaisingTick);
         float raiseProgressStart = SpearAnimations.progress(raiseProgress, 0.0F, 0.5F);
         float raiseProgressMiddle = SpearAnimations.progress(raiseProgress, 0.5F, 0.8F);
         float raiseProgressEnd = SpearAnimations.progress(raiseProgress, 0.8F, 1.0F);
         float swayProgress = SpearAnimations.progress(time, (float)startSwayingTick, (float)finishSwayingTick);
         float lowerProgress = Ease.outCubic(Ease.inOutElastic(SpearAnimations.progress(time - 20.0F, (float)startLoweringTick, (float)finishLoweringTick)));
         float raiseBackProgress = SpearAnimations.progress(time, (float)(finishRaisingBackTick - 5), (float)finishRaisingBackTick);
         float swayIntensity = 2.0F * Ease.outCirc(swayProgress) - 2.0F * Ease.inCirc(raiseBackProgress);
         float swayScaleSlow = Mth.sin((float)(time * 19.0F * (float) (Math.PI / 180.0))) * swayIntensity;
         float swayScaleFast = Mth.sin((float)(time * 30.0F * (float) (Math.PI / 180.0))) * swayIntensity;
         return new SpearAnimations.UseParams(
            raiseProgress,
            raiseProgressStart,
            raiseProgressMiddle,
            raiseProgressEnd,
            swayProgress,
            lowerProgress,
            raiseBackProgress,
            swayIntensity,
            swayScaleSlow,
            swayScaleFast
         );
      }
   }

    public static void thirdHand(net.minecraft.client.model.geom.ModelPart arm,net.minecraft.client.model.geom.ModelPart head,boolean right,SpearMaterial m,float time,boolean flying) {
        int invert=right?1:-1;arm.yRot=-.1f*invert+head.yRot;arm.xRot=-(float)Math.PI/2+head.xRot+.8f;
        if(flying)arm.xRot-=.9599311f;
        arm.yRot=Mth.clamp(arm.yRot,-(float)Math.PI/3,(float)Math.PI/3);
        arm.xRot=Mth.clamp(arm.xRot,-2*(float)Math.PI/3,(float)Math.PI/6);
        if(time<=0)return;UseParams params=UseParams.fromKineticWeapon(m,time);float degree=(float)Math.PI/180;
        arm.yRot+=-invert*params.swayScaleFast()*degree*params.swayIntensity();
        arm.zRot+=-invert*params.swayScaleSlow()*degree*params.swayIntensity()*.5f;
        arm.xRot+=degree*(-40*params.raiseProgressStart()+30*params.raiseProgressMiddle()-20*params.raiseProgressEnd()+20*params.lowerProgress()+10*params.raiseBackProgress()+.6f*params.swayScaleSlow()*params.swayIntensity());
    }
    public static void thirdItem(PoseStack pose,HumanoidArm arm,SpearMaterial m,float time,float swing) {
        float attack=Ease.inQuad(progress(swing,.05f,.2f)),retract=Ease.inOutExpo(progress(swing,.4f,1));
        if(time>0) {
            UseParams params=UseParams.fromKineticWeapon(m,time);int invert=arm==HumanoidArm.RIGHT?1:-1;
            float raise=1-Ease.outBack(1-params.raiseProgress());
            pose.translate(0,0,-.38f*(raise-params.raiseBackProgress()));
            around(pose,Axis.XN.rotationDegrees(70*(params.raiseProgress()-params.raiseBackProgress())-40*(attack-retract)),0,-.03125f,.125f);
            around(pose,Axis.YP.rotationDegrees(invert*90*(params.raiseProgress()-params.swayProgress()+3*retract+attack)),0,0,.125f);
        }
        if(swing>0){around(pose,Axis.XN.rotationDegrees(70*(attack-retract)),0,-.125f,.125f);pose.translate(0,.38f*(attack-retract),0);}
    }
    public static float stabArm(float swing) { return (90*Ease.inOutSine(progress(swing,0,.05f))-120*Ease.inQuad(progress(swing,.05f,.2f))+30*Ease.inOutExpo(progress(swing,.4f,1)))*(float)Math.PI/180; }
}
