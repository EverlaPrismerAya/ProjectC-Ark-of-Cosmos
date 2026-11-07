package net.everla.projectc.arkofcosmos.enchant;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.BrimstoneWitchEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import net.minecraftforge.network.*;

public final class WitchEnchanting {
    private WitchEnchanting() {}
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ArkOfCosmos.MOD_ID);
    public static final RegistryObject<MenuType<WitchMenu>> MENU=MENUS.register("witch_enchanting",()->IForgeMenuType.create((id,inventory,buffer)->new WitchMenu(id,inventory,inventory.player.level().getEntity(buffer.readVarInt()) instanceof BrimstoneWitchEntity witch?witch:null)));
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"witch"),()->"3","3"::equals,"3"::equals);
    private record Collect(int entity) {}
    private record Infuse(int menu,int revision,ResourceLocation recipe,net.minecraft.world.item.ItemStack input,net.minecraft.world.item.ItemStack result) {}
    public static void infuse(WitchMenu menu,NecromanticInfusion recipe){CHANNEL.sendToServer(new Infuse(menu.containerId,menu.revision(),recipe.id(),menu.item().copy(),recipe.result().copy()));}
    private record Flames(int entity,java.util.UUID uuid,int ticks) {}
    private record NecromanticVisual(int kind,int entity,java.util.UUID uuid,net.minecraft.world.phys.Vec3 position) {}
    public static void necromanticVisual(net.minecraft.world.entity.Entity entity,int kind){
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),new NecromanticVisual(kind,entity.getId(),entity.getUUID(),entity.position()));
    }
    public static void flames(net.minecraft.world.entity.LivingEntity entity,int ticks){
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),new Flames(entity.getId(),entity.getUUID(),ticks));
    }
    public static void flamesTo(net.minecraft.world.entity.player.Player player,net.minecraft.world.entity.LivingEntity entity,int ticks){
        if(player instanceof net.minecraft.server.level.ServerPlayer server)CHANNEL.send(PacketDistributor.PLAYER.with(()->server),new Flames(entity.getId(),entity.getUUID(),ticks));
    }
    public static void register(IEventBus bus) {
        MENUS.register(bus);
        NecromanticInfusion.register(bus);
        CHANNEL.messageBuilder(NecromanticVisual.class,3,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeVarInt(p.kind);b.writeVarInt(p.entity);b.writeUUID(p.uuid);b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);})
            .decoder(b->new NecromanticVisual(b.readVarInt(),b.readVarInt(),b.readUUID(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble())))
            .consumerMainThread((p,c)->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->net.everla.projectc.arkofcosmos.client.NecromanticEffects.visual(p.kind,p.entity,p.uuid,p.position))).add();
        CHANNEL.messageBuilder(Infuse.class,2,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeVarInt(p.menu);b.writeVarInt(p.revision);b.writeResourceLocation(p.recipe);b.writeItem(p.input);b.writeItem(p.result);})
            .decoder(b->new Infuse(b.readVarInt(),b.readVarInt(),b.readResourceLocation(),b.readItem(),b.readItem()))
            .consumerMainThread((p,c)->{var player=c.get().getSender();if(player!=null&&player.containerMenu instanceof WitchMenu menu&&menu.containerId==p.menu)menu.infuse(player,p.recipe,p.input,p.result,p.revision);}).add();
        CHANNEL.messageBuilder(Flames.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeVarInt(p.entity);b.writeUUID(p.uuid);b.writeVarInt(p.ticks);})
            .decoder(b->new Flames(b.readVarInt(),b.readUUID(),b.readVarInt()))
            .consumerMainThread((p,c)->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->net.everla.projectc.arkofcosmos.client.WitchClient.flames(p.entity,p.uuid,p.ticks))).add();
        if(net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED) net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(WitchSoulEvents.class);
        CHANNEL.messageBuilder(Collect.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->b.writeVarInt(p.entity))
            .decoder(b->new Collect(b.readVarInt())).consumerMainThread((p,c)->{
                var player=c.get().getSender();
                if(player==null || !(player.level().getEntity(p.entity) instanceof BrimstoneWitchEntity npc)) return;
                var eye=player.getEyePosition(); var end=eye.add(player.getLookAngle().scale(6));
                var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,eye,end,player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),e->e.isPickable()&&!e.isSpectator(),36);
                if(hit==null || hit.getEntity()!=npc)return;
                var block=player.level().clip(new net.minecraft.world.level.ClipContext(eye,hit.getLocation(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                if(block.getType()==net.minecraft.world.phys.HitResult.Type.MISS)npc.collect(player);
            }).add();
    }
    public static void collect(int id) { CHANNEL.sendToServer(new Collect(id)); }
}
