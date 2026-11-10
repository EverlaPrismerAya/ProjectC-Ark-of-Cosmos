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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before RF 4.0's cancellable drawBar hook, retaining its frame and texture option. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.client.events.BossBarEvent",priority=1100,remap=false)
public abstract class SatsukiLegacyBossBarMixin {
    @Unique private static final ResourceLocation ark$nether=ResourceLocation.fromNamespaceAndPath("revelationfix","textures/ui/boss_bar.png");
    @Unique private static final ResourceLocation ark$normal=ResourceLocation.fromNamespaceAndPath("goety_revelation","textures/ui/boss_bar.png");

    @Inject(method="drawBar",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ark$legacyRin(GuiGraphics graphics,int x,int y,float partial,Mob mob,CallbackInfo ci){
        if(!(mob instanceof SatsukiBoss)||!(mob instanceof Apostle apostle))return;
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
        ci.cancel();
    }
}
