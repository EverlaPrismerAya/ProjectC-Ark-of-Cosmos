import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.CalamitasSpace;
import net.minecraft.world.phys.Vec3;
public final class CalamitasSpaceCheck {
 public static void main(String[] args) {
  for(double speed:new double[]{0,1e-12,1e-9,1e-7,1e-5,.1,10}) {
   var velocity=new Vec3(0,0,speed);
   for(var offset:new Vec3[]{new Vec3(10,0,0),new Vec3(0,0,-10),new Vec3(1e-9,0,0),Vec3.ZERO}) {
    var next=CalamitasSpace.homeGigablast(velocity,offset);
    if(!Double.isFinite(next.lengthSqr())||Math.abs(next.length()-speed)>Math.max(1e-24,speed*1e-12))throw new AssertionError("Gigablast lost source speed "+speed);
    if(speed>0&&offset.x>0&&next.x<=0)throw new AssertionError("Gigablast did not turn toward target");
   }
  }
  for(double length:new double[]{0,1e-9,1e-7,1e-6,1e-5,1e-4,.1,100}) {
   var fallback=new Vec3(1,0,0);var unit=CalamitasSpace.unit(new Vec3(0,0,length),fallback);
   if(Math.abs(unit.length()-1)>1e-12)throw new AssertionError("near-zero source direction collapsed at "+length);
   if(length>=1e-6&&unit.z<.99999)throw new AssertionError("small nonzero source direction lost");
   var arena=new net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena(Vec3.ZERO,16);
   arena.beginBarrage(new Vec3(length,300,length),1);
   if(Math.abs(arena.attackX().length()-1)>1e-12)throw new AssertionError("small horizontal barrage plane collapsed");
   net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena.load(arena.save());
  }
  System.out.println("Calamitas near-vertical aim: nonzero unit planes below vanilla normalization cutoff and NBT passed");
  var random=new Random(48123);var sum=Vec3.ZERO;double xx=0,yy=0,zz=0;
  for(int i=0;i<12000;i++) {var v=CalamitasSpace.direction(random);if(Math.abs(v.length()-1)>1e-12)throw new AssertionError("unit speed");sum=sum.add(v);xx+=v.x*v.x;yy+=v.y*v.y;zz+=v.z*v.z;
   var axis=CalamitasSpace.spreadAxis(v,random);if(Math.abs(axis.length()-1)>1e-12||Math.abs(axis.dot(v))>1e-12)throw new AssertionError("3D fan axis");}
  if(sum.length()/12000>.025||Math.min(xx,Math.min(yy,zz))/12000<.3)throw new AssertionError("sphere distribution collapsed or biased");
  System.out.println("Calamitas feedback 3D sphere: unit speeds, all three axes, unbiased spread and perpendicular brother fan axes passed");
  var plane=new Vec3(.6,0,.8);
  for(int i=0;i<12000;i++) {
   var shoot=i<5?new Vec3[]{Vec3.ZERO,new Vec3(0,1,0),plane.cross(new Vec3(0,-1,0)),new Vec3(0,1e-5,0),plane.scale(.3).add(0,-.4,0)}[i]:CalamitasSpace.direction(random).scale(.01+random.nextDouble()*3);
   var axis=CalamitasSpace.fistAxis(shoot,plane);
   if(Math.abs(axis.length()-1)>1e-10||Math.abs(axis.dot(shoot))>1e-10)throw new AssertionError("fist locked plane unit/perpendicular");
   for(int branch=0;branch<3;branch++) {
    var ray=CalamitasSpace.fistRay(shoot,axis,branch);
    if(Math.abs(ray.length()-shoot.length())>1e-10||Math.abs(ray.dot(axis))>1e-10)throw new AssertionError("fist ray speed/plane");
    if(shoot.lengthSqr()>0&&Math.abs(ray.dot(shoot.scale(-1))/shoot.lengthSqr()-(branch==0?1:-.5))>1e-10)throw new AssertionError("fist source 120-degree angle");
   }
  }
  System.out.println("PASS: fist locked plane 12000 3D/vertical/depth/zero cases, unit axis, three120degree rays and unchanged speed");
 }
}
