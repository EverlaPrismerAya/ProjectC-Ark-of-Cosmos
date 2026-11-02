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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkHooks;

/** Ordinary vanilla vitality; the 33 bars are a presentation of one health pool. */
public final class FourthCalamityEntity extends Monster {
    private static final EntityDataAccessor<Boolean> GLIDING=SynchedEntityData.defineId(FourthCalamityEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MACE_WINDUP=SynchedEntityData.defineId(FourthCalamityEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DEFEAT_TICKS=SynchedEntityData.defineId(FourthCalamityEntity.class,EntityDataSerializers.INT);
    public static final int DEFEAT_DURATION=100;
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("boss.arkofcosmos.the_player"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);
    public final FourthCalamityAI combat=new FourthCalamityAI(this);
    public final FourthCalamityActions actions=new FourthCalamityActions(this);
    private int lastBars=-1,lastPhase=-1;
    private Defense defense;
    private DamageSource defeatSource;

    /** One native hurt transaction, including event handlers which write health directly. */
    private final class Defense {
        final float armor=getArmorValue(),toughness=(float)getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        final float resistance=hasEffect(MobEffects.DAMAGE_RESISTANCE)?Math.max(0,1-.2f*(getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()+1)):1;
        final float absorption=getAbsorptionAmount();
        final float phaseMultiplier=phase()==3?.25f:1;
        final int protection;
        float limit;
        Defense(DamageSource source,float amount) {
            DamageSource calculation=new DamageSource(source.typeHolder(),source.getDirectEntity(),source.getEntity(),source.getSourcePosition()) {
                @Override public boolean is(TagKey<DamageType> tag) {
                    if(tag==DamageTypeTags.BYPASSES_ARMOR||tag==DamageTypeTags.BYPASSES_ENCHANTMENTS
                        ||tag==DamageTypeTags.BYPASSES_EFFECTS||tag==DamageTypeTags.BYPASSES_RESISTANCE
                        ||tag==DamageTypeTags.BYPASSES_INVULNERABILITY)return false;
                    return typeHolder().is(tag);
                }
            };
            protection=Mth.clamp(EnchantmentHelper.getDamageProtection(getArmorSlots(),calculation),0,20);
            budget(amount);
        }
        float armor(float amount) {
            float effective=Mth.clamp(armor-amount/(2+toughness/4),armor*.2f,20);
            return amount*(1-effective/25);
        }
        float magic(float amount) { return amount*resistance*(1-protection/25f)*phaseMultiplier; }
        void budget(float raw) { limit=Math.max(0,magic(armor(raw))-absorption); }
    }
    private boolean defend(DamageSource source) {
        if(source.getEntity()!=null||source.getDirectEntity()!=null)return true;
        var key=source.typeHolder().unwrapKey();
        if(key.isEmpty()||!key.get().location().getNamespace().equals("minecraft"))return true;
        // Preserve native environmental damage; reject runtime flags added to an otherwise ordinary source.
        for(var tag:java.util.List.of(DamageTypeTags.BYPASSES_ARMOR,DamageTypeTags.BYPASSES_ENCHANTMENTS,
            DamageTypeTags.BYPASSES_EFFECTS,DamageTypeTags.BYPASSES_RESISTANCE,DamageTypeTags.BYPASSES_INVULNERABILITY))
            if(source.is(tag)!=source.typeHolder().is(tag))return true;
        return false;
    }
    @Override protected float getDamageAfterArmorAbsorb(DamageSource source,float amount) {
        if(defense==null)return super.getDamageAfterArmorAbsorb(source,amount);
        defense.budget(amount);return defense.armor(amount);
    }
    @Override protected float getDamageAfterMagicAbsorb(DamageSource source,float amount) {
        if(defense==null)return super.getDamageAfterMagicAbsorb(source,amount)*(phase()==3?.25f:1);
        float resisted=amount*(1-defense.resistance);
        if(resisted>0&&resisted<Float.MAX_VALUE&&source.getEntity() instanceof ServerPlayer player)
            player.awardStat(net.minecraft.stats.Stats.DAMAGE_DEALT_RESISTED,Math.round(resisted*10));
        return defense.magic(amount);
    }
    @Override protected void actuallyHurt(DamageSource source,float amount) {
        Defense previous=defense;
        if(defense==null&&defend(source))defense=new Defense(source,amount);
        try { super.actuallyHurt(source,amount); }
        finally { defense=previous; }
    }
    @Override public void setHealth(float health) {
        if(defeated()){super.setHealth(1);return;}
        // Event callbacks must not apply damage before cancellation, i-frames or totem checks.
        if(defense!=null&&health<getHealth())return;
        super.setHealth(health);
    }
    /** Called only from vanilla actuallyHurt's health commit, after Forge damage events. */
    public void commitDamageHealth(DamageSource source,float health) {
        if(defeated())return;
        if(defense!=null&&health<getHealth())health=getHealth()-Math.min(getHealth()-health,defense.limit);
        if(phase()==3&&health<=0){beginDefeat(source);return;}
        super.setHealth(health);
    }
    public boolean defeated() { return entityData.hasItem(DEFEAT_TICKS)&&entityData.get(DEFEAT_TICKS)>=0; }
    public int defeatTicks() { return defeated()?entityData.get(DEFEAT_TICKS):0; }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(key==DEFEAT_TICKS&&defeated()&&getBbHeight()>.4f)refreshDimensions();
    }
    private void beginDefeat(DamageSource source) {
        if(defeated())return;
        super.setHealth(1);defeatSource=source;entityData.set(DEFEAT_TICKS,0);
        // Keep vanilla travel/gravity active; customServerAiStep gates all combat while defeated.
        clearCombat();setTarget(null);setNoAi(false);stopRiding();removeAllEffects();
        setXxa(0);setYya(0);setZza(0);getMoveControl().setWantedPosition(getX(),getY(),getZ(),0);
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        refreshDimensions();
    }

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
            .add(Attributes.MOVEMENT_SPEED,.36).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ATTACK_KNOCKBACK,1).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    @Override public void knockback(double strength,double x,double z) {}
    @Override public void push(double x,double y,double z) {}
    @Override public boolean isPushable() { return false; }
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
    @Override protected void defineSynchedData() { super.defineSynchedData();entityData.define(GLIDING,false);entityData.define(MACE_WINDUP,false);entityData.define(DEFEAT_TICKS,-1); }
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
    @Override public EntityDimensions getDimensions(Pose pose) { return defeated()?EntityDimensions.scalable(.6f,.35f):pose==Pose.FALL_FLYING?EntityDimensions.scalable(.6f,.6f):super.getDimensions(pose); }
    @Override protected float getStandingEyeHeight(Pose pose,EntityDimensions size) { return defeated()?.2f:pose==Pose.FALL_FLYING?.4f:1.62f; }
    @Override public void teleportTo(double x,double y,double z) {
        super.teleportTo(x,y,z);net.everla.projectc.arkofcosmos.combat.vanilla.SpearCombat.resetMotion(this);
    }
    @Override protected void customServerAiStep() { if(isAlive()&&!defeated())combat.tick(); }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) {
            if(defeated()) {
                setDeltaMovement(0,Math.min(0,getDeltaMovement().y),0);
                int elapsed=defeatTicks()+1;entityData.set(DEFEAT_TICKS,elapsed);
                if(elapsed>=DEFEAT_DURATION) {
                    super.setHealth(0);super.die(defeatSource!=null?defeatSource:damageSources().generic());
                    remove(RemovalReason.KILLED);
                }
                return;
            }
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
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player);if(!defeated()){syncBar(player,true);bar.addPlayer(player);} }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player);bar.removePlayer(player);syncBar(player,false); }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(defeated())return false;
        Defense previous=defense;
        defense=defend(source)?new Defense(source,amount):null;
        try {
            float before=getHealth();boolean hit=super.hurt(source,amount);
            if(hit&&combat!=null&&!defeated())combat.damaged(Math.max(0,before-getHealth()));return hit;
        } finally { defense=previous; }
    }
    @Override public void die(DamageSource source) {
        if(defeated())return;
        super.die(source);clearCombat();
    }
    @Override public boolean isAttackable() { return !defeated()&&super.isAttackable(); }
    private void clearCombat() {
        if(combat!=null)combat.stop();
        if(actions!=null)actions.cleanup();
        if(bar!=null){for(ServerPlayer player:bar.getPlayers())syncBar(player,false);bar.removeAllPlayers();}
    }
    @Override public void remove(RemovalReason reason) { if(!level().isClientSide)clearCombat();super.remove(reason); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(defeated())tag.putInt("FourthDefeatTicks",defeatTicks());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);setGliding(false);combat.stop();
        if(tag.contains("FourthDefeatTicks")) {
            beginDefeat(damageSources().generic());
            entityData.set(DEFEAT_TICKS,Mth.clamp(tag.getInt("FourthDefeatTicks"),0,DEFEAT_DURATION-1));
        }
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
