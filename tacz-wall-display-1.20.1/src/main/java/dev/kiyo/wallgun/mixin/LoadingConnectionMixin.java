package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LoadingConnectionMixin {
    @Unique private ServerPlayer wallgun$player(){return ((ServerGamePacketListenerImpl)(Object)this).player;}
    @Inject(method="tick",at=@At("HEAD"))
    private void keepNetworkAlive(CallbackInfo ci) {
        var player=wallgun$player();
        if(LoadingSessions.waiting(player)){
            var access=(LoadingConnectionAccess)(Object)this;
            access.wallgun$clientFloating(false);access.wallgun$vehicleFloating(false);
            access.wallgun$groundTicks(0);access.wallgun$vehicleGroundTicks(0);
        }
    }
    @Inject(method={"handleMovePlayer","handleMoveVehicle","handlePlayerInput","handlePlayerAction","handleUseItemOn","handleUseItem","handleInteract","handlePlayerCommand","handleContainerClick","handleContainerButtonClick","handleSetCreativeModeSlot","handlePlaceRecipe","handlePlayerAbilities","handleSetCarriedItem","handlePaddleBoat","handleTeleportToEntityPacket","handleAnimate","handleChatCommand","handleClientCommand"},at=@At("HEAD"),cancellable=true)
    private void noGameplay(@Coerce net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ServerGamePacketListener> packet,CallbackInfo ci) {
        var player=wallgun$player();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(LoadingSessions.waiting(player))ci.cancel();
    }
    @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true)
    private void noGunActions(ServerboundCustomPayloadPacket packet,CallbackInfo ci) {
        var player=wallgun$player();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(!LoadingSessions.waiting(player))return;
        var id=packet.getIdentifier();
        if(id.getNamespace().equals("tacz") || (id.getNamespace().equals(WallGuns.ID) && !id.equals(LoadingPayloads.ID)))ci.cancel();
    }
}
