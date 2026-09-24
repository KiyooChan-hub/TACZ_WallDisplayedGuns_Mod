package dev.kiyo.wallgun.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kiyo.wallgun.WallGunEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/** Visibility/light collection only. No per-frame bone traversal or vertex emission here. */
public final class WallGunRenderer implements BlockEntityRenderer<WallGunEntity> {
    public WallGunRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(WallGunEntity gun, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (ModList.get().isLoaded("create") && CreateMovingGuns.renderIfVirtual(gun, pose, light)) return;
        WallBatches.enqueue(gun, light);
    }
    @Override public AABB getRenderBoundingBox(WallGunEntity gun) { return new AABB(gun.getBlockPos()).inflate(1.0); }
    // The anchor section may be outside the frustum while a long barrel is still visible.
    @Override public boolean shouldRenderOffScreen(WallGunEntity gun) { return true; }
    @Override public int getViewDistance() { return 96; }
}
