package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.client.LoadingClient;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ReceivingLevelScreen.class)
public abstract class LoadingTerrainMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void wallgunStage(net.minecraft.client.gui.GuiGraphics gui,int mouseX,int mouseY,float partial,CallbackInfo ci) {
        if(!LoadingClient.waiting())return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        gui.drawCenteredString(mc.font,net.minecraft.network.chat.Component.translatable("wallgun.loading."+LoadingClient.stage()),
            gui.guiWidth()/2,gui.guiHeight()/2+24,0xffffff);
    }
    @Inject(method="onClose",at=@At("HEAD"),cancellable=true)
    private void waitForServer(CallbackInfo ci){if(LoadingClient.waiting())ci.cancel();}
}
