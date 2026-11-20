package net.everla.projectc.arkofcosmos.entity;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
/** Existing ground/water patrol retained independently of combat choreography. */
final class MahouPatrol {
    private final MahouTsukaiEntity boss;
    private Vec3 territory,wander;
    private int patrolPause,patrolBlocked;
    private double patrolAngle;
    MahouPatrol(MahouTsukaiEntity boss){this.boss=boss;}
    private void face(Vec3 at){boss.getLookControl().setLookAt(at.x,at.y,at.z,180,180);}
    private Vec3 avoidSolid(Vec3 desired){
        // Prefer open swept volumes, but phase out when completely enclosed rather than becoming trapped.
        for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}){
            Vec3 candidate=desired.yRot((float)angle);
            if(!boss.level().getBlockCollisions(boss,boss.getBoundingBox().expandTowards(candidate)).iterator().hasNext())return candidate;
        }
        return desired.add(0,.3,0);
    }

    void tick(){
        Vec3 previous=boss.getDeltaMovement();double speed=previous.multiply(1,0,1).length();
        if(speed>.1)boss.setDeltaMovement(previous.x*.1/speed,Math.max(-.12,Math.min(.12,previous.y)),previous.z*.1/speed);
        if(territory==null){territory=boss.position();patrolAngle=Math.toRadians(boss.getYRot());}
        if(boss.horizontalCollision)patrolBlocked++;else patrolBlocked=0;
        if(wander!=null&&boss.position().multiply(1,0,1).distanceTo(wander.multiply(1,0,1))<1){
            wander=null;patrolPause=40+boss.getRandom().nextInt(41);
        }
        if(patrolBlocked>=20){wander=null;patrolBlocked=0;patrolPause=20;}
        if(patrolPause>0){
            patrolPause--;
            boss.setDeltaMovement(boss.getDeltaMovement().multiply(.8,0,.8).add(0,
                    Math.max(-.12,Math.min(.12,hoverGround(boss.position())+2-boss.getY())),0));return;
        }
        if(wander==null){
            patrolAngle+=.65+boss.getRandom().nextDouble()*.2;
            double radius=12+boss.getRandom().nextDouble()*4;
            Vec3 at=territory.add(Math.cos(patrolAngle)*radius,0,Math.sin(patrolAngle)*radius);
            wander=new Vec3(at.x,hoverGround(at)+2,at.z);
        }
        wander=new Vec3(wander.x,hoverGround(wander)+2,wander.z);
        Vec3 horizontal=wander.subtract(boss.position()).multiply(1,0,1);
        Vec3 delta=new Vec3(horizontal.x,hoverGround(boss.position().add(horizontal.normalize().scale(2)))+2-boss.getY(),horizontal.z);
        Vec3 travel=horizontal.length()<.10?horizontal:horizontal.normalize().scale(.10);
        Vec3 desired=travel.add(0,Math.max(-.12,Math.min(.12,delta.y)),0);
        Vec3 velocity=boss.getDeltaMovement().lerp(desired,.12);
        velocity=avoidSolid(velocity);boss.setDeltaMovement(velocity);
        if(velocity.multiply(1,0,1).lengthSqr()>1e-6)face(boss.getEyePosition().add(velocity.normalize().scale(8)));
    }

    private double hoverGround(Vec3 at){return hoverGround(at,boss.getY()-2);}
    private double hoverGround(Vec3 at,double fallback){
        double topY=boss.getY()+3;
        // Recover even if already submerged: a ray starting underwater only sees that interior fluid cell.
        if(boss.isInWaterOrBubble()||!boss.level().getFluidState(boss.blockPosition()).isEmpty()||!boss.level().getFluidState(net.minecraft.core.BlockPos.containing(at.x,boss.getY(),at.z)).isEmpty())
            topY=Math.max(topY,boss.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.util.Mth.floor(at.x),net.minecraft.util.Mth.floor(at.z))+1);
        Vec3 top=new Vec3(at.x,topY,at.z);
        BlockHitResult hit=boss.level().clip(new ClipContext(top,top.add(0,-64,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,boss));
        return hit.getType()==HitResult.Type.MISS?fallback:hit.getLocation().y;
    }

}
