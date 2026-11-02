package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.combat.FourthCalamityAI;
import net.everla.projectc.arkofcosmos.combat.FourthCalamityActions;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Ordinary vanilla vitality; the 33 bars are a presentation of one health pool. */
public final class FourthCalamityEntity extends Monster {
    private static final EntityDataAccessor<Boolean> GLIDING=SynchedEntityData.defineId(FourthCalamityEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MACE_WINDUP=SynchedEntityData.defineId(FourthCalamityEntity.class,EntityDataSerializers.BOOLEAN);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("boss.arkofcosmos.the_player"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);
    public final FourthCalamityAI combat=new FourthCalamityAI(this);
    public final FourthCalamityActions actions=new FourthCalamityActions(this);
    private int lastBars=-1,lastPhase=-1;

    public FourthCalamityEntity(EntityType<? extends FourthCalamityEntity> type,Level level) {
        super(type,level);setPersistenceRequired();getNavigation().setCanFloat(true);
        equip(EquipmentSlot.HEAD,Items.NETHERITE_HELMET);
        equip(EquipmentSlot.CHEST,Items.NETHERITE_CHESTPLATE);
        equip(EquipmentSlot.LEGS,Items.NETHERITE_LEGGINGS);
        equip(EquipmentSlot.FEET,Items.NETHERITE_BOOTS);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,-1,0,false,false));
        for(EquipmentSlot slot:EquipmentSlot.values())setDropChance(slot,0);
        xpReward=0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(ArkOfCosmos.FOURTH_MAX_HEALTH.get(),660).add(Attributes.ATTACK_DAMAGE,2)
            .add(Attributes.MOVEMENT_SPEED,.36).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ATTACK_KNOCKBACK,1);
    }
    // Vanilla's global max-health attribute is capped at 1024. Use a native, synced attribute only for this boss.
    @Override public double getAttributeValue(Attribute attribute) { return super.getAttributeValue(attribute==Attributes.MAX_HEALTH?ArkOfCosmos.FOURTH_MAX_HEALTH.get():attribute); }
    @Override public AttributeInstance getAttribute(Attribute attribute) { return super.getAttribute(attribute==Attributes.MAX_HEALTH?ArkOfCosmos.FOURTH_MAX_HEALTH.get():attribute); }
    @Override public double getAttributeBaseValue(Attribute attribute) { return super.getAttributeBaseValue(attribute==Attributes.MAX_HEALTH?ArkOfCosmos.FOURTH_MAX_HEALTH.get():attribute); }
    private void equip(EquipmentSlot slot,Item item) {
        ItemStack stack=new ItemStack(item);stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION,4);
        stack.getOrCreateTag().putBoolean("Unbreakable",true);setItemSlot(slot,stack);
        getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(slot));
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() { super.defineSynchedData();entityData.define(GLIDING,false);entityData.define(MACE_WINDUP,false); }
    public boolean maceWindup() { return entityData.get(MACE_WINDUP); }
    public void setMaceWindup(boolean value) { entityData.set(MACE_WINDUP,value); }
    public int bars() { return Math.max(0,(int)Math.ceil(getHealth()/20)); }
    public int phase() { return getHealth()<=20?3:getHealth()<=320?2:1; }
    public float barProgress() { int count=bars();return count==0?0:(getHealth()-(count-1)*20)/20; }
    public boolean gliding() { return entityData.get(GLIDING); }
    public void setGliding(boolean value) { entityData.set(GLIDING,value);updateGlidingFlag(); }
    public void updateGlidingFlag() {
        boolean flying=gliding()&&!onGround()&&!isPassenger()&&!hasEffect(MobEffects.LEVITATION);
        setSharedFlag(7,flying);setPose(flying?Pose.FALL_FLYING:Pose.STANDING);
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return pose==Pose.FALL_FLYING?EntityDimensions.scalable(.6f,.6f):super.getDimensions(pose); }
    @Override protected float getStandingEyeHeight(Pose pose,EntityDimensions size) { return pose==Pose.FALL_FLYING?.4f:1.62f; }
    @Override public void teleportTo(double x,double y,double z) {
        super.teleportTo(x,y,z);net.everla.projectc.arkofcosmos.combat.vanilla.SpearCombat.resetMotion(this);
    }
    @Override protected void customServerAiStep() { if(isAlive())combat.tick(); }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) {
            if(isAlive()&&!hasEffect(MobEffects.DAMAGE_RESISTANCE))addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,-1,0,false,false));
            bar.setProgress(barProgress());
            bar.setVisible(isAlive());
            if(lastBars!=bars()||lastPhase!=phase()) {
                lastBars=bars();lastPhase=phase();
                bar.setColor(phase()==1?BossEvent.BossBarColor.RED:phase()==2?BossEvent.BossBarColor.PURPLE:BossEvent.BossBarColor.WHITE);
                for(ServerPlayer player:bar.getPlayers())syncBar(player,true);
            }
        }
    }
    private void syncBar(ServerPlayer player,boolean present) { ArkNetwork.fourthBossBar(player,bar.getId(),present?phase():0,present?bars():0); }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player);syncBar(player,true);bar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player);bar.removePlayer(player);syncBar(player,false); }
    @Override public boolean hurt(DamageSource source,float amount) {
        float before=getHealth();boolean hit=super.hurt(source,amount);
        if(hit&&combat!=null)combat.damaged(Math.max(0,before-getHealth()));return hit;
    }
    @Override public void die(DamageSource source) { super.die(source);clearCombat(); }
    private void clearCombat() {
        if(combat!=null)combat.stop();
        if(actions!=null)actions.cleanup();
        if(bar!=null){for(ServerPlayer player:bar.getPlayers())syncBar(player,false);bar.removeAllPlayers();}
    }
    @Override public void remove(RemovalReason reason) { if(!level().isClientSide)clearCombat();super.remove(reason); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag);setGliding(false);combat.stop(); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
