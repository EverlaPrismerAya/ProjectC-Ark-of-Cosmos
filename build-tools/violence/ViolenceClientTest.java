package net.everla.projectc.arkofcosmos;

import java.util.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.everla.projectc.arkofcosmos.entity.ViolenceEntity;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class ViolenceClientTest {
    static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    static final boolean PEER=System.getProperty("violence.role","host").equals("peer");
    static final boolean BSL=Boolean.getBoolean("violence.bsl");
    static final Path CONTROL=Path.of(System.getProperty("violence.control"));
    static int stage,age,total;static String shot;static Process peer;static boolean connected,sawLeft,sawRight,sawReturn,observed;
    static volatile boolean ready;static volatile Throwable failure;static UUID cow;static float health;
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void write(String key,String value){try{Files.writeString(CONTROL.resolve(key),value);}catch(Exception e){throw new RuntimeException(e);}}
    static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayerByName("ViolenceHost"));}catch(Throwable t){failure=t;}});}
    static void launchPeer()throws Exception{
        var spec=com.google.gson.JsonParser.parseString(Files.readString(CONTROL.resolve("launch.json"))).getAsJsonObject();var args=new ArrayList<String>();
        for(var arg:java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())args.add(arg.replace("-Dviolence.role=host","-Dviolence.role=peer"));
        args.add("-cp");args.add(System.getProperty("java.class.path"));args.add(spec.get("main").getAsString());
        var game=new ArrayList<String>();spec.getAsJsonArray("args").forEach(a->game.add(a.getAsString()));
        Path runtime=CONTROL.resolve("peer-runtime");Files.createDirectories(runtime);Files.copy(Minecraft.getInstance().gameDirectory.toPath().resolve("options.txt"),runtime.resolve("options.txt"),StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectories(runtime.resolve("config"));Files.writeString(runtime.resolve("config/oculus.properties"),"enableShaders=false\nshaderPack=\n");
        for(int i=0;i<game.size();i++){if(game.get(i).equals("--username"))game.set(i+1,"ViolencePeer");if(game.get(i).equals("--gameDir"))game.set(i+1,runtime.toAbsolutePath().toString());if(game.get(i).equals("--uuid"))game.set(i+1,UUID.nameUUIDFromBytes("OfflinePlayer:ViolencePeer".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
        args.addAll(game);var lines=new ArrayList<String>();for(var arg:args)lines.add("\""+arg.replace("\\","\\\\").replace("\"","\\\"")+"\"");
        Path argfile=CONTROL.resolve("peer-java.args");Files.write(argfile,lines,java.nio.charset.Charset.forName(System.getProperty("native.encoding")));
        peer=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"@"+argfile.toAbsolutePath()).directory(runtime.toFile()).redirectErrorStream(true).redirectOutput(CONTROL.resolve("peer.log").toFile()).start();
    }
    static void next(int next){stage=next;age=0;write("stage",Integer.toString(stage));}
    static void fixture(){server(p->{
        p.teleportTo(p.serverLevel(),0,8,0,Set.of(),0,0);p.setNoGravity(true);p.getAbilities().flying=true;p.onUpdateAbilities();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArkOfCosmos.VIOLENCE.get()));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)p.serverLevel().setChunkForced(x,z,true);
        var t=EntityType.COW.create(p.serverLevel());t.setPos(0,8,10);t.setNoAi(true);t.setNoGravity(true);t.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);t.setHealth(10000);t.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,20000,4));p.serverLevel().addFreshEntity(t);cow=t.getUUID();health=t.getHealth();
        var s=p.server;s.setUsesAuthentication(false);int port=net.minecraft.util.HttpUtil.getAvailablePort();try{s.getConnection().startTcpServerListener(java.net.InetAddress.getByName("127.0.0.1"),port);}catch(Exception e){throw new RuntimeException(e);}write("port",Integer.toString(port));ready=true;
    });}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(failure!=null)throw new AssertionError("server fixture",failure);require(++total<7000,"client test timeout stage "+stage);age++;
            if(total%200==0)LOG.info("VIOLENCE_CLIENT_PROGRESS peer={} stage={} age={}",PEER,stage,age);
            if(Files.exists(CONTROL.resolve("failure")))throw new AssertionError(Files.readString(CONTROL.resolve("failure")));
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){mc.options.pauseOnLostFocus=false;
                if(PEER){stage=1;write("peer-title","ready");return;}
                if(!BSL)launchPeer();next(1);var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("Violence-"+System.currentTimeMillis(),new LevelSettings("Violence validation",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(12345,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(PEER){
                if(stage==1&&!connected&&Files.exists(CONTROL.resolve("port"))){connected=true;String address="127.0.0.1:"+Files.readString(CONTROL.resolve("port")).trim();ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Violence local test",address,false),false);}
                if(mc.level!=null&&mc.player!=null){
                    if(!observed){observed=true;write("peer-joined","ready");}
                    for(var e:mc.level.entitiesForRendering())if(e instanceof ViolenceEntity a){var s=a.view();if(!s.isEmpty()){if(s.getBoolean("Javelin"))sawRight=true;else sawLeft=true;if(s.getBoolean("Returning"))sawReturn=true;}}
                    if(sawLeft)write("peer-left","synced");
                    if(Files.exists(CONTROL.resolve("host-done"))){require(sawLeft&&sawRight&&sawReturn,"remote modes and return synced");require(!mc.level.entitiesForRendering().iterator().hasNext()||mc.level.getEntitiesOfClass(ViolenceEntity.class,mc.player.getBoundingBox().inflate(256)).isEmpty(),"no remote attack residue");LOG.info("VIOLENCE_PEER_PASS independentTCP left right return cleanup");write("peer-pass","ready");stage=99;mc.stop();}
                }return;
            }
            if(stage==1&&mc.level!=null&&mc.player!=null&&!ready){next(10);fixture();return;}
            if(stage==10&&ready&&(BSL||Files.exists(CONTROL.resolve("peer-joined")))){next(2);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.mouseHandler.releaseMouse();}
            if(mc.player==null||mc.level==null||stage==1||stage==10)return;
            mc.player.setYRot(0);mc.player.setXRot(stage==4?-30:0);
            if(stage==2||stage==4)ArkNetwork.send(true,false,false);
            if(stage==2&&age==45){
                require(net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.trailShader!=null,"trail shader compiled");
                require(!BSL||net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat.shaderPackActive(),"BSL actually active");
                require(mc.level.getEntitiesOfClass(ViolenceEntity.class,mc.player.getBoundingBox().inflate(256)).size()==1,"one synced left attack");shot="left-"+(BSL?"bsl":"plain");
            }
            if(stage==2&&age>80&&(BSL||Files.exists(CONTROL.resolve("peer-left")))){ArkNetwork.send(false,false,false);next(3);server(p->{var t=(LivingEntity)p.serverLevel().getEntity(cow);require(t.getHealth()<health,"natural left hits real cow");LOG.info("VIOLENCE_LEFT_DAMAGE_PASS damage={}",health-t.getHealth());});}
            if(stage==3&&age>30){require(mc.level.getEntitiesOfClass(ViolenceEntity.class,mc.player.getBoundingBox().inflate(256)).isEmpty(),"left recovered");next(4);}
            if(stage==4&&age==35){shot="aim3d-"+(BSL?"bsl":"plain");require(mc.level.getEntitiesOfClass(ViolenceEntity.class,mc.player.getBoundingBox().inflate(256)).stream().anyMatch(a->a.getY()>mc.player.getY()+3),"upward three dimensional steering");}
            if(stage==4&&age>50){ArkNetwork.send(false,false,false);next(5);}
            if(stage==5&&age==30){server(p->{var t=(LivingEntity)p.serverLevel().getEntity(cow);health=t.getHealth();for(int x=-2;x<=2;x++)for(int y=7;y<=11;y++)p.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,5),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());});}
            if(stage==5&&age>40){next(6);mc.options.setCameraType(CameraType.FIRST_PERSON);ArkNetwork.send(false,true,false);}
            if(stage==6&&age==2)ArkNetwork.send(false,false,false);
            if(stage==6&&age==6)shot="throw-"+(BSL?"bsl":"plain");
            if(stage==6&&age>55){server(p->{var t=(LivingEntity)p.serverLevel().getEntity(cow);require(t.getHealth()<health,"natural thrown attack penetrated wall");require(net.everla.projectc.arkofcosmos.combat.BossHealingLock.active(t)==false,"no boss healing lock");LOG.info("VIOLENCE_THROW_DAMAGE_PASS damage={}",health-t.getHealth());});next(7);}
            if(stage==7&&age>15){require(mc.level.getEntitiesOfClass(ViolenceEntity.class,mc.player.getBoundingBox().inflate(256)).isEmpty(),"no host attack residue");write("host-done","ready");next(8);}
            if(stage==8&&(BSL||Files.exists(CONTROL.resolve("peer-pass"))&&peer!=null&&!peer.isAlive())){LOG.info("VIOLENCE_CLIENT_PASS BSL={} realInput snapshots 3DAim wallPenetration recovery resources screenshots multiplayer={}",BSL,!BSL);stage=99;mc.stop();}
        }catch(Throwable t){LOG.error("VIOLENCE_CLIENT_FAIL peer="+PEER,t);write("failure",t.toString());stage=99;if(peer!=null&&peer.isAlive())peer.destroy();mc.stop();}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||shot==null)return;
        try{var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());image.writeToFile(CONTROL.resolve(shot+".png"));image.close();LOG.info("VIOLENCE_SCREENSHOT {}",shot);shot=null;}catch(Exception e){failure=e;}
    }
}

