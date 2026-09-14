package dev.kiyo.wallgun.mixin;
import com.tacz.guns.compat.ar.ARCompat;
import dev.kiyo.wallgun.client.MeshCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = ARCompat.class, remap = false)
public class AccelerationCaptureMixin {
    @Inject(method = "shouldAccelerate", at = @At("HEAD"), cancellable = true)
    private static void wallgun$cpuSnapshot(CallbackInfoReturnable<Boolean> cir) {
        if (MeshCapture.active() != null) cir.setReturnValue(false);
    }
}
