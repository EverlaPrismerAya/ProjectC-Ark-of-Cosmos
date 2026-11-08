package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Goety's complete Apostle circle animation, particles and sounds. */
@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.util.SummonApostle")
public abstract class CalamitasSummonMixin extends Entity {
    protected CalamitasSummonMixin(EntityType<?> type, Level level) { super(type, level); }

    @ModifyArg(method = {"tick", "m_8119_"}, remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V", remap = true), index = 0)
    private Component calamitasMessage(Component original) {
        // Custom name metadata is synchronized; Forge persistent data is server-only.
        return getCustomName() != null && getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("dialogue.arkofcosmos.calamitas.summoning") ? getCustomName() : original;
    }

    @Inject(method = {"tick", "m_8119_"}, remap = false, at = @At(value = "NEW", target = "com/Polarice3/Goety/common/entities/boss/Apostle", remap = false), cancellable = true)
    private void summonCalamitas(CallbackInfo ci) {
        var data = getPersistentData();
        if (!data.contains("ArkCalamitasSummon") || !(level() instanceof ServerLevel server)) return;
        EntityType.loadEntityRecursive(data.getCompound("ArkCalamitasSummon"), server, entity -> {
            entity.getPersistentData().putBoolean("ArkSummoningComplete", true);
            entity.setPos(position());
            if (entity instanceof SupremeCalamitasEntity boss && data.hasUUID("ArkCalamitasTarget")
                    && server.getEntity(data.getUUID("ArkCalamitasTarget")) instanceof net.minecraft.world.entity.LivingEntity target) boss.setTarget(target);
            server.addFreshEntity(entity);
            return entity;
        });
        discard();
        ci.cancel();
    }

    @Inject(method = {"addAdditionalSaveData", "m_7380_"}, remap = false, at = @At("TAIL"))
    private void saveClock(CompoundTag tag, CallbackInfo ci) {
        if (getPersistentData().contains("ArkCalamitasSummon")) tag.putInt("ArkSummonTicks", tickCount);
    }

    @Inject(method = {"readAdditionalSaveData", "m_7378_"}, remap = false, at = @At("TAIL"))
    private void loadClock(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("ArkSummonTicks")) tickCount = tag.getInt("ArkSummonTicks");
    }
}
