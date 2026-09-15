package dev.kiyo.wallgun.smoke.mixin;
import dev.kiyo.wallgun.smoke.ReferenceCapture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Test-only hook below all TACZ model paths; never ships in the release JAR. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class ReferenceBufferMixin {
    @Inject(method="getBuffer", at=@At("HEAD"), cancellable=true)
    private void referenceBuffer(RenderType type, CallbackInfoReturnable<VertexConsumer> ci) {
        if (ReferenceCapture.active != null) ci.setReturnValue(ReferenceCapture.active.buffer(type));
    }
    @Inject(method="endBatch()V", at=@At("HEAD"), cancellable=true)
    private void referenceEndAll(CallbackInfo ci) { if (ReferenceCapture.active != null) ci.cancel(); }
    @Inject(method="endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at=@At("HEAD"), cancellable=true)
    private void referenceEnd(RenderType type, CallbackInfo ci) { if (ReferenceCapture.active != null) ci.cancel(); }
}
