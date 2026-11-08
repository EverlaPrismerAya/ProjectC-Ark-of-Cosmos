package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mega.revelationfix.safe.entity.Apollyon2Interface;
import net.everla.projectc.arkofcosmos.combat.LegacyApollyonDamageWindow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import z1gned.goetyrevelation.util.ApollyonAbilityHelper;

/** Applied after the old GR/RF methods have merged; newer RF keeps its own implementation. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.entities.boss.Apostle",priority=-3000)
public abstract class LegacyApollyonMixin implements LegacyApollyonDamageWindow.Access {
    @Unique private LegacyApollyonDamageWindow ark$window;


    @Override public LegacyApollyonDamageWindow ark$damageWindow(){
        if(ark$window==null)ark$window=new LegacyApollyonDamageWindow((Apostle)(Object)this);
        return ark$window;
    }
    @Override public void ark$startDamageWindow(int ticks){
        // RF 4.0 still synchronizes its separate timer into GR at the next hurt call.
        ((LegacyApollyonDamageWindow.Access)this).revelaionfix$setHitCooldown(ticks);
        ((ApollyonAbilityHelper)this).allTitlesApostle_1_20_1$setHitCooldown(ticks);
    }
    @WrapMethod(method={"hurt","m_6469_"},remap=false,require=1)
    private boolean ark$ownHealthLoss(DamageSource source,float amount,Operation<Boolean> original){
        var window=LegacyApollyonDamageWindow.get((LivingEntity)(Object)this);
        return window==null?original.call(source,amount):window.hurt(()->original.call(source,amount));
    }
    @Inject(method={"setHealth","m_21153_"},remap=false,at=@At("HEAD"),cancellable=true,require=1)
    private void ark$settleHealth(float health,CallbackInfo ci){
        var self=(LivingEntity)(Object)this;
        var window=LegacyApollyonDamageWindow.get(self);
        if(window==null)return;
        ci.cancel();
        if(Float.isNaN(health))return;
        var change=window.prepareHealthChange(self.getHealth(),health);
        ((Apollyon2Interface)this).revelaionfix$setApollyonHealth(change.health());
        window.finishHealthChange(change,self.getHealth());
    }
    @WrapMethod(method="allTitlesApostle_1_20_1$setHitCooldown",remap=false,require=1)
    private void ark$cooldownChanged(int ticks,Operation<Void> original){
        var window=LegacyApollyonDamageWindow.get((LivingEntity)(Object)this);
        if(window!=null)window.onCooldownChanged(((ApollyonAbilityHelper)this).allTitlesApostle_1_20_1$getHitCooldown(),ticks);
        original.call(ticks);
    }
    @WrapOperation(method={"hurt","m_6469_","actuallyHurt","m_6475_"},remap=false,at=@At(value="FIELD",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;moddedInvul:I",opcode=Opcodes.GETFIELD,remap=false),require=2)
    private int ark$ignoreOldInvul(Apostle self,Operation<Integer> original){
        return LegacyApollyonDamageWindow.get(self)!=null?0:original.call(self);
    }
    @WrapOperation(method={"actuallyHurt","m_6475_"},remap=false,at=@At(value="FIELD",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;moddedInvul:I",opcode=Opcodes.PUTFIELD,remap=false),require=1)
    private void ark$keepDamageWindow(Apostle self,int ticks,Operation<Void> original){
        if(LegacyApollyonDamageWindow.get(self)==null)original.call(self,ticks);
    }
}
