package net.everla.projectc.arkofcosmos.combat.vanilla;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class MaceCombat {
    public static boolean canSmash(LivingEntity e) { return e.fallDistance>1.5f&&!e.isFallFlying(); }
    public static float bonus(float fall,int density) { return (fall<=3?4*fall:fall<=8?12+2*(fall-3):22+fall-8)+.5f*density*fall; }
    public static void impact(LivingEntity attacker,LivingEntity target,ItemStack stack) {
        float fall=attacker.fallDistance;attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(1,0,1).add(0,.01f,0));
        ImpulseFall.mark(attacker,true);VanillaCombat.syncMotion(attacker);
        VanillaCombat.sound(attacker,target.onGround()?fall>5?"item.mace.smash_ground_heavy":"item.mace.smash_ground":"item.mace.smash_air",1,1);
        attacker.level().levelEvent(2013,target.getOnPos(),750);
        for(LivingEntity other:attacker.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(3.5))) {
            if(other==attacker||other==target||other.isSpectator()||attacker.isAlliedTo(other)||other instanceof ArmorStand a&&a.isMarker()
                ||other instanceof TamableAnimal animal&&animal.isTame()&&animal.isOwnedBy(target)
                ||other instanceof Player p&&p.isCreative()&&p.getAbilities().flying)continue;
            Vec3 direction=other.position().subtract(target.position());
            double power=(3.5-direction.length())*.7f*(fall>5?2:1)*(attacker instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity?1:1-other.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if(power>0){Vec3 push=direction.normalize().scale(power);other.push(push.x,.7f,push.z);VanillaCombat.syncMotion(other);}
        }
        wind(attacker,stack);
        attacker.fallDistance=0;
    }
    public static void wind(LivingEntity attacker,ItemStack stack) {
        int wind=VanillaCombat.enchant(stack,VanillaCombat.WIND_BURST);
        if(wind>0&&attacker.fallDistance>=1.5f&&!attacker.isFallFlying())WindBurst.explode(attacker.level(),null,attacker.position(),3.5f,wind==1?1.2f:wind==2?1.75f:2.2f,false,attacker instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity);
    }
}
