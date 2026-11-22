package net.everla.projectc.arkofcosmos.entity;

import java.util.*;
import net.everla.projectc.arkofcosmos.calamity.*;
import net.everla.projectc.arkofcosmos.combat.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import static net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter.Attack.*;

/** Server-side Infernum choreography. Every position/velocity below is world space;
 * source pixel vectors cross the boundary through pixels() exactly once. */
final class MahouInfernumAI {
    final MahouTsukaiEntity boss;
    private final MahouPatrol patrol;
    MahouInfernumEncounter.Attack attack=SummonSepulcher;
    int stage, timer, cycle, entrance=270, count, state, finalState, countdown, wallIndex;
    boolean berserk, desperation;
    Vec3 plane=new Vec3(1,0,0), velocity=Vec3.ZERO, aim=new Vec3(1,0,0), origin=Vec3.ZERO;
    double angle, direction=1, offset, counter;
    SupremeCalamitasArena arena;
    private boolean initialized;
    private boolean retireLegacyMembers;
    boolean shortRitual;
    private UUID trackingWarning;
    private int unseen;
    private double sourcePixels=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.calamitasValue("pixels_per_block");
    MahouInfernumAI(MahouTsukaiEntity boss){this.boss=boss;this.patrol=new MahouPatrol(boss);}
    double pixels(){return sourcePixels;}
    Vec3 center(){return boss.getBoundingBox().getCenter();}
    Vec3 target(){return boss.getTarget().getBoundingBox().getCenter();}
    Vec3 unit(Vec3 v){return v.lengthSqr()<1e-12?plane:v.normalize();}
    Vec3 xy(double x,double y){return plane.scale(x/pixels()).add(0,-y/pixels(),0);}
    Vec3 planeDirection(double angle){return plane.scale(Math.cos(angle)).add(0,-Math.sin(angle),0);}
    double planeAngle(Vec3 direction){return Math.atan2(-direction.y,direction.dot(plane));}
    Vec3 planarAim(Vec3 direction){return unit(plane.scale(direction.dot(plane)).add(0,direction.y,0));}
    Vec3 planar(Vec3 vector){return plane.scale(vector.dot(plane)).add(0,vector.y,0);}
    Vec3 rotate(Vec3 v,double a){
        Vec3 axis=plane.cross(new Vec3(0,-1,0)).normalize();
        return v.scale(Math.cos(a)).add(axis.cross(v).scale(Math.sin(a))).add(axis.scale(axis.dot(v)*(1-Math.cos(a))));
    }
    Vec3 turnTowards(Vec3 v,Vec3 desired,double maxAngle){
        if(v.lengthSqr()<1e-12)return v;
        Vec3 from=v.normalize(),to=unit(desired);double a=Math.acos(Math.max(-1,Math.min(1,from.dot(to))));
        if(a<=maxAngle)return to.scale(v.length());
        Vec3 tangent=to.subtract(from.scale(from.dot(to)));
        if(tangent.lengthSqr()<1e-12){tangent=from.cross(plane);if(tangent.lengthSqr()<1e-12)tangent=from.cross(new Vec3(0,1,0));}
        return from.scale(Math.cos(maxAngle)).add(tangent.normalize().scale(Math.sin(maxAngle))).scale(v.length());
    }
    boolean rage(){return boss.mistilteinnEnraged();}
    boolean fast(){return berserk;}
    boolean tailAttack(){return stage==4&&attack==DesperationPhase;}
    boolean invulnerable(){return initialized&&(entrance>0||attack==PhaseTransition||attack==SummonSepulcher||attack==SummonBrothers||attack==SummonShadowDemon||attack==SummonSeekers||tailAttack());}
    AABB arenaBounds(){
        if(boss.isRemoved()||boss.defeating())return null;
        if(boss.level().isClientSide){var view=boss.infernumView();return view.contains("Arena")?SupremeCalamitasArena.load(view.getCompound("Arena")).bounds():null;}
        return arena==null?null:arena.bounds();
    }
    boolean interceptDeath(){
        if(stage<4){desperation=true;return true;}
        return !boss.defeating();
    }
    private void acquire(){
        var fourth=boss.level().getEntitiesOfClass(FourthCalamityEntity.class,boss.getBoundingBox().inflate(128),e->!e.defeated()&&MahouTargeting.valid(boss,e))
            .stream().min(Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if(fourth!=null){boss.setTarget(fourth);unseen=0;return;}
        var arenaPlayer=((ServerLevel)boss.level()).players().stream()
            .filter(p->MahouTargeting.valid(boss,p)&&MahouTargeting.insideArena(boss,p))
            .min(Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if(arenaPlayer!=null){boss.setTarget(arenaPlayer);unseen=0;return;}
        var player=boss.level().getNearestPlayer(boss.getX(),boss.getY(),boss.getZ(),128,p->p instanceof Player v&&MahouTargeting.valid(boss,v)&&boss.hasLineOfSight(v));
        LivingEntity old=boss.getTarget();
        if(player!=null){boss.setTarget(player);unseen=0;}
        else if(!MahouTargeting.valid(boss,old)||old instanceof Player&&++unseen>100)boss.setTarget(null);
        if(boss.getTarget()==null&&MahouTargeting.valid(boss,boss.getLastHurtByMob()))boss.setTarget(boss.getLastHurtByMob());
    }
    private float retreatOpacity=1;
    void tick(){
        acquire();boss.setNoGravity(true);
        if(boss.getTarget()==null){
            if(!initialized){patrol.tick();return;}
            clearShots();boss.spell(0);boss.setDeltaMovement(Vec3.ZERO);
            for(int i=0;i<3&&!boss.isRemoved();i++){
                retreatOpacity*=.935f;
                boss.forceCombatPosition(boss.position().add(0,4*(1-Math.sin(Math.PI*retreatOpacity))/pixels(),0));
                if(retreatOpacity<=25/255f){boss.retreatInfernum();return;}
            }
            var view=boss.infernumView().copy();view.putFloat("RetreatOpacity",retreatOpacity);boss.publishInfernum(view);return;
        }
        boss.setDeltaMovement(Vec3.ZERO);
        if(retireLegacyMembers){
            clearShots();for(var kind:MahouInfernumSummonEntity.Kind.values())clearKind(kind);
            retireLegacyMembers=false;
        }
        if(!initialized){
            sourcePixels=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.calamitasValue("pixels_per_block");
            shortRitual=CalamitasBattleData.infernumRitualPlayed((ServerLevel)boss.level());
            initialized=true;arena=new SupremeCalamitasArena(center(),pixels());
            CalamitasBattleData.get((ServerLevel)boss.level()).begin(boss.getUUID());lockPlane();
        }
        for(int step=0;step<3&&!boss.isRemoved()&&!boss.defeating();step++){
            volleySpoken=false;
            if(entrance>0){entrance--;continue;}
            int next=MahouInfernumEncounter.nextStage(stage,boss.getHealth()/boss.getMaxHealth());
            if(next!=stage){stage=next;cycle=0;start(PhaseTransition);}
            if(desperation&&stage<4){stage=4;cycle=0;start(PhaseTransition);}
            followPlayerPlane();
            timer++;
            boss.spell(attack==SummonSepulcher||attack==SummonBrothers||attack==SummonSeekers||attack==SummonShadowDemon||attack==PhaseTransition?4:attack==ExplosiveCharges?0:1);
            switch(attack){
                case HorizontalDarkSoulRelease->souls();
                case CondemnationFanBurst->fan();
                case ExplosiveCharges->charges();
                case HellblastBarrage->hellblast();
                case SummonSuicideBomberDemons->bombers();
                case BrimstoneJewelBeam->jewel();
                case DarkMagicBombWalls->walls();
                case FireLaserSpin->spin();
                case BecomeBerserk->{velocity=velocity.scale(.95);if(timer>=95){boss.forceCombatPosition(target().add(0,450/pixels(),0));berserk=true;next();}}
                case PhaseTransition->transition();
                case SummonSepulcher->sepulcher();
                case SummonShadowDemon->{velocity=velocity.scale(.95);if(timer==170)summon(MahouInfernumSummonEntity.Kind.SHADOW,center().add(0,850/pixels(),0),0);if(timer>=170)next();}
                case SummonBrothers->brothers();
                case SummonSeekers->seekers();
                case ShadowDemon_ReleaseExplodingShadowBlasts,ShadowDemon_ShadowGigablastsAndCharges->{if(living(MahouInfernumSummonEntity.Kind.SHADOW)==0)summon(MahouInfernumSummonEntity.Kind.SHADOW,center().add(0,10,0),0);velocity=velocity.scale(.95);}
                case DesperationPhase->finale();
            }
            Vec3 previousPosition=boss.position();
            if(attack==DesperationPhase&&finalState==4){
                double y=net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.Y,boss.getBoundingBox(),boss.level().getBlockCollisions(boss,boss.getBoundingBox().expandTowards(velocity)),velocity.y);
                boss.forceCombatPosition(previousPosition.add(0,y,0));if(y!=velocity.y)velocity=Vec3.ZERO;
            }else boss.forceCombatPosition(previousPosition.add(velocity));
            if(attack==ExplosiveCharges){
                var volume=boss.getBoundingBox().minmax(boss.getBoundingBox().move(velocity.scale(-1)));
                for(var victim:boss.level().getEntitiesOfClass(LivingEntity.class,volume,v->v!=boss&&v.isAlive()&&!boss.isAlliedTo(v)))
                    WeaponDamage.hurt(victim,new net.minecraft.world.damagesource.DamageSource(boss.damageSources().mobAttack(boss).typeHolder(),boss,boss),16);
            }
            // NPCs update before projectiles; head/leader precede their dependants.
            var server=(ServerLevel)boss.level();var journal=CalamitasBattleData.get(server);
            var summons=new ArrayList<MahouInfernumSummonEntity>();
            for(UUID id:journal.members(boss.getUUID(),false))if(server.getEntity(id) instanceof MahouInfernumSummonEntity member)summons.add(member);
            summons.sort(Comparator.comparingInt(MahouInfernumSummonEntity::sourceOrder).thenComparingInt(Entity::getId));
            for(var member:summons)if(!member.isRemoved())member.sourceStep(boss);
            if(boss.getTarget()==null)return;
            var shots=new ArrayList<MahouInfernumAttackEntity>();
            for(UUID id:journal.members(boss.getUUID(),true))if(server.getEntity(id) instanceof MahouInfernumAttackEntity shot)shots.add(shot);
            shots.sort(Comparator.comparingInt(Entity::getId));
            for(var shot:shots)if(!shot.isRemoved())shot.sourceStep(boss);
            if(boss.getTarget()==null)return;
            arena.stepAppearance(boss.getHealth()/boss.getMaxHealth(),boss.defeating());
            arena.step(false,false,false,false,false,()->boss.getTarget().getBoundingBox(),(before,after)->CalamitasArenaBoundary.shrink(boss,before,after));
        }
        Vec3 d=target().subtract(center());boss.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));boss.yBodyRot=boss.getYRot();
        var view=new CompoundTag();view.putFloat("RetreatOpacity",retreatOpacity);view.putInt("Stage",stage);view.putString("Attack",attack.name());view.putInt("Frame",timer);view.putBoolean("Rage",rage());view.put("Arena",arena.save());view.putDouble("Pixels",pixels());view.put("ArenaPlayers",CalamitasArenaBoundary.participants((ServerLevel)boss.level(),arena.bounds()));
        boss.presentationPhase(Math.min(3,stage+1));
        MahouInfernumAttackEntity.vector(view,"Velocity",velocity);MahouInfernumAttackEntity.vector(view,"Plane",plane);boss.publishInfernum(view);
        CalamitasArenaBoundary.syncCarried((ServerLevel)boss.level());
    }
    void lockPlane(){Vec3 d=target().subtract(center()).multiply(1,0,1);if(d.lengthSqr()>1e-9)plane=d.normalize();}
    static Vec3 turnPlane(Vec3 vector,Vec3 from,Vec3 to){
        double dot=from.dot(to),cross=from.x*to.z-from.z*to.x;
        return new Vec3(vector.x*dot-vector.z*cross,vector.y,vector.x*cross+vector.z*dot);
    }
    private void followPlayerPlane(){
        Vec3 before=plane;lockPlane();
        aim=turnPlane(aim,before,plane);
        if(attack==DarkMagicBombWalls)origin=center().add(turnPlane(origin.subtract(center()),before,plane));
    }
    void start(MahouInfernumEncounter.Attack value){attack=value;trackingWarning=null;timer=count=state=0;counter=offset=angle=0;direction=boss.getRandom().nextBoolean()?1:-1;lockPlane();aim=unit(target().subtract(center()));

    }
    void next(){
        clearKind(MahouInfernumSummonEntity.Kind.BOMBER);
        if(attack==BecomeBerserk){var choices=new MahouInfernumEncounter.Attack[]{CondemnationFanBurst,ExplosiveCharges,HellblastBarrage};start(choices[boss.getRandom().nextInt(3)]);}
        else{berserk=false;start(MahouInfernumEncounter.cycle(stage,cycle++));}
    }
    void moveTo(Vec3 destination,double speed,double acceleration){
        Vec3 desired=unit(destination.subtract(center())).scale(speed/pixels());
        velocity=SupremeCalamitasCombat.fly(velocity,desired,acceleration/pixels());
    }
    Vec3 side(double x,double y){double facing=target().subtract(center()).dot(plane)>=0?1:-1;return target().subtract(plane.scale(facing*x/pixels())).add(0,y/pixels(),0);}
    private boolean volleySpoken;
    MahouInfernumAttackEntity warning(Vec3 at,Vec3 direction,int time){return shot(MahouInfernumAttackEntity.Kind.WARNING,at,unit(direction),time,0,0);}
    private void trackingLine(Vec3 direction,int duration){
        if(duration>0){trackingWarning=warning(center(),direction,duration).getUUID();}
        else if(trackingWarning!=null&&((ServerLevel)boss.level()).getEntity(trackingWarning) instanceof MahouInfernumAttackEntity warning)
            warning.point(center(),direction,plane);
    }
    MahouInfernumAttackEntity shot(MahouInfernumAttackEntity.Kind kind,Vec3 at,Vec3 speed,int life,float damage,double radius){var shot=MahouInfernumAttackEntity.spawn(boss,kind,at,speed,plane,life,damage,radius,tailAttack());
        boolean attackShot=switch(kind){case WARNING,BOMB_WARNING,PILLAR_WARNING,RITUAL,HEART_STAR,EXPLOSION,ORB->false;default->true;};
        if(!volleySpoken&&attackShot&&!shot.isRemoved()){volleySpoken=true;boss.rageVolleyDialogue();}
        return shot;}
    void dart(Vec3 at,Vec3 direction,double speed){shot(MahouInfernumAttackEntity.Kind.DART,at,unit(direction).scale(speed/pixels()),690,14,0);}
    void burst(Vec3 at,Vec3 direction,int n,double spread,double speed){direction=planarAim(direction);for(int i=0;i<n;i++)planarDart(at,rotate(unit(direction),-spread+2*spread*i/Math.max(1,n-1)),speed);}
    void planarDart(Vec3 at,Vec3 direction,double speed){shot(MahouInfernumAttackEntity.Kind.DART,at,unit(direction).scale(speed/pixels()),690,14,0);}
    void bomb(Vec3 at,Vec3 speed,int life,double radius){shot(MahouInfernumAttackEntity.Kind.BOMB,at,speed,life,0,radius/pixels());}
    MahouInfernumAttackEntity giga(Vec3 at,Vec3 direction,double speed){return shot(MahouInfernumAttackEntity.Kind.GIGA,at,unit(direction).scale(speed/pixels()),90,15,0);}
    void summon(MahouInfernumSummonEntity.Kind kind,Vec3 at,int index){MahouInfernumSummonEntity.spawn(boss,kind,at,index);}
    int living(MahouInfernumSummonEntity.Kind kind){return CalamitasBattleData.get((ServerLevel)boss.level()).living(boss.getUUID(),"mahou_"+kind.name());}
    void clearKind(MahouInfernumSummonEntity.Kind kind){
        var data=CalamitasBattleData.get((ServerLevel)boss.level());
        for(UUID id:data.members(boss.getUUID(),false))if(((ServerLevel)boss.level()).getEntity(id) instanceof MahouInfernumSummonEntity m&&m.kind()==kind)m.retire();
        data.retireRole(boss.getUUID(),"mahou_"+kind.name());
    }
    void clearShots(){
        var data=CalamitasBattleData.get((ServerLevel)boss.level());
        for(UUID id:data.members(boss.getUUID(),true)){
            if(((ServerLevel)boss.level()).getEntity(id) instanceof MahouInfernumAttackEntity a)a.retire();else data.died(boss.getUUID(),id);
        }
    }
    void clear(){
        if(!(boss.level() instanceof ServerLevel server))return;
        clearShots();for(var kind:MahouInfernumSummonEntity.Kind.values())clearKind(kind);
        CalamitasBattleData.get(server).end(boss.getUUID());
    }
    private void souls(){
        moveTo(side(820,0),32,.9);int rate=rage()?3:fast()?4:stage>=1?5:7;
        if(timer>=60&&(timer-60)%rate==rate-1){int n=(timer-60)/rate;double a=-.67+1.34*(n%3)/2+(boss.getRandom().nextDouble()*2-1)*.25;
            Vec3 v=xy(Math.cos(a)*(rage()?22:8.5)*.67*(target().subtract(center()).dot(plane)>=0?1:-1),Math.sin(a)*(rage()?22:8.5)).add(0,boss.getTarget().getDeltaMovement().y/3,0);
            shot(MahouInfernumAttackEntity.Kind.DARK_SOUL,center(),v,360,15,0);}
        if(timer>=240){timer=0;if(++count>=2)next();}
    }
    private void fan(){
        int charge=stage>=1?155:120,spin=stage>=1?84:48;if(fast())spin-=12;
        double speed=fast()?15:stage>=1?14.75:11.25,spread=fast()?3.03:stage>=1?2.72:2.94;if(stage>=3)speed+=4.75;if(rage())speed+=17.5;
        double hoverFactor=Math.max(0,Math.min(1,1-timer/(charge*.75)))*.65+.35;
        moveTo(arena.origin(),32*hoverFactor,1.2*hoverFactor);
        if(timer<=charge)aim=planarAim(target().add(boss.getTarget().getDeltaMovement().scale(10/3d)).subtract(center()));
        int first=rage()||charge%2==0?1:2;
        if(timer>=charge-18&&timer<charge+first)trackingLine(fanDirection(spread,first),timer==charge-18?18+first:0);
        if((stage>=1||fast())&&timer==charge)for(int i=0;i<6;i++){Vec3 off=xy(Math.cos(i*Math.PI/3)*650,Math.sin(i*Math.PI/3)*650).subtract(planar(boss.getTarget().getDeltaMovement()).scale(5));Vec3 at=target().add(off);planarDart(at,off.scale(-1),off.length()*pixels()/52*.4);warning(at,off.scale(-1),52);}
        if(timer>charge&&timer%(rage()?1:2)==(rage()?0:1))shot(MahouInfernumAttackEntity.Kind.ARROW,center(),fanDirection(spread,timer-charge).scale(speed/pixels()),80,14,0);
        if(timer>=charge+52){if(++count>=2)next();else timer=spin;}
    }
    private Vec3 fanDirection(double spread,int step){return rotate(aim,spread*direction*(.5-step/52d));}
    private void charges(){
        int duration=stage>=1?41:45,n=fast()?5:6,delay=rage()?40:120-(stage>=3?18:0)-(fast()?45:0);
        double speed=43+(stage>=1?4.5:0)+(stage>=3?3.5:0)+(fast()?9.5:0)+(rage()?20:0);
        if(timer<132){Vec3 destination=side(500,270).subtract(velocity);Vec3 delta=destination.subtract(center());boss.forceCombatPosition(boss.position().add(unit(delta).scale(Math.min(delta.length(),10/pixels()))));moveTo(destination,28,1.1);}
        else if((timer-132)%duration==0){aim=unit(target().subtract(center()));velocity=aim.scale(speed/pixels());
            shot(MahouInfernumAttackEntity.Kind.BOMB,center(),aim.scale((stage>=1?15:20)/pixels()),132+duration*n-timer+delay,15,(1020+(rage()?1250:0))/pixels());
            if(count++%3==2)giga(center(),rotate(aim,(boss.getRandom().nextDouble()*2-1)*.4),(stage>=1?15:20)*.5);
        }else velocity=velocity.scale(.9835);
        int untilCharge=timer<132?132-timer:duration-(timer-132)%duration;
        if(untilCharge<=32&&timer+untilCharge<=132+duration*n)
            trackingLine(unit(target().subtract(center())),untilCharge==32?32:0);
        if(timer>=132+duration*n)next();
    }
    private void hellblast(){
        int rate=14-(stage>=1?2:0)-(fast()?3:0);double amp=330+(stage>=1?20:0)+(stage>=3?50:0)+(fast()?50:0);
        double hoverFactor=.36+.64*Math.max(0,Math.min(1,timer/(105*.65)));
        Vec3 destination=side(700+(living(MahouInfernumSummonEntity.Kind.SEEKER)>0?120:0),timer<105?0:-Math.sin((timer-105)*Math.PI/56)*amp);
        Vec3 desired=unit(destination.subtract(center())).scale(hoverFactor*Math.min(destination.distanceTo(center()),32/pixels()));
        velocity=SupremeCalamitasCombat.fly(velocity,desired,hoverFactor*2.25/pixels()).lerp(desired,.1);
        if(timer>=105&&timer<465&&timer%rate==rate-1){Vec3 d=unit(target().subtract(center()));Vec3 v=unit(new Vec3(d.x,d.y*.1,d.z));shot(MahouInfernumAttackEntity.Kind.HELLBLAST,center(),v.scale(15/pixels()),255,14,0);
            if(count++%5==4)burst(center(),d,rage()?37:7,rage()?1.13:.45,8.4+(stage>=1?1.36:0)+(stage>=3?2.5:0)+(fast()?2.6:0)+(rage()?13.5:0));}
        if(timer>=555)next();
    }
    private void bombers(){
        if(living(MahouInfernumSummonEntity.Kind.SEEKER)>0){next();return;}
        int n=stage>=1?8:6,cast=6*n+84+45,rate=rage()?9:stage>=1?27:32;
        if(timer<cast){velocity=velocity.scale(.925);if(count<n&&timer%6==5){Vec3 at=center().add(xy(Math.cos(count*Math.PI*2/n)*225,Math.sin(count*Math.PI*2/n)*225));shot(MahouInfernumAttackEntity.Kind.RITUAL,at,Vec3.ZERO,84,0,0);count++;}return;}
        if(offset==0)offset=center().subtract(target()).dot(plane)>=0?1:-1;
        Vec3 hover=target().add(plane.scale(offset*600/pixels())).add(0,300/pixels(),0);
        if(center().distanceTo(hover)>100/pixels())moveTo(hover,32,1.5);
        if(timer%rate==rate-1&&timer<cast+420&&center().distanceTo(target())>320/pixels()){
            burst(center(),target().subtract(center()),rage()?17:stage>=1?7:5,.45,rage()?30:7.5);if(++state%6==5){offset*=-1;velocity=velocity.scale(.3);boss.forceCombatPosition(target().add(plane.scale(offset*600/pixels())).add(0,300/pixels(),0));}}
        if(timer>=cast+510)next();
    }
    private void jewel(){
        if(timer==1){clearShots();boss.forceCombatPosition(side(600,0));velocity=Vec3.ZERO;angle=planeAngle(target().subtract(center()));}
        if(center().distanceTo(arena.origin())>400/pixels()){
            Vec3 next=center().lerp(arena.origin(),.025);Vec3 delta=arena.origin().subtract(next);
            if(delta.length()>6/pixels())next=next.add(delta.normalize().scale(6/pixels()));else next=arena.origin();
            boss.forceCombatPosition(boss.position().add(next.subtract(center())));
        }
        if(timer<142){Vec3 d=target().subtract(center());double desired=planeAngle(d);double turn=Math.PI/45+(Math.PI/355-Math.PI/45)*Math.min(1,timer/(142*.67));double delta=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(desired-angle))*Math.PI/180;angle+=Math.max(-turn,Math.min(turn,delta));if(timer>=100)trackingLine(planeDirection(angle),timer==100?42:0);}
        if(timer==142){Vec3 delta=target().subtract(center());direction=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(planeAngle(delta)-angle))>0?1:-1;origin=center();shot(MahouInfernumAttackEntity.Kind.JEWEL_BEAM,origin,planeDirection(angle),360,18.333334f,(Math.PI*2*1.25*(stage>=2?1.3:1)/360)*direction);}
        if(timer>142){int rate=rage()?3:stage>=2?6:8;if(timer%rate==rate-1)dart(center(),target().subtract(center()),rage()?38:16);
            int bombs=stage>=2?50:60;if(timer%bombs==bombs-1)bomb(center(),unit(target().add(boss.getTarget().getDeltaMovement().scale(35/3d)).subtract(center())).scale((rage()?38:16)*.7/pixels()),120,1100+(rage()?3000:0));
            if(timer%85==84)shot(MahouInfernumAttackEntity.Kind.RITUAL,target().subtract(boss.getTarget().getDeltaMovement().scale(5/3d)),Vec3.ZERO,84,0,0);}
        if(timer>=502){clearShots();next();}
    }
    private void walls(){
        int rate=rage()?5:stage>=3?16:20,cinders=rage()?15:stage>=3?5:3;if(center().distanceTo(side(600,350))>150/pixels())moveTo(side(600,350),32,1.5);
        if(timer>=135&&timer<465&&timer%rate==rate-1){double a=boss.getRandom().nextDouble()*Math.PI*2;for(int i=0;i<cinders;i++){Vec3 off=xy(Math.cos(a+i*Math.PI*2/cinders)*1000,Math.sin(a+i*Math.PI*2/cinders)*1000);shot(MahouInfernumAttackEntity.Kind.FLAME,target().add(off),off.normalize().scale(-2.7/pixels()),300,14,0);}}
        int wrapped=(timer-135)%(rate*3+107);
        if(timer>=135&&wrapped==rate*3){
            Vec3 movement=planarAim(boss.getTarget().getDeltaMovement());
            int attempts=0;
            do{angle=boss.getRandom().nextDouble()*Math.PI*2;}while(++attempts<16&&planeDirection(angle).dot(movement)>Math.cos(.91));
            origin=target().add(planeDirection(angle).scale(1150/pixels()));
        }
        if(timer>=135&&wrapped>=rate*3&&wrapped<=rate*3+91&&wrapped%7==6){double y=(-.5+(wrapped-rate*3)/91d)*(stage>=3?2100:1800)/pixels();Vec3 at=origin.add(planeDirection(angle+Math.PI/2).scale(y));shot(MahouInfernumAttackEntity.Kind.BOMB_WARNING,at,planeDirection(angle).scale(-1),45,0,1080/pixels());}
        if(timer>=555)next();
    }
    private void spin(){
        if(timer<120&&center().distanceTo(arena.origin())>100/pixels())velocity=velocity.lerp(unit(arena.origin().subtract(center())).scale(15/pixels()),.1);else velocity=velocity.scale(.92);
        if(timer==165){origin=center().add(0,-8/pixels(),0);shot(MahouInfernumAttackEntity.Kind.ORB,origin,Vec3.ZERO,575,0,525/pixels());}
        if(timer==185)for(int i=0;i<5;i++)warning(origin,overloadDirection(i,MahouInfernumAttackEntity.OVERLOAD_INITIAL_TURN),105).parameter(MahouInfernumAttackEntity.OVERLOAD_WARNING);
        if(timer==200)velocity=new Vec3(0,35/pixels(),0);
        if(timer==290)for(int i=0;i<5;i++)shot(MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM,origin,overloadDirection(i,0),450,18.333334f,0);
        int rate=rage()?18:stage>=3?57:75;if(timer>=235&&timer%rate==rate-1)giga(center(),target().subtract(center()),10.5+(stage>=3?1.25:0)+(rage()?25:0));
        if(timer>=800){clearShots();next();}
    }
    private Vec3 overloadDirection(int index,double turn){return planeDirection(index*Math.PI*2/5+.8+turn);}
    private void transition(){
        if(timer==1){clearShots();clearKind(MahouInfernumSummonEntity.Kind.BOMBER);boss.forceCombatPosition(target().add(0,450/pixels(),0));velocity=Vec3.ZERO;}
        if(timer>=105){next();start(stage==1?SummonShadowDemon:stage==2?SummonBrothers:stage==3?SummonSeekers:DesperationPhase);if(stage==4)clearKind(MahouInfernumSummonEntity.Kind.SHADOW);}
    }
    private void sepulcher(){
        velocity=velocity.scale(.95);
        // Thirteen visual-only stars retain the source ritual; no killable heart gate.
        int delay=shortRitual?30:60,spin=shortRitual?60:150,focus=shortRitual?38:92,t=timer-delay;
        if(t==1)for(int i=0;i<13;i++){var a=shot(MahouInfernumAttackEntity.Kind.HEART_STAR,center(),Vec3.ZERO,spin+focus+140,0,i/12d);a.parameter(i);}
        if(t>=0&&t<=spin)counter+=(.001+(Math.PI/27-.001)*t/spin)*Math.max(0,Math.min(1,(spin-t)/36d));
        if(t==spin+focus){summon(MahouInfernumSummonEntity.Kind.WORM,center().add(0,2100/pixels(),0),0);}
        if(t>=spin+focus+140&&living(MahouInfernumSummonEntity.Kind.WORM)==0){CalamitasBattleData.markInfernumRitualPlayed((ServerLevel)boss.level());clearShots();next();}
    }
    private void brothers(){
        velocity=velocity.scale(.95);
        if(timer==85)for(int side:new int[]{-1,1})warning(brotherSpawn(side),plane.scale(-side),120).parameter(MahouInfernumAttackEntity.BROTHER_WARNING);
        if(timer==205){boss.brothersDialogue();summon(MahouInfernumSummonEntity.Kind.CATACLYSM,brotherSpawn(-1),0);summon(MahouInfernumSummonEntity.Kind.CATASTROPHE,brotherSpawn(1),0);}
        if(timer>=285&&living(MahouInfernumSummonEntity.Kind.CATACLYSM)==0&&living(MahouInfernumSummonEntity.Kind.CATASTROPHE)==0)next();
    }
    Vec3 brotherSpawn(double side){return center().add(plane.scale(side*600/pixels()));}
    private void seekers(){
        if(timer==1){clearShots();clearKind(MahouInfernumSummonEntity.Kind.BOMBER);}
        velocity=velocity.scale(.98);
        if(timer>75&&timer<135&&timer%5==0){int i=(timer-75)/5;double a=Math.PI*4*(timer-75)/60;Vec3 off=planeDirection(a).scale((i>6?1000:500)/pixels());summon(MahouInfernumSummonEntity.Kind.SEEKER,arena.origin().add(off),i);}
        if(timer>=135&&living(MahouInfernumSummonEntity.Kind.SEEKER)<=3){clearKind(MahouInfernumSummonEntity.Kind.SEEKER);next();}
    }
    private double planeHalfSpan(){
        var b=arena.bounds();return Math.min(Math.abs(plane.x)<1e-9?Double.POSITIVE_INFINITY:b.getXsize()/(2*Math.abs(plane.x)),Math.abs(plane.z)<1e-9?Double.POSITIVE_INFINITY:b.getZsize()/(2*Math.abs(plane.z)));
    }
    private void finale(){
        int rate=rage()?11:56,n=rage()?17:7;double speed=rage()?40:8.4;
        if(finalState==0){moveTo(side(500,350),32,1.5);
            if(timer<660&&timer%16==15){offset=(offset+300/pixels())%(planeHalfSpan()*2);Vec3 at=arena.origin().add(plane.scale(-planeHalfSpan()+offset));at=new Vec3(at.x,arena.bounds().minY+10/pixels(),at.z);shot(MahouInfernumAttackEntity.Kind.PILLAR_WARNING,at,new Vec3(0,1,0),60,0,0);}
            if(timer<660&&timer%18==17)shot(MahouInfernumAttackEntity.Kind.REDIRECT_SOUL,center(),rotate(new Vec3(0,1,0),(boss.getRandom().nextDouble()*2-1)*.73).scale((8+boss.getRandom().nextDouble()*9)/pixels()),230,15,0);
            if(timer>=720){clearShots();finalState=1;timer=0;count=0;countdown=120;boss.forceCombatPosition(arena.origin());velocity=Vec3.ZERO;}}
        else if(finalState==1){
            if(--countdown<=0){countdown=Math.max(56,120-++count*6);for(int i=0;i<4;i++)bomb(center(),xy(Math.cos(i*Math.PI/2+(count%2)*Math.PI/4)*16,Math.sin(i*Math.PI/2+(count%2)*Math.PI/4)*16),(int)(countdown*.95),1050);}
            if(timer%rate==rate/2){aim=planarAim(target().subtract(center()));for(int side:new int[]{-1,1})for(int i=0;i<n;i++)warning(center(),rotate(aim,-.79+1.58*i/(n-1)).scale(side),rate/2).parameter(MahouInfernumAttackEntity.FINALE_WARNING);}
            if(timer%rate==rate-1){burst(center(),aim,n,.79,speed);burst(center(),aim.scale(-1),n,.79,speed);}
            if(timer>=636){clearShots();finalState=2;timer=count=wallIndex=0;countdown=75;}}
        else if(finalState==2){
            if(timer%60==59){var bounds=arena.bounds();for(double x=-planeHalfSpan();x<planeHalfSpan();x+=840/pixels())for(double dx=0;dx<420/pixels();dx+=42/pixels()){
                double along=x+dx+(wallIndex%2)*420/pixels()+(boss.getRandom().nextDouble()*2-1)*8/pixels();Vec3 at=arena.origin().add(plane.scale(along));at=new Vec3(at.x,bounds.maxY+(boss.getRandom().nextDouble()*2-1)*12/pixels(),at.z);if(along<planeHalfSpan())shot(MahouInfernumAttackEntity.Kind.FINAL_DART,at,new Vec3(0,-5/pixels(),0),480,14,0);}
                wallIndex++;}
            if(--countdown<=0){countdown=Math.max(34,75-++count*4);giga(center(),target().subtract(center()),9.4).parameter(13);}
            if(timer>=1050){clearShots();finalState=3;timer=0;countdown=38;count=0;}}
        else if(finalState==3){
            if(timer>=countdown){boss.forceCombatPosition(target().add(rotate(plane,boss.getRandom().nextDouble()*Math.PI*2).scale((180+boss.getRandom().nextDouble()*275)/pixels())));timer=0;countdown=Math.max(4,countdown-2);if(countdown==4&&++count>=20){finalState=4;timer=0;}}}
        else {if(timer==1){boss.forceCombatPosition(target().add(0,300/pixels(),0));velocity=Vec3.ZERO;}velocity=velocity.lerp(new Vec3(0,-6/pixels(),0),.018);if(timer>=780)boss.finishInfernum();}
    }
    void save(CompoundTag root){
        var t=new CompoundTag();t.putFloat("RetreatOpacity",retreatOpacity);t.putInt("Version",2);if(trackingWarning!=null)t.putUUID("TrackingWarning",trackingWarning);t.putDouble("Pixels",sourcePixels);t.putInt("Stage",stage);t.putString("Attack",attack.name());t.putInt("Timer",timer);t.putInt("Cycle",cycle);t.putInt("Entrance",entrance);t.putInt("Count",count);t.putInt("State",state);t.putInt("FinalState",finalState);t.putInt("Countdown",countdown);t.putInt("WallIndex",wallIndex);t.putBoolean("Berserk",berserk);t.putBoolean("Desperation",desperation);t.putBoolean("Initialized",initialized);t.putBoolean("RetireLegacyMembers",retireLegacyMembers);t.putBoolean("ShortRitual",shortRitual);t.putDouble("Angle",angle);t.putDouble("Direction",direction);t.putDouble("Offset",offset);t.putDouble("Counter",counter);
        MahouInfernumAttackEntity.vector(t,"Plane",plane);MahouInfernumAttackEntity.vector(t,"Velocity",velocity);MahouInfernumAttackEntity.vector(t,"Aim",aim);MahouInfernumAttackEntity.vector(t,"Origin",origin);if(arena!=null)t.put("Arena",arena.save());root.put("Infernum",t);
    }
    void load(CompoundTag root){
        var t=root.getCompound("Infernum");if(t.isEmpty()){stage=Math.max(0,Math.min(2,root.getByte("Phase")-1));attack=stage==0?SummonSepulcher:PhaseTransition;entrance=stage==0?270:0;retireLegacyMembers=root.contains("Phase");return;}
        if(t.getDouble("Pixels")>0)sourcePixels=Double.isFinite(t.getDouble("Pixels"))?t.getDouble("Pixels"):sourcePixels;
        retreatOpacity=t.contains("RetreatOpacity")?Math.max(0,Math.min(1,t.getFloat("RetreatOpacity"))):1;
        trackingWarning=t.hasUUID("TrackingWarning")?t.getUUID("TrackingWarning"):null;stage=Math.max(0,Math.min(4,t.getInt("Stage")));try{attack=MahouInfernumEncounter.Attack.valueOf(t.getString("Attack"));}catch(IllegalArgumentException e){attack=SummonSepulcher;}
        timer=t.getInt("Timer");cycle=t.getInt("Cycle");entrance=t.getInt("Entrance");count=t.getInt("Count");state=t.getInt("State");finalState=t.getInt("FinalState");countdown=t.getInt("Countdown");wallIndex=t.getInt("WallIndex");berserk=t.getBoolean("Berserk");desperation=t.getBoolean("Desperation");initialized=t.getBoolean("Initialized")&&t.contains("Arena");angle=t.getDouble("Angle");direction=t.getDouble("Direction");offset=t.getDouble("Offset");counter=t.getDouble("Counter");
        plane=MahouInfernumAttackEntity.vector(t,"Plane").multiply(1,0,1);plane=plane.lengthSqr()>1e-9?plane.normalize():new Vec3(1,0,0);velocity=MahouInfernumAttackEntity.vector(t,"Velocity");aim=MahouInfernumAttackEntity.vector(t,"Aim");origin=MahouInfernumAttackEntity.vector(t,"Origin");if(t.contains("Arena"))arena=SupremeCalamitasArena.load(t.getCompound("Arena"));
        retireLegacyMembers=t.getBoolean("RetireLegacyMembers");shortRitual=t.getBoolean("ShortRitual");
        if(t.getInt("Version")!=2){
            retireLegacyMembers=true;trackingWarning=null;timer=cycle=count=state=finalState=countdown=wallIndex=0;
            velocity=Vec3.ZERO;berserk=false;attack=stage==0?SummonSepulcher:PhaseTransition;entrance=0;
        }
    }
}

