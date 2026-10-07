package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.gameplay.LocalPlayerDraw;
import dev.kiyo.wallgun.client.LoadingGunClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=LocalPlayerDraw.class,remap=false)
public abstract class LoadingGunDrawMixin {
    @Redirect(method="draw",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/network/PacketDistributor;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void noRecoveryEcho(CustomPacketPayload message,CustomPacketPayload[] extra) {
        if(!LoadingGunClient.localOnly())PacketDistributor.sendToServer(message,extra);
    }
}
