package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.item.ElementsItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElementsGameTests {
    private static ServerPlayer player(GameTestHelper h){
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ElementsRegression"));
        h.getLevel().addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(1,120,1)));p.setYRot(0);p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get()));return p;
    }
    private static List<ElementsEntity> attacks(ServerPlayer p,int kind){return p.level().getEntitiesOfClass(ElementsEntity.class,p.getBoundingBox().inflate(100),e->e.ownerId()==p.getId()&&e.kind()==kind);}
    private static void clear(ServerPlayer p){for(var e:p.level().getEntitiesOfClass(ElementsEntity.class,p.getBoundingBox().inflate(100),a->a.ownerId()==p.getId()))e.discard();}
    private static void input(ServerPlayer p,boolean left,boolean right,boolean alt){ElementsCombat.input(p,new ArkNetwork.Input(left,right,alt));}
    private static void cool(ServerPlayer p){for(int i=0;i<8;i++)ElementsCombat.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    @GameTest(template="empty",timeoutTicks=40,batch="elements")
    public static void comboAndSnap(GameTestHelper h){
        ServerPlayer p=player(h);ItemStack stack=p.getMainHandItem();ElementsItem.charge(stack,10);
        for(int part=0;part<5;part++){
            input(p,true,false,false);var blade=attacks(p,ElementsEntity.BLADE).get(0);
            h.assertTrue(blade.view().getInt("Mode")== (part==4?2:part%2),"Wrong combo mode");
            h.assertTrue(attacks(p,ElementsEntity.NEEDLE).size()==(part%2==1?1:0),"Solar needle count");
            h.assertTrue(attacks(p,ElementsEntity.STAR).size()==(part%2==1?4:0),"Glass star count");
            h.assertTrue(ElementsItem.charge(stack)==9-part,"Attack charge consumption");
            if(part==4){
                Vec3 plane=ElementsEntity.vector(blade.view(),"F");for(int i=0;i<3;i++)blade.tick();
                p.setYRot(100);p.setXRot(-40);for(int i=0;i<3;i++)blade.tick();
                h.assertTrue(ElementsEntity.vector(blade.view(),"F").distanceTo(plane)<1e-9,"Throw rotated its locked plane");
                h.assertTrue(blade.position().distanceTo(p.getBoundingBox().getCenter())<=31.25+.001,"Throw exceeded reach");
                h.assertTrue(Math.abs(ElementsEntity.vector(blade.view(),"Direction").y)>.1,"Throw failed to follow 3D pitch");
                input(p,false,false,false);blade.tick();h.assertTrue(blade.view().getInt("Mode")==3,"Release did not snap");
                h.assertTrue(ElementsItem.charge(stack)==5,"Snap incorrectly granted Cosmos charge");
            }
            input(p,false,false,false);clear(p);cool(p);
        }
        p.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="elements")
    public static void parryBlastAndNativeDamage(GameTestHelper h){
        ServerPlayer p=player(h);input(p,false,true,false);h.assertTrue(ElementsCombat.cooldown(p)==340,"Parry cooldown");
        var incoming=new net.minecraftforge.event.entity.living.LivingAttackEvent(p,p.damageSources().generic(),5);ElementsCombat.incoming(incoming);
        h.assertTrue(incoming.isCanceled()&&ElementsItem.charge(p.getMainHandItem())==10,"Parry did not grant ten charges");
        var target=EntityType.COW.create(h.getLevel());target.setPos(p.position().add(0,0,7));target.setNoAi(true);h.getLevel().addFreshEntity(target);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        clear(p);input(p,false,false,false);cool(p);input(p,false,true,true);
        h.assertTrue(ElementsItem.charge(p.getMainHandItem())==0&&attacks(p,ElementsEntity.BLAST).size()==1,"Blast did not consume all charges");
        Vec3 position=p.position();var blast=attacks(p,ElementsEntity.BLAST).get(0);for(int i=0;i<12;i++)blast.tick();
        h.assertTrue(target.getHealth()<200,"Blast did not hit target");
        h.assertTrue(ArkDamage.health(target)<0,"New weapon enabled special health");
        h.assertTrue(p.position().distanceToSqr(position)<1e-12,"Elements blast moved the player");
        clear(p);p.discard();target.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="elements")
    public static void resonanceRefresh(GameTestHelper h){
        ServerPlayer p=player(h);var target=EntityType.COW.create(h.getLevel());target.setPos(p.position().add(6,0,0));target.setNoAi(true);h.getLevel().addFreshEntity(target);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.setNoGravity(true);target.noPhysics=true;
        ElementsCombat.applyResonance(target,p,20);
        for(int i=1;i<=20;i++){
            if(i==8)ElementsCombat.applyResonance(target,p,20);
            target.tick();
            h.assertTrue(Math.abs(target.getHealth()-(100-i/5))<.001,"Resonance tick "+i+" health="+target.getHealth()+" elapsed="+target.getPersistentData().getInt("ElementsDotElapsed"));
        }
        // Call only the DOT handler so normal movement/drag cannot hide an added impulse.
        Vec3 motion=new Vec3(.12,.07,-.09);target.setDeltaMovement(motion);
        target.getPersistentData().putInt("ElementsDotElapsed",4);
        float pulseHealth=target.getHealth();
        ElementsCombat.resonance(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(target));
        h.assertTrue(target.getHealth()<pulseHealth,"Resonance pulse did not damage target");
        h.assertTrue(target.getDeltaMovement().distanceToSqr(motion)<1e-12,"Resonance added knockback or erased existing motion");
        target.knockback(.4,1,0);
        h.assertTrue(target.getDeltaMovement().distanceToSqr(motion)>1e-6,"Resonance suppressed unrelated knockback");
        target.removeEffect(ArkOfCosmos.ELEMENTAL_MIX.get());ElementsCombat.resonance(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(target));
        h.assertTrue(!target.getPersistentData().contains("ElementsDotElapsed"),"Expired resonance did not clear its counter");
        ElementsCombat.applyResonance(target,p,30);float before=target.getHealth();
        for(int i=1;i<=30;i++){target.tick();h.assertTrue(Math.abs(target.getHealth()-(before-i/5))<.001,"30-tick resonance expiry lost a pulse at tick "+i);}
        target.tick();h.assertTrue(!target.hasEffect(ArkOfCosmos.ELEMENTAL_MIX.get())&&!target.getPersistentData().contains("ElementsDotElapsed"),"Expired resonance remained active");
        target.discard();p.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="elements")
    public static void glassStarWall(GameTestHelper h){
        ServerPlayer p=player(h);Vec3 at=p.getBoundingBox().getCenter();
        var wall=net.minecraft.core.BlockPos.containing(at.add(0,0,8));
        for(int x=-1;x<=1;x++)for(int y=-1;y<=2;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var frame=net.everla.projectc.arkofcosmos.util.AttackFrame.capture(p);
        var star=ElementsEntity.create(p,ElementsEntity.STAR,0,0,1.4f,at,frame.forward().scale(.8),frame);
        for(int i=0;i<12&&!star.isRemoved();i++)star.tick();
        h.assertTrue(star.isRemoved(),"Glass star ignored its delayed wall collision");
        h.assertTrue(star.getBoundingBox().maxZ<=wall.getZ()+.001,"Glass star volume crossed the wall");
        p.discard();h.succeed();
    }
}
