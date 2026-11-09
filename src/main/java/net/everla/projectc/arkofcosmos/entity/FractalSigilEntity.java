package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.SatsukiConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Satsuki Rin's replacement for the phase-two fire blast trap. Same lead-up as the trap (20 ticks, none when the
 * trap was "immediate"), then one second of First Fractal slashes at the player cadence, all aimed 2 blocks above
 * the sigil (never at her target).
 */
public final class FractalSigilEntity extends Entity {
    public static final int TRAP_LEAD=20,ACTIVE=20,FADE=10;
    private static final EntityDataAccessor<Integer> LEAD=SynchedEntityData.defineId(FractalSigilEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(FractalSigilEntity.class,EntityDataSerializers.FLOAT);
    private LivingEntity owner;
    private int frame;
    public FractalSigilEntity(EntityType<? extends FractalSigilEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public static FractalSigilEntity create(EntityType<? extends FractalSigilEntity> type,LivingEntity owner,Vec3 at,boolean immediate,float radius){
        var e=new FractalSigilEntity(type,owner.level());e.owner=owner;e.setPos(at);
        e.entityData.set(LEAD,immediate?0:TRAP_LEAD);e.entityData.set(RADIUS,Math.max(1.5f,radius));return e;
    }
    public int lead(){return entityData.get(LEAD);}
    public float radius(){return entityData.get(RADIUS);}
    @Override protected void defineSynchedData(){entityData.define(LEAD,TRAP_LEAD);entityData.define(RADIUS,3f);}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        if(owner==null||!owner.isAlive()||owner.isDeadOrDying()){discard();return;}
        int t=tickCount-lead();
        if(t>=ACTIVE+FADE){discard();return;}
        if(t<0||t>=ACTIVE)return;
        Vec3 aim=position().add(0,2,0);
        float damage=(float)SatsukiConfig.value("fractal_sigil_damage");
        // Player cadence: three 60 Hz frames per tick, one slash every 7 frames, hue cycling over 35 frames.
        for(int sub=0;sub<3;sub++){
            if(frame%7==0)FirstFractalEntity.launchBoss(owner,aim,damage,frame/35d%1,sub*2);
            frame++;
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<128*128;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
