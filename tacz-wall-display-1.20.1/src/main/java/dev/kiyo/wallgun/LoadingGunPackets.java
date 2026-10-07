package dev.kiyo.wallgun;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.network.message.ClientMessageSyncBaseTimestamp;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

/** Forge multiplexes TaCZ messages. Ask its registered encoder rather than hardcoding IDs. */
public final class LoadingGunPackets {
    private static final class Discriminators {
        static final int DRAW=encodedId(new ClientMessagePlayerDrawGun());
        static final int TIME=encodedId(new ClientMessageSyncBaseTimestamp());
    }
    private static int encodedId(Object message) {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{NetworkHandler.CHANNEL.encodeMessage(message,buffer);return buffer.readUnsignedByte();}
        finally{buffer.release();}
    }
    public static boolean block(ServerPlayer player,ServerboundCustomPayloadPacket packet) {
        var path=packet.getIdentifier().getPath();
        if(path.equals("handshake"))return false;
        if(!path.equals("network"))return true;
        // Do not consume or retain the inbound buffer; Forge must decode allowed packets unchanged.
        var data=packet.getInternalData();
        if(!data.isReadable())return true;
        int message=data.getUnsignedByte(data.readerIndex());
        if(message==Discriminators.TIME)return false;
        if(message==Discriminators.DRAW)LoadingSessions.deferGunDraw(player);
        return true;
    }
    private LoadingGunPackets() {}
}
