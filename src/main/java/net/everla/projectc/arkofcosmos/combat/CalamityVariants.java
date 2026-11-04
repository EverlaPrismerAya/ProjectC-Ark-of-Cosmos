package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.UUID;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class CalamityVariants {
    public static final String VARIANT="ArkCalamityVariant";
    public static boolean special(ItemStack stack) {
        return stack.is(Items.WITHER_SKELETON_SKULL)&&stack.hasTag()
            &&stack.getTag().getCompound("BlockEntityTag").getCompound("ForgeData").getBoolean(VARIANT);
    }
    public static ItemStack skull() {
        ItemStack stack=new ItemStack(Items.WITHER_SKELETON_SKULL);
        CompoundTag data=new CompoundTag();data.putBoolean(VARIANT,true);
        stack.getOrCreateTagElement("BlockEntityTag").put("ForgeData",data);
        return stack;
    }
    public static boolean variant(LivingEntity entity) { return entity.getPersistentData().getBoolean(VARIANT); }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof WitherSkeleton skeleton))return;
        var data=skeleton.getPersistentData();
        if(data.getBoolean("ArkCalamityRolled"))return;
        data.putBoolean("ArkCalamityRolled",true);
        if(skeleton.getRandom().nextFloat()>=.2f)return;
        data.putBoolean(VARIANT,true);
        skeleton.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(
            UUID.fromString("c3dc897a-b71c-4ab0-a54e-29078762f2a0"),"Calamity health",.25,AttributeModifier.Operation.MULTIPLY_TOTAL));
        skeleton.getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier(
            UUID.fromString("45324ec4-ce47-4adb-b873-8515a1cb5ec9"),"Calamity attack",.15,AttributeModifier.Operation.MULTIPLY_TOTAL));
        skeleton.setHealth(skeleton.getMaxHealth());
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        var entity=event.getEntity();
        if((entity instanceof WitherSkeleton||entity instanceof WitherBoss)&&variant(entity)&&entity.level() instanceof ServerLevel level&&entity.tickCount%4==0)
            level.sendParticles(new DustParticleOptions(new Vector3f(1,.05f,.05f),1),entity.getX(),entity.getY()+1,entity.getZ(),3,.3,.6,.3,0);
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if(event.getEntity() instanceof WitherBoss&&variant(event.getEntity()))event.setAmount(event.getAmount()*.75f);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent event) {
        if(event.getAmount()>0&&!ElementsCombat.isResonanceDamage(event.getEntity())&&event.getSource().getEntity() instanceof WitherBoss boss&&variant(boss))
            ElementsCombat.applyResonance(event.getEntity(),boss,20);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        var entity=event.getEntity();
        if(!variant(entity))return;
        if(entity instanceof WitherSkeleton)entity.spawnAtLocation(skull());
        if(entity instanceof WitherBoss)entity.spawnAtLocation(new ItemStack(ArkOfCosmos.ASHES_OF_CALAMITY.get(),5+entity.getRandom().nextInt(4)));
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if(event.getItemStack().is(ArkOfCosmos.AURIC_BAR.get()))event.getToolTip().add(Component.translatable("tooltip.arkofcosmos.auric_bar.summon"));
        if(event.getItemStack().is(ArkOfCosmos.ASHES_OF_CALAMITY.get()))event.getToolTip().add(Component.translatable("tooltip.arkofcosmos.ashes_of_calamity.summon"));
        if(special(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.arkofcosmos.calamity_skull").withStyle(ChatFormatting.DARK_PURPLE));
            event.getToolTip().add(Component.translatable("tooltip.arkofcosmos.calamity_skull.summon").withStyle(ChatFormatting.GOLD));
        }
    }
}
