import java.util.*;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.minecraft.world.phys.Vec3;

public final class CalamitasSparksCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var deathRandom = new Random(71862); int fades = 0; double[] squaredSpeeds = new double[3]; int[] groups = new int[3];
        var deathCenter = new Vec3(412, 81, -115); var deathPlane = new Vec3(.6, 0, .8); var deathDepth = deathPlane.cross(new Vec3(0, 1, 0));
        for (int trial = 0; trial < 5000; trial++) {
            double pixels = trial % 2 == 0 ? 16 : 32;
            var emitted = new ArrayList<SupremeCalamitasSparks.DeathDust>();
            SupremeCalamitasSparks.seekerDeathDust(deathCenter, deathPlane, pixels, deathRandom, emitted::add);
            require(emitted.size() == 25, "seeker death exactly25 native Dust");
            int noGravity = 0;
            for (int i = 0; i < 25; i++) {
                var e = emitted.get(i); var d = e.dust();
                require(d.type() == 235 && d.variant() >= 0 && d.variant() < 3, "seeker death native235 three frames");
                boolean expected = i >= 5 && (i - 5) % 2 == 0; require(e.noGravity() == expected, "source5 +10 interleaved pairs");
                if (expected) noGravity++;
                if (e.fadeIn() > 0) { require(i < 5 && d.scale() == .5f && e.fadeIn() >= 1 && e.fadeIn() <= 1.900001, "source first5 fade/scale override"); fades++; }
                else require(d.scale() >= (expected ? 2.4f : 1.6f) && d.scale() <= (expected ? 3.600001 : 2.400001), "NewDust0.8..1.2 base scale");
                Vec3 offset = d.position().subtract(deathCenter).scale(pixels);
                for (double axis : new double[]{offset.dot(deathPlane), -offset.y, offset.dot(deathDepth)})
                    require(axis >= -21.000001 && axis <= 24.000001 && Math.abs(axis - Math.rint(axis)) < 1e-8, "native50px region integer offsets extended to depth");
                int group = i < 5 ? 0 : expected ? 1 : 2; double multiplier = group == 0 ? 3 : group == 1 ? 5 : 2;
                double speed2 = d.velocity().lengthSqr() * pixels * pixels / (multiplier * multiplier);
                require(speed2 <= 8.000001, "source sampled2D speed magnitude preserved"); squaredSpeeds[group] += speed2; groups[group]++;
            }
            require(noGravity == 10, "source exactly10 noGravity and15 ordinary");
        }
        require(fades > 12000 && fades < 13000, "source first5 half fade probability");
        for (int i = 0; i < 3; i++) require(Math.abs(squaredSpeeds[i] / groups[i] - 2.8) < .06, "native square velocity distribution not invented spherical magnitude");
        System.out.println("PASS: seeker death5000 bursts125000 Dust, exact25/10noGravity, native box/scale/speed distribution/half fade");
        var bloodRandom = new Random(11491); double bloodRadius = 0; Vec3 bloodDirections = Vec3.ZERO;
        var heartCenter = new Vec3(120, 80, -240); var heartEndpoint = new Vec3(125, 93, -228); var heartPlane = new Vec3(.6, 0, .8);
        for (int trial = 0; trial < 1000; trial++) {
            var blood = new ArrayList<SupremeCalamitasSparks.Dust>();
            double pixels = trial % 2 == 0 ? 16 : 32;
            SupremeCalamitasSparks.heartTendrilDust(heartCenter, heartEndpoint, heartPlane, pixels, bloodRandom, blood::add);
            require(blood.size() == 70, "heart exactly70 Dust per endpoint");
            for (int i = 0; i < 70; i++) {
                var d = blood.get(i); require(d.type() == 5 && d.variant() >= 0 && d.variant() < 3, "heart native Blood5 three frames");
                Vec3 expected = heartCenter.lerp(heartEndpoint, i / 70f).add(heartPlane.scale(4 / pixels)).add(0, -4 / pixels, 0);
                require(d.position().distanceTo(expected) < 1e-12, "heart 0..69/70 line positions and native4px NewDust offset");
                require(d.scale() >= 1 && d.scale() < 1.4f, "heart source scale");
                double speed = d.velocity().length() * pixels; require(speed >= 0 && speed < 3 + 1e-6, "heart source circular speed magnitude");
                require(Math.abs(d.horizontalSpeed() - d.velocity().x * pixels) < 1e-6, "heart native horizontal spin");
                bloodRadius += speed / 3; if (speed > 1e-9) bloodDirections = bloodDirections.add(d.velocity().normalize());
            }
        }
        require(bloodRadius > 34000 && bloodRadius < 36000 && bloodDirections.length() / 70000 < .015, "heart uniform radius and 3D directions");
        System.out.println("PASS: heart1000 tendrils70000 Blood5, source count/line/4px offset/scale/speed and spherical directions");
        var hexRandom=new Random(91425);int hexCount=0,hexRed=0;
        for(int i=0;i<20000;i++) {
            var spark=SupremeCalamitasSparks.hexSpark(Vec3.ZERO,16,hexRandom);
            if(spark==null)continue;
            hexCount++;if(spark.color()==0xFF0000)hexRed++;
            require(spark.inward()&&spark.lifetime()>=18&&spark.lifetime()<27,"Hex VoidSpark lifetime");
            require(spark.scale()>=.015f&&spark.scale()<.045f,"Hex source scale");
            require(spark.color()==0xFF0000||spark.color()==0xFF00A5,"Hex quantized source tint");
            require(Math.abs(spark.position().x)<=10/16d&&Math.abs(spark.position().y)<=20/16d&&Math.abs(spark.position().z)<=10/16d,"Hex source spot box");
            require(spark.velocity().x==0&&spark.velocity().z==0&&spark.velocity().y>=6/16d&&spark.velocity().y<10/16d,"Hex source upward speed");
        }
        require(hexCount>9600&&hexCount<10400&&hexRed>hexCount*.47&&hexRed<hexCount*.53,"Hex half emission and tint probability");
        int brimstone=0;int[] dustFrames=new int[3];
        for(int i=0;i<20000;i++) {
            var dust=SupremeCalamitasSparks.hexDust(Vec3.ZERO,16,hexRandom);
            if(dust.type()==235)brimstone++;else require(dust.type()==261,"source native Dust type");
            require(dust.variant()>=0&&dust.variant()<3,"native three Dust frames");dustFrames[dust.variant()]++;
            require(dust.scale()>=.4f&&dust.scale()<.5f,"source Dust scale");
            require(Math.abs(dust.position().x)<=10/16d&&Math.abs(dust.position().y)<=20/16d&&Math.abs(dust.position().z)<=10/16d,"source Dust spot");
            double speed=dust.velocity().length()*16;
            require(speed>=.5-1e-6&&speed<2.5+1e-6,"source Dust speed magnitude");
            require(dust.velocity().y/dust.velocity().length()>=Math.cos(.6)-1e-6,"source Dust cone angle");
            require(Math.abs(Math.hypot(dust.velocity().x,dust.velocity().z)*16-Math.abs(dust.horizontalSpeed()))<1e-6,"source Dust spin projection");
        }
        require(brimstone>9600&&brimstone<10400,"half probability native Dust variants");
        for(int count:dustFrames)require(count>6300&&count<7000,"uniform native Dust frames");
        for(boolean giga:new boolean[]{false,true}) {
            var blooms=new ArrayList<SupremeCalamitasSparks.Light>();
            for(int remaining=60;remaining>0;remaining--) {
                int before=blooms.size();SupremeCalamitasSparks.fireballBlooms(giga,remaining,Vec3.ZERO,blooms::add);
                require(blooms.size()-before==(remaining==30?2:remaining==15||remaining==8?1:0),"fireball bloom exact source frame");
            }
            require(blooms.size()==4&&blooms.get(0).lifetime()==30&&blooms.get(1).lifetime()==30&&blooms.get(2).lifetime()==15&&blooms.get(3).lifetime()==8,"fireball bloom source count/life");
            require(blooms.get(0).color()==0x79154D&&blooms.get(2).color()==0xFF0000&&blooms.get(3).color()==0xFFFFFF,"fireball bloom colors");
            require(blooms.get(0).scale()==(giga?.85f:.7f)&&blooms.get(2).scale()==(giga?.8f:.65f)&&blooms.get(3).scale()==(giga?.7f:.5f),"fireball bloom source scales");
        }
        var trailRandom=new Random(931);
        var trailCenter=new Vec3(20,70,-30);var trailVelocity=new Vec3(.2,-.3,.4);
        for(boolean giga:new boolean[]{false,true}) {
            double radialSum=0;int magenta=0;
            for(int i=0;i<10000;i++) {
                var spark=SupremeCalamitasSparks.fireballTrail(giga,trailCenter,trailVelocity,16,trailRandom);
                Vec3 offset=spark.position().subtract(trailCenter.subtract(trailVelocity));
                double radius=(giga?30:20)/16d, fraction=offset.length()/radius;
                require(fraction<=1,"source trail offset radius");radialSum+=fraction*fraction;
                double ratio=spark.velocity().length()/trailVelocity.length();
                require(ratio>=.1-1e-6&&ratio<1,"source backward speed magnitude");
                require(spark.velocity().cross(trailVelocity).length()<1e-12&&spark.velocity().dot(trailVelocity)<0,"backward heading");
                require(spark.scale()>=(giga?.5f:.35f)&&spark.scale()<(giga?.75f:.6f)&&spark.lifetime()==14&&!spark.inward(),"trail scale lifetime type");
                require(spark.color()==0xFF0000||spark.color()==0xFF007F,"source trail colors");
                if(spark.color()==0xFF007F)magenta++;
            }
            require(radialSum>3150&&radialSum<3500&&magenta>4700&&magenta<5300,"source radial and color distributions");
        }
        require(SupremeCalamitasSparks.fireballTrail(false,trailCenter,Vec3.ZERO,16,trailRandom).velocity().lengthSqr()==0,"stationary trail remains stationary");
        var orbRandom=new Random(5123);int black=0;
        for(int hit=0;hit<10000;hit++) {
            var orbs=new ArrayList<SupremeCalamitasSparks.Orb>();
            SupremeCalamitasSparks.vortexHit(16,orbRandom,orbs::add);
            require(orbs.size()>=1&&orbs.size()<=2,"source one orb plus optional black orb");
            for(int i=0;i<orbs.size();i++) {
                var orb=orbs.get(i);double speed=orb.velocity().length()*16;
                require(speed>=Math.sqrt(72)*.3f-1e-6&&speed<Math.sqrt(72)*1.1f+1e-6,"source vortex orb speed magnitude");
                require(orb.scale()>=1.55f&&orb.scale()<3.75f,"source vortex orb scale");
                require(orb.additive()==(i==0)&&orb.glowCenter()==(i==0),"source orb blend and center flags");
                require(i==0?(orb.color()==0xFF0000||orb.color()==0xFF007F):orb.color()==0,"source quantized color");
            }
            if(orbs.size()==2)black++;
        }
        require(black>4700&&black<5300,"source half probability black orb");
        var a=new Vec3(11,70,-25);var b=new Vec3(-14,90,34);var random=new Random(48123);
        int total=0,red=0,tint=0;
        require(SupremeCalamitasSparks.inwardCount(60)==0,"60 silent");
        require(SupremeCalamitasSparks.inwardCount(59)==2,"source float loop ceil");
        require(SupremeCalamitasSparks.inwardCount(20)==6,"source power one");
        require(SupremeCalamitasSparks.inwardCount(1)==9,"unclamped power");
        require(SupremeCalamitasSparks.inwardCount(0)==21,"final power four");
        for(int delay=59;delay>=0;delay--){
            var out=new ArrayList<SupremeCalamitasSparks.Spark>();
            SupremeCalamitasSparks.brothers(delay,a,b,16,random,out::add);
            int inward=SupremeCalamitasSparks.inwardCount(delay)*2;
            require(out.size()==inward+(delay==0?60:0),"source count at delay "+delay);total+=out.size();
            for(int i=0;i<out.size();i++){
                var s=out.get(i);var center=i%2==0?b:a;
                if(s.color()==0xFF0000)red++;else tint++;
                if(i<inward){
                    require(s.inward()&&s.lifetime()==9,"inward kind/life");
                    require(s.position().subtract(center).add(s.velocity().scale(18)).length()<1e-10,"inward 4.5/-0.25 relation");
                    double speed=s.velocity().length()*16;
                    require(speed>=70*.04*2*.25-1e-5&&speed<=150*.04*13*.25+1e-5,"inward magnitude preserved");
                    require(s.scale()>=.08f-delay*.002f&&s.scale()<.16f-delay*.002f,"signed source scale range");
                    if(i%2==1)require(s.velocity().equals(out.get(i-1).velocity()),"source shared paired offset");
                }else{
                    require(!s.inward()&&s.lifetime()==35&&s.position().equals(center),"outward native center/life");
                    require(s.velocity().length()*16>=Math.sqrt(50)*.5-1e-6&&s.velocity().length()*16<Math.sqrt(50)*1.5,"outward speed");
                }
            }
        }
        require(red>tint&&tint>0,"source color alternatives");
        var small=new ArrayList<SupremeCalamitasSparks.Spark>();var large=new ArrayList<SupremeCalamitasSparks.Spark>();
        SupremeCalamitasSparks.brothers(0,a,b,16,new Random(9),small::add);SupremeCalamitasSparks.brothers(0,a,b,32,new Random(9),large::add);
        for(int i=0;i<small.size();i++)require(small.get(i).velocity().distanceTo(large.get(i).velocity().scale(2))<1e-10,"pixel config preserves source speed");
        net.everla.projectc.arkofcosmos.calamity.client.CalamitasSparkParticleCheck.main(args);
        var velocity=new Vec3(.02,-.03,.04);
        for(boolean dash:new boolean[]{false,true}) for(boolean burst:new boolean[]{false,true}) {
            var out=new ArrayList<SupremeCalamitasSparks.Spark>();
            SupremeCalamitasSparks.slash(dash,burst,a,velocity,out::add);
            require(out.size()==(burst||!dash?1:3),"slash source count");
            for(var spark:out) require(spark.velocity().equals(velocity)&&spark.inward()==burst
                    &&spark.lifetime()==(burst?9:25)&&spark.scale()==(burst?(dash?1.3f:.7f):5),"slash source parameters");
            require(out.get(0).position().equals(a),"slash center");
            if(dash&&!burst) require(out.get(1).position().equals(a.add(velocity.scale(50)))
                    &&out.get(2).position().equals(a.subtract(velocity.scale(50))),"slash locked 3D offsets");
        }
        System.out.println("Calamitas slash warning1/3 and burst1, source scales/lifetimes/locked velocity offsets passed");
        System.out.println("Calamitas source spark emissions passed: last60frames total="+total+", shared paired offsets, signed scales, speed ranges and pixel conversion");
    }
}
