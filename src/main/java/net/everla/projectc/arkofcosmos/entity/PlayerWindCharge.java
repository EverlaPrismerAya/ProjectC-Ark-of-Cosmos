package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

public final class PlayerWindCharge extends Projectile implements ItemSupplier {
    private boolean ended;
    public PlayerWindCharge(EntityType<? extends PlayerWindCharge> type,Level level) { super(type,level); }
    @Override protected void defineSynchedData() {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag);tag.putInt("Age",tickCount); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag);tickCount=tag.getInt("Age"); }
    @Override public ItemStack getItem() { return new ItemStack(Items.FIRE_CHARGE); }
    @Override public void shoot(double x,double y,double z,float power,float uncertainty) {
        Vec3 movement=new Vec3(x,y,z).normalize().add(random.triangle(0,.0172275*uncertainty),random.triangle(0,.0172275*uncertainty),random.triangle(0,.0172275*uncertainty)).scale(power);
        setDeltaMovement(movement);double horizontal=movement.horizontalDistance();
        setYRot((float)(net.minecraft.util.Mth.atan2(movement.x,movement.z)*180/Math.PI));setXRot((float)(net.minecraft.util.Mth.atan2(movement.y,horizontal)*180/Math.PI));yRotO=getYRot();xRotO=getXRot();
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return !(tickCount<2&&distance<12.25)&&super.shouldRenderAtSqrDistance(distance); }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return .1f; }
    @Override protected AABB makeBoundingBox() { return super.makeBoundingBox().move(0,-.15f,0); }
    @Override protected boolean canHitEntity(Entity entity) { return !(entity instanceof PlayerWindCharge)&&!(entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal)&&super.canHitEntity(entity); }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide&&(getY()>level().getMaxBuildHeight()+30||getOwner()!=null&&getOwner().isRemoved()||!level().hasChunkAt(blockPosition()))) { if(getY()>level().getMaxBuildHeight()+30)burst(position());else discardAttack();return; }
        HitResult hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
        if(hit.getType()!=HitResult.Type.MISS) { setPos(hit.getLocation());if(!level().isClientSide)onHit(hit); }
        if(!isRemoved()) { Vec3 next=position().add(getDeltaMovement());setPos(next);updateRotation(); }
        checkInsideBlocks();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        Entity owner=getOwner();Entity target=hit.getEntity();
        if(owner instanceof LivingEntity living)living.setLastHurtMob(target);
        var source=new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,new net.minecraft.resources.ResourceLocation("arkofcosmos","wind_charge"))),this,owner);
        if(target.hurt(source,1)&&owner instanceof LivingEntity living)net.minecraft.world.item.enchantment.EnchantmentHelper.doPostDamageEffects(living,target);
        burst(position());
    }
    @Override protected void onHitBlock(BlockHitResult hit) { burst(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.25))); }
    private void burst(Vec3 at) { WindBurst.explode(level(),this,at,1.2f,1.22f,true);discardAttack(); }
    public void discardAttack() { ended=true;super.remove(RemovalReason.DISCARDED); }
    public boolean ending() { return ended; }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ended&&(getOwner()==null||!getOwner().isRemoved()))return;
        super.remove(reason);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(tickCount<5||source.getEntity()==null)return false;
        Entity deflector=source.getEntity();setDeltaMovement(deflector.getLookAngle().scale(1.5));setOwner(deflector);VanillaCombat.syncMotion(this);return true;
    }
    @Override public void push(double x,double y,double z) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
