package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.input.RefitKey;
import com.tacz.guns.util.InputExtraCheck;
import dev.kiyo.wallgun.client.PlacementClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep TACZ refitting available without re-enabling other gun controls. */
@Mixin(value = RefitKey.class, remap = false)
public abstract class RefitKeyMixin {
    @Redirect(method = "onRefitPress", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/InputExtraCheck;isInGame()Z", remap = false))
    private static boolean wallgun$allowRefitInPlacementMode() {
        return PlacementClient.interceptGun() || InputExtraCheck.isInGame();
    }
}
