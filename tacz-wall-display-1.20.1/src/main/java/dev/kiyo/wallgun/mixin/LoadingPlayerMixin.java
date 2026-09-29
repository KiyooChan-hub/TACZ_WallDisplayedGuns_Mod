package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.LoadingSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayer.class)
public abstract class LoadingPlayerMixin {
    @Inject(method="restoreFrom",at=@At("HEAD"))
    private void newLife(ServerPlayer old,boolean keep,CallbackInfo ci){LoadingSessions.arm((ServerPlayer)(Object)this);}
    @Inject(method="changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),remap=false)
    private void travel(ServerLevel destination,net.minecraftforge.common.util.ITeleporter teleporter,CallbackInfoReturnable<Entity> ci){LoadingSessions.arm((ServerPlayer)(Object)this);}
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"))
    private void teleport(ServerLevel destination,double x,double y,double z,float yaw,float pitch,CallbackInfo ci){
        var player=(ServerPlayer)(Object)this;
        if(player.serverLevel()!=destination)LoadingSessions.arm(player);
    }
    @Inject(method={"tick","doTick"},at=@At("HEAD"),cancellable=true)
    private void waitForScene(CallbackInfo ci){if(LoadingSessions.waiting((ServerPlayer)(Object)this))ci.cancel();}
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void noDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){if(LoadingSessions.waiting((ServerPlayer)(Object)this))ci.setReturnValue(false);}
    @Inject(method="isInvulnerableTo",at=@At("HEAD"),cancellable=true)
    private void protectedPlayer(DamageSource source,CallbackInfoReturnable<Boolean> ci){if(LoadingSessions.waiting((ServerPlayer)(Object)this))ci.setReturnValue(true);}
}
