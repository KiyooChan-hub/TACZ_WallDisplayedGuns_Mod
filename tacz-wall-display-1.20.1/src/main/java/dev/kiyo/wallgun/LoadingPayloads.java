package dev.kiyo.wallgun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Consumer;

/** Required activation protocol. Mismatched versions fail connection negotiation. */
public final class LoadingPayloads {
    public static final ResourceLocation ID=new ResourceLocation(WallGuns.ID,"loading_session");
    private static final String PROTOCOL="activation-2";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ID,()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    public static Consumer<CompoundTag> client=tag->{};
    public record Message(CompoundTag data) {}
    public static CompoundTag message(String kind,long id,String dimension){
        var tag=new CompoundTag();tag.putString("kind",kind);tag.putLong("id",id);tag.putString("dimension",dimension);return tag;
    }
    public static void init(){
        CHANNEL.registerMessage(0,Message.class,(m,b)->b.writeNbt(m.data()),b->new Message(b.readNbt()),(m,s)->{
            var context=s.get();
            context.enqueueWork(()->{
                if(m.data()==null)return;
                var player=context.getSender();
                if(player!=null)LoadingSessions.receive(player,m.data());else client.accept(m.data());
            });
            context.setPacketHandled(true);
        });
    }
    public static void send(ServerPlayer player,CompoundTag tag){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Message(tag));}
    public static void send(CompoundTag tag){CHANNEL.sendToServer(new Message(tag));}
}
