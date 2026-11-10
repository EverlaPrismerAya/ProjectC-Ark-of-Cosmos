package net.everla.projectc.arkofcosmos.mixin.client;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RF's native Apollyon boss bar, used for Satsuki Rin when the ProjectC bar is disabled.
 * RF scales the fill by GR's global {@code APOLLYON_HEALTH} and hard-codes the Apollyon name and Goety titles in the
 * full Nether bar; for Rin the fill follows her own maximum health and the name/titles are her own.
 */
@Pseudo
@Mixin(targets="com.mega.revelationfix.client.screen.bossbar.ApollyonBossbar",remap=false)
public abstract class SatsukiNativeBossBarMixin {
    /** The Apostle being drawn; getApollyonPercent runs first in renderApollyonBar, before the name is built. */
    @Unique private static Apostle ark$drawing;

    @Inject(method="getApollyonPercent",at=@At("RETURN"),cancellable=true,remap=false,require=0)
    private static void ark$rinPercent(Apostle apostle,float partial,CallbackInfoReturnable<Float> cir){
        ark$drawing=apostle;
        if(!(apostle instanceof SatsukiBoss))return;
        float max=apostle.getMaxHealth();
        if(max<=0){cir.setReturnValue(0f);return;}
        // Rin synchronizes her authoritative health; RF's cached percent uses Apollyon's global maximum.
        cir.setReturnValue(Math.max(0,Math.min(1,apostle.getHealth()/max)));
    }

    @ModifyArg(method="renderApollyonBar",remap=false,require=0,
            at=@At(value="INVOKE",target="Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",remap=true))
    private static String ark$rinKeys(String key){
        if(!(ark$drawing instanceof SatsukiBoss))return key;
        if(key.equals("name.goety_revelation.apollyon"))return "entity.arkofcosmos.satsuki_rin";
        if(key.startsWith("title.goety."))return "title.arkofcosmos.satsuki_rin."+key.substring("title.goety.".length());
        return key;
    }
}
