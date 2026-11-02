package net.everla.projectc.arkofcosmos.combat;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.minecraft.core.*;
import net.minecraft.util.Mth;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;

/** Performs actual vanilla actions. Damage forecasts never apply damage. */
public final class FourthCalamityActions {
    public static final double REACH=4.5;
    public static final int PEARL_COOLDOWN=60;
    private final FourthCalamityEntity boss;
    private FakePlayer operator;
    private ThrownEnderpearl pursuingPearl;
    private Vec3 pearlOrigin;
    private long useTick=Long.MIN_VALUE,attackTick=Long.MIN_VALUE,nextPearl;
    public FourthCalamityActions(FourthCalamityEntity boss) { this.boss=boss; }
    public void hold(Item item) {
        if(boss.getMainHandItem().is(item))return;
        boss.stopUsingItem();boss.getAttributes().removeAttributeModifiers(boss.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND));
        ItemStack stack=new ItemStack(item);stack.getOrCreateTag().putBoolean("Unbreakable",true);
        boss.setItemSlot(EquipmentSlot.MAINHAND,stack);
        boss.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
    }
    private boolean useBudget() {
        long now=boss.level().getServer().getTickCount();if(useTick==now)return false;useTick=now;return true;
    }
    public boolean attackBudget() {
        long now=boss.level().getServer().getTickCount();if(attackTick==now)return false;attackTick=now;return true;
    }
    public boolean terrainAllowed() { return ForgeEventFactory.getMobGriefingEvent(boss.level(),boss); }
    private FakePlayer operator(Item item) {
        ServerLevel level=(ServerLevel)boss.level();
        if(operator==null||operator.level()!=level)operator=new FakePlayer(level,new GameProfile(boss.getUUID(),"FourthCalamity"));
        operator.moveTo(boss.getX(),boss.getY(),boss.getZ(),boss.getYRot(),boss.getXRot());
        operator.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));operator.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        return operator;
    }
    public boolean reachable(Vec3 point,BlockPos clicked) {
        Level level=boss.level();
        if(!level.hasChunkAt(clicked)||!level.getWorldBorder().isWithinBounds(clicked)||level.isOutsideBuildHeight(clicked)
            ||boss.getEyePosition().distanceToSqr(point)>REACH*REACH)return false;
        BlockHitResult ray=level.clip(new ClipContext(boss.getEyePosition(),point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,boss));
        return ray.getType()==HitResult.Type.MISS||ray.getBlockPos().equals(clicked);
    }
    private boolean validHit(BlockHitResult hit) {
        if(!reachable(hit.getLocation(),hit.getBlockPos()))return false;
        Vec3 inside=hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.001));
        BlockHitResult actual=boss.level().clip(new ClipContext(boss.getEyePosition(),inside,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,boss));
        return actual.getType()==HitResult.Type.BLOCK&&actual.getBlockPos().equals(hit.getBlockPos())&&actual.getDirection()==hit.getDirection();
    }
    public boolean canPlace(BlockPos position) {
        if(!boss.level().hasChunkAt(position)||!boss.level().getBlockState(position).canBeReplaced()
            ||!boss.level().isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(),position,net.minecraft.world.phys.shapes.CollisionContext.empty()))return false;
        for(Direction direction:Direction.values()) {
            BlockPos support=position.relative(direction);if(!boss.level().hasChunkAt(support))continue;
            Direction face=direction.getOpposite();
            Vec3 point=Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
            if(validHit(new BlockHitResult(point,face,support,false)))return true;
        }
        return false;
    }
    public boolean canUse(BlockPos position) {
        if(!boss.level().hasChunkAt(position))return false;
        for(Direction face:Direction.values()) {
            Vec3 point=Vec3.atCenterOf(position).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
            if(validHit(new BlockHitResult(point,face,position,false)))return true;
        }
        return false;
    }
    private boolean interact(Item item,BlockHitResult hit) { return interact(item,hit,false); }
    private boolean interact(Item item,BlockHitResult hit,boolean detonate) {
        BlockPos pos=hit.getBlockPos();
        if(!terrainAllowed()||!boss.level().hasChunkAt(pos)||!boss.level().getWorldBorder().isWithinBounds(pos)
            ||boss.getEyePosition().distanceToSqr(hit.getLocation())>REACH*REACH||!detonate&&!validHit(hit))return false;
        FakePlayer player=operator(item);
        if(!boss.level().mayInteract(player,hit.getBlockPos())||!useBudget())return false;
        hold(item);boss.swing(InteractionHand.MAIN_HAND,true);
        CalamityAttackGuard.PLACING.set(boss);
        try { return player.gameMode.useItemOn(player,boss.level(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(); }
        finally { CalamityAttackGuard.PLACING.remove();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); }
    }
    public boolean place(Item item,BlockPos position) {
        if(!boss.level().hasChunkAt(position)||!boss.level().getBlockState(position).canBeReplaced())return false;
        for(Direction direction:Direction.values()) {
            BlockPos support=position.relative(direction);
            if(!boss.level().hasChunkAt(support)||boss.level().getBlockState(support).isAir())continue;
            Direction face=direction.getOpposite();Vec3 point=Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
            if(reachable(point,support)&&interact(item,new BlockHitResult(point,face,support,false)))return true;
        }
        return false;
    }
    private List<BlockHitResult> blockHits(BlockPos position) {
        List<BlockHitResult> hits=new ArrayList<>();
        if(!boss.level().hasChunkAt(position))return hits;
        var shape=boss.level().getBlockState(position).getShape(boss.level(),position);
        if(shape.isEmpty())return hits;
        AABB bounds=shape.bounds().move(position);
        for(Direction face:Direction.values())for(double u:new double[]{.5,.15,.85})for(double v:new double[]{.5,.15,.85}) {
            double x=Mth.lerp(u,bounds.minX,bounds.maxX),y=Mth.lerp(v,bounds.minY,bounds.maxY),z=Mth.lerp(v,bounds.minZ,bounds.maxZ);
            Vec3 point=switch(face) {
                case WEST->new Vec3(bounds.minX,y,z);case EAST->new Vec3(bounds.maxX,y,z);
                case DOWN->new Vec3(x,bounds.minY,z);case UP->new Vec3(x,bounds.maxY,z);
                case NORTH->new Vec3(x,y,bounds.minZ);case SOUTH->new Vec3(x,y,bounds.maxZ);
            };
            BlockHitResult hit=new BlockHitResult(point,face,position,false);if(validHit(hit))hits.add(hit);
        }
        return hits;
    }
    public boolean useBlock(Item item,BlockPos position) {
        for(var hit:blockHits(position))if(interact(item,hit))return true;
        return false;
    }
    public boolean detonate(BlockPos position) {
        if(!boss.level().hasChunkAt(position))return false;
        var state=boss.level().getBlockState(position);
        if(!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)&&!state.is(Blocks.RESPAWN_ANCHOR))return false;
        return interact(Items.OBSIDIAN,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false),true);
    }
    public boolean chargeAnchor(BlockPos position) {
        return interact(Items.GLOWSTONE,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false),true);
    }
    public boolean crystalSpace(BlockPos base) {
        Level level=boss.level();BlockPos above=base.above();
        return level.hasChunkAt(base)&&level.hasChunkAt(above.above())&&level.getWorldBorder().isWithinBounds(above)
            &&level.isEmptyBlock(above)&&level.getEntities(null,new AABB(above).expandTowards(0,1,0)).isEmpty();
    }
    public boolean crystal(BlockPos base) {
        if(!terrainAllowed()||!crystalSpace(base))return false;
        var state=boss.level().getBlockState(base);
        if(!state.is(Blocks.OBSIDIAN)&&!state.is(Blocks.BEDROCK)) { place(Items.OBSIDIAN,base);return false; }
        return useBlock(Items.END_CRYSTAL,base);
    }
    public EndCrystal bestCrystal(LivingEntity target) {
        EndCrystal best=null;double score=Double.NEGATIVE_INFINITY;
        for(EndCrystal crystal:boss.level().getEntitiesOfClass(EndCrystal.class,boss.getBoundingBox().inflate(REACH))) {
            if(!CalamityAttackGuard.owned(crystal,boss)||!canAttack(crystal))continue;
            double value=score(crystal.position(),target);
            if(value>score){score=value;best=crystal;}
        }
        return best;
    }
    public boolean canAttack(Entity target) {
        return target.isAlive()&&boss.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter())<=REACH*REACH
            &&(target instanceof EndCrystal||boss.level().clip(new ClipContext(boss.getEyePosition(),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS);
    }
    public boolean breakCrystal(EndCrystal crystal) {
        if(!terrainAllowed()||!canAttack(crystal)||!attackBudget())return false;
        boss.swing(InteractionHand.MAIN_HAND,true);return crystal.hurt(boss.damageSources().mobAttack(boss),1);
    }
    public BlockPos bestBase(LivingEntity target) {
        BlockPos best=null;double bestScore=Double.NEGATIVE_INFINITY;BlockPos center=boss.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,4,5))) {
            if(!boss.level().hasChunkAt(pos)||!crystalSpace(pos)||boss.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))>REACH*REACH)continue;
            var state=boss.level().getBlockState(pos);
            if(state.is(Blocks.OBSIDIAN)||state.is(Blocks.BEDROCK)) { if(!canUse(pos))continue; }
            else if(!canPlace(pos))continue;
            double value=score(Vec3.atBottomCenterOf(pos.above()),target);
            if(value>bestScore){bestScore=value;best=pos.immutable();}
        }
        return best;
    }
    public double score(Vec3 position,LivingEntity target) {
        float self=forecast(position,boss),enemy=forecast(position,target);
        return enemy<=0?Double.NEGATIVE_INFINITY:enemy-self*.5;
    }
    private float forecast(Vec3 at,LivingEntity target) {
        double distance=at.distanceTo(target.position())/12;
        if(distance>1)return 0;
        double exposure=Explosion.getSeenPercent(at,target),impact=(1-distance)*exposure;
        float damage=(float)((impact*impact+impact)*42+1);
        if(target instanceof net.minecraft.world.entity.player.Player)damage=switch(boss.level().getDifficulty()) {
            case PEACEFUL -> 0;case EASY -> Math.min(damage/2+1,damage);case HARD -> damage*1.5f;default -> damage;
        };
        damage=CombatRules.getDamageAfterAbsorb(damage,target.getArmorValue(),(float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance=target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if(resistance!=null)damage*=Math.max(0,1-.2f*(resistance.getAmplifier()+1));
        return CombatRules.getDamageAfterMagicAbsorb(damage,EnchantmentHelper.getDamageProtection(target.getArmorSlots(),boss.damageSources().explosion(null,boss)));
    }
    public boolean pearl(LivingEntity target) {
        if(boss.distanceToSqr(target)<=REACH*REACH)return false;
        long now=boss.level().getServer().getTickCount();if(now<nextPearl)return false;
        ThrownEnderpearl pearl=new ThrownEnderpearl(boss.level(),boss);
        Vec3 direction=pearlDirection(pearl.position(),target);
        if(direction==null||!useBudget())return false;
        hold(Items.ENDER_PEARL);pearl.shoot(direction.x,direction.y,direction.z,1.5f,0);
        boolean spawned=boss.level().addFreshEntity(pearl);nextPearl=now+PEARL_COOLDOWN;
        if(spawned){pursuingPearl=pearl;pearlOrigin=boss.position();}
        boss.swing(InteractionHand.MAIN_HAND,true);return spawned;
    }
    public boolean consumePearlArrival() {
        if(pursuingPearl==null||!pursuingPearl.isRemoved())return false;
        boolean arrived=boss.position().distanceToSqr(pursuingPearl.position())<9&&boss.position().distanceToSqr(pearlOrigin)>4;
        pursuingPearl=null;pearlOrigin=null;return arrived;
    }
    private Vec3 pearlDirection(Vec3 origin,LivingEntity target) {
        // Bounded trajectory search: native drag/gravity and block rays, without a client module or world writes.
        Vec3 best=null;double bestScore=9;
        Vec3 predicted=target.position().add(target.getDeltaMovement().scale(Math.min(8,boss.distanceTo(target)/1.5)));
        Vec3 delta=predicted.subtract(origin);
        for(double lift:new double[]{0,-.08,.08,.16,.3,.5}) {
            Vec3 direction=delta.add(0,delta.horizontalDistance()*lift,0).normalize(),velocity=direction.scale(1.5),position=origin;
            for(int step=0;step<60;step++) {
                Vec3 next=position.add(velocity);
                if(!boss.level().hasChunkAt(BlockPos.containing(next)))break;
                BlockHitResult hit=boss.level().clip(new ClipContext(position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
                var entityHit=target.getBoundingBox().inflate(.15).clip(position,next);
                boolean collision=hit.getType()!=HitResult.Type.MISS||entityHit.isPresent();
                if(collision) {
                    Vec3 impact=entityHit.isPresent()&&(hit.getType()==HitResult.Type.MISS||position.distanceToSqr(entityHit.get())<position.distanceToSqr(hit.getLocation()))?entityHit.get():hit.getLocation();
                    double score=impact.distanceToSqr(predicted);
                    if(score<bestScore){bestScore=score;best=direction;}break;
                }
                position=next;velocity=velocity.scale(.99).add(0,-.03,0);
            }
        }
        return best;
    }
    public boolean arrow(LivingEntity target,boolean harming) {
        if(!boss.hasLineOfSight(target)||!attackBudget())return false;
        double flight=boss.distanceTo(target)/3.15;
        Vec3 delta=target.position().add(0,target.getBbHeight()*.5,0).add(target.getDeltaMovement().scale(flight))
            .subtract(boss.getEyePosition().add(0,-.1,0)).add(0,.025*flight*flight,0);
        boss.setYRot((float)(Math.atan2(delta.z,delta.x)*180/Math.PI)-90);
        boss.setXRot((float)(-Math.atan2(delta.y,delta.horizontalDistance())*180/Math.PI));boss.setYHeadRot(boss.getYRot());
        hold(Items.CROSSBOW);ItemStack bow=boss.getMainHandItem();
        ItemStack ammo=PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW),harming?Potions.STRONG_HARMING:Potions.SLOW_FALLING);
        ListTag loaded=new ListTag();loaded.add(ammo.save(new CompoundTag()));
        bow.getOrCreateTag().put("ChargedProjectiles",loaded);CrossbowItem.setCharged(bow,true);
        CrossbowItem.performShooting(boss.level(),boss,InteractionHand.MAIN_HAND,bow,3.15f,1);
        CrossbowItem.setCharged(bow,false);boss.swing(InteractionHand.MAIN_HAND,true);return true;
    }
    public boolean melee(LivingEntity target) {
        if(boss.distanceToSqr(target)>9||!boss.hasLineOfSight(target)||!attackBudget())return false;
        boss.swing(InteractionHand.MAIN_HAND,true);return boss.doHurtTarget(target);
    }
    public void cleanup() {
        operator=null;pursuingPearl=null;pearlOrigin=null;
        if(boss.level() instanceof ServerLevel level) {
            List<Entity> attacks=new ArrayList<>();for(Entity entity:level.getAllEntities())if(CalamityAttackGuard.owned(entity,boss))attacks.add(entity);
            for(Entity entity:attacks){entity.getPersistentData().remove(CalamityAttackGuard.OWNER);entity.discard();}
        }
    }
}
