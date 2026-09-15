package dev.kiyo.wallgun.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.world.item.*;

/** Flat source-pack slot texture, including in the hand; never warms up heavy gun geometry. */
public final class WallGunIcon extends BlockEntityWithoutLevelRenderer {
    public static final WallGunIcon INSTANCE = new WallGunIcon();
    private WallGunIcon() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var snapshot=WallGuns.snapshot(stack);
        var texture=snapshot == null ? MissingTextureAtlasSprite.getLocation() : TimelessAPI.getGunDisplay(snapshot.copyGun()).map(i->i.getSlotTexture()).orElse(MissingTextureAtlasSprite.getLocation());
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var p=pose.last();
        out.vertex(p.pose(),0,0,.5F).color(-1).uv(0,1).overlayCoords(overlay).uv2(light).normal(p.normal(),0,0,1).endVertex();
        out.vertex(p.pose(),1,0,.5F).color(-1).uv(1,1).overlayCoords(overlay).uv2(light).normal(p.normal(),0,0,1).endVertex();
        out.vertex(p.pose(),1,1,.5F).color(-1).uv(1,0).overlayCoords(overlay).uv2(light).normal(p.normal(),0,0,1).endVertex();
        out.vertex(p.pose(),0,1,.5F).color(-1).uv(0,0).overlayCoords(overlay).uv2(light).normal(p.normal(),0,0,1).endVertex();
    }
}
