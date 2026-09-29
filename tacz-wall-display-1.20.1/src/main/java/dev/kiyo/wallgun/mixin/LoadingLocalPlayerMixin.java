package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.client.LoadingClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class LoadingLocalPlayerMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void waitWithoutPrediction(CallbackInfo ci){if(LoadingClient.waiting()){var p=(LocalPlayer)(Object)this;p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;ci.cancel();}}
}
