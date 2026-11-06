import java.util.ArrayList;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.minecraft.world.phys.Vec3;

public final class CalamitasFlameDustCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var random = new Random(60114); Vec3 center = new Vec3(3, 20, -8), velocity = new Vec3(.2, -.3, .4);
        var start = new ArrayList<SupremeCalamitasSparks.Light>();
        SupremeCalamitasSparks.vortexStart(center, start::add);
        require(start.size() == 3, "vortex startup three blooms");
        for (int i = 0; i < 3; i++) {
            var light = start.get(i);
            require(light.bloom() && light.position().equals(center) && light.lifetime() == 120, "vortex startup center/lifetime");
            require(light.scale() == (i < 2 ? 1.45f : 1.35f) && light.color() == (i < 2 ? 0xFF004C : 0xFFFFFF), "vortex startup scale/color");
        }
        System.out.println("PASS: vortex startup3 original Bloom count/center/scale/color/lifetime");
        var burst = new ArrayList<SupremeCalamitasSparks.Light>();
        SupremeCalamitasSparks.sepulcherBlooms(center, burst::add);
        require(burst.size() == 2, "Sepulcher bloom pair");
        for (int i=0;i<2;i++) {
            var light=burst.get(i);
            require(light.bloom() && light.position().equals(center) && light.lifetime()==30, "Sepulcher center/life");
            require(light.scale()==(i==0?.9f:.8f) && light.color()==(i==0?0xFF0000:0xFFFFFF), "Sepulcher scale/color");
        }
        System.out.println("PASS: Sepulcher burst two native Bloom targets/color/center/life");
        for(int sample=0;sample<10000;sample++) {
            Vec3 aim=new Vec3(.1,.2,.3), inherited=new Vec3(-.2,.1,.05);
            var orb=SupremeCalamitasSparks.seekerWarning(center,aim,inherited,random);
            require(orb.position().equals(center.add(aim.scale(7)))&&orb.lifetime()==15,"seeker warning position/life");
            Vec3 own=orb.velocity().subtract(inherited.scale(.5f));
            double factor=own.dot(aim)/aim.lengthSqr();
            require(own.cross(aim).lengthSqr()<1e-20&&factor>=.6f*1.8f-1e-6&&factor<1.1f*1.8f+1e-6,"seeker warning speed and inherited motion");
            require(orb.scale()>=.7f&&orb.scale()<.75f&&(orb.color()==0xFF004C||orb.color()==0xFF0000),"seeker warning scale/color");
        }
        System.out.println("PASS: seeker warning GlowOrb10000 source offset/velocity/inherited motion/color/scale/life");
        int seekerRed=0,seekerTotal=0;
        for(boolean fired:new boolean[]{false,true})for(int sample=0;sample<10000;sample++) {
            var dusts=new ArrayList<SupremeCalamitasSparks.Dust>();
            Vec3 aim=new Vec3(.6,.8,0).scale(5/16d);
            SupremeCalamitasSparks.seekerShotDust(fired,center,aim,16,random,dusts::add);
            require(dusts.size()==(fired?5:10),"seeker shot counts");
            for(var d:dusts) {
                seekerTotal++;if(d.type()==60)seekerRed++;
                require((d.type()==60||d.type()==114)&&d.variant()>=0&&d.variant()<3&&d.position().equals(center),"seeker native ID/frame/center");
                double speed=d.velocity().length()*16/(fired?5:Math.sqrt(18));
                require(speed>=(fired?2.2f:.5f)-1e-6&&speed<(fired?3.8f:1.3f)+1e-6,"seeker speed");
                require(d.scale()>=(fired?1.8f:1.3f)&&d.scale()<(fired?2.1f:2.4f),"seeker scale");
                if(fired)require(d.velocity().normalize().dot(aim.normalize())>=Math.cos(1)-1e-6,"seeker one radian cone");
            }
        }
        require(seekerTotal==150000&&seekerRed>49000&&seekerRed<51000,"seeker one-third color mix");
        System.out.println("PASS: seeker shot Dust20000 cases/counts5or10/native60and114/source speed/scale/aim cone");
        int negativeDust=0;
        for(int sample=0;sample<10000;sample++) {
            float scale=random.nextFloat(.1f,1.5f);
            var dusts=new ArrayList<SupremeCalamitasSparks.Dust>();
            SupremeCalamitasSparks.vortexGrowthDust(scale,center,16,random,dusts::add);
            require(dusts.size()==10,"growth Dust count10");
            for(var dust:dusts) {
                require(dust.type()==235&&dust.variant()>=0&&dust.variant()<3&&dust.position().equals(center),"growth Dust native235 and center mapping");
                double speed=dust.velocity().length()*16/Math.sqrt(1800);
                require(speed>=.05f-1e-6&&speed<1.2f+1e-6,"growth Dust speed");
                float original=dust.scale()+scale*1.5f;
                require(original>=1.7f-1e-6&&original<2.8f+1e-6,"growth Dust scale");
                require(Math.abs(dust.horizontalSpeed()-dust.velocity().x*16)<1e-5,"growth Dust source spin");
                if(dust.scale()<0)negativeDust++;
            }
        }
        require(negativeDust>0,"growth Dust negative scales preserved");
        System.out.println("PASS: vortex growth Dust10000 cases/count10/center/native235/speed/signed scale");
        int negative = 0;
        for (int sample = 0; sample < 10000; sample++) {
            float scale = random.nextFloat(.1f, 1.5f);
            var growth = new ArrayList<SupremeCalamitasSparks.Spark>();
            SupremeCalamitasSparks.vortexGrowth(scale, center, 16, random, growth::add);
            require(growth.size() == 3, "vortex growth count3");
            for (var orb : growth) {
                require(orb.position().distanceTo(center.add(orb.velocity().scale(2 * (scale * 5f)))) < 1e-12, "growth source position/velocity relation");
                double speed = orb.velocity().length() * 16 / Math.sqrt(800);
                require(speed >= .1f - 1e-6 && speed < 1.1f, "growth source speed");
                float base = orb.scale() + scale * 1.5f;
                require(base >= 1.55f - 1e-6 && base < 2.75f + 1e-6 && orb.color() == 0xFF007F && orb.lifetime() == 60, "growth scale/color/life");
                if (orb.scale() < 0) negative++;
            }
        }
        require(negative > 0, "source negative scales retained");
        System.out.println("PASS: vortex growth10000 cases, exact3, source offset/speed/color/lifetime and signed scale");
        int red = 0, total = 0;
        for (int mode = 0; mode < 4; mode++) for (int sample = 0; sample < 10000; sample++) {
            var dusts = new ArrayList<SupremeCalamitasSparks.Dust>();
            SupremeCalamitasSparks.flameDust(mode, center, velocity, 16, random, dusts::add);
            require(dusts.size() == (mode == 0 ? 1 : mode == 1 ? 2 : mode == 2 ? 18 : 25), "source counts");
            for (var dust : dusts) {
                total++; if (dust.type() == 60) red++;
                require(dust.type() == 60 || dust.type() == 114, "native IDs");
                require(dust.variant() >= 0 && dust.variant() < 3 && dust.position().equals(center), "source frame/center");
                float low = mode == 0 || mode == 3 ? .9f : mode == 1 ? .7f : .75f;
                require(dust.scale() >= low && dust.scale() < (mode == 2 ? 1.3f : 1.8f), "source scale");
                if (mode == 0) {
                    double factor = dust.velocity().dot(velocity) / velocity.lengthSqr();
                    require(factor >= .1f - 1e-6 && factor < .7f && dust.velocity().cross(velocity).lengthSqr() < 1e-20, "wave old velocity relation");
                } else {
                    double speed = dust.velocity().length() * 16, base = Math.sqrt(2) * (mode == 1 ? 4 : mode == 2 ? 16 : 20);
                    require(speed >= base * .5f - 1e-6 && speed < base * 1.3f + 1e-6, "3D radial source speed");
                }
            }
        }
        require(total == 460000 && red > total * .329 && red < total * .338, "source one-third mix");
        double radialTotal = 0;
        for (int sample = 0; sample < 10000; sample++) {
            var dusts = new ArrayList<SupremeCalamitasSparks.Dust>();
            SupremeCalamitasSparks.flameDust(4, center, velocity, 16, random, dusts::add);
            require(dusts.size() == 1, "seeker/Sepulcher trail count");
            var dust = dusts.get(0);
            require(dust.type() == 182 && dust.variant() >= 0 && dust.variant() < 3, "TheDestroyer native ID/frame");
            double radius = dust.position().distanceTo(center) * 16;
            require(radius < 4 && dust.scale() >= .2f && dust.scale() < .6f, "dart offset/scale");
            radialTotal += radius;
            double factor = -dust.velocity().dot(velocity) / velocity.lengthSqr();
            require(factor >= .05f - 1e-6 && factor < .45f && dust.velocity().cross(velocity).lengthSqr() < 1e-20, "dart reverse source velocity");
        }
        require(radialTotal / 10000 > 1.95 && radialTotal / 10000 < 2.05, "source uniform radial factor");
        System.out.println("PASS: seeker/Sepulcher Dust 10000 cases, exact1 TheDestroyer182, source radius/scale/reverse speed");
        for (boolean giga : new boolean[]{false, true}) for (int sample = 0; sample < 10000; sample++) {
            var points = new ArrayList<SupremeCalamitasSparks.Spark>();
            SupremeCalamitasSparks.fireballPoints(giga, center, 16, random, points::add);
            require(points.size() == (giga ? 25 : 18), "Point source count");
            for (var point : points) {
                Vec3 offset = point.position().subtract(center);
                require(Math.abs(offset.length() * 16 - Math.sqrt(2) * (giga ? 15 : 12)) < 1e-10, "Point radial offset");
                double factor = point.velocity().length() / offset.length();
                require(factor >= .3f - 1e-6 && factor < 1 && offset.cross(point.velocity()).lengthSqr() < 1e-20, "Point shared direction/speed");
                require(point.lifetime() == 15 && point.scale() == (giga ? 1.25f : 1.1f), "Point scale/lifetime");
                require(point.color() == 0xFF0000 || point.color() == 0xFF007F, "Point source colors");
            }
        }
        System.out.println("PASS: fireball Point 20000 cases, exact18/25 count, source offset/speed/color/scale/lifetime");
        System.out.println("PASS: flame Dust 40000 cases, exact1/2/18/25 quantities, 460000 particles, native1:2 mix, frame/scale/center/3D speed");
    }
}
