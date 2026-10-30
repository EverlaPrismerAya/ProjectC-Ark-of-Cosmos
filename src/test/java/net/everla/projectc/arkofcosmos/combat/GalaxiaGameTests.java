package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.GalaxiaEntity;
import net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GalaxiaGameTests {
    @GameTest(template="empty",timeoutTicks=40,batch="galaxia")
    public static void modes(GameTestHelper h) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GalaxiaRegression"));
        h.getLevel().addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(1,40,1)));p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArkOfCosmos.GALAXIA.get()));
        h.assertTrue(Math.abs(GalaxiaRules.throwCurve(.45)-1)<1e-8,"Phoenix launch/hold boundary");
        h.assertTrue(Math.abs(GalaxiaRules.throwCurve(1)+.05)<1e-8,"Phoenix retraction overshoot");
        h.assertTrue(GalaxiaRules.lungeDuration(90)==14 && GalaxiaRules.lungeDuration(360)==44,"Andromeda dash duration");
        for(int mode=0;mode<4;mode++) {
            final int selected=mode;
            GalaxiaCombat.input(p,false,selected);GalaxiaCombat.input(p,true,-1);
            GalaxiaEntity blade=entities(p,GalaxiaEntity.BLADE).get(0);
            h.assertTrue(blade.view().getInt("Mode")==mode,"Attunement selection mismatch");
            if(mode==2 || mode==3) {
                for(int yaw:new int[]{73,179,-150}) {
                    p.setYRot(yaw);p.setXRot(-51);blade.tick();
                    var rendered=blade.renderState(.5f);
                    var f=new net.everla.projectc.arkofcosmos.util.AttackFrame(GalaxiaEntity.vector(rendered,"F"),GalaxiaEntity.vector(rendered,"R"));
                    Vec3 expected=GalaxiaEntity.vector(rendered,"Direction");
                    h.assertTrue(f.direction(rendered.getFloat("Rotation")).distanceTo(expected)<1e-6,"Charging blade failed to follow 3D aim");
                    h.assertTrue(Math.abs(f.forward().dot(f.right()))<1e-6,"Charging blade frame is not orthogonal");
                }
                p.setYRot(0);p.setXRot(0);blade.tick();
            }
            if(mode==0) {
                p.setYRot(73);p.setXRot(-51);blade.tick();
                h.assertTrue(GalaxiaEntity.vector(blade.view(),"F").distanceTo(p.getLookAngle().normalize())<1e-6,"Phoenix plane did not follow live yaw and pitch");
                Vec3 forward=GalaxiaEntity.vector(blade.view(),"F"),right=GalaxiaEntity.vector(blade.view(),"R");
                h.assertTrue(Math.abs(GalaxiaEntity.vector(blade.view(),"Direction").dot(forward.cross(right)))<1e-6,"Phoenix rotation escaped its updated plane");
                p.setYRot(0);p.setXRot(0);
                for(int i=0;i<60;i++)blade.tick();
                h.assertTrue(blade.view().getInt("Charge")>=300,"Phoenix did not reach throw threshold");
                int before=entities(p,GalaxiaEntity.BOLT).size();
                GalaxiaCombat.input(p,false,-1);blade.tick();
                h.assertTrue(blade.view().getInt("State")==1,"Phoenix did not enter throw state");
                h.assertTrue(entities(p,GalaxiaEntity.BOLT).size()==before+6,"Phoenix throw must emit exactly six bolts");
                Vec3 releasedForward=GalaxiaEntity.vector(blade.view(),"F"),releasedDirection=GalaxiaEntity.vector(blade.view(),"Direction");
                p.setYRot(150);p.setXRot(70);blade.tick();
                h.assertTrue(GalaxiaEntity.vector(blade.view(),"F").distanceTo(releasedForward)<1e-8
                        && GalaxiaEntity.vector(blade.view(),"Direction").distanceTo(releasedDirection)<1e-8,"Released Phoenix must retain its plane and trajectory");
                p.setYRot(0);p.setXRot(0);
            } else if(mode==1) {
                for(int i=0;i<15;i++)blade.tick();
                h.assertTrue(entities(p,GalaxiaEntity.CHAIN).size()==1,"Aries constellation missing or duplicated");
                h.assertTrue(blade.position().distanceTo(p.getBoundingBox().getCenter())<=650/16d+.001,"Aries exceeded source reach");
            } else if(mode==2) {
                for(int i=0;i<50;i++)blade.tick();
                GalaxiaCombat.input(p,false,-1);blade.tick();
                h.assertTrue(blade.isRemoved() && entities(p,GalaxiaEntity.STAR).size()==1,"Polaris release must create one star");
                Vec3 original=p.position();
                entities(p,GalaxiaEntity.STAR).get(0).setPos(p.getBoundingBox().getCenter().add(0,0,20));
                net.minecraft.core.BlockPos wall=net.minecraft.core.BlockPos.containing(original.add(0,0,3));
                for(int x=-1;x<=1;x++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                GalaxiaCombat.input(p,true,-1);GalaxiaEntity dash=entities(p,GalaxiaEntity.BLADE).get(0);dash.tick();
                h.assertTrue(p.getBoundingBox().maxZ<=wall.getZ()+.001,"Polaris dash crossed a solid wall");
                h.assertTrue(entities(p,GalaxiaEntity.SLASH).size()==1,"Blocked Polaris dash must settle its path slash once");
                dash.tick();h.assertTrue(entities(p,GalaxiaEntity.SLASH).size()==1,"Dash completion duplicated its slash");
                for(int x=-1;x<=1;x++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                p.setPos(original);
            } else {
                for(int i=0;i<60;i++)blade.tick();
                h.assertTrue(blade.view().getInt("Charge")==360,"Andromeda charge ceiling");
                h.assertTrue(entities(p,GalaxiaEntity.BOLT).size()==24,"Andromeda thresholds must emit 5+5+5+9 bolts");
                Vec3 original=p.position();net.minecraft.core.BlockPos wall=net.minecraft.core.BlockPos.containing(original.add(0,0,3));
                for(int x=-1;x<=1;x++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                GalaxiaCombat.input(p,false,-1);blade.tick();
                h.assertTrue(blade.isRemoved(),"Andromeda sword-tip terrain impact did not end lunge");
                h.assertTrue(entities(p,GalaxiaEntity.SURFACE_WAVE).size()==1,"Andromeda impact must launch a surface ring controller");
                h.assertTrue(p.position().distanceTo(original)<.01,"Sword-tip impact must precede player displacement");
                // A complete impact surface must preserve the source's five pairs, not scale count with circumference.
                for(int x=-52;x<=52;x++)for(int y=-52;y<=52;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for(int generation=0;generation<5;generation++) {
                    var waves=entities(p,GalaxiaEntity.SURFACE_WAVE);
                    h.assertTrue(waves.size()==1,"Missing Andromeda wave generation");
                    var wave=waves.get(0);
                    for(int tick=0;tick<20 && !wave.isRemoved();tick++)wave.tick();
                    h.assertTrue(entities(p,GalaxiaEntity.SPROUT).size()==2*(generation+1),"Each wave generation must add exactly two source eruptions");
                }
                h.assertTrue(entities(p,GalaxiaEntity.SURFACE_WAVE).isEmpty(),"Full charge exceeded five source generations");
                int boltsBefore=entities(p,GalaxiaEntity.BOLT).size();
                for(var sprout:entities(p,GalaxiaEntity.SPROUT))for(int tick=0;tick<10;tick++)sprout.tick();
                h.assertTrue(entities(p,GalaxiaEntity.BOLT).size()==boltsBefore+10,"Full charge shockwave must emit exactly ten bolts");
                for(int x=-52;x<=52;x++)for(int y=-52;y<=52;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                for(int x=-1;x<=1;x++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            GalaxiaCombat.input(p,false,-1);
            h.getLevel().getEntitiesOfClass(GalaxiaEntity.class,p.getBoundingBox().inflate(200)).forEach(GalaxiaEntity::discard);
        }
        p.discard();h.succeed();
    }
    private static List<GalaxiaEntity> entities(net.minecraft.world.entity.player.Player p,int kind) {
        return p.level().getEntitiesOfClass(GalaxiaEntity.class,p.getBoundingBox().inflate(200),e->e.ownerId()==p.getId() && e.kind()==kind);
    }
}
