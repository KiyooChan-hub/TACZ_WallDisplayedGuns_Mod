package dev.kiyo.wallgun.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.*;
import com.tacz.guns.client.model.IFunctionalRenderer;
import dev.kiyo.wallgun.client.MeshCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Only captures during our synchronous, one-time bake. Ordinary TACZ renders are untouched. */
@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockCaptureMixin {
    @Shadow @Final protected List<BedrockPart> shouldRender;
    @Shadow protected List<IFunctionalRenderer> delegateRenderers;
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V", at = @At("HEAD"), cancellable = true)
    private void wallgun$capture(PoseStack pose, ItemDisplayContext context, RenderType type, int light, int overlay, float r, float g, float b, float a, CallbackInfo ci) {
        MeshCapture capture = MeshCapture.active();
        if (capture == null) return;
        var consumer = capture.buffer(type);
        pose.pushPose();
        try { for (BedrockPart part : shouldRender) part.render(pose, context, consumer, light, overlay, r, g, b, a); }
        finally { pose.popPose(); }
        for (IFunctionalRenderer renderer : delegateRenderers) renderer.render(pose, consumer, context, light, overlay);
        ci.cancel();
    }
}
