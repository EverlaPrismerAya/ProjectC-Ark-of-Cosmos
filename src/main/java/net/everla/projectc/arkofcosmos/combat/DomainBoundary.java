package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.MahouAttackEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Clip the whole bounding box against the domain sphere, retaining tangential movement. */
public final class DomainBoundary {
    public static Vec3 clip(Entity entity,Vec3 movement){
        if(entity.level()==null||entity instanceof MahouAttackEntity)return movement;
        for(var domain:entity.level().getEntitiesOfClass(MahouAttackEntity.class,
                entity.getBoundingBox().expandTowards(movement).inflate(33),
                e->e.kind()==MahouAttackEntity.DOMAIN&&!e.isRemoved())){
            if(entity.getBoundingBox().getCenter().distanceToSqr(domain.position())<32*32)
                movement=clipBox(entity.getBoundingBox(),movement,domain.position());
            else{
                // The outer face also blocks entry, just like a solid entity collision.
                Vec3 offset=entity.getBoundingBox().getCenter().subtract(domain.position());
                double radius=32+entity.getBoundingBox().getSize()/2;
                double a=movement.lengthSqr(),b=offset.dot(movement);
                double c=offset.lengthSqr()-radius*radius,discriminant=b*b-a*c;
                if(a>0&&b<0&&discriminant>=0){
                    double hit=(-b-Math.sqrt(discriminant))/a;
                    if(hit>=0&&hit<1)movement=movement.scale(Math.max(0,hit-.00001));
                    else if(c<0)movement=Vec3.ZERO;
                }
            }
        }
        return movement;
    }
    public static Vec3 clipBox(AABB box,Vec3 movement,Vec3 origin){
        Vec3 center=box.getCenter().subtract(origin);
        double[] c={center.x,center.y,center.z};
        double[] half={box.getXsize()/2,box.getYsize()/2,box.getZsize()/2};
        double[] delta={movement.x,movement.y,movement.z};
        // Match vanilla's vertical-first collision response; collision flags are set by Entity.move.
        for(int axis:new int[]{1,0,2}){
            int a=(axis+1)%3,b=(axis+2)%3;
            double farA=Math.abs(c[a])+half[a],farB=Math.abs(c[b])+half[b];
            double limit=Math.max(0,Math.sqrt(Math.max(0,32*32-farA*farA-farB*farB))-half[axis]-.00001);
            double next=net.minecraft.util.Mth.clamp(c[axis]+delta[axis],-limit,limit);
            delta[axis]=next-c[axis];c[axis]=next;
        }
        return new Vec3(delta[0],delta[1],delta[2]);
    }
}
