package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.client.LoadingClient;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ReceivingLevelScreen.class)
public abstract class LoadingTerrainMixin {
    @Inject(method="onClose",at=@At("HEAD"),cancellable=true)
    private void waitForServer(CallbackInfo ci){if(LoadingClient.waiting())ci.cancel();}
}
