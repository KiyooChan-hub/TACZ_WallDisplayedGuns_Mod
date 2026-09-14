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
        out.addVertex(p,0,0,.5F).setColor(-1).setUv(0,1).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
        out.addVertex(p,1,0,.5F).setColor(-1).setUv(1,1).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
        out.addVertex(p,1,1,.5F).setColor(-1).setUv(1,0).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
        out.addVertex(p,0,1,.5F).setColor(-1).setUv(0,0).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
    }
}
