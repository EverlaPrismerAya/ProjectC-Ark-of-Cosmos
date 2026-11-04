package net.everla.projectc.arkofcosmos.mixin.client.gui;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LerpingBossEvent.class)
public interface BossBarProgressAccessor {
    @Accessor("targetPercent")
    float arkofcosmos$targetProgress();
}
