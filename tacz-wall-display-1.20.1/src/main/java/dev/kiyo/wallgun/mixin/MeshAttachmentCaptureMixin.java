package dev.kiyo.wallgun.mixin;

import com.example.taczmeshloader.core.PolyMeshModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import dev.kiyo.wallgun.client.MeshCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.IdentityHashMap;
import java.util.Map;

/** Captures mesh attachments for both ordinary and mesh guns; leaves live rendering alone. */
@Pseudo
@Mixin(targets = "com.example.taczmeshloader.tacz.TaczPolyMeshAttachmentModel", remap = false)
public abstract class MeshAttachmentCaptureMixin extends BedrockAttachmentModel {
    @Shadow private PolyMeshModel polyMeshModel;
    @Shadow private void restorePartVisibilityForPolyMesh() { throw new AssertionError(); }

    protected MeshAttachmentCaptureMixin(BedrockModelPOJO model, BedrockVersion version) { super(model, version); }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wallgun$captureMeshAttachment(ItemStack attachment, ItemStack gun, PoseStack pose,
            ItemDisplayContext context, RenderType type, int light, int overlay, CallbackInfo ci) {
        var capture = MeshCapture.active();
        if (capture == null || context != ItemDisplayContext.FIXED || polyMeshModel == null) return;
        var api = IAttachment.getIAttachmentOrNull(attachment);
        if (api == null) throw new IllegalStateException("Missing mesh attachment identity");
        var texture = TimelessAPI.getClientAttachmentIndex(api.getAttachmentId(attachment))
                .orElseThrow().getModelTexture();
        Map<BedrockPart, Boolean> visibility = new IdentityHashMap<>();
        wallgun$rememberVisibility(getRootNode(), visibility);
        var flags = (PolyMeshStateAccessor) (Object) polyMeshModel;
        boolean ocularHidden = flags.wallgun$ocularHidden();
        boolean ringHidden = flags.wallgun$ocularRingHidden();
        pose.pushPose();
        try {
            super.render(attachment, gun, pose, context, type, light, overlay);
            restorePartVisibilityForPolyMesh();
            // FIXED is not first person: no scope stencil hole or hidden ocular ring.
            polyMeshModel.setOcularBonesHidden(false);
            polyMeshModel.setOcularRingBonesHidden(false);
            polyMeshModel.renderWithTranslucentSplit(pose, capture::buffer, texture, light, overlay, false);
        } finally {
            polyMeshModel.setOcularBonesHidden(ocularHidden);
            polyMeshModel.setOcularRingBonesHidden(ringHidden);
            visibility.forEach((part, visible) -> part.visible = visible);
            pose.popPose();
        }
        ci.cancel();
    }

    @Unique private static void wallgun$rememberVisibility(BedrockPart part, Map<BedrockPart, Boolean> out) {
        if (part == null || out.put(part, part.visible) != null) return;
        for (var child : part.children) wallgun$rememberVisibility(child, out);
    }
}
