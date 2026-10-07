package dev.kiyo.wallgun.mixin;

import com.example.taczmeshloader.core.PolyMeshModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import dev.kiyo.wallgun.client.MeshCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional TML bridge. Never enter its live GPU/stencil/batch-flush path during baking. */
@Pseudo
@Mixin(targets = "com.example.taczmeshloader.tacz.TaczPolyMeshGunModel", remap = false)
public abstract class MeshGunCaptureMixin extends BedrockGunModel {
    @Shadow private PolyMeshModel polyMeshModel;
    @Shadow private ResourceLocation overrideTexture;
    @Shadow private boolean cachedHasAdditionalMagMesh;

    protected MeshGunCaptureMixin(BedrockModelPOJO model, BedrockVersion version) { super(model, version); }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wallgun$captureMesh(PoseStack pose, ItemStack gun, ItemDisplayContext context,
                                     RenderType type, int light, int overlay, CallbackInfo ci) {
        var capture = MeshCapture.active();
        if (capture == null || context != ItemDisplayContext.FIXED || polyMeshModel == null) return;
        ResourceLocation texture = overrideTexture != null ? overrideTexture
                : TimelessAPI.getGunDisplay(gun).orElseThrow().getModelTexture();
        // This model belongs to the detached GunDisplayInstance, never the player's live gun.
        if (cachedHasAdditionalMagMesh) polyMeshModel.setExcludeSubtree("additional_magazine");
        else polyMeshModel.clearExcludeSubtree();
        pose.pushPose();
        try {
            // Apply TACZ's saved attachment/magazine visibility and capture mixed cube parts.
            super.render(pose, gun, context, type, light, overlay);
            polyMeshModel.renderWithTranslucentSplit(pose, capture::buffer, texture, light, overlay, false);
        } finally {
            polyMeshModel.clearExcludeSubtree();
            pose.popPose();
        }
        ci.cancel();
    }
}
