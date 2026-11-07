package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.item.BrokenBiomeBladeItem;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.clamp;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.lerp;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.exp;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.thrust;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.thrustScale;
import static net.everla.projectc.arkofcosmos.calamity.combat.BrokenBiomeRules.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Local Calamity BrokenBiomeBlade projectile AIs, with user-approved world-space conversions. */
public final class BrokenBiomeEntity extends Entity {
    public static final int BLADE=0,BEAM=1;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(BrokenBiomeEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot=new AttackRenderSnapshot();
    private static final Map<LivingEntity,Map<UUID,Long>> NPC_IMMUNITY=new WeakHashMap<>();
    private static final SpriteBlade SHAPE=new SpriteBlade("item/broken_biome_blade");
    private final Map<UUID,Integer> immunity=new HashMap<>();
    private int subframe;
    private long startTick;
    private long sourceClock(){return level().getGameTime()*18+subframe;}
    private UUID ownerUuid;private int ownerId,kind,mode,state,age,left,duration,combo,cooldown,alpha,variant,coyote;
    private boolean ended,bounced;
    private float damage,scale=1,rotation;
    private double shred,reach=25;
    private Vec3 direction=new Vec3(0,0,1),velocity=Vec3.ZERO,cp1=Vec3.ZERO,cp2=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(direction,new Vec3(1,0,0));
    private ItemStack weapon=ItemStack.EMPTY,heldStack=ItemStack.EMPTY;
    private ListTag samples=new ListTag(),trail=new ListTag();
    public BrokenBiomeEntity(EntityType<? extends BrokenBiomeEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    private static Vec3 center(LivingEntity p){return p instanceof Player player?OldOneEntity.swordHilt(player):p.getBoundingBox().getCenter();}

    private static BrokenBiomeEntity create(Player p,int kind,int mode,Vec3 at,AttackFrame f,float amount,int life){
        BrokenBiomeEntity e=new BrokenBiomeEntity(ArkOfCosmos.BROKEN_BIOME_ATTACK.get(),p.level());e.startTick=p.level().getGameTime();e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=kind;e.mode=mode;e.frame=f;e.direction=f.forward();e.damage=amount;e.left=e.duration=life;e.heldStack=p.getMainHandItem();e.weapon=e.heldStack.copy();e.setPos(at);return e;
    }
    private void spawn(){level().addFreshEntity(this);publish();}
    public static void attack(Player p){
        if(BrokenBiomeFreeze.frozen(p))return;
        int mode=BrokenBiomeBladeItem.main(p.getMainHandItem());var c=BrokenBiomeCombat.control(p);
        if(mode!=COLD)c.combo=0;
        int life=mode<=PURE?30:mode==COLD?new int[]{15,20,50}[c.combo]:mode==TROPICAL?90:mode==EVIL?26:40;
        BrokenBiomeEntity e=create(p,BLADE,mode,center(p),AttackFrame.capture(p),base(mode),life);e.combo=c.combo;e.cp1=e.cp2=center(p);
        e.reach=Math.max(300/16d,Math.min(400/16d,WeaponUser.aim(p).distanceTo(center(p))));
        if(mode==COLD){c.combo=(c.combo+1)%3;if(e.combo==2)e.damage*=1.15f;e.sound(p,e.combo==0?"fractured_swing":e.combo==1?"true_biome_flail":"biome_cold_thrust");}
        else if(mode==TROPICAL)e.sound(p,"true_biome_flail");
        else if(mode==EVIL){if(c.lunge>0)e.impulse(p,e.direction.scale(3));c.lunge=0;e.sound(p,"biome_evil");}
        else if(mode<=PURE){e.sound(p,"biome_swing");if(mode==PURE){BrokenBiomeEntity b=create(p,BEAM,PURE,center(p),e.frame,base(PURE),40);b.velocity=e.direction.scale(12/16d);b.spawn();}}
        else if(mode==HOT)e.sound(p,"galaxia_item90");
        if(mode<=PURE){e.rotation=(float)swordAngle(0);e.direction=e.frame.direction(e.rotation);}
        e.spawn();
    }
    public boolean activeBlade(){return kind()==BLADE&&(level().isClientSide?view().getInt("Left"):left)>0;}
    public int ownerId(){return level().isClientSide?view().getInt("Owner"):ownerId;}
    public int kind(){return level().isClientSide?view().getInt("Kind"):kind;}
    public LivingEntity curseOwner(){return net.everla.projectc.arkofcosmos.combat.WeaponUser.resolve(level(),ownerUuid);}
    public ItemStack weapon(){return weapon;}
    private Player owner(){return WeaponUser.resolve(level(),ownerUuid) instanceof Player p?p:null;}
    private boolean valid(Player p){return WeaponUser.active(p)&&!BrokenBiomeFreeze.frozen(p)&&BrokenBiomeCombat.holding(p)&&p.getMainHandItem()==heldStack;}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag s){}
    @Override protected void addAdditionalSaveData(CompoundTag s){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<256*256;}
    @Override public void tick(){
        super.tick();if(level().isClientSide){clientSnapshot.tick(view(),"Age");return;}
        Player p=owner();if(!valid(p)){discardAttack();return;}
        // Match the legacy sword's network grace period without extending its hit window.
        if(kind==BLADE&&mode<=PURE&&left<=0){if(left==0){left=-1;publish();}else discardAttack();return;}
        samples=new ListTag();samples.add(snapshot());int count=steps(kind,mode);
        for(int i=0;i<count&&!isRemoved();i++){
            subframe=i*18/count;immunity.replaceAll((id,t)->t-1);immunity.values().removeIf(t->t<=0);cooldown--;
            switch(kind){case BLADE->blade(p);case BEAM->beam(p);}

            if(isRemoved())break;age++;remember();if(--left<=0&&!(kind==BLADE&&mode<=PURE))finish(p);samples.add(snapshot());
        }
        if(!isRemoved())publish();
    }
    private Vec3 aim(Player p){Vec3 v=WeaponUser.aim(p).subtract(center(p)).normalize();return v.lengthSqr()<1e-8?p.getLookAngle():v;}
    private void follow(Player p){frame=GalaxiaEntity.aimFrame(aim(p),AttackFrame.capture(p).right());direction=frame.forward();}
    private void blade(Player p){
        if(mode<=PURE){
            double previous=swordAngle(age);rotation=(float)swordAngle(age+1);direction=frame.direction(rotation);setPos(center(p));
            double radius=Math.hypot(SHAPE.WIDTH,SHAPE.HEIGHT)/16;
            for(LivingEntity target:targets(new AABB(position(),position()).inflate(radius),p))
                if(SHAPE.sweeps(target.getBoundingBox(),position(),frame,previous,rotation))hit(p,target,30,damage,false);
            return;
        }
        switch(mode){case HOT->hot(p);case COLD->cold(p);case TROPICAL->tropical(p);case EVIL->evil(p);}
    }
    private void hot(Player p){
        if(!BrokenBiomeCombat.channeling(p)){finish(p);return;}
        follow(p);setPos(center(p).add(direction.scale(60/16d)));shred=Math.max(0,Math.min(500,shred));double ratio=clamp(shred/250);scale=(float)(1+ratio);
        int cd=40-(int)(15*ratio);
        if(!p.onGround()&&cooldown<=0&&touches(center(p).add(direction.scale(84/16d*scale)),10/16d)){
            impulse(p,direction.scale(-3));cooldown=30;BrokenBiomeCombat.control(p).lunge=1;sound(p,"galaxia_impact");event("pogo",center(p).add(direction.scale(84/16d*scale)));
        }
        shred-=1;
        if(left<=2){left=2;hitLine(p,center(p),center(p).add(direction.scale(94/16d*scale)),76/16d*scale,cd,(float)(damage*lerp(1,60/35d,clamp(shred/250))*cd/40),false);}
    }
    private void cold(Player p){
        double t=age/(double)duration;
        rotation=(float)(combo==2?0:lerp((combo==0?-1:1)*(combo==0?2.3:1.8)/2,(combo==0?1:-1)*(combo==0?2.3:1.8)/2,1-(1-t)*(1-t)));
        direction=frame.direction(rotation);scale=(float)(combo==2?1+.6*thrustScale(t):1+.6*Math.sin(t*Math.PI));
        variant=combo==2?Math.min(5,(age+1)/5):0;
        Vec3 offset=combo==2?frame.forward().scale(thrust(t)*60/16d):Vec3.ZERO;
        setPos(center(p).add(offset));double length=combo==0?90:combo==1?110:variant<=2?85:150;
        hitLine(p,position(),position().add(direction.scale(length/16d*scale)),24/16d,10,damage,false);
        if(random.nextFloat()<(combo==2?.5:.25))event(combo==2?"cold_mist":"cold_snow",combo==2?center(p).add(frame.forward().scale(40/16d)):center(p).add(direction.scale(100/16d*scale)));
    }
    private double swingProgress(){if(age>63&&coyote>0)return 0;return age<=63?exp(age/63):(age-63)/27;}
    private Vec3 swing(double t){
        if(age>63)return frame.forward().scale(reach*(1-t));
        double length=Math.max(reach*lerp(Math.sin(t*Math.PI/2),1,.04),65/16d);
        return frame.direction(lerp(-2.6179938,0,t)).scale(length);
    }
    private void tropical(Player p){
        if(age>63&&state==0){state=1;coyote=15;sound(p,"biome_whip_snap");}
        Vec3 c=center(p);double progress=swingProgress();setPos(c.add(swing(progress)));direction=position().subtract(c).normalize();rotation=(float)frame.angle(direction);
        if(age<=63){cp1=c.add(swing(clamp(progress+.5)).scale(.2));cp2=c.add(swing(clamp(progress+.2)).scale(.5));}
        else{Vec3 perpendicular=frame.direction(frame.angle(direction)+Math.PI/2);double smooth=progress*progress*(3-2*progress);cp1=c.add(swing(lerp(progress,1,progress))).add(perpendicular.scale(-smooth*155/16d));cp2=c.add(swing(lerp(progress,1,progress/2))).add(perpendicular.scale(smooth*100/16d));}
        if(coyote>0){
            if(touches(position(),2)&&(!p.onGround()||position().y>p.getY())){impulse(p,position().subtract(c).normalize().scale(14*3/16d));coyote=0;}
            sound(p,"item65");coyote--;
        }
        for(LivingEntity t:targets(new AABB(c,position()).inflate(reach*.6+6),p)){
            boolean tip=ArkGeometry.line(t.getBoundingBox(),position().subtract(direction.scale(18/16d)),position().add(direction.scale(18/16d)),32/16d,frame),body=false;Vec3 prev=c;
            for(int i=1;i<32&&!body;i++){Vec3 point=bezier(c,cp1,cp2,position(),i/31d);body=ArkGeometry.line(t.getBoundingBox(),prev,point,6/16d,frame);prev=point;}
            boolean critical=tip&&coyote>0;float amount=damage*(tip?(critical?2.4f:1):.6f);if((tip||body))hit(p,t,60,amount,critical);
        }
    }
    public static Vec3 bezier(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double t){double u=1-t;return a.scale(u*u*u).add(b.scale(3*u*u*t)).add(c.scale(3*u*t*t)).add(d.scale(t*t*t));}
    private void evil(Player p){
        double t=age/(double)duration;scale=(float)(1+.6*Math.sin(t*Math.PI));
        setPos(center(p).add(direction.scale(Math.sin(t*Math.PI)*60/16d)));
        hitLine(p,position(),position().add(direction.scale(120/16d*scale)),24/16d,10,damage,false);
    }
    private void beam(Player p){
        if(age==0)sound(p,"true_biome_beam_start");
        if(age>=15){scale+=state==0?-.02f:.02f;alpha+=state==0?30:-30;if(alpha>=250){alpha=255;state=1;}else if(alpha<=0){alpha=0;state=0;}}
        Vec3 next=position().add(velocity);
        if(left<35&&level().getBlockCollisions(this,new AABB(position(),next).inflate(1)).iterator().hasNext()){finish(p);return;}
        setPos(next);direction=velocity.normalize();rotation=(float)frame.angle(direction);
        for(LivingEntity t:targets(new AABB(position().subtract(velocity),position()).inflate(1),p))
            if(ArkGeometry.line(t.getBoundingBox(),position().subtract(velocity),position(),2,frame)&&hit(p,t,10,damage,false)){
                t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.CRUMBLING.get(),30));finish(p);break;
            }
    }
    private List<LivingEntity> targets(AABB bounds,Player p){return level().getEntitiesOfClass(LivingEntity.class,bounds,t->WeaponUser.canHit(p,t));}
    private void hitLine(Player p,Vec3 a,Vec3 b,double width,int cd,float amount,boolean critical){for(LivingEntity t:targets(new AABB(a,b).inflate(width/2+1),p))if(ArkGeometry.line(t.getBoundingBox(),a,b,width,frame))hit(p,t,cd,amount,critical);}
    private boolean hit(Player p,LivingEntity t,int cd,float amount,boolean critical){
        if(isRemoved()||immunity.containsKey(t.getUUID()))return false;
        boolean ordinary=kind==BEAM||kind==BLADE&&(mode<=PURE||mode==COLD||mode==EVIL);
        long clock=sourceClock();if(ordinary&&NPC_IMMUNITY.getOrDefault(t,Map.of()).getOrDefault(ownerUuid,0L)>clock)return false;
        int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);boolean started=fire>0&&!t.isOnFire();if(started)t.setSecondsOnFire(1);
        float applied=amount+EnchantmentHelper.getDamageBonus(weapon,t.getMobType())*(critical?2:1);
        if(!WeaponDamage.hurt(t,WeaponUser.source(this,p),applied)){if(started)t.clearFire();return false;}
        immunity.put(t.getUUID(),cd);if(ordinary)NPC_IMMUNITY.computeIfAbsent(t,k->new HashMap<>()).put(ownerUuid,clock+60);p.setLastHurtMob(t);
        if(fire>0)t.setSecondsOnFire(4*fire);EnchantmentHelper.doPostHurtEffects(t,p);weapon.getAllEnchantments().forEach((e,l)->e.doPostAttack(p,t,l));
        double kb=(kind==BLADE?.75:0)+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK)*.5;if(kb>0){Vec3 away=t.position().subtract(p.position());t.knockback(kb,-away.x,-away.z);}
        if(kind==BLADE){
            if(mode==HOT&&cooldown<=0){shred+=62;cooldown=20;invulnerable(p,6);if(p.getDeltaMovement().y<0)impulse(p,p.getDeltaMovement().multiply(1,0,1).add(0,2*3/16d,0));sound(p,"galaxia_hit30");}
            if(mode==COLD&&combo==2)BrokenBiomeFreeze.apply(t,20);
            if(mode==EVIL&&!bounced){p.heal(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.originalDamage(2));double speed=Math.min(22*3/16d,Math.max(8*3/16d,p.getDeltaMovement().length())*(p.onGround()?.2:1));impulse(p,direction.scale(-speed));bounced=true;invulnerable(p,10);}
        }
        if(critical)event("critical",center(t),center(t).subtract(center(p)).normalize());
        return true;
    }
    private void invulnerable(Player p,int frames){ArkCombat.controls(p).dashInvulnerableUntil=Math.max(ArkCombat.controls(p).dashInvulnerableUntil,level().getGameTime()+(frames+2)/3);}
    private boolean touches(Vec3 at,double diameter){return level().getBlockCollisions(this,new AABB(at,at).inflate(diameter/2)).iterator().hasNext();}
    private void impulse(Player p,Vec3 delta){p.setDeltaMovement(delta);p.hurtMarked=true;p.fallDistance=0;}
    private void finish(Player p){
        if(ended)return;
        if(kind==BLADE&&mode==HOT)sound(p,"galaxia_hit43");
        if(kind==BEAM){sound(p,"true_biome_beam_end");event("beam_end",position());}
        discardAttack();
    }
    public void discardAttack(){if(ended)return;ended=true;super.discard();}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ended&&left>0&&valid(owner()))return;super.remove(reason);}
    private void sound(Player p,String name){sound(p,name,1,0);}
    private void sound(Player p,String name,float volume,float pitch){TrueBiomeCombat.sound(this,p,name,volume,pitch,name.equals("galaxia_hit30"),false);}
    private void event(String name,Vec3 at){event(name,at,direction);}
    private void event(String name,Vec3 at,Vec3 fxDirection){CompoundTag s=snapshot();s.putString("Event","broken_biome_fx");s.putString("Fx",name);vector(s,"At",at);vector(s,"Direction",fxDirection);net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,s);}
    private void remember(){CompoundTag s=new CompoundTag();vector(s,"Position",position());s.putFloat("Rotation",rotation);trail.add(0,s);while(trail.size()>(kind==BEAM?5:10))trail.remove(trail.size()-1);}
    private CompoundTag snapshot(){
        CompoundTag s=new CompoundTag();s.putUUID("Attack",getUUID());s.putLong("StartTick",startTick);s.putInt("Owner",ownerId);s.putInt("Kind",kind);s.putInt("Mode",mode);s.putInt("State",state);s.putInt("Age",age);s.putInt("Left",left);s.putInt("Duration",duration);s.putInt("Combo",combo);s.putInt("Coyote",coyote);s.putInt("Variant",variant);s.putInt("Alpha",alpha);s.putDouble("Shred",shred);s.putFloat("Scale",scale);s.putFloat("Rotation",rotation);
        vector(s,"Position",position());vector(s,"F",frame.forward());vector(s,"R",frame.right());vector(s,"Direction",direction);vector(s,"Velocity",velocity);vector(s,"CP1",cp1);vector(s,"CP2",cp2);Player p=owner();vector(s,"Center",p==null?position():center(p));vector(s,"OwnerVelocity",p==null?Vec3.ZERO:p.getDeltaMovement().scale(1/3d));s.put("Trail",trail.copy());return s;
    }
    private void publish(){CompoundTag s=snapshot();s.put("Samples",samples.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){if(clientSnapshot!=null&&level().isClientSide&&key.equals(VIEW))clientSnapshot.initialize(view());super.onSyncedDataUpdated(key);}
    public CompoundTag renderState(float partial){
        CompoundTag state=clientSnapshot.state().isEmpty()?view():clientSnapshot.state();ListTag list=state.getList("Samples",10);if(list.isEmpty())return state;
        double index=clamp(partial)*(list.size()-1);int i=(int)index;CompoundTag a=list.getCompound(i),b=list.getCompound(Math.min(i+1,list.size()-1)),s=a.copy();double t=index-i;
        for(String key:new String[]{"Position","Center","CP1","CP2"})vector(s,key,vector(a,key).lerp(vector(b,key),t));
        for(String key:new String[]{"Age","Scale","Rotation"})s.putDouble(key,lerp(a.getDouble(key),b.getDouble(key),t));
        AttackFrame f=AttackFrame.interpolateView(new AttackFrame(vector(a,"F"),vector(a,"R")),new AttackFrame(vector(b,"F"),vector(b,"R")),t);vector(s,"F",f.forward());vector(s,"R",f.right());
        Vec3 d=vector(a,"Direction").lerp(vector(b,"Direction"),t).normalize();if(d.lengthSqr()>.5)vector(s,"Direction",d);return s;
    }
}
