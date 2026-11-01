package net.everla.projectc.arkofcosmos.combat.vanilla;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.PlayerWindCharge;
import net.everla.projectc.arkofcosmos.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.*;

public final class VanillaCombat {
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ArkOfCosmos.MOD_ID);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS=DeferredRegister.create(ForgeRegistries.ENCHANTMENTS,ArkOfCosmos.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ArkOfCosmos.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ArkOfCosmos.MOD_ID);
    public static final Map<String,RegistryObject<Item>> SPEARS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<SoundEvent>> AUDIO=new HashMap<>();
    public static final RegistryObject<Item> MACE=ITEMS.register("mace",MaceItem::new);
    public static final RegistryObject<Item> HEAVY_CORE=ITEMS.register("heavy_core",()->new Item(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> COPPER_SWORD=ITEMS.register("copper_sword",()->new SwordItem(CopperEquipment.TIER,3,-2.4f,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_SHOVEL=ITEMS.register("copper_shovel",()->new ShovelItem(CopperEquipment.TIER,1.5f,-3f,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_PICKAXE=ITEMS.register("copper_pickaxe",()->new PickaxeItem(CopperEquipment.TIER,1,-2.8f,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_AXE=ITEMS.register("copper_axe",()->new AxeItem(CopperEquipment.TIER,7,-3.2f,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_HOE=ITEMS.register("copper_hoe",()->new HoeItem(CopperEquipment.TIER,-1,-2f,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_HELMET=ITEMS.register("copper_helmet",()->new ArmorItem(CopperEquipment.Armor.INSTANCE,ArmorItem.Type.HELMET,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_CHESTPLATE=ITEMS.register("copper_chestplate",()->new ArmorItem(CopperEquipment.Armor.INSTANCE,ArmorItem.Type.CHESTPLATE,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_LEGGINGS=ITEMS.register("copper_leggings",()->new ArmorItem(CopperEquipment.Armor.INSTANCE,ArmorItem.Type.LEGGINGS,new Item.Properties()));
    public static final RegistryObject<Item> COPPER_BOOTS=ITEMS.register("copper_boots",()->new ArmorItem(CopperEquipment.Armor.INSTANCE,ArmorItem.Type.BOOTS,new Item.Properties()));
    public static final RegistryObject<Enchantment> LUNGE=enchantment("lunge",VanillaEnchantments.Kind.LUNGE);
    public static final RegistryObject<Enchantment> DENSITY=enchantment("density",VanillaEnchantments.Kind.DENSITY);
    public static final RegistryObject<Enchantment> BREACH=enchantment("breach",VanillaEnchantments.Kind.BREACH);
    public static final RegistryObject<Enchantment> WIND_BURST=enchantment("wind_burst",VanillaEnchantments.Kind.WIND_BURST);
    public static final RegistryObject<EntityType<PlayerWindCharge>> WIND_CHARGE=ENTITIES.register("player_wind_charge",()->EntityType.Builder.<PlayerWindCharge>of(PlayerWindCharge::new,MobCategory.MISC).sized(.3125f,.3125f).clientTrackingRange(4).updateInterval(1).build("arkofcosmos:player_wind_charge"));
    static {
        for(SpearMaterial m:SpearMaterial.VALUES) SPEARS.put(m.name(),ITEMS.register(m.name()+"_spear",()->new SpearItem(m)));
        for(String s:new String[]{"item.spear.use","item.spear.hit","item.spear.attack","item.spear_wood.use","item.spear_wood.hit","item.spear_wood.attack","item.spear.lunge_1","item.spear.lunge_2","item.spear.lunge_3","item.mace.smash_air","item.mace.smash_ground","item.mace.smash_ground_heavy","entity.wind_charge.throw","entity.wind_charge.wind_burst"})
            AUDIO.put(s,SOUNDS.register(s,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ArkOfCosmos.MOD_ID,s))));
    }
    private static RegistryObject<Enchantment> enchantment(String id,VanillaEnchantments.Kind kind) { return ENCHANTMENTS.register(id,()->new VanillaEnchantments(kind)); }
    public static void register(IEventBus bus) {
        VanillaParticles.REGISTER.register(bus);ITEMS.register(bus);ENCHANTMENTS.register(bus);ENTITIES.register(bus);SOUNDS.register(bus);AncientCityLoot.REGISTER.register(bus);
        MinecraftForge.EVENT_BUS.register(VanillaCombat.class);
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey()==CreativeModeTabs.COMBAT) { SPEARS.values().forEach(e::accept);e.accept(MACE);e.accept(COPPER_SWORD);e.accept(COPPER_AXE);e.accept(COPPER_PICKAXE);e.accept(COPPER_SHOVEL);e.accept(COPPER_HOE);e.accept(COPPER_HELMET);e.accept(COPPER_CHESTPLATE);e.accept(COPPER_LEGGINGS);e.accept(COPPER_BOOTS); }
            if(e.getTabKey()==CreativeModeTabs.INGREDIENTS)e.accept(HEAVY_CORE);
        });
    }
    public static void sound(Entity entity,String id,float volume,float pitch) { entity.level().playSound(null,entity.getX(),entity.getY(),entity.getZ(),AUDIO.get(id).get(),entity.getSoundSource(),volume,pitch); }
    public static int enchant(ItemStack stack,RegistryObject<Enchantment> e) { return EnchantmentHelper.getItemEnchantmentLevel(e.get(),stack); }
    public static net.minecraft.world.damagesource.DamageSource source(LivingEntity attacker,String id) {
        return new net.minecraft.world.damagesource.DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("arkofcosmos",id))),attacker);
    }
    public static void syncMotion(Entity entity) { entity.hurtMarked=true;if(entity instanceof ServerPlayer player)player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player)); }
    public static InteractionResult throwCharge(Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);Level level=player.level();
        if(!stack.is(Items.FIRE_CHARGE)||player.getCooldowns().isOnCooldown(Items.FIRE_CHARGE))return InteractionResult.FAIL;
        if(!level.isClientSide) {
            PlayerWindCharge charge=new PlayerWindCharge(WIND_CHARGE.get(),level);charge.setOwner(player);charge.setPos(player.getX(),player.getEyeY(),player.getZ());
            charge.shootFromRotation(player,player.getXRot(),player.getYRot(),0,1.5f,1);level.addFreshEntity(charge);
        }
        sound(player,"entity.wind_charge.throw",.5f,.4f/(level.random.nextFloat()*.4f+.8f));
        player.getCooldowns().addCooldown(Items.FIRE_CHARGE,10);player.awardStat(Stats.ITEM_USED.get(Items.FIRE_CHARGE));
        if(!player.getAbilities().instabuild)stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @SubscribeEvent public static void rightAir(PlayerInteractEvent.RightClickItem e) {
        if(e.getItemStack().is(Items.FIRE_CHARGE)) { e.setCanceled(true);e.setCancellationResult(throwCharge(e.getEntity(),e.getHand())); }
    }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock e) {
        if(e.getItemStack().is(Items.FIRE_CHARGE)) { e.setCanceled(true);e.setCancellationResult(throwCharge(e.getEntity(),e.getHand())); }
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent e) {
        LivingEntity entity=e.getEntity();if(entity.level().isClientSide)return;
        SpearCombat.track(entity);ImpulseFall.tick(entity);
    }
    @SubscribeEvent public static void fall(LivingFallEvent e) { ImpulseFall.fall(e); }
}
