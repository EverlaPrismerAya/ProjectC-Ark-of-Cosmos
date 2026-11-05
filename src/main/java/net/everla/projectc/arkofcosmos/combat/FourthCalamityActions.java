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
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;

/** Performs actual vanilla actions. Damage forecasts never apply damage. */
public final class FourthCalamityActions {
    public enum CrystalResult {
        BASE_PLACED, CRYSTAL_PLACED, DETONATED, WAITING, BLOCKED;
        public boolean progressed() { return this==BASE_PLACED||this==CRYSTAL_PLACED||this==DETONATED; }
    }
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    public static final double REACH=4.5;
    public static final int PEARL_COOLDOWN=60;
    private final FourthCalamityEntity boss;
    private FakePlayer operator;
    private ThrownEnderpearl pursuingPearl;
    private Vec3 pearlOrigin;
    private boolean retreatingPearl;
    private long useTick=Long.MIN_VALUE,attackTick=Long.MIN_VALUE,nextPearl;
    private BlockPos miningBlock;
    private float miningProgress;
    private final Map<BlockPos,Integer> deniedMining=new HashMap<>();
    private UUID pendingCrystal;
    private UUID explodedCrystal;
    private String failure="NONE",nativeResult="NONE";
    private final Map<BlockPos,Integer> rejectedBases=new HashMap<>();
    private final Map<String,Integer> diagnosticTicks=new HashMap<>();
    public FourthCalamityActions(FourthCalamityEntity boss) { this.boss=boss; }
    public void hold(Item item) {
        if(boss.getMainHandItem().is(item)){enchantWeapon(boss.getMainHandItem());return;}
        boss.stopUsingItem();boss.getAttributes().removeAttributeModifiers(boss.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND));
        ItemStack stack=new ItemStack(item);stack.getOrCreateTag().putBoolean("Unbreakable",true);
        enchantWeapon(stack);
        boss.setItemSlot(EquipmentSlot.MAINHAND,stack);
        boss.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
    }
    static void enchantWeapon(ItemStack stack) {
        Item item=stack.getItem();
        if(item instanceof net.everla.projectc.arkofcosmos.item.SpearItem||item==Items.NETHERITE_SWORD) {
            enchant(stack,Enchantments.SHARPNESS,5);
            enchant(stack,Enchantments.SMITE,5);
            enchant(stack,Enchantments.BANE_OF_ARTHROPODS,5);
            if(net.minecraftforge.fml.ModList.get().isLoaded("goety_revelation")) {
                var piercer=net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety_revelation","reality_piercer"));
                if(piercer!=null)enchant(stack,piercer,10);
            }
        }
        if(item instanceof net.everla.projectc.arkofcosmos.item.SpearItem) {
            enchant(stack,Enchantments.FIRE_ASPECT,2);
        } else if(item==VanillaCombat.MACE.get())enchant(stack,VanillaCombat.DENSITY.get(),5);
        else if(item==Items.NETHERITE_SWORD) {
            enchant(stack,Enchantments.KNOCKBACK,2);enchant(stack,Enchantments.FIRE_ASPECT,2);
        } else if(item==Items.CROSSBOW) {
            enchant(stack,Enchantments.QUICK_CHARGE,3);enchant(stack,Enchantments.MULTISHOT,1);
        } else if(item==Items.BOW) {
            enchant(stack,Enchantments.POWER_ARROWS,5);enchant(stack,Enchantments.FLAMING_ARROWS,1);enchant(stack,Enchantments.PUNCH_ARROWS,2);
        }
    }
    private static void enchant(ItemStack stack,net.minecraft.world.item.enchantment.Enchantment enchantment,int level) {
        if(EnchantmentHelper.getItemEnchantmentLevel(enchantment,stack)==level)return;
        var enchantments=EnchantmentHelper.getEnchantments(stack);enchantments.put(enchantment,level);
        EnchantmentHelper.setEnchantments(enchantments,stack);
    }
    private boolean useBudget() {
        long now=boss.level().getServer().getTickCount();if(useTick==now)return false;useTick=now;return true;
    }
    public boolean attackBudget() {
        long now=boss.level().getServer().getTickCount();if(attackTick==now)return false;attackTick=now;boss.attackDialogue();return true;
    }
    public boolean attackAvailable() { return attackTick!=boss.level().getServer().getTickCount(); }
    public void diagnostic(String step,BlockPos base,CrystalResult result,String reason) {
        if(!Boolean.getBoolean("arkofcosmos.crystalDebug")&&!boss.getTags().contains("ark_crystal_debug"))return;
        String key=step+":"+result;
        if(boss.tickCount<diagnosticTicks.getOrDefault(key,Integer.MIN_VALUE))return;
        diagnosticTicks.put(key,boss.tickCount+20);
        LOGGER.info("[FourthCrystal] boss={} phase={} tick={} step={} base={} crystal={} result={} reason={} native={}",
            boss.getUUID(),boss.phase(),boss.tickCount,step,base,pendingCrystal,result,reason,step.contains("SELECT")?"NONE":nativeResult);
    }
    public void interactionDenied(String reason) { failure=reason; }
    public void crystalExplosion(EndCrystal crystal,boolean cancelled) {
        if(cancelled)failure="EXPLOSION_PROTECTION_DENIED";
        else explodedCrystal=crystal.getUUID();
    }
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
        return placementHit(position)!=null;
    }
    public boolean canUse(BlockPos position) {
        return blockHit(position,null)!=null;
    }
    public boolean canPlaceCrystalCover(BlockPos pos) {
        return boss.tickCount>=rejectedBases.getOrDefault(pos,Integer.MIN_VALUE)&&canPlace(pos);
    }
    public CrystalResult crystalCover(BlockPos pos) {
        failure="NONE";nativeResult="NONE";
        place(Items.OBSIDIAN,pos);
        if(boss.level().getBlockState(pos).is(Blocks.OBSIDIAN)) {
            diagnostic("COVER",pos,CrystalResult.BASE_PLACED,"CONFIRMED");return CrystalResult.BASE_PLACED;
        }
        return crystalFailure(pos,"COVER");
    }
    public boolean scaffold(BlockPos pos) {
        if(!net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(boss.level(),boss)
            ||!boss.level().getFluidState(pos).isEmpty()||!canPlace(pos))return false;
        place(Items.COBBLESTONE,pos);
        return boss.level().getBlockState(pos).is(Blocks.COBBLESTONE);
    }
    private BlockHitResult placementHit(BlockPos position) {
        for(Direction direction:Direction.values()) {
            BlockPos support=position.relative(direction);
            if(!boss.level().hasChunkAt(support)||boss.level().getBlockState(support).canBeReplaced())continue;
            BlockHitResult hit=blockHit(support,direction.getOpposite());
            if(hit!=null)return hit;
        }
        return null;
    }
    private boolean interact(Item item,BlockHitResult hit) { return interact(item,hit,false); }
    private boolean interact(Item item,BlockHitResult hit,boolean detonate) {
        BlockPos pos=hit.getBlockPos();
        failure="NONE";nativeResult="NONE";
        if(!boss.level().hasChunkAt(pos)||boss.level().isOutsideBuildHeight(pos)||!boss.level().getWorldBorder().isWithinBounds(pos)){failure="WORLD_BOUNDS";return false;}
        if(boss.getEyePosition().distanceToSqr(hit.getLocation())>REACH*REACH){failure="OUT_OF_REACH";return false;}
        if(!detonate&&!validHit(hit)){failure="RAY_BLOCKED";return false;}
        FakePlayer player=operator(item);
        if(!boss.level().mayInteract(player,hit.getBlockPos())){failure="PROTECTION_DENIED";return false;}
        if(!useBudget()){failure="USE_BUDGET";return false;}
        hold(item);boss.swing(InteractionHand.MAIN_HAND,true);
        CalamityAttackGuard.PLACING.set(boss);
        try {
            InteractionResult result=player.gameMode.useItemOn(player,boss.level(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            nativeResult=result.name();
            if(!result.consumesAction()&&failure.equals("NONE"))failure="NATIVE_REJECTED";
            return result.consumesAction();
        }
        finally { CalamityAttackGuard.PLACING.remove();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); }
    }
    public boolean place(Item item,BlockPos position) {
        if(!boss.level().hasChunkAt(position)||!boss.level().getBlockState(position).canBeReplaced()){failure="BASE_OCCUPIED";return false;}
        BlockHitResult hit=placementHit(position);
        if(hit==null){failure="NO_REACHABLE_SUPPORT_FACE";return false;}
        return interact(item,hit);
    }
    private BlockHitResult blockHit(BlockPos position,Direction requiredFace) {
        if(!boss.level().hasChunkAt(position))return null;
        var shape=boss.level().getBlockState(position).getShape(boss.level(),position);
        if(shape.isEmpty())return null;
        AABB bounds=shape.bounds().move(position);
        for(Direction face:Direction.values()) {
            if(requiredFace!=null&&face!=requiredFace)continue;
            for(double u:new double[]{.5,.15,.85})for(double v:new double[]{.5,.15,.85}) {
                double x=Mth.lerp(u,bounds.minX,bounds.maxX),y=Mth.lerp(v,bounds.minY,bounds.maxY),z=Mth.lerp(v,bounds.minZ,bounds.maxZ);
                Vec3 point=switch(face) {
                    case WEST->new Vec3(bounds.minX,y,z);case EAST->new Vec3(bounds.maxX,y,z);
                    case DOWN->new Vec3(x,bounds.minY,z);case UP->new Vec3(x,bounds.maxY,z);
                    case NORTH->new Vec3(x,y,bounds.minZ);case SOUTH->new Vec3(x,y,bounds.maxZ);
                };
                BlockHitResult hit=new BlockHitResult(point,face,position,false);if(validHit(hit))return hit;
            }
        }
        return null;
    }
    public boolean useBlock(Item item,BlockPos position) {
        var hit=blockHit(position,null);
        if(hit==null){failure="NO_REACHABLE_BLOCK_FACE";return false;}
        // One native attempt: another face must not bypass a protection rejection.
        return interact(item,hit);
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
    public boolean validCrystalBase(BlockPos base) {
        return crystalBaseRejection(base)==null;
    }
    public String crystalBaseRejection(BlockPos base) {
        return crystalBaseRejection(base,false);
    }
    private String crystalBaseRejection(BlockPos base,boolean ignoreEntities) {
        if(base==null)return "NO_CANDIDATE";
        if(boss.tickCount<rejectedBases.getOrDefault(base,Integer.MIN_VALUE))return "RETRY_COOLDOWN";
        Level level=boss.level();
        if(!level.hasChunkAt(base)||!level.hasChunkAt(base.above(2)))return "UNLOADED";
        if(level.isOutsideBuildHeight(base)||level.isOutsideBuildHeight(base.above())
            ||!level.getWorldBorder().isWithinBounds(base))return "WORLD_BOUNDS";
        if(!level.isEmptyBlock(base.above()))return "BLOCK_ABOVE";
        if(!ignoreEntities&&!level.getEntities(null,new AABB(base.above()).expandTowards(0,1,0)).isEmpty())return "ENTITY_OCCUPIED";
        Vec3 eye=boss.getEyePosition();
        Vec3 nearest=new Vec3(Mth.clamp(eye.x,base.getX(),base.getX()+1),Mth.clamp(eye.y,base.getY(),base.getY()+1),Mth.clamp(eye.z,base.getZ(),base.getZ()+1));
        if(eye.distanceToSqr(nearest)>REACH*REACH)return "OUT_OF_REACH";
        var state=boss.level().getBlockState(base);
        if(state.is(Blocks.OBSIDIAN)||state.is(Blocks.BEDROCK))return canUse(base)?null:"RAY_BLOCKED";
        if(!state.canBeReplaced())return "BASE_OCCUPIED";
        if(!ignoreEntities&&!level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(),base,net.minecraft.world.phys.shapes.CollisionContext.empty()))return "BASE_ENTITY_OCCUPIED";
        return placementHit(base)!=null?null:"NO_REACHABLE_SUPPORT_FACE";
    }
    boolean crystalsBlockedByEntities(LivingEntity target) {
        if(bestCrystal(target)!=null)return false;
        boolean occupied=false;
        BlockPos center=boss.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,4,5))) {
            String reason=crystalBaseRejection(pos);
            if(reason==null)return false;
            if(("ENTITY_OCCUPIED".equals(reason)||"BASE_ENTITY_OCCUPIED".equals(reason))
                &&crystalBaseRejection(pos,true)==null
                &&!boss.level().getEntitiesOfClass(Mob.class,new AABB(pos).expandTowards(0,2,0),
                    mob->mob!=boss&&mob.isAlive()&&mob.getTarget()==boss).isEmpty())occupied=true;
        }
        return occupied;
    }
    boolean crystalSpaceAvailable(LivingEntity target) {
        if(bestCrystal(target)!=null)return true;
        BlockPos center=boss.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,4,5))) {
            if(crystalBaseRejection(pos)==null&&score(Vec3.atBottomCenterOf(pos.above()),target)>Double.NEGATIVE_INFINITY)return true;
        }
        return false;
    }
    /** Meteor KillAura multi-target pass: one timing gate, then attack every selected entity. */
    void killAura() {
        if(!attackBudget())return;
        for(Entity target:boss.level().getEntities(boss,boss.getBoundingBox().inflate(6),
            e->e.isAlive()&&!e.isSpectator()&&!boss.isAlliedTo(e)
                &&!(e instanceof FourthCalamityEntity)&&!CalamityAttackGuard.owned(e,boss)
                &&(!(e instanceof net.minecraft.world.entity.player.Player p)||!p.isCreative())
                &&e.getBoundingBox().distanceToSqr(boss.position())<=36)) {
            boss.doHurtTarget(target);
        }
        boss.swing(InteractionHand.MAIN_HAND,true);
    }
    public EndCrystal pendingCrystal() {
        if(pendingCrystal==null)return null;
        Entity entity=((ServerLevel)boss.level()).getEntity(pendingCrystal);
        if(entity instanceof EndCrystal crystal&&crystal.isAlive())return crystal;
        pendingCrystal=null;return null;
    }
    public void forgetPendingCrystal() { pendingCrystal=null; }
    private CrystalResult crystalFailure(BlockPos base,String step) {
        boolean waiting=failure.equals("USE_BUDGET")||failure.equals("ATTACK_BUDGET");
        if(!waiting&&base!=null)rejectedBases.put(base.immutable(),boss.tickCount+20);
        CrystalResult result=waiting?CrystalResult.WAITING:CrystalResult.BLOCKED;
        diagnostic(step,base,result,failure);return result;
    }
    public CrystalResult crystal(BlockPos base) {
        rejectedBases.entrySet().removeIf(entry->boss.tickCount>=entry.getValue());
        failure="NONE";nativeResult="NONE";
        String rejected=crystalBaseRejection(base);
        if(rejected!=null){failure=rejected;return crystalFailure(base,"PLACE");}
        var state=boss.level().getBlockState(base);
        if(!state.is(Blocks.OBSIDIAN)&&!state.is(Blocks.BEDROCK)) {
            place(Items.OBSIDIAN,base);
            if(boss.level().getBlockState(base).is(Blocks.OBSIDIAN)) {
                diagnostic("SUPPORT",base,CrystalResult.BASE_PLACED,"CONFIRMED");return CrystalResult.BASE_PLACED;
            }
            return crystalFailure(base,"SUPPORT");
        }
        useBlock(Items.END_CRYSTAL,base);
        EndCrystal crystal=crystalAt(base);
        if(crystal!=null) {
            pendingCrystal=crystal.getUUID();diagnostic("PLACE",base,CrystalResult.CRYSTAL_PLACED,"CONFIRMED");
            return CrystalResult.CRYSTAL_PLACED;
        }
        if(failure.equals("NONE"))failure="ENTITY_NOT_CREATED";
        return crystalFailure(base,"PLACE");
    }
    public EndCrystal crystalAt(BlockPos base) {
        return boss.level().getEntitiesOfClass(EndCrystal.class,new AABB(base.above()))
            .stream().filter(c->c.isAlive()&&CalamityAttackGuard.owned(c,boss)
                &&c.blockPosition().equals(base.above())).findFirst().orElse(null);
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
    public CrystalResult detonateCrystal(EndCrystal crystal) {
        failure="NONE";nativeResult="NONE";explodedCrystal=null;
        if(!canAttack(crystal))failure="CRYSTAL_OUT_OF_REACH";
        else if(!attackBudget())failure="ATTACK_BUDGET";
        else {
            boss.swing(InteractionHand.MAIN_HAND,true);
            boolean hit;
            CalamityAttackGuard.DETONATING.set(boss);
            try { hit=crystal.hurt(boss.damageSources().mobAttack(boss),1); }
            finally { CalamityAttackGuard.DETONATING.remove(); }
            nativeResult=Boolean.toString(hit);
            if(hit&&crystal.isRemoved()&&crystal.getRemovalReason()==Entity.RemovalReason.KILLED&&crystal.getUUID().equals(explodedCrystal)) {
                diagnostic("BREAK",crystal.blockPosition().below(),CrystalResult.DETONATED,"CONFIRMED");
                if(crystal.getUUID().equals(pendingCrystal))pendingCrystal=null;
                return CrystalResult.DETONATED;
            }
            if(failure.equals("NONE"))failure="ATTACK_OR_EXPLOSION_REJECTED";
        }
        return crystalFailure(null,"BREAK");
    }
    public BlockPos bestBase(LivingEntity target) {
        return bestBase(target,false);
    }
    public boolean comboBase(BlockPos pos) {
        return pos!=null&&pos.getY()==boss.blockPosition().getY()
            &&(boss.level().getBlockState(pos).canBeReplaced()||boss.level().getBlockState(pos).is(Blocks.OBSIDIAN));
    }
    public BlockPos bestBase(LivingEntity target,boolean raisedCombo) {
        rejectedBases.entrySet().removeIf(entry->boss.tickCount>=entry.getValue());
        Map<String,Integer> rejected=new TreeMap<>();
        BlockPos best=null;double bestScore=Double.NEGATIVE_INFINITY;BlockPos center=boss.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,4,5))) {
            if(raisedCombo&&!comboBase(pos))continue;
            String reason=crystalBaseRejection(pos);
            if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
            double value=score(Vec3.atBottomCenterOf(pos.above()),target);
            if(value>bestScore){bestScore=value;best=pos.immutable();}
        }
        if(best==null)diagnostic("SELECT",null,CrystalResult.BLOCKED,"NO_LEGAL_CANDIDATE "+rejected);
        return best;
    }
    public double score(Vec3 position,LivingEntity target) {
        float self=forecast(position,boss,false),enemy=forecast(position,target,true);
        return enemy<=0?Double.NEGATIVE_INFINITY:enemy-self*.5;
    }
    private float forecast(Vec3 at,LivingEntity target,boolean targetRanking) {
        double distance=at.distanceTo(target.position())/12;
        if(distance>1)return 0;
        double exposure=Explosion.getSeenPercent(at,target),impact=(1-distance)*exposure;
        float damage=(float)((impact*impact+impact)*42+1);
        if(target instanceof net.minecraft.world.entity.player.Player)damage=switch(boss.level().getDifficulty()) {
            case PEACEFUL -> 0;case EASY -> Math.min(damage/2+1,damage);case HARD -> damage*1.5f;default -> damage;
        };
        // Rank enemies by raw blast potential; mitigation only belongs in self-risk prediction.
        if(targetRanking)return damage;
        damage=CombatRules.getDamageAfterAbsorb(damage,target.getArmorValue(),(float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance=target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if(resistance!=null)damage*=Math.max(0,1-.2f*(resistance.getAmplifier()+1));
        return CombatRules.getDamageAfterMagicAbsorb(damage,EnchantmentHelper.getDamageProtection(target.getArmorSlots(),boss.damageSources().explosion(null,boss)))*(target==boss?.5f:1);
    }
    private Item miningTool(net.minecraft.world.level.block.state.BlockState state) {
        Item best=Items.NETHERITE_PICKAXE;float speed=0;
        for(Item tool:new Item[]{Items.NETHERITE_PICKAXE,Items.NETHERITE_AXE,Items.NETHERITE_SHOVEL,Items.NETHERITE_HOE}) {
            float value=new ItemStack(tool).getDestroySpeed(state);
            if(value>speed){speed=value;best=tool;}
        }
        return best;
    }
    public double miningCost(BlockPos pos) {
        if(!boss.level().hasChunkAt(pos)||!boss.level().getWorldBorder().isWithinBounds(pos)||boss.level().isOutsideBuildHeight(pos))return Double.POSITIVE_INFINITY;
        var state=boss.level().getBlockState(pos);
        if(!state.getFluidState().isEmpty())return Double.POSITIVE_INFINITY;
        if(state.getCollisionShape(boss.level(),pos).isEmpty())return 0;
        float hardness=state.getDestroySpeed(boss.level(),pos);
        if(hardness<0||boss.tickCount<deniedMining.getOrDefault(pos,0))return Double.POSITIVE_INFINITY;
        ItemStack tool=new ItemStack(miningTool(state));
        return 1+hardness*30/(tool.getDestroySpeed(state)+26);
    }
    public void stopMining() {
        if(miningBlock!=null)boss.level().destroyBlockProgress(boss.getId(),miningBlock,-1);
        miningBlock=null;miningProgress=0;
    }
    public boolean mine(BlockPos pos) {
        if(!Double.isFinite(miningCost(pos))||!net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(boss.level(),boss))return false;
        Vec3 center=Vec3.atCenterOf(pos);
        if(!reachable(center,pos))return false;
        var state=boss.level().getBlockState(pos);
        if(state.getCollisionShape(boss.level(),pos).isEmpty())return false;
        if(!pos.equals(miningBlock)){stopMining();miningBlock=pos.immutable();}
        Item tool=miningTool(state);hold(tool);enchant(boss.getMainHandItem(),Enchantments.BLOCK_EFFICIENCY,5);
        FakePlayer player=operator(tool);player.setItemInHand(InteractionHand.MAIN_HAND,boss.getMainHandItem().copy());player.setOnGround(boss.onGround());
        miningProgress+=state.getDestroyProgress(player,boss.level(),pos);
        boss.swing(InteractionHand.MAIN_HAND,true);boss.level().destroyBlockProgress(boss.getId(),pos,Math.min(9,(int)(miningProgress*10)));
        if(miningProgress>=1&&useBudget()) {
            if(!player.gameMode.destroyBlock(pos))deniedMining.put(pos.immutable(),boss.tickCount+200);
            stopMining();
        }
        return true;
    }
    public boolean pearl(LivingEntity target) {
        if(boss.distanceToSqr(target)<=REACH*REACH)return false;
        Vec3 predicted=target.position().add(pearlTargetMotion(target).scale(Math.min(20,boss.distanceTo(target)/1.5)));
        Vec3 away=boss.position().subtract(predicted).multiply(1,0,1).normalize();
        predicted=predicted.add(away.scale(boss.phase()==1?2.4:3.5));
        return throwPearl(predicted,target,false);
    }
    private Vec3 pearlTargetMotion(LivingEntity target) {
        Vec3 motion=target instanceof net.minecraft.server.level.ServerPlayer
            ?net.everla.projectc.arkofcosmos.combat.vanilla.SpearCombat.motion(target):target.getDeltaMovement();
        motion=motion.multiply(1,0,1);
        return motion.lengthSqr()<=1?motion:Vec3.ZERO; // Teleports are not a chase velocity.
    }
    public boolean repositionPearl(Vec3 destination) { return boss.combat.safeLanding(destination)&&throwPearl(destination,null,false); }
    public boolean retreatPearl(Vec3 destination) { return throwPearl(destination,null,true); }
    public boolean retreatPearlFlying() { return retreatingPearl&&pursuingPearl!=null&&!pursuingPearl.isRemoved(); }
    public boolean chasePearlFlying() { return !retreatingPearl&&pursuingPearl!=null&&!pursuingPearl.isRemoved()&&pursuingPearl.tickCount<=60; }
    private boolean throwPearl(Vec3 destination,LivingEntity target,boolean retreat) {
        if(pursuingPearl!=null&&!pursuingPearl.isRemoved())return false;
        long now=boss.level().getServer().getTickCount();if(now<nextPearl)return false;
        ThrownEnderpearl pearl=new ThrownEnderpearl(boss.level(),boss);
        Vec3 direction=pearlDirection(pearl.position(),destination,target);
        if(direction==null||!useBudget())return false;
        hold(Items.ENDER_PEARL);pearl.shoot(direction.x,direction.y,direction.z,1.5f,0);
        boolean spawned=boss.level().addFreshEntity(pearl);
        if(spawned){nextPearl=now+PEARL_COOLDOWN;pursuingPearl=pearl;pearlOrigin=boss.position();retreatingPearl=retreat;}
        boss.swing(InteractionHand.MAIN_HAND,true);return spawned;
    }
    public boolean consumePearlArrival() {
        if(pursuingPearl==null||!pursuingPearl.isRemoved())return false;
        boolean arrived=!retreatingPearl&&boss.position().distanceToSqr(pursuingPearl.position())<9&&boss.position().distanceToSqr(pearlOrigin)>4;
        pursuingPearl=null;pearlOrigin=null;retreatingPearl=false;return arrived;
    }
    private Vec3 pearlDirection(Vec3 origin,Vec3 predicted,LivingEntity target) {
        // Bounded trajectory search: native drag/gravity and block rays, without a client module or world writes.
        Vec3 best=null;double bestScore=target==null?9:4;
        Vec3 delta=predicted.subtract(origin);
        Vec3 targetMotion=target==null?Vec3.ZERO:pearlTargetMotion(target);
        for(double lift:new double[]{-.3,-.24,-.18,-.12,-.08,-.04,0,.04,.08,.12,.16,.2,.3,.5,.8,1.2,2,4,8,12,16,24,32}) {
            Vec3 direction=delta.add(0,delta.horizontalDistance()*lift,0).normalize(),velocity=direction.scale(1.5),position=origin;
            for(int step=0;step<120;step++) {
                Vec3 next=position.add(velocity);
                if(!boss.level().hasChunkAt(BlockPos.containing(next)))break;
                BlockHitResult hit=boss.level().clip(new ClipContext(position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
                var entityHit=target==null?java.util.Optional.<Vec3>empty():target.getBoundingBox().move(targetMotion.scale(step+1)).inflate(.15).clip(position,next);
                boolean collision=hit.getType()!=HitResult.Type.MISS||entityHit.isPresent();
                if(collision) {
                    Vec3 impact=entityHit.isPresent()&&(hit.getType()==HitResult.Type.MISS||position.distanceToSqr(entityHit.get())<position.distanceToSqr(hit.getLocation()))?entityHit.get():hit.getLocation();
                    if(!boss.combat.safeLanding(impact.add(0,.01,0)))break;
                    double score=impact.distanceToSqr(predicted);
                    if(target!=null) {
                        Vec3 expected=target.position().add(targetMotion.scale(step+1));
                        Vec3 toward=expected.subtract(origin).multiply(1,0,1).normalize();
                        double standOff=boss.phase()==1?2.4:3.5;
                        // Land on our side of the moving target, not in its body or beyond it.
                        if(impact.subtract(expected).dot(toward)>-1.5||!boss.combat.safeLanding(impact.add(0,.01,0)))break;
                        score=impact.distanceToSqr(expected.subtract(toward.scale(standOff)));
                    }
                    if(score<bestScore){bestScore=score;best=direction;}break;
                }
                position=next;velocity=velocity.scale(.99).add(0,-.03,0);
            }
        }
        return best;
    }
    public boolean arrow(LivingEntity target,boolean harming) {
        if(!boss.hasLineOfSight(target)){boss.stopUsingItem();return false;}
        hold(Items.CROSSBOW);ItemStack bow=boss.getMainHandItem();
        if(!boss.isUsingItem()){boss.startUsingItem(InteractionHand.MAIN_HAND);return false;}
        if(boss.getTicksUsingItem()<CrossbowItem.getChargeDuration(bow)||!attackBudget())return false;
        aimArrow(target,3.15);
        boss.stopUsingItem();
        ItemStack ammo=PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW),harming?Potions.STRONG_HARMING:Potions.SLOW_FALLING);
        ListTag loaded=new ListTag();
        int arrows=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT,bow)>0?3:1;
        for(int i=0;i<arrows;i++)loaded.add(ammo.save(new CompoundTag()));
        bow.getOrCreateTag().put("ChargedProjectiles",loaded);CrossbowItem.setCharged(bow,true);
        CrossbowItem.performShooting(boss.level(),boss,InteractionHand.MAIN_HAND,bow,3.15f,1);
        CrossbowItem.setCharged(bow,false);boss.swing(InteractionHand.MAIN_HAND,true);return true;
    }
    private Vec3 aimArrow(LivingEntity target,double speed) {
        double flight=boss.distanceTo(target)/speed;
        Vec3 delta=target.position().add(0,target.getBbHeight()*.5,0).add(target.getDeltaMovement().scale(flight))
            .subtract(boss.getEyePosition().add(0,-.1,0)).add(0,.025*flight*flight,0);
        boss.setYRot((float)(Math.atan2(delta.z,delta.x)*180/Math.PI)-90);
        boss.setXRot((float)(-Math.atan2(delta.y,delta.horizontalDistance())*180/Math.PI));boss.setYHeadRot(boss.getYRot());
        return delta;
    }
    public boolean bowArrow(LivingEntity target) {
        if(!boss.hasLineOfSight(target)){boss.stopUsingItem();return false;}
        hold(Items.BOW);ItemStack bow=boss.getMainHandItem();
        if(!boss.isUsingItem()){boss.startUsingItem(InteractionHand.MAIN_HAND);return false;}
        if(boss.getTicksUsingItem()<20||!attackBudget())return false;
        Vec3 delta=aimArrow(target,3);boss.stopUsingItem();
        ItemStack ammo=PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW),Potions.STRONG_HARMING);
        AbstractArrow arrow=((ArrowItem)ammo.getItem()).createArrow(boss.level(),ammo,boss);
        arrow.shoot(delta.x,delta.y,delta.z,3,1);arrow.setCritArrow(true);
        int power=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,bow);
        if(power>0)arrow.setBaseDamage(arrow.getBaseDamage()+power*.5+.5);
        arrow.setKnockback(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS,bow));
        if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS,bow)>0)arrow.setSecondsOnFire(100);
        arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        if(!boss.level().addFreshEntity(arrow))return false;
        boss.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT,1,1/(boss.getRandom().nextFloat()*.4f+1.2f)+.5f);
        boss.swing(InteractionHand.MAIN_HAND,true);return true;
    }
    public boolean windCharge(Vec3 landing) {
        if(!useBudget())return false;
        hold(Items.FIRE_CHARGE);
        var charge=new net.everla.projectc.arkofcosmos.entity.PlayerWindCharge(VanillaCombat.WIND_CHARGE.get(),boss.level());
        charge.setOwner(boss);charge.setPos(boss.getX(),boss.getEyeY(),boss.getZ());
        Vec3 direction=landing.subtract(charge.position());charge.shoot(direction.x,direction.y,direction.z,1.5f,0);
        boolean spawned=boss.level().addFreshEntity(charge);
        if(spawned)boss.attackDialogue();
        return spawned;
    }
    public boolean maceMelee(LivingEntity target) {
        if(!target.isAlive()||boss.distanceToSqr(target)>9||!attackBudget())return false;
        boss.swing(InteractionHand.MAIN_HAND,true);return boss.doHurtTarget(target);
    }
    public boolean melee(LivingEntity target) {
        if(boss.distanceToSqr(target)>9||!boss.hasLineOfSight(target)||!attackBudget())return false;
        boss.swing(InteractionHand.MAIN_HAND,true);return boss.doHurtTarget(target);
    }
    public void cleanup() {
        stopMining();deniedMining.clear();operator=null;pursuingPearl=null;pearlOrigin=null;retreatingPearl=false;pendingCrystal=null;rejectedBases.clear();diagnosticTicks.clear();
        if(boss.level() instanceof ServerLevel level) {
            List<Entity> attacks=new ArrayList<>();for(Entity entity:level.getAllEntities())if(CalamityAttackGuard.owned(entity,boss))attacks.add(entity);
            for(Entity entity:attacks){entity.getPersistentData().remove(CalamityAttackGuard.OWNER);if(entity instanceof net.everla.projectc.arkofcosmos.entity.PlayerWindCharge charge)charge.discardAttack();else entity.discard();}
        }
    }
}
