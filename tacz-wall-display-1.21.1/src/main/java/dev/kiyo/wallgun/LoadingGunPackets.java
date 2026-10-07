package dev.kiyo.wallgun;

import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.network.message.ClientMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.handshake.Acknowledge;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Only called during loading. Bootstrap traffic is not a combat request. */
public final class LoadingGunPackets {
    public static boolean block(ServerPlayer player,CustomPacketPayload payload) {
        if(payload instanceof ClientMessageSyncBaseTimestamp || payload instanceof Acknowledge)return false;
        if(payload instanceof ClientMessagePlayerDrawGun)LoadingSessions.deferGunDraw(player);
        return true;
    }
    private LoadingGunPackets() {}
}
