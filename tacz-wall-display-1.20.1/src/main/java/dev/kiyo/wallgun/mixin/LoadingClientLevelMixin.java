package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.client.LoadingClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class LoadingClientLevelMixin {
    @Inject(method={"handleLogin","handleRespawn"},at=@At("HEAD"))
    private void entering(@Coerce net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> packet,CallbackInfo ci){
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ClientPacketListener)(Object)this,net.minecraft.client.Minecraft.getInstance());
        LoadingClient.entering();
    }
}
