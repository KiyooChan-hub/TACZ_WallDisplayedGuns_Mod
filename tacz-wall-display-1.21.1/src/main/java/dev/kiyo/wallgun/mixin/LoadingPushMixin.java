package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.LoadingSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class LoadingPushMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void noPhysics(net.minecraft.world.entity.MoverType type,net.minecraft.world.phys.Vec3 movement,CallbackInfo ci){if((Object)this instanceof ServerPlayer p && LoadingSessions.waiting(p))ci.cancel();}
    @Inject(method="push(DDD)V",at=@At("HEAD"),cancellable=true)
    private void noPush(double x,double y,double z,CallbackInfo ci){if((Object)this instanceof ServerPlayer p && LoadingSessions.waiting(p))ci.cancel();}
}
