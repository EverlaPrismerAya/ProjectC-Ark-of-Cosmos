package net.everla.projectc.arkofcosmos.revengeance;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public class CombatBoostClientTest {
    static int stage,age,total;static volatile boolean ready;static volatile Throwable failure;
    static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));ready=true;}catch(Throwable e){failure=e;}});}
    static void refill(net.minecraft.server.level.ServerPlayer p){p.removeEffect(RevengeanceModMobEffects.RAGE_EFFECT.get());p.removeEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get());p.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).setBaseValue(100);p.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(10000);}
    static void both(net.minecraft.server.level.ServerPlayer p){check(p.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()),"rage activated through real packet");check(p.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get()),"adrenaline activated through real packet");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();age++;total++;
        try{
            if(failure!=null)throw new AssertionError("server check failed",failure);
            if(total>5000)throw new AssertionError("client validation timeout at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                check(CombatBoostKeys.RAGE.getKey().getValue()==GLFW.GLFW_KEY_V,"default rage V");check(CombatBoostKeys.ADRENALINE.getKey().getValue()==GLFW.GLFW_KEY_B,"default adrenaline B");
                stage=1;age=0;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.options.pauseOnLostFocus=false;
                mc.createWorldOpenFlows().createFreshLevel("Boost-"+System.currentTimeMillis(),new LevelSettings("Boost validation",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(12345,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){ready=false;stage=2;age=0;server(p->{var boss=net.minecraft.world.entity.EntityType.WITHER.create(p.level());boss.setPos(p.position().add(5,0,0));boss.setNoAi(true);p.level().addFreshEntity(boss);refill(p);});return;}
            if(stage==2&&ready&&age>20){stage=3;age=0;var key=InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B);CombatBoostKeys.RAGE.setKey(key);CombatBoostKeys.ADRENALINE.setKey(key);net.minecraft.client.KeyMapping.resetMapping();CombatBoostKeys.keyboard(new InputEvent.Key(GLFW.GLFW_KEY_B,0,GLFW.GLFW_PRESS,0));return;}
            if(stage==3&&age>15){ready=false;stage=4;age=0;server(p->{both(p);refill(p);});return;}
            if(stage==4&&ready&&age>20){stage=5;age=0;var key=InputConstants.Type.MOUSE.getOrCreate(4);CombatBoostKeys.RAGE.setKey(key);CombatBoostKeys.ADRENALINE.setKey(key);net.minecraft.client.KeyMapping.resetMapping();CombatBoostKeys.mouse(new InputEvent.MouseButton.Post(4,GLFW.GLFW_PRESS,0));return;}
            if(stage==5&&age>15){ready=false;stage=6;age=0;server(CombatBoostClientTest::both);return;}
            if(stage==6&&ready){LOG.info("COMBAT_BOOST_CLIENT_PASS defaults_V_B shared_keyboard shared_mouse network_dual_activation");stage=99;mc.stop();}
        }catch(Throwable e){LOG.error("COMBAT_BOOST_CLIENT_FAIL stage="+stage,e);stage=99;mc.stop();}
    }
}
