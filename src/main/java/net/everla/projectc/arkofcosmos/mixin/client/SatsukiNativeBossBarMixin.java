package net.everla.projectc.arkofcosmos.mixin.client;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.client.gui.GuiGraphics;
import net.everla.projectc.arkofcosmos.client.SatsukiBossBarFill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.network.chat.Component;
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
    @Unique private static float ark$phaseAlpha=1;

    @Inject(method="applyDissolve",at=@At("HEAD"),remap=false,require=0)
    private static void ark$phaseAlpha(float phase,CallbackInfo ci){
        ark$phaseAlpha=Mth.clamp(phase,0,1);
    }

    @Inject(method="getApollyonPercent",at=@At("RETURN"),cancellable=true,remap=false,require=0)
    private static void ark$rinPercent(Apostle apostle,float partial,CallbackInfoReturnable<Float> cir){
        ark$drawing=apostle;
        if(!(apostle instanceof SatsukiBoss))return;
        SatsukiBossBarFill.update(apostle,partial);
        float max=apostle.getMaxHealth();
        if(max<=0){cir.setReturnValue(0f);return;}
        // Rin synchronizes her authoritative health; RF's cached percent uses Apollyon's global maximum.
        cir.setReturnValue(Math.max(0,Math.min(1,apostle.getHealth()/max)));
    }

    @Inject(method="drawCosmicHealth(Lcom/mega/endinglib/util/mc/client/MegaGuiGraphics;Lnet/minecraft/resources/ResourceLocation;IIFFFFFIIFI)V",
            at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void ark$rainbowFill(@Coerce GuiGraphics graphics,ResourceLocation texture,int x,int y,
            float u,float v,float cosmicU,float cosmicV,float health,int height,int cosmicHeight,float alpha,int hurt,CallbackInfo ci){
        if(!(ark$drawing instanceof SatsukiBoss))return;
        SatsukiBossBarFill.fill(graphics,x,y,health,height,1);
        ci.cancel();
    }

    @Inject(method="drawDissolvedCosmicHealth(Lcom/mega/endinglib/util/mc/client/MegaGuiGraphics;Lnet/minecraft/resources/ResourceLocation;IIFFFIIFI)V",
            at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void ark$rainbowTransition(@Coerce GuiGraphics graphics,ResourceLocation texture,int x,int y,
            float u,float v,float health,int height,int cosmicHeight,float alpha,int hurt,CallbackInfo ci){
        if(!(ark$drawing instanceof SatsukiBoss))return;
        // RF multiplies cosmic alpha by its red hurt animation; use the phase animation independently.
        SatsukiBossBarFill.fill(graphics,x,y,health,height,ark$phaseAlpha);
        ci.cancel();
    }

    @ModifyArg(method="renderApollyonBar",remap=false,require=0,
            at=@At(value="INVOKE",target="Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",remap=true))
    private static String ark$rinKeys(String key){
        if(!(ark$drawing instanceof SatsukiBoss))return key;
        if(key.equals("name.goety_revelation.apollyon"))return "entity.arkofcosmos.satsuki_rin";
        if(key.startsWith("title.goety."))return "title.arkofcosmos.satsuki_rin."+key.substring("title.goety.".length());
        return key;
    }

    @ModifyArgs(method="renderOldApollyonBar",at=@At(value="INVOKE",
            target="Lcom/mega/revelationfix/client/screen/bossbar/ApollyonBossbar;renderClassicApollyonBar(Lcom/mega/endinglib/util/mc/client/MegaGuiGraphics;Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/chat/Component;ILnet/minecraft/resources/ResourceLocation;IIFZZI)I"),remap=false,require=0)
    private static void ark$oldNameAndTitle(Args args){
        if(!(ark$drawing instanceof SatsukiBoss))return;
        int title=((z1gned.goetyrevelation.util.ApollyonAbilityHelper)ark$drawing).allTitleApostle$getTitleNumber();
        Component label=ark$drawing.getName().copy().append(" · ").append(Component.translatable("title.arkofcosmos.satsuki_rin."+title));
        var minecraft=net.minecraft.client.Minecraft.getInstance();
        args.set(2,label);
        args.set(3,minecraft.getWindow().getGuiScaledWidth()/2-minecraft.font.width(label)/2);
        args.set(9,true);
    }
}
