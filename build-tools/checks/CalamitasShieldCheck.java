import net.everla.projectc.arkofcosmos.calamity.*;
import net.minecraft.world.phys.Vec3;

public final class CalamitasShieldCheck {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void near(float actual,double expected,String message){require(Math.abs(actual-expected)<.00002,message+": "+actual);}
    public static void main(String[] args){
        var encounter=new SupremeCalamitasEncounter();var combat=new SupremeCalamitasCombat();var shield=new SupremeCalamitasShield();Vec3 x=new Vec3(1,0,0);
        encounter.restore(new SupremeCalamitasEncounter.State(SupremeCalamitasEncounter.Phase.COMBAT,3,0,1,true,false,false,0));
        var c=combat.save();c.putInt("Mode",3);c.putInt("Elapsed",450);c.putBoolean("WillCharge",true);combat.load(c);
        var early=new SupremeCalamitasShield();c.putInt("Elapsed",100);combat.load(c);early.step(encounter,combat,.4f,x,x);require(early.save().getBoolean("WillCharge") && !early.save().getBoolean("Preparing"),"source charge palette precedes final30frame skull warning");c.putInt("Elapsed",450);combat.load(c);
        shield.step(encounter,combat,.4f,new Vec3(1,0,1),x);var first=shield.save();require(first.getInt("HitboxWidth")==152 && first.getInt("HitboxHeight")==152,"pre-lerp integer source dimensions");near(first.getFloat("Scale"),.956,"source charge forcefield lerp");near(first.getFloat("ShieldOpacity"),1,"source initial skull alpha");require(first.getBoolean("Preparing"),"last30frames prepare charge");
        shield.lockDash(new Vec3(1,2,3));var locked=shield.save();c.putInt("Mode",2);c.putInt("Elapsed",1);combat.load(c);
        for(int frame=0;frame<100;frame++)shield.step(encounter,combat,.4f,new Vec3(-5,9,-7),x);
        var after=shield.save();require(after.getInt("HitboxWidth")==68 && after.getInt("HitboxHeight")==68,"charged integer source dimensions");near((float)after.getDouble("DirectionX"),locked.getDouble("DirectionX"),"dash skull direction remains locked");near(after.getFloat("Scale"),.45+(.956-.45)*Math.pow(.92,100),"source exponential charge shrink");
        encounter.restore(new SupremeCalamitasEncounter.State(SupremeCalamitasEncounter.Phase.COMBAT,4,0,1,true,false,true,0));
        shield=new SupremeCalamitasShield();
        for(int i=0;i<2082;i++){
            shield.step(encounter,combat,.3f,x,x);
            var copy=new SupremeCalamitasShield();copy.load(shield.save());require(copy.save().equals(shield.save()),"every source-frame shield NBT roundtrip");shield=copy;
        }
        require(shield.save().getBoolean("PostMusic"),"music cue at2082eligibleframes");near(shield.save().getFloat("Opacity"),.7,"music cue opacity");near(shield.save().getFloat("VisualScale"),1.5,"charging retains cue scale");
        encounter.restore(new SupremeCalamitasEncounter.State(SupremeCalamitasEncounter.Phase.HEART_CAST,5,0,1,true,true,true,0));var paused=shield.save().getInt("Music");shield.step(encounter,combat,.01f,x,x);require(shield.save().getInt("Music")==paused,"heart cast skips music region");near(shield.save().getFloat("Opacity"),.6,"one percent opacity");
        shield.step(encounter,combat,.9f,x,x);near(shield.save().getFloat("Opacity"),.6,"native healing preserves prior source visual opacity");
        encounter.restore(new SupremeCalamitasEncounter.State(SupremeCalamitasEncounter.Phase.ACCEPTANCE,5,0,2,true,true,true,3));
        for(int i=0;i<400;i++)shield.step(encounter,combat,.0001f,x,x);
        require(shield.save().getFloat("Scale")<1e-12 && shield.save().getFloat("ShieldOpacity")<1e-12,"acceptance fades both shield geometries");
        require(shield.hitboxWidth()==42 && shield.hitboxHeight()==44,"source acceptance minimum dimensions");
        System.out.println("PASS: shield source lerps, final30frame charge warning, locked 3D dash, 2082 source cue/NBT resumes, cast pause, native-healing visual persistence and acceptance shrink");
    }
}
