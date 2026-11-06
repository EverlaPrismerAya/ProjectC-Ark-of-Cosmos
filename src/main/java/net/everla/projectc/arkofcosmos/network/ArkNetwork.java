package net.everla.projectc.arkofcosmos.network;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

public final class ArkNetwork {
    private ArkNetwork() {}
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "input"), () -> "7", "7"::equals, "7"::equals);
    private record CommonConfig(CompoundTag data) {}
    public static void commonConfig(net.minecraft.server.level.ServerPlayer player,CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new CommonConfig(data));
    }
    public record Input(boolean left, boolean right, boolean alt) {
        static Input read(FriendlyByteBuf buffer) { return new Input(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()); }
        void write(FriendlyByteBuf buffer) { buffer.writeBoolean(left); buffer.writeBoolean(right); buffer.writeBoolean(alt); }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    ArkCombat.input(context.getSender(), this);
                    net.everla.projectc.arkofcosmos.combat.OldOneCombat.input(context.getSender(), this.left());
                    net.everla.projectc.arkofcosmos.combat.ElementsCombat.input(context.getSender(), this);
                    net.everla.projectc.arkofcosmos.combat.FracturedCombat.input(context.getSender(), this);
                    net.everla.projectc.arkofcosmos.combat.AncientsCombat.input(context.getSender(), this);
                }
            });
            context.setPacketHandled(true);
        }
    }
    public static void spearJab() { CHANNEL.sendToServer(new SpearJab()); }
    private record SpearJab() {}
    private record SpearFeedback(int entity) {}
    public static void spearFeedback(Entity entity) { CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),new SpearFeedback(entity.getId())); }
    private record BrokenBiomeInput(boolean left,int slot,int mode) {}
    public static void brokenBiome(boolean left,int slot,int mode){CHANNEL.sendToServer(new BrokenBiomeInput(left,slot,mode));}
    public static void register() {
        CHANNEL.messageBuilder(CommonConfig.class,11,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->b.writeNbt(p.data())).decoder(b->new CommonConfig(b.readNbt()))
            .consumerMainThread((p,c)->net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(p.data())).add();
        CHANNEL.messageBuilder(BrokenBiomeInput.class,10,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeBoolean(p.left());b.writeByte(p.slot());b.writeByte(p.mode());})
            .decoder(b->new BrokenBiomeInput(b.readBoolean(),b.readByte(),b.readByte()))
            .consumerMainThread((p,c)->{var player=c.get().getSender();if(player!=null){net.everla.projectc.arkofcosmos.combat.BrokenBiomeCombat.input(player,p.left(),false);net.everla.projectc.arkofcosmos.combat.BrokenBiomeCombat.select(player,p.slot(),p.mode());}}).add();
        CHANNEL.messageBuilder(BiomeUnlocks.class,9,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->b.writeByte(p.mask())).decoder(b->new BiomeUnlocks(b.readUnsignedByte()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.everla.projectc.arkofcosmos.client.BiomeClient.unlocks=p.mask())).add();
        CHANNEL.messageBuilder(BiomeInput.class,8,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeBoolean(p.left());b.writeBoolean(p.right());b.writeByte(p.slot());b.writeByte(p.mode());})
            .decoder(b->new BiomeInput(b.readBoolean(),b.readBoolean(),b.readByte(),b.readByte()))
            .consumerMainThread((p,c)->{if(c.get().getSender()!=null){var player=c.get().getSender();net.everla.projectc.arkofcosmos.combat.BiomeCombat.input(player,p.left(),p.right());net.everla.projectc.arkofcosmos.combat.BiomeCombat.select(player,p.slot(),p.mode());}}).add();
        CHANNEL.messageBuilder(TrueBiomeInput.class,7,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeBoolean(p.left());b.writeByte(p.slot());b.writeByte(p.mode());})
            .decoder(b->new TrueBiomeInput(b.readBoolean(),b.readByte(),b.readByte()))
            .consumerMainThread((p,c)->{if(c.get().getSender()!=null)net.everla.projectc.arkofcosmos.combat.TrueBiomeCombat.input(c.get().getSender(),p.left(),p.slot(),p.mode());}).add();
        CHANNEL.messageBuilder(FourthBossBar.class,6,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.id());b.writeByte(p.phase());b.writeVarInt(p.bars());})
            .decoder(b->new FourthBossBar(b.readUUID(),b.readByte(),b.readVarInt()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.everla.projectc.arkofcosmos.client.FourthCalamityBossBar.update(p.id(),p.phase(),p.bars()))).add();
        CHANNEL.messageBuilder(SpearFeedback.class,5,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->b.writeVarInt(p.entity())).decoder(b->new SpearFeedback(b.readVarInt()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.everla.projectc.arkofcosmos.client.vanilla.VanillaClient.feedback(p.entity()))).add();
        CHANNEL.messageBuilder(SpearJab.class,4,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{}).decoder(b->new SpearJab())
            .consumerMainThread((p,c)->{ if(c.get().getSender()!=null) net.everla.projectc.arkofcosmos.combat.vanilla.SpearCombat.jab(c.get().getSender()); }).add();
        CHANNEL.messageBuilder(BossBar.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, b) -> { b.writeUUID(p.id()); b.writeByte(p.phase()); })
                .decoder(b -> new BossBar(b.readUUID(), b.readByte()))
                .consumerMainThread((p, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> net.everla.projectc.arkofcosmos.client.MahouBossBar.update(p.id(), p.phase()))).add();
        CHANNEL.messageBuilder(GalaxiaInput.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b) -> { b.writeBoolean(p.left()); b.writeInt(p.selection()); })
                .decoder(b -> new GalaxiaInput(b.readBoolean(),b.readInt()))
                .consumerMainThread((p,c) -> { if(c.get().getSender()!=null) net.everla.projectc.arkofcosmos.combat.GalaxiaCombat.input(c.get().getSender(),p.left(),p.selection()); }).add();
        CHANNEL.messageBuilder(Input.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Input::write).decoder(Input::read).consumerNetworkThread(Input::handle).add();
        CHANNEL.messageBuilder(Effect.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeNbt(packet.data))
                .decoder(buffer -> new Effect(buffer.readNbt()))
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ArkEffects.receive(packet.data))).add();
    }
    private record BossBar(java.util.UUID id, int phase) {}
    private record FourthBossBar(java.util.UUID id,int phase,int bars) {}
    public static void fourthBossBar(net.minecraft.server.level.ServerPlayer player,java.util.UUID id,int phase,int bars) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new FourthBossBar(id,phase,bars));
    }
    public static void bossBar(net.minecraft.server.level.ServerPlayer player, java.util.UUID id, int phase) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BossBar(id, phase));
    }
    private record Effect(CompoundTag data) {}
    public static void calamitasMusic(java.util.UUID owner, boolean start) {
        var data = new CompoundTag(); data.putString("Event", "calamitas_acceptance_music");
        data.putUUID("Owner", owner); data.putBoolean("Start", start);
        CHANNEL.send(PacketDistributor.ALL.noArg(), new Effect(data));
    }
    private record GalaxiaInput(boolean left, int selection) {}
    private record TrueBiomeInput(boolean left,int slot,int mode) {}
    private record BiomeInput(boolean left,boolean right,int slot,int mode) {}
    private record BiomeUnlocks(int mask) {}
    public static void biomeUnlocks(net.minecraft.server.level.ServerPlayer p,int mask){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new BiomeUnlocks(mask));}
    public static void biomeSelect(int slot,int mode){CHANNEL.sendToServer(new BiomeInput(false,false,slot,mode));}
    public static void biome(boolean left,boolean right){CHANNEL.sendToServer(new BiomeInput(left,right,-1,-1));}
    public static void trueBiome(boolean left,int slot,int mode){CHANNEL.sendToServer(new TrueBiomeInput(left,slot,mode));}
    public static void galaxia(boolean left,int selection) { CHANNEL.sendToServer(new GalaxiaInput(left,selection)); }
    public static void effect(Entity entity, CompoundTag data) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new Effect(data));
    }
    public static void nearbyEffect(Entity entity,CompoundTag data){
        CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(entity.getX(),entity.getY(),entity.getZ(),256,entity.level().dimension())),new Effect(data));
    }
    public static void playerEffect(net.minecraft.server.level.ServerPlayer player, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Effect(data));
    }
    public static void parryEffect(net.minecraft.world.entity.player.Player player,AttackFrame frame){
        CompoundTag data=new CompoundTag();data.putString("Event","parry");data.putInt("Owner",player.getId());
        net.minecraft.world.phys.Vec3 at=player.getBoundingBox().getCenter();
        data.putDouble("AtX",at.x);data.putDouble("AtY",at.y);data.putDouble("AtZ",at.z);
        data.putDouble("FX",frame.forward().x);data.putDouble("FY",frame.forward().y);data.putDouble("FZ",frame.forward().z);
        data.putDouble("RX",frame.right().x);data.putDouble("RY",frame.right().y);data.putDouble("RZ",frame.right().z);
        data.putDouble("Width",player.getBbWidth());data.putDouble("Height",player.getBbHeight());
        effect(player,data);
    }
    public static void send(boolean left, boolean right, boolean alt) { CHANNEL.sendToServer(new Input(left, right, alt)); }
}
