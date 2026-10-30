package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.world.phys.Vec3;

/** Numerical source checkpoints, runnable without a window or GL context. */
public final class ArkFxCheck {
    public static void main(String[] args) {
        checkAttackSnapshots();
        checkElementsFx();
        checkClientSubscriberNames();
        // Camera turns must not project a 3D chain segment onto its old attack plane.
        Vec3 segment=new Vec3(4,7,-9);
        for(Vec3 normal: new Vec3[]{new Vec3(0,0,1),new Vec3(1,0,0),segment.normalize()}){
            Vec3 side=Math.abs(normal.y)<.9?normal.cross(new Vec3(0,1,0)).normalize():new Vec3(1,0,0);
            AttackFrame camera=new AttackFrame(side,normal.cross(side));
            AttackFrame ribbon=GalaxiaRenderer.lineFrame(segment,camera);
            double angle=ribbon.angle(segment)+Math.PI/2;
            Vec3 drawn=ribbon.direction(angle+Math.PI/2).scale(-segment.length());
            near(drawn.distanceTo(segment),0);
        }
        checkGalaxiaSubsteps();
        checkGalaxiaResources();
        if (!ArkFxDraw.ownerFirstPerson(7,7,true,true)
                || ArkFxDraw.ownerFirstPerson(7,8,true,true)
                || ArkFxDraw.ownerFirstPerson(7,7,false,true)
                || ArkFxDraw.ownerFirstPerson(7,7,true,false)
                || ArkFxDraw.ownerFirstPerson(-1,-1,true,true))
            throw new AssertionError("Near fade must only affect the owner in first person");
        near(ArkFxDraw.nearOpacity(0), .12);
        near(ArkFxDraw.nearOpacity(.5), .12);
        near(ArkFxDraw.nearOpacity(1.75), .56);
        near(ArkFxDraw.nearOpacity(3), 1);
        near(ArkFxDraw.nearOpacity(30), 1);
        ArkFxDraw.fadeEye = Vec3.ZERO;
        near(ArkFxDraw.opacityAt(Vec3.ZERO), .12);
        ArkFxDraw.fadeEye = null;
        near(ArkFxDraw.opacityAt(Vec3.ZERO), 1);
        float[] half=ArkEffects.mul(ArkEffects.rgb(210,105,30),.5);
        near(half[0],105d/255);near(half[3],.5);
        float[] green=ArkEffects.shift(ArkEffects.rgb(255,0,0),1d/3);
        near(green[0],0);near(green[1],1);near(green[2],0);
        AttackFrame plane=new AttackFrame(new Vec3(1,0,0),new Vec3(0,1,0));
        ArkFxParticle spark=new ArkFxParticle(ArkFxParticle.Type.SPARKLE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,20,plane);
        spark.spin=.1;spark.rotation=0;spark.update();
        near(spark.position.x,1);near(spark.velocity.x,.95);near(spark.opacity,Math.sin(Math.PI/20));near(spark.rotation,.1);
        ArkFxParticle smoke=new ArkFxParticle(ArkFxParticle.Type.SMOKE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,10,plane);
        smoke.opacity=.28;smoke.update();near(smoke.scale,1.01);near(smoke.opacity,.2744);near(smoke.velocity.x,.85);
        smoke.update();near(smoke.scale,1.01*.975);
        ArkFxParticle flare=new ArkFxParticle(ArkFxParticle.Type.FLARE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,20,plane);
        flare.delay=2;flare.update();flare.update();near(flare.time,0);near(flare.position.length(),0);
        flare.update();near(flare.time,1);near(flare.position.x,1);
        ArkFxParticle ring=new ArkFxParticle(ArkFxParticle.Type.RING,Vec3.ZERO,Vec3.ZERO,ArkEffects.WHITE,.05,8,plane);
        ring.originalScale=.05;ring.finalScale=1.8;ring.update();near(ring.scale,.05+1.75*(1-Math.pow(.875,4)));
        if(ArkFxDraw.FORMAT.getVertexSize()!=28)throw new AssertionError("Position/color/float3 UV stride");
        var front=new AttackFrame(new Vec3(0,0,1),new Vec3(-1,0,0));
        var back=new AttackFrame(new Vec3(0,0,-1),new Vec3(1,0,0));
        var middle=AttackFrame.interpolateView(front,back,.5);
        near(middle.forward().length(),1);near(middle.right().length(),1);
        near(middle.forward().dot(middle.right()),0);near(middle.forward().z,0);
        var vertical=AttackFrame.interpolateView(front,new AttackFrame(new Vec3(0,1,0),new Vec3(-1,0,0)),1);
        near(vertical.forward().y,1);near(vertical.normal().length(),1);
        for(int mode=0;mode<4;mode++) {
            int start=net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.tooltipColor(mode);
            if(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.attunementColor(mode,-Math.PI/2)!=start)
                throw new AssertionError("Galaxia attunement color must reach source primary color");
        }
        if(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.attunementColor(0,Math.PI/2)!=0xFF8F00)
            throw new AssertionError("Phoenix source secondary tooltip color");
        near(GalaxiaAudio.volume("book",2.5f),1);
        near(GalaxiaAudio.volume("impact",1.5f),1);
        near(GalaxiaAudio.volume("lightning",.4f),.4);
        near(GalaxiaAudio.volume("hit30",1),.5);
        near(GalaxiaAudio.volume("item120",.5f),.5);
        near(GalaxiaAudio.pitch("item90",0),Math.pow(2,-.06));
        near(GalaxiaAudio.pitch("hit43",1),Math.pow(2,.1));
        near(GalaxiaAudio.pitch("book",0),1);
        System.out.println("Ark FX source checkpoints passed");
    }
    private static void checkAttackSnapshots() {
        var history = new java.util.ArrayList<net.minecraft.nbt.CompoundTag>();
        for(int age=6;age>=0;age--){
            var point=new net.minecraft.nbt.CompoundTag();point.putInt("Age",age);point.putDouble("PositionX",age*2);history.add(point);
        }
        near(GalaxiaRenderer.historyPosition(history,3).x,6);
        near(GalaxiaRenderer.historyPosition(history,3.5).x,7);
        near(GalaxiaRenderer.historyPosition(history,4).x,8);
        near(GalaxiaRenderer.historyPosition(history,-1).x,0);
        near(GalaxiaRenderer.historyPosition(history,7).x,12);
        for (String clock : new String[]{"Age", "Steps"}) {
            var latch = new net.everla.projectc.arkofcosmos.entity.AttackRenderSnapshot();
            var incoming = trajectory(clock, 3, 0, 3);
            latch.initialize(incoming);
            incoming.putDouble("PositionX", 999);
            near(latch.state().getDouble("PositionX"), 3);
            // First client tick must not replay the path already initialized at spawn.
            latch.tick(trajectory(clock, 3, 0, 3), clock);
            for (float partial : new float[]{0, .5f, 1}) {
                near(net.everla.projectc.arkofcosmos.entity.AttackRenderSnapshot.partial(latch.state(), partial), 1);
                near(latch.state().getDouble("PositionX"), 3);
            }
            incoming = trajectory(clock, 6, 3, 6);
            latch.tick(incoming, clock);
            for (float partial : new float[]{0, .5f, 1})
                near(net.everla.projectc.arkofcosmos.entity.AttackRenderSnapshot.partial(latch.state(), partial), partial);
            var late = trajectory(clock, 9, 6, 9);
            latch.initialize(late); // A mid-tick packet cannot replace the active trajectory.
            near(latch.state().getInt(clock), 6);
            incoming.getList("Samples", 10).getCompound(0).putDouble("PositionX", 999);
            near(latch.state().getList("Samples", 10).getCompound(0).getDouble("PositionX"), 3);
            for (int tick = 0; tick < 4; tick++) {
                latch.tick(trajectory(clock, 6, 3, 6), clock);
                if (!latch.state().getList("Samples", 10).isEmpty())
                    throw new AssertionError("Unchanged snapshots must not replay or emit particles");
                near(latch.state().getDouble("PositionX"), 6);
            }
            if(clock.equals("Age")) {
                var effects=trajectory(clock,6,3,6);
                var path=effects.getList("Samples",10);
                for(int i=0;i<path.size();i++){
                    var point=path.getCompound(i);point.putInt("Age",5+i);point.putInt("Kind",-1);
                    point.putDouble("FZ",1);point.putDouble("RX",1);
                }
                int id=-1001;
                GalaxiaEffects.consume(id,effects,-1);
                int count=GalaxiaEffects.visual(id).history.size();
                GalaxiaEffects.consume(id,effects,-1);
                if(count!=1 || GalaxiaEffects.visual(id).history.size()!=count)
                    throw new AssertionError("Duplicate logical steps must not emit again");
                GalaxiaEffects.clear();
            }
            latch.tick(late, clock);
            near(latch.state().getInt(clock), 9);
            near(latch.state().getList("Samples", 10).getCompound(0).getDouble("PositionX"), 6);
            near(latch.state().getList("Samples", 10).getCompound(1).getDouble("PositionX"), 9);
        }
    }
    private static net.minecraft.nbt.CompoundTag trajectory(String clock, int age, double start, double end) {
        var state = new net.minecraft.nbt.CompoundTag();
        state.putInt(clock, age); state.putDouble("PositionX", end);
        var samples = new net.minecraft.nbt.ListTag();
        for (double position : new double[]{start, end}) {
            var sample = new net.minecraft.nbt.CompoundTag();
            sample.putDouble("PositionX", position); samples.add(sample);
        }
        state.put("Samples", samples); return state;
    }
    private static void checkElementsFx(){
        near(ElementsRenderer.needleOutlineRadius(0),0);near(ElementsRenderer.needleOutlineRadius(15),2);near(ElementsRenderer.needleOutlineRadius(30),3);
        float[] tint=ElementsRenderer.needleTint(30);near(tint[0],1);near(tint[1],0);near(tint[2],69/255d);
        near(ElementsAudio.attenuation(0),1);near(ElementsAudio.attenuation(78.125),.5);near(ElementsAudio.attenuation(156.25),0);
        near(ElementsAudio.pitch("item63",0),Math.pow(2,-.06));near(ElementsAudio.pitch("item35",0),1);near(ElementsAudio.pitch("phoenix",1),1);
        if(!ElementsAudio.unlimited("elements_item9")||!ElementsAudio.unlimited("needle_death")||ElementsAudio.unlimited("item71"))throw new AssertionError("Source sound concurrency");
        AttackFrame f=new AttackFrame(new Vec3(1,0,0),new Vec3(0,1,0));
        var dust=new ElementsVanillaParticles.Particle(66,Vec3.ZERO,new Vec3(1,1,0),1,ArkEffects.WHITE,f,-1);dust.noGravity=true;dust.update();
        near(dust.at.x,1);near(dust.scale,.97);near(dust.velocity.x,.98*.99*.92);near(dust.velocity.y,.98*.92);
        var spatial=new java.util.Random(42);
        double normalEnergy=0;
        for(int i=0;i<64;i++){
            Vec3 velocity=ElementsVanillaParticles.spread(new Vec3(1,2,0),spatial);
            near(velocity.length(),Math.sqrt(5));normalEnergy+=velocity.z*velocity.z;
        }
        if(normalEnergy<1)throw new AssertionError("Dust/Gore must spread outside the attack plane");
        var spatialDust=new ElementsVanillaParticles.Particle(66,Vec3.ZERO,new Vec3(0,0,1),1,ArkEffects.WHITE,f,-1);
        spatialDust.noGravity=true;spatialDust.update();near(spatialDust.velocity.z,.98*.99*.92);
        spatialDust.update();near(spatialDust.at.z,1+.98*.99*.92);
        var holo=new ElementsVanillaParticles.Particle(-1,Vec3.ZERO,new Vec3(1,0,0),2,ArkEffects.WHITE,f,-1);holo.life=10;holo.update();
        near(holo.at.x,1);near(holo.opacity,Math.sqrt(.1));near(holo.scale,1.92);near(holo.velocity.x,.875);
        var gore=new ElementsVanillaParticles.Particle(16,Vec3.ZERO,new Vec3(1,0,0),.7,ArkEffects.WHITE,f,-1);gore.alpha=100;gore.update();near(gore.scale,.69);near(gore.at.x,.98);
        int steps=1;while(gore.update()&&steps<100)steps++;if(steps<58||steps>61)throw new AssertionError("Source Gore shrink lifetime");
    }
    private static void checkClientSubscriberNames() {
        var factory = new net.minecraftforge.eventbus.ClassLoaderFactory();
        var names = new java.util.HashMap<String, java.lang.reflect.Method>();
        for (Class<?> subscriber : new Class<?>[]{
                net.everla.projectc.arkofcosmos.client.ArkClient.Registration.class,
                net.everla.projectc.arkofcosmos.client.GalaxiaClient.GalaxiaRegistration.class}) {
            for (var method : subscriber.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(net.minecraftforge.eventbus.api.SubscribeEvent.class)) continue;
                String name = factory.getUniqueName(method);
                var previous = names.putIfAbsent(name, method);
                if (previous != null) throw new AssertionError("Forge callback name collision: " + previous + " and " + method);
            }
        }
    }
    private static void checkGalaxiaSubsteps(){
        for(int count:new int[]{3,6,12}){
            var state=new net.minecraft.nbt.CompoundTag();var path=new net.minecraft.nbt.ListTag();
            for(int i=0;i<=count;i++){
                var s=new net.minecraft.nbt.CompoundTag();s.putInt("Age",i);s.putInt("Kind",net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.CHAIN);
                s.putDouble("FX",1);s.putDouble("RY",1);path.add(s);
            }
            state.put("Samples",path);
            for(int phase=0;phase<3;phase++)GalaxiaEffects.consume(count,state,phase);
            var visual=GalaxiaEffects.visual(count);
            if(visual.step!=count||visual.history.size()!=count)throw new AssertionError("Missing Galaxia substeps: "+count);
            for(int phase=0;phase<3;phase++)GalaxiaEffects.consume(count,state,phase);
            GalaxiaEffects.consume(count,state,-1);
            if(visual.history.size()!=count)throw new AssertionError("Duplicate Galaxia snapshot emitted twice");
        }
        GalaxiaEffects.clear();
        var state=new net.minecraft.nbt.CompoundTag();var path=new net.minecraft.nbt.ListTag();
        for(int i=0;i<3;i++){
            var sample=new net.minecraft.nbt.CompoundTag();
            sample.putInt("Age",600+i);sample.putInt("Kind",0);sample.putInt("Mode",0);sample.putInt("Charge",600);sample.putFloat("Scale",2.5f);
            sample.putDouble(i<2?"FX":"FZ",1);sample.putDouble(i<2?"RY":"RX",1);
            sample.putDouble(i<2?"DirectionX":"DirectionZ",1);path.add(sample);
        }
        state.put("Samples",path);GalaxiaEffects.consume(999,state,-1);
        var smear=GalaxiaEffects.visual(999).smear;
        if(smear==null || smear.plane.forward().distanceTo(new Vec3(0,0,1))>1e-8)
            throw new AssertionError("Charged Phoenix smear retained its creation plane after turning");
        GalaxiaEffects.clear();ArkEffects.particles.clear();
    }
    private static void checkGalaxiaResources(){
        for(String name:new String[]{"galaxia/galaxiaextra","galaxia/galaxiaextra2","galaxia/galaxiabolt",
                "galaxia/galaxiacancer","galaxia/galaxiacapricorn","galaxia/bloomring","galaxia/phoenixchain",
                "galaxia/hollowcirclesoftedge","sparkle","bloomcircle","bloomline","bloomlinecap","thinsparkle",
                "heavysmoke","circularsmearsmokey","thinendedline"}){
            var path=java.nio.file.Path.of("src/main/resources/assets/arkofcosmos/"+ArkFxDraw.texture(name).getPath());
            if(!java.nio.file.Files.isRegularFile(path))throw new AssertionError("Missing Galaxia visual resource: "+path);
        }
        try{
            var manifest=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("licenses/RESOURCE-SOURCES.json"))).getAsJsonObject();
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(var element:manifest.getAsJsonArray("files")){
                var entry=element.getAsJsonObject();String file=entry.get("file").getAsString();
                if(!file.contains("/textures/galaxia/")&&!file.endsWith("galaxia_tint.fsh"))continue;
                String hash=java.util.HexFormat.of().formatHex(digest.digest(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(file))));
                if(!hash.equalsIgnoreCase(entry.get("sha256").getAsString()))throw new AssertionError("Galaxia resource hash changed: "+file);
            }
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static void near(double actual,double expected){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(actual+" != "+expected);}
}
