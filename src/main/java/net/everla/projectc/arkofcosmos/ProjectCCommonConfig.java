package net.everla.projectc.arkofcosmos;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import static net.everla.projectc.arkofcosmos.entity.MahouAttackEntity.*;

/** Forge watches the common TOML; snapshots also synchronize item panels and boss bars. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class ProjectCCommonConfig {
    private ProjectCCommonConfig() {}
    public static final ForgeConfigSpec SPEC;
    private static final Map<String,ForgeConfigSpec.ConfigValue<? extends Number>> VALUES=new LinkedHashMap<>();
    private static volatile Map<String,Double> current;
    private static volatile boolean remoteValues;
    private static volatile net.minecraftforge.fml.config.ModConfig loadedConfig;
    private static volatile java.nio.file.attribute.FileTime modified;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.push("boss").push("mahou");
        define(b,"health",1000,1,1000000000,"Maximum health");
        define(b,"phase_two_threshold",.5,0,.999,"Remaining health ratio for transitioning from phase one to phase two");
        define(b,"phase_three_threshold",.25,0,.999,"Remaining health ratio for transitioning from phase two to phase three");
        define(b,"summon_threshold",.5,0,1,"Remaining health ratio for summoning the shikigami in phase three");
        define(b,"skill_damage_multiplier",1,0,100,"Base skill damage multiplier");
        b.push("skills");
        for(String skill:new String[]{"pillar","wave","bloom","gravity","void_vortex","red","blue","fusion","purple","blue_dash","domain_text","blade","needle","star","blast","galaxia_blade","galaxia_bolt","galaxia_star","resonance","other"})
            define(b,skill,1,0,100,"Damage multiplier for this skill, multiplied by skill_damage_multiplier");
        b.pop();
        b.pop().push("fourth_calamity");
        var bars=b.comment("Number of health bars. Total health = health_bars * bar_health").defineInRange("health_bars",44,1,10000);
        VALUES.put(String.join(".",bars.getPath()),bars);
        define(b,"bar_health",20,1,1000000,"Health per bar");
        define(b,"skill_damage_multiplier",1,0,100,"Attack damage multiplier, including vanilla melee attacks, projectiles, and end crystal explosions");
        b.push("skills");
        for(String skill:new String[]{"melee","spear","mace","arrow","trident","wind_charge","explosion","other"})
            define(b,skill,1,0,100,"Damage multiplier for this attack type, multiplied by skill_damage_multiplier");
        b.pop();
        b.pop().push("calamitas");
        define(b,"enchant_cost",32,0,4096,"Emerald cost for applying one witch curse");
        define(b,"disenchant_cost",8,0,4096,"Emerald cost for removing one witch curse");
        define(b,"apollyon_replacement_chance",.01,0,1,"Chance to replace a newly summoned Apollyon in the Nether with Calamitas; 0 disables replacement, 1 always replaces. Loaded entities are never rerolled");
        define(b,"health_multiplier",.002,.0002,100,"Source health conversion; shared by Calamitas and her summons. Calamitas maximum health is at least 100");
        define(b,"damage_multiplier",.24,0,100,"Source damage conversion: original / 1000 * 240; before the shared Mahou damage policy");
        define(b,"armor_multiplier",.1,0,1,"Source defense conversion to native Minecraft armor points");
        define(b,"movement_multiplier",1,.01,100,"Movement conversion after pixels_per_block; does not change the source clock");
        define(b,"pixels_per_block",16,1,256,"Source pixels per Minecraft block; captured at encounter creation");
        b.push("skills");
        for(String skill:new String[]{"contact","dart","hellblast","fireblast","gigablast","wave","maelstrom","fist","slash"})
            define(b,skill,1,0,100,"Independent attack multiplier, multiplied by damage_multiplier");
        b.pop();
        b.pop().pop().push("weapons");
        String[] ids={"fractured_ark","ark_of_the_ancients","ark_of_the_elements","ark_of_cosmos","ark_of_the_old_one","broken_biome_blade","biome_blade","true_biome_blade","galaxia"};
        double[] damage={4,6,7,16,20,5,7,9.5,16};
        for(int i=0;i<ids.length;i++) {
            b.push(ids[i]);
            define(b,"base_damage",damage[i],.01,1000000,"Base attack damage");
            define(b,"attack_speed",4,.01,1000,"Displayed attack speed");
            b.pop();
        }
        b.pop().push("integration");
        define(b,"end_halo_trip_chance",.05,0,1,"Chance for a player wearing the Halo of the End to trip when taking sweet berry bush damage, checked at most once every 10 ticks");
        b.pop();SPEC=b.build();
        Map<String,Double> defaults=new LinkedHashMap<>();
        VALUES.forEach((key,value)->defaults.put(key,value.getDefault().doubleValue()));current=Map.copyOf(defaults);
    }
    private static void define(ForgeConfigSpec.Builder b,String name,double value,double min,double max,String comment) {
        var entry=b.comment(comment).defineInRange(name,value,min,max);
        VALUES.put(String.join(".",entry.getPath()),entry);
    }
    public static double value(String key) { return current.get(key); }
    public static double calamitasValue(String key) { return value("boss.calamitas."+key); }
    public static double calamitasDamage(String skill,double sourceDamage) {
        return sourceDamage*calamitasValue("damage_multiplier")*calamitasValue("skills."+skill);
    }
    public static float damage(String item) { return (float)value("weapons."+item+".base_damage"); }
    public static int bars() { return (int)value("boss.fourth_calamity.health_bars"); }
    public static double fourthHealth() { return bars()*value("boss.fourth_calamity.bar_health"); }
    public static float threshold(int phase) { return phase>=3?0:(float)value("boss.mahou.phase_"+(phase==1?"two":"three")+"_threshold"); }
    public static float bossDamage(net.minecraft.world.entity.LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float amount) {
        boolean mahou=source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
        var fourth=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(source);
        if(!mahou&&(fourth==null||target==fourth))return amount;
        String boss=mahou?"mahou":"fourth_calamity",skill="other";
        var direct=source.getDirectEntity();
        if(mahou) {
            if(direct instanceof net.everla.projectc.arkofcosmos.entity.MahouAttackEntity attack)skill=switch(attack.kind()) {
                case PILLAR->"pillar";case WAVE->"wave";case BLOOM->"bloom";case GRAVITY->"gravity";case VOID_VORTEX->"void_vortex";
                case RED->"red";case BLUE->"blue";case FUSION->"fusion";case PURPLE->"purple";case BLUE_DASH->"blue_dash";case DOMAIN_TEXT->"domain_text";default->"other";
            };
            else if(direct instanceof net.everla.projectc.arkofcosmos.entity.ElementsEntity attack)skill=switch(attack.kind()) {
                case net.everla.projectc.arkofcosmos.entity.ElementsEntity.BLADE->"blade";
                case net.everla.projectc.arkofcosmos.entity.ElementsEntity.NEEDLE->"needle";
                case net.everla.projectc.arkofcosmos.entity.ElementsEntity.STAR->"star";default->"other";
                case net.everla.projectc.arkofcosmos.entity.ElementsEntity.BLAST->"blast";
            };
            else if(direct instanceof net.everla.projectc.arkofcosmos.entity.GalaxiaEntity attack)skill=switch(attack.kind()) {
                case net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.BLADE->"galaxia_blade";
                case net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.BOLT->"galaxia_bolt";
                case net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.STAR->"galaxia_star";default->"other";
            };
            else if(net.everla.projectc.arkofcosmos.combat.ElementsCombat.isResonanceDamage(target))skill="resonance";
        } else {
            if(source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))skill="explosion";
            else if(source.typeHolder().unwrapKey().map(key->key.location().toString()).orElse("").equals("arkofcosmos:wind_charge"))skill="wind_charge";
            else skill=switch(source.getMsgId()) {
                case "spear"->"spear";case "mace_smash"->"mace";case "arrow"->"arrow";
                case "trident"->"trident";case "wind_charge"->"wind_charge";
                default->direct==fourth?(fourth.getMainHandItem().getItem() instanceof net.everla.projectc.arkofcosmos.item.MaceItem?"mace":"melee"):"other";
            };
        }
        float rage=source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity mage
                ?mage.outgoingDamageMultiplier():1f;
        return (float)(amount*rage*value("boss."+boss+".skill_damage_multiplier")*value("boss."+boss+".skills."+skill));
    }
    public static CompoundTag snapshot() {
        var tag=new CompoundTag();current.forEach(tag::putDouble);return tag;
    }
    public static void receive(CompoundTag tag) {
        remoteValues=ServerLifecycleHooks.getCurrentServer()==null;
        Map<String,Double> values=new LinkedHashMap<>();
        VALUES.forEach((key,spec)->values.put(key,tag.contains(key)?tag.getDouble(key):spec.getDefault().doubleValue()));
        current=Map.copyOf(values);
    }
    public static void localValues() {
        Map<String,Double> values=new LinkedHashMap<>();VALUES.forEach((key,value)->values.put(key,value.get().doubleValue()));
        current=Map.copyOf(values);
    }
    public static void disconnected() { remoteValues=false;localValues(); }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void changed(ModConfigEvent event) {
            if(event.getConfig().getSpec()!=SPEC||event instanceof ModConfigEvent.Unloading)return;
            if(remoteValues&&ServerLifecycleHooks.getCurrentServer()==null)return;
            loadedConfig=event.getConfig();
            try { modified=java.nio.file.Files.getLastModifiedTime(loadedConfig.getFullPath()); }
            catch(java.io.IOException exception) { modified=null; }
            localValues();
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null)server.execute(()->{
                for(var level:server.getAllLevels())for(var entity:level.getAllEntities())applyHealth(entity);
                for(var player:server.getPlayerList().getPlayers())net.everla.projectc.arkofcosmos.network.ArkNetwork.commonConfig(player,snapshot());
            });
        }
    }
    // NightConfig 3.6.4 drops coalesced MODIFY events (count > 1), common with Windows editors.
    @SubscribeEvent public static void watch(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||loadedConfig==null||event.getServer().getTickCount()%20!=0)return;
        try {
            var stamp=java.nio.file.Files.getLastModifiedTime(loadedConfig.getFullPath());
            if(stamp.equals(modified))return;
            if(loadedConfig.getConfigData() instanceof com.electronwill.nightconfig.core.file.CommentedFileConfig file) {
                file.load();
                if(!SPEC.isCorrect(file)) { SPEC.correct(file);file.save(); }
                SPEC.afterReload();Reload.changed(new ModConfigEvent.Reloading(loadedConfig));
            }
        } catch(java.io.IOException|RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(ProjectCCommonConfig.class).warn("Cannot reload projectc-common.toml; keeping the last valid values",exception);
            try { modified=java.nio.file.Files.getLastModifiedTime(loadedConfig.getFullPath()); }
            catch(java.io.IOException ignored) { modified=null; }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)net.everla.projectc.arkofcosmos.network.ArkNetwork.commonConfig(player,snapshot());
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) { if(!event.getLevel().isClientSide)applyHealth(event.getEntity()); }
    private static void applyHealth(net.minecraft.world.entity.Entity entity) {
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss)boss.applyConfiguredHealth();
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity boss)boss.applyConfiguredHealth();
        if(entity instanceof net.minecraft.world.entity.LivingEntity living) {
            var stack=living.getMainHandItem();var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
            if(id!=null&&id.getNamespace().equals(ArkOfCosmos.MOD_ID)&&current.containsKey("weapons."+id.getPath()+".base_damage")) {
                var modifiers=stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
                living.getAttributes().removeAttributeModifiers(modifiers);
                living.getAttributes().addTransientAttributeModifiers(modifiers);
            }
        }
    }
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event) {
        if(event.getSlotType()!=EquipmentSlot.MAINHAND)return;
        var id=ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if(id==null||!id.getNamespace().equals(ArkOfCosmos.MOD_ID)||!current.containsKey("weapons."+id.getPath()+".attack_speed"))return;
        for(var modifier:java.util.List.copyOf(event.getModifiers().get(Attributes.ATTACK_SPEED)))
            if(modifier.getId().equals(java.util.UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3")))event.removeModifier(Attributes.ATTACK_SPEED,modifier);
        event.addModifier(Attributes.ATTACK_SPEED,new AttributeModifier(java.util.UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3"),"Weapon modifier",value("weapons."+id.getPath()+".attack_speed")-4,AttributeModifier.Operation.ADDITION));
        if(id.getPath().equals("ark_of_the_old_one")) {
            for(var modifier:java.util.List.copyOf(event.getModifiers().get(Attributes.ATTACK_DAMAGE)))
                if(modifier.getId().equals(java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF")))event.removeModifier(Attributes.ATTACK_DAMAGE,modifier);
            event.addModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),"Weapon modifier",damage(id.getPath())-1,AttributeModifier.Operation.ADDITION));
        }
    }
}
