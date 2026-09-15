package dev.kiyo.wallgun.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public interface GunTransformInvoker {
    @Invoker("applyPositioningTransform")
    static void wallgun$position(ItemDisplayContext context, TransformScale scale, BedrockGunModel model, PoseStack pose) { throw new AssertionError(); }
    @Invoker("applyScaleTransform")
    static void wallgun$scale(ItemDisplayContext context, TransformScale scale, PoseStack pose) { throw new AssertionError(); }
}
