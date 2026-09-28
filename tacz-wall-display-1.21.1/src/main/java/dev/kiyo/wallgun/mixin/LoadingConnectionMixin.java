package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LoadingConnectionMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private boolean clientVehicleIsFloating;
    @Shadow private int aboveGroundTickCount;
    @Shadow private int aboveGroundVehicleTickCount;
    @Inject(method="tick",at=@At("HEAD"))
    private void keepNetworkAlive(CallbackInfo ci) {
        if(LoadingSessions.waiting(player)){clientIsFloating=false;clientVehicleIsFloating=false;aboveGroundTickCount=0;aboveGroundVehicleTickCount=0;}
    }
    @Inject(method={"handleMovePlayer","handleMoveVehicle","handlePlayerInput","handlePlayerAction","handleUseItemOn","handleUseItem","handleInteract","handlePlayerCommand","handleContainerClick","handleContainerButtonClick","handleSetCreativeModeSlot","handlePlaceRecipe","handlePlayerAbilities","handleSetCarriedItem","handlePaddleBoat","handleTeleportToEntityPacket","handleAnimate","handleChatCommand","handleSignedChatCommand","handleClientCommand"},at=@At("HEAD"),cancellable=true)
    private void noGameplay(@Coerce net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ServerGamePacketListener> packet,CallbackInfo ci) {
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(LoadingSessions.waiting(player))ci.cancel();
    }
    @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true)
    private void noGunActions(ServerboundCustomPayloadPacket packet,CallbackInfo ci) {
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(!LoadingSessions.waiting(player))return;
        var id=packet.payload().type().id();
        if(id.getNamespace().equals("tacz") || (id.getNamespace().equals(WallGuns.ID) && !id.equals(LoadingPayloads.Message.TYPE.id())))ci.cancel();
    }
}
