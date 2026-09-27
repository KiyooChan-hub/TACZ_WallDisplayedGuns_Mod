package dev.kiyo.wallgun.mixin;

import dev.kiyo.wallgun.client.WallWarmup;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the original terrain-loading screen, including its dimension-specific background. */
@Mixin(ReceivingLevelScreen.class)
public abstract class TerrainWarmupMixin {
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void wallgun$waitForDisplayMeshes(CallbackInfo ci) {
        if (WallWarmup.holdLoadingScreen()) ci.cancel();
    }
}
