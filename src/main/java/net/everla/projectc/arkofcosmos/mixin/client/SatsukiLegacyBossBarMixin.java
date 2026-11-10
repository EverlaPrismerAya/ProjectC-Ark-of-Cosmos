package net.everla.projectc.arkofcosmos.mixin.client;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import net.everla.projectc.arkofcosmos.client.SatsukiBossBarFill;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bypass RF 4.0's cancellable drawBar hook at the outer event entry point. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.client.events.BossBarEvent",priority=1100,remap=false)
public abstract class SatsukiLegacyBossBarMixin {
    @Shadow public static java.util.Map<java.util.UUID,Mob> BOSS_BARS;
    @Unique private static final ResourceLocation ark$nether=ResourceLocation.fromNamespaceAndPath("revelationfix","textures/ui/boss_bar.png");
    @Unique private static final ResourceLocation ark$normal=ResourceLocation.fromNamespaceAndPath("goety_revelation","textures/ui/boss_bar.png");

    @Inject(method="renderBossBar",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ark$legacyRin(CustomizeGuiOverlayEvent.BossEventProgress event,CallbackInfo ci){
        if(net.everla.projectc.arkofcosmos.ProjectCClientConfig.SATSUKI_BOSS_BAR.get()
                ||!com.Polarice3.Goety.config.MainConfig.SpecialBossBar.get())return;
        Mob mob=BOSS_BARS.get(event.getBossEvent().getId());
        if(!(mob instanceof SatsukiBoss)||!(mob instanceof Apostle apostle))return;
        GuiGraphics graphics=event.getGuiGraphics();
        var minecraft=net.minecraft.client.Minecraft.getInstance();
        int x=minecraft.getWindow().getGuiScaledWidth()/2-100,y=event.getY();
        float partial=event.getPartialTick();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        boolean nether=false;
        try{
            nether=apostle.isInNether()&&Class.forName("com.mega.revelationfix.common.config.ClientConfig")
                    .getField("enableNewNetherApollyonBossbar").getBoolean(null);
        }catch(ReflectiveOperationException ignored){ /* Retain GR's standard frame if the option is unavailable. */ }
        SatsukiBossBarFill.update(apostle,partial);
        float max=apostle.getMaxHealth();
        float fraction=max>0?Mth.clamp(apostle.getHealth()/max,0,1):0;
        SatsukiBossBarFill.fill(graphics,x+9,y+4,fraction*182,8,1);
        graphics.blit(nether?ark$nether:ark$normal,x,y,0,apostle.isSecondPhase()?16:0,200,16,256,256);
        int title=((z1gned.goetyrevelation.util.ApollyonAbilityHelper)apostle).allTitleApostle$getTitleNumber();
        Component label=apostle.getName().copy().append(" · ").append(Component.translatable("title.arkofcosmos.satsuki_rin."+title));
        graphics.drawString(minecraft.font,label,x+100-minecraft.font.width(label)/2,y-9,0xFFFFFF);
        event.setCanceled(true);
        event.setIncrement(29);
        ci.cancel();
    }
}
