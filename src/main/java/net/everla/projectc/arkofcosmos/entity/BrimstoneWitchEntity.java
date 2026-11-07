package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.*;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasCombat;
import net.everla.projectc.arkofcosmos.combat.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Public, persistent town NPC. Retaliation never follows a player ownership chain. */
public final class BrimstoneWitchEntity extends PathfinderMob implements ProtectedBoss {
    private SupremeCalamitasCombat combat = new SupremeCalamitasCombat();
    private long lastAttack,combatSession;
    public long combatSession(){return combatSession;}
    private UUID attacker;
    private boolean leaving;
    private Player customer;
    public BrimstoneWitchEntity(EntityType<? extends BrimstoneWitchEntity> type, Level level) {
        super(type, level); setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25)
                .add(Attributes.FOLLOW_RANGE,64).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.6){@Override public boolean canUse(){return !fighting()&&!trading()&&super.canUse();}@Override public boolean canContinueToUse(){return !fighting()&&!trading()&&super.canContinueToUse();}});
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
    }
    public boolean fighting() { return getTarget()!=null; }
    public boolean trading() { return customer!=null; }
    public boolean customer(Player p) { return customer==p; }
    public boolean acquire(Player player) { if (fighting() || trading()) return false; customer=player; getNavigation().stop(); return true; }
    public void release(Player player) { if (customer==player) customer=null; }
    @Override public boolean hurt(DamageSource source,float amount) {
        if (!level().isClientSide && amount>0 && !WeaponUser.playerOwnedDamage(source)
                && source.getEntity() instanceof LivingEntity living && living!=this && !isAlliedTo(living)
                && !(living instanceof BrimstoneWitchEntity) && distanceToSqr(living)<=4096) {
            if(getTarget()==null)combatSession++;
            attacker=living.getUUID(); lastAttack=level().getGameTime(); setTarget(living);
        }
        return false;
    }
    @Override public float combatVitality() { return 20; }
    @Override public boolean redirectHealthWrite(float health) { return true; }
    @Override public boolean blocksHealthWrite(float health) { return true; }
    @Override public void ordinaryDamage(DamageSource source,float amount) { hurt(source,amount); }
    @Override public void kill() {}
    @Override public void die(DamageSource source) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        var journal=CalamitasBattleData.get(server); journal.begin(getUUID());
        if (customer!=null && (!customer.isAlive() || distanceToSqr(customer)>64 || !(customer.containerMenu instanceof net.everla.projectc.arkofcosmos.enchant.WitchMenu))) customer=null;
        if (attacker!=null && getTarget()==null && server.getEntity(attacker) instanceof LivingEntity living) setTarget(living);
        LivingEntity target=getTarget();
        if (target==null || !target.isAlive() || distanceToSqr(target)>4096 || server.getGameTime()-lastAttack>=200) {
            if (attacker!=null) { combatSession++; fadeAttacks(); attacker=null; setTarget(null); combat=new SupremeCalamitasCombat(); }
            setNoGravity(false); noPhysics=false; return;
        }
        if (customer!=null) customer.closeContainer();
        getNavigation().stop(); setNoGravity(true); noPhysics=true;
        double pixels=ProjectCCommonConfig.calamitasValue("pixels_per_block");
        Vec3 axis=target.position().subtract(position()).multiply(1,0,1).normalize();
        if (axis.lengthSqr()<.5) axis=new Vec3(1,0,0);
        for(int i=0;i<3;i++) {
            combat.step(getBoundingBox().getCenter().scale(pixels),target.getBoundingBox().getCenter().scale(pixels),
                    target.getBbWidth()*pixels/2,axis,true,false,1,1,new java.util.Random(random.nextLong()),shot->{
                var kind=CalamitasAttackEntity.Kind.valueOf(shot.kind().name());
                String skill=kind.name().toLowerCase(java.util.Locale.ROOT);
                int base=kind==CalamitasAttackEntity.Kind.DART?80:kind==CalamitasAttackEntity.Kind.GIGABLAST?105:95;
                CalamitasAttackEntity.spawn(this,kind,shot.position().scale(1/pixels),shot.velocity().scale(ProjectCCommonConfig.calamitasValue("movement_multiplier")/pixels),shot.planeX(),pixels,
                    ProjectCCommonConfig.calamitasDamage(skill,base),ProjectCCommonConfig.calamitasDamage("dart",80),shot.maximumSpeed()*ProjectCCommonConfig.calamitasValue("movement_multiplier")/pixels,shot.variant());
            });
            setPos(position().add(combat.velocity().scale(ProjectCCommonConfig.calamitasValue("movement_multiplier")/pixels)));
            if(combat.contact() && getBoundingBox().intersects(target.getBoundingBox()))
                WeaponDamage.hurt(target,damageSources().mobAttack(this),(float)ProjectCCommonConfig.calamitasDamage("contact",210));
        }
        getLookControl().setLookAt(target,30,30); setDeltaMovement(Vec3.ZERO);
    }
    public void fadeAttacks() {
        if(level() instanceof ServerLevel server) for(Entity e:server.getAllEntities())
            if(e instanceof CalamitasAttackEntity attack && getUUID().equals(attack.ownerUUID())) attack.fadeWitch();
    }
    public boolean protectRemoval(RemovalReason reason){return !level().isClientSide && !leaving && reason.shouldDestroy();}
    public void vanish() {
        leaving=true; fadeAttacks();
        if(level() instanceof ServerLevel server) CalamitasBattleData.get(server).end(getUUID());
        discard();
    }
    @Override public void remove(RemovalReason reason) { if(level().isClientSide || leaving || !reason.shouldDestroy()) super.remove(reason); }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level().isClientSide) return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer server && acquire(player)) NetworkHooks.openScreen(server,
            new net.minecraft.world.SimpleMenuProvider((id,inventory,p)->new net.everla.projectc.arkofcosmos.enchant.WitchMenu(id,inventory,this),
                net.minecraft.network.chat.Component.translatable("menu.arkofcosmos.witch")),b->b.writeVarInt(getId()));
        return InteractionResult.CONSUME;
    }
    public boolean collect(ServerPlayer player) {
        if(isRemoved() || fighting() || trading() || distanceToSqr(player)>36 || !player.hasLineOfSight(this)) return false;
        vanish(); ItemStack item=new ItemStack(CalamitasContent.LOCUS.get());
        if(!player.getInventory().add(item)) player.drop(item,false);
        return true;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.put("WitchCombat",combat.save()); tag.putLong("LastAttack",lastAttack);tag.putLong("CombatSession",combatSession);
        if(attacker!=null) tag.putUUID("Attacker",attacker);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); lastAttack=tag.getLong("LastAttack");combatSession=tag.getLong("CombatSession");
        attacker=tag.hasUUID("Attacker")?tag.getUUID("Attacker"):null;
        if(tag.contains("WitchCombat")) combat.load(tag.getCompound("WitchCombat"));
    }
}
