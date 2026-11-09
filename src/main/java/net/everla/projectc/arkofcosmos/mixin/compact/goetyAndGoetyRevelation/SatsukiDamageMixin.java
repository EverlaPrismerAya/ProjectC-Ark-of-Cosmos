package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;
import net.everla.projectc.arkofcosmos.combat.SatsukiDamageAccess;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.entities.boss.Apostle",priority=-4000)
public abstract class SatsukiDamageMixin extends Monster implements SatsukiDamageAccess {
    protected SatsukiDamageMixin(EntityType<? extends Monster> type,Level level){super(type,level);}
    @Unique public boolean ark$satsukiHurt(DamageSource source,float amount){return super.hurt(source,amount);}
    @Unique public void ark$satsukiActuallyHurt(DamageSource source,float amount){super.actuallyHurt(source,amount);}
}
