package net.everla.projectc.arkofcosmos.enchant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
public record TaintedGeometry(Vec3 shoulder,Vec3 elbow,Vec3 hand,Vec3 blade,Vec3 direction) {
    public static TaintedGeometry of(Player p,Vec3 center,Vec3 tip,int variant,double age,double completion,boolean swinging){
        Vec3 forward=p.getLookAngle(),up=new Vec3(0,1,0);double vertical=variant==0?0:-80;
        double t=CurseMath.smooth(0,1,Math.min(1,completion/.67))*Math.max(0,Math.min(1,(1-completion)/.33));
        Vec3 aim=center.add(forward.scale(-(80+Math.abs(vertical)*.55)/16)).add(up.scale(27/16d+(variant==1?-30/16d:0)));
        if(swinging)aim=aim.lerp(center.add(forward.scale(510/16d)),t);
        Vec3 elbow=center.add(aim.subtract(center).normalize().scale(80/16d));
        Vec3 idle=swinging?Vec3.ZERO:forward.scale(Math.cos(age/27)*5/16).add(up.scale(Math.sin(age/27)*4/16));
        if(variant==1)idle=idle.add(forward.scale((1-completion)*40/16));
        Vec3 arm=tip.subtract(elbow).normalize().scale(108/16d).add(idle);
        Vec3 hand=elbow.add(arm);Vec3 direction=arm.normalize();
        double angle=Math.PI/2*Math.max(0,Math.min(1,(1-completion)/.17));
        direction=direction.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
        return new TaintedGeometry(center,elbow.add(idle),hand,hand.add(direction.scale((16d/4+31)/16)),direction);
    }
}
