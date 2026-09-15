package dev.kiyo.wallgun.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.util.RenderDistance;
import dev.kiyo.wallgun.client.MeshCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Static displays retain high-detail geometry and its matching texture regardless of TACZ LOD settings. */
@Mixin(value = RenderDistance.class, remap = false)
public abstract class HighDetailCaptureMixin {
    @Inject(method = "inRenderHighPolyModelDistance", at = @At("HEAD"), cancellable = true)
    private static void wallgun$highDetail(PoseStack pose, CallbackInfoReturnable<Boolean> result) {
        // The capture is scoped by GunMeshes' try/finally; ordinary gun rendering keeps TACZ's decision.
        if (MeshCapture.active() != null) result.setReturnValue(true);
    }
}
