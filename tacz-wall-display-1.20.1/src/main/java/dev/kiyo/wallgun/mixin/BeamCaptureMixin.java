package dev.kiyo.wallgun.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.BeamRenderer;
import dev.kiyo.wallgun.client.MeshCapture;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** A static display retains the laser device, not its live aiming ray and world-dependent bounds. */
@Mixin(value=BeamRenderer.class,remap=false)
public class BeamCaptureMixin {
    @Inject(method="renderLaserBeam",at=@At("HEAD"),cancellable=true)
    private static void wallgun$omitLiveBeam(ItemStack gun,PoseStack pose,ItemDisplayContext context,List<BedrockPart> paths,CallbackInfo ci) {
        if(MeshCapture.active()!=null)ci.cancel();
    }
}
