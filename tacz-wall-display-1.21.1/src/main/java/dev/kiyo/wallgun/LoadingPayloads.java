package dev.kiyo.wallgun;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

/** Required, versioned protocol. Older clients/servers fail negotiation instead of losing protection. */
public final class LoadingPayloads {
    public static Consumer<CompoundTag> client = tag -> {};
    public record Message(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Message> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WallGuns.ID,"loading_session"));
        public static final StreamCodec<ByteBuf,Message> CODEC = ByteBufCodecs.COMPOUND_TAG.map(Message::new,Message::data);
        public Type<? extends CustomPacketPayload> type() {return TYPE;}
    }
    public static CompoundTag message(String kind,long id,String dimension) {
        var tag=new CompoundTag();tag.putString("kind",kind);tag.putLong("id",id);tag.putString("dimension",dimension);return tag;
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("activation-2").playBidirectional(Message.TYPE,Message.CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player) LoadingSessions.receive(player,payload.data());
            else client.accept(payload.data());
        });
    }
    public static void send(ServerPlayer player,CompoundTag tag) {PacketDistributor.sendToPlayer(player,new Message(tag));}
    public static void send(CompoundTag tag) {PacketDistributor.sendToServer(new Message(tag));}
}
