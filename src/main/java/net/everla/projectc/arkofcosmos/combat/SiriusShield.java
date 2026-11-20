package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Per-player state: switching stacks never refreshes the skill. Synced to observers and owner. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class SiriusShield {
    private static final EntityDataAccessor<Float> CAPACITY=ArkDamage.SIRIUS_CAPACITY;
    private static final EntityDataAccessor<Integer> TIME=ArkDamage.SIRIUS_TIME;
    private static final EntityDataAccessor<Integer> COOLDOWN=ArkDamage.SIRIUS_COOLDOWN;
    private static final EntityDataAccessor<Integer> HIT=ArkDamage.SIRIUS_HIT;
    public static void define(LivingEntity e) { var d=e.getEntityData();d.define(CAPACITY,0f);d.define(TIME,0);d.define(COOLDOWN,0);d.define(HIT,0); }
    public static float capacity(Player p) { return p.getEntityData().get(CAPACITY); }
    public static int time(Player p) { return p.getEntityData().get(TIME); }
    public static int cooldown(Player p) { return p.getEntityData().get(COOLDOWN); }
    public static int flash(Player p) { return p.getEntityData().get(HIT); }
    public static boolean holding(Player p) { return p.getMainHandItem().is(ArkOfCosmos.SIRIUS.get()); }
    public static void input(Player p,boolean right,boolean alt) {
        boolean pressed=right&&alt;
        boolean previous=p.getPersistentData().getBoolean("SiriusPressed");
        p.getPersistentData().putBoolean("SiriusPressed",pressed);
        if(!pressed||previous||!holding(p)||!p.isAlive()||p.isSpectator()||cooldown(p)>0)return;
        p.stopUsingItem();
        p.getEntityData().set(CAPACITY,(float)ProjectCCommonConfig.value("weapons.sirius.shield_capacity"));
        p.getEntityData().set(TIME,160);p.getEntityData().set(COOLDOWN,300);p.getPersistentData().putInt("SiriusCooldown",300);
    }
    /** Returns overflow after this shield; used once by each damage authority. */
    public static float absorb(LivingEntity e,float amount) {
        if(!(e instanceof Player p)||e.level().isClientSide||!Float.isFinite(amount)||amount<=0||time(p)<=0||capacity(p)<=0)return amount;
        float blocked=Math.min(amount,capacity(p));
        p.getEntityData().set(CAPACITY,capacity(p)-blocked);p.getEntityData().set(HIT,8);
        if(capacity(p)<=0)p.getEntityData().set(TIME,0);
        return amount-blocked;
    }
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if(!e.getLevel().isClientSide&&e.getEntity() instanceof Player p)p.getEntityData().set(COOLDOWN,p.getPersistentData().getInt("SiriusCooldown"));
    }
    @SubscribeEvent public static void clonePlayer(net.minecraftforge.event.entity.player.PlayerEvent.Clone e) {
        e.getEntity().getEntityData().set(COOLDOWN,cooldown(e.getOriginal()));
        e.getEntity().getPersistentData().putInt("SiriusCooldown",cooldown(e.getOriginal()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e) { e.setAmount(absorb(e.getEntity(),e.getAmount())); }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide)return;
        var p=e.player;var d=p.getEntityData();
        if(cooldown(p)>0)d.set(COOLDOWN,cooldown(p)-1);
        p.getPersistentData().putInt("SiriusCooldown",cooldown(p));
        if(flash(p)>0)d.set(HIT,flash(p)-1);
        if(time(p)>0)d.set(TIME,p.isAlive()?time(p)-1:0);
        if(time(p)<=0)d.set(CAPACITY,0f);
    }
}
