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
            ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "input"), () -> "1", "1"::equals, "1"::equals);
    public record Input(boolean left, boolean right, boolean alt) {
        static Input read(FriendlyByteBuf buffer) { return new Input(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()); }
        void write(FriendlyByteBuf buffer) { buffer.writeBoolean(left); buffer.writeBoolean(right); buffer.writeBoolean(alt); }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    ArkCombat.input(context.getSender(), this);
                    net.everla.projectc.arkofcosmos.combat.ElementsCombat.input(context.getSender(), this);
                }
            });
            context.setPacketHandled(true);
        }
    }
    public static void register() {
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
    private record Effect(CompoundTag data) {}
    private record GalaxiaInput(boolean left, int selection) {}
    public static void galaxia(boolean left,int selection) { CHANNEL.sendToServer(new GalaxiaInput(left,selection)); }
    public static void effect(Entity entity, CompoundTag data) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new Effect(data));
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
