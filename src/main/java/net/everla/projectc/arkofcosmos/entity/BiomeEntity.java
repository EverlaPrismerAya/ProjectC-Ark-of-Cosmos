package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.item.BiomeBladeItem;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Local Calamity TrueBiomeBlade projectile AIs, with user-approved world-space conversions. */
public final class BiomeEntity extends Entity {
    public static final int BLADE=0,BEAM=1,HOT_SHOT=2,MIST=3,VINE=4,DASH=5,MONOLITH=6,ORB=7,SIGIL=8;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(BiomeEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot=new AttackRenderSnapshot();
    private static final Map<LivingEntity,Map<UUID,Long>> NPC_IMMUNITY=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> MIST_IMMUNITY=new WeakHashMap<>();
    private final Map<UUID,Integer> immunity=new HashMap<>();
    private int subframe;
    private long startTick;
    private long sourceClock(){return level().getGameTime()*18+subframe;}
    private UUID ownerUuid;private int ownerId,kind,mode,state,age,left,duration,combo,cooldown,charge,overCharge,indicator,hits,alpha,variant,landTime,dashTime,coyote;
    private boolean ended,charged,bounced,chargeSound,spinSound,moved,water;
    private float damage,scale=1,rotation;
    private double shred,reach=25,wait,size=1;
    private Vec3 direction=new Vec3(0,0,1),velocity=Vec3.ZERO,start=Vec3.ZERO,end=Vec3.ZERO,origin=Vec3.ZERO,cp1=Vec3.ZERO,cp2=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(direction,new Vec3(1,0,0));
    private ItemStack weapon=ItemStack.EMPTY,heldStack=ItemStack.EMPTY;
    private LivingEntity target,from;
    private ListTag samples=new ListTag(),trail=new ListTag();
    public BiomeEntity(EntityType<? extends BiomeEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    private static Vec3 center(LivingEntity p){return p instanceof Player player?OldOneEntity.swordHilt(player):p.getBoundingBox().getCenter();}

    private static BiomeEntity create(Player p,int kind,int mode,Vec3 at,AttackFrame f,float amount,int life){
        BiomeEntity e=new BiomeEntity(ArkOfCosmos.BIOME_ATTACK.get(),p.level());e.startTick=p.level().getGameTime();e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=kind;e.mode=mode;e.frame=f;e.direction=f.forward();e.damage=amount;e.left=e.duration=life;e.heldStack=p.getMainHandItem();e.weapon=e.heldStack.copy();e.setPos(at);return e;
    }
    private void spawn(){level().addFreshEntity(this);publish();}
    public static void attack(Player p){
        int mode=BiomeBladeItem.main(p.getMainHandItem());var c=BiomeCombat.control(p);
        if(mode!=COLD&&mode!=TROPICAL)c.combo=0;
        int life=mode<=PURE?15:mode==COLD?new int[]{15,20,50}[c.combo]:mode==TROPICAL?90:mode==EVIL?26:mode==MARINE?40:mode==HOT?30:2;
        BiomeEntity e=create(p,BLADE,mode,center(p),AttackFrame.capture(p),base(mode),life);e.combo=c.combo;e.cp1=e.cp2=center(p);
        e.reach=Math.max(300/16d,Math.min(400/16d,WeaponUser.aim(p).distanceTo(center(p))));
        if(mode==COLD){c.combo=(c.combo+1)%3;if(e.combo==2)e.damage*=1.75f;e.sound(p,e.combo==0?"fractured_swing":e.combo==1?"true_biome_flail":"biome_cold_thrust");}
        else if(mode==TROPICAL){c.combo=(c.combo+1)%2;e.sound(p,"true_biome_flail");}
        else if(mode==EVIL){e.charged=c.power==3;if(e.charged)c.power=0;else if(c.lunge>0)e.impulse(p,e.direction.scale(16*3/16d));c.lunge=Math.max(0,c.lunge-1);e.sound(p,"biome_evil");}
        else if(mode<=PURE){e.sound(p,"biome_swing");if(mode==PURE){BiomeEntity b=create(p,BEAM,PURE,center(p),e.frame,base(PURE),90);b.velocity=e.direction.scale(12/16d);b.spawn();}}
        else if(mode==HOT||mode==HOLY)e.sound(p,"galaxia_item90");
        else if(mode==ASTRAL)e.sound(p,"galaxia_item101");
        if(mode<=PURE){e.rotation=(float)(-Math.PI/3);e.direction=e.frame.direction(e.rotation);}
        e.spawn();
    }
    public boolean activeBlade(){return kind()==BLADE&&(level().isClientSide?view().getInt("Left"):left)>0;}
    public int ownerId(){return level().isClientSide?view().getInt("Owner"):ownerId;}
    public int kind(){return level().isClientSide?view().getInt("Kind"):kind;}
    public ItemStack weapon(){return weapon;}
    private Player owner(){return WeaponUser.resolve(level(),ownerUuid) instanceof Player p?p:null;}
    private boolean valid(Player p){return WeaponUser.active(p)&&BiomeCombat.holding(p)&&p.getMainHandItem()==heldStack;}
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
            switch(kind){case BLADE->blade(p);case BEAM->beam(p);case HOT_SHOT->hotShot(p);case MIST->mist(p);case VINE->vine(p);case DASH->hitLine(p,start,end,0,60,damage,false);case MONOLITH->monolith(p);case ORB->orb(p);case SIGIL->{if(!WeaponUser.canHit(p,target))discardAttack();else setPos(center(target));}}
            if(isRemoved())break;age++;remember();if(--left<=0&&!(kind==BLADE&&mode<=PURE))finish(p);samples.add(snapshot());
        }
        if(moved&&p instanceof ServerPlayer sp){if(sp.connection!=null)sp.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());moved=false;}
        if(!isRemoved())publish();
    }
    private Vec3 aim(Player p){Vec3 v=WeaponUser.aim(p).subtract(center(p)).normalize();return v.lengthSqr()<1e-8?p.getLookAngle():v;}
    private void follow(Player p){frame=GalaxiaEntity.aimFrame(aim(p),AttackFrame.capture(p).right());direction=frame.forward();}
    private void blade(Player p){
        if(mode<=PURE){double previous=OldOneEntity.swordAngle(age);rotation=(float)OldOneEntity.swordAngle(age+1);direction=frame.direction(rotation);setPos(OldOneEntity.swordHilt(p));
            double reach=Math.hypot(OldOneBlade.WIDTH,OldOneBlade.HEIGHT)/16;
            for(LivingEntity target:targets(new AABB(position(),position()).inflate(reach),p))if(OldOneBlade.sweeps(target.getBoundingBox(),position(),frame,previous,rotation))hit(p,target,15,damage,false);return;}
        switch(mode){case HOT->hot(p);case COLD->cold(p);case TROPICAL->tropical(p);case EVIL->evil(p);case HOLY->holy(p);case ASTRAL->astral(p);case MARINE->marine(p);}
    }
    private void hot(Player p){
        if(!BiomeCombat.channeling(p)){finish(p);return;}follow(p);setPos(center(p).add(direction.scale(60/16d)));shred=Math.max(0,Math.min(500,shred));double ratio=clamp(shred/250);scale=(float)(1+ratio);
        int cd=30-(int)(14*ratio);
        if(!p.onGround()&&cooldown<=0&&touches(center(p).add(direction.scale(100/16d*scale)),10/16d)){
            impulse(p,direction.scale(-16*3/16d));cooldown=30;overCharge=20;BiomeCombat.control(p).lunge=2;sound(p,"galaxia_impact");event("pogo",center(p).add(direction.scale(100/16d*scale)));
        }
        if(ratio>.5){if(indicator<=0){sound(p,"true_biome_charge");indicator=20;}}else indicator--;
        shred-=.65;overCharge--;
        if(left<=2){left=2;hitLine(p,center(p),center(p).add(direction.scale(120/16d*scale)),76/16d*scale,cd,(float)(damage*lerp(1,160/122d,clamp(shred/250))*cd/30),false);}
    }
    private void hotShot(Player p){setPos(position().add(velocity.scale(2)));hitLine(p,position().subtract(direction.scale(42/16d*scale)),position().add(direction.scale(42/16d*scale)),76/16d*scale,30,damage,false);}
    private void cold(Player p){
        double t=age/(double)duration;rotation=(float)(combo==2?0:lerp((combo==0?-1:1)*(combo==0?2.3:1.8)/2,(combo==0?1:-1)*(combo==0?2.3:1.8)/2,1-(1-t)*(1-t)));
        direction=frame.direction(rotation);scale=(float)(combo==2?1+.3*thrustScale(t):1+.6*Math.sin(t*Math.PI));Vec3 offset=combo==2?frame.forward().scale(thrust(t)*60/16d):Vec3.ZERO;
        setPos(center(p).add(offset));hitLine(p,position(),position().add(direction.scale((combo==2?225:150)/16d*scale)),26/16d,10,damage,false);
        if(random.nextFloat()<(combo==2?.5:.25)){
            Vec3 at=center(p).add(frame.forward().scale(40/16d));Vec3 v=direction.scale(5/16d);
            if(combo==2){Vec3 randomOffset=frame.direction(random.nextDouble()*Math.PI*2).scale(Math.sqrt(random.nextDouble())*30/16d);at=at.add(randomOffset);v=at.subtract(center(p)).scale(.2).add(p.getDeltaMovement().scale(1/3d));}
            BiomeEntity mist=create(p,MIST,COLD,at,frame,(float)(damage*.09),300);mist.velocity=v;mist.variant=random.nextInt(3);mist.spawn();if(combo!=2)event("cold_snow",center(p).add(direction.scale(100/16d*scale)));
        }
    }
    private double swingProgress(){if(age>49.5&&coyote>0)return 0;return age<=49.5?exp(age/49.5):(age-49.5)/40.5;}
    private Vec3 swing(double t){
        if(age>49.5)return frame.forward().scale(reach*(1-t));
        double length=Math.max(reach*lerp(Math.sin(t*Math.PI/2),1,.04),65/16d);
        if(combo==1)length*=lerp(.1,.3,Math.sin(t*Math.PI));
        return frame.direction((combo==0?1:-1)*lerp(-2.6179938,0,t)).scale(length);
    }
    private void tropical(Player p){
        if(age>49.5&&state==0){state=1;coyote=15;sound(p,"biome_whip_snap");}
        Vec3 c=center(p);double progress=swingProgress();setPos(c.add(swing(progress)));direction=position().subtract(c).normalize();rotation=(float)frame.angle(direction);
        if(age<=49.5){cp1=c.add(swing(clamp(progress+.5)).scale(.2));cp2=c.add(swing(clamp(progress+.2)).scale(.5));}
        else{Vec3 perpendicular=frame.direction(frame.angle(direction)+Math.PI/2);double smooth=progress*progress*(3-2*progress);cp1=c.add(swing(lerp(progress,1,progress))).add(perpendicular.scale(smooth*155/16d));cp2=c.add(swing(lerp(progress,1,progress/2))).add(perpendicular.scale(-smooth*100/16d));}
        if(coyote>0){
            if(touches(position(),2)&&(!p.onGround()||position().y>p.getY())){impulse(p,position().subtract(c).normalize().scale(14*3/16d));coyote=0;}
            sound(p,"item65");coyote--;
        }
        for(LivingEntity t:targets(new AABB(c,position()).inflate(reach*.6+6),p)){
            boolean tip=ArkGeometry.line(t.getBoundingBox(),position().subtract(direction.scale(85/16d)),position().add(direction.scale(85/16d)),32/16d,frame),body=false;Vec3 prev=c;
            for(int i=1;i<32&&!body;i++){Vec3 point=bezier(c,cp1,cp2,position(),i/31d);body=ArkGeometry.line(t.getBoundingBox(),prev,point,6/16d,frame);prev=point;}
            boolean critical=tip&&coyote>0;float amount=damage*(tip?(critical?3:1):.6f);float before=t.getHealth()+t.getAbsorptionAmount();
            if((tip||body)&&hit(p,t,60,amount,critical)&&critical)entangle(p,t,Math.max(0,before-t.getHealth()-t.getAbsorptionAmount())*.3f);
        }
    }
    public static Vec3 bezier(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double t){double u=1-t;return a.scale(u*u*u).add(b.scale(3*u*u*t)).add(c.scale(3*u*t*t)).add(d.scale(t*t*t));}
    private void entangle(Player p,LivingEntity root,float amount){
        Set<UUID> excluded=new HashSet<>();excluded.add(root.getUUID());boolean first=true;
        for(int i=0;i<3;i++){
            LivingEntity next=null;double distance=400/16d;for(LivingEntity t:targets(root.getBoundingBox().inflate(distance),p))if(WeaponUser.enemy(p,t)&&!excluded.contains(t.getUUID())&&center(t).distanceTo(center(root))<distance){distance=center(t).distanceTo(center(root));next=t;}
            if(next==null)break;excluded.add(next.getUUID());if(first){sound(p,"biome_vines");first=false;}BiomeEntity e=create(p,VINE,TROPICAL,center(next),frame,amount,20);e.target=next;e.from=root;e.start=center(root);e.end=center(next);e.spawn();
        }
    }
    private void vine(Player p){if(!WeaponUser.canHit(p,target)||from==null||!from.isAlive()){discardAttack();return;}setPos(center(target));start=center(from);end=center(target);hit(p,target,30,damage,false);}
    private void evil(Player p){
        if(charged&&dashTime==0){dashTime=1;origin=center(p);invulnerable(p,60);}
        if(dashTime>0&&dashTime<20){left=duration/2;boolean clear=movePlayer(p,direction.scale(30/16d));dashTime++;if(!clear)dashTime=20;}
        if(dashTime==20){
            BiomeEntity d=create(p,DASH,EVIL,origin.lerp(center(p),.5),frame,damage*3,20);d.start=origin;d.end=center(p);d.spawn();dashTime=21;impulse(p,direction.scale(6*3/16d));sound(p,"true_biome_slice");
        }
        double t=(duration-left)/(double)duration;scale=(float)(1+.6*Math.sin(t*Math.PI));setPos(center(p).add(direction.scale(Math.sin(t*Math.PI)*60/16d)));
        hitLine(p,position(),position().add(direction.scale(140/16d*scale)),24/16d,10,damage,false);
    }
    private void holy(Player p){
        if(state==0){double phase=frame.angle(direction);follow(p);direction=frame.direction(phase);}
        if(!BiomeCombat.channeling(p)&&state==0){if(charge<300){sound(p,"galaxia_item77");finish(p);return;}state=1;left=90;direction=aim(p);frame=GalaxiaEntity.aimFrame(direction,AttackFrame.capture(p).right());sound(p,"galaxia_item80");event("holy_throw",position());}
        if(state==0){
            if(!chargeSound&&charge>=300){chargeSound=true;sound(p,"galaxia_item76");}
            double phase=frame.angle(direction);if(phase>-3*Math.PI/4&&phase<-Math.PI/4)spinSound=false;else if(phase>Math.PI/4&&phase<3*Math.PI/4&&!spinSound){spinSound=true;sound(p,"item71");}
            scale=1+charge/600f*1.5f;direction=frame.direction(phase+Math.max(.4,charge/600d)*Math.PI/4*.2);setPos(center(p).add(direction.scale(scale*10/16d)));left=91;charge=Math.min(600,charge+1);
        }else{double t=(90-left)/90d;setPos(center(p).add(direction.scale(scale*10/16d+440/16d*net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.throwCurve(t))));scale=(float)((1+charge/600d*1.5)*Math.max(.3,1-Math.max(0,(t-.6)/.4)));}
        rotation=(float)frame.angle(direction);hitLine(p,position(),position().add(direction.scale(100/16d*scale)),20/16d*scale,16,(float)(damage*(state==1?lerp(1,3.8,charge/600d):.4+2*charge/600d)),false);
    }
    private void astral(Player p){
        if(state==0)follow(p);
        if(state==0&&!BiomeCombat.channeling(p)){if(charge<125){sound(p,"galaxia_item109");finish(p);return;}state=1;left=duration=(7+(int)((charge/500d-.25)*20))*2;start=position().subtract(center(p));sound(p,"galaxia_item120",.5f,0);}
        if(state==0){setPos(center(p).add(direction.scale(70/16d*net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.chargeDisplacement(charge/500d))));left=2;charge=Math.min(500,charge+1);overCharge--;
            if(indicator<3&&charge>=125*(indicator+1)){sound(p,"true_biome_pulse",1,(float)(-.2+.1*indicator));indicator++;overCharge=20;}
            if(charge==500&&indicator<4){indicator++;overCharge=20;sound(p,"galaxia_beacon");}
        }else{
            setPos(center(p).add(start.lerp(direction.scale(40/16d),clamp((duration-left)/(double)duration*2))));Vec3 tip=center(p).add(direction.scale(120/16d*scale));
            if(touches(tip,10/16d)){slam(p,tip);finish(p);return;}
            if(!movePlayer(p,direction.scale(15/16d))){slam(p,tip);finish(p);return;}
            hitLine(p,center(p),center(p).add(direction.scale(120/16d*scale)),20/16d*scale,16,(float)(damage*(1+4*Math.pow(charge/500d,2))),false);
        }scale=1+charge/500f*.3f;
    }
    private void slam(Player p,Vec3 tip){
        sound(p,"galaxia_impact",1.5f,0);if(charge<250)return;
        var hit=level().clip(new ClipContext(center(p),tip.add(direction),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 at=hit.getType()==HitResult.Type.MISS?tip:hit.getLocation(),normal=hit.getType()==HitResult.Type.MISS?direction.scale(-1):Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        addMonolith(p,at,normal,1,0);
        if(charge<375)return;Vec3 tangent=frame.right().subtract(normal.scale(frame.right().dot(normal))).normalize();if(tangent.lengthSqr()<.5)tangent=normal.cross(new Vec3(0,0,1)).normalize();if(tangent.lengthSqr()<.5)tangent=new Vec3(1,0,0);
        for(int side=0;side<2;side++){
            double angle=side*Math.PI;Vec3 radial=tangent.scale(Math.cos(angle)).add(normal.cross(tangent).scale(Math.sin(angle)));
            if(surfaceMonolith(p,at,normal,radial,150/16d,.8)&&charge==500)surfaceMonolith(p,at,normal,radial,250/16d,.5);
        }
    }
    private boolean surfaceMonolith(Player p,Vec3 at,Vec3 normal,Vec3 radial,double distance,double size){
        Vec3 top=at.add(radial.scale(distance)).add(normal.scale(150/16d));var h=level().clip(new ClipContext(top,top.subtract(normal.scale(300/16d)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(h.getType()==HitResult.Type.MISS)return false;addMonolith(p,h.getLocation(),Vec3.atLowerCornerOf(h.getDirection().getNormal()),size,(1-size)*34);return true;
    }
    private void addMonolith(Player p,Vec3 at,Vec3 normal,double size,double wait){BiomeEntity e=create(p,MONOLITH,ASTRAL,at,GalaxiaEntity.aimFrame(normal,frame.right()),damage*1.25f,102);e.direction=normal;e.size=size;e.wait=wait;e.variant=random.nextInt(4);e.spawn();}
    private void monolith(Player p){
        if(wait>0){wait--;left=102;return;}
        if(left==100&&size>=1)event("monolith_start",position());if(left==80){sound(p,"biome_eruption");event("monolith_burst",position());}
        double t=(100-left)/100d,width=monolithWidth(t)*90*size/16,height=monolithHeight(t)*420*size/16;
        hitLine(p,position(),position().add(direction.scale(height)),width,10,damage,false);
    }
    private void marine(Player p){
        double half=age%20,sign=age<=20?-1:1,angle=(Math.PI/4+3*Math.PI/8*half/20)*sign;
        direction=frame.forward().scale(-Math.cos(angle)).add(frame.right().scale(Math.sin(angle)));setPos(center(p).add(direction.scale(Math.sin(half/20*Math.PI)*30/16d)));rotation=(float)frame.angle(direction);
        if(half==10){Vec3 shot=aim(p);BiomeEntity e=create(p,ORB,MARINE,center(p),GalaxiaEntity.aimFrame(shot,frame.right()),damage,160);e.direction=shot;e.velocity=e.direction.scale(3/16d).add(p.getDeltaMovement().scale(1/3d));e.spawn();sound(p,"biome_splash",1,(random.nextFloat()-.5f)*2);}
    }
    private void beam(Player p){
        if(age==0)sound(p,"true_biome_beam_start");
        if(age>=15){scale+=state==0?-.02f:.02f;alpha+=state==0?30:-30;if(alpha>=250){alpha=255;state=1;}else if(alpha<=0){alpha=0;state=0;}}
        if(target==null)for(BiomeEntity e:BiomeCombat.attacks(p))if(e.kind==SIGIL&&WeaponUser.canHit(p,e.target)){target=e.target;break;}
        if(target!=null&&!WeaponUser.canHit(p,target))target=null;
        if(target!=null){Vec3 to=center(target).subtract(position());if(to.length()>=to.subtract(velocity).length()&&velocity.normalize().dot(to.normalize())>Math.cos(Math.PI/4)){left=30;velocity=turn(velocity,to,Math.toRadians(lerp(12.5,2.5,clamp(to.length()*16/10)))).scale(velocity.length());}}
        Vec3 next=position().add(velocity);if(left<85&&blockBetween(position(),next)){finish(p);return;}setPos(next);direction=velocity.normalize();rotation=(float)frame.angle(direction);
        for(LivingEntity t:targets(new AABB(position(),position()).inflate(1),p))if(hit(p,t,10,damage,false)){t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.ARMOR_CRUNCH.get(),30));finish(p);break;}
    }
    private static Vec3 turn(Vec3 v,Vec3 to,double limit){Vec3 a=v.normalize(),b=to.normalize();double angle=Math.acos(Math.max(-1,Math.min(1,a.dot(b))));if(angle<=limit)return b;Vec3 side=b.subtract(a.scale(a.dot(b))).normalize();return a.scale(Math.cos(limit)).add(side.scale(Math.sin(limit)));}
    private void mist(Player p){
        velocity=velocity.scale(.85);setPos(position().add(velocity.scale(2)));rotation+=.02f*left/300f;
        if(alpha<165){scale+=.05f;alpha+=2;}else{scale*=.975f;alpha++;}if(alpha>=170){discardAttack();return;}
        for(LivingEntity t:targets(new AABB(position(),position()).inflate(17/16d*scale),p))if(MIST_IMMUNITY.getOrDefault(t,0L)<=sourceClock()&&hit(p,t,10,damage,false)){MIST_IMMUNITY.put(t,sourceClock()+60);if(hits>=2){finish(p);return;}}
    }
    private void orb(Player p){
        boolean wet=level().getFluidState(blockPosition()).is(FluidTags.WATER);
        if(wet){water=true;velocity=velocity.multiply(1,.33,1);}else if(water){finish(p);return;}
        if(water){velocity=velocity.scale(1.02);scale*=1.01f;left--;if(blockBetween(position(),position().add(velocity))){finish(p);return;}setPos(position().add(velocity));}
        else{
            Vec3 horizontal=new Vec3(direction.x,0,direction.z).normalize();double speed=Math.min(8/16d,Math.abs(velocity.dot(horizontal))+.3/16d);velocity=horizontal.scale(speed).add(0,Math.max(-15/16d,velocity.y-.3/16d),0);
            Vec3 next=position().add(velocity);var wall=level().clip(new ClipContext(position(),next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS){if(wall.getDirection().getAxis().isHorizontal()){finish(p);return;}state=1;next=wall.getLocation().add(0,.02,0);velocity=horizontal.scale(speed).add(0,4/16d,0);}
            if(state==1){
                landTime++;Vec3 above=next.add(0,1,0);var ground=level().clip(new ClipContext(above,next.add(0,-7,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if(ground.getType()==HitResult.Type.MISS){state=0;landTime=0;}else next=ground.getLocation().add(0,.02,0);
            }setPos(next);rotation+=.1f;
        }
        if(water){Vec3 across=new Vec3(-direction.z,0,direction.x);hitLine(p,position().subtract(across.scale(66/16d*scale)),position().add(across.scale(66/16d*scale)),40/16d,10,damage*1.5f,false);}
        else if(landTime>=10){double sy=waveY(landTime,left);hitLine(p,position(),position().add(0,sy*110/16d,0),(1+.1*(1-Math.sin((landTime-10)*.1)))*40/16d,10,damage,false);}
        else for(LivingEntity t:targets(new AABB(position(),position()).inflate(13/16d),p))hit(p,t,10,damage,false);
    }
    public static double waveY(double land,double left){return (1+.2*(1+Math.sin((land-10)*.1)))*clamp((land-10)/30)*clamp(left/30);}
    private List<LivingEntity> targets(AABB bounds,Player p){return level().getEntitiesOfClass(LivingEntity.class,bounds,t->WeaponUser.canHit(p,t));}
    private void hitLine(Player p,Vec3 a,Vec3 b,double width,int cd,float amount,boolean critical){for(LivingEntity t:targets(new AABB(a,b).inflate(width/2+1),p))if(ArkGeometry.line(t.getBoundingBox(),a,b,width,frame))hit(p,t,cd,amount,critical);}
    private boolean hit(Player p,LivingEntity t,int cd,float amount,boolean critical){
        if(isRemoved()||immunity.containsKey(t.getUUID()))return false;
        boolean ordinary=kind==MONOLITH||kind==ORB||kind==BEAM||kind==BLADE&&(mode<=PURE||mode==COLD||mode==EVIL);
        long clock=sourceClock();if(ordinary&&NPC_IMMUNITY.getOrDefault(t,Map.of()).getOrDefault(ownerUuid,0L)>clock)return false;
        int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);boolean started=fire>0&&!t.isOnFire();if(started)t.setSecondsOnFire(1);
        if(kind==MONOLITH&&hits>0)amount*=.25f;
        float applied=amount+EnchantmentHelper.getDamageBonus(weapon,t.getMobType())*(critical?2:1);
        if(!WeaponDamage.hurt(t,WeaponUser.source(this,p),applied)){if(started)t.clearFire();return false;}
        immunity.put(t.getUUID(),cd);if(ordinary)NPC_IMMUNITY.computeIfAbsent(t,k->new HashMap<>()).put(ownerUuid,clock+60);hits++;p.setLastHurtMob(t);
        if(fire>0)t.setSecondsOnFire(4*fire);EnchantmentHelper.doPostHurtEffects(t,p);weapon.getAllEnchantments().forEach((e,l)->e.doPostAttack(p,t,l));
        double kb=(kind==BLADE?.75:kind==MONOLITH?1:0)+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK)*.5;if(kb>0){Vec3 away=t.position().subtract(p.position());t.knockback(kb,-away.x,-away.z);}
        if(kind==BLADE){
            if(mode==PURE){BiomeEntity sigil=BiomeCombat.attacks(p).stream().filter(e->e.kind==SIGIL).findFirst().orElse(null);if(sigil==null){sigil=create(p,SIGIL,PURE,center(t),frame,0,900);sigil.target=t;sigil.spawn();}else{sigil.target=t;sigil.left=900;}}
            if(mode==HOT&&cooldown<=0){shred+=62;cooldown=20;invulnerable(p,8);if(p.getDeltaMovement().y<0)impulse(p,p.getDeltaMovement().multiply(1,0,1).add(0,2*3/16d,0));sound(p,"galaxia_hit30");}
            if(mode==COLD&&combo==2)t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.GLACIAL_STATE.get(),14));
            if(mode==EVIL&&!charged&&!bounced){p.heal(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.damage(2));double speed=Math.min(22*3/16d,Math.max(8*3/16d,p.getDeltaMovement().length())*(p.onGround()?.2:1));impulse(p,direction.scale(-speed));bounced=true;invulnerable(p,10);if(++BiomeCombat.control(p).power==3)sound(p,"galaxia_item79");}
            if(mode==ASTRAL)invulnerable(p,20);
        }
        if(critical)event("critical",center(t),center(t).subtract(center(p)).normalize());
        if(kind==DASH)event("dash_hit",center(t),center(p).subtract(center(t)).normalize());return true;
    }
    private void invulnerable(Player p,int frames){ArkCombat.controls(p).dashInvulnerableUntil=Math.max(ArkCombat.controls(p).dashInvulnerableUntil,level().getGameTime()+(frames+2)/3);}
    private boolean touches(Vec3 at,double diameter){return level().getBlockCollisions(this,new AABB(at,at).inflate(diameter/2)).iterator().hasNext();}
    private boolean blockBetween(Vec3 a,Vec3 b){return level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS;}
    private void impulse(Player p,Vec3 delta){p.setDeltaMovement(delta);p.hurtMarked=true;p.fallDistance=0;}
    // Owner motion advances at 60 Hz; extraUpdates=1 callers pass half a source-frame displacement.
    private boolean movePlayer(Player p,Vec3 delta){int slices=Math.max(1,(int)Math.ceil(delta.length()/.2));Vec3 slice=delta.scale(1d/slices);for(int i=0;i<slices;i++){if(level().getBlockCollisions(p,p.getBoundingBox().move(slice)).iterator().hasNext())return false;p.setPos(p.position().add(slice));moved=true;}p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);return true;}
    private void finish(Player p){
        if(ended)return;
        if(kind==BLADE&&mode==ASTRAL&&state==1)impulse(p,direction.scale(30*3/16d*.33));
        if(kind==BLADE&&mode==HOT){sound(p,"galaxia_hit43");if(clamp(shred/250)>.5){BiomeEntity e=create(p,HOT_SHOT,HOT,position(),frame,damage*4.5f,(int)(30+clamp(shred/250)*30));e.direction=aim(p);e.frame=GalaxiaEntity.aimFrame(e.direction,frame.right());e.shred=shred;e.scale=(float)(1+clamp(shred/250));e.velocity=e.direction.scale(6/16d);e.sound(p,"galaxia_item90");e.spawn();}}
        if(kind==HOT_SHOT){sound(p,"galaxia_item60");event("hot_end",position());}
        if(kind==BEAM){sound(p,"true_biome_beam_end");event("beam_end",position());}
        if(kind==ORB){sound(p,"biome_splash",1,(random.nextFloat()-.5f)*2);event("orb_end",position());}
        discardAttack();
    }
    public void discardAttack(){if(ended)return;ended=true;super.discard();}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ended&&left>0&&valid(owner()))return;super.remove(reason);}
    private void sound(Player p,String name){sound(p,name,1,0);}
    private void sound(Player p,String name,float volume,float pitch){TrueBiomeCombat.sound(this,p,name,volume,pitch,false,false);}
    private void event(String name,Vec3 at){event(name,at,direction);}
    private void event(String name,Vec3 at,Vec3 fxDirection){CompoundTag s=snapshot();s.putString("Event","biome_fx");s.putString("Fx",name);vector(s,"At",at);vector(s,"Direction",fxDirection);net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,s);}
    private void remember(){CompoundTag s=new CompoundTag();vector(s,"Position",position());s.putFloat("Rotation",rotation);trail.add(0,s);while(trail.size()>(kind==BEAM?2:10))trail.remove(trail.size()-1);}
    private CompoundTag snapshot(){
        CompoundTag s=new CompoundTag();s.putUUID("Attack",getUUID());s.putLong("StartTick",startTick);s.putInt("Owner",ownerId);s.putInt("Kind",kind);s.putInt("Mode",mode);s.putInt("State",state);s.putInt("Age",age);s.putInt("Left",left);s.putInt("Duration",duration);s.putInt("Combo",combo);s.putInt("Charge",charge);s.putInt("OverCharge",overCharge);s.putInt("Coyote",coyote);s.putInt("DashTime",dashTime);s.putInt("Variant",variant);s.putInt("Alpha",alpha);s.putInt("LandTime",landTime);s.putBoolean("Water",water);s.putDouble("Shred",shred);s.putDouble("Size",size);s.putDouble("Wait",wait);s.putFloat("Scale",scale);s.putFloat("Rotation",rotation);
        vector(s,"Position",position());vector(s,"F",frame.forward());vector(s,"R",frame.right());vector(s,"Direction",direction);vector(s,"Velocity",velocity);vector(s,"Start",start);vector(s,"End",end);vector(s,"CP1",cp1);vector(s,"CP2",cp2);Player p=owner();vector(s,"Center",p==null?position():center(p));vector(s,"OwnerVelocity",p==null?Vec3.ZERO:p.getDeltaMovement().scale(1/3d));s.put("Trail",trail.copy());return s;
    }
    private void publish(){CompoundTag s=snapshot();s.put("Samples",samples.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){if(clientSnapshot!=null&&level().isClientSide&&key.equals(VIEW))clientSnapshot.initialize(view());super.onSyncedDataUpdated(key);}
    public CompoundTag renderState(float partial){
        CompoundTag state=clientSnapshot.state().isEmpty()?view():clientSnapshot.state();ListTag list=state.getList("Samples",10);if(list.isEmpty())return state;
        double index=clamp(partial)*(list.size()-1);int i=(int)index;CompoundTag a=list.getCompound(i),b=list.getCompound(Math.min(i+1,list.size()-1)),s=a.copy();double t=index-i;
        for(String key:new String[]{"Position","Center","Start","End","CP1","CP2"})vector(s,key,vector(a,key).lerp(vector(b,key),t));
        for(String key:new String[]{"Age","Scale","Rotation"})s.putDouble(key,lerp(a.getDouble(key),b.getDouble(key),t));
        AttackFrame f=AttackFrame.interpolateView(new AttackFrame(vector(a,"F"),vector(a,"R")),new AttackFrame(vector(b,"F"),vector(b,"R")),t);vector(s,"F",f.forward());vector(s,"R",f.right());
        Vec3 d=vector(a,"Direction").lerp(vector(b,"Direction"),t).normalize();if(d.lengthSqr()>.5)vector(s,"Direction",d);return s;
    }
}
