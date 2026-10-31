package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules;
import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.*;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
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

/** Independent 60/120 Hz source state machines. No ArkDamage or Cosmos-only mechanics. */
public final class ElementsEntity extends Entity {
    public static final int BLADE=0,NEEDLE=1,STAR=2,PARRY=3,BLAST=4;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(ElementsEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot = new AttackRenderSnapshot();
    private UUID ownerUuid;
    private int ownerId,kind,mode,charge,age,left,life,hits,stitches,soundDelay;
    private boolean missed,ended,bossBurstStar,bossMovingBlast,bossRain;
    private int ownerPhase;
    private UUID rainStarTarget;
    private float damage,rotation,scale=1,snapStart;
    private Vec3 velocity=Vec3.ZERO,direction=new Vec3(0,0,1),aim=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(new Vec3(0,0,1),new Vec3(-1,0,0));
    private ItemStack weapon=ItemStack.EMPTY;
    private final Map<Integer,Integer> hitAt=new HashMap<>();
    private ListTag samples=new ListTag();
    private final float[] stitchAngles=new float[8];
    public ElementsEntity(EntityType<? extends ElementsEntity> type,Level level){super(type,level);noPhysics=true;}
    public static ElementsEntity create(LivingEntity p,int kind,int mode,int charge,float damage,Vec3 at,Vec3 velocity,AttackFrame frame){
        ElementsEntity e=new ElementsEntity(ArkOfCosmos.ELEMENTS_ATTACK.get(),p.level());
        e.ownerPhase=p instanceof MahouTsukaiEntity boss?boss.phase():0;e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=kind;e.mode=mode;e.charge=charge;e.damage=damage;e.velocity=velocity;e.frame=frame;e.direction=frame.forward();e.aim=WeaponUser.aim(p);e.weapon=p.getMainHandItem().copy();e.refreshDimensions();e.setPos(at);
        e.left=e.life=kind==BLADE?(mode==2?80:35):kind==NEEDLE?30:kind==STAR?120:kind==PARRY?15:70;
        e.scale=kind==NEEDLE?(p instanceof MahouTsukaiEntity?.6f:2.4f):kind==STAR?1.5f:1;
        for(int i=0;i<8;i++)e.stitchAngles[i]=(float)(p.getRandom().nextDouble()*Math.PI/2+Math.PI/4);
        e.samples.add(e.snapshot());e.publish();p.level().addFreshEntity(e);return e;
    }
    static ElementsEntity bossRift(MahouTsukaiEntity boss,AttackFrame frame){
        ElementsEntity e=create(boss,BLAST,0,10,ElementsRules.blastDamage(10,0),boss.getBoundingBox().getCenter(),Vec3.ZERO,frame);
        e.bossMovingBlast=true;return e;
    }
    static ElementsEntity bossStar(MahouTsukaiEntity boss,AttackFrame frame){
        ElementsEntity e=create(boss,STAR,0,0,ElementsRules.BASE_DAMAGE*.2f,boss.getBoundingBox().getCenter(),frame.forward().scale(.28),frame);
        e.bossBurstStar=true;e.left=e.life=48;e.publish();return e;
    }
    static ElementsEntity rainStar(MahouTsukaiEntity boss,LivingEntity target,Vec3 at){
        AttackFrame frame=GalaxiaEntity.aimFrame(target.getBoundingBox().getCenter().subtract(at).normalize(),new Vec3(1,0,0));
        ElementsEntity e=bossStar(boss,frame);e.setPos(at);e.bossRain=true;e.rainStarTarget=target.getUUID();
        e.samples.clear();e.samples.add(e.snapshot());e.publish();return e;
    }
    private LivingEntity starTarget(MahouTsukaiEntity boss){
        return rainStarTarget==null?boss.getTarget():WeaponUser.resolve(level(),rainStarTarget);
    }
    static ElementsEntity bossFanNeedle(MahouTsukaiEntity boss,AttackFrame frame){
        ElementsEntity e=create(boss,NEEDLE,1,0,ElementsRules.BASE_DAMAGE*.8f,boss.getBoundingBox().getCenter(),frame.forward().scale(.3),frame);
        // Slow fans need the longer deceleration window to cross the boss comfort distance.
        e.left=e.life=300;e.publish();return e;
    }
    static ElementsEntity bossGateNeedle(MahouTsukaiEntity boss,float damage,Vec3 at,AttackFrame frame){
        ElementsEntity e=create(boss,NEEDLE,1,0,damage,at,frame.forward().scale(10/16d),frame);
        // 30 native substeps reach only ~10 blocks at this speed; 600 reach ~96,
        // covering the 64-block target range plus the gates' rear/side/height offsets.
        e.left=e.life=600;e.publish();return e;
    }
    static ElementsEntity rainNeedle(MahouTsukaiEntity boss,Vec3 at){
        AttackFrame frame=new AttackFrame(new Vec3(0,-1,0),new Vec3(1,0,0));
        ElementsEntity e=create(boss,NEEDLE,1,0,ElementsRules.BASE_DAMAGE*.8f,at,frame.forward().scale(.45),frame);
        e.bossRain=true;e.left=e.life=180;e.publish();return e;
    }
    public int ownerId(){return entityData.get(VIEW).getInt("Owner");}
    public int kind(){return entityData.get(VIEW).getInt("Kind");}
    public ItemStack weapon(){return weapon;}
    public LivingEntity owner(){return WeaponUser.resolve(level(),ownerUuid);}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public EntityDimensions getDimensions(Pose pose){return (level().isClientSide?kind():kind)==STAR?EntityDimensions.fixed(20/16f,20/16f):EntityDimensions.fixed(.5f,.5f);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256*256;}
    @Override public void tick(){
        super.tick();if(level().isClientSide){clientSnapshot.tick(view(),"Age");return;}
        LivingEntity p=owner();if(!WeaponUser.active(p)||p instanceof MahouTsukaiEntity boss&&(boss.phase()!=ownerPhase||bossRain&&boss.phaseTransitionTicks()==0)){finish(false);return;}
        if((kind==BLADE||kind==PARRY)&&!p.getMainHandItem().is(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get())){finish(false);return;}
        samples=new ListTag();samples.add(snapshot());
        int updates=kind==BLADE?6:3;
        for(int step=0;step<updates&&!isRemoved();step++){
            if(age==0){
                if(kind==BLADE)sound(charge>0||mode==2?"phoenix":"item71",1,false,false);
                if(kind==PARRY||kind==BLAST)sound("item84",.3f,false,false);
            }
            switch(kind){case BLADE->blade(p);case NEEDLE->needle(p);case STAR->star(p);case PARRY->parry(p);case BLAST->blast(p);}
            age++;left--;samples.add(snapshot());
            if(!isRemoved()&&left<=0)finish(true);
        }
        if(!isRemoved())publish();
    }
    private Vec3 center(LivingEntity p){return p.getBoundingBox().getCenter();}
    private void sound(String name,float volume,boolean only,boolean relative){ElementsCombat.sound(this,ownerId,name,volume,only,relative);}
    private void blade(LivingEntity p){
        Vec3 c=center(p);
        if(mode<2){
            float t=age/35f,ratio=ArkCurves.swing(t);int sign=mode==0?1:-1;
            rotation=(float)(Math.PI*3/8*sign*(1-2*ratio)-(mode==1?Math.PI/4:0));
            scale=1.2f+(float)Math.sin(ratio*Math.PI)*.6f+charge/10f*.2f;
            setPos(c.add(direction.scale(30/16d)));
            Vec3 start=c.add(frame.direction(rotation).scale(30/16d));
            hitLine(p,start,start.add(frame.direction(rotation).scale(142/16d*scale)),24/16d,35,damage,20);
            return;
        }
        double t=(80-left)/80d;
        if(mode==2){
            // User-selected aim-following replaces only the original fixed throw direction/reach.
            Vec3 target=WeaponUser.aim(p);aim=aim.lerp(target,1-Math.exp(-1d/120/.10));
            Vec3 offset=aim.subtract(c);double reach=Math.max(200/16d,Math.min(ElementsRules.MAX_REACH,offset.length()));
            if(offset.lengthSqr()>1e-9)direction=offset.normalize();
            setPos(c.add(direction.scale(ElementsRules.throwRatio(t)*reach)));
            rotation-=.23561947f;scale=1+(float)Math.sin(t*Math.PI)*.5f;
            if(age==12&&!missed)sound("item4",1,true,true);
            if(!(p instanceof Player player && ElementsCombat.channeling(player))&&!missed){
                if(ElementsRules.snapWindow(age)){
                    mode=3;snapStart=rotation;left=20;hitAt.clear();event("snap",position());
                }else missed=true;
            }
        }
        if(mode==3){
            double snap=(20-left)/20d;
            double reach=Math.max(200/16d,Math.min(ElementsRules.MAX_REACH,aim.subtract(c).length()));
            setPos(c.add(direction.scale(reach*(1-.2*(1-Math.sqrt(1-snap*snap))))));scale=1.5f;
            double end=frame.angle(direction)+Math.PI/20;
            if(frame.angle(direction)+Math.PI/4>snapStart)end-=Math.PI*2;
            rotation=(float)(snapStart+(end-snapStart)*Math.sqrt(1-Math.pow(ElementsRules.clamp(snap+.2)-1,2)));
        }
        double size=142/16d*scale;
        Vec3 other=c.lerp(position(),smooth(ElementsRules.clamp((20-left)/20d+.25)));
        AABB broad=getBoundingBox().inflate(size+2).minmax(new AABB(c,position()).inflate(size));
        for(LivingEntity target:level().getEntitiesOfClass(LivingEntity.class,broad,e->WeaponUser.canHit(p,e))){
            boolean overlap=ArkGeometry.box(target.getBoundingBox(),position(),frame.forward(),frame.right(),frame.normal(),size/2,size/2,size/2);
            if(mode==3)overlap|=ArkGeometry.line(target.getBoundingBox(),other,other.add(direction.scale(size)),0,frame);
            if(overlap)hit(p,target,mode==3?20:10,damage*(mode==3?1.2f:1),20);
        }
    }
    private static double smooth(double t){return t*t*(3-2*t);}
    private void needle(LivingEntity p){
        velocity=velocity.scale(1-Math.pow(age/(double)life,3));
        Vec3 old=position(),next=old.add(velocity);
        var terrain=bossRain?level().clip(new ClipContext(old,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,this)):null;
        setPos(terrain!=null&&terrain.getType()!=HitResult.Type.MISS?terrain.getLocation():next);rotation=(float)frame.angle(velocity);
        if(terrain!=null&&terrain.getType()!=HitResult.Type.MISS){finish(true);return;}
        if(velocity.length()<1/16d){finish(true);return;}
        Vec3 v=velocity.normalize(),a=position().subtract(v.scale(16/16d));
        hitLine(p,a,a.add(v.scale(44/16d*scale)),24/16d,30,damage,20);
    }
    private void star(LivingEntity p){
        if(bossBurstStar&&p instanceof MahouTsukaiEntity boss){
            LivingEntity target=starTarget(boss);
            if(!MahouPhaseOneAI.valid(boss,target)){finish(false);return;}
            Vec3 aim=target.getBoundingBox().getCenter().subtract(position()).normalize();
            velocity=velocity.scale(.96).add(aim.scale(.28*.04));
            if(age>=life-1){finish(true);return;}
        }
        if(age>30&&!bossBurstStar)velocity=velocity.scale(.95);
        if(soundDelay--<=0){soundDelay=20+random.nextInt(40);if(random.nextInt(5)==0)ElementsCombat.sound(this,ownerId,"elements_item9",1,false,false,position().subtract(frame.forward().add(frame.right()).scale(10/16d)));}
        if(velocity.length()<2/16d&&left<60){finish(true);return;}
        rotation+=(Math.abs(velocity.dot(frame.forward()))+Math.abs(velocity.dot(frame.right())))*16*.01;
        LivingEntity target=WeaponUser.nearest(p,position(),350/16d);
        if(!(p instanceof MahouTsukaiEntity)&&target!=null&&(age<=5||level().clip(new ClipContext(position(),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS))
            velocity=velocity.scale(19/20d).add(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(12/16d/20));
        Vec3 old=position(),next=old.add(velocity);
        if(age>5&&!bossBurstStar){
            // Sweep the projectile volume, not just its center, to retain the original tile hitbox.
            Vec3 move=Entity.collideBoundingBox(this,velocity,getBoundingBox(),level(),List.of());
            if(move.distanceToSqr(velocity)>1e-12){setPos(old.add(move));finish(true);return;}
        }
        setPos(next);hitLine(p,old,next,20/16d,120,damage,20);
    }
    private void parry(LivingEntity p){
        float thrust=ArkCurves.parryThrust(age/15f);scale=1.4f+thrust*.2f;
        setPos(center(p).add(direction.scale((10+10*thrust)/16d)));
        if(!(p instanceof Player player && ElementsCombat.parried(player)))hitLine(p,position(),position().add(direction.scale(142/16d*scale)),44/16d,340,damage,30);
    }
    private void blast(LivingEntity p){
        if(bossMovingBlast)setPos(center(p));
        scale=1.4f;int count=ElementsRules.stitches(age);
        while(stitches<count){ElementsCombat.sound(p,ownerId,"item"+(63+stitches%3),.5f,false,false,p.getBoundingBox().getCenter());stitches++;}
        if(age==26)event("blast",position());
        if(age>25)hitLine(p,position(),position().add(direction.scale(ElementsRules.thrust(age)*242/16d)),30/16d,72,damage,0);
    }
    private void hitLine(LivingEntity p,Vec3 a,Vec3 b,double width,int cooldown,float amount,int dot){
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(width/2+2),e->WeaponUser.canHit(p,e)))
            if(ArkGeometry.line(t.getBoundingBox(),a,b,width,frame)){
                hit(p,t,cooldown,amount,dot);if(bossBurstStar)finish(true);if(isRemoved())return;
                if(kind==PARRY&&(p instanceof Player player && ElementsCombat.parried(player)))return;
            }
    }
    private void hit(LivingEntity p,LivingEntity t,int cooldown,float amount,int dot){
        if(isRemoved())return;
        Integer previous=hitAt.get(t.getId());if(previous!=null&&age-previous<cooldown)return;
        if(kind==BLAST)amount*=Math.pow(.25,hits*.1);
        amount+=EnchantmentHelper.getDamageBonus(weapon,t.getMobType());
        var source=WeaponUser.source(this,p);
        if(!WeaponDamage.hurt(t,source,amount))return;
        p.setLastHurtMob(t);
        hitAt.put(t.getId(),age);hits++;
        int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);if(fire>0)t.setSecondsOnFire(fire*4);
        double knockback=(8.5+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK)*2)/16d;
        t.knockback(knockback,-direction.x,-direction.z);
        EnchantmentHelper.doPostHurtEffects(t,p);weapon.getAllEnchantments().forEach((enchantment,level)->enchantment.doPostAttack(p,t,level));
        if(dot>0)ElementsCombat.applyResonance(t,p,dot);
        event("hit",t.getBoundingBox().getCenter());
        if(kind==PARRY && p instanceof Player player)ElementsCombat.parrySuccess(player);
        if(kind==BLADE&&mode==3)sound("snap",1,false,false);
        if(kind==NEEDLE||kind==STAR)finish(true);
    }
    private void finish(boolean natural){
        if(ended)return;ended=true;
        if(natural){
            if(bossBurstStar&&owner() instanceof MahouTsukaiEntity boss&&MahouPhaseOneAI.valid(boss,starTarget(boss))){
                Vec3 aim=starTarget(boss).getBoundingBox().getCenter().subtract(position());
                AttackFrame burst=GalaxiaEntity.aimFrame(aim.lengthSqr()<1e-8?frame.forward():aim.normalize(),frame.right());
                int count=rainStarTarget==null?8:boss.phase()==3?5:3;
                for(int i=0;i<count;i++)GalaxiaEntity.bossBolt(boss,position(),burst.direction(i*Math.PI*2/count),.3,1);
                ArkOfCosmos.sound(this,"galaxia_lightning",1);
            }
            if(kind==BLADE&&mode==3)sound("item84",1,false,false);
            if(kind==NEEDLE||kind==STAR)sound("needle_death",kind==STAR?.5f:1,false,false);
            event("end",position());
        }
        super.discard();
    }
    private void event(String name,Vec3 at){CompoundTag data=snapshot();data.putString("Event","elements_"+name);data.putInt("Id",getId());vector(data,"At",at);LivingEntity p=owner();ArkNetwork.effect(p==null?this:p,data);}
    private CompoundTag snapshot(){
        CompoundTag s=new CompoundTag();s.putInt("Owner",ownerId);s.putInt("Kind",kind);s.putInt("Mode",mode);s.putInt("Charge",charge);s.putInt("Age",age);s.putInt("Left",left);s.putInt("Life",life);s.putInt("Stitches",stitches);s.putBoolean("Missed",missed);s.putFloat("Rotation",rotation);s.putFloat("Scale",scale);
        vector(s,"Position",position());vector(s,"Velocity",velocity);vector(s,"Direction",direction);vector(s,"F",frame.forward());vector(s,"R",frame.right());
        LivingEntity p=owner();vector(s,"Center",p==null?position():center(p));
        ListTag angles=new ListTag();for(float angle:stitchAngles)angles.add(FloatTag.valueOf(angle));s.put("Angles",angles);return s;
    }
    private void publish(){CompoundTag s=snapshot();s.put("Samples",samples.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (level().isClientSide && key.equals(VIEW) && !view().isEmpty())
            clientSnapshot.initialize(view());
        super.onSyncedDataUpdated(key);
    }
    public CompoundTag effectSnapshot() {
        return level().isClientSide ? clientSnapshot.state() : view();
    }
    public CompoundTag renderState(float partial){
        ListTag list=effectSnapshot().getList("Samples",10);if(list.isEmpty())return effectSnapshot();
        double index=ElementsRules.clamp(partial)*(list.size()-1);int i=(int)index;CompoundTag a=list.getCompound(i),b=list.getCompound(Math.min(i+1,list.size()-1)),s=a.copy();double t=index-i;
        for(String k:new String[]{"Position","Velocity","Direction","Center"})vector(s,k,vector(a,k).lerp(vector(b,k),t));
        for(String k:new String[]{"Age","Left","Rotation","Scale"})s.putDouble(k,a.getDouble(k)+(b.getDouble(k)-a.getDouble(k))*t);
        return s;
    }
    public static Vec3 vector(CompoundTag s,String key){return new Vec3(s.getDouble(key+"X"),s.getDouble(key+"Y"),s.getDouble(key+"Z"));}
    public static void vector(CompoundTag s,String key,Vec3 v){s.putDouble(key+"X",v.x);s.putDouble(key+"Y",v.y);s.putDouble(key+"Z",v.z);}
}
