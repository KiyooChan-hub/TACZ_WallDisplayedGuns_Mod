package dev.kiyo.wallgun.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import dev.kiyo.wallgun.client.MeshCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** TACZ draws scope bodies/rings outside BedrockModel.render, directly to its live buffer. */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class AttachmentCaptureMixin {
    @Inject(method = "renderTempPart", at = @At("HEAD"), cancellable = true)
    private void wallgun$capturePart(PoseStack pose, ItemDisplayContext context, RenderType type,
                                    int light, int overlay, List<BedrockPart> path, CallbackInfo ci) {
        MeshCapture capture = MeshCapture.active();
        if (capture == null || context != ItemDisplayContext.FIXED) return;
        BedrockPart part = path.get(path.size()-1);
        pose.pushPose();
        try {
            for (int i = 0; i < path.size() - 1; i++) path.get(i).translateAndRotateAndScale(pose);
            part.visible = true;
            part.render(pose, context, capture.buffer(type), light, overlay);
        } finally {
            // Match TACZ: temporarily exposed nodes must be hidden before the generic pass.
            part.visible = false;
            pose.popPose();
        }
        ci.cancel();
    }
}
