package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.item.TrueBiomeBladeItem;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.combat.TrueBiomeRules.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** OmegaBiomeBlade projectile state machines. Clocks are Terraria updates, not Minecraft ticks. */
public final class TrueBiomeEntity extends Entity {
    public static final int BLADE=0,BEAM=1,WHEEL=2,DASH=3,CHAIN=4,HOOK=5,BLAST=6,MONOLITH=7,SIGIL=8,WAVE=9;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(TrueBiomeEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot=new AttackRenderSnapshot();
    private UUID ownerUuid;
    private int ownerId,kind,mode,state,age,left=2,charge,overCharge,indicator,cooldown,hits,duration,generation,branches=3;
    private float damage,scale=1,rotation;
    private double shredCharge,wait,size=1,gravity;
    private Vec3 direction=new Vec3(0,0,1),velocity=Vec3.ZERO,start=Vec3.ZERO,end=Vec3.ZERO,origin=Vec3.ZERO,tangent=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(direction,new Vec3(1,0,0));
    private ItemStack weapon=ItemStack.EMPTY,heldStack=ItemStack.EMPTY;
    private int selectedMain,selectedSecondary;
    private TrueBiomeEntity wheel;
    private LivingEntity target,from;
    private final Map<UUID,Integer> immunity=new HashMap<>();
    private static final Map<LivingEntity,Map<UUID,Long>> NPC_IMMUNITY=new WeakHashMap<>();
    private final double[] seeds=new double[3],angles=new double[3];
    private final List<Vec3[]> chains=new ArrayList<>();
    private ListTag samples=new ListTag(),trail=new ListTag();
    private boolean ended,dashing,movedPlayer,chargeSound,directFire=true,spinSound,beamGrowing;
    private int bounceTime;
    private int chainTimer,chargeSoundCooldown;
    private long sourceClock;
    public TrueBiomeEntity(EntityType<? extends TrueBiomeEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    private static TrueBiomeEntity create(Player p,int kind,int mode,Vec3 at,AttackFrame frame,float damage,int life){
        TrueBiomeEntity e=new TrueBiomeEntity(ArkOfCosmos.TRUE_BIOME_ATTACK.get(),p.level());
        e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=kind;e.mode=mode;e.frame=frame;e.direction=frame.forward();e.damage=damage;e.left=life;
        e.heldStack=p.getMainHandItem();e.weapon=e.heldStack.copy();e.selectedMain=TrueBiomeBladeItem.main(e.weapon);e.selectedSecondary=TrueBiomeBladeItem.secondary(e.weapon);e.setPos(at);
        p.level().addFreshEntity(e);return e;
    }
    public static TrueBiomeEntity blade(Player p,int mode){
        TrueBiomeEntity e=create(p,BLADE,mode,center(p),AttackFrame.capture(p),base(mode),mode==FURY?30:2);
        if(mode==FURY)for(TrueBiomeEntity w:TrueBiomeCombat.attacks(p))if(w.kind==WHEEL){
            Vec3 toward=w.position().subtract(center(p)).normalize();
            if(toward.dot(WeaponUser.aim(p).subtract(center(p)).normalize())<Math.cos(Math.PI/4))w.finish(p);
            else{e.wheel=w;e.dashing=true;e.start=center(p);w.left=60;e.invulnerable(p,20);}break;
        }
        e.sound(p,mode==TIDES?"galaxia_item101":"galaxia_item90");e.publish();return e;
    }
    public static void passive(Player p,int mode){
        if(mode==PRIDE){TrueBiomeEntity e=create(p,BEAM,mode,center(p),AttackFrame.capture(p),damage(mode,200),50);e.velocity=e.direction.scale(15/16d);e.state=1;e.publish();}
        else{TrueBiomeEntity e=create(p,BLAST,mode,center(p),AttackFrame.capture(p),damage(mode,200),60);e.publish();}
    }
    public static void ensureHook(Player p,boolean proc){
        TrueBiomeEntity hook=null;for(TrueBiomeEntity e:TrueBiomeCombat.attacks(p))if(e.kind==HOOK){hook=e;break;}
        if(hook==null)hook=create(p,HOOK,CHAINED,center(p),AttackFrame.capture(p),damage(CHAINED,500),2);
        if(proc&&hook.state==0){hook.state=1;hook.left=30;hook.direction=hook.frame.forward();hook.sound(p,"item71");hook.event("hook_start",hook.position());}
        hook.publish();
    }
    public int ownerId(){return level().isClientSide?view().getInt("Owner"):ownerId;}
    public int kind(){return level().isClientSide?view().getInt("Kind"):kind;}
    public ItemStack weapon(){return weapon;}
    private Player owner(){return WeaponUser.resolve(level(),ownerUuid) instanceof Player p?p:null;}
    private boolean valid(Player p){return WeaponUser.active(p)&&TrueBiomeCombat.holding(p)&&p.getMainHandItem()==heldStack&&TrueBiomeBladeItem.main(heldStack)==selectedMain&&TrueBiomeBladeItem.secondary(heldStack)==selectedSecondary;}
    private static Vec3 center(LivingEntity e){return e.getBoundingBox().getCenter();}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag s){}
    @Override protected void addAdditionalSaveData(CompoundTag s){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<256*256;}
    @Override public void tick(){
        super.tick();if(level().isClientSide){clientSnapshot.tick(view(),"Age");return;}
        Player p=owner();if(!valid(p)){discardAttack();return;}
        samples=new ListTag();samples.add(snapshot());
        int steps=kind==WHEEL?12:kind==BEAM||kind==BLADE&&mode!=CHAINED?6:3;
        for(int i=0;i<steps&&!isRemoved();i++){
            sourceClock=level().getGameTime()*6+i*6/steps;
            immunity.replaceAll((id,t)->t-1);immunity.values().removeIf(t->t<=0);cooldown--;
            switch(kind){
                case BLADE -> bladeStep(p);
                case BEAM -> beamStep(p);
                case WHEEL -> wheelStep(p);
                case DASH -> hitLine(p,start,end,0,60,damage*2,1,true);
                case CHAIN -> {if(!WeaponUser.canHit(p,target)||from==null||!from.isAlive())discardAttack();else{setPos(center(target));start=center(from);end=center(target);hit(p,target,30,damage,.1,false);}}
                case HOOK -> hookStep(p);
                case BLAST -> blastStep(p);
                case MONOLITH -> monolithStep(p);
                case SIGIL -> {if(!WeaponUser.canHit(p,target))discardAttack();else setPos(center(target));}
                case WAVE -> waveStep(p);
            }
            if(isRemoved())break;
            age++;remember();if(--left<=0)finish(p);samples.add(snapshot());
        }
        if(movedPlayer&&p instanceof ServerPlayer sp){if(sp.connection!=null)sp.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());movedPlayer=false;}
        if(!isRemoved())publish();
    }
    private Vec3 rotate(Vec3 v,double a){Vec3 n=frame.normal();return v.scale(Math.cos(a)).add(n.cross(v).scale(Math.sin(a))).add(n.scale(n.dot(v)*(1-Math.cos(a))));}
    private Vec3 aim(Player p){Vec3 v=WeaponUser.aim(p).subtract(center(p)).normalize();return v.lengthSqr()<1e-8?p.getLookAngle():v;}
    private void bladeStep(Player p){
        boolean held=TrueBiomeCombat.channeling(p);Vec3 c=center(p);
        if(mode==PRIDE){
            if(state==0){double phase=frame.angle(direction);frame=AttackFrame.capture(p);direction=frame.direction(phase);}
            if(!held&&state==0){if(charge<300){sound(p,"galaxia_item77");finish(p);return;}state=1;left=90;direction=aim(p);sound(p,"galaxia_item80");event("pride_throw",position());}
            if(state==0){
                if(!chargeSound&&charge>=300){chargeSound=true;sound(p,"galaxia_item76");}
                double angle=frame.angle(direction);
                if(angle>-3*Math.PI/4&&angle<-Math.PI/4&&spinSound)spinSound=false;
                else if(angle>Math.PI/4&&angle<3*Math.PI/4&&!spinSound){spinSound=true;directFire=true;sound(p,"item71");}
                if(charge>=300&&(charge+overCharge)%30==29){Vec3 dir=frame.direction(random.nextDouble()*Math.PI*2);if(target!=null&&target.isAlive())dir=turn(dir,center(target).subtract(c),Math.PI/2);beam(p,c,dir,damage*.5f,80);}
                if(charge>=450&&directFire&&direction.dot(aim(p))>Math.cos(.1)){beam(p,c,aim(p),damage*.5f,80);event("pride_direct",c.add(aim(p).scale(scale*1.88*78/16)));directFire=false;}
                scale=1+charge/600f*1.5f;direction=rotate(direction,Math.max(.4,charge/600d)*Math.PI/4*.2).normalize();
                setPos(c.add(direction.scale(scale*10/16d)));left=91;if(++charge>600){charge=600;overCharge++;}
            }else{
                double t=(90-left)/90d;setPos(c.add(direction.scale(scale*10/16d+440/16d*net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.throwCurve(t))));
                scale=(1+charge/600f*1.5f)*(float)Math.max(.3,1-Math.max(0,(t-.6)/.4));
            }
            rotation=(float)frame.angle(direction);
            hitLine(p,position(),position().add(direction.scale(140/16d*scale)),25/16d*scale,20,(float)(damage*(state==1?lerp(1,3.3,charge/600d):.3+.9*charge/600d)),state==1?1:.24,false);
        }else if(mode==CHAINED){
            if(!held){finish(p);return;}left=2;direction=WeaponUser.aim(p).subtract(c).normalize();frame=GalaxiaEntity.aimFrame(direction,AttackFrame.capture(p).right());setPos(c.add(direction.scale(60/16d)));rotation=(float)frame.angle(direction);
            updateChains(p);chainTimer++;
            if(chainTimer>=30)for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(40),v->WeaponUser.canHit(p,v))){
                boolean tip=false,body=false;
                for(Vec3[] curve:chains)if(new AABB(curve[4],curve[4]).inflate(25/16d).intersects(t.getBoundingBox()))tip=true;
                // The confirmed spatial adaptation follows each rendered curve, not a chord to its tip.
                for(int k=0;k<chains.size()&&!body;k++){
                    Vec3[] curve=chains.get(k);Vec3 previous=curve[0];AttackFrame chainFrame=chainFrame(k);
                    for(int j=1;j<40&&!body;j++){Vec3 point=bezier(curve,j/39d);body=ArkGeometry.line(t.getBoundingBox(),previous,point,20/16d,chainFrame);previous=point;}
                }
                if(tip||body){float before=t.getHealth()+t.getAbsorptionAmount();
                    if(hit(p,t,30,tip?damage*2:damage*.5f,tip?.1:.05,tip)&&tip){
                        float dealt=Math.max(0,before-t.getHealth()-t.getAbsorptionAmount());ghostChains(p,t,dealt*.5f);
                    }
                }
            }
        }else if(mode==FURY){
            if(!held){finish(p);return;}
            shredCharge=Math.max(0,Math.min(500,shredCharge));direction=aim(p);if(!dashing)frame=GalaxiaEntity.aimFrame(direction,AttackFrame.capture(p).right());rotation=(float)frame.angle(direction);scale=(float)(1+shred(shredCharge));setPos(c.add(direction.scale(60/16d)));
            if(dashing){
                if(wheel==null||wheel.isRemoved()){endDash(p);}
                else{Vec3 delta=wheel.position().subtract(c);if(delta.length()<60/16d){wheel.finish(p);endDash(p);}else if(!movePlayer(p,delta.normalize().scale(60/16d))){wheel.finish(p);endDash(p);}}
            }
            if(!dashing&&touches(c.add(direction.scale(100/16d*scale)),10/16d)&&!p.onGround()&&cooldown<=0){
                p.setDeltaMovement(direction.scale(-3));p.hurtMarked=true;p.fallDistance=0;cooldown=30;bounceTime=20;sound(p,"galaxia_impact");event("pogo",c.add(direction.scale(100/16d*scale)));
            }
            if(shred(shredCharge)>.25){if(chargeSoundCooldown<=0){chargeSoundCooldown=20;sound(p,"true_biome_charge",1,0,true,true);}}
            else chargeSoundCooldown--;
            shredCharge=Math.max(0,shredCharge-.65);
            bounceTime--;
            if(left<=2){left=2;hitLine(p,c,c.add(direction.scale(130/16d*scale)),86/16d*scale,furyCooldown(shredCharge),furyDamage(shredCharge),.1,false);}
        }else{
            if(state==0){direction=aim(p);frame=GalaxiaEntity.aimFrame(direction,AttackFrame.capture(p).right());}
            if(!held&&state==0){if(charge<125){sound(p,"galaxia_item109");finish(p);return;}state=1;left=duration=lungeDuration(charge);start=position().subtract(c);sound(p,"galaxia_item120",.5f,0,false,false);}
            if(state==0){
                direction=aim(p);setPos(c.add(direction.scale(70/16d*net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.chargeDisplacement(charge/500d))));charge=Math.min(500,charge+1);left=2;overCharge--;
                if(indicator<3&&charge>=125*(indicator+1)){blast(p,1+indicator*.15f,damage*.8f);sound(p,"true_biome_pulse",1,(float)(-.2+.1*indicator),false,false);indicator++;overCharge=20;}
                if(charge==500&&indicator<4){blast(p,1+indicator*.15f,damage*.8f);indicator++;overCharge=20;sound(p,"galaxia_beacon");}
            }else{
                setPos(c.add(start.lerp(direction.scale(40/16d),clamp((duration-left)/(double)duration*2))));
                Vec3 tip=c.add(direction.scale(120/16d*scale));
                if(touches(tip,10/16d)){slam(p,tip);finish(p);return;}
                if(!movePlayer(p,direction.scale(30/16d))){slam(p,tip);finish(p);return;}
                hitLine(p,c,c.add(direction.scale(145/16d*scale)),25/16d*scale,16,(float)(damage*(1+2.5*Math.pow(charge/500d,2))),1,false);
            }
            scale=1+charge/500f*.3f;rotation=(float)frame.angle(direction);
        }
    }
    private void updateChains(Player p){
        for(int i=0;i<3;i++)if((chainTimer-i*2)%6==0||seeds[i]==0){angles[i]=frame.angle(direction)+(random.nextDouble()-.5)*(i==0?Math.PI/32:Math.PI/8);seeds[i]=.2+random.nextDouble()*99.8;chainTimer++;}
        if(chainTimer%10==1)sound(p,"true_biome_flail");
        chains.clear();for(int i=0;i<3;i++)chains.add(chain(center(p),chainFrame(i),angles[i],seeds[i],((chainTimer-i*10d/3)%10)/10,i==0?1:-1));
    }
    private AttackFrame chainFrame(int index){
        double angle=index*Math.PI*2/3;
        return new AttackFrame(frame.forward(),frame.right().scale(Math.cos(angle)).add(frame.normal().scale(Math.sin(angle))));
    }
    private void ghostChains(Player p,LivingEntity t,float amount){
        sound(p,"true_biome_slice");Set<UUID> excluded=new HashSet<>();excluded.add(t.getUUID());
        for(int i=0;i<3;i++){
            LivingEntity next=null;double distance=400/16d;
            for(LivingEntity v:level().getEntitiesOfClass(LivingEntity.class,t.getBoundingBox().inflate(distance),v->WeaponUser.enemy(p,v)&&!excluded.contains(v.getUUID())))
                if(center(v).distanceTo(center(t))<distance){distance=center(v).distanceTo(center(t));next=v;}
            if(next==null)break;excluded.add(next.getUUID());
            TrueBiomeEntity e=create(p,CHAIN,CHAINED,center(t),frame,amount,20);e.target=next;e.from=t;e.gravity=(30+random.nextDouble()*20)/16;e.start=center(t);e.end=center(next);e.publish();
        }
    }
    private void beam(Player p,Vec3 at,Vec3 dir,float amount,int life){TrueBiomeEntity e=create(p,BEAM,PRIDE,at,frame,amount,life);e.velocity=dir.scale(15/16d);e.direction=dir;e.publish();}
    private void beamStep(Player p){
        if(age==0)sound(p,"true_biome_beam_start");
        if(age>=15){scale+=beamGrowing?.02f:-.02f;if(scale<=.82f)beamGrowing=true;else if(scale>=1)beamGrowing=false;}
        if(target==null){for(TrueBiomeEntity e:TrueBiomeCombat.attacks(p))if(e.kind==SIGIL&&WeaponUser.canHit(p,e.target)){target=e.target;break;}}
        else if(!target.isAlive())target=null;
        if(target!=null&&target.isAlive()){
            Vec3 to=center(target).subtract(position());
            if(to.length()>=to.subtract(velocity).length()&&velocity.normalize().dot(to.normalize())>Math.cos(Math.PI/4)){
                left=70;double limit=Math.toRadians(lerp(15,2.5,clamp(to.length()*16/10)));velocity=turn(velocity,to,limit).scale(velocity.length());
            }
        }
        Vec3 old=position();setPos(old.add(velocity));direction=velocity.normalize();rotation=(float)frame.angle(direction);
        hitLine(p,old,position(),32/16d,80,damage,.05,false);
    }
    private static Vec3 turn(Vec3 dir,Vec3 to,double radians){
        Vec3 a=dir.normalize(),b=to.normalize();double angle=Math.acos(Math.max(-1,Math.min(1,a.dot(b))));if(angle<=radians)return b;
        Vec3 side=b.subtract(a.scale(a.dot(b))).normalize();return a.scale(Math.cos(radians)).add(side.scale(Math.sin(radians))).normalize();
    }
    private void wheelStep(Player p){
        velocity=velocity.scale(.96);Vec3 old=position(),delta=velocity.scale(2); // AI explicitly moves once, then Terraria moves again.
        int n=Math.max(1,(int)Math.ceil(delta.length()/.2));Vec3 slice=delta.scale(1d/n);
        for(int i=0;i<n;i++){
            if(touches(position().add(slice),45/16d)){velocity=velocity.scale(.95);break;}setPos(position().add(slice));
        }
        hitLine(p,position().subtract(direction.scale(42/16d*scale)),position().add(direction.scale(42/16d*scale)),76/16d*scale,60,damage,.4,false);
    }
    private void endDash(Player p){
        if(!dashing)return;dashing=false;sound(p,"meaty");p.setDeltaMovement(p.getDeltaMovement().scale(.1));
        TrueBiomeEntity slash=create(p,DASH,FURY,start.lerp(center(p),.5),frame,damage*2.5f,20);slash.start=start;slash.end=center(p);slash.publish();
    }
    private void hookStep(Player p){
        if(state==0){left=2;Vec3 goal=center(p).subtract(frame.right().scale(40/16d)).add(frame.direction(level().getGameTime()/20d+Math.PI/4).scale(Math.sqrt(2)*10/16d));
            Vec3 at=position().lerp(goal,.05),delta=goal.subtract(at);setPos(at.add(delta.normalize().scale(Math.min(4/16d,delta.length()))));
        }else{
            direction=rotate(direction,-.41887906193733215).normalize();rotation=(float)frame.angle(direction);setPos(center(p).add(direction.scale(140/16d*(1+.3*Math.sin(left/30d*Math.PI)))));
            hitLine(p,position(),position(),66/16d,40,damage,0,false);
            if(left==1){state=0;left=2;sound(p,"galaxia_item101");event("hook_end",position());}
        }
    }
    private void blast(Player p,double size,float amount){TrueBiomeEntity e=create(p,BLAST,TIDES,center(p),frame,amount,60);e.size=size;e.publish();}
    private void blastStep(Player p){
        scale=(float)((1+Math.sin(left/60d*Math.PI)*.2)*size);
        if(left==60){sound(p,"galaxia_item79");event("blast_start",position());}
        if(left==40){sound(p,"true_biome_blast");event("blast_burst",position());}
        // Source has ordinary NPC immunity (10 source ticks), not per-projectile local immunity.
        if(left<40)for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(85/16d*scale),v->WeaponUser.canHit(p,v)))
            if(npcReady(t))if(hit(p,t,10,damage,.5,false))npcHit(t);
    }
    private void slam(Player p,Vec3 tip){
        sound(p,"galaxia_impact");
        BlockHitResult hit=level().clip(new ClipContext(center(p),tip.add(direction.scale(1)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 at=hit.getType()==HitResult.Type.MISS?tip:hit.getLocation();Vec3 normal=hit.getType()==HitResult.Type.MISS?direction.scale(-1):Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        monolith(p,at,normal,1,81,0);
        TrueBiomeEntity w=create(p,WAVE,TIDES,at,frame,damage*1.1f,2);w.direction=normal;w.origin=at;w.size=charge/500d;w.wait=(1-w.size)*34;
        w.tangent=frame.right().subtract(normal.scale(frame.right().dot(normal))).normalize();if(w.tangent.lengthSqr()<.5)w.tangent=normal.cross(new Vec3(0,0,1)).normalize();if(w.tangent.lengthSqr()<.5)w.tangent=new Vec3(1,0,0);w.publish();
    }
    private void monolith(Player p,Vec3 at,Vec3 normal,double size,int life,double delay){
        TrueBiomeEntity e=create(p,MONOLITH,TIDES,at,GalaxiaEntity.aimFrame(normal,frame.right()),kind==WAVE?damage:damage*1.1f,life);e.direction=normal;e.size=size;e.wait=delay;e.state=random.nextInt(4);e.publish();
    }
    private void waveStep(Player p){
        left=2;if(wait-->0)return;Vec3 other=direction.cross(tangent).normalize();int surviving=0;
        int generations=1;for(double s=size/Math.pow(.8,generation);s*.8>.4;s*=.8)generations++;
        for(int i=0;i<2;i++)if((branches&(1<<i))!=0){
            double angle=Math.PI*i+Math.PI*generation/generations;Vec3 radial=tangent.scale(Math.cos(angle)).add(other.scale(Math.sin(angle)));
            Vec3 above=origin.add(radial.scale((generation+1)*150/16d)).add(direction.scale(150/16d));
            var hit=level().clip(new ClipContext(above,above.subtract(direction.scale(300/16d)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()==HitResult.Type.MISS)continue;surviving|=1<<i;
            monolith(p,hit.getLocation(),Vec3.atLowerCornerOf(hit.getDirection().getNormal()),size,102,0);
        }
        if(surviving!=0&&size*.8>.4){TrueBiomeEntity next=create(p,WAVE,TIDES,origin,frame,damage,2);next.origin=origin;next.direction=direction;next.tangent=tangent;next.size=size*.8;next.generation=generation+1;next.branches=surviving;next.wait=2+Math.sqrt(1-Math.pow(next.size-1,2))*3;next.publish();}
        discardAttack();
    }
    private void monolithStep(Player p){
        if(wait>0){wait--;left=102;return;}
        if(left==100&&size>=1)event("monolith_start",position());
        if(left==80){sound(p,"galaxia_eruption");event("monolith_burst",position());}
        double t=(100-left)/100d,width=monolithWidth(t)*90*size/16,height=monolithHeight(t)*420*size/16;
        for(LivingEntity target:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position().add(direction.scale(height))).inflate(width/2+1),v->WeaponUser.canHit(p,v)))
            if(ArkGeometry.line(target.getBoundingBox(),position(),position().add(direction.scale(height)),width,frame)&&npcReady(target))
                if(hit(p,target,10,damage*(hits>0?.15f:1),1,false))npcHit(target);
    }
    private boolean npcReady(LivingEntity target){return NPC_IMMUNITY.getOrDefault(target,Map.of()).getOrDefault(ownerUuid,0L)<=sourceClock;}
    private void npcHit(LivingEntity target){NPC_IMMUNITY.computeIfAbsent(target,k->new HashMap<>()).put(ownerUuid,sourceClock+20);}
    private boolean touches(Vec3 at,double diameter){return level().getBlockCollisions(this,new AABB(at,at).inflate(diameter/2)).iterator().hasNext();}
    private boolean movePlayer(Player p,Vec3 delta){
        int slices=Math.max(1,(int)Math.ceil(delta.length()/.2));Vec3 slice=delta.scale(1d/slices);
        for(int i=0;i<slices;i++){if(level().getBlockCollisions(p,p.getBoundingBox().move(slice)).iterator().hasNext())return false;p.setPos(p.position().add(slice));movedPlayer=true;}
        p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);return true;
    }
    private void invulnerable(Player p,int updates){ArkCombat.controls(p).dashInvulnerableUntil=Math.max(ArkCombat.controls(p).dashInvulnerableUntil,level().getGameTime()+(updates+2)/3);}
    private void hitLine(Player p,Vec3 a,Vec3 b,double width,int cd,float amount,double proc,boolean crit){
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(width/2+1),v->WeaponUser.canHit(p,v)))
            if(ArkGeometry.line(t.getBoundingBox(),a,b,width,frame))hit(p,t,cd,amount,proc,crit);
    }
    private boolean hit(Player p,LivingEntity t,int cd,float amount,double proc,boolean crit){
        if(isRemoved()||immunity.containsKey(t.getUUID()))return false;immunity.put(t.getUUID(),cd);
        int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);boolean started=fire>0&&!t.isOnFire();if(started)t.setSecondsOnFire(1);
        float enchanted=amount+EnchantmentHelper.getDamageBonus(weapon,t.getMobType())*(crit?2:1);
        if(!WeaponDamage.hurt(t,WeaponUser.source(this,p),enchanted)){if(started)t.clearFire();return false;}
        hits++;TrueBiomeCombat.proc(p,proc);p.setLastHurtMob(t);
        if(fire>0)t.setSecondsOnFire(4*fire);EnchantmentHelper.doPostHurtEffects(t,p);weapon.getAllEnchantments().forEach((e,l)->e.doPostAttack(p,t,l));
        double knockback=(kind==BLADE||kind==WHEEL?.75:kind==BLAST||kind==MONOLITH?1:0)+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK)*.5;
        if(knockback>0){Vec3 away=t.position().subtract(p.position());t.knockback(knockback,-away.x,-away.z);}
        if(crit)event(kind==DASH?"dash_hit":"critical",center(t));
        if(kind==BLADE&&mode==PRIDE&&state==1){target=t;TrueBiomeEntity sigil=null;for(TrueBiomeEntity e:TrueBiomeCombat.attacks(p))if(e.kind==SIGIL){sigil=e;break;}
            if(sigil==null)sigil=create(p,SIGIL,PRIDE,center(t),frame,0,1000);sigil.target=t;sigil.left=1000;sigil.publish();}
        if(kind==BLADE&&mode==FURY&&cooldown<=0){shredCharge+=62;cooldown=20;invulnerable(p,10);if(p.getDeltaMovement().y<0)p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1).add(0,.375,0));sound(p,"galaxia_hit30");}
        if(kind==BLADE&&mode==TIDES)invulnerable(p,20);
        if(kind==HOOK)t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.GLACIAL_STATE.get(),34));
        if(kind==BEAM){t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.ARMOR_CRUNCH.get(),30));finish(p);}
        if(kind==DASH)p.heal(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.damage(4));
        return true;
    }
    private void finish(Player p){
        if(ended)return;
        if(kind==BLADE&&mode==FURY){sound(p,"galaxia_hit43");if(shred(shredCharge)>.8){TrueBiomeEntity e=create(p,WHEEL,FURY,position(),frame,damage*2.5f,120);e.shredCharge=shredCharge;e.scale=(float)(1+shred(shredCharge));e.direction=direction;e.velocity=direction;e.sound(p,"galaxia_item90");e.publish();}if(dashing)p.setDeltaMovement(p.getDeltaMovement().scale(.1));}
        if(kind==WHEEL){sound(p,"galaxia_item60");event("wheel_end",position());}
        if(kind==BEAM){sound(p,"true_biome_beam_end");event("beam_end",position());}
        discardAttack();
    }
    public void discardAttack(){if(ended)return;if(!level().isClientSide&&kind==HOOK&&owner()!=null){sound(owner(),"true_biome_hook_end");event("hook_death",position());}ended=true;super.discard();}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ended&&left>0&&valid(owner()))return;super.remove(reason);}
    private void sound(Player p,String name){sound(p,name,1,0,false,false);}
    private void sound(Player p,String name,float volume,float pitch,boolean ownerOnly,boolean relative){TrueBiomeCombat.sound(this,p,name,volume,pitch,ownerOnly,relative);}
    private void event(String name,Vec3 at){CompoundTag s=snapshot();s.putString("Event","true_biome_fx");s.putString("Fx",name);vector(s,"At",at);net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,s);}
    private CompoundTag pose(){CompoundTag s=new CompoundTag();s.putInt("Age",age);s.putFloat("Rotation",rotation);s.putFloat("Scale",scale);vector(s,"Position",position());return s;}
    private void remember(){trail.add(0,pose());while(trail.size()>(kind==BEAM?2:10))trail.remove(trail.size()-1);}
    private CompoundTag snapshot(){
        CompoundTag s=pose();s.putUUID("Attack",getUUID());s.putInt("Owner",ownerId);s.putInt("Kind",kind);s.putInt("Mode",mode);s.putInt("State",state);s.putInt("Left",left);s.putInt("Charge",charge);s.putInt("OverCharge",overCharge);s.putInt("ChainTimer",chainTimer);s.putInt("Bounce",bounceTime);
        s.putDouble("Shred",shredCharge);s.putDouble("Size",size);s.putDouble("Wait",wait);s.putDouble("Gravity",gravity);s.putInt("Generation",generation);
        for(int i=0;i<3;i++)s.putDouble("Whip"+i,angles[i]);
        vector(s,"F",frame.forward());vector(s,"R",frame.right());vector(s,"Direction",direction);vector(s,"Velocity",velocity);vector(s,"Start",start);vector(s,"End",end);
        Player p=owner();vector(s,"Aim",p==null?direction:aim(p));vector(s,"Center",p==null?position():center(p));vector(s,"OwnerVelocity",p==null?Vec3.ZERO:p.getDeltaMovement().scale(1/3d));s.put("Trail",trail.copy());
        if(kind==BLADE&&mode==CHAINED){ListTag curves=new ListTag();for(Vec3[] curve:chains){CompoundTag c=new CompoundTag();for(int i=0;i<curve.length;i++)vector(c,"P"+i,curve[i]);AttackFrame cf=chainFrame(curves.size());vector(c,"F",cf.forward());vector(c,"R",cf.right());curves.add(c);}s.put("Chains",curves);}
        return s;
    }
    private void publish(){CompoundTag s=snapshot();s.put("Samples",samples.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){if(clientSnapshot!=null&&level().isClientSide&&key.equals(VIEW))clientSnapshot.initialize(view());super.onSyncedDataUpdated(key);}
    public CompoundTag renderState(float partial){
        CompoundTag state=clientSnapshot.state().isEmpty()?view():clientSnapshot.state();ListTag list=state.getList("Samples",10);if(list.isEmpty())return state;
        double index=clamp(partial)*(list.size()-1);int i=(int)index;CompoundTag a=list.getCompound(i),b=list.getCompound(Math.min(i+1,list.size()-1)),s=a.copy();double t=index-i;
        for(String key:new String[]{"Position","Center","Start","End"})vector(s,key,vector(a,key).lerp(vector(b,key),t));
        for(String key:new String[]{"Age","Rotation","Scale"})s.putDouble(key,lerp(a.getDouble(key),b.getDouble(key),t));
        if(s.getInt("Kind")==BLADE){
            AttackFrame view=AttackFrame.interpolateView(new AttackFrame(vector(a,"F"),vector(a,"R")),new AttackFrame(vector(b,"F"),vector(b,"R")),t);
            double phaseA=new AttackFrame(vector(a,"F"),vector(a,"R")).angle(vector(a,"Direction")),phaseB=new AttackFrame(vector(b,"F"),vector(b,"R")).angle(vector(b,"Direction"));
            double angle=phaseA+Math.atan2(Math.sin(phaseB-phaseA),Math.cos(phaseB-phaseA))*t;
            vector(s,"F",view.forward());vector(s,"R",view.right());vector(s,"Direction",view.direction(angle));s.putDouble("Rotation",angle);
        }
        if(s.getInt("Kind")==BLADE&&s.getInt("Mode")==CHAINED){
            ListTag ca=a.getList("Chains",10),cb=b.getList("Chains",10),curves=new ListTag();
            for(int k=0;k<Math.min(ca.size(),cb.size());k++){CompoundTag curve=ca.getCompound(k).copy();for(int j=0;j<5;j++)vector(curve,"P"+j,vector(ca.getCompound(k),"P"+j).lerp(vector(cb.getCompound(k),"P"+j),t));curves.add(curve);}s.put("Chains",curves);
        }return s;
    }
}
