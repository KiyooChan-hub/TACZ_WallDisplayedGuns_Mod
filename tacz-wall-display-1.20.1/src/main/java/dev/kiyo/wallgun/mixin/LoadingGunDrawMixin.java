package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.gameplay.LocalPlayerDraw;
import dev.kiyo.wallgun.client.LoadingGunClient;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=LocalPlayerDraw.class,remap=false)
public abstract class LoadingGunDrawMixin {
    @Redirect(method="draw",at=@At(value="INVOKE",target="Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private void noRecoveryEcho(SimpleChannel channel,Object message) {
        if(!LoadingGunClient.localOnly())channel.sendToServer(message);
    }
}
